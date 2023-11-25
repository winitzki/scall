package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.Expression._
import io.chymyst.dhall.Syntax.ExpressionScheme.{Variable, underscore}
import io.chymyst.dhall.SyntaxConstants.Builtin.Natural
import io.chymyst.dhall.SyntaxConstants.{Builtin, VarName}
import io.chymyst.dhall.unit.TestUtils.{DhallTest, UsingCaches}
import io.chymyst.dhall.{Parser, Semantics, TypecheckResult}
import munit.FunSuite

class SimpleSemanticsTest extends DhallTest {

  test("substitute in a variable") {
    val variable = v("x")
    val result   = Semantics.substitute(variable, VarName("x"), 0, Variable(underscore, 0))
    expect(result.toDhall == "_")
  }

  test("substitute in a lambda") {
    val lam    = (v("y") | ~Natural) -> v("x")
    val result = Semantics.substitute(lam, VarName("x"), 0, Variable(underscore, 0))
    expect(result.toDhall == "λ(y: Natural) -> _")
  }

  test("alpha-normalize a nested lambda") {
    val nested = (v("x") | ~Natural) -> ((v("y") | ~Natural) -> v("x"))
    expect(nested.toDhall == "λ(x: Natural) -> λ(y: Natural) -> x")
    expect(nested.alphaNormalized.toDhall == "λ(_: Natural) -> λ(_: Natural) -> _@1")
  }

  test("alpha-normalize record access") {
    val dhall = "{ x = \"foo\" }.x"
    val expr  = Parser.parseDhall(dhall).get.value.value
    val exprN = expr.betaNormalized
    expect(exprN.toDhall == "\"foo\"")
  }

  test("correct precedence for imports with fallback") {
    val dhall = "./import1 ? ./import2"
    val expr  = Parser.parseDhall(dhall).get.value.value
    expect(expr.toDhall == "./import1 ? ./import2")
  }

  test("beta-normalize with unique subexpressions") {
    """let enumerate
       |    : Natural → List Natural
       |    = λ(n : Natural) →
       |        List/build
       |          Natural
       |          ( λ(list : Type) →
       |            λ(cons : Natural → list → list) →
       |              List/fold
       |                { index : Natural, value : {} }
       |                ( List/indexed
       |                    {}
       |                    ( List/build
       |                        {}
       |                        ( λ(list : Type) →
       |                          λ(cons : {} → list → list) →
       |                            Natural/fold n list (cons {=})
       |                        )
       |                    )
       |                )
       |                list
       |                (λ(x : { index : Natural, value : {} }) → cons x.index)
       |          )
       |
       |let example0 = assert : enumerate 10 ≡ [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 ]
       |
       |let example1 = assert : enumerate 0 ≡ ([] : List Natural)
       |
       |in  enumerate
       |""".stripMargin.dhall.betaNormalized
  }

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

    expect(expr.inferType == TypecheckResult.Valid((~Builtin.List)((~Builtin.Optional)(~Natural))))
    expect(expr.betaNormalized.toDhall == "[(Some 0), (None Natural)]")
  }
}
