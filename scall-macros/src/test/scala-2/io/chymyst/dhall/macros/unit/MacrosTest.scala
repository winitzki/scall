package io.chymyst.dhall.macros.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.macros.Macros
import izumi.reflect.{Tag, TagK, TagKK}
import munit.FunSuite

class MacrosTest extends FunSuite {

  test("determine subclasses of a sealed trait") {

    sealed trait TestTrait[B] {
      def a[A](x: A): A = x
    }
    object TestTrait          {
      final case class Y1(x: String, y: Int) extends TestTrait[Int]
      final case object Y2                   extends TestTrait[Nothing]
      // final case class Y3[X, XX](xx: XX, y: Boolean) extends TestTrait[X]
    }
    val subclasses = Macros.knownSubclasses[TestTrait[Double]]
    expect(subclasses == List(Tag[TestTrait.Y1], Tag[TestTrait.Y2.type]))
    println(subclasses)

    sealed trait Tr[A]
    final case object X0                  extends Tr[Int]
    final case class X1[A](x: A)          extends Tr[A]
    final case class X2[B](x: Int)        extends Tr[Float]
    final case class X3[A, B](x: A, y: B) extends Tr[B] // whatever

    val subclasses2 = Macros.knownSubclasses[Tr[_]]
    println(subclasses2)
    expect(subclasses2 == List(Tag[X0.type], TagK[X1], TagK[X2], TagKK[X3]))
  }
}
