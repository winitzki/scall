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

class ToScalaTest extends FunSuite {

  test("convert DoubleLiteral to Scala") {
    val x             = "-0.0".dhall
    val y             = x.asScala[Double]
    val z: Expression = DoubleLiteral(y)
    expect(x == z)
  }

  test("convert other literals to Scala") {
    expect("12345".dhall.asScala[Natural].intValue == 12345)
    expect("0b1010".dhall.asScala[Natural].intValue == 10)
    expect("\"0b1010\"".dhall.asScala[String] == "0b1010")
    expect(" +0b1010 ".dhall.asScala[BigInt].intValue == 10)
    expect("True".dhall.asScala[Boolean] == true)
    expect("False".dhall.asScala[Boolean] == false)
    // For byte arrays, we need to compare the data in the arrays to verify that they are equal.
    expect(" 0x\"11111111\"  ".dhall.asScala[Array[Byte]] sameElements Array[Byte](17, 17, 17, 17))
    expect("00:00:00".dhall.asScala[LocalTime] == LocalTime.of(0, 0, 0))
    expect("2003-03-03".dhall.asScala[LocalDate] == LocalDate.of(2003, 3, 3))
    expect("-02:00".dhall.asScala[ZoneOffset] == ZoneOffset.ofHoursMinutes(-2, 0))
  }

  test("convert kinds to DhallKinds") {
    expect("Type".dhall.asScala[DhallKinds] == DhallKinds.Type)
    expect("Kind".dhall.asScala[DhallKinds] == DhallKinds.Kind)
    expect("Sort".dhall.asScala[DhallKinds] == DhallKinds.Sort)
  }

  test("convert type names to Scala type tags") {
    expect("Text".dhall.asScala[Tag[String]] == Tag[String])
    expect("Bool".dhall.asScala[Tag[Boolean]] == Tag[Boolean])
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
