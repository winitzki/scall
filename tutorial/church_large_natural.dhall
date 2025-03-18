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

let LFix
    : (Type → Type) → Type
    = λ(F : Type → Type) → ∀(r : Type) → (F r → r) → r

let ChurchNat = LFix F

let church_zero
    : ChurchNat
    = λ(r : Type) → λ(frr : F r → r) → frr (F r).Z

let church_succ
    : ChurchNat → ChurchNat
    = λ(c : ChurchNat) →
      λ(r : Type) →
      λ(frr : F r → r) →
        frr ((F r).S (c r frr))

let church_one
    : ChurchNat
    = church_succ church_zero

let church_two
    : ChurchNat
    = church_succ church_one

let church_to_natural
    : ChurchNat → Natural
    = λ(c : ChurchNat) →
        let reduce
            : F Natural → Natural
            = λ(fn : F Natural) →
                merge { Z = 0, S = λ(pred : Natural) → 1 + pred } fn

        in  c Natural reduce

let natural_to_church
    : Natural → ChurchNat
    = λ(n : Natural) → Natural/fold n ChurchNat church_succ church_zero

let church_is_zero
    : ChurchNat → Bool
    = λ(c : ChurchNat) →
        c
          Bool
          (λ(fb : F Bool) → merge { Z = True, S = λ(pred : Bool) → False } fb)

let _ = assert : church_to_natural church_zero ≡ 0

let _ = assert : church_is_zero church_zero ≡ True

let _ = assert : church_to_natural church_one ≡ 1

let _ = assert : church_is_zero church_one ≡ False

let _ = assert : church_to_natural church_two ≡ 2

let _ = assert : church_is_zero church_two ≡ False

let fix
    -- Church encoded `fix`
    : ∀(F : Type → Type) → Functor F → F (LFix F) → LFix F
    =
      -- This traverses the entire structure.
      λ(F : Type → Type) →
      λ(functorF : Functor F) →
        let C = LFix F

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
    -- Church encoded `unfix`
    : ∀(F : Type → Type) → Functor F → LFix F → F (LFix F)
    =
      -- This traverses the entire structure.
      λ(F : Type → Type) →
      λ(functorF : Functor F) →
        let C = LFix F

        let fmap_fix
            : F (F C) → F C
            = functorF.fmap (F C) C (fix F functorF)

        in  λ(c : C) → c (F C) fmap_fix

let church_predecessor
    : ChurchNat → ChurchNat
    = λ(c : ChurchNat) →
        merge
          { Z = church_zero, S = λ(pred : ChurchNat) → pred }
          (unfix F functorF c)

let _ = assert : church_predecessor church_two ≡ church_one

let large_natural =
    -- Takes about 1 second.
      natural_to_church 5000

in  { F
    , LFix
    , ChurchNat
    , church_to_natural
    , natural_to_church
    , church_is_zero
    , church_predecessor
    , large_natural
    }
