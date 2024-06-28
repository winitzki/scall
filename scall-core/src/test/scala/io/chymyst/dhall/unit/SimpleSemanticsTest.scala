package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Semantics.BetaNormalizingOptions
import io.chymyst.dhall.Syntax.Expression
import io.chymyst.dhall.Syntax.Expression._
import io.chymyst.dhall.Syntax.ExpressionScheme.{ExprOperator, Variable, underscore}
import io.chymyst.dhall.SyntaxConstants.Builtin.Natural
import io.chymyst.dhall.SyntaxConstants.{Operator, VarName}
import io.chymyst.dhall.TypeCheck.KnownVars
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
    val result =
      """
        |( \(y: Natural) -> Natural/fold y Natural (\(x: Natural) -> x) 0 ) 500000000000000000
        |""".stripMargin.dhall.typeCheckAndBetaNormalize()
    expect(result.unsafeGet.print == "0")
  }

  test("shortcut in Natural/fold if the result no longer changes, with symbolic lambda") {
    val result =
      """
        |( \(x: Natural) -> \(y: Natural) -> Natural/fold x Natural (\(x: Natural) -> x) (y + 1) ) 50000000000000000000000000
        |""".stripMargin.dhall.typeCheckAndBetaNormalize()
    expect(result.unsafeGet.print == "λ(y : Natural) → y + 1")
  }

  test("shortcut in Natural/fold with a function 1") {
    val result =
      """
        |let f : Natural → Natural = λ(x : Natural) → if Natural/isZero x then 1 else x
        |in Natural/fold 10000000000000000000000000000 Natural f 0
        |""".stripMargin.dhall.typeCheckAndBetaNormalize()
    expect(result.unsafeGet.print == "1")
  }

  test("shortcut in Natural/fold with a function 2") {
    val result =
      """
        |let f : Natural → Natural = λ(x : Natural) → if Natural/isZero x then 1 else Natural/subtract x 2
        |in Natural/fold 10000000000000000000000000000 Natural f 0
        |""".stripMargin.dhall.typeCheckAndBetaNormalize()
    expect(result.unsafeGet.print == "1")
  }

  test("avoid expanding Natural/fold when the result grows as a symbolic expression") {
    val result =
      """
        |( \(y: Natural) -> Natural/fold 10000000000000000000000000000 Natural (\(x: Natural) -> x + 1) (y + 1) )
        |""".stripMargin.dhall.typeCheckAndBetaNormalize()
    expect(result.unsafeGet.print contains "Natural/fold 9999999999999999999999999502 Natural")
    //    expect(result.unsafeGet.print == "λ(y : Natural) → Natural/fold 10000000000000000000000000000 Natural (λ(x : Natural) → x + 1) (y + 1)")
  }

  test("compute expression count") {
    expect("1".dhall.exprCount == 1)
    expect("1 + 1".dhall.exprCount == 2)
    expect("""\(y: Natural) -> Natural/fold 10 Natural (\(x: Natural) -> x + 1) y""".dhall.exprCount == 8)
  }

  test("foldWhile performance test with bitLength") {
    val result =
      """
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
      case TypecheckResult.Invalid(errors) =>
        println(errors)
        errors contains "Expression `assert` failed: Unequal sides, Natural/isZero y does not equal False, in Natural/isZero y ≡ False, expression under type inference: assert : Natural/isZero y ≡ False, type inference context = {y : Natural, x : Natural}"
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
    Seq(
      """\(x: List Bool) -> List/head Bool (([] : List Bool) # x)""" -> "λ(x : List Bool) → List/head Bool x",
      """\(x: List Bool) -> List/last Bool (x # ([] : List Bool))""" -> "λ(x : List Bool) → List/last Bool x",
      """\(x: List Bool) -> List/length Bool ([ True ] # x)"""       -> "λ(x : List Bool) → 1 + List/length Bool x",
      """\(x: List Bool) -> List/head Bool ([ True ] # x)"""         -> "λ(x : List Bool) → Some True",
      """\(x: List Bool) -> List/last Bool (x # [ True ])"""         -> "λ(x : List Bool) → Some True",
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
      """\(x: Text) -> \(y: Text) -> Text/replace "" y x """ -> """λ(x : Text) → λ(y : Text) → x""",
      """\(x: Text) -> \(y: Text) -> Text/replace x y "" """ -> """λ(x : Text) → λ(y : Text) → """"",
    ).foreach { case (input, output) =>
      expect(input.dhall.typeCheckAndBetaNormalize().unsafeGet.print == output)
    }
  }

  test("invalid field name is an error 1") {
    expect(
      Try(
        "{x = 1}.y".dhall.betaNormalized
      ).failed.get.getMessage contains "Record access has invalid field name (y), which should be one of the record literal's fields: (x), expression being evaluated: { x = 1 }.y"
    )
  }

  test("invalid field name is an error 2") {
    expect(
      Try(
        "{x = 1}.y".dhall.typeCheckAndBetaNormalize().unsafeGet
      ).failed.get.getMessage == "Type-checking failed with errors: List(In field selection, the record type with field names (x) does not contain field name (y), expression under type inference: { x = 1 }.y, type inference context = {})"
    )
  }

  test("invalid field name is an error 3") {
    expect(
      Try(
        "{x : Bool}.y".dhall.betaNormalized
      ).failed.get.getMessage contains "Record access has invalid field name (y), which should be one of the record type's fields: (x), expression being evaluated: { x : Bool }.y"
    )
  }

  test("invalid field name is an error 4") {
    expect(
      Try(
        "{x : Bool}.y".dhall.typeCheckAndBetaNormalize().unsafeGet
      ).failed.get.getMessage == "Type-checking failed with errors: List(Record type with field names (x) does not contain field name (y), expression under type inference: { x : Bool }.y, type inference context = {})"
    )
  }

  test("record types field access") {
    expect("{a: Bool, b: Integer}.a".dhall.typeCheckAndBetaNormalize().unsafeGet.print == "Bool")
    expect("{a: Bool, b: Integer}.{a}".dhall.typeCheckAndBetaNormalize().unsafeGet.print == "{ a : Bool }")
    expect(
      Try(
        "{a: Bool, b: Integer}.({a : Text})".dhall.typeCheckAndBetaNormalize().unsafeGet
      ).failed.get.getMessage contains "ProjectByType is invalid because the base expression has type Type instead of RecordType"
    )
  }

  test("no support for kind-polymorphic functions") {
    Try(
      "λ(a : Kind) → λ(b : a) → λ(x : b) → x".dhall.typeCheckAndBetaNormalize().unsafeGet.print
    ).failed.get.getMessage contains "instead found input type a, output type a, expression under type inference: ∀(x : b) → b, type inference context = {a : Kind, b : a}"
  }

  test("a function is equivalent to its eta expansion") {
    val result = "λ(f : Bool → Bool) → assert : f === (λ(x : Bool) → f x)".dhall.typeCheckAndBetaNormalize().unsafeGet.print
    expect(result == "λ(f : ∀(_ : Bool) → Bool) → assert : f ≡ (λ(x : Bool) → f x)")
  }

  test("eta expansion with two curried arguments") {
    val result = "λ(f : Bool → Bool → Bool) → (λ(x : Bool) → λ(y : Bool) → f x y)".dhall.typeCheckAndBetaNormalize().unsafeGet.print
    expect(result == "λ(f : ∀(_ : Bool) → ∀(_ : Bool) → Bool) → λ(x : Bool) → λ(y : Bool) → f x y")
  }

  test("assert with eta expansion with two curried arguments") {
    val result = "λ(f : Bool → Bool → Bool) → assert : f === (λ(x : Bool) → λ(y : Bool) → f x y)".dhall.typeCheckAndBetaNormalize().unsafeGet.print
    expect(result == "λ(f : ∀(_ : Bool) → ∀(_ : Bool) → Bool) → assert : f ≡ (λ(x : Bool) → λ(y : Bool) → f x y)")
  }

  test("failure in eta expansion with two curried arguments") {
    val failure = "λ(f : Bool → Bool → Bool) → assert : f === (λ(x : Bool) → λ(y : Bool) → f y x)".dhall.inferTypeWith(KnownVars.empty)
    expect(failure match {
      case TypecheckResult.Invalid(errors) => errors exists (_ contains "Unequal sides, f does not equal λ(_ : Bool) → λ(_ : Bool) → f _ _@1")
    })
  }

  test("eta expansion with free occurrences of external bound variable") {
    val result = "λ(f : Bool → Bool → Bool) → λ(x : Bool) → assert : f x === (λ(x : Bool) → f x@1 x)".dhall.typeCheckAndBetaNormalize().unsafeGet.print
    expect(result == "λ(f : ∀(_ : Bool) → ∀(_ : Bool) → Bool) → λ(x : Bool) → assert : f x ≡ (λ(x : Bool) → f x@1 x)")
  }

  test("failure 1 with f x in eta expansion with free occurrences of external bound variable") {
    val failure = "λ(f : Bool → Bool → Bool) → λ(x : Bool) → assert : f x === (λ(x : Bool) → f x x)".dhall.inferTypeWith(KnownVars.empty)
    expect(failure match {
      case TypecheckResult.Invalid(errors) => errors exists (_ contains "Unequal sides, f x does not equal λ(_ : Bool) → f _ _, in f x ≡ (λ(x : Bool) → f x x)")
    })
  }

  test("failure 2 with f x in eta expansion with free occurrences of external bound variable") {
    val failure = "λ(f : Bool → Bool → Bool) → λ(x : Bool) → assert : f === (λ(x : Bool) → f x x)".dhall.inferTypeWith(KnownVars.empty)
    expect(failure match {
      case TypecheckResult.Invalid(errors) =>
        errors exists (_ contains "Types of two sides of `===` are not equivalent: ∀(_ : Bool) → ∀(_ : Bool) → Bool and ∀(x : Bool) → Bool")
    })
  }

  test("eta-reduction works regardless of types") {
    expect("\\(x : Bool) -> f x x".dhall.betaNormalized.print == "λ(x : Bool) → f x x")
    expect("\\(f: Bool) -> \\(x : Bool) -> f x x".dhall.betaNormalized.print == "λ(f : Bool) → λ(x : Bool) → f x x")
  }

  test("identity law of function composition") {
    expect("""
        | let identity
        |    : ∀(A : Type) → ∀(x : A) → A
        |    = λ(A : Type) → λ(x : A) → x
        | let compose_forward : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (a → b) → (b → c) → a → c
        |    = λ(a : Type) →
        |      λ(b : Type) →
        |      λ(c : Type) →
        |      λ(f : a → b) →
        |      λ(g : b → c) →
        |      λ(x : a) →
        |        g (f x)
        |  in
        | λ(a : Type) →
        |      λ(b : Type) →
        |      λ(c : Type) →
        |      λ(d : Type) →
        |      λ(f : a → b) →
        |      λ(g : b → c) →
        |      λ(h : c → d) →
        |      λ(k : a → b → c) →
        |        { right_identity_law_forward =
        |            assert : compose_forward a b b f (identity b) ≡ f
        |        , left_identity_law_forward =
        |            assert : compose_forward a a b (identity a) f ≡ f
        |        , associativity_law_forward =
        |              assert
        |            :   compose_forward a b d f (compose_forward b c d g h)
        |              ≡ compose_forward a c d (compose_forward a b c f g) h
        |        }
        |""".stripMargin.dhall.typeCheckAndBetaNormalize().isValid)
  }

  test("associativity rewrite 1") {
    val right          = "x + (y + z)".dhall
    val rightRewritten = Semantics.betaNormalizeAndExpand(right, BetaNormalizingOptions(rewriteAssociativity = true)).scheme
    val leftScheme     = ExprOperator(Expression(ExprOperator(v("x"), Operator.Plus, v("y"))), Operator.Plus, v("z"))
    val rightScheme    = ExprOperator(v("x"), Operator.Plus, Expression(ExprOperator(v("y"), Operator.Plus, v("z"))))
    expect(right.scheme == rightScheme)
    expect(rightRewritten.scheme == leftScheme)
  }

  test("associativity rewrite 2") {
    val left  = "(x && y) && z".dhall
    val right = "x && (y && z)".dhall
    expect(Semantics.equivalent(left, right))
  }

  test("associativity law of monoids") {
    expect("""
        |let Monoid = λ(m : Type) → { empty : m, append : m → m → m }
        |
        |      let monoidBool
        |          : Monoid Bool
        |          = { empty = True, append = λ(x : Bool) → λ(y : Bool) → x && y }
        |
        |      let monoidNatural
        |          : Monoid Natural
        |          = { empty = 0, append = λ(x : Natural) → λ(y : Natural) → x + y }
        |
        |      let monoidText
        |          : Monoid Text
        |          = { empty = "", append = λ(x : Text) → λ(y : Text) → x ++ y }
        |
        |      let monoidList
        |          : ∀(a : Type) → Monoid (List a)
        |          = λ(a : Type) →
        |              { empty = [] : List a
        |              , append = λ(x : List a) → λ(y : List a) → x # y
        |              }
        |let monoidLaws = λ(m : Type) → λ(monoid_m : Monoid m) → λ(x : m) → λ(y : m) → λ(z : m) →
        |  let plus = monoid_m.append
        |  let e = monoid_m.empty
        |    in {
        |        monoid_left_id_law = { _1 = plus e x, _2 = x },
        |        monoid_right_id_law = { _1 = plus x e, _2 = x },
        |        monoid_assoc_law = { _1 = plus x (plus y z), _2 = plus (plus x y) z },
        |       }
        |let monoidLaws_eq = λ(m : Type) → λ(monoid_m : Monoid m) → λ(x : m) → λ(y : m) → λ(z : m) →
        |  let plus = monoid_m.append
        |  let e = monoid_m.empty
        |    in {
        |        monoid_left_id_law = plus e x === x,
        |        monoid_right_id_law = plus x e === x,
        |        monoid_assoc_law = plus x (plus y z) === plus (plus x y) z,
        |       }
        |
        |let check_monoidBool_assoc_law =
        |            λ(x : Bool) →
        |            λ(y : Bool) →
        |            λ(z : Bool) →
        |              assert : (monoidLaws Bool monoidBool x y z).monoid_assoc_law._1 === (monoidLaws Bool monoidBool x y z).monoid_assoc_law._2
        |let check_monoidBool_assoc_law_eq =
        |            λ(x : Bool) →
        |            λ(y : Bool) →
        |            λ(z : Bool) →
        |              assert : (monoidLaws_eq Bool monoidBool x y z).monoid_assoc_law
        |in True
        |""".stripMargin.dhall.typeCheckAndBetaNormalize().isValid)
  }

  test("equivalence for Double literals") {
    val x = "0.0".dhall
    val y = "-0.0".dhall
    expect(Semantics.equivalent(x, x))
    expect(!Semantics.equivalent(x, y))
  }

  test("`with` for Optional works if it does not change type") { // https://github.com/dhall-lang/dhall-haskell/issues/2597
    val result = "(Some 1) with ? = 2".dhall.typeCheckAndBetaNormalize()
    expect(result.unsafeGet.print == "Some 2")
  }

  test("fail `with` for Optional if it changes type") {
    val result = """(Some 1) with ? = "hello"""".dhall.typeCheckAndBetaNormalize()
    expect(
      Try(
        result.unsafeGet
      ).failed.get.getMessage contains "Inferred type Text differs from the expected type Natural, expression under type inference: \"hello\""
    )
  }

  test("`with` for Optional works if it does not change type, with deep record access") { // https://github.com/dhall-lang/dhall-haskell/issues/2597
    val result = "(Some { x.y = 1 }) with ?.x.y = 2".dhall.typeCheckAndBetaNormalize()
    expect(result.unsafeGet.print == "Some { x = { y = 2 } }")
  }

  test("succeed `with` for Optional if it changes type, with deep record access") {
    val result = """(Some { x.y = 1 }) with ?.x.y = "hello"""".dhall.typeCheckAndBetaNormalize()
    expect(result.unsafeGet.print == "Some { x = { y = \"hello\" } }")
//    expect(
//      Try(
//        result.unsafeGet
//      ).failed.get.getMessage contains "Inferred type Text differs from the expected type { x : { y : Natural } }, expression under type inference: \"hello\""
//    )
  }

}
