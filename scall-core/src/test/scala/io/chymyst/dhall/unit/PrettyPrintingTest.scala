package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax
class PrettyPrintingTest extends DhallTest {

  test("print Dhall expressions") {
    TestFixtures.prettyPrintingExamples.foreach { case (a, b) =>
      expect(a.dhall.print == b)
    }
  }

  test("print assert expressions") {
    expect("let x=1===1 in assert: x".dhall.typeCheckAndBetaNormalize().unsafeGet.print == "assert : 1 ≡ 1")
  }

  test("print long expressions without stack overflow, without parentheses") {
    // TODO fix the stack overflow while printing and/or while parsing
    // "1 + 1 + 1 + 1 + ... + 1"
    def assocPlus(n: Int): String = "1" + " + 1" * n

    expect(assocPlus(1) == "1 + 1")
    expect(assocPlus(3) == "1 + 1 + 1 + 1")

    (1 to 3000 by 50).foreach { n =>
      val (parsed, elapsedParsing)   = elapsedNanos(assocPlus(n).dhall)
      println(f"Iteration $n, parsing elapsed ${elapsedParsing / 1000000000.0}%.2f s")
      val (printed, elapsedPrinting) = elapsedNanos(parsed.print)
      println(f"Iteration $n, printed elapsed ${elapsedPrinting / 1000000000.0}%.2f s")
      expect(elapsedParsing < 3 * 1000L * 1000L * 1000L) // Parsing must take less than 3 seconds.
      expect(printed.length == 4 * n + 1)
    }
  }

  test("print long expressions without stack overflow, with parentheses") {
    // "((...(1 + 1) + 1) + ...) + 1"
    def leftAssocPlus(n: Int): String = "(" * (n - 1) + "1" + " + 1)" * (n - 1) + " + 1"

    expect(leftAssocPlus(1) == "1 + 1")
    expect(leftAssocPlus(3) == "((1 + 1) + 1) + 1")

    // TODO: fix the parser so that parsing this expression does not become exponentially slow
    // TODO fix the stack overflow while printing and/or while parsing
    (1 to 30).foreach { n =>
      val (parsed, elapsedParsing)   = elapsedNanos(leftAssocPlus(n).dhall)
      val (printed, elapsedPrinting) = elapsedNanos(parsed.print)
      println(f"Iteration $n, parsing elapsed ${elapsedParsing / 1000000000.0}%.2f s, printed elapsed ${elapsedPrinting / 1000000000.0}%.2f s")
      expect(elapsedParsing < 3 * 1000L * 1000L * 1000L) // Parsing must take less than 3 seconds.
      expect(printed.length == 4 * n + 1)
    }
  }

  test("print long expressions without stack overflow, right-associated with parentheses") {
    // "1 + (1 + (... + 1))...)"
    def rightAssocPlus(n: Int): String = "1 + (" * (n - 1) + "1 + 1" + ")" * (n - 1)

    expect(rightAssocPlus(1) == "1 + 1")
    expect(rightAssocPlus(3) == "1 + (1 + (1 + 1))")

    // TODO: fix the parser so that parsing this expression does not become exponentially slow
    // TODO fix the stack overflow while printing and/or while parsing
    (1 to 30).foreach { n =>
      val (parsed, elapsedParsing)   = elapsedNanos(rightAssocPlus(n).dhall)
      val (printed, elapsedPrinting) = elapsedNanos(parsed.print)
      println(f"Iteration $n, parsing elapsed ${elapsedParsing / 1000000000.0}%.2f s, printed elapsed ${elapsedPrinting / 1000000000.0}%.2f s")
      expect(elapsedParsing < 3 * 1000L * 1000L * 1000L) // Parsing must take less than 3 seconds.
      expect(printed.length == 4 * n + 1)
    }
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

    (1 to 3000 by 50).foreach { n =>
      val (parsed, elapsedParsing)   = elapsedNanos(assocPlus(n).dhall)
      println(f"Iteration $n, parsing elapsed ${elapsedParsing / 1000000000.0}%.2f s")
      val (printed, elapsedPrinting) = elapsedNanos(Syntax.print1(parsed))
      println(f"Iteration $n, printed elapsed ${elapsedPrinting / 1000000000.0}%.2f s")
      expect(elapsedParsing < 3 * 1000L * 1000L * 1000L) // Parsing must take less than 3 seconds.
      expect(printed.length == 4 * n + 1)
    }
  }

}
