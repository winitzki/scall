package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.{Expression, Natural}
import io.chymyst.dhall.Syntax.ExpressionScheme.DoubleLiteral
import io.chymyst.dhall.codec.DhallBuiltinFunctions._
import io.chymyst.dhall.codec.{DhallKinds, Lazy}
import io.chymyst.dhall.codec.FromDhall.DhallExpressionAsScala
import izumi.reflect.{Tag, TagK}
import munit.FunSuite

import java.time.{LocalDate, LocalTime, ZoneOffset}

class ToScalaTest extends FunSuite {

  test("convert DoubleLiteral to Scala") {
    val x             = "-0.0".dhall
    val y             = x.asScala[Double]
    val z: Expression = y match {
      case Left(error)          => throw new Exception(s"Failure to convert to Double: $error")
      case Right(x: Lazy[Double]) => DoubleLiteral(x.value)
    }
    expect(x == z)
  }

  test("convert other literals to Scala") {
    expect("0b1010".dhall.asScala[Natural].map(_.value.intValue) == Right(10))
    expect("\"0b1010\"".dhall.asScala[String].map(_.value) == Right("0b1010"))
    expect(" +0b1010 ".dhall.asScala[BigInt].map(_.value.intValue) == Right(10))
    expect("True".dhall.asScala[Boolean].map(_.value) == Right(true))
    expect("False".dhall.asScala[Boolean].map(_.value) == Right(false))
    // For byte arrays, we need to compare the data in the arrays to verify that they are equal.
    expect((" 0x\"11111111\"  ".dhall.asScala[Array[Byte]] map (_.value sameElements Array[Byte](17, 17, 17, 17))  ) == Right(true))
    expect("00:00:00".dhall.asScala[LocalTime].map(_.value) == Right(LocalTime.of(0, 0, 0)))
    expect("2003-03-03".dhall.asScala[LocalDate].map(_.value) == Right(LocalDate.of(2003, 3, 3)))
    expect("-02:00".dhall.asScala[ZoneOffset].map(_.value) == Right(ZoneOffset.ofHoursMinutes(-2, 0)))
  }

  test("convert kinds to DhallKinds") {
    expect("Type".dhall.asScala[DhallKinds].map(_.value) == Right(DhallKinds.Type))
    expect("Kind".dhall.asScala[DhallKinds].map(_.value) == Right(DhallKinds.Kind))
    expect("Sort".dhall.asScala[DhallKinds].map(_.value) == Right(DhallKinds.Sort))
  }

  test("convert type names to Scala type tags") {
    expect("Text".dhall.asScala[Tag[String]].map(_.value) == Right(Tag[String]))
    expect("Bool".dhall.asScala[Tag[Boolean]].map(_.value) == Right(Tag[Boolean]))
    expect("Integer".dhall.asScala[Tag[BigInt]].map(_.value) == Right(Tag[BigInt]))
    expect("Natural".dhall.asScala[Tag[BigInt]].map(_.value) == Right(Tag[BigInt]))
    expect("Time".dhall.asScala[Tag[LocalTime]].map(_.value) == Right(Tag[LocalTime]))
    expect("Date".dhall.asScala[Tag[LocalDate]].map(_.value) == Right(Tag[LocalDate]))
    expect("TimeZone".dhall.asScala[Tag[ZoneOffset]].map(_.value) == Right(Tag[ZoneOffset]))
  }

  test("convert type constructors to Scala type tags") {
    expect("List".dhall.asScala[TagK[List]].map(_.value) == Right(TagK[List]))
    expect("Optional".dhall.asScala[TagK[Option]].map(_.value) == Right(TagK[Option]))
  }

  test("convert built-in functions to Scala functions") {
    expect("Natural/even".dhall.asScala[Natural => Boolean].map(_.value) == Right(Natural_even))
    expect("Natural/odd".dhall.asScala[Natural => Boolean].map(_.value) == Right(Natural_odd))
    expect(Natural_odd(2) == false && Natural_even(2) == true)
  }

  test("convert generic functions to Scala generic functions") {
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
    val d = "\\(A: Type) -> \\(x : A) -> x".dhall
  }
}
