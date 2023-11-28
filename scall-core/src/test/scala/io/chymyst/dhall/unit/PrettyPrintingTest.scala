package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
class PrettyPrintingTest extends DhallTest {

  val exprs: Seq[(String, String)] = Seq(
    ("1 + 2", "1 + 2"),
    ("1 + 2 * 3", "1 + 2 * 3"),
    ("1 * 2 + 3", "1 * 2 + 3"),
    ("(1 + 2) * 3", "(1 + 2) * 3"),
    ("(1 + 2) + 3", "1 + 2 + 3"),
    ("1 + (2 + 3)", "1 + 2 + 3"),
    ("\\(x : A) -> (x + 1) * 2", "λ(x : A) -> (x + 1) * 2"),
    ("\\(x : A) -> Natural/isZero (x + 1) * 2", "λ(x : A) -> Natural/isZero (x + 1) * 2"),
    ("\\(x : A) -> Natural/isZero ((x + 1) * 2)", "λ(x : A) -> Natural/isZero ((x + 1) * 2)"),
    ("\\(x : A) -> Natural/isZero (x + 1)", "λ(x : A) -> Natural/isZero (x + 1)"),
    ("\\(x : A) -> Natural/isZero x + 1", "λ(x : A) -> Natural/isZero x + 1"),
    ("((if (Natural/isZero (1 + 1)) then Natural/even else Natural/odd) 2)", "(if Natural/isZero (1 + 1) then Natural/even else Natural/odd) 2"),
  )

  test("print Dhall expressions") {
    exprs.foreach { case (a, b) =>
      expect(a.dhall.toDhall == b)
    }
  }
}