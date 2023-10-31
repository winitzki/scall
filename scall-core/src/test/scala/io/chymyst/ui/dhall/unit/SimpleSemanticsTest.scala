package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.ui.dhall.{Parser, Semantics}
import io.chymyst.ui.dhall.Syntax.Expression._
import io.chymyst.ui.dhall.Syntax.ExpressionScheme.{Variable, underscore}
import io.chymyst.ui.dhall.SyntaxConstants.Builtin.Natural
import io.chymyst.ui.dhall.SyntaxConstants.VarName
import munit.FunSuite

class SimpleSemanticsTest extends FunSuite {

  test("substitute in a variable") {
    val variable =  v("x")
    val result = Semantics.substitute(variable, VarName("x"), 0, Variable(underscore, 0))
    expect(result.toDhall == "_@0")
  }

  test("substitute in a lambda") {
    val lam = (v("y") | ~Natural) -> v("x")
    val result = Semantics.substitute(lam, VarName("x"), 0, Variable(underscore, 0))
    expect(result.toDhall == "\\(y : Natural) -> _@0")
  }

  test("alpha-normalize a nested lambda") {
    val nested = (v("x") | ~Natural) -> ((v("y") | ~Natural) -> v("x"))
    expect(nested.toDhall == "\\(x : Natural) -> \\(y : Natural) -> x@0")
    expect(nested.alphaNormalized.toDhall == "\\(_ : Natural) -> \\(_ : Natural) -> _@1")
  }

  test ("alpha-normalize record access") {
    val dhall = "{ x = \"foo\" }.x"
    val expr = Parser.parseDhall(dhall).get.value.value
    val exprN = expr.betaNormalized
    expect(exprN.toDhall == "\"foo\"")
  }
}
