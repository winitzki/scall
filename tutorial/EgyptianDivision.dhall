let H = ./Hylo.dhall

let Natural/lessThan = https://prelude.dhall-lang.org/Natural/lessThan

let Functor = (./Functor.dhall).Functor

let Foldable = H.Foldable

let Monoid = H.Monoid

let P = λ(X : Type) → < P1 : Natural | P2 : { p : X, b : Natural } >

let fmap_P =
      λ(a : Type) →
      λ(b : Type) →
      λ(f : a → b) →
      λ(pa : P a) →
        merge
          { P1 = (P b).P1
          , P2 = λ(x : { p : a, b : Natural }) → (P b).P2 { p = f x.p, b = x.b }
          }
          pa

let functorP
    : Functor P
    = { fmap = fmap_P }

let reduce_P
    : ∀(M : Type) → Monoid M → P M → M
    = λ(M : Type) →
      λ(monoidM : Monoid M) →
      λ(pm : P M) →
        merge
          { P1 = λ(_ : Natural) → monoidM.empty
          , P2 = λ(x : { p : M, b : Natural }) → x.p
          }
          pm

let foldableP
    : Foldable P
    = { reduce = reduce_P }

let Result = { div : Natural, rem : Natural }

let postprocess1 =
      λ(a : Natural) →
      λ(b : Natural) →
        if    Natural/lessThan a b
        then  { div = 0, rem = a }
        else  { div = 1, rem = Natural/subtract b a }

let postprocess2 =
      λ(p2 : { p : Result, b : Natural }) →
        let quotient = p2.p.div

        let remainder = p2.p.rem

        in  if    Natural/lessThan remainder p2.b
            then  { div = 2 * quotient, rem = remainder }
            else  { div = 2 * quotient + 1
                  , rem = Natural/subtract p2.b remainder
                  }

let alg
    : Natural → P Result → Result
    = λ(a : Natural) →
      λ(pp : P Result) →
        merge { P1 = postprocess1 a, P2 = postprocess2 } pp

let coalg
    : Natural → Natural → P Natural
    = λ(a : Natural) →
      λ(b : Natural) →
        if    Natural/lessThan (Natural/subtract b a) b
        then  (P Natural).P1 b
        else  (P Natural).P2 { p = 2 * b, b }

let egyptian_div_mod_do-not-use-this-is-very-slow
    : Natural → Natural → Result
    = λ(a : Natural) →
      λ(b : Natural) →
        let stopgap
            : Natural → Result
            = λ(b : Natural) → { div = 0, rem = b }

        let limit = a

        in  H.hylo_Nat
              P
              functorP
              limit
              Natural
              b
              (coalg a)
              Result
              (alg a)
              stopgap

let _ =
        assert
      :   egyptian_div_mod_do-not-use-this-is-very-slow 11 2
        ≡ { div = 5, rem = 1 }

let egyptian_div_mod_N
    : Natural → Natural → Result
    = λ(a : Natural) →
      λ(b : Natural) →
        let stopgap
            : Natural → Result
            = λ(b : Natural) → { div = 0, rem = b }

        let limit = a

        in  H.hylo_N
              P
              functorP
              foldableP
              limit
              Natural
              b
              (coalg a)
              Result
              (alg a)
              stopgap

let _ = assert : egyptian_div_mod_N 11 2 ≡ { div = 5, rem = 1 }

let egyptian_div_mod_A
    : Natural → Natural → Result
    = λ(a : Natural) →
      λ(b : Natural) →
        let stopgap
            : Natural → Result
            = λ(b : Natural) → { div = 0, rem = b }

        let limit
            : Natural
            = H.hylo_max_depth P functorP foldableP a Natural (coalg a) b

        in  H.hylo_Nat
              P
              functorP
              limit
              Natural
              b
              (coalg a)
              Result
              (alg a)
              stopgap

let _ = assert : egyptian_div_mod_A 11 2 ≡ { div = 5, rem = 1 }

let powersOf2Until_
    -- create a list [1, 2, 4, 8, ..., 2^p] such that a < b * 2 ^ (p + 1) .
    : Natural → Natural → List Natural
    = λ(a : Natural) →
      λ(b : Natural) →
        let TableT = { result : List Natural, power2 : Natural }

        let succ =
              λ(prev : TableT) →
                if    Natural/lessThan a (prev.power2 * b * 2)
                then  prev
                else  let newPower2 = prev.power2 * 2

                      in  { result = prev.result # [ newPower2 ]
                          , power2 = newPower2
                          }

        in  (Natural/fold a TableT succ { result = [ 1 ], power2 = 1 }).result

let Optional/default = https://prelude.dhall-lang.org/Optional/default

let powersOf2Until
    -- create a list [1, 2, 4, 8, ..., 2^p] such that a < b * 2 ^ (p + 1) .
    : Natural → Natural → List Natural
    = λ(a : Natural) →
      λ(b : Natural) →
        let appendNewPower =
              λ(prev : List Natural) →
                let nextPower =
                      2 * Optional/default Natural 0 (List/last Natural prev)

                in  if    Natural/lessThan a (nextPower * b)
                    then  prev
                    else  prev # [ nextPower ]

        in  Natural/fold a (List Natural) appendNewPower [ 1 ]

let egyptian_div_mod
    : Natural → Natural → Result
    = λ(a : Natural) →
      λ(b : Natural) →
        let powers2 = powersOf2Until a b

        let update
            : Natural → Result → Result
            = λ(power2 : Natural) →
              λ(prev : Result) →
                if    Natural/lessThan prev.rem (power2 * b)
                then  prev
                else  { div = prev.div + power2
                      , rem = Natural/subtract (power2 * b) prev.rem
                      }

        in  List/fold Natural powers2 Result update { div = 0, rem = a }

let _ = assert : powersOf2Until 15 1 ≡ [ 1, 2, 4, 8 ]

let _ = assert : powersOf2Until 16 1 ≡ [ 1, 2, 4, 8, 16 ]

let _ = assert : powersOf2Until 17 1 ≡ [ 1, 2, 4, 8, 16 ]

let _ = assert : powersOf2Until 11 2 ≡ [ 1, 2, 4 ]

let _ = assert : egyptian_div_mod 10 1 ≡ { div = 10, rem = 0 }

let _ = assert : egyptian_div_mod 10 10 ≡ { div = 1, rem = 0 }

let _ = assert : egyptian_div_mod 10 11 ≡ { div = 0, rem = 10 }

let _ = assert : egyptian_div_mod 10 2 ≡ { div = 5, rem = 0 }

let _ = assert : egyptian_div_mod 11 2 ≡ { div = 5, rem = 1 }

in  { egyptian_div_mod, egyptian_div_mod_A, egyptian_div_mod_N }
