package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.Expression._
import io.chymyst.dhall.Syntax.ExpressionScheme.{Variable, underscore}
import io.chymyst.dhall.SyntaxConstants.Builtin.Natural
import io.chymyst.dhall.SyntaxConstants.{Builtin, VarName}
import io.chymyst.dhall.{Parser, Semantics, TypecheckResult}

class SimpleSemanticsTest extends DhallTest {

  test("substitute in a variable") {
    val variable = v("x")
    val result   = Semantics.substitute(variable, VarName("x"), 0, Variable(underscore, 0))
    expect(result.print == "_")
  }

  test("substitute in a lambda") {
    val lam    = (v("y") | ~Natural) -> v("x")
    val result = Semantics.substitute(lam, VarName("x"), 0, Variable(underscore, 0))
    expect(result.print == "λ(y : Natural) → _")
  }

  test("alpha-normalize a nested lambda") {
    val nested = (v("x") | ~Natural) -> ((v("y") | ~Natural) -> v("x"))
    expect(nested.print == "λ(x : Natural) → λ(y : Natural) → x")
    expect(nested.alphaNormalized.print == "λ(_ : Natural) → λ(_ : Natural) → _@1")
  }

  test("alpha-normalize record access") {
    val dhall = "{ x = \"foo\" }.x"
    val expr  = Parser.parseDhall(dhall).get.value.value
    val exprN = expr.betaNormalized
    expect(exprN.print == "\"foo\"")
  }

  test("correct precedence for imports with fallback") {
    val dhall = "./import1 ? ./import2"
    val expr  = Parser.parseDhall(dhall).get.value.value
    expect(expr.print == "./import1 ? ./import2")
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

  test("foldWhile performance test with bitLength") { // TODO: this should work with iterations = 1000. Try optimizing foldWhile and try implementing a lazy evaluation strategy.
    val result = """
      |-- Helpers from Prelude/Natural.
      |let Natural/lessThanEqual
      |    : Natural → Natural → Bool
      |    = λ(x : Natural) → λ(y : Natural) → Natural/isZero (Natural/subtract y x)
      |
      |let example = assert : Natural/lessThanEqual 5 6 ≡ True
      |let example = assert : Natural/lessThanEqual 5 5 ≡ True
      |let example = assert : Natural/lessThanEqual 5 4 ≡ False
      |
      |let Natural/equal
      |    : Natural → Natural → Bool
      |    = λ(a : Natural) → λ(b : Natural) → Natural/lessThanEqual a b && Natural/lessThanEqual b a
      |
      |let Natural/lessThan
      |    : Natural → Natural → Bool
      |    = λ(a : Natural) → λ(b : Natural) → Natural/lessThanEqual a b && Natural/equal a b == False
      |
      |let example = assert : Natural/lessThan 5 6 ≡ True
      |let example = assert : Natural/lessThan 5 5 ≡ False
      |let example = assert : Natural/lessThan 5 4 ≡ False
      |
      |-- Fold while an updater function returns a non-empty option, up to a given number of iterations.
      |let foldWhile: ∀(n: Natural) → ∀(res : Type) → ∀(succ : res → Optional res) → ∀(zero : res) → res =
      |    \(n: Natural) -> \(R: Type) -> \(succ: R -> Optional R) -> \(zero: R) ->
      |    let Acc: Type = { current: R, done: Bool }
      |    let update: Acc -> Acc = \(acc: Acc) -> if acc.done then acc else
      |    merge { Some = \(r: R) -> acc // {current = r}, None = acc // {done = True} } (succ acc.current)
      |    let init: Acc = { current = zero, done = False }
      |    let result: Acc = Natural/fold n Acc update init
      |    in
      |    result.current
      |
      |-- Subtract 1 from 5 until the result is below 3. Max 6 iterations. This becomes very slow at >= 8 iterations.
      |let example = let iterations = 6
      |    in assert : foldWhile iterations Natural (\(x: Natural) -> if Natural/lessThan x 3 then None Natural else Some (Natural/subtract 1 x)) 5 === 2
      |
      |-- Compute 1 + ceil(log2(n)) by counting how many times we need to multiply by 2 so that the result is >= n.
      |let log2 = \(n: Natural) ->
      |    let Acc = { result: Natural, bound: Natural }
      |    let foldResult = foldWhile n Acc (\(acc: Acc) ->
      |        if Natural/lessThan n acc.bound then None Acc else Some { result = acc.result + 1, bound = acc.bound * 2}
      |    ) { result = 0, bound = 1 }
      |       in foldResult.result
      |
      |    in [log2 0, log2 1, log2 2, log2 3, log2 4, log2 5]
      |""".stripMargin.dhall.typeCheckAndBetaNormalize().unsafeGet
    expect(result.print == "[0, 1, 2, 2, 3, 3]")
  }
}
