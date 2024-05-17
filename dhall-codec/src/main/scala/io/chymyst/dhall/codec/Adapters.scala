package io.chymyst.dhall.codec

import io.chymyst.dhall.Syntax.ExpressionScheme.TextLiteral
import io.chymyst.dhall.Syntax.{Expression, Natural}
import io.chymyst.dhall.SyntaxConstants.Builtin.TextShow
import io.chymyst.dhall.SyntaxConstants._
import io.chymyst.dhall.TypecheckResult
import izumi.reflect.Tag

import java.time.{LocalDate, LocalTime, ZoneOffset}
import scala.annotation.tailrec
import scala.language.{dynamics, implicitConversions, reflectiveCalls}

sealed trait DhallKinds

object DhallKinds {
  case object Type extends DhallKinds
  case object Kind extends DhallKinds
  case object Sort extends DhallKinds
}

object DhallBuiltinFunctions {
  trait NaturalBuildArg {
    def apply[A]: (A => A) => A => A
  }

  val List_length: Tag[_] => List[Any] => Natural                   = _ => _.length
  val List_reverse: Tag[_] => List[Any] => List[Any]                = _ => _.reverse
  val List_head: Tag[_] => List[Any] => Option[Any]                 = _ => _.headOption
  val List_last: Tag[_] => List[Any] => Option[Any]                 = _ => _.lastOption
  val List_indexed: Tag[_] => List[Any] => List[DhallRecordValue]   = tag =>
    _.zipWithIndex.map { case (a, i) => DhallRecordValue(Map(FieldName("index") -> (BigInt(i), Tag[Natural]), FieldName("value") -> (a, tag))) }
  val Date_show: LocalDate => String                                = _.toString
  val Double_show: Double => String                                 = _.toString
  val Integer_clamp: BigInt => Natural                              = x => if (x < 0) BigInt(0) else x
  val Integer_negate: BigInt => BigInt                              = -_
  val Integer_show: BigInt => String                                = _.toString(10)
  val Integer_toDouble: BigInt => Double                            = _.toDouble
  val Natural_even: Natural => Boolean                              = _ % 2 == 0
  val Natural_isZero: Natural => Boolean                            = _ == 0
  val Natural_odd: Natural => Boolean                               = _ % 2 != 0
  val Natural_show: Natural => String                               = _.toString(10)
  val Natural_subtract: Natural => Natural => Natural               = x => y => y - x
  val Natural_toInteger: Natural => BigInt                          = identity
  val Natural_build: NaturalBuildArg => Natural                     = { build =>
    build.apply[Natural](x => x + 1)(BigInt(0))
  }
  val Natural_fold: Natural => Tag[_] => (Any => Any) => Any => Any = { m => _ => update => init =>
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

  override def toString: String = s"AsScalaVal($lazyValue, $inferredType, $typeTag)"
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

object Adapters {
  implicit class DhallExpressionAsScala(val expr: Expression) extends AnyVal {
    def asScala[A](implicit tpe: Tag[A]): A = FromDhall.asScala(expr)
  }
}
