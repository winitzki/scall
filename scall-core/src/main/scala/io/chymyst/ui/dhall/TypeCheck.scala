package io.chymyst.ui.dhall

import io.chymyst.ui.dhall.Applicative.seqSeq
import io.chymyst.ui.dhall.Semantics.equivalent
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.Syntax.{Expression, ExpressionScheme}
import io.chymyst.ui.dhall.SyntaxConstants.{Builtin, Constant, ConstructorName, FieldName, Operator, VarName}
import io.chymyst.ui.dhall.TypeCheckResult._

import scala.language.postfixOps

sealed trait TypeCheckResult[+A] {
  def isValid: Boolean

  def map[B](f: A => B): TypeCheckResult[B]

  def flatMap[B](f: A => TypeCheckResult[B]): TypeCheckResult[B]

  def zip[B](other: TypeCheckResult[B]): TypeCheckResult[(A, B)]

  def withFilter(p: A => Boolean): TypeCheckResult[A]
}

object TypeCheckResult {
  final case class Valid[A](expr: A) extends TypeCheckResult[A] {
    override def isValid: Boolean = true

    override def map[B](f: A => B): TypeCheckResult[B] = Valid(f(expr))

    override def flatMap[B](f: A => TypeCheckResult[B]): TypeCheckResult[B] = f(expr)

    override def zip[B](other: TypeCheckResult[B]): TypeCheckResult[(A, B)] = other match {
      case Valid(expr2) => Valid(expr, expr2)
      case Invalid(errors) => Invalid(errors)
    }

    override def withFilter(p: A => Boolean): TypeCheckResult[A] = if (p(expr)) this else typeError(s"Unexpected expression $expr")
  }

  final case class Invalid(errors: TypeCheck.TypeCheckErrors) extends TypeCheckResult[Nothing] {
    override def isValid: Boolean = false

    override def map[B](f: Nothing => B): TypeCheckResult[B] = Invalid(errors)

    override def flatMap[B](f: Nothing => TypeCheckResult[B]): TypeCheckResult[B] = Invalid(errors)

    override def zip[B](other: TypeCheckResult[B]): TypeCheckResult[(Nothing, B)] = other match {
      case Valid(_) => Invalid(errors)
      case Invalid(otherErrors) => Invalid(errors ++ otherErrors)
    }

    override def withFilter(p: Nothing => Boolean): TypeCheckResult[Nothing] = this
  }

  def typeError(message: String): TypeCheckResult[Nothing] = Invalid(Seq(message))

  implicit val ApplicativeTypeCheckResult: Applicative[TypeCheckResult] = new Applicative[TypeCheckResult] {
    override def zip[A, B](fa: TypeCheckResult[A], fb: TypeCheckResult[B]): TypeCheckResult[(A, B)] = fa zip fb

    override def map[A, B](f: A => B)(fa: TypeCheckResult[A]): TypeCheckResult[B] = fa map f

    override def pure[A](a: A): TypeCheckResult[A] = Valid(a)
  }
}

object TypeCheck {
  val emptyContext: Gamma = Gamma(Map())

  type TypeCheckErrors = Seq[String] // Non-empty list.

  final case class Gamma(defs: Map[VarName, IndexedSeq[Expression]]) {
    def lookup(variable: Variable): Option[Expression] = defs.get(variable.name).flatMap { exprs =>
      if (variable.index.isValidInt) exprs.lift(variable.index.intValue) else None
    }

    def append(varName: VarName, expr: Expression) = defs.updatedWith(varName) {
      case Some(exprs) => Some(exprs :+ expr)
      case None => Some(IndexedSeq(expr))
    }

    def mapExpr(f: Expression => Expression): Gamma = Gamma(defs.map { case (name, exprs) => (name, exprs.map(f)) })
  }

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/type-inference.md
  // Check that a given expression has the given type, that is, gamma |- expr : tipe. If this holds, no errors are output.
  def validate(gamma: Gamma, expr: Expression, tipe: Expression): TypeCheckResult[Expression] = {
    inferType(gamma, expr) match {
      case Valid(inferredType) =>
        if (Semantics.equivalent(tipe, inferredType))
          Valid(expr)
        else
          typeError(s"Expression ${expr.toDhall} has inferred type ${inferredType.toDhall} and not the expected type ${tipe.toDhall}")

      case error@Invalid(_) => error
    }
  }

  def required(cond: Boolean)(error: String): TypeCheckResult[Unit] = if (cond) Valid(()) else typeError(error)

  // Infer the type of a given expression (not necessarily in beta-normalized form). If no errors, return Right(tipe) that fits gamma |- expr : tipe.
  def inferType(gamma: Gamma, expr: Expression): TypeCheckResult[Expression] = {
    implicit def toExpr(expr: Expression): TypeCheckResult[Expression] = Valid(expr)

    implicit def fromBuiltin(builtin: Builtin): TypeCheckResult[Expression] = Valid(~builtin)

    val _Type: Expression = ExprConstant(Constant.Type)
    val underscore: Expression = Expression(Variable(ExpressionScheme.underscore, BigInt(0)))

    def typeOfToMap(t: Expression): Expression = (~Builtin.List)(Expression(RecordType(Seq(
      (FieldName("mapKey"), ~Builtin.Text),
      (FieldName("mapValue"), t)
    ))))

    // Compute the upper bound of all universes in the given list.
    def upperBoundUniverse(defs: Seq[Option[Expression]]): TypeCheckResult[Expression] = {
      val result: TypeCheckResult[Seq[Constant]] = for {
        // Verify that all expressions are typed as Type, Kind, or Sort.
        exprTypes <- seqSeq(defs.flatMap(_.map(_.inferTypeWith(gamma))))
        unexpectedTypes = exprTypes.filterNot(_.scheme match {
          case ExprConstant(Constant.Type) | ExprConstant(Constant.Kind) | ExprConstant(Constant.Sort) => true
          case _ => false
        })
        _ <- required(unexpectedTypes.isEmpty)(s"Unexpected types (must be one of Type, Kind, Sort): ${unexpectedTypes.map(_.toDhall).mkString("; ")}")
      } yield exprTypes.map(_.scheme).collect {
        case ExprConstant(Constant.Type) => Constant.Type
        case ExprConstant(Constant.Kind) => Constant.Kind
        case ExprConstant(Constant.Sort) => Constant.Sort
      }

      result.map(_.foldLeft(Constant.Type: Constant)((x, y) => x union y)).map(ExprConstant.apply)
    }

    val result: TypeCheckResult[Expression] = expr.scheme match {
      case v@Variable(_, _) =>
        // TODO report issue: "If the natural number associated with the variable is greater than or equal to the number of type annotations in the context matching the variable then that is a type error." This seems to be incorrect: the type error occurs if the index is strictly greater.
        gamma.lookup(v) match {
          case Some(tipe) => tipe.inferTypeWith(gamma) match {
            case Valid(_) => tipe
            case Invalid(errors) => Invalid(errors :+ s"Variable ${expr.toDhall} has type error(s)")
          }
          case None => typeError(s"Variable ${expr.toDhall} is not in type inference context $gamma")
        }

      case Lambda(name, tipe, body) => ???

      case Forall(name, tipe, body) => ???

      case Let(name, tipe, subst, body) => ???

      case If(cond, ifTrue, ifFalse) =>
        val lopType = ifTrue.inferAndValidateTypeWith(gamma)
        val ropType = ifFalse.inferAndValidateTypeWith(gamma)
        val equivalenceCheck = for {
          pair <- lopType zip ropType
          _ <- required(Semantics.equivalent(pair._1, pair._2))(s"Types of two If() clauses are not equivalent: ${pair._1.toDhall} and ${pair._2.toDhall}")
        } yield pair._1
        validate(gamma, cond, ~Builtin.Bool) zip equivalenceCheck map (_._2)

      case Merge(record, update, tipe) => ???

      case ToMap(e, tipe) => e.inferTypeWith(gamma) flatMap {
        case Expression(RecordType(Seq())) => tipe match {
          case Some(t) =>
            validate(gamma, t, _Type).flatMap {
              _.betaNormalized match {
                case newT@Expression(Application(Expression(ExprBuiltin(Builtin.List)), Expression(RecordType(Seq(
                (FieldName("mapKey"), Expression(ExprBuiltin(Builtin.Text))),
                (FieldName("mapValue"), t),
                ))))) => newT
                case other => typeError(s"toMap must have a type annotation of the form ${typeOfToMap(~"T")} for some type T but has ${other.toDhall}")
              }
            }
          case None => typeError(s"toMap of empty record, ${expr.toDhall}, must have a type annotation")
        }
        case Expression(RecordType(defs)) => tipe match {
          case Some(t1) => ToMap(e, None).inferTypeWith(gamma).flatMap { t0 =>
            if (equivalent(t0, t1)) Valid(t0)
            else typeError(s"toMap with type annotation ${t1.toDhall} has a different inferred type ${t0.toDhall}")
          }
          case None =>
            // All types in `defs` must be equivalent and must have type Type. Also, `defs` is now a non-empty list.
            val allTypesEqual = defs.tail.find(tipe => !equivalent(defs.head._2, tipe._2)) match {
              case Some((field, expr)) => typeError(s"toMap's argument must be a record with equal types, but found non-equal types {${defs.head._1.name} : ${defs.head._2.toDhall}, ..., ${field.name} : ${expr.toDhall}}")
              case None => validate(gamma, defs.head._2, _Type)
            }
            allTypesEqual.map(typeOfToMap)


        }
        case other => typeError(s"toMap's argument must have a record type but instead has type ${other.toDhall}")
      }

      case EmptyList(tipe) => for {
        _ <- tipe.inferTypeWith(gamma)
        Application(Expression(ExprBuiltin(Builtin.List)), t) <- Valid(tipe.betaNormalized.scheme)
        _ <- validate(gamma, t, _Type) // TODO: see if we can allow List elements that are of type Kind or Sort.
      } yield tipe.betaNormalized

      case NonEmptyList(exprs) =>
        seqSeq(exprs.map(_.inferTypeWith(gamma).flatMap { expr => validate(gamma, expr, _Type).map(_ => expr) }))
          // Require all types to be the same.
          .flatMap { types =>
            val differentType: Option[Expression] = types.tail.find(tipe => !equivalent(types.head, tipe))
            differentType match {
              case Some(value) => typeError(s"List must have elements of the same type but found [${types.head.toDhall}, ..., ${value.toDhall}, ...]")
              case None => (~Builtin.List)(types.head)
            }
          }

      case Annotation(data, tipe) =>
        if (tipe == Expression(~Constant.Sort)) validate(gamma, data, tipe).map(_ => tipe)
        else {
          for {
            pair <- data.inferTypeWith(gamma) zip tipe.inferTypeWith(gamma)
            _ <- required(Semantics.equivalent(pair._1, tipe))(s"Inferred type ${pair._1.toDhall} is not equal to the type ${tipe.toDhall} given in the annotation")
          } yield pair._1 // Return the inferred type because it is assured to be in a normalized form.
        }

      case ExprOperator(lop, op, rop) => op match {
        case Operator.Or | Operator.And | Operator.Equal | Operator.NotEqual =>
          validate(gamma, lop, ~Builtin.Bool) zip validate(gamma, rop, ~Builtin.Bool) map (_ => ~Builtin.Bool)
        case Operator.Plus | Operator.Times =>
          validate(gamma, lop, ~Builtin.Natural) zip validate(gamma, rop, ~Builtin.Natural) map (_ => ~Builtin.Natural)
        case Operator.TextAppend =>
          validate(gamma, lop, ~Builtin.Text) zip validate(gamma, rop, ~Builtin.Text) map (_ => ~Builtin.Text)
        case Operator.ListAppend =>
          val lopType = for {
            tipe <- lop.inferTypeWith(gamma)
            Application(Expression(ExprBuiltin(Builtin.List)), t) <- Valid(tipe.scheme)
          } yield t
          val ropType = for {
            tipe <- rop.inferTypeWith(gamma)
            Application(Expression(ExprBuiltin(Builtin.List)), t) <- Valid(tipe.scheme)
          } yield t
          (lopType zip ropType).flatMap { case (l, r) =>
            if (equivalent(l, r)) Valid((~Builtin.List)(l))
            else typeError(s"List types must be equal for ListAppend but found ${l.toDhall}, ${r.toDhall}")
          }

        case Operator.CombineRecordTerms =>
          (lop.inferTypeWith(gamma) zip rop.inferTypeWith(gamma)) flatMap { case (lopType, ropType) =>
            Expression(ExprOperator(lopType, Operator.CombineRecordTypes, ropType)).wellTypedBetaNormalize(gamma)
          }

        case Operator.Prefer => (lop.inferTypeWith(gamma) zip rop.inferTypeWith(gamma)) flatMap {
          case (Expression(RecordType(leftDefs)), Expression(RecordType(rightDefs))) =>
            // Keep all labels from the left, add all new labels from the right, replace existing labels by those from the right.
            Expression(RecordType((leftDefs.toMap ++ rightDefs.toMap).toSeq.sortBy(_._1.name)))

          case (other1, other2) => typeError(s"Arguments of Operator.Prefer (${Operator.Prefer.name}) must both have record types, instead found ${other1.toDhall} and ${other2.toDhall}")
        }

        case Operator.CombineRecordTypes => // Recursive merge.
          (lop.inferTypeWith(gamma) zip rop.inferTypeWith(gamma)) flatMap { case (lopType, ropType) =>
            (lop.betaNormalized.scheme, rop.betaNormalized.scheme, lopType.scheme, ropType.scheme) match {
              case (RecordType(leftDefs), RecordType(rightDefs), ExprConstant(leftC), ExprConstant(rightC)) =>
                // The result is always the universe-level union. We just need to verify that all common labels have types that also can be combined.
                val result = Expression(ExprConstant(leftC union rightC))
                val leftDefsMap = leftDefs.toMap
                val rightDefsMap = rightDefs.toMap
                val commonLabels = leftDefsMap.keySet intersect rightDefsMap.keySet
                val commonLabelsCanBeCombined = seqSeq(commonLabels.toSeq.map(l => Expression(ExprOperator(leftDefsMap(l), Operator.CombineRecordTypes, rightDefsMap(l))).inferTypeWith(gamma)))

                commonLabelsCanBeCombined.map(_ => result)

              case (other1, other2, t1, t2) => typeError(s"Arguments of Operator.CombineRecordTypes (${Operator.CombineRecordTypes.name}) must both have record types, instead found ${other1.toDhall} : ${t1.toDhall} and ${other2.toDhall} : ${t2.toDhall}")
            }
          }

        case Operator.Equivalent =>
          val lopType = for {
            tLop <- lop.inferTypeWith(gamma)
            _ <- validate(gamma, tLop, _Type) // TODO: see if we can relax this restriction. What if we allow this to be a Kind or a Sortl?
          } yield tLop
          val ropType = for {
            tRop <- rop.inferTypeWith(gamma)
            _ <- validate(gamma, tRop, _Type) // TODO: see if we can relax this restriction. What if we allow this to be a Kind or a Sortl?
          } yield tRop
          val equivalenceCheck = for {
            pair <- lopType zip ropType
            _ <- required(Semantics.equivalent(pair._1, pair._2))(s"Types of two sides of `===` are not equivalent: ${pair._1.toDhall} and ${pair._2.toDhall}")
          } yield ()
          equivalenceCheck.map(_ => _Type)

        case Operator.Alternative => typeError(s"Cannot typecheck an expression with unresolved imports: ${expr.toDhall}")
      }

      case Application(func, arg) => ???

      // Field selection is possible only in two cases: from a record value and from a union type.
      case Field(base, name) => base.inferTypeWith(gamma).flatMap {
        case Expression(r@RecordType(defs)) => r.lookup(name) match {
          case Some(tipe) => tipe
          case None => typeError(s"RecordType with field names ${defs.map(_._1.name).mkString(", ")} does not contain $name")
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
            case r@UnionType(defs) => r.lookup(ConstructorName(name.name)) match {
              case Some(Some(tipe)) =>
                val shifted = Semantics.shift(true, VarName(name.name), 0, r)
                (~(name.name) | tipe) ->: shifted
              case Some(None) => Expression(r)
              case None => typeError(s"UnionType with field names ${defs.map(_._1.name).mkString(", ")} does not contain $name")
            }
            case other => typeError(s"Field selection is possible only from union type but found ${other.toDhall}")
          }
      }

      case ProjectByLabels(base, labels) =>
        val distinctLabelsCheck = if (labels.size != labels.distinct.size) typeError(s"Duplicate projection labels in {${labels.mkString(", ")}}") else Valid(())
        val baseTypeIsARecordHavingAllLabels = base.inferTypeWith(gamma).flatMap { tipe =>
          tipe.scheme match {
            case RecordType(defs) =>
              val labelSet = labels.toSet
              val missingLabels = labelSet diff defs.map(_._1).toSet
              if (missingLabels.nonEmpty)
                typeError(s"Record projection by {${labels.mkString(", ")}} is invalid because labels {${missingLabels.mkString(", ")}} are missing from the base record")
              else Expression(RecordType(defs.filter(d => labelSet contains d._1)))
            case other => typeError(s"ProjectByLabels is invalid because the base expression has type ${other.toDhall} instead of RecordType")
          }
        }
        distinctLabelsCheck zip baseTypeIsARecordHavingAllLabels map (_._2)

      case ProjectByType(base, by) =>
        val baseType = base.inferTypeWith(gamma).flatMap {
          case Expression(RecordType(defs)) => Valid(defs)
          case other => typeError(s"ProjectByType is invalid because the base expression has type ${other.toDhall} instead of RecordType")
        }
        val projectIsByRecordType = by.inferTypeWith(gamma).flatMap { _ =>
          by.betaNormalized.scheme match {
            case RecordType(defs) => Valid(defs)
            case other => typeError(s"ProjectByType is invalid because the projection expression is ${other.toDhall} instead of RecordType")
          }
        }
        baseType zip projectIsByRecordType flatMap { case (defsBase, defsProjectBy) =>
          val baseLabels = defsBase.toMap
          val projectLabels = defsProjectBy.toMap
          val missingLabels = projectLabels.keySet diff baseLabels.keySet
          val missingLabelsCheck = if (missingLabels.isEmpty)
            Valid(())
          else
            typeError(s"ProjectByType is invalid because labels are missing: {${missingLabels.map(_.name).mkString(", ")}}")
          val commonLabels = projectLabels.keySet intersect baseLabels.keySet
          val mismatchedTypes = commonLabels.filterNot { field => equivalent(baseLabels(field), projectLabels(field)) }
          val mismatchedTypesCheck =
            if (mismatchedTypes.isEmpty) Valid(Expression(RecordType(defsBase.filter(d => projectLabels contains d._1))))
            else
              typeError(s"ProjectByType is invalid because types for labels {${mismatchedTypes.map(_.name).mkString(", ")}} are mismatched")
          missingLabelsCheck zip mismatchedTypesCheck map (_._2)
        }

      case c@Completion(_, _) => Semantics.desugar(c).inferTypeWith(gamma)

      case Assert(assertion) =>
        validate(gamma, assertion, _Type) match {
          case Valid(_) =>
            assertion.betaNormalized.scheme match {
              case exprN@ExprOperator(lop, Operator.Equivalent, rop) =>
                if (Semantics.equivalent(lop, rop)) Expression(exprN) // "The inferred type of an assertion is the same as the provided annotation."
                else typeError(s"Expression `assert` failed: Unequal sides in ${exprN.toDhall}")
              case _ => typeError(s"An `assert` expression must have an equality type but has ${assertion.toDhall}")
            }
          case errors => errors
        }

      case With(data, pathComponents, body) => ???

      case DoubleLiteral(_) => Builtin.Double
      case NaturalLiteral(_) => Builtin.Natural
      case IntegerLiteral(_) => Builtin.Integer

      case TextLiteral(interpolations, _) =>
        val typesOfInterpolations = interpolations.map { case (prefix, expr) => (expr, expr.inferTypeWith(gamma)) }
        val interpolationsWithTypeBuiltinText = typesOfInterpolations.map {
          case (expr, Valid(tipe)) => if (Semantics.equivalent(tipe, ~Builtin.Text)) Valid(expr)
          else typeError(s"Interpolation chunk ${expr.toDhall} is not of type Text but has type ${tipe.toDhall}")
          case (_, other) => other
        }
        seqSeq(interpolationsWithTypeBuiltinText).map(_ => ~Builtin.Text) // If all interpolation expressions have type Text, the entire TextLiteral also does.

      case BytesLiteral(_) => Builtin.Bytes
      case DateLiteral(_, _, _) => Builtin.Date
      case TimeLiteral(_) => Builtin.Time
      case TimeZoneLiteral(_) => Builtin.TimeZone

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
        val constructorNames = defs.map(_._1)
        // Verify that all constructor names are distinct.
        val constructorNamesDistinct = required(constructorNames.size == constructorNames.distinct.size)(s"Some constructor names are duplicated in ${expr.toDhall}")
        upperBoundUniverse(defs.map(_._2)) zip constructorNamesDistinct map (_._1)

      case ShowConstructor(data) => data.inferTypeWith(gamma) flatMap {
        case Expression(Application(Expression(ExprBuiltin(Builtin.Optional)), _)) |
             Expression(UnionType(_)) => ~Builtin.Text
        case tipe => typeError(s"showConstructor used with data of type ${tipe.toDhall} but must be a union type or Optional")
      }

      case Import(_, _, _) => typeError(s"Cannot typecheck an expression with unresolved imports: ${expr.toDhall}")

      case KeywordSome(data) => // The argument of Some can be only a Type. No universe-level polymorphism!
        data.inferTypeWith(gamma).flatMap { tipe => validate(gamma, tipe, _Type).map(_ => (~Builtin.Optional)(tipe)) }

      case ExprBuiltin(builtin) => builtin match {
        case Builtin.Bool => _Type
        case Builtin.Bytes => _Type
        case Builtin.Date => _Type
        case Builtin.DateShow => ~Builtin.Date ->: ~Builtin.Text
        case Builtin.Double => _Type
        case Builtin.DoubleShow => ~Builtin.Double ->: ~Builtin.Text
        case Builtin.Integer => _Type
        case Builtin.IntegerClamp => ~Builtin.Integer ->: ~Builtin.Natural
        case Builtin.IntegerNegate => ~Builtin.Integer ->: ~Builtin.Integer
        case Builtin.IntegerShow => ~Builtin.Integer ->: ~Builtin.Text
        case Builtin.IntegerToDouble => ~Builtin.Integer ->: ~Builtin.Double
        case Builtin.List => _Type ->: _Type
        case buildOrFold@(Builtin.ListBuild | Builtin.ListFold) =>
          val buildFunctionType: Expression = (~"list" | _Type) ->: (~"cons" | (~"a" ->: ~"list" ->: ~"list")) ->: (~"nil" | ~"list") ->: ~"list"

          buildOrFold match {
            case Builtin.ListBuild => (~"a" | _Type) ->: buildFunctionType ->: (~Builtin.List)(~"a")
            case Builtin.ListFold => (~"a" | _Type) ->: (~Builtin.List)(~"a") ->: buildFunctionType
          }

        case Builtin.ListHead | Builtin.ListLast => (~"a" | _Type) ->: (~Builtin.List)(~"a") ->: (~Builtin.Optional)(~"a")
        case Builtin.ListIndexed => (~"a" | _Type) ->: (~Builtin.List)(~"a") ->: (~Builtin.List)(Expression(RecordType(Seq(
          (FieldName("index"), ~Builtin.Natural),
          (FieldName("value"), ~"a"),
        ))))
        case Builtin.ListLength => (~"a" | _Type) ->: (~Builtin.List)(~"a") ->: ~Builtin.Natural
        case Builtin.ListReverse => (~"a" | _Type) ->: (~Builtin.List)(~"a") ->: (~Builtin.List)(~"a")
        case Builtin.Natural => _Type
        case buildOrFold@(Builtin.NaturalBuild | Builtin.NaturalFold) =>
          val nat = ~"natural"
          val buildFunctionType: Expression = (nat | _Type) ->: (~"succ" | (nat ->: nat)) ->: (~"zero" | nat) ->: nat
          buildOrFold match {
            case Builtin.NaturalBuild => buildFunctionType ->: ~Builtin.Natural
            case Builtin.NaturalFold => ~Builtin.Natural ->: buildFunctionType
          }

        case Builtin.NaturalIsZero => ~Builtin.Natural ->: ~Builtin.Bool
        case Builtin.NaturalOdd | Builtin.NaturalEven => ~Builtin.Natural ->: ~Builtin.Bool
        case Builtin.NaturalShow => ~Builtin.Natural ->: ~Builtin.Text
        case Builtin.NaturalSubtract => ~Builtin.Natural ->: ~Builtin.Natural ->: ~Builtin.Natural
        case Builtin.NaturalToInteger => ~Builtin.Natural ->: ~Builtin.Integer

        case Builtin.None => (~"A" | _Type) ->: (~Builtin.Optional)(~"A")
        case Builtin.Optional => _Type ->: _Type
        case Builtin.Text => _Type
        case Builtin.TextReplace => (~"needle" | ~Builtin.Text) ->: (~"replacement" | ~Builtin.Text) ->: (~"haystack" | ~Builtin.Text) ->: ~Builtin.Text
        case Builtin.TextShow => ~Builtin.Text ->: ~Builtin.Text
        case Builtin.Time => _Type
        case Builtin.TimeShow => ~Builtin.Time ->: ~Builtin.Text
        case Builtin.TimeZone => _Type
        case Builtin.TimeZoneShow => ~Builtin.TimeZone ->: ~Builtin.Text
      }

      case ExprConstant(constant) => constant match {
        case Constant.Type => Valid(ExprConstant(Constant.Kind))
        case Constant.Kind => Valid(ExprConstant(Constant.Sort))
        case Constant.Sort => typeError(s"Expression $expr is not well-typed because it is the top universe")
        case Constant.True | Constant.False => Builtin.Bool

      }
    }
    result.map(_.betaNormalized)
  }

  def functionCheck(arg: Constant, body: Constant): Constant = (arg, body) match {
    case (_, Constant.Type) => Constant.Type
    case _ => arg union body
  }
}
