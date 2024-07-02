package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.codec.FromScala
import izumi.reflect.{Tag, TagK, TagKK}
import munit.FunSuite

import scala.util.Try

class FromScalaTest extends FunSuite {

  test("convert Scala Double type to Dhall") {
    Map(
      Tag[Boolean]              -> "Bool",
      Tag[Int]                  -> "Integer",
      Tag[BigInt]               -> "Natural",
      Tag[Double]               -> "Double",
      Tag[String]               -> "Text",
      Tag[java.time.LocalDate]  -> "Date",
      Tag[java.time.ZoneOffset] -> "TimeZone",
      Tag[java.time.LocalTime]  -> "Time",
    ).foreach { case (tag, string) =>
      val x = FromScala.asDhallType(tag)
      expect(x.print == string)
    }

    expect(FromScala.asDhallTypeK[List].print == "List")
    expect(FromScala.asDhallTypeK[Option].print == "Optional")
  }

  final case class Pair1(x: Int, y: String)
  final case class Pair2[A](x: A, y: String)
  final case class Pair[+A, B](x: A, y: B)

  sealed trait Disjunctive1
  final case class D1A(x: Int)               extends Disjunctive1
  final case class D1B(y: String, z: String) extends Disjunctive1

  sealed trait Disjunctive2[+_]
  final case class D2A[A](x: Int, y: A)      extends Disjunctive2[A]
  final case class D2B(y: String, z: String) extends Disjunctive2[Nothing]

  test("convert Scala case class") {
    Try(FromScala.asDhallType[Pair1])
  }

  test("convert Scala types that are not supported") {
    Try(FromScala.asDhallType[List[String]])
    Try(FromScala.asDhallType[Pair1])
    Try(FromScala.asDhallType[Pair2[Int]])
    Try(FromScala.asDhallType[Pair[Int, String]])
    Try(FromScala.asDhallType[Disjunctive1])
    Try(FromScala.asDhallType[D1A])
    Try(FromScala.asDhallType[D1B])
    Try(FromScala.asDhallType[Disjunctive2[Double]])
    Try(FromScala.asDhallType[D2A[Double]])
    Try(FromScala.asDhallType[D2B])
  }

  test("convert Scala type constructors that are not supported") {
    Try(FromScala.asDhallTypeK[Pair2])
    Try(FromScala.asDhallTypeKK[Pair])
  }

}
