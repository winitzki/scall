package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.Expression
import io.chymyst.dhall.Syntax.Expression.v
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.SyntaxConstants.Builtin.{Natural, NaturalFold}
import io.chymyst.dhall.unit.SimpleCBORperformanceTest.{cborRoundtrip1, cborRoundtrip2}
import io.chymyst.dhall.{CBOR, CBORmodel}

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
    val n        = 75 // TODO: n = 250 gives a stack overflow
    val expr     = largeNormalForm(n)
    expect(expr.exprCount == 6 * n + 3)
    // TODO: this creates a stack overflow, need to fix.
    //    expr.typeCheckAndBetaNormalize().unsafeGet
    val elapsed1 = elapsedNanos(cborRoundtrip1(expr))._2 / 1000000.0
    val elapsed2 = elapsedNanos(cborRoundtrip2(expr))._2 / 1000000.0
    println(s"cbor1 : $elapsed1 ms, cbor2 : $elapsed2 ms")
    expect(elapsed1 > elapsed2 * 1.5) // cbor1 is about 2x slower than cbor2
  }

  test("beta-normalizing performance") {
    val n        = 75
    val expr     = largeNormalForm(n)
    expect(expr.exprCount == 6 * n + 3)
    // TODO: fix stack overflow
    val elapsed1 = elapsedNanos(expr.typeCheckAndBetaNormalize().unsafeGet)._2 / 1000000.0
    println(s"beta-normalizing expression of length $n takes $elapsed1 ms")
  }

  test("no more stack overflow in Scala MurmurHash") {
    val n         = 50000
    val delta     = 1000
    val lastGoodN = (1 to n by delta).map { i =>
      println(s"Iteration $i")
      val expr  = largeNormalForm(i) // This will throw an exception if we fail to produce that expression due to stack overflow.
      val count = expr.exprCount     // This should not throw any exceptions either.
      val hash = expr.hashCode()
      i
    }.last
    expect(lastGoodN > n - delta)
  }
}
