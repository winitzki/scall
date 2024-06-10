package io.chymyst.dhall.codec

import io.chymyst.dhall.Syntax.Expression
import io.chymyst.dhall.SyntaxConstants.Builtin._
import izumi.reflect.{AnyTag, Tag, TagK, TagKK}

object FromScala {

  private val primitiveTag: Map[Tag[_], Expression] = Map(
    Tag[Boolean]              -> ~Bool,
    Tag[Int]                  -> ~Integer,
    Tag[BigInt]               -> ~Natural,
    Tag[Double]               -> ~Double,
    Tag[String]               -> ~Text,
    Tag[java.time.LocalDate]  -> ~Date,
    Tag[java.time.ZoneOffset] -> ~TimeZone,
    Tag[java.time.LocalTime]  -> ~Time,
  )

  private val primitiveTagK = Map(TagK[List] -> ~List, TagK[Option] -> ~Optional)

  def asDhallType[A](implicit tag: Tag[A]): Expression      = primitiveTag.get(tag) match {
    case Some(value) => value
    case scala.None  =>
      // Check if the tag is a subtype of Product.
      if (tag <:< Tag[Product]) {
        println(s"$tag has precise class: ${tag.hasPreciseClass}")
      }
      println(s"Not yet implemented: convert type tag $tag to Dhall, ltt=${tag.tag}, typeArgs=${tag.tag.typeArgs}, typeWithoutargs=${tag.tag.withoutArgs}")
      ???
  }
  def asDhallTypeK[A[_]](implicit tag: TagK[A]): Expression = primitiveTagK.get(tag) match {
    case Some(value) => value
    case scala.None  =>
      println(
        s"Not yet implemented: convert type constructor tag $tag to Dhall, ltt=${tag.tag}, typeArgs=${tag.tag.typeArgs}, typeWithoutargs=${tag.tag.withoutArgs}"
      )
      ???
  }

  def asDhallTypeKK[A[_, _]](implicit tag: TagKK[A]): Expression = {
    println(
      s"Not yet implemented: convert type constructor tag $tag to Dhall, ltt=${tag.tag}, typeArgs=${tag.tag.typeArgs}, typeWithoutargs=${tag.tag.withoutArgs}"
    )
    ???
  }
}
