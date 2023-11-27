package io.chymyst.dhall.codec

import io.chymyst.dhall.Applicative.{ApplicativeOps, seqSeq}
import io.chymyst.dhall.Syntax.ExpressionScheme.{ExprConstant, TextLiteral, Variable}
import io.chymyst.dhall.Syntax.{Expression, ExpressionScheme, Natural}
import io.chymyst.dhall.SyntaxConstants.Builtin.TextShow
import io.chymyst.dhall.SyntaxConstants.{Builtin, Constant, ConstructorName, FieldName, Operator}
import io.chymyst.dhall.TypecheckResult.Invalid
import io.chymyst.dhall.codec.DhallBuiltinFunctions._
import io.chymyst.dhall.{SyntaxConstants, TypecheckResult}
import izumi.reflect.{Tag, TagK}

import java.time.{LocalDate, LocalTime, ZoneOffset}
import scala.language.implicitConversions

sealed trait DhallKinds

object DhallKinds {
  final case object Type extends DhallKinds
  final case object Kind extends DhallKinds
  final case object Sort extends DhallKinds
}

object DhallBuiltinFunctions {
  val Date_show: LocalDate => String                  = _.toString
  val Double_show: Double => String                   = _.toString
  val Integer_clamp: BigInt => Natural                = x => if (x < 0) BigInt(0) else x
  val Integer_negate: BigInt => BigInt                = -_
  val Integer_show: BigInt => String                  = _.toString(10)
  val Integer_toDouble: BigInt => Double              = _.toDouble
  val Natural_even: Natural => Boolean                = _ % 2 == 0
  val Natural_isZero: Natural => Boolean              = _ == 0
  val Natural_odd: Natural => Boolean                 = _ % 2 != 0
  val Natural_show: Natural => String                 = _.toString(10)
  val Natural_subtract: Natural => Natural => Natural = x => y => y - x
  val Natural_toInteger: Natural => BigInt            = identity
  val Time_show: LocalTime => String                  = _.toString
  val Text_show: String => String                     = x => (~TextShow)(TextLiteral.ofString(x)).betaNormalized.toDhall
  val TimeZone_show: ZoneOffset => String             = _.toString // TODO verify that this prints a reasonable representation of TimeZone, or use the Dhall format instead.
}

final case class DhallRecordValue(fields: Map[FieldName, (Any, Tag[_])])
final case class DhallRecordType(fields: Map[FieldName, Tag[_]])
final case class DhallUnionType(fields: Map[ConstructorName, Tag[_]])
final case class DhallUnionValue(value: Any, tpe: DhallUnionType, constructor: ConstructorName)

object FromDhall {

  implicit class DhallExpressionAsScala(val x: Expression) extends AnyVal {
    def asScala[A](implicit tpe: Tag[A]): Either[Seq[AsScalaError], Lazy[A]] = FromDhall.asScala[A](x)
  }

  final case class AsScalaError(expr: Expression, inferredType: TypecheckResult[Expression], typeTag: Tag[_], message: Option[String] = None) {
    private lazy val typecheckingMessage = inferredType match {
      case TypecheckResult.Valid(tipe: Expression) => s"inferred type ${tipe.toDhall}"
      case TypecheckResult.Invalid(errors)         => s"type errors: $errors"
    }

    override def toString: String =
      s"Expression ${expr.toDhall} having $typecheckingMessage cannot be converted to the given Scala type $typeTag (${typeTag.tag.longNameWithPrefix})${message
          .map(": " + _).getOrElse("")}"
  }

  def asScala[A](expr: Expression, variables: Map[Variable, Expression] = Map())(implicit tpe: Tag[A]): Either[Seq[AsScalaError], Lazy[A]] = {

    implicit def toSingleError(error: AsScalaError): Left[Seq[AsScalaError], Nothing] = Left(Seq(error))

    implicit def toRightResult[B](result: Lazy[B]): Right[Nothing, Lazy[B]] = Right(result)

    // Exception: Dhall's `Sort` cannot be type-checked.
    if (expr.scheme == ExprConstant(SyntaxConstants.Constant.Sort)) {
      if (tpe == Tag[DhallKinds]) Lazy.strict(DhallKinds.Sort.asInstanceOf[A])
      else AsScalaError(expr, Invalid(Seq("Expression(ExprConstant(Sort)) is not well-typed because it is the top universe")), tpe)
    } else {
      expr.inferType match {
        case errors @ TypecheckResult.Invalid(_)     => AsScalaError(expr, errors, tpe)
        case validType @ TypecheckResult.Valid(tipe) =>
          // Helper functions.
          def checkType[E](value: => E, expectedTag: Tag[E])(implicit tpe: Tag[A]): Either[Seq[AsScalaError], Lazy[A]] =
            if (tpe == expectedTag) Lazy(value.asInstanceOf[A]) else AsScalaError(expr, validType, tpe)

          def checkTypeLazy[E](lazyValue: Lazy[E], expectedTag: Tag[_])(implicit tpe: Tag[E]): Either[Seq[AsScalaError], Lazy[A]] =
            if (tpe == expectedTag) lazyValue.asInstanceOf[Lazy[A]] else AsScalaError(expr, validType, tpe)

          //          println(
//            s"DEBUG: (${expr.toDhall}).asScala with expected type tag ${tpe.tag}\nscalaStyledName=${tpe.tag.scalaStyledName}\nlongNameWithPrefix=${tpe.tag.longNameWithPrefix}\nlongNameInternalSymbol=${tpe.tag.longNameInternalSymbol}\nshortName=${tpe.tag.shortName}"
//          )

          expr.scheme match {
            case v @ ExpressionScheme.Variable(_, _)                    =>
              variables.get(v) match {
                case Some(knownVariableAssignment) => asScala[A](knownVariableAssignment, variables) // TODO: is this correct?
                case None                          => AsScalaError(expr, validType, tpe, Some(s"Error: undefined variable $v while known variables are $variables"))
              }
            case ExpressionScheme.Lambda(name, tipe, body)              => ???
            case ExpressionScheme.Forall(name, tipe, body)              => ???
            case ExpressionScheme.Let(name, tipe, subst, body)          => ???
            case ExpressionScheme.If(cond, ifTrue, ifFalse)             =>
              for {
                condition <- asScala[Boolean](cond, variables).map(_.value)
                result    <- asScala[A](if (condition) ifTrue else ifFalse, variables)
              } yield result
            case ExpressionScheme.Merge(record, update, tipe)           => ???
            case ExpressionScheme.ToMap(data, tipe)                     => ???
            case ExpressionScheme.EmptyList(tipe)                       => checkType(Seq(), Tag[Seq[_]]) // TODO check if this works and make it type-safe if possible.
            case ExpressionScheme.NonEmptyList(exprs)                   => ???
            case ExpressionScheme.Annotation(data, tipe)                => asScala[A](data, variables)
            case ExpressionScheme.ExprOperator(lop, op, rop)            =>
              def useOp[P: Tag, Q: Tag](operator: (P, Q) => _): Either[Seq[AsScalaError], Lazy[A]] = {
                val evalLop = asScala[P](lop, variables)
                val evalRop = asScala[Q](rop, variables)

                val opUncurried: ((P, Q)) => A = { case (a, b) => operator(a, b).asInstanceOf[A] }
                evalLop.zip(evalRop).map { a => a._1 zip a._2 map opUncurried }
              }

              op match {
                case Operator.Or                 => useOp[Boolean, Boolean](_ || _)
                case Operator.Plus               => useOp[Natural, Natural](_ + _)
                case Operator.TextAppend         => useOp[String, String](_ ++ _)
                case Operator.ListAppend         => useOp[List[_], List[_]](_ ++ _)
                case Operator.And                => useOp[Boolean, Boolean](_ && _)
                case Operator.CombineRecordTerms => ???
                case Operator.Prefer             => ???
                case Operator.CombineRecordTypes => ???
                case Operator.Times              => useOp[Natural, Natural](_ * _)
                case Operator.Equal              => useOp[Boolean, Boolean](_ == _)
                case Operator.NotEqual           => useOp[Boolean, Boolean](_ != _)
                case Operator.Equivalent         => Lazy.strict(null.asInstanceOf[A]) // TODO: do we need something else here?
                case Operator.Alternative        => AsScalaError(expr, validType, tpe, Some("Cannot convert to Scala unless all import alternatives are resolved"))
              }
            case ExpressionScheme.Application(func, arg)                => ???
            case ExpressionScheme.Field(base, name)                     => ???
            case ExpressionScheme.ProjectByLabels(base, labels)         => ???
            case ExpressionScheme.ProjectByType(base, by)               => ???
            case ExpressionScheme.Completion(base, target)              => ???
            case ExpressionScheme.Assert(_)                             =>
              // This assertion has been type-checked, so it holds or we have a type error. We return Unit here.
              checkType((), Tag[Unit])
            case ExpressionScheme.With(data, pathComponents, body)      => ???
            case ExpressionScheme.DoubleLiteral(value)                  => checkType(value, Tag[Double])
            case ExpressionScheme.NaturalLiteral(value)                 => checkType(value, Tag[Natural])
            case ExpressionScheme.IntegerLiteral(value)                 => checkType(value, Tag[BigInt])
            case ExpressionScheme.TextLiteral(interpolations, trailing) =>
              val computeInterpolated: Either[Seq[AsScalaError], Seq[Lazy[String]]] = seqSeq(interpolations.map { case (prefix, expr) =>
                asScala[String](expr, variables).map(_.map(prefix + _))
              })

              val concatenateInterpolated: Either[Seq[AsScalaError], Lazy[String]] =
                computeInterpolated.map(interpolatedParts => seqSeq(interpolatedParts).map(_.mkString + trailing))

              concatenateInterpolated.flatMap(checkTypeLazy(_, Tag[String]))

            case b: ExpressionScheme.BytesLiteral        => checkType(b.bytes, Tag[Array[Byte]])
            case d: ExpressionScheme.DateLiteral         => checkType(d.toLocalDate, Tag[LocalDate])
            case d: ExpressionScheme.TimeLiteral         => checkType(d.toLocalTime, Tag[LocalTime])
            case d: ExpressionScheme.TimeZoneLiteral     => checkType(d.toZoneOffset, Tag[ZoneOffset])
            case ExpressionScheme.RecordType(defs)       =>
              ??? // need to use the inferred record type (tipe) checkType(defs.map { case (k, v) => ()}.toMap, Tag[DhallRecordType])
            case ExpressionScheme.RecordLiteral(defs)    => ???
            case ExpressionScheme.UnionType(defs)        => ???
            case ExpressionScheme.ShowConstructor(data)  => ???
            case ExpressionScheme.Import(_, _, _)        =>
              AsScalaError(expr, validType, tpe, Some("Cannot convert to Scala unless imports are resolved"))
            case ExpressionScheme.KeywordSome(data)      => ??? // TODO  Check that the type is Option[X] and then return  Some(asScala[X](data))
            case ExpressionScheme.ExprBuiltin(builtin)   =>
              builtin match {
                case Builtin.Bool             => checkType(Tag[Boolean], Tag[Tag[Boolean]])
                case Builtin.Bytes            => checkType(Tag[Array[Byte]], Tag[Tag[Array[Byte]]])
                case Builtin.Date             => checkType(Tag[LocalDate], Tag[Tag[LocalDate]])
                case Builtin.DateShow         => checkType(Date_show, Tag[LocalDate => String])
                case Builtin.Double           => checkType(Tag[Double], Tag[Tag[Double]])
                case Builtin.DoubleShow       => checkType(Double_show, Tag[Double => String])
                case Builtin.Integer          => checkType(Tag[BigInt], Tag[Tag[BigInt]])
                case Builtin.IntegerClamp     => checkType(Integer_clamp, Tag[BigInt => Natural])
                case Builtin.IntegerNegate    => checkType(Integer_negate, Tag[BigInt => BigInt])
                case Builtin.IntegerShow      => checkType(Integer_show, Tag[BigInt => String])
                case Builtin.IntegerToDouble  => checkType(Integer_toDouble, Tag[BigInt => Double])
                case Builtin.List             => checkType(TagK[List], Tag[TagK[List]])
                case Builtin.ListBuild        => ???
                case Builtin.ListFold         => ???
                case Builtin.ListHead         => ???
                case Builtin.ListIndexed      => ???
                case Builtin.ListLast         => ???
                case Builtin.ListLength       => ???
                case Builtin.ListReverse      => ???
                case Builtin.Natural          => checkType(Tag[Natural], Tag[Tag[Natural]])
                case Builtin.NaturalBuild     => ???
                case Builtin.NaturalEven      => checkType(Natural_even, Tag[Natural => Boolean])
                case Builtin.NaturalFold      => ???
                case Builtin.NaturalIsZero    => checkType(Natural_isZero, Tag[Natural => Boolean])
                case Builtin.NaturalOdd       => checkType(Natural_odd, Tag[Natural => Boolean])
                case Builtin.NaturalShow      => checkType(Natural_show, Tag[Natural => String])
                case Builtin.NaturalSubtract  => checkType(Natural_subtract, Tag[Natural => Natural => Natural])
                case Builtin.NaturalToInteger => checkType(Natural_toInteger, Tag[Natural => BigInt])
                case Builtin.None             => ???
                case Builtin.Optional         => checkType(TagK[Option], Tag[TagK[Option]])
                case Builtin.Text             => checkType(Tag[String], Tag[Tag[String]])
                case Builtin.TextReplace      => ???
                case Builtin.TextShow         => checkType(Text_show, Tag[String => String])
                case Builtin.Time             => checkType(Tag[LocalTime], Tag[Tag[LocalTime]])
                case Builtin.TimeShow         => checkType(Time_show, Tag[LocalTime => String])
                case Builtin.TimeZone         => checkType(Tag[ZoneOffset], Tag[Tag[ZoneOffset]])
                case Builtin.TimeZoneShow     => checkType(TimeZone_show, Tag[ZoneOffset => String])
              }
            case ExpressionScheme.ExprConstant(constant) =>
              constant match {
                case Constant.Type  => checkType(DhallKinds.Type, Tag[DhallKinds])
                case Constant.Kind  => checkType(DhallKinds.Kind, Tag[DhallKinds])
                case Constant.Sort  => checkType(DhallKinds.Sort, Tag[DhallKinds])
                case Constant.True  => checkType(true, Tag[Boolean])
                case Constant.False => checkType(false, Tag[Boolean])
              }
          }
      }
    }
  }
}
