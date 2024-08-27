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

let egyptian_div_mod
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

let _ = assert : egyptian_div_mod 11 2 ≡ { div = 5, rem = 1 }

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
let egyptian_div_mod_A : Natural → Natural → Result =  λ(a : Natural) →
                                                             λ(b : Natural) →
                                                              let stopgap
                                                                         : Natural → Result
                                                                         = λ(b : Natural) → { div = 0, rem = b }
                                                             let limit : Natural = H.hylo_max_depth P functorP foldableP a Natural (coalg a) b
                                                             in H.hylo_Nat P functorP limit Natural b (coalg a)  Result (alg a) stopgap
                                                             let _ = assert : egyptian_div_mod_A 11 2 ≡ { div = 5, rem = 1 }
in  { egyptian_div_mod, egyptian_div_mod_A, egyptian_div_mod_N }
