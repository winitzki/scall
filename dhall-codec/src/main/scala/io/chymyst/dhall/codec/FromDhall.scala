package io.chymyst.dhall.codec

import io.chymyst.dhall.Syntax.ExpressionScheme.{ExprConstant, RecordType, Variable}
import io.chymyst.dhall.Syntax.{Expression, ExpressionScheme, Natural}
import io.chymyst.dhall.SyntaxConstants._
import io.chymyst.dhall.TypeCheck.KnownVars
import io.chymyst.dhall.TypecheckResult.{Invalid, Valid}
import io.chymyst.dhall.codec.DhallBuiltinFunctions._
import io.chymyst.dhall.{SyntaxConstants, TypecheckResult}
import io.chymyst.tc.Applicative.{ApplicativeOps, seqSeq}
import izumi.reflect.{Tag, TagK, TagKK}

import java.time.{LocalDate, LocalTime, ZoneOffset}
import scala.language.{dynamics, implicitConversions, reflectiveCalls}

object FromDhall {

  /** Convert a Dhall expression into a Scala value. The type parameter `A` must be specified.
    *
    * @param expr
    *   A Dhall expression. This must be a closed term, having no free variables.
    * @param tpe
    *   An izumi type tag corresponding to the given type parameter `A`.
    * @tparam A
    *   The expected Scala type of the Dhall expression after it is converted to Scala.
    * @return
    *   A Scala value of type `A`, or an exception thrown on errors.
    */
  def asScala[A](expr: Expression)(implicit tpe: Tag[A]): A = FromDhall.valueAndType(expr, Map(), KnownVars.empty) match {
    case Left(errors) =>
      val errorMessage = errors.mkString("", "; ", "")
      throw new Exception("Error importing from Dhall: " + errorMessage)

    case Right(asScalaVal) =>
      if (tpe == asScalaVal.typeTag) asScalaVal.value.asInstanceOf[A]
      else
        throw new Exception(
          s"Error importing from Dhall: type mismatch: expected type $tpe but the Dhall value actually has type ${asScalaVal.inferredType} and type tag ${asScalaVal.typeTag}"
        )
  }

  private def valueAndType(expr: Expression, variables: Map[Variable, AsScalaVal], dhallVars: KnownVars): Either[Seq[AsScalaError], AsScalaVal] = {

    implicit def toSingleError(error: AsScalaError): Left[Seq[AsScalaError], Nothing] = Left(Seq(error))

    def shiftVars(up: Boolean, varName: VarName): Map[Variable, AsScalaVal] => Map[Variable, AsScalaVal] = _.map { case (variable, value) =>
      val shift = if (up) 1 else -1
      if (variable.name == varName) (variable.copy(index = variable.index + shift), value) else (variable, value)
    }

    // Exception: Dhall's `Sort` cannot be type-checked.
    if (expr.scheme == ExprConstant(SyntaxConstants.Constant.Sort)) {
      Right(new AsScalaVal(DhallKinds.Sort, Invalid(Seq("Expression(ExprConstant(Sort)) is not well-typed because it is the top universe")), Tag[DhallKinds]))
    } else {
      // println(s"DEBUG $expr.asScala, variables = $variables, typing context = $dhallVars")
      expr.inferTypeWith(dhallVars) match {
        case errors @ TypecheckResult.Invalid(_)     => AsScalaError(expr, errors)
        case validType @ TypecheckResult.Valid(tipe) =>
          // Helper functions.
          def result[E](value: => E, expectedTag: Tag[E]): Either[Seq[AsScalaError], AsScalaVal] =
            Right(new AsScalaVal(value, validType, expectedTag))

          //          println(
          //            s"DEBUG: (${expr.print}).asScala with expected type tag ${tpe.tag}\nscalaStyledName=${tpe.tag.scalaStyledName}\nlongNameWithPrefix=${tpe.tag.longNameWithPrefix}\nlongNameInternalSymbol=${tpe.tag.longNameInternalSymbol}\nshortName=${tpe.tag.shortName}"
          //          )

          expr.scheme match {
            case v @ ExpressionScheme.Variable(_, _)      =>
              variables.get(v) match {
                case Some(knownVariableAssignment) =>
                  Right(knownVariableAssignment)
                case None                          =>
                  AsScalaError(expr, validType, None, Some(s"Error: undefined variable $v while known variables are $variables"))
              }
            case ExpressionScheme.Lambda(name, tpe, body) =>
              // Create a Scala function with variable named "x". Substitute name = x in body but first shift name upwards in body.
              // Example:
              // "λ(n : Natural) → n + (λ(n : Natural) → n + n@1) 2" should evaluate to "λ(n : Natural) → n + 2 + n"
              // It is replaced by { x: Any => x.asInstanceOf[BigInt] + {x2 : Any => x2 + x}(2) }
              var varXValue: Any = null
              val variables1     = shiftVars(up = true, name)(variables)
              val dhallVars2     = dhallVars.prependAndShift(name, tpe)
              for {
                varType     <- valueAndType(tpe, variables, dhallVars)
                varTag       = varType.value match {
                                 case x: Tag[_]          => x
                                 case x: DhallRecordType =>
                                   varXValue = DhallRecordValue(Map(), x) // The record values will be filled in later but the type tag must be known early.
                                   Tag[DhallRecordValue]
                               }
                varX         = new AsScalaVal(varXValue, Valid(tpe), varTag)
                variables2   = variables1 ++ Map(ExpressionScheme.Variable(name, BigInt(0)) -> varX)
                bodyAsScala <- valueAndType(body, variables2, dhallVars2)
              } yield {
                val lambda = { (x: Any) =>
                  varXValue = x
                  bodyAsScala.value
                }
                new AsScalaVal(lambda, validType, Tag.appliedTag(TagKK[Function1], List(varTag.tag, bodyAsScala.typeTag.tag)))
              }

            case ExpressionScheme.Forall(name, tipe, body)     => ???
            case ExpressionScheme.Let(name, tipe, subst, body) =>
              val e = ExpressionScheme.Application(Expression(ExpressionScheme.Lambda(name, subst.inferType.unsafeGet, body)), subst)
              valueAndType(e, variables, dhallVars)
            case ExpressionScheme.If(cond, ifTrue, ifFalse)    =>
              for {
                condition <- valueAndType(cond, variables, dhallVars) // This has been type-checked, so `condition` is of Dhall type `Bool`.
                result    <-
                  valueAndType(if (condition.value.asInstanceOf[Boolean]) ifTrue else ifFalse, variables, dhallVars) // Only convert to Scala if necessary.
              } yield result
            case ExpressionScheme.Merge(record, update, tipe)  => ???
            case ExpressionScheme.ToMap(data, tipe)            => ???
            case ExpressionScheme.EmptyList(_)                 =>
              tipe.scheme match {
                case ExpressionScheme.Application(_, tpe: Expression) =>
                  valueAndType(tpe, variables, dhallVars).flatMap { t =>
                    result(Seq(), Tag.appliedTag(TagK[Seq], List(t.typeTag.tag)))
                  }
              }
            case ExpressionScheme.NonEmptyList(exprs)          =>
              seqSeq(exprs.map(valueAndType(_, variables, dhallVars))).flatMap { vals =>
                val listOfValues = vals.map(_.value)
                tipe.scheme match {
                  case ExpressionScheme.Application(_, tpe: Expression) =>
                    valueAndType(tpe, variables, dhallVars).flatMap { t =>
                      result(listOfValues, Tag.appliedTag(TagK[Seq], List(t.typeTag.tag)))
                    }
                }
              }
            case ExpressionScheme.Annotation(data, _)          => valueAndType(data, variables, dhallVars)
            case ExpressionScheme.ExprOperator(lop, op, rop)   =>
              // No checking needed here, because all expressions were already type-checked.
              def useOp[P: Tag, Q: Tag, R: Tag](operator: (P, Q) => R): Either[Seq[AsScalaError], AsScalaVal] = {
                val evalLop = valueAndType(lop, variables, dhallVars)
                val evalRop = valueAndType(rop, variables, dhallVars)
                // The final value must be of the given type.
                evalLop zip evalRop map { case (x, y) =>
                  new AsScalaVal(operator(x.value.asInstanceOf[P], y.value.asInstanceOf[Q]), validType, implicitly[Tag[R]])
                }
              }

              op match {
                case Operator.Or                 => // useOp[Boolean, Boolean](_ || _)
                  // This operation must be lazy and avoid evaluating `rop` if `lop` is `True`.
                  for {
                    l      <- valueAndType(lop, variables, dhallVars)
                    result <- if (l.value.asInstanceOf[Boolean]) Right(l) else valueAndType(rop, variables, dhallVars)
                  } yield result
                case Operator.Plus               => useOp[Natural, Natural, Natural](_ + _)
                case Operator.TextAppend         => useOp[String, String, String](_ ++ _)
                case Operator.ListAppend         => useOp[Seq[_], Seq[_], Seq[_]](_ ++ _)
                case Operator.And                => // useOp[Boolean, Boolean](_ && _)
                  // This operation must be lazy and avoid evaluating `rop` if `lop` is `False`.
                  for {
                    l      <- valueAndType(lop, variables, dhallVars)
                    result <- if (!l.value.asInstanceOf[Boolean]) Right(l) else valueAndType(rop, variables, dhallVars)
                  } yield result
                case Operator.CombineRecordTerms => ???
                case Operator.Prefer             => ???
                case Operator.CombineRecordTypes => ???
                case Operator.Times              => useOp[Natural, Natural, Natural](_ * _)
                case Operator.Equal              => useOp[Boolean, Boolean, Boolean](_ == _)
                case Operator.NotEqual           => useOp[Boolean, Boolean, Boolean](_ != _)
                case Operator.Equivalent         => useOp[AsScalaVal, AsScalaVal, DhallEqualityType]((x, y) => DhallEqualityType(x, y))
                // case Operator.Alternative        => AsScalaError(expr, validType, None, Some("Cannot convert to Scala unless all import alternatives are resolved")) // This will never occur because it would fail type-checking, which we now do up front.
              }
            case ExpressionScheme.Application(func, arg)       =>
              for {
                functionHead                    <- valueAndType(func, variables, dhallVars)
                functionResultWithTypeVar       <- functionHead.inferredType.unsafeGet.scheme match {
                                                     // TODO fix this: resultType may have a free type variable bound as `tvar` here.
                                                     case ExpressionScheme.Forall(tvar, tvarType, resultType: Expression) => Right((resultType, tvar, tvarType))
                                                   }
                (functionResult, tvar, tvarType) = functionResultWithTypeVar
                variables1                       = shiftVars(up = true, tvar)(variables)
                dhallVars2                       = dhallVars.prependAndShift(tvar, tvarType)
//                tvarScala <- valueAndType(tvar, variables, dhallVars)
//                  variables2 = variables1 ++ Map(ExpressionScheme.Variable(tvar, BigInt(0)) -> tvarScala)
                functionResultTag               <- valueAndType(functionResult, variables1, dhallVars2)
                argument                        <- valueAndType(arg, variables, dhallVars)
              } yield new AsScalaVal(functionHead.value.asInstanceOf[Function1[Any, Any]](argument.value), validType, functionResultTag.typeTag)

            case ExpressionScheme.Field(base, name)                     =>
              for {
                x <- valueAndType(base, variables, dhallVars)
              } yield { // Important: must return AsScalaVal(value, ...) where value is not yet evaluated ("on-call" or "by-name") but the type tag is already known.
                x.value match {
                  case record: DhallRecordValue =>
                    new AsScalaVal(
                      {
                        x.value.asInstanceOf[DhallRecordValue].fields(name) // Cannot use `record` here! Need to use `x.value`.
                      },
                      validType,
                      record.recordType.fields(name),
                    )

                  case record: DhallRecordType =>
                    val tag = record.fields(name)
                    new AsScalaVal(tag, validType, Tag.apply(tag))
                }
              }
            case ExpressionScheme.ProjectByLabels(base, labels)         => ???
            case ExpressionScheme.ProjectByType(base, by)               => ???
            case ExpressionScheme.Completion(base, target)              => ???
            case ExpressionScheme.Assert(_)                             =>
              // This assertion has been type-checked, so it holds or we have a type error. We return Unit here.
              result((), Tag[Unit])
            case ExpressionScheme.With(data, pathComponents, body)      => ???
            case ExpressionScheme.DoubleLiteral(value)                  => result(value, Tag[Double])
            case ExpressionScheme.NaturalLiteral(value)                 => result(value, Tag[Natural])
            case ExpressionScheme.IntegerLiteral(value)                 => result(value, Tag[BigInt])
            case ExpressionScheme.TextLiteral(interpolations, trailing) =>
              val computeInterpolated: Either[Seq[AsScalaError], Seq[String]] = seqSeq(interpolations.map { case (prefix, expr) =>
                valueAndType(expr, variables, dhallVars)
                  .map(_.map(prefix + _)).map(_.value.asInstanceOf[String]) // We should have typechecked this, so all exprs are strings.
              })
              val concatenateInterpolated: Either[Seq[AsScalaError], String]  =
                computeInterpolated.map(_.mkString + trailing)

              concatenateInterpolated.flatMap(result(_, Tag[String]))

            case b: ExpressionScheme.BytesLiteral     => result(b.bytes, Tag[Array[Byte]])
            case d: ExpressionScheme.DateLiteral      => result(d.toLocalDate, Tag[LocalDate])
            case d: ExpressionScheme.TimeLiteral      => result(d.toLocalTime, Tag[LocalTime])
            case d: ExpressionScheme.TimeZoneLiteral  => result(d.toZoneOffset, Tag[ZoneOffset])
            case ExpressionScheme.RecordType(defs)    =>
              seqSeq(defs.map { case (field, tipe) =>
                valueAndType(tipe, variables, dhallVars).map { t =>
//                                        println(s"DEBUG: processing RecordType($defs), got t=$t")
                  (field, t.value.asInstanceOf[Tag[_]])
                }
              }).map(_.toMap).map(fields => new AsScalaVal(DhallRecordType(fields), validType, Tag[DhallRecordType]))
            case ExpressionScheme.RecordLiteral(defs) =>
              val types: Either[Seq[AsScalaError], Map[FieldName, Tag[_]]]       =
                seqSeq(tipe.scheme.asInstanceOf[RecordType[Expression]].defs.map { case (field, tipe) =>
                  valueAndType(tipe, variables, dhallVars).map { t =>
//                      println(s"DEBUG: processing RecordLiteral($defs), got t=$t")
                    (field, t.value.asInstanceOf[Tag[_]])
                  }
                }).map(_.toMap)
              val exprs: Either[Seq[AsScalaError], Seq[(FieldName, AsScalaVal)]] = seqSeq(defs.map { case (field, value) =>
                valueAndType(value, variables, dhallVars).map((field, _))
              })
              exprs zip types map { case (exprSeq, typeMap) =>
                val fields: Map[FieldName, Any] = exprSeq.map { case (field, value) => (field, value.value) }.toMap
                val tpe                         = DhallRecordType(exprSeq.map { case (field, value) => (field, typeMap(field)) }.toMap)
                new AsScalaVal(DhallRecordValue(fields, tpe), validType, Tag[DhallRecordValue])
              }
            case ExpressionScheme.UnionType(defs)     =>
              val types: Either[Seq[AsScalaError], Map[ConstructorName, Tag[_]]] = seqSeq(defs.map {
                case (constructor, None)                => Right((constructor, Tag[Unit]))
                case (constructor, Some(t: Expression)) => valueAndType(t, variables, dhallVars).map(r => (constructor, r.typeTag))
              }).map(_.toMap)
              types.map(fields => new AsScalaVal(DhallUnionType(fields), validType, Tag[DhallUnionType]))

            case ExpressionScheme.ShowConstructor(data) =>
              valueAndType(data, variables, dhallVars).flatMap { r =>
                // TODO: first we need to implement a Scala equivalent for values of union types
                r.inferredType match {
                  case Valid(Expression(tpe)) => ???
                }
              }
            case ExpressionScheme.Import(_, _, _)       =>
              AsScalaError(expr, validType, None, Some("Cannot convert to Scala unless imports are resolved"))
            case ExpressionScheme.KeywordSome(data)     =>
              valueAndType(data, variables, dhallVars).flatMap { dataAsScala =>
                result(Some(dataAsScala.value), Tag.appliedTag(TagK[Option], List(dataAsScala.typeTag.tag)))
              }

            case ExpressionScheme.ExprBuiltin(builtin)   =>
              builtin match {
                case Builtin.Bool             => result(Tag[Boolean], Tag[Tag[Boolean]])
                case Builtin.Bytes            => result(Tag[Array[Byte]], Tag[Tag[Array[Byte]]])
                case Builtin.Date             => result(Tag[LocalDate], Tag[Tag[LocalDate]])
                case Builtin.DateShow         => result(Date_show, Tag[LocalDate => String])
                case Builtin.Double           => result(Tag[Double], Tag[Tag[Double]])
                case Builtin.DoubleShow       => result(Double_show, Tag[Double => String])
                case Builtin.Integer          => result(Tag[BigInt], Tag[Tag[BigInt]])
                case Builtin.IntegerClamp     => result(Integer_clamp, Tag[BigInt => Natural])
                case Builtin.IntegerNegate    => result(Integer_negate, Tag[BigInt => BigInt])
                case Builtin.IntegerShow      => result(Integer_show, Tag[BigInt => String])
                case Builtin.IntegerToDouble  => result(Integer_toDouble, Tag[BigInt => Double])
                case Builtin.List             => result(TagK[List], Tag[TagK[List]])
                case Builtin.ListBuild        => ???
                case Builtin.ListFold         => ???
                case Builtin.ListHead         => result(List_head, Tag[Tag[_] => List[Any] => Option[Any]])
                case Builtin.ListIndexed      => result(List_indexed, Tag[Tag[_] => List[Any] => List[DhallRecordValue]])
                case Builtin.ListLast         => result(List_last, Tag[Tag[_] => List[Any] => Option[Any]])
                case Builtin.ListLength       => result(List_length, Tag[Tag[_] => List[Any] => Natural])
                case Builtin.ListReverse      => result(List_reverse, Tag[Tag[_] => List[Any] => List[Any]])
                case Builtin.Natural          => result(Tag[Natural], Tag[Tag[Natural]])
                case Builtin.NaturalBuild     => result(Natural_build, Tag[NaturalBuildArg => Natural])
                case Builtin.NaturalEven      => result(Natural_even, Tag[Natural => Boolean])
                case Builtin.NaturalFold      => result(Natural_fold, Tag[Natural => Tag[_] => (Any => Any) => Any => Any])
                case Builtin.NaturalIsZero    => result(Natural_isZero, Tag[Natural => Boolean])
                case Builtin.NaturalOdd       => result(Natural_odd, Tag[Natural => Boolean])
                case Builtin.NaturalShow      => result(Natural_show, Tag[Natural => String])
                case Builtin.NaturalSubtract  => result(Natural_subtract, Tag[Natural => Natural => Natural])
                case Builtin.NaturalToInteger => result(Natural_toInteger, Tag[Natural => BigInt])
                case Builtin.None             => result(None, Tag[Option[Nothing]])
                case Builtin.Optional         => result(TagK[Option], Tag[TagK[Option]])
                case Builtin.Text             => result(Tag[String], Tag[Tag[String]])
                case Builtin.TextReplace      => result(Text_replace, Tag[String => String => String => String])
                case Builtin.TextShow         => result(Text_show, Tag[String => String])
                case Builtin.Time             => result(Tag[LocalTime], Tag[Tag[LocalTime]])
                case Builtin.TimeShow         => result(Time_show, Tag[LocalTime => String])
                case Builtin.TimeZone         => result(Tag[ZoneOffset], Tag[Tag[ZoneOffset]])
                case Builtin.TimeZoneShow     => result(TimeZone_show, Tag[ZoneOffset => String])
              }
            case ExpressionScheme.ExprConstant(constant) =>
              constant match {
                case Constant.Type  => result(DhallKinds.Type, Tag[DhallKinds])
                case Constant.Kind  => result(DhallKinds.Kind, Tag[DhallKinds])
                case Constant.Sort  => result(DhallKinds.Sort, Tag[DhallKinds])
                case Constant.True  => result(true, Tag[Boolean])
                case Constant.False => result(false, Tag[Boolean])
              }
          }
      }
    }
  }
}
