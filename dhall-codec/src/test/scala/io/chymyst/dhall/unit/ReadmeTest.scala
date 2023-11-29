package io.chymyst.dhall.unit

import munit.FunSuite

class ReadmeTest extends FunSuite {

  test("simple examples from readme") {
    import io.chymyst.dhall.Parser.StringAsDhallExpression
    import io.chymyst.dhall.codec.FromDhall.DhallExpressionAsScala

    val a = "Natural/odd 123".dhall.typeCheckAndBetaNormalize().unsafeGet.asScala[Boolean]

    assert(a == true)

    val b = "1 + 2".dhall.typeCheckAndBetaNormalize().unsafeGet.asScala[BigInt]

    assert(b == 3)
  }

  test("factorial example from readme") {
    import io.chymyst.dhall.Parser.StringAsDhallExpression
    import io.chymyst.dhall.codec.FromDhall.DhallExpressionAsScala
    import io.chymyst.dhall.Syntax.Expression
    val factorial: Expression =
      """
        |\(x: Natural) ->
        |  let t = {acc: Natural, count: Natural}
        |  let result = Natural/fold x t (\(x: t) -> {acc = x.acc * x.count, count = x.count + 1} ) {acc = 1, count = 1}
        |    in result.acc
        """.stripMargin.dhall.betaNormalized

    assert(
      factorial.toDhall ==
        """
        |λ(x : Natural) → (Natural/fold x { acc : Natural, count : Natural } (λ(x : { acc : Natural, count : Natural }) → { acc = x.acc * x.count, count = x.count + 1 }) { acc = 1, count = 1 }).acc
        |""".stripMargin.trim
    )

    val ten: Expression = "10".dhall

    val tenFactorial: BigInt = factorial(ten).betaNormalized.asScala[BigInt]

    assert(tenFactorial == BigInt(3628800))
  }
}
