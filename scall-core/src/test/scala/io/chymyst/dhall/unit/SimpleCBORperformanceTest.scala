package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.Expression
import io.chymyst.dhall.Syntax.Expression.v
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.SyntaxConstants.Builtin.{Natural, NaturalFold}
import io.chymyst.dhall.unit.SimpleCBORperformanceTest.{cborRoundtrip1, cborRoundtrip2}
import io.chymyst.dhall.{CBOR, CBORmodel}

import scala.collection.mutable

object SimpleCBORperformanceTest {
  def cborRoundtrip1(expr: Expression) = {
    val aModel = CBOR.toCborModel(expr)

    val aBytes: Array[Byte] = aModel.encodeCbor1
    val bModel: CBORmodel   = CBORmodel.decodeCbor1(aBytes)

    val aModelString = aModel.toString
    val bModelString = bModel.toString
    expect(aModelString == bModelString)
  }

  def cborRoundtrip2(expr: Expression) = {
    val aModel = CBOR.toCborModel(expr)

    val aBytes: Array[Byte] = aModel.encodeCbor2
    val bModel: CBORmodel   = CBORmodel.decodeCbor2(aBytes)

    val aModelString = aModel.toString
    val bModelString = bModel.toString
    expect(aModelString == bModelString)
  }
}

class SimpleCBORperformanceTest extends DhallTest {

  import scala.util.control.TailCalls._

  // λ(x : Natural) → Natural/fold x Natural () 1
  def largeNormalForm(size: Int): Expression = {
    def large(size: Int): TailRec[Expression] =
      if (size == 0)
        done("λ(x : Natural) → x + 1".dhall)
      else
        for {
          x <- tailcall(large(size - 1))
        } yield {
          (v("x") | ~Natural) -> (~NaturalFold)(v("x"))(~Natural)(x)(v("x") + NaturalLiteral(1))

        }

    large(size).result
  }

  // s"λ(x : Natural) → Natural/fold x Natural (${largeNormalForm(size - 1)}) (x + 1)")

  test("produce an expression with a large normal form") {
    val n        = 50 // TODO: n = 250 gives a stack overflow. Set n = 50000.
    val expr     = largeNormalForm(n)
    expect(expr.exprCount == 6 * n + 3)
    // TODO: this creates a stack overflow, need to fix.
    //    expr.typeCheckAndBetaNormalize().unsafeGet
    val elapsed1 = elapsedNanos(cborRoundtrip1(expr))._2 / 1000000.0
    val elapsed2 = elapsedNanos(cborRoundtrip2(expr))._2 / 1000000.0
    println(s"cbor1 : $elapsed1 ms, cbor2 : $elapsed2 ms")
    expect(elapsed1 > elapsed2 * 1.3) // cbor1 is about 2x slower than cbor2
  }

  test("beta-normalizing performance") {
    val n        = 25
    val expr     = largeNormalForm(n)
    expect(expr.exprCount == 6 * n + 3)
    // TODO: fix stack overflow and set n = 200000
    val elapsed1 = elapsedNanos(expr.typeCheckAndBetaNormalize().unsafeGet)._2 / 1000000.0
    println(s"beta-normalizing for an expression of length $n takes $elapsed1 ms")
  }

  test("no more stack overflow in Scala MurmurHash3 or hashCode()") {
    val n         = 50000 // TODO: Set this to 50000
    val delta     = n / 20 - 1
    val lastGoodN = (1 to n by delta).map { i =>
      println(s"Iteration $i")
      val expr  = largeNormalForm(i) // This will throw an exception if we fail to produce that expression due to stack overflow.
      val count = expr.exprCount     // This should not throw any exceptions either.
      val hash  = expr.hashCode()
      i
    }.last
    expect(lastGoodN > n - delta)
  }

  sealed trait Tree

  final case class Leaf() extends Tree

  final case class Branch(left: Tree, right: Tree) extends Tree

  def largeTree(n: Int): Tree = {
    def large: Int => TailRec[Tree] = {
      case 0 => done(Leaf())
      case n =>
        for {
          x <- tailcall(large(n - 1))
          y <- tailcall(large(0))
        } yield Branch(x, y)
    }

    large(n).result
  }

  test("stack overflow in Scala MurmurHash3") {
    val n     = 1000 // TODO: set this to 100000
    val delta = n / 50
    val cache = mutable.Map[Tree, Int]()
    (0 to n by delta).foreach { i =>
      val tree = largeTree(i)
      println(s"Size $i, tree computed")
      cache.put(tree, i)
      println(s"Size $i, tree cached")
    }

  }
}
