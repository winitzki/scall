package io.chymyst.dhall

import io.chymyst.dhall.Applicative.seqSeq
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.Syntax.{Expression, ExpressionScheme, PathComponent}
import io.chymyst.dhall.SyntaxConstants._
import io.chymyst.dhall.TypeCheck.KnownVars
import io.chymyst.dhall.TypecheckResult._

import scala.language.postfixOps

sealed trait TypecheckResult[+A] {
  def isValid: Boolean

  def map[B](f: A => B): TypecheckResult[B]

  def flatMap[B](f: A => TypecheckResult[B]): TypecheckResult[B]

  def zip[B](other: TypecheckResult[B]): TypecheckResult[(A, B)]

  def withFilter(p: A => Boolean): TypecheckResult[A]

  def unsafeGet: A
}

object TypecheckResult {
  final case class Valid[A](expr: A) extends TypecheckResult[A] {
    override def unsafeGet: A = expr

    override def isValid: Boolean = true

    override def map[B](f: A => B): TypecheckResult[B] = Valid(f(expr))

    override def flatMap[B](f: A => TypecheckResult[B]): TypecheckResult[B] = f(expr)

    override def zip[B](other: TypecheckResult[B]): TypecheckResult[(A, B)] = other match {
      case Valid(expr2)    => Valid(expr, expr2)
      case Invalid(errors) => Invalid(errors)
    }

    override def withFilter(p: A => Boolean): TypecheckResult[A] = if (p(expr)) this else typeError(s"Unexpected expression $expr")(KnownVars(Map()))
  }

  final case class Invalid(errors: TypeCheck.TypeCheckErrors) extends TypecheckResult[Nothing] {
    override def unsafeGet: Nothing = throw new Exception(s"Type-checking failed with errors: $errors")

    override def isValid: Boolean = false

    override def map[B](f: Nothing => B): TypecheckResult[B] = Invalid(errors)

    override def flatMap[B](f: Nothing => TypecheckResult[B]): TypecheckResult[B] = Invalid(errors)

    override def zip[B](other: TypecheckResult[B]): TypecheckResult[(Nothing, B)] = other match {
      case Valid(_)             => Invalid(errors)
      case Invalid(otherErrors) => Invalid(errors ++ otherErrors)
    }

    override def withFilter(p: Nothing => Boolean): TypecheckResult[Nothing] = this
  }

  def typeError(message: String)(implicit gamma: KnownVars): TypecheckResult[Nothing] = Invalid(Seq(message + s", type inference context = $gamma"))

  implicit val ApplicativeTypeCheckResult: Applicative[TypecheckResult] = new Applicative[TypecheckResult] {
    override def zip[A, B](fa: TypecheckResult[A], fb: TypecheckResult[B]): TypecheckResult[(A, B)] = fa zip fb

    override def map[A, B](f: A => B)(fa: TypecheckResult[A]): TypecheckResult[B] = fa map f

    override def pure[A](a: A): TypecheckResult[A] = Valid(a)
  }
}

object TypeCheck {
  val emptyContext = KnownVars(Map())

  val maxCacheSize: Option[Int] = Some(1000000) // Specify `None` for no limit.

  val cacheTypeCheck =
    new ObservedCache("Type-checking cache", ObservedCache.createCache[(KnownVars, ExpressionScheme[Expression]), TypecheckResult[Expression]](maxCacheSize))

  type TypeCheckErrors = Seq[String] // Non-empty list.

  object KnownVars {
    def empty: KnownVars = KnownVars(Map())
  }

  // This data structure is denoted by Γ in the math notation. This can be used as a "context" for type-checking and for evaluation.
  final case class KnownVars(variables: Map[VarName, IndexedSeq[Expression]]) {
    def lookup(variable: Variable): Option[Expression] = variables.get(variable.name).flatMap { exprs =>
      if (variable.index.isValidInt) exprs.lift(variable.index.intValue) else None
    }

    // Important: the expressions must be prepended to the list even though the math notation is `(Γ0, x : A1)`.
    // This is because a de Bruijn index increases to the left in the list.
    def prepend(varName: VarName, expr: Expression) = KnownVars(variables.updatedWith(varName) {
      case Some(exprs) =>
        // println(s"DEBUG: prepending ${varName.name} : ${expr.print} to ${exprs.map{_.print}.mkString("[", ", ", "]")} in $this")
        Some(expr +: exprs)
      case None        => Some(IndexedSeq(expr))
    })

    def mapExpr(f: Expression => Expression): KnownVars = KnownVars(variables.map { case (name, exprs) => (name, exprs.map(f)) })

    override def toString: String = variables
      .flatMap { case (varName, exprs) =>
        exprs.zipWithIndex.map { case (expr, index) =>
          varName.name + (if (index > 0) s"@$index" else "") + " : " + expr.print
        }
      }.mkString("{", ", ", "}")
  }

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/type-inference.md
  // Check that a given expression has the given type, that is, gamma |- expr : tipe. If this holds, no errors are output.
  def validate(gamma: KnownVars, expr: Expression, tipe: Expression): TypecheckResult[Expression] = {
    inferType(gamma, expr) match {
      case Valid(inferredType) =>
        if (Semantics.equivalent(tipe, inferredType))
          Valid(expr)
        else
          typeError(s"Expression ${expr.print} has inferred type ${inferredType.print} and not the expected type ${tipe.print}")(gamma)

      case error @ Invalid(_) => error
    }
  }

  def required(cond: Boolean)(error: String)(implicit gamma: KnownVars): TypecheckResult[Unit] =
    if (cond) Valid(()) else typeError(error)

  val _Type: Expression      = ExprConstant(Constant.Type)
  val underscore: Expression = Expression(Variable(ExpressionScheme.underscore, BigInt(0)))

  // Infer the type of a given expression (not necessarily in beta-normalized form). If no errors, return Right(tipe) that fits gamma |- expr : tipe.
  def inferType(gamma: KnownVars, expr: Expression): TypecheckResult[Expression] = cacheTypeCheck.getOrElseUpdate((gamma, expr), inferTypeOrCached(gamma, expr))

  private def inferTypeOrCached(gamma: KnownVars, exprToInferTypeOf: Expression): TypecheckResult[Expression] = {
    //    println(s"DEBUG: ${LocalDateTime.now} inferType(${exprToInferTypeOf.print})")
    implicit def toExpr(expr: Expression): TypecheckResult[Expression] = Valid(expr)

    implicit def fromBuiltin(builtin: Builtin): TypecheckResult[Expression] = Valid(~builtin)

    implicit val _gamma: KnownVars = gamma

    def typeOfToMap(t: Expression): Expression = (~Builtin.List)(Expression(RecordType(Seq((FieldName("mapKey"), ~Builtin.Text), (FieldName("mapValue"), t)))))

    // Compute the upper bound of all universes in the given list.
    def upperBoundUniverse(defs: Seq[Option[Expression]]): TypecheckResult[Expression] = {
      val result: TypecheckResult[Seq[Constant]] = for {
        // Verify that all expressions are typed as Type, Kind, or Sort.
        exprTypes      <- seqSeq(defs.flatMap(_.map(_.inferTypeWith(gamma))))
        unexpectedTypes = exprTypes.filterNot(_.scheme match {
                            case ExprConstant(Constant.Type) | ExprConstant(Constant.Kind) | ExprConstant(Constant.Sort) => true
                            case _                                                                                       => false
                          })
        _              <- required(unexpectedTypes.isEmpty)(s"Unexpected types (must be one of Type, Kind, Sort): ${unexpectedTypes.map(_.print).mkString("; ")}")
      } yield exprTypes.map(_.scheme).collect {
        case ExprConstant(Constant.Type) => Constant.Type
        case ExprConstant(Constant.Kind) => Constant.Kind
        case ExprConstant(Constant.Sort) => Constant.Sort
      }

      result.map(_.foldLeft(Constant.Type: Constant)((x, y) => x union y)).map(ExprConstant.apply) // For empty sequences, it's always `Type`.
    }

    // TODO: possible optimization - replace Seq[(VarName, Expr)] by Map[VarName, Expr] and so on. Make sure we detect and eliminate repeated keys at an appropriate stage.

    // TODO: possible optimization - put this into case classes and avoid a big match/case
    val result: TypecheckResult[Expression] = exprToInferTypeOf.scheme match {
      case v @ Variable(_, _) =>
        gamma.lookup(v) match {
          case Some(tipe) =>
            tipe.inferTypeWith(gamma) match {
              case Valid(_)        => tipe
              case Invalid(errors) => Invalid(errors :+ s"Variable ${exprToInferTypeOf.print} has type error(s)")
            }
          case None       => typeError(s"Variable ${exprToInferTypeOf.print} is not in type inference context")
        }

      case Lambda(name, tipe, body) =>
        for {
          varType       <- tipe.typeCheckAndBetaNormalize(gamma)
          updatedContext = gamma.prepend(name, varType).mapExpr(Semantics.shift(true, name, 0, _))
          // _ = println(s"DEBUG 2: updated context is $updatedContext")
          bodyType      <- body.inferTypeWith(updatedContext)
          typeOfLambda   = (Expression(Variable(name, BigInt(0))) | varType) ->: bodyType
          _             <- typeOfLambda.inferTypeWith(gamma)
        } yield typeOfLambda

      case Forall(name, tipe, body) =>
        val updatedContext = gamma.prepend(name, tipe).mapExpr(Semantics.shift(true, name, 0, _))
        // println(s"DEBUG 2: updated context is $updatedContext")
        tipe.inferTypeWith(gamma) zip body.inferTypeWith(updatedContext) flatMap {
          case (Expression(ExprConstant(inputType)), Expression(ExprConstant(outputType))) => Expression(ExprConstant(functionCheck(inputType, outputType)))
          case (other1, other2)                                                            =>
            typeError(s"A function's input and output types must be one of Type, Kind, or Sort, but instead found ${other1.print} and ${other2.print}")
        }

      case Let(name, tipe, subst, body) =>
        val substWellTyped = for {
          typeOfSubst <- subst.inferTypeWith(gamma)
          _           <- tipe match {
                           case Some(annot) =>
                             for {
                               _ <- annot.inferTypeWith(gamma)
                               _ <-
                                 required(Semantics.equivalent(annot, typeOfSubst))(s"Type annotation ${annot.print} does not match inferred type ${typeOfSubst.print}")
                             } yield ()
                           case None        => Valid(())
                         }
        } yield typeOfSubst
        // TODO report issue: suggest to add this optimization to the standard in type-inference.md?
        // Optimization: the type of `let x = subst in x` is just the type of `subst`, no need to beta-normalize `subst` in that case.
        val typeOfBody     = body.scheme match {
          case Variable(x, index) if x == name && index == 0 => substWellTyped
          case _                                             =>
            for {
              _         <- substWellTyped
              a1         = subst.betaNormalized
              a2         = Semantics.shift(true, name, 0, a1)
              b1         = Semantics.substitute(body, name, BigInt(0), a2)
              b2         = Semantics.shift(false, name, 0, b1)
              typeOfLet <- b2.inferTypeWith(gamma)
            } yield typeOfLet
        }
        typeOfBody

      case If(cond, ifTrue, ifFalse) =>
        val lopType          = ifTrue.inferAndValidateTypeWith(gamma)
        val ropType          = ifFalse.inferAndValidateTypeWith(gamma)
        val equivalenceCheck = for {
          pair <- lopType zip ropType
          _    <- required(Semantics.equivalent(pair._1, pair._2))(s"Types of two If() clauses are not equivalent: ${pair._1.print} and ${pair._2.print}")
        } yield pair._1
        validate(gamma, cond, ~Builtin.Bool) zip equivalenceCheck map (_._2)

      case Merge(record, update, tipe) =>
        record.inferTypeWith(gamma) zip update.inferTypeWith(gamma) flatMap {
          case (Expression(RecordType(Seq())), u @ Expression(UnionType(defs))) =>
            if (defs.isEmpty) {
              tipe match {
                case Some(value) => Valid(value)
                case None        => typeError(s"merge expression with empty arguments must have a type annotation, but found ${exprToInferTypeOf.print}")
              }
            } else typeError(s"merge expression with empty matcher must be applied to an empty union, but found ${u.print}")

          case _ if tipe.nonEmpty =>
            Expression(Merge(record, update, None)).inferTypeWith(gamma).flatMap { inferred =>
              if (Semantics.equivalent(inferred, tipe.get)) inferred
              else typeError(s"merge expression has inferred type ${inferred.print}, but type annotation ${tipe.get.print}")
            }

          case (
                Expression(matcher @ RecordType(defsMatcher)),
                Expression(target @ UnionType(defsTarget)),
              ) => // Now the RecordType is nonempty but tipe is empty.
            if (defsMatcher.sizeCompare(defsTarget) == 0) {
              val typesInParts: TypecheckResult[Seq[Expression]] = seqSeq(matcher.sorted.defs zip target.sorted.defs map {
                case ((name1, expr1), (name2, expr2)) if name1.name == name2.name =>
                  expr2 match {
                    case Some(partType) =>
                      expr1 match {
                        case Expression(handlerType @ Forall(varName, varType, targetType)) =>
                          for {
                            result <- Valid(Semantics.shift(false, varName, 0, targetType))
                            _      <-
                              required(Semantics.equivalent(varType, partType))(
                                s"merge expression must have matcher's argument types equal to field types, but found ${varType.print} and ${partType.print}"
                              )
                            _      <-
                              required(!Semantics.freeVars(targetType).names.contains(varName))(
                                s"Disallowed handler type ${handlerType.print}, cannot be a type constructor (a handler's body cannot have $varName as a free variable)"
                              )
                          } yield result

                        case other => typeError(s"merge expression must have a function matcher for field $name1, but instead it has type ${other.print}")
                      }
                    case None           => Valid(expr1)
                  }
                case ((name1, expr1), (name2, expr2))                             => typeError(s"merge's matcher has field $name1 not equal to target type's $name2")
              })
              typesInParts.flatMap { exprs => // Non-empty list.
                exprs.tail.find(expr => !Semantics.equivalent(exprs.head, expr)) match {
                  case Some(value) =>
                    typeError(s"merge expression must have all matcher's output types the same, but found ${exprs.head.print}, ..., ${value.print}")
                  case None        => Valid(exprs.head)
                }
              }
            } else typeError(s"merge expression's both arguments must have equal size, but found ${matcher.print} and ${target.print}")

          case (Expression(RecordType(_)), Expression(Application(Expression(ExprBuiltin(Builtin.Optional)), optType))) =>
            val updatedContext = gamma
              .prepend(VarName("x"), Expression(UnionType(Seq((ConstructorName("None"), None), (ConstructorName("Some"), Some(optType)))))).mapExpr(
                Semantics.shift(true, VarName("x"), 0, _)
              )
            val updatedRecord  = Semantics.shift(true, VarName("x"), 0, record) // TODO verify that this is true, as this contradicts type-inference.md
            Expression(Merge(updatedRecord, ~"x", None)).inferTypeWith(updatedContext)

          case (other1, other2) =>
            typeError(s"merge's first argument must have RecordType and the second argument must have UnionType, but found ${other1.print} and ${other2.print}")
        }

      case ToMap(e, tipe) =>
        e.inferTypeWith(gamma) flatMap {
          case Expression(RecordType(Seq())) =>
            tipe match {
              case Some(t) =>
                validate(gamma, t, _Type).flatMap {
                  _.betaNormalized match {
                    case newT @ Expression(
                          Application(
                            Expression(ExprBuiltin(Builtin.List)),
                            Expression(RecordType(Seq((FieldName("mapKey"), Expression(ExprBuiltin(Builtin.Text))), (FieldName("mapValue"), t)))),
                          )
                        ) =>
                      newT
                    case other => typeError(s"toMap must have a type annotation of the form ${typeOfToMap(~"T")} for some type T but has ${other.print}")
                  }
                }
              case None    => typeError(s"toMap of empty record, ${exprToInferTypeOf.print}, must have a type annotation")
            }
          case Expression(RecordType(defs))  =>
            tipe match {
              case Some(t1) =>
                ToMap(e, None).inferTypeWith(gamma).flatMap { t0 =>
                  if (Semantics.equivalent(t0, t1)) Valid(t0)
                  else typeError(s"toMap with type annotation ${t1.print} has a different inferred type ${t0.print}")
                }
              case None     =>
                // All types in `defs` must be equivalent and must have type Type. Also, `defs` is now a non-empty list.
                val allTypesEqual = defs.tail.find(tipe => !Semantics.equivalent(defs.head._2, tipe._2)) match {
                  case Some((field, expr)) =>
                    typeError(
                      s"toMap's argument must be a record with equal types, but found non-equal types {${defs.head._1.name} : ${defs.head._2.print}, ..., ${field.name} : ${expr.print}}"
                    )
                  case None                => validate(gamma, defs.head._2, _Type)
                }
                allTypesEqual.map(typeOfToMap)

            }
          case other                         => typeError(s"toMap's argument must have a record type but instead has type ${other.print}")
        }

      case EmptyList(tipe) =>
        for {
          _                                                     <- tipe.inferTypeWith(gamma)
          Application(Expression(ExprBuiltin(Builtin.List)), t) <- Valid(tipe.betaNormalized.scheme)
          _                                                     <- validate(gamma, t, _Type) // TODO: see if we can allow List elements that are of type Kind or Sort.
        } yield tipe.betaNormalized

      case NonEmptyList(exprs) =>
        seqSeq(exprs.map(_.inferTypeWith(gamma).flatMap { expr => validate(gamma, expr, _Type).map(_ => expr) }))
          // Require all types to be the same.
          .flatMap { types =>
            val differentType: Option[Expression] = types.tail.find(tipe => !Semantics.equivalent(types.head, tipe))
            differentType match {
              case Some(value) => typeError(s"List must have elements of the same type but found [${types.head.print}, ..., ${value.print}, ...]")
              case None        => (~Builtin.List)(types.head)
            }
          }

      case Annotation(data, tipe) =>
        if (tipe == Expression(~Constant.Sort)) validate(gamma, data, tipe).map(_ => tipe)
        else {
          for {
            pair <- data.inferTypeWith(gamma) zip tipe.inferTypeWith(gamma)
            _    <- required(Semantics.equivalent(pair._1, tipe))(s"Inferred type ${pair._1.print} is not equal to the type ${tipe.print} given in the annotation")
          } yield pair._1 // Return the inferred type because it is assured to be in a normalized form.
        }

      case ExprOperator(lop, op, rop) =>
        op match {
          case Operator.Or | Operator.And | Operator.Equal | Operator.NotEqual =>
            validate(gamma, lop, ~Builtin.Bool) zip validate(gamma, rop, ~Builtin.Bool) map (_ => ~Builtin.Bool)
          case Operator.Plus | Operator.Times                                  =>
            validate(gamma, lop, ~Builtin.Natural) zip validate(gamma, rop, ~Builtin.Natural) map (_ => ~Builtin.Natural)
          case Operator.TextAppend                                             =>
            validate(gamma, lop, ~Builtin.Text) zip validate(gamma, rop, ~Builtin.Text) map (_ => ~Builtin.Text)
          case Operator.ListAppend                                             =>
            val lopType = for {
              tipe                                                  <- lop.inferTypeWith(gamma)
              Application(Expression(ExprBuiltin(Builtin.List)), t) <- Valid(tipe.scheme)
            } yield t
            val ropType = for {
              tipe                                                  <- rop.inferTypeWith(gamma)
              Application(Expression(ExprBuiltin(Builtin.List)), t) <- Valid(tipe.scheme)
            } yield t
            (lopType zip ropType).flatMap { case (l, r) =>
              if (Semantics.equivalent(l, r)) Valid((~Builtin.List)(l))
              else typeError(s"List types in ${exprToInferTypeOf.print} must be equal for ListAppend but found ${l.print} and ${r.print}")
            }

          case Operator.CombineRecordTerms =>
            (lop.inferTypeWith(gamma) zip rop.inferTypeWith(gamma)) flatMap { case (lopType, ropType) =>
              Expression(ExprOperator(lopType, Operator.CombineRecordTypes, ropType)).typeCheckAndBetaNormalize(gamma)
            }

          case Operator.Prefer =>
            (lop.inferTypeWith(gamma) zip rop.inferTypeWith(gamma)) flatMap {
              case (Expression(RecordType(leftDefs)), Expression(RecordType(rightDefs))) =>
                // Keep all labels from the left, add all new labels from the right, replace existing labels by those from the right.
                Expression(RecordType((leftDefs.toMap ++ rightDefs.toMap).toSeq.sortBy(_._1.name)))

              case (other1, other2) =>
                typeError(
                  s"Arguments of Operator.Prefer (${Operator.Prefer.name}) must both have record types, instead found ${other1.print} and ${other2.print}"
                )
            }

          case Operator.CombineRecordTypes => // Recursive merge.
            (lop.inferTypeWith(gamma) zip rop.inferTypeWith(gamma)) flatMap { case (lopType, ropType) =>
              (lop.betaNormalized.scheme, rop.betaNormalized.scheme, lopType.scheme, ropType.scheme) match {
                case (RecordType(leftDefs), RecordType(rightDefs), ExprConstant(leftC), ExprConstant(rightC)) =>
                  // The result is always the universe-level union. We just need to verify that all common labels have types that also can be combined.
                  val result                    = Expression(ExprConstant(leftC union rightC))
                  val leftDefsMap               = leftDefs.toMap
                  val rightDefsMap              = rightDefs.toMap
                  val commonLabels              = leftDefsMap.keySet intersect rightDefsMap.keySet
                  val commonLabelsCanBeCombined = seqSeq(
                    commonLabels.toSeq.map(l => Expression(ExprOperator(leftDefsMap(l), Operator.CombineRecordTypes, rightDefsMap(l))).inferTypeWith(gamma))
                  )

                  commonLabelsCanBeCombined.map(_ => result)

                case (other1, other2, t1, t2) =>
                  typeError(
                    s"Arguments of Operator.CombineRecordTypes (${Operator.CombineRecordTypes.name}) must both have record types, instead found ${other1.print} : ${t1.print} and ${other2.print} : ${t2.print}"
                  )
              }
            }

          case Operator.Equivalent =>
            val lopType          = for {
              tLop <- lop.inferTypeWith(gamma)
              _    <- validate(gamma, tLop, _Type) // TODO: see if we can relax this restriction. What if we allow this to be a Kind or a Sortl?
            } yield tLop
            val ropType          = for {
              tRop <- rop.inferTypeWith(gamma)
              _    <- validate(gamma, tRop, _Type) // TODO: see if we can relax this restriction. What if we allow this to be a Kind or a Sortl?
            } yield tRop
            val equivalenceCheck = for {
              pair <- lopType zip ropType
              _    <-
                required(Semantics.equivalent(pair._1, pair._2))(s"Types of two sides of `===` are not equivalent: ${pair._1.print} and ${pair._2.print}")
            } yield ()
            equivalenceCheck.map(_ => _Type)

          case Operator.Alternative => typeError(s"Cannot typecheck an expression with unresolved imports: ${exprToInferTypeOf.print}")
        }

      case Application(func, arg)        =>
        func.inferTypeWith(gamma) zip arg.inferTypeWith(gamma) flatMap {
          case (Expression(Forall(varName, varType, bodyType)), argType) =>
            if (Semantics.equivalent(varType, argType)) {
              val a1 = Semantics.shift(true, varName, 0, arg)
              val b1 = Semantics.substitute(bodyType, varName, BigInt(0), a1)
              val b2 = Semantics.shift(false, varName, 0, b1)
              Valid(b2.betaNormalized)
            } else
              typeError(s"Function application in ${exprToInferTypeOf.print} must have matching types, but instead found ${varType.print} and ${argType.print}")
          case (other, _)                                                => typeError(s"Function application in ${exprToInferTypeOf.print} must use a function type, but instead found ${other.print}")
        }

      // Field selection is possible only from a record value, from a record type, or from a union type.
      case Field(base, name)             =>
        base.inferTypeWith(gamma).flatMap {
          case Expression(r @ RecordType(defs))                                                                                            =>
            r.lookup(name) match {
              case Some(tipe) => tipe
              case None       => typeError(s"RecordType with field names ${defs.map(_._1.name).mkString(", ")} does not contain $name")
            }
          /*

        If a union alternative is non-empty, then the corresponding constructor is a function that wraps a value of the appropriate type:
        Γ ⊢ u:c   u⇥<x:T|ts...>  ↑(1,x,0,<x:T|ts...>)=U
         ─────────────────────────────────────────────────────────────────
          Γ ⊢ u.x:∀(x:T)→U
        If a union alternative is empty, then the corresponding constructor's type is the same as the original union type:
        Γ⊢u:c   u⇥<x|ts...>
         ───────────────────────────
          Γ ⊢ u.x:<x|ts...>
           */
          case Expression(ExprConstant(Constant.Type)) | Expression(ExprConstant(Constant.Kind)) | Expression(ExprConstant(Constant.Sort)) =>
            base.betaNormalized.scheme match {
              case r @ UnionType(defs) =>
                r.lookup(ConstructorName(name.name)) match {
                  case Some(Some(tipe)) =>
                    val shifted = Semantics.shift(true, VarName(name.name), 0, r)
                    (~(name.name) | tipe) ->: shifted
                  case Some(None)       => Expression(r)
                  case None             => typeError(s"UnionType with field names ${defs.map(_._1.name).mkString(", ")} does not contain $name")
                }

              case r @ RecordType(defs) =>
                r.lookup(name) match {
                  case Some(tipe) => tipe.inferTypeWith(gamma)
                  case None       => typeError(s"RecordType with field names ${defs.map(_._1.name).mkString(", ")} does not contain $name")
                }

              case other => typeError(s"Field selection is possible only from record type or union type but found ${other.print}")
            }

          case other =>
            typeError(
              s"Field selection in ${exprToInferTypeOf.print} must be for a record type, a record value, or a union type, but instead found type ${other.print}"
            )
        }

      // TODO add tests to make sure typecheck fails for t.{} unless t is a literal record type or t is a value of record type, otherwise this code is wrong. Follow https://github.com/dhall-lang/dhall-lang/pull/1371
      case ProjectByLabels(base, labels) =>
        val labelSet                         = labels.toSet
        val distinctLabelsCheck              =
          if (labels.size != labelSet.size)
            typeError(
              s"Duplicate projection labels (${labels.sortBy(_.name).diff(labels.sortBy(_.name).distinct).mkString(", ")}) in {${labels.mkString(", ")}}"
            )
          else Valid(())
        val baseTypeIsARecordHavingAllLabels = base.inferTypeWith(gamma).flatMap { tipe =>
          tipe.scheme match {
            case RecordType(defs) =>
              val missingLabels = labelSet diff defs.map(_._1).toSet
              if (missingLabels.nonEmpty)
                typeError(
                  s"Record projection by {${labels.mkString(", ")}} is invalid because labels {${missingLabels.mkString(", ")}} are missing from the base record"
                )
              else Expression(RecordType(defs.filter(d => labelSet contains d._1)))

            case ExprConstant(Constant.Type) | ExprConstant(Constant.Kind) | ExprConstant(Constant.Sort) =>
              base.betaNormalized.scheme match {
                case RecordType(defs) =>
                  val missingLabels = labelSet diff defs.map(_._1).toSet
                  if (missingLabels.nonEmpty)
                    typeError(
                      s"Record projection by {${labels.mkString(", ")}} is invalid because labels {${missingLabels.mkString(", ")}} are missing from the base record type"
                    )
                  else upperBoundUniverse(defs.filter(d => labelSet contains d._1).map(pair => Some(pair._2)))
                case other            => typeError(s"ProjectByLabels is invalid because the base expression is ${other.print}, but it must be a literal RecordType")
              }

            case other =>
              typeError(
                s"ProjectByLabels is invalid, the base expression has type ${other.print} but it must be a value of a record type or reducible to a literal RecordType"
              )
          }
        }
        distinctLabelsCheck zip baseTypeIsARecordHavingAllLabels map (_._2)

      case ProjectByType(base, by) =>
        val baseType              = base.inferTypeWith(gamma).flatMap {
          case Expression(RecordType(defs)) => Valid(defs)
          case other                        => typeError(s"ProjectByType is invalid because the base expression has type ${other.print} instead of RecordType")
        }
        val projectIsByRecordType =
          by.typeCheckAndBetaNormalize(gamma).flatMap {
            case Expression(RecordType(defs)) => Valid(defs)
            case other                        => typeError(s"ProjectByType is invalid because the projection expression is ${other.print} instead of RecordType")
          }
        baseType zip projectIsByRecordType flatMap { case (defsBase, defsProjectBy) =>
          val baseLabels                   = defsBase.toMap
          val projectLabels                = defsProjectBy.toMap
          val projectByLabelsMissingInBase = projectLabels.keySet diff baseLabels.keySet
          val checkNoMissingLabels         =
            if (projectByLabelsMissingInBase.isEmpty) Valid(())
            else typeError(s"ProjectByType is invalid because labels are missing: {${projectByLabelsMissingInBase.map(_.name).mkString(", ")}}")
          val commonLabels                 = projectLabels.keySet intersect baseLabels.keySet
          val mismatchedTypes              = commonLabels.filterNot { field => Semantics.equivalent(baseLabels(field), projectLabels(field)) }
          val noMismatchedTypes            =
            if (mismatchedTypes.isEmpty)
              Valid(Expression(RecordType(defsProjectBy))) // This will be the final result.
            else typeError(s"ProjectByType is invalid because types for labels {${mismatchedTypes.map(_.name).mkString(", ")}} are mismatched")

          checkNoMissingLabels zip noMismatchedTypes map (_._2)
        }

      case c @ Completion(_, _) => Semantics.desugar(c).inferTypeWith(gamma)

      case Assert(assertion) =>
        validate(gamma, assertion, _Type) match {
          case Valid(_) =>
            assertion.betaNormalized.scheme match {
              case exprN @ ExprOperator(lop, Operator.Equivalent, rop) =>
                if (Semantics.equivalent(lop, rop)) Expression(exprN) // "The inferred type of an assertion is the same as the provided annotation."
                else typeError(s"Expression `assert` failed: Unequal sides in ${exprN.print}")
              case other                                               => typeError(s"An `assert` expression must have an equality type but has ${other.print}")
            }
          case errors   => errors
        }

      case With(data, pathComponents, body) =>
        data.inferTypeWith(gamma).flatMap {
          case t @ Expression(r @ RecordType(defs)) =>
            pathComponents.head match { // pathComponents.head must exist since the list is not empty.
              case PathComponent.Label(first) =>
                val newData: Expression = pathComponents.tail match {
                  case Seq()      => body
                  case moreFields =>
                    val newBase: Expression = r.lookup(first) match {
                      case Some(_) => Field(data, first)
                      case None    => RecordLiteral(Seq())
                    }
                    With(newBase, moreFields, body)
                }
                newData.inferTypeWith(gamma).map { t => RecordType(defs.filterNot(_._1 == first) :+ (first, t)).sorted }

              case PathComponent.DescendOptional =>
                typeError(s"The label `?` can be used in `with` expressions only with the `Optional` type, but here it is used with ${t.print}")
            }

          case tt @ Expression(Application(Expression(ExprBuiltin(Builtin.Optional)), t)) =>
            if (pathComponents.head.isOptionalLabel) {
              validate(gamma, body, t).map(_ => tt)
            } else typeError(s"An Optional value must be updated with `?` but instead found ${pathComponents.head}")

          case other => typeError(s"A `with` expression's arg must have record type, but instead found ${other.print}")
        }

      case DoubleLiteral(_)  => Builtin.Double
      case NaturalLiteral(_) => Builtin.Natural
      case IntegerLiteral(_) => Builtin.Integer

      case TextLiteral(interpolations, _) =>
        val typesOfInterpolations             = interpolations.map { case (prefix, expr) => (expr, expr.inferTypeWith(gamma)) }
        val interpolationsWithTypeBuiltinText = typesOfInterpolations.map {
          case (expr, Valid(tipe)) =>
            if (Semantics.equivalent(tipe, ~Builtin.Text)) Valid(expr)
            else typeError(s"Interpolation chunk ${expr.print} is not of type Text but has type ${tipe.print}")
          case (_, other)          => other
        }
        seqSeq(interpolationsWithTypeBuiltinText).map(_ => ~Builtin.Text) // If all interpolation expressions have type Text, the entire TextLiteral also does.

      case BytesLiteral(_)         => Builtin.Bytes
      case DateLiteral(_, _, _)    => Builtin.Date
      case TimeLiteral(_, _, _, _) => Builtin.Time
      case TimeZoneLiteral(_)      => Builtin.TimeZone

      // TODO report issue - `type-inference.md` does not say that typechecking a RecordType must reject duplicate fields. (It says that for RecordLiteral only.) However, `RecordTypeDuplicateFields.dhall` is one of the failure tests.
      case RecordType(defs) =>
        val duplicates = defs.map(_._1) diff defs.map(_._1).distinct
        if (duplicates.isEmpty)
          upperBoundUniverse(defs.map(_._2).map(Some.apply))
        else typeError(s"RecordType may not have duplicate fields: {${duplicates.map(_.name).mkString(", ")}}")

      case RecordLiteral(defs) =>
        val typesOfFields = seqSeq(defs.map(_._2.inferAndValidateTypeWith(gamma)))
        typesOfFields.map(exprs => RecordType(exprs.zip(defs).map { case (tipe, (field, _)) => (field, tipe) }))

      case UnionType(defs) =>
        val constructorNames         = defs.map(_._1)
        // Verify that all constructor names are distinct.
        val constructorNamesDistinct =
          required(constructorNames.size == constructorNames.distinct.size)(s"Some constructor names are duplicated in ${exprToInferTypeOf.print}")
        upperBoundUniverse(defs.map(_._2)) zip constructorNamesDistinct map (_._1)

      case ShowConstructor(data) =>
        data.inferTypeWith(gamma) flatMap {
          case Expression(Application(Expression(ExprBuiltin(Builtin.Optional)), _)) | Expression(UnionType(_)) => ~Builtin.Text
          case tipe                                                                                             => typeError(s"showConstructor's argument must have a union type or Optional type, but has type ${tipe.print}")
        }

      case Import(_, _, _) => typeError(s"Cannot typecheck an expression with unresolved imports: ${exprToInferTypeOf.print}")

      case KeywordSome(data) => // The argument of Some can be only a Type. No universe-level polymorphism!
        data.inferTypeWith(gamma).flatMap { tipe => validate(gamma, tipe, _Type).map(_ => (~Builtin.Optional)(tipe)) }

      case ExprBuiltin(builtin) =>
        builtin match {
          case Builtin.Bool                                         => _Type
          case Builtin.Bytes                                        => _Type
          case Builtin.Date                                         => _Type
          case Builtin.DateShow                                     => ~Builtin.Date ->: ~Builtin.Text
          case Builtin.Double                                       => _Type
          case Builtin.DoubleShow                                   => ~Builtin.Double ->: ~Builtin.Text
          case Builtin.Integer                                      => _Type
          case Builtin.IntegerClamp                                 => ~Builtin.Integer ->: ~Builtin.Natural
          case Builtin.IntegerNegate                                => ~Builtin.Integer ->: ~Builtin.Integer
          case Builtin.IntegerShow                                  => ~Builtin.Integer ->: ~Builtin.Text
          case Builtin.IntegerToDouble                              => ~Builtin.Integer ->: ~Builtin.Double
          case Builtin.List                                         => _Type ->: _Type
          case buildOrFold @ (Builtin.ListBuild | Builtin.ListFold) =>
            val buildFunctionType: Expression = (~"list" | _Type) ->: (~"cons" | (~"a" ->: ~"list" ->: ~"list")) ->: (~"nil" | ~"list") ->: ~"list"

            buildOrFold match {
              case Builtin.ListBuild => (~"a" | _Type) ->: buildFunctionType ->: (~Builtin.List)(~"a")
              case Builtin.ListFold  => (~"a" | _Type) ->: (~Builtin.List)(~"a") ->: buildFunctionType
            }

          case Builtin.ListHead | Builtin.ListLast                        => (~"a" | _Type) ->: (~Builtin.List)(~"a") ->: (~Builtin.Optional)(~"a")
          case Builtin.ListIndexed                                        =>
            (~"a" | _Type) ->: (~Builtin.List)(~"a") ->: (~Builtin.List)(
              Expression(RecordType(Seq((FieldName("index"), ~Builtin.Natural), (FieldName("value"), ~"a"))))
            )
          case Builtin.ListLength                                         => (~"a" | _Type) ->: (~Builtin.List)(~"a") ->: ~Builtin.Natural
          case Builtin.ListReverse                                        => (~"a" | _Type) ->: (~Builtin.List)(~"a") ->: (~Builtin.List)(~"a")
          case Builtin.Natural                                            => _Type
          case buildOrFold @ (Builtin.NaturalBuild | Builtin.NaturalFold) =>
            val nat                           = ~"natural"
            val buildFunctionType: Expression = (nat | _Type) ->: (~"succ" | (nat ->: nat)) ->: (~"zero" | nat) ->: nat
            buildOrFold match {
              case Builtin.NaturalBuild => buildFunctionType ->: ~Builtin.Natural
              case Builtin.NaturalFold  => ~Builtin.Natural ->: buildFunctionType
            }

          case Builtin.NaturalIsZero                    => ~Builtin.Natural ->: ~Builtin.Bool
          case Builtin.NaturalOdd | Builtin.NaturalEven => ~Builtin.Natural ->: ~Builtin.Bool
          case Builtin.NaturalShow                      => ~Builtin.Natural ->: ~Builtin.Text
          case Builtin.NaturalSubtract                  => ~Builtin.Natural ->: ~Builtin.Natural ->: ~Builtin.Natural
          case Builtin.NaturalToInteger                 => ~Builtin.Natural ->: ~Builtin.Integer

          case Builtin.None         => (~"A" | _Type) ->: (~Builtin.Optional)(~"A")
          case Builtin.Optional     => _Type ->: _Type
          case Builtin.Text         => _Type
          case Builtin.TextReplace  => (~"needle" | ~Builtin.Text) ->: (~"replacement" | ~Builtin.Text) ->: (~"haystack" | ~Builtin.Text) ->: ~Builtin.Text
          case Builtin.TextShow     => ~Builtin.Text ->: ~Builtin.Text
          case Builtin.Time         => _Type
          case Builtin.TimeShow     => ~Builtin.Time ->: ~Builtin.Text
          case Builtin.TimeZone     => _Type
          case Builtin.TimeZoneShow => ~Builtin.TimeZone ->: ~Builtin.Text
        }

      case ExprConstant(constant) =>
        constant match {
          case Constant.Type                  => Valid(ExprConstant(Constant.Kind))
          case Constant.Kind                  => Valid(ExprConstant(Constant.Sort))
          case Constant.Sort                  => typeError(s"Expression ${exprToInferTypeOf.scheme} is not well-typed because it is the top universe")
          case Constant.True | Constant.False => Builtin.Bool

        }
    }
    result.map(_.betaNormalized)
  }

  def functionCheck(arg: Constant, body: Constant): Constant = (arg, body) match {
    case (_, Constant.Type) => Constant.Type
    case _                  => arg union body
  }
}
