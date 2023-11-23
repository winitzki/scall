package io.chymyst.dhall.codec

import io.chymyst.dhall.Syntax.ExpressionScheme.{ExprConstant, Variable}
import io.chymyst.dhall.Syntax.{Expression, ExpressionScheme, Natural}
import io.chymyst.dhall.SyntaxConstants.{Builtin, Constant}
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
  val Natural_even: Natural => Boolean = _ % 2 == 0
  val Natural_odd: Natural => Boolean  = _ % 2 != 0
}

object FromDhall {

  implicit class DhallExpressionAsScala(val x: Expression) extends AnyVal {
    def asScala[A](implicit tpe: Tag[A]): Either[AsScalaError, Lazy[A]] = FromDhall.asScala[A](x)
  }

  final case class AsScalaError(expr: Expression, inferredType: TypecheckResult[Expression], typeTag: Tag[_]) {
    private lazy val typecheckingMessage = inferredType match {
      case TypecheckResult.Valid(tipe: Expression) => s"inferred type ${tipe.toDhall}"
      case TypecheckResult.Invalid(errors)         => s"type errors: $errors"
    }

    override def toString: String =
      s"Expression ${expr.toDhall} having $typecheckingMessage cannot be converted to the given Scala type $typeTag (${typeTag.tag.longNameWithPrefix})"

  }

  def asScala[A](expr: Expression, variables: Map[Variable, Expression] = Map())(implicit tpe: Tag[A]): Either[AsScalaError, Lazy[A]] = {

    implicit def toLazy[A](a: A): Lazy[A] = new Lazy(a)

    // Exception: Dhall's `Sort` cannot be type-checked.
    if (expr.scheme == ExprConstant(SyntaxConstants.Constant.Sort)) {
      if (tpe == Tag[DhallKinds]) Right(DhallKinds.Sort.asInstanceOf[A])
      else Left(AsScalaError(expr, TypecheckResult.Invalid(Seq("Expression(ExprConstant(Sort)) is not well-typed because it is the top universe")), tpe))
    } else {
      expr.inferType match {
        case errors @ TypecheckResult.Invalid(_)     => Left(AsScalaError(expr, errors, tpe))
        case validType @ TypecheckResult.Valid(tipe) =>
          def checkType(value: => Any, expectedTag: Tag[_])(implicit tpe: Tag[A]): Either[AsScalaError, Lazy[A]] =
            if (tpe == expectedTag) Right(value.asInstanceOf[A]) else Left(AsScalaError(expr, validType, tpe))

//          println(
//            s"DEBUG: (${expr.toDhall}).asScala with expected type tag ${tpe.tag}\nscalaStyledName=${tpe.tag.scalaStyledName}\nlongNameWithPrefix=${tpe.tag.longNameWithPrefix}\nlongNameInternalSymbol=${tpe.tag.longNameInternalSymbol}\nshortName=${tpe.tag.shortName}"
//          )
          expr.scheme match {
            case v @ ExpressionScheme.Variable(_, _)                     =>
              variables.get(v) match {
                case Some(knownVariableAssignment) => asScala[A](knownVariableAssignment, variables) // TODO: is this correct?
                case None                          => ???
              }
            case ExpressionScheme.Lambda(name, tipe, body)               => ???
            case ExpressionScheme.Forall(name, tipe, body)               => ???
            case ExpressionScheme.Let(name, tipe, subst, body)           => ???
            case ExpressionScheme.If(cond, ifTrue, ifFalse)              => ???
            case ExpressionScheme.Merge(record, update, tipe)            => ???
            case ExpressionScheme.ToMap(data, tipe)                      => ???
            case ExpressionScheme.EmptyList(tipe)                        => checkType(Seq(), Tag[Seq[_]]) // TODO check if this works
            case ExpressionScheme.NonEmptyList(exprs)                    => ???
            case ExpressionScheme.Annotation(data, tipe)                 => asScala[A](data, variables)
            case ExpressionScheme.ExprOperator(lop, op, rop)             => ???
            case ExpressionScheme.Application(func, arg)                 => ???
            case ExpressionScheme.Field(base, name)                      => ???
            case ExpressionScheme.ProjectByLabels(base, labels)          => ???
            case ExpressionScheme.ProjectByType(base, by)                => ???
            case ExpressionScheme.Completion(base, target)               => ???
            case ExpressionScheme.Assert(_)                              =>
              // This assertion has been type-checked, so it holds or we have a type error. We return Unit here.
              checkType((), Tag[Unit])
            case ExpressionScheme.With(data, pathComponents, body)       => ???
            case ExpressionScheme.DoubleLiteral(value)                   => checkType(value, Tag[Double])
            case ExpressionScheme.NaturalLiteral(value)                  => checkType(value, Tag[Natural])
            case ExpressionScheme.IntegerLiteral(value)                  => checkType(value, Tag[BigInt])
            case ExpressionScheme.TextLiteral(interpolations, trailing)  =>
              if (interpolations.isEmpty) checkType(trailing, Tag[String])
              else ??? // TODO: need `traverse` for this
            case b: ExpressionScheme.BytesLiteral                        => checkType(b.bytes, Tag[Array[Byte]])
            case d: ExpressionScheme.DateLiteral                         => checkType(d.toLocalDate, Tag[LocalDate])
            case d: ExpressionScheme.TimeLiteral                         => checkType(d.toLocalTime, Tag[LocalTime])
            case d: ExpressionScheme.TimeZoneLiteral                     => checkType(d.toZoneOffset, Tag[ZoneOffset])
            case ExpressionScheme.RecordType(defs)                       => ???
            case ExpressionScheme.RecordLiteral(defs)                    => ???
            case ExpressionScheme.UnionType(defs)                        => ???
            case ExpressionScheme.ShowConstructor(data)                  => ???
            case ExpressionScheme.Import(importType, importMode, digest) =>
              Left(AsScalaError(expr, validType, tpe)) // Imports must be resolved before converting to Scala values.
            case ExpressionScheme.KeywordSome(data)                      => ???                           // TODO  Check that the type is Option[X] and then return  Some(asScala[X](data))
            case ExpressionScheme.ExprBuiltin(builtin)                   =>
              builtin match {
                case Builtin.Bool             => checkType(Tag[Boolean], Tag[Tag[Boolean]])
                case Builtin.Bytes            => checkType(Tag[Array[Byte]], Tag[Tag[Array[Byte]]])
                case Builtin.Date             => checkType(Tag[LocalDate], Tag[Tag[LocalDate]])
                case Builtin.DateShow         => ???
                case Builtin.Double           => checkType(Tag[Double], Tag[Tag[Double]])
                case Builtin.DoubleShow       => ???
                case Builtin.Integer          => checkType(Tag[BigInt], Tag[Tag[BigInt]])
                case Builtin.IntegerClamp     => ???
                case Builtin.IntegerNegate    => ???
                case Builtin.IntegerShow      => ???
                case Builtin.IntegerToDouble  => ???
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
                case Builtin.NaturalIsZero    => ???
                case Builtin.NaturalOdd       => checkType(Natural_odd, Tag[Natural => Boolean])
                case Builtin.NaturalShow      => ???
                case Builtin.NaturalSubtract  => ???
                case Builtin.NaturalToInteger => ???
                case Builtin.None             => ???
                case Builtin.Optional         => checkType(TagK[Option], Tag[TagK[Option]])
                case Builtin.Text             => checkType(Tag[String], Tag[Tag[String]])
                case Builtin.TextReplace      => ???
                case Builtin.TextShow         => ???
                case Builtin.Time             => checkType(Tag[LocalTime], Tag[Tag[LocalTime]])
                case Builtin.TimeShow         => ???
                case Builtin.TimeZone         => checkType(Tag[ZoneOffset], Tag[Tag[ZoneOffset]])
                case Builtin.TimeZoneShow     => ???
              }
            case ExpressionScheme.ExprConstant(constant)                 =>
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