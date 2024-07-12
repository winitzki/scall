package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.SyntaxConstants.Builtin
import io.chymyst.dhall.TypecheckResult

import scala.util.Try

class DoNotationTest extends DhallTest {
  test("do notation") {
    val expr =
      """
        |let fold
        |    : ∀(a : Type) →
        |      Optional a →
        |      ∀(optional : Type) →
        |      ∀(some : a → optional) →
        |      ∀(none : optional) →
        |        optional
        |    = λ(a : Type) →
        |      λ(o : Optional a) →
        |      λ(optional : Type) →
        |      λ(some : a → optional) →
        |      λ(none : optional) →
        |        merge { Some = some, None = none } o
        |in
        |
        |let bind
        |    : ∀(a: Type) -> ∀(b: Type) -> ∀(_: Optional a) -> ∀(_: ∀(_: a) -> Optional b) -> Optional b
        |    = λ(a: Type)-> λ(b: Type) -> λ(x: Optional a) -> λ(f: a -> Optional b) -> fold a x (Optional b) f (None b)
        |in
        |
        |let subtract1Optional = λ(x : Natural) → if Natural/isZero x then None Natural else Some (Natural/subtract 1 x)
        |in
        |
        |
        |let subtract3Optional = λ(x : Natural) →
        |  as Optional Natural in bind
        |    with y : Natural in subtract1Optional x
        |    with z : Natural in subtract1Optional y
        |    then subtract1Optional z
        |in
        |
        |let _ = assert : subtract3Optional 10 === Some 7
        |let _ = assert : subtract3Optional 3 === Some 0
        |let _ = assert : subtract3Optional 2 === None Natural
        |let _ = assert : subtract3Optional 1 === None Natural
        |let _ = assert : subtract3Optional 0 === None Natural
        |in
        |
        |[ subtract3Optional 3, subtract3Optional 2]
        |""".stripMargin.dhall

    expect(expr.inferType == TypecheckResult.Valid((~Builtin.List)((~Builtin.Optional)(~Builtin.Natural))))
    expect(expr.betaNormalized.print == "[Some 0, None Natural]")
  }

  test("do notation having no `with` lines") {
    val expr =
      """
        |let fold
        |    : ∀(a : Type) →
        |      Optional a →
        |      ∀(optional : Type) →
        |      ∀(some : a → optional) →
        |      ∀(none : optional) →
        |        optional
        |    = λ(a : Type) →
        |      λ(o : Optional a) →
        |      λ(optional : Type) →
        |      λ(some : a → optional) →
        |      λ(none : optional) →
        |        merge { Some = some, None = none } o
        |in
        |
        |let bind
        |    : ∀(a: Type) -> ∀(b: Type) -> ∀(_: Optional a) -> ∀(_: ∀(_: a) -> Optional b) -> Optional b
        |    = λ(a: Type)-> λ(b: Type) -> λ(x: Optional a) -> λ(f: a -> Optional b) -> fold a x (Optional b) f (None b)
        |in
        |
        |let subtract1Optional = λ(x : Natural) → if Natural/isZero x then None Natural else Some (Natural/subtract 1 x)
        |in
        |
        |let subtract1aOptional = λ(x : Natural) →
        |  as Optional Natural in bind
        |    then subtract1Optional x
        |in
        |
        |let _ = assert : subtract1aOptional 10 === Some 9
        |let _ = assert : subtract1aOptional 3 === Some 2
        |let _ = assert : subtract1aOptional 2 === Some 1
        |let _ = assert : subtract1aOptional 1 === Some 0
        |let _ = assert : subtract1aOptional 0 === None Natural
        |in
        |
        |[ subtract1aOptional 1, subtract1aOptional 0]
        |""".stripMargin.dhall

    expect(expr.inferType == TypecheckResult.Valid((~Builtin.List)((~Builtin.Optional)(~Builtin.Natural))))
    expect(expr.betaNormalized.print == "[Some 0, None Natural]")
  }

  test("parse do notation correctly") {
    "as Optional Natural in if a then b else c then d".dhall                    // No test failures.
    "as Optional Natural in if a then b else c with x : Text in y then d".dhall // No test failures.
  }

  test("parse do notation and detect error 1") {
    expect(
      Try(
        "as Optional Natural in if a then b".dhall
      ).failed.get.getMessage contains "Dhall parser error: Expected complete_dhall_file:1:1 / complete_expression:1:1 / expression:1:1 / expression_as_in:1:1 / expression:1:24 / expression_if_then_else:1:24 / requireKeyword:1:35 / \"else\":1:35"
    )
  }

  test("parse do notation and detect error 2") {
    expect(
      Try(
        "as Optional Natural in if a then b else c".dhall
      ).failed.get.getMessage contains "Dhall parser error: Expected complete_dhall_file:1:1 / complete_expression:1:1 / expression:1:1 / expression_as_in:1:1 / requireKeyword:1:42 / \"then\":1:42"
    )
  }

}
