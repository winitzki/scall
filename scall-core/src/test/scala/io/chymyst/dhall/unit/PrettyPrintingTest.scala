package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
class PrettyPrintingTest extends DhallTest {

  test("print Dhall expressions") {
    TestFixtures.prettyPrintingExamples.foreach { case (a, b) =>
      expect(a.dhall.print == b)
    }
  }

  test("print assert expressions") {
    expect("let x=1===1 in assert: x".dhall.typeCheckAndBetaNormalize().unsafeGet.print == "assert : 1 ≡ 1")
  }

  test("print long expressions without stack overflow") {
    (1 to 1000 by 50).foreach { n =>
      println(s"Iteration $n")
      expect(("1" + " + 1" * n).dhall.print.length == 4 * n + 1)
    }
  }
}
