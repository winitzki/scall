package io.chymyst.dhall.codec

import io.chymyst.dhall.Syntax.ExpressionScheme.{ExprConstant, RecordType, TextLiteral, Variable}
import io.chymyst.dhall.Syntax.{Expression, ExpressionScheme, Natural}
import io.chymyst.dhall.SyntaxConstants.Builtin.TextShow
import io.chymyst.dhall.SyntaxConstants._
import io.chymyst.dhall.TypeCheck.KnownVars
import io.chymyst.dhall.TypecheckResult.{Invalid, Valid}
import io.chymyst.dhall.codec.DhallBuiltinFunctions._
import io.chymyst.dhall.{SyntaxConstants, TypecheckResult}
import io.chymyst.tc.Applicative.{ApplicativeOps, seqSeq}
import izumi.reflect.{Tag, TagK, TagKK}

import java.time.{LocalDate, LocalTime, ZoneOffset}
import scala.annotation.tailrec
import scala.language.{dynamics, implicitConversions, reflectiveCalls}

sealed trait DhallKinds

object DhallKinds {
  final case object Type extends DhallKinds
  final case object Kind extends DhallKinds
  final case object Sort extends DhallKinds
}

object DhallBuiltinFunctions {
  val List_length: Tag[_] => List[Any] => Natural                    = _ => _.length
  val List_reverse: Tag[_] => List[Any] => List[Any]                 = _ => _.reverse
  val List_head: Tag[_] => List[Any] => Option[Any]                  = _ => _.headOption
  val List_last: Tag[_] => List[Any] => Option[Any]                  = _ => _.lastOption
  val List_indexed: Tag[_] => List[Any] => List[DhallRecordValue]    = tag =>
    _.zipWithIndex.map { case (a, i) => DhallRecordValue(Map(FieldName("index") -> (BigInt(i), Tag[Natural]), FieldName("value") -> (a, tag))) }
  val Date_show: LocalDate => String                                 = _.toString
  val Double_show: Double => String                                  = _.toString
  val Integer_clamp: BigInt => Natural                               = x => if (x < 0) BigInt(0) else x
  val Integer_negate: BigInt => BigInt                               = -_
  val Integer_show: BigInt => String                                 = _.toString(10)
  val Integer_toDouble: BigInt => Double                             = _.toDouble
  val Natural_even: Natural => Boolean                               = _ % 2 == 0
  val Natural_isZero: Natural => Boolean                             = _ == 0
  val Natural_odd: Natural => Boolean                                = _ % 2 != 0
  val Natural_show: Natural => String                                = _.toString(10)
  val Natural_subtract: Natural => Natural => Natural                = x => y => y - x
  val Natural_toInteger: Natural => BigInt                           = identity
  val Natural_build: { def apply[A]: (A => A) => A => A } => Natural = { build =>
    build.apply[Natural](x => x + 1)(BigInt(0))
  }
  val Natural_fold: Natural => Tag[_] => (Any => Any) => Any => Any  = { m => _ => update => init =>
    @tailrec
    def loop(currentResult: Any, counter: Natural): Any =
      if (counter >= m) currentResult
      else {
        val newResult = update(currentResult)
        if (newResult == currentResult) {
          // Shortcut: the result did not change after applying `g` and normalizing, so no need to continue looping.
          currentResult
        } else {
          loop(newResult, counter + 1)
        }
      }

    loop(currentResult = init, counter = BigInt(0))
  }

  val Time_show: LocalTime => String                     = _.toString
  val Text_show: String => String                        = x => (~TextShow)(TextLiteral.ofString(x)).betaNormalized.print
  val Text_replace: String => String => String => String = find => replace => source => source.replace(find, replace)
  val TimeZone_show: ZoneOffset => String                = _.toString // TODO verify that this prints a reasonable representation of TimeZone, or use the Dhall format instead.
}

final case class DhallRecordValue(fields: Map[FieldName, (Any, Tag[_])]) extends Dynamic {
  def selectDynamic(field: String): Any = fields(FieldName(field))._1
}

final case class DhallRecordType(fields: Map[FieldName, Tag[_]]) extends Dynamic {
  def selectDynamic(field: String): Tag[_] = fields(FieldName(field))
}

final case class DhallUnionType(fields: Map[ConstructorName, Tag[_]])
final case class DhallUnionValue(value: Any, tpe: DhallUnionType, constructor: ConstructorName)
final case class DhallEqualityType(left: AsScalaVal, right: AsScalaVal)

/** This represents a successful conversion from Dhall to Scala. The `inferredType` must be `Valid()` except when `value = Sort`, which is not typeable.
  *
  * @param lazyValue
  *   A Scala value converted from a Dhall expression.
  * @param inferredType
  *   The result of typechecking the Dhall expression.
  * @param typeTag
  *   The izumi type tag corresponding to the converted Scala value.
  */
final class AsScalaVal(lazyValue: => Any, val inferredType: TypecheckResult[Expression], val typeTag: Tag[_]) {
  lazy val value = lazyValue

  def map(f: Any => Any): AsScalaVal = new AsScalaVal(f(value), inferredType, typeTag)
}

final case class AsScalaError(expr: Expression, inferredType: TypecheckResult[Expression], typeTag: Option[Tag[_]] = None, message: Option[String] = None) {
  private lazy val typecheckingMessage = inferredType match {
    case TypecheckResult.Valid(tipe: Expression) => s"inferred type ${tipe.print}"
    case TypecheckResult.Invalid(errors)         => s"type errors: $errors"
  }

  override def toString: String =
    s"Expression ${expr.print} having $typecheckingMessage cannot be converted to the given Scala type $typeTag (${typeTag.map(_.tag.longNameWithPrefix)})${message
        .map(": " + _).getOrElse("")}"
}

object FromDhall {

  implicit class DhallExpressionAsScala(val expr: Expression) extends AnyVal {
    def asScala[A](implicit tpe: Tag[A]): A = FromDhall.asScala(expr)
  }

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
                case Some(knownVariableAssignment) => Right(knownVariableAssignment)
                case None                          => AsScalaError(expr, validType, None, Some(s"Error: undefined variable $v while known variables are $variables"))
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
                varTag       = varType.value.asInstanceOf[Tag[_]]
                varX         = new AsScalaVal(varXValue, Valid(tpe), varTag)
                variables2   = variables1 ++ Map(ExpressionScheme.Variable(name, BigInt(0)) -> varX)
                bodyAsScala <- valueAndType(body, variables2, dhallVars2)
              } yield {
                val lambda = { x: Any =>
                  varXValue = x
                  bodyAsScala.value
                }
                new AsScalaVal(lambda, validType, Tag.appliedTag(TagKK[Function1], List(varTag.tag, bodyAsScala.typeTag.tag)))
              }

            case ExpressionScheme.Forall(name, tipe, body)              => ???
            case ExpressionScheme.Let(name, tipe, subst, body)          => ???
            case ExpressionScheme.If(cond, ifTrue, ifFalse)             =>
              for {
                condition <- valueAndType(cond, variables, dhallVars) // This has been type-checked, so `condition` is of Dhall type `Bool`.
                result    <-
                  valueAndType(if (condition.value.asInstanceOf[Boolean]) ifTrue else ifFalse, variables, dhallVars) // Only convert to Scala if necessary.
              } yield result
            case ExpressionScheme.Merge(record, update, tipe)           => ???
            case ExpressionScheme.ToMap(data, tipe)                     => ???
            case ExpressionScheme.EmptyList(_)                          =>
              tipe.scheme match {
                case ExpressionScheme.Application(_, tpe: Expression) =>
                  valueAndType(tpe, variables, dhallVars).flatMap { t =>
                    result(Seq(), Tag.appliedTag(TagK[Seq], List(t.typeTag.tag)))
                  }
              }
            case ExpressionScheme.NonEmptyList(exprs)                   =>
              seqSeq(exprs.map(valueAndType(_, variables, dhallVars))).flatMap { vals =>
                val listOfValues = vals.map(_.value)
                tipe.scheme match {
                  case ExpressionScheme.Application(_, tpe: Expression) =>
                    valueAndType(tpe, variables, dhallVars).flatMap { t =>
                      result(listOfValues, Tag.appliedTag(TagK[Seq], List(t.typeTag.tag)))
                    }
                }
              }
            case ExpressionScheme.Annotation(data, _)                   => valueAndType(data, variables, dhallVars)
            case ExpressionScheme.ExprOperator(lop, op, rop)            =>
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
            case ExpressionScheme.Application(func, arg)                =>
              for {
                functionHead      <- valueAndType(func, variables, dhallVars)
                functionResult    <- functionHead.inferredType.unsafeGet.scheme match {
                                       case ExpressionScheme.Forall(tvar, _, resultType: Expression) =>
                                         Right(resultType) // TODO fix this: resultType may have a free type variable bound as `tvar` here.
                                     }
                functionResultTag <- valueAndType(functionResult, variables, dhallVars)
                argument          <- valueAndType(arg, variables, dhallVars)
              } yield new AsScalaVal(functionHead.value.asInstanceOf[Function1[Any, Any]](argument.value), validType, functionResultTag.typeTag)
            case ExpressionScheme.Field(base, name)                     => ???
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
              seqSeq(defs.map { case (field, tipe) => valueAndType(tipe, variables, dhallVars).map(t => (field, t.typeTag)) })
                .map(_.toMap)
                .map(fields => new AsScalaVal(DhallRecordType(fields), validType, Tag[DhallRecordType]))
            case ExpressionScheme.RecordLiteral(defs) =>
              val types: Either[Seq[AsScalaError], Map[FieldName, Tag[_]]]       = seqSeq(tipe.scheme.asInstanceOf[RecordType[Expression]].defs.map {
                case (field, tipe) => valueAndType(tipe, variables, dhallVars).map(t => (field, t.typeTag))
              }).map(_.toMap)
              val exprs: Either[Seq[AsScalaError], Seq[(FieldName, AsScalaVal)]] = seqSeq(defs.map { case (field, value) =>
                valueAndType(value, variables, dhallVars).map((field, _))
              })
              exprs zip types map { case (exprSeq, typeMap) =>
                val fields: Map[FieldName, (Any, Tag[_])] = exprSeq.map { case (field, value) => (field, (value.value, typeMap(field))) }.toMap
                new AsScalaVal(DhallRecordValue(fields), validType, Tag[DhallRecordValue])
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
                case Builtin.NaturalBuild     => result(Natural_build, Tag[{ def apply[A]: (A => A) => A => A } => Natural])
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
