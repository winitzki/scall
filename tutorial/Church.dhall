-- Church.T0 F : Type is the Church encoding of the fixpoint µ(x : Type) → F x  = T0 F
-- Church.T1 F : Type → Type is the Church encoding of the fixpoint µ(x : Type) → F a x  = T1 F a
-- Church.K0 F : Type → Type is the Church encoding of the fixpoint µ(X : Type → Type) → F X a  = K0 F a
let B = ./Bifunctor.dhall

let T0
    : B.Functor → Type
    = λ(F : B.Functor) → ∀(r : Type) → (F r → r) → r

let T1
    : B.Bifunctor → B.Functor
    = λ(F : B.Bifunctor) → λ(a : Type) → ∀(r : Type) → (F a r → r) → r

let fixT1
    : ∀(F : B.Bifunctor) →
      B.Bimap F →
      ∀(a : Type) →
      ∀(fa : F a (T1 F a)) →
        T1 F a
    = λ(F : B.Bifunctor) →
      λ(bimapF : B.Bimap F) →
      λ(a : Type) →
        let C = T1 F a

        in  λ(fa : F a C) →
            λ(r : Type) →
            λ(farr : F a r → r) →
              let c2r
                  : C → r
                  = λ(c : C) → c r farr

              let far
                  : F a r
                  = bimapF a C a r (λ(x : a) → x) c2r fa

              in  farr far

let unfixT1
    : ∀(F : B.Bifunctor) → B.Bimap F → ∀(a : Type) → T1 F a → F a (T1 F a)
    = λ(F : B.Bifunctor) →
      λ(bimapF : B.Bimap F) →
      λ(a : Type) →
        let C = T1 F a

        in  λ(c : C) →
              let fmapFix
                  : F a (F a C) → F a C
                  = bimapF a (F a C) a C (λ(x : a) → x) (fixT1 F bimapF a)

              in  c (F a C) fmapFix

let mapT1
    : ∀(F : B.Bifunctor) →
      B.Bimap F →
      ∀(a : Type) →
      ∀(b : Type) →
      (a → b) →
      T1 F a →
        T1 F b
    = λ(F : B.Bifunctor) →
      λ(bimapF : B.Bimap F) →
      λ(a : Type) →
      λ(b : Type) →
      λ(f : a → b) →
      λ(ca : T1 F a) →
      λ(r : Type) →
      λ(fbrr : F b r → r) →
        let farr
            : F a r → r
            = λ(far : F a r) →
                let fbr
                    : F b r
                    = bimapF a r b r f (λ(x : r) → x) far

                in  fbrr fbr

        in  ca r farr

let K0
    : (B.Functor → B.Functor) → B.Functor
    = λ(F : B.Functor → B.Functor) →
      λ(a : Type) →
        ∀(r : B.Functor) → (∀(b : Type) → F r b → r b) → r a

in  { T0, T1, fixT1, unfixT1, mapT1, K0 }
