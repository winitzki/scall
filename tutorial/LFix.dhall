-- Generic definitions for Church encodings of recursive types.
let Functor = (./Functor.dhall).Functor

let LFix : (Type → Type) → Type
  = λ(F : Type → Type) → ∀(r : Type) → (F r → r) → r

let fix : ∀(F : Type → Type) → Functor F → F (LFix F) → LFix F
  = λ(F : Type → Type) → λ(functorF : Functor F) →
    let C = LFix F
    in
      λ(fc : F C) → λ(r : Type) → λ(frr : F r → r) →
        let c2r : C → r = λ(c : C) → c r frr
        let fmap_c2r : F C → F r = functorF.fmap C r c2r
        let fr : F r = fmap_c2r fc
          in frr fr

let unfix : ∀(F : Type → Type) → Functor F → LFix F → F (LFix F)
  = λ(F : Type → Type) → λ(functorF : Functor F) →
    let C = LFix F
    let fmap_fix : F (F C) → F C = functorF.fmap (F C) C (fix F functorF)
      in λ(c : C) → c (F C) fmap_fix

in { LFix, fix, unfix }
