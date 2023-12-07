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
    import io.chymyst.dhall.Syntax.Expression
    import io.chymyst.dhall.codec.FromDhall.DhallExpressionAsScala
    val factorial: Expression =
      """
        |\(x: Natural) ->
        |  let t = {acc: Natural, count: Natural}
        |  let result = Natural/fold x t (\(x: t) -> {acc = x.acc * x.count, count = x.count + 1} ) {acc = 1, count = 1}
        |    in result.acc
        """.stripMargin.dhall.betaNormalized

    assert(
      factorial.print ==
        """
        |λ(x : Natural) → (Natural/fold x { acc : Natural, count : Natural } (λ(x : { acc : Natural, count : Natural }) → { acc = x.acc * x.count, count = x.count + 1 }) { acc = 1, count = 1 }).acc
        |""".stripMargin.trim
    )

    val ten: Expression = "10".dhall

    val tenFactorial: Expression = factorial(ten)

    assert(tenFactorial.betaNormalized.asScala[BigInt] == BigInt(3628800))
  }

  test("ill-typed example from readme") {
    import io.chymyst.dhall.Parser.StringAsDhallExpression

    // Curry's Y combinator. We set the `Bool` type arbitrarily; the types do not match.
    val illTyped = """\(f : Bool) -> let p = (\(x : Bool) -> f x x) in p p""".dhall
    val argument = """\(x: Bool) -> x""".dhall

    val bad = illTyped(argument)
    // These expressions fail type-checking.
    assert(illTyped.typeCheckAndBetaNormalize().isValid == false)
    assert(bad.typeCheckAndBetaNormalize().isValid == false)

    // If we try evaluating `bad` without type-checking, we will get an infinite loop.
    val result: String =
      try bad.betaNormalized.print
      catch {
        case e: Throwable => e.toString
      }
    assert(result == "java.lang.StackOverflowError")
  }
}
