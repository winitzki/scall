package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax

class StressTest extends DhallTest {

  def measure(generate: Int => String, max: Int, by: Int) = {
    (1 to max by by).foreach { n =>
      val (parsed, elapsedParsing)         = elapsedNanos(generate(n).dhall)
      println(f"Iteration $n\tparsing elapsed ${elapsedParsing / 1000000000.0}%.2f s")
      val (printed, elapsedPrinting)       = elapsedNanos(parsed.print)
      println(f"Iteration $n\t\tprinted elapsed ${elapsedPrinting / 1000000000.0}%.2f s")
      val (normalized, elapsedNormalizing) = elapsedNanos(parsed.betaNormalized)
      println(f"Iteration $n\t\t\tnormalized elapsed ${elapsedNormalizing / 1000000000.0}%.2f s")
    }
  }

  test("print long expressions without stack overflow, without parentheses") {

    // "1 + 1 + 1 + 1 + ... + 1"
    def assocPlus(n: Int): String = "1" + " + 1" * n

    expect(assocPlus(1) == "1 + 1")
    expect(assocPlus(3) == "1 + 1 + 1 + 1")

    measure(assocPlus, 3000, 50)
  }

  test("print long expressions without stack overflow, with parentheses") {
    // "((...(1 + 1) + 1) + ...) + 1"
    def leftAssocPlus(n: Int): String = "(" * (n - 1) + "1" + " + 1)" * (n - 1) + " + 1"

    expect(leftAssocPlus(1) == "1 + 1")
    expect(leftAssocPlus(3) == "((1 + 1) + 1) + 1")

    // TODO: fix the parser so that parsing this expression does not become exponentially slow
    // TODO fix the stack overflow while printing and/or while parsing
    measure(leftAssocPlus, 12, 2)
  }

  test("print long expressions without stack overflow, right-associated with parentheses") {
    // "1 + (1 + (... + 1))...)"
    def rightAssocPlus(n: Int): String = "1 + (" * (n - 1) + "1 + 1" + ")" * (n - 1)

    expect(rightAssocPlus(1) == "1 + 1")
    expect(rightAssocPlus(3) == "1 + (1 + (1 + 1))")

    // TODO: fix the parser so that parsing this expression does not become exponentially slow
    // TODO fix the stack overflow while printing and/or while parsing
    measure(rightAssocPlus, 12, 2)
  }

  test("print with the new tail-recursive function print1") {
    expect(Syntax.print1("1 + 1".dhall) == "1 + 1")
    expect(Syntax.print1("1 + (1 + 1)".dhall) == "1 + 1 + 1")
  }

  test("print1 long expressions without stack overflow, without parentheses") {
    // TODO fix the stack overflow while printing and/or while parsing
    // "1 + 1 + 1 + 1 + ... + 1"
    def assocPlus(n: Int): String = "1" + " + 1" * n

    expect(assocPlus(1) == "1 + 1")
    expect(assocPlus(3) == "1 + 1 + 1 + 1")

    measure(assocPlus, 3500, 100) // Stack overflow in beta-normalizing due to murmurhash, unless we use smaller step than 200.
  }

  test("deeply nested lists") {
    val generate = { n: Int => "[" * n + "1" + "]" * n }
    expect(generate(1) == "[1]")
    expect(generate(3) == "[[[1]]]")
    measure(generate, 12, 2)
  }

  test("deeply nested parentheses") {
    val generate = { n: Int => "(" * n + "1" + ")" * n }
    expect(generate(1) == "(1)")
    expect(generate(3) == "(((1)))")
    measure(generate, 12, 2)
  }

  test("deeply nested records") {
    val generate = { n: Int => "{x = " * n + "1" + "}" * n }
    expect(generate(1) == "{x = 1}")
    expect(generate(3) == "{x = {x = {x = 1}}}")
    measure(generate, 12, 2)
  }

  test("deeply nested record types") {
    val generate = { n: Int => "{x : " * n + "Bool" + "}" * n }
    expect(generate(1) == "{x : Bool}")
    expect(generate(3) == "{x : {x : {x : Bool}}}")
    measure(generate, 12, 2)
  }

  test("deeply nested applications") {
    val generate = { n: Int => "List " + "(List " * (n - 1) + "Bool" + ")" * (n - 1) }
    expect(generate(1) == "List Bool")
    expect(generate(3) == "List (List (List Bool))")
    measure(generate, 12, 2)
  }

  test("deeply nested applications under lambda") {
    val generate = { n: Int => "\\(x: Bool -> Bool) -> x " + "(x " * (n - 1) + "True" + ")" * (n - 1) }
    expect(generate(1) == "\\(x: Bool -> Bool) -> x True")
    expect(generate(3) == "\\(x: Bool -> Bool) -> x (x (x True))")
    measure(generate, 12, 2)
  }

  test("deeply nested lambdas") {
    val generate = { n: Int => "\\(x: Bool) -> " * n + "x" }
    expect(generate(1) == "\\(x: Bool) -> x")
    expect(generate(3) == "\\(x: Bool) -> \\(x: Bool) -> \\(x: Bool) -> x")
    measure(generate, 12, 2)
  }

}
