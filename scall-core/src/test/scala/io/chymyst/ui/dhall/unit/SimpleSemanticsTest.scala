package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.ui.dhall.Semantics
import munit.FunSuite
import io.chymyst.ui.dhall.Syntax.Expression._
import io.chymyst.ui.dhall.SyntaxConstants.Builtin.Natural

class SimpleSemanticsTest extends FunSuite {

  test("alpha-normalize a nested lambda") {
    val nested = (v("x") | ~Natural) -> ((v("y") | ~Natural) -> v("x"))
    expect(nested.toDhall == "\\(x : Natural) -> \\(y : Natural) -> x@0")
    expect(Semantics.alphaNormalize(nested).toDhall == "\\(_ : Natural) -> \\(_ : Natural) -> _@1")
  }
}
