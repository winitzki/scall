let F = λ(r : Type) → < Z | S : r >

let Fmap_t =
      λ(F : Type → Type) → ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b

let Functor = λ(F : Type → Type) → { fmap : Fmap_t F }

let functorF
    : Functor F
    = { fmap =
          λ(a : Type) →
          λ(b : Type) →
          λ(f : a → b) →
          λ(fa : F a) →
            merge { Z = (F b).Z, S = λ(pred : a) → (F b).S (f pred) } fa
      }


let LFixMendler
    : (Type → Type) → Type
    = λ(F : Type → Type) → ∀(a : Type) → (∀(r : Type) → (r → a) → F r → a) → a

let MendlerNat = LFixMendler F
 

let identity = \(a : Type) -> \(x : a) -> x

let mendler_zero
    : MendlerNat
    = λ(a : Type) → λ(y : ∀(r : Type) → (r → a) → F r → a) → y a (identity a) (F a).Z

let mendler_succ
    : MendlerNat → MendlerNat
    = λ(c : MendlerNat) →
      λ(a : Type) → λ(y : ∀(r : Type) → (r → a) → F r → a) →
        y a (identity a) ((F a).S (c a y))

let mendler_one
    : MendlerNat
    = mendler_succ mendler_zero

let mendler_two
    : MendlerNat
    = mendler_succ mendler_one

let mendler_to_natural
    : MendlerNat → Natural
    = λ(c : MendlerNat) →
        let reduce
            : ∀(r : Type) → (r → Natural) → F r → Natural
            = \(r : Type) ->  λ(f : r -> Natural) → \(fr : F r) ->
                merge { Z = 0, S = λ(pred : r) → 1 + f pred } fr

        in  c Natural reduce

let natural_to_mendler
    : Natural → MendlerNat
    = λ(n : Natural) → Natural/fold n MendlerNat mendler_succ mendler_zero

let mendler_is_zero
    : MendlerNat → Bool
    = λ(c : MendlerNat) →
     let reduce
                : ∀(r : Type) → (r → Bool) → F r → Bool
                = \(r : Type) ->  λ(f : r -> Bool) → \(fr : F r) ->
                    merge { Z = True, S = λ(pred : r) → False } fr

            in  c Bool reduce

let _ = assert : mendler_to_natural mendler_zero ≡ 0

let _ = assert : mendler_is_zero mendler_zero ≡ True

let _ = assert : mendler_to_natural mendler_one ≡ 1

let _ = assert : mendler_is_zero mendler_one ≡ False

let _ = assert : mendler_to_natural mendler_two ≡ 2

let _ = assert : mendler_is_zero mendler_two ≡ False


let mendler_predecessor_wrong
    : MendlerNat → MendlerNat
    = λ(c : MendlerNat) →
         let reduce
                         : ∀(r : Type) → (r → MendlerNat) → F r → MendlerNat
                         = \(r : Type) ->  λ(f : r -> MendlerNat) → \(fr : F r) ->
                             merge { Z = mendler_zero, S = λ(pred : r) → f pred } fr

                     in  c MendlerNat reduce


let _ = assert : mendler_to_natural (mendler_predecessor_wrong mendler_zero) ≡ 0
let _ = assert : mendler_to_natural (mendler_predecessor_wrong mendler_one) ≡ 0
--let _ = assert : mendler_to_natural (mendler_predecessor_wrong mendler_two) ≡ 1
{-
let fix
    -- Mendler encoded `fix`
    : ∀(F : Type → Type) → Functor F → F (LFixMendler F) → LFixMendler F
    =
      -- This traverses the entire structure.
      λ(F : Type → Type) →
      λ(functorF : Functor F) →
        let C = LFixMendler F

        in  λ(fc : F C) →
            λ(r : Type) →
            λ(frr : F r → r) →
              let c2r
                  : C → r
                  = λ(c : C) → c r frr

              let fmap_c2r
                  : F C → F r
                  = functorF.fmap C r c2r

              let fr
                  : F r
                  = fmap_c2r fc

              in  frr fr

let unfix
    -- Mendler encoded `unfix`
    : ∀(F : Type → Type) → Functor F → LFixMendler F → F (LFixMendler F)
    =
      -- This traverses the entire structure.
      λ(F : Type → Type) →
      λ(functorF : Functor F) →
        let C = LFixMendler F

        let fmap_fix
            : F (F C) → F C
            = functorF.fmap (F C) C (fix F functorF)

        in  λ(c : C) → c (F C) fmap_fix

let mendler_predecessor_wrong_unfix
    : MendlerNat → MendlerNat
    = λ(c : MendlerNat) →
        merge
          { Z = mendler_zero, S = λ(pred : MendlerNat) → pred }
          (unfix F functorF c)

let _ = assert : mendler_predecessor_wrong mendler_two ≡ mendler_one

let large_natural =
    -- Takes about 1 second.
      natural_to_mendler 5000

in  { F
    , LFixMendler
    , MendlerNat
    , mendler_to_natural
    , natural_to_mendler
    , mendler_is_zero
    , mendler_predecessor_wrong
    , large_natural
    }
-}

in True