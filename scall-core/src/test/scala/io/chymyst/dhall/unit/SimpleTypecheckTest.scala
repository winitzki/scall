package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.test.ResourceFiles.enumerateResourceFiles
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.Syntax.{DhallFile, Expression}
import io.chymyst.dhall.SyntaxConstants.{Builtin, ConstructorName, FieldName, VarName}
import io.chymyst.dhall.TypeCheck._Type
import io.chymyst.dhall.TypecheckResult.Valid
import io.chymyst.dhall.{Parser, TypecheckResult}
import munit.FunSuite

import java.io.FileInputStream

class SimpleTypecheckTest extends FunSuite {
  test("typecheck record of types") {
    val input = "{ x = 1, y = +2 }"
    expect(
      Parser.parseDhall(input).get.value.value.inferType == Valid(
        Expression(RecordType(List((FieldName("x"), Expression(ExprBuiltin(Builtin.Natural))), (FieldName("y"), Expression(ExprBuiltin(Builtin.Integer))))))
      )
    )
  }

  test("shift in type variable") {
    val input = "λ(t : Type) → λ(t : t) -> t"
    expect(Parser.parseDhall(input).get.value.value.inferType == Valid((~"t" | _Type) ->: (~"t" | ~"t") ->: Expression(Variable(VarName("t"), BigInt(1)))))
  }

  test("shift in type variable without pun") {
    val input = "λ(t : Type) → λ(x : t) -> t"
    expect(Parser.parseDhall(input).get.value.value.inferType == Valid((~"t" | _Type) ->: (~"x" | ~"t") ->: _Type))
  }

  test("shift in type variable with index 1 without generics") {
    val input = "λ(t : Text) → λ(t : Natural) -> t@1"
    expect(Parser.parseDhall(input).get.value.value.inferType == Valid((~"t" | ~Builtin.Text) ->: (~"t" | ~Builtin.Natural) ->: ~Builtin.Text))
  }

  test("shift in type variable with index 1 without pun") {
    val input = "λ(t : Type) → λ(t : Natural) -> t@1"
    expect(Parser.parseDhall(input).get.value.value.inferType == Valid((~"t" | _Type) ->: (~"t" | ~Builtin.Natural) ->: _Type))
  }

  test("shift in type variable with index 1 with pun") {
    val input = "λ(t : Type) → λ(t : t) -> t@1"
    expect(Parser.parseDhall(input).get.value.value.inferType == Valid((~"t" | _Type) ->: (~"t" | ~"t") ->: _Type))
  }

  test("shift in type variable with union constructor") {
    val input = "λ(t : Type) → <x : t>.x"
    expect(
      Parser.parseDhall(input).get.value.value.inferType == Valid(
        (~"t" | _Type) ->: (~"x" | ~"t") ->: Expression(UnionType(List((ConstructorName("x"), Some(~"t")))))
      )
    )
  }

  test("shift in type variable with union constructor and pun") {
    val input = "λ(t : Type) → <t : t>.t"
    expect(
      Parser.parseDhall(input).get.value.value.inferType == Valid(
        (~"t" | _Type) ->: (~"t" | ~"t") ->: Expression(UnionType(List((ConstructorName("t"), Some(Expression(Variable(VarName("t"), BigInt(1))))))))
      )
    )
  }

  test("simplify equivalence type") {
    val input = "(λ(g : Natural → Bool) → assert : g 0 ≡ g 0) Natural/even"
    expect(
      Parser.parseDhall(input).get.value.value.inferType.map(_.toDhall) == Valid("True ≡ True")
    ) // Valid((~Constant.True).op(Operator.Equivalent)(~Constant.True)))
  }

  test("type inference failure with RecordSelectionNotRecord.dhall") {
    enumerateResourceFiles("dhall-lang/tests/type-inference/failure", Some("RecordSelectionNotRecord.dhall")).foreach { file =>
      val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
      println(s"Parsed expression: ${ourResult.toDhall}")
      ourResult.inferType match {
        case TypecheckResult.Invalid(errors) =>
          expect(errors contains "Field selection in True.x must be for a record or a union, but instead found type Bool, type inference context = {}")
      }
    }
  }

}