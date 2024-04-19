let F = λ(r : Type) → < Nil | Cons : { head : Integer, tail : r } >
let Fmap_t = λ(F : Type → Type) → ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b
let Functor = λ(F : Type → Type) → { fmap : Fmap_t F }
let functorF : Functor F = {
    fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fa : F a) → merge {
      Nil = (F b).Nil,
      Cons = λ(pair : { head : Integer, tail : a }) → (F b).Cons (pair // { tail = f pair.tail })
    } fa
  }
let LFix : (Type → Type) → Type
  = λ(F : Type → Type) → ∀(r : Type) → (F r → r) → r

let ListInt = LFix F
let fix : ∀(F : Type → Type) → Functor F →
   F (LFix F) → LFix F
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
in {
     nil = fix F functorF (F ListInt).Nil,
     cons = λ(h : Integer) → λ(t : ListInt) → fix F functorF ((F ListInt).Cons { head = h, tail = t }),
   }
