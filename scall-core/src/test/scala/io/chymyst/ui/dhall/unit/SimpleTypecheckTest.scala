package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.ui.dhall.Parser
import io.chymyst.ui.dhall.Syntax.Expression
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.SyntaxConstants.{Builtin, FieldName}
import io.chymyst.ui.dhall.TypeCheckResult.Valid
import munit.FunSuite

class SimpleTypecheckTest extends FunSuite {
  test("typecheck record of types") {
    val input = "{ x = 1, y = +2 }"
    expect(Parser.parseDhall(input).get.value.value.inferType == Valid(Expression(RecordType(List((FieldName("x"), Expression(ExprBuiltin(Builtin.Natural))), (FieldName("y"), Expression(ExprBuiltin(Builtin.Integer))))))))
  }
}
