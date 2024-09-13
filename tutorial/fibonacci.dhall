let H = ./Hylo.dhall

let Natural/lessThan = https://prelude.dhall-lang.org/Natural/lessThan

let Functor = (./Functor.dhall).Functor

let Foldable = H.Foldable

let Monoid = H.Monoid

let const
    : ∀(a : Type) → ∀(b : Type) → b → a → b
    = λ(a : Type) → λ(b : Type) → λ(x : b) → λ(_ : a) → x

let P
    : Type → Type
    = λ(A : Type) → < P0 | P1 : { call_1 : A, call_2 : A } >

let functorP
    : Functor P
    = { fmap =
          λ(a : Type) →
          λ(b : Type) →
          λ(f : a → b) →
          λ(pa : P a) →
            merge
              { P0 = (P b).P0
              , P1 =
                  λ(r1 : { call_1 : a, call_2 : a }) →
                    (P b).P1 { call_1 = f r1.call_1, call_2 = f r1.call_2 }
              }
              pa
      }

let reduce_P
    : ∀(M : Type) → Monoid M → P M → M
    = λ(M : Type) →
      λ(monoidM : Monoid M) →
      λ(pm : P M) →
        merge
          { P0 = monoidM.empty
          , P1 =
              λ(p1 : { call_1 : M, call_2 : M }) →
                monoidM.append p1.call_1 p1.call_2
          }
          pm

let foldableP
    : Foldable P
    = { reduce = reduce_P }

let arg_1_1 = λ(n : Natural) → Natural/subtract 1 n

let arg_1_2 = λ(n : Natural) → Natural/subtract 2 n

let post_1 = λ(r1 : Natural) → λ(r2 : Natural) → r1 + r2

let do_choice
    : Natural → Bool
    = λ(n : Natural) → Natural/lessThan n 3

let coalgFib
    : Natural → P Natural
    = λ(n : Natural) →
        let choice
            : Bool
            = do_choice n

        in  if    choice
            then  (P Natural).P0
            else  (P Natural).P1 { call_1 = arg_1_1 n, call_2 = arg_1_2 n }

let algFib
    : P Natural → Natural
    = λ(p : P Natural) →
        merge
          { P0 = 1
          , P1 =
              λ(r1 : { call_1 : Natural, call_2 : Natural }) →
                post_1 r1.call_1 r1.call_2
          }
          p

let fibonacci
    : Natural → Natural
    = λ(n : Natural) →
        H.hylo_Nat
          P
          functorP
          n
          Natural
          n
          coalgFib
          Natural
          algFib
          (const Natural Natural 0)

let _ = assert : fibonacci 8 ≡ 21

let fibonacci_auto
    : Natural → Natural
    = λ(n : Natural) →
        H.hylo_N
          P
          functorP
          foldableP
          n
          Natural
          n
          coalgFib
          Natural
          algFib
          (const Natural Natural 0)

let _ = assert : fibonacci_auto 8 ≡ 21

in  { fibonacci, fibonacci_auto }
