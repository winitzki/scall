package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
class PrettyPrintingTest extends DhallTest {

  val exprs: Seq[(String, String)] = Seq(
    ("\\(x : A) -> (x + 1) * 2", "λ(x : A) -> (x + 1) * 2"),
    // ("\\(x : A) -> Natural/isZero (x + 1) * 2", "λ(x : A) -> (Natural/isZero (x + 1)) * 2"),
    ("\\(x : A) -> Natural/isZero ((x + 1) * 2)", "λ(x : A) -> Natural/isZero ((x + 1) * 2)"),
    ("\\(x : A) -> Natural/isZero (x + 1)", "λ(x : A) -> Natural/isZero (x + 1)"),
    ("\\(x : A) -> Natural/isZero x + 1", "λ(x : A) -> Natural/isZero x + 1"),
  )

  test("print Dhall expressions") {
    exprs.foreach { case (a, b) =>
      expect(a.dhall.toDhall == b)
    }
  }
}
