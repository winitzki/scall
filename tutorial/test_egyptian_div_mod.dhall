let Natural/lessThan = https://prelude.dhall-lang.org/Natural/lessThan

let Fmap_t =
      λ(F : Type → Type) → ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b

let Functor = λ(F : Type → Type) → { fmap : Fmap_t F }

let Pair = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }

let hylo_Nat
    : ∀(F : Type → Type) →
      Functor F →
      Natural →
      ∀(t : Type) →
      t →
      (t → F t) →
      ∀(r : Type) →
      (F r → r) →
      (t → r) →
        r
    = λ(F : Type → Type) →
      λ(functorF : Functor F) →
      λ(limit : Natural) →
      λ(t : Type) →
      λ(seed : t) →
      λ(coalg : t → F t) →
      λ(r : Type) →
      λ(alg : F r → r) →
      λ(stopgap : t → r) →
        let update
            : (t → r) → t → r
            = λ(f : t → r) → λ(y : t) → alg (functorF.fmap t r f (coalg y))

        let transform
            : t → r
            = Natural/fold limit (t → r) update stopgap

        in  transform seed

let P = λ(X : Type) → < P1 : Natural | P2 : { p : X, b : Natural } >

let fmap_P
    : Fmap_t P
    = λ(a : Type) →
      λ(b : Type) →
      λ(f : a → b) →
      λ(pa : P a) →
        merge
          { P1 = λ(i : Natural) → (P b).P1 i
          , P2 = λ(x : { p : a, b : Natural }) → (P b).P2 { p = f x.p, b = x.b }
          }
          pa

let functorP
    : Functor P
    = { fmap = fmap_P }

let postprocess1 =
      λ(a : Natural) →
      λ(b : Natural) →
        if    Natural/lessThan a b
        then  { _1 = 0, _2 = a }
        else  { _1 = 1, _2 = Natural/subtract b a }

let postprocess2 =
      λ(p2 : { p : { _1 : Natural, _2 : Natural }, b : Natural }) →
        let quotient = p2.p._1

        let remainder = p2.p._2

        in  if    Natural/lessThan remainder p2.b
            then  { _1 = 2 * quotient, _2 = remainder }
            else  { _1 = 2 * quotient + 1
                  , _2 = Natural/subtract p2.b remainder
                  }

let alg
    : Natural → P (Pair Natural Natural) → Pair Natural Natural
    = λ(a : Natural) →
      λ(pp : P (Pair Natural Natural)) →
        merge { P1 = postprocess1 a, P2 = postprocess2 } pp

let coalg
    : Natural → Natural → P Natural
    = λ(a : Natural) →
      λ(b : Natural) →
        if    Natural/lessThan (Natural/subtract b a) b
        then  (P Natural).P1 b
        else  (P Natural).P2 { p = 2 * b, b }

let egyptian_div_mod
    : Natural → Natural → Pair Natural Natural
    = λ(a : Natural) →
      λ(b : Natural) →
        let stopgap
            : Natural → Pair Natural Natural
            = λ(b : Natural) → { _1 = 0, _2 = b }

        let limit = a

        in  hylo_Nat
              P
              functorP
              limit
              Natural
              b
              (coalg a)
              (Pair Natural Natural)
              (alg a)
              stopgap

let _ =
      let x1 = (P Natural).P1 123

      let y1 = functorP.fmap Natural Natural (λ(b : Natural) → b + 1) x1

      let _ = assert : y1 ≡ (P Natural).P1 123

      let x2 = (P Natural).P2 { p = 100, b = 2 }

      let y2 = functorP.fmap Natural Natural (λ(b : Natural) → b + 1) x2

      let _ = assert : y2 ≡ (P Natural).P2 { p = 101, b = 2 }

      in  True

let _ = assert : egyptian_div_mod 11 2 ≡ { _1 = 5, _2 = 1 }

let _ = assert : egyptian_div_mod 105 5 ≡ { _1 = 21, _2 = 0 }

in  { egyptian_div_mod }
