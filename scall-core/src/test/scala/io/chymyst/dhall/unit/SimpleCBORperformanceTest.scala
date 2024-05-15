package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import com.upokecenter.cbor.CBORObject
import io.chymyst.dhall.CBORmodel.{CDouble, CMap, CString, CTagged}
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.Expression
import io.chymyst.dhall.Syntax.Expression.v
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.SyntaxConstants.Builtin.{Natural, NaturalFold}
import io.chymyst.dhall.unit.SimpleCBORperformanceTest.{cborRoundtrip1, cborRoundtrip2}
import io.chymyst.dhall.unit.SimpleCBORtest.cborRoundtrip
import io.chymyst.dhall.{CBOR, CBORmodel, Grammar}

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

  // λ(x : Natural) → Natural/fold x Natural () 1
  def largeNormalForm(size: Int): Expression = if (size == 0)
    "λ(x : Natural) → x + 1".dhall
  else {
    (v("x") | ~Natural) -> (~NaturalFold)(v("x"))(~Natural)(largeNormalForm(size - 1))(v("x") + NaturalLiteral(1))
  }

  // s"λ(x : Natural) → Natural/fold x Natural (${largeNormalForm(size - 1)}) (x + 1)")

  test("produce an expression with a large normal form") {
    val n        = 150 // TODO: n = 250 gives a stack overflow
    val expr     = largeNormalForm(n)
    expect(expr.exprCount == 6 * n + 3)
    // TODO: this takes an exponentially long time! Need to fix.
    //    expr.typeCheckAndBetaNormalize().unsafeGet
    val elapsed1 = elapsedNanos(cborRoundtrip1(expr))._2 / 1000000.0
    val elapsed2 = elapsedNanos(cborRoundtrip2(expr))._2 / 1000000.0
    println(s"$elapsed1 ms, $elapsed2 ms")
  }
}
