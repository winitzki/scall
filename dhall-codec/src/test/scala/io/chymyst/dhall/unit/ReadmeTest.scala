package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import munit.FunSuite

class ReadmeTest extends FunSuite {

  test("simple examples from readme") {
    import io.chymyst.dhall.Parser.StringAsDhallExpression
    import io.chymyst.dhall.codec.FromDhall.DhallExpressionAsScala

    val a = "Natural/odd 123".dhall.typeCheckAndBetaNormalize().unsafeGet.asScala[Boolean]

    expect(a == true)

    val b = "1 + 2".dhall.typeCheckAndBetaNormalize().unsafeGet.asScala[BigInt]

    expect(b == 3)
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

    expect(
      factorial.print ==
        """
        |λ(x : Natural) → (Natural/fold x { acc : Natural, count : Natural } (λ(x : { acc : Natural, count : Natural }) → { acc = x.acc * x.count, count = x.count + 1 }) { acc = 1, count = 1 }).acc
        |""".stripMargin.trim
    )

    val ten: Expression = "10".dhall

    val tenFactorial: Expression = factorial(ten)

    expect(tenFactorial.betaNormalized.asScala[BigInt] == BigInt(3628800))
  }

  test("ill-typed example from readme") {
    import io.chymyst.dhall.Parser.StringAsDhallExpression

    // Curry's Y combinator. We set the `Bool` type arbitrarily; the types do not match.
    val illTyped = """\(f : Bool) -> let p = (\(x : Bool) -> f x x) in p p""".dhall
    val argument = """\(x: Bool) -> x""".dhall

    val bad = illTyped(argument)
    // These expressions fail type-checking.
    expect(illTyped.typeCheckAndBetaNormalize().isValid == false)
    expect(bad.typeCheckAndBetaNormalize().isValid == false)

    // If we try evaluating `bad` without type-checking, we will get an infinite loop.
    val result =
      try bad.betaNormalized
      catch {
        case e: Throwable => e.toString
      }
    expect(result == "java.lang.StackOverflowError")
  }

  test("Scala factorial example from readme") {
    import io.chymyst.dhall.Parser.StringAsDhallExpression
    import io.chymyst.dhall.codec.FromDhall.DhallExpressionAsScala

    val factorialDhall = """
                                         |\(x: Natural) ->
                                         |  let t = {acc: Natural, count: Natural}
                                         |  let result = Natural/fold x t (\(x: t) -> {acc = x.acc * x.count, count = x.count + 1} ) {acc = 1, count = 1}
                                         |    in result.acc
        """.stripMargin.dhall.betaNormalized
    // TODO enable this test
//    val factorial: BigInt => BigInt = factorialDhall.asScala[BigInt => BigInt]
//    assert(factorial(BigInt(10)) == BigInt(3628800))
  }
}
