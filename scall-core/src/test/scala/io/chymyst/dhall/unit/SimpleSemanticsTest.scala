package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.Expression._
import io.chymyst.dhall.Syntax.ExpressionScheme.{Variable, underscore}
import io.chymyst.dhall.SyntaxConstants.Builtin.Natural
import io.chymyst.dhall.SyntaxConstants.VarName
import io.chymyst.dhall.{Parser, Semantics, TypecheckResult}

import scala.util.Try

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

  test("alpha-normalization and beta-normalization should refuse imports") {
    expect(Try("./import1".dhall.alphaNormalized).failed.get.getMessage contains "Unresolved imports cannot be alpha-normalized")
    expect(Try("./import1".dhall.betaNormalized).failed.get.getMessage contains "Unresolved import in ./import1 cannot be beta-normalized")
    expect(Try("./import1 ? ./import2".dhall.alphaNormalized).failed.get.getMessage contains "Unresolved imports cannot be alpha-normalized")
    expect(
      Try(
        "./import1 ? ./import2".dhall.betaNormalized
      ).failed.get.getMessage contains "Unresolved import alternative in ./import1 ? ./import2 cannot be beta-normalized"
    )
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
       |""".stripMargin.dhall.typeCheckAndBetaNormalize()
  }

  test("shortcut in Natural/fold if the result no longer changes") {
    val result = """
                   |( \(y: Natural) -> Natural/fold y Natural (\(x: Natural) -> x) 0 ) 500000000000000000
                   |""".stripMargin.dhall.typeCheckAndBetaNormalize()
    expect(result.unsafeGet.print == "0")
  }

  test("shortcut in Natural/fold if the result no longer changes, with symbolic lambda") {
    val result = """
                   |( \(x: Natural) -> \(y: Natural) -> Natural/fold x Natural (\(x: Natural) -> x) y ) 50000000000000000000000000
                   |""".stripMargin.dhall.typeCheckAndBetaNormalize()
    expect(result.unsafeGet.print == "λ(y : Natural) → y")
  }

  test("avoid expanding Natural/fold when the result grows as a symbolic expression") {
    val result = """
                   |( \(y: Natural) -> Natural/fold 10000000000000000000000000000 Natural (\(x: Natural) -> x + 1) y )
                   |""".stripMargin.dhall.typeCheckAndBetaNormalize()
    expect(result.unsafeGet.print contains "Natural/fold 9999999999999999999999999501 Natural")
//    expect(result.unsafeGet.print == "λ(y : Natural) → Natural/fold 10000000000000000000000000000 Natural (λ(x : Natural) → x + 1) y")
  }

  test("compute expression count") {
    expect("1".dhall.exprCount == 1)
    expect("1 + 1".dhall.exprCount == 2)
    expect("""\(y: Natural) -> Natural/fold 10 Natural (\(x: Natural) -> x + 1) y""".dhall.exprCount == 8)
  }

  test("foldWhile performance test with bitLength") {
    val result = """
      |-- Helpers from Prelude/Natural.
      |let iterations = 1000 -- Should not be slow even with many iterations.
      |
      |let Natural/lessThanEqual
      |    : Natural → Natural → Bool
      |    = λ(x : Natural) → λ(y : Natural) → Natural/isZero (Natural/subtract y x)
      |
      |let example = assert : Natural/lessThanEqual 5 6 ≡ True
      |let example = assert : Natural/lessThanEqual 5 5 ≡ True
      |let example = assert : Natural/lessThanEqual 5 4 ≡ False
      |
      |let Natural/lessThan
      |    : Natural → Natural → Bool
      |    = λ(a : Natural) → λ(b : Natural) → Natural/lessThanEqual a b && Natural/lessThanEqual b a == False
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
      |let example = assert : foldWhile iterations Natural (\(x: Natural) -> if Natural/lessThan x 3 then None Natural else Some (Natural/subtract 1 x)) 5 === 2
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

  // Prohibit division by 0 statically.
  test("safe division using dependent types") {
    val result =
      """
        |let Void: Type = ∀(x: Type) -> x
        |let absurd = \(x: Type) -> \(v: Void) -> v x
        |
        |let Nonzero: Natural -> Type = \(y: Natural) -> if Natural/isZero y then Void else {}
        |
        |let Natural/lessThanEqual
        |    : Natural → Natural → Bool
        |    = λ(x : Natural) → λ(y : Natural) → Natural/isZero (Natural/subtract y x)
        |let Natural/lessThan
        |    : Natural → Natural → Bool
        |    = λ(a : Natural) → λ(b : Natural) → Natural/lessThanEqual a b && Natural/lessThanEqual b a == False
        |
        | -- unsafeDiv y x means x / y
        |let unsafeDiv : Natural -> Natural -> Natural =
        |    let Acc = {result: Natural, sub: Natural, done: Bool}
        |    in \(y: Natural) -> \(x: Natural) ->
        |         let r: Acc = Natural/fold x Acc (\(acc: Acc) ->
        |             if acc.done then acc
        |             else if Natural/lessThan acc.sub y then acc // {done = True}
        |             else acc // {result = acc.result + 1, sub = Natural/subtract y acc.sub}) {result = 0, sub = x, done = False}
        |         in r.result
        |
        |let example = assert : unsafeDiv 2 4 === 2
        |let example = assert : unsafeDiv 2 3 === 1
        |let example = assert : unsafeDiv 2 2 === 1
        |let example = assert : unsafeDiv 3 2 === 0
        |let example = assert : unsafeDiv 0 2 === 2 -- The answer is wrong, because it is assumed that we will never divide by zero.
        |
        |let safeDiv = \(y: Natural) -> \(x: Natural) -> \(_: Nonzero y) -> unsafeDiv y x
        |
        |    in [ safeDiv 2 4 {=}, safeDiv 2 3 {=}, safeDiv 2 2 {=}, safeDiv 3 2 {=} ]
        |""".stripMargin.dhall.typeCheckAndBetaNormalize().unsafeGet
    expect(result.print == "[2, 1, 1, 0]")
  }

  test("safe division using assert") {
    val result =
      """
        |let Natural/lessThanEqual
        |    : Natural → Natural → Bool
        |    = λ(x : Natural) → λ(y : Natural) → Natural/isZero (Natural/subtract y x)
        |let Natural/lessThan
        |    : Natural → Natural → Bool
        |    = λ(a : Natural) → λ(b : Natural) → Natural/lessThanEqual a b && Natural/lessThanEqual b a == False
        |
        | -- unsafeDiv y x means x / y
        |let unsafeDiv : Natural -> Natural -> Natural =
        |    let Acc = {result: Natural, sub: Natural, done: Bool}
        |    in \(y: Natural) -> \(x: Natural) ->
        |         let init: Acc = {result = 0, sub = x, done = False}
        |         let update: Acc -> Acc = \(acc: Acc) ->
        |             if acc.done then acc
        |             else if Natural/lessThan acc.sub y then acc // {done = True}
        |             else acc // {result = acc.result + 1, sub = Natural/subtract y acc.sub}
        |           in (Natural/fold x Acc update init).result
        |
        |let example = assert : unsafeDiv 2 4 === 2
        |let example = assert : unsafeDiv 2 3 === 1
        |let example = assert : unsafeDiv 2 2 === 1
        |let example = assert : unsafeDiv 3 2 === 0
        |let example = assert : unsafeDiv 0 2 === 2 -- The answer is wrong, because it is assumed that we will never divide by zero.
        |
        |let safeDiv = \(y: Natural) -> \(x: Natural) ->
        |    let _ = assert : Natural/isZero y === False -- this does not work!
        |    in unsafeDiv y x
        |
        |    in True
        |""".stripMargin.dhall.typeCheckAndBetaNormalize()
    expect(result match {
      case TypecheckResult.Invalid(errors) => errors exists (_ contains "Expression `assert` failed: Unequal sides in Natural/isZero y ≡ False")
    })
  }

  test("beta-normalization with Natural/fold and shortcut") {
    val input =
      """(λ(n : Natural) → List/fold { index : Natural, value : {} } (List/indexed {} (Natural/fold n (List {}) (λ(`as` : List {}) → ([{=}]) # `as`) ([] : List {}))) (List Natural) (λ(x : { index : Natural, value : {} }) → λ(`as` : List Natural) → ([x.index]) # `as`) ([] : List Natural)) 10""".dhall
    expect(input.betaNormalized.print == "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]")
    expect(
      input.alphaNormalized.print == """(λ(_ : Natural) → List/fold { index : Natural, value : {} } (List/indexed {} (Natural/fold _ (List {}) (λ(_ : List {}) → [{=}] # _) ([] : List {}))) (List Natural) (λ(_ : { index : Natural, value : {} }) → λ(_ : List Natural) → [_@1.index] # _) ([] : List Natural)) 10"""
    )
    expect(input.alphaNormalized.betaNormalized.print == "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]")
  }

  test("beta-normalization for appended lists") {
    Map(
      """\(x: List Bool) -> List/length Bool ([ True ] # x)"""       -> "λ(x : List Bool) → 1 + List/length Bool x",
      """\(x: List Bool) -> List/head Bool ([ True ] # x)"""         -> "λ(x : List Bool) → Some True",
      """\(x: List Bool) -> List/head Bool (([] : List Bool) # x)""" -> "λ(x : List Bool) → List/head Bool x",
      """\(x: List Bool) -> List/last Bool (x # [ True ])"""         -> "λ(x : List Bool) → Some True",
      """\(x: List Bool) -> List/last Bool (x # ([] : List Bool))""" -> "λ(x : List Bool) → List/last Bool x",
    ).foreach { case (input, output) =>
      val normalized = input.dhall.betaNormalized
      expect(normalized.print == output)
      expect(input.dhall.typeCheckAndBetaNormalize().unsafeGet == normalized)
    }
  }

  test("Text/replace various cases") {
    Map(
      """ Text/replace "abc" "def" "abcxyzabc" """           -> """"defxyzdef"""",
      """ Text/replace "abc" "def" "xyzabc" """              -> """"xyzdef"""",
      """ Text/replace "abc" "def" "abcxyz" """              -> """"defxyz"""",
      """ Text/replace "abc" "def" "abc" """                 -> """"def"""",
      """ Text/replace "abc" "def" "xyz" """                 -> """"xyz"""",
      """ Text/replace "" "def" "xyzabc" """                 -> """"xyzabc"""",
      """\(x: Text) -> \(y: Text) -> Text/replace "" x y """ -> """λ(x : Text) → λ(y : Text) → y""",
      """\(x: Text) -> \(y: Text) -> Text/replace x y "" """ -> """λ(x : Text) → λ(y : Text) → """"",
    ).foreach { case (input, output) =>
      expect(input.dhall.typeCheckAndBetaNormalize().unsafeGet.print == output)
    }
  }

  test("invalid field name is an error") {
    expect(
      Try(
        "{x = 1}.y".dhall.betaNormalized
      ).failed.get.getMessage contains "Record access in { x = 1 }.y has invalid field name (y), which should be one of the record literal's fields: (x)"
    )
    expect(
      Try(
        "{x = 1}.y".dhall.typeCheckAndBetaNormalize().unsafeGet
      ).failed.get.getMessage == "Type-checking failed with errors: List(In field selection, the record type with field names (x) does not contain field name (y), type inference context = {})"
    )
    expect(
      Try(
        "{x : Bool}.y".dhall.betaNormalized
      ).failed.get.getMessage contains "Record access in { x : Bool }.y has invalid field name (y), which should be one of the record type's fields: (x)"
    )
    expect(
      Try(
        "{x : Bool}.y".dhall.typeCheckAndBetaNormalize().unsafeGet
      ).failed.get.getMessage == "Type-checking failed with errors: List(Record type with field names (x) does not contain field name (y), type inference context = {})"
    )
  }
}
