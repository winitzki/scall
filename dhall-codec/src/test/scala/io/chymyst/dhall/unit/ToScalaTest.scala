package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.{Expression, Natural}
import io.chymyst.dhall.Syntax.ExpressionScheme.DoubleLiteral
import io.chymyst.dhall.codec.DhallBuiltinFunctions._
import io.chymyst.dhall.codec.{DhallKinds, DhallRecordValue}
import io.chymyst.dhall.codec.FromDhall.DhallExpressionAsScala
import izumi.reflect.{Tag, TagK}
import munit.FunSuite

import java.time.{LocalDate, LocalTime, ZoneOffset}
import scala.util.Try

class ToScalaTest extends FunSuite {

  test("convert DoubleLiteral to Scala") {
    val x             = "-0.0".dhall
    val y             = x.asScala[Double]
    val z: Expression = DoubleLiteral(y)
    expect(x == z)
    expect(y == -0.0, y == 0.0, y.sign == 0, !(y equals 0.0)) // Note: y = -0.0 and not 0.0 but only `equals` can see this difference.
  }

  test("convert other literals to Scala") {
    expect("12345".dhall.asScala[Natural].intValue == 12345)
    expect("0b1010".dhall.asScala[Natural].intValue == 10)
    expect("\"0b1010\"".dhall.asScala[String] == "0b1010")
    expect(" +0b1010 ".dhall.asScala[BigInt].intValue == 10)
    expect("-0b1010 ".dhall.asScala[BigInt].intValue == -10)
    expect("True".dhall.asScala[Boolean] == true)
    expect("False".dhall.asScala[Boolean] == false)
    // For byte arrays, we need to compare the data in the arrays to verify that they are equal.
    expect(" 0x\"11111111\"  ".dhall.asScala[Array[Byte]] sameElements Array[Byte](17, 17, 17, 17))
    expect("00:00:00".dhall.asScala[LocalTime] == LocalTime.of(0, 0, 0))
    expect("2003-03-03".dhall.asScala[LocalDate] == LocalDate.of(2003, 3, 3))
    expect("-02:00".dhall.asScala[ZoneOffset] == ZoneOffset.ofHoursMinutes(-2, 0))
    expect("-02:30".dhall.asScala[ZoneOffset] == ZoneOffset.ofHoursMinutes(-2, -30))
  }

  test("convert kinds to DhallKinds") {
    expect("Type".dhall.asScala[DhallKinds] == DhallKinds.Type)
    expect("Kind".dhall.asScala[DhallKinds] == DhallKinds.Kind)
    expect("Sort".dhall.asScala[DhallKinds] == DhallKinds.Sort)
  }

  test("convert type names to Scala type tags") {
    expect("Text".dhall.asScala[Tag[String]] == Tag[String])
    expect("Bool".dhall.asScala[Tag[Boolean]] == Tag[Boolean])
    expect("Bytes".dhall.asScala[Tag[Array[Byte]]] == Tag[Array[Byte]])
    expect("Integer".dhall.asScala[Tag[BigInt]] == Tag[BigInt])
    expect("Natural".dhall.asScala[Tag[BigInt]] == Tag[BigInt])
    expect("Time".dhall.asScala[Tag[LocalTime]] == Tag[LocalTime])
    expect("Date".dhall.asScala[Tag[LocalDate]] == Tag[LocalDate])
    expect("TimeZone".dhall.asScala[Tag[ZoneOffset]] == Tag[ZoneOffset])
  }

  test("convert type constructors to Scala type tags") {
    expect("List".dhall.asScala[TagK[List]] == TagK[List])
    expect("Optional".dhall.asScala[TagK[Option]] == TagK[Option])
  }

  test("convert built-in functions to Scala functions") {
    expect("Natural/even".dhall.asScala[Natural => Boolean] == Natural_even)
    expect("Natural/odd".dhall.asScala[Natural => Boolean] == Natural_odd)
    expect(Natural_odd(2) == false && Natural_even(2) == true)
    expect("Natural/show".dhall.asScala[Natural => String] == Natural_show)
    expect("Natural/show 2".dhall.typeCheckAndBetaNormalize().unsafeGet.asScala[String] == "2")
    expect("Natural/subtract".dhall.asScala[Natural => Natural => Natural] == Natural_subtract)
    expect("Natural/isZero".dhall.asScala[Natural => Boolean] == Natural_isZero)
    expect("Natural/toInteger".dhall.asScala[Natural => BigInt] == Natural_toInteger)
//    expect("Natural/subtract 1 20".dhall.asScala[Natural].intValue == 19) // TODO enable this test
    expect("Double/show".dhall.asScala[Double => String] == Double_show)
    expect("Text/show".dhall.asScala[String => String] == Text_show)
    expect("Integer/show".dhall.asScala[BigInt => String] == Integer_show)
    expect("Integer/negate".dhall.asScala[BigInt => BigInt] == Integer_negate)
    expect("Integer/clamp".dhall.asScala[BigInt => Natural] == Integer_clamp)
    expect("Date/show".dhall.asScala[LocalDate => String] == Date_show)
    expect("Time/show".dhall.asScala[LocalTime => String] == Time_show)
    expect("TimeZone/show".dhall.asScala[ZoneOffset => String] == TimeZone_show)

  }

  test("fail to convert imports") {
    expect(Try("./file".dhall.asScala[Boolean]).failed.get.getMessage contains "Cannot typecheck an expression with unresolved imports")
    expect(Try("./file ? ./file".dhall.asScala[Boolean]).failed.get.getMessage contains "Cannot typecheck an expression with unresolved imports")
    expect(Try("True ? True".dhall.asScala[Boolean]).failed.get.getMessage contains "Cannot typecheck an expression with unresolved imports")
  }

  test("make sure we typecheck when doing .asScala") {
    expect(Try("1 : 2".dhall.asScala[BigInt]).failed.get.getMessage contains "Inferred type Natural is not equal to the type 2 given in the annotation")
    expect(Try("1 : Bool".dhall.asScala[BigInt]).failed.get.getMessage contains "Inferred type Natural is not equal to the type Bool given in the annotation")
  }

  test("convert arithmetic") {
    expect("1 + 1".dhall.asScala[Natural] == 2)
    expect("2 * 2".dhall.asScala[Natural] == 4)
    expect("False && True".dhall.asScala[Boolean] == false)
    expect("False == True".dhall.asScala[Boolean] == false)
    expect("False != True".dhall.asScala[Boolean] == true)
    expect("False || True".dhall.asScala[Boolean] == true)
    expect("if False then False else True".dhall.asScala[Boolean] == true)
    expect(" \"abc\" ++ \"de\" ".dhall.asScala[String] == "abcde")
    expect(" \"abc\" ++ \"de\" : Text".dhall.asScala[String] == "abcde")
    expect("assert : False === False".dhall.asScala[Unit] == ())
  }

  test("lists") { // TODO enable this test and fix type tags. The error is: Error importing from Dhall: type mismatch: expected type Tag[Seq[+Boolean]] but Dhall value actually has type Valid(List Bool) and type tag Tag[List[+Tag[=Boolean]]]
    expect(Tag.appliedTag(TagK[List], List(Tag[String].tag)) == Tag[List[String]])
    expect("[]: List Text".dhall.asScala[Seq[Tag[String]]] == Seq())

    expect("[True, False]".dhall.asScala[Seq[Tag[Boolean]]] == Seq(true, false))
    expect(
      "[1, 2] # [3, 4]".dhall.asScala[Seq[_]] == Seq(1, 2, 3, 4).map(BigInt(_))
    ) // TODO: fix type. This should accept asScala[Seq[Natural]] but currently it does not work.
  }

  test("Optional") {
    expect("Some True".dhall.asScala[Option[Boolean]] == Some(true))
    // expect("None Bool".dhall.asScala[Option[Boolean]] == None) // TODO enable this test
  }

  test("convert record literals to Scala") {
    val record = """{a = 1, b = True, c = "xyz"}""".dhall.asScala[DhallRecordValue]
    expect(record.a.asInstanceOf[BigInt] == BigInt(1))
    expect(record.b.asInstanceOf[Boolean] == true)
    expect(record.c.asInstanceOf[String] == "xyz")
  }

  test("wip convert generic functions to Scala generic functions") {
    object TestObj  {
      def a[A](x: A): A = x
    }
    trait TestTrait {
      def a[A](x: A): A = x
    }
    class TestClass {
      def a[A](x: A): A = x
    }
    val tag1 = Tag[TestObj.type]
    val tag2 = Tag[TestTrait]
    val tag3 = Tag[TestClass]
    val tag4 = Tag[{ def a[A]: A }]
    val d    = "\\(A: Type) -> \\(x : A) -> x".dhall
  }
}
