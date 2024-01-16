-- Church.T0 F : Type is the Church encoding of the fixpoint µ(x : Type) → F x  = T0 F
-- Church.T1 F : Type → Type is the Church encoding of the fixpoint µ(x : Type) → F a x  = T1 F a
-- Church.K0 F : Type → Type is the Church encoding of the fixpoint µ(X : Type → Type) → F X a  = K0 F a
let T0
    : (Type → Type) → Type
    = λ(F : Type → Type) → ∀(r : Type) → (F r → r) → r

let T1
    : (Type → Type → Type) → Type → Type
    = λ(F : Type → Type → Type) → λ(a : Type) → ∀(r : Type) → (F a r → r) → r

let fixT1
    : ∀(F : Type → Type → Type) →
      ∀ ( bimapF
        : ∀(a : Type) →
          ∀(b : Type) →
          ∀(c : Type) →
          ∀(d : Type) →
          ∀(f : a → c) →
          ∀(g : b → d) →
          F a b →
            F c d
        ) →
      ∀(a : Type) →
      ∀(fa : F a (T1 F a)) →
        T1 F a
    = λ(F : Type → Type → Type) →
      λ ( bimapF
        : ∀(a : Type) →
          ∀(b : Type) →
          ∀(c : Type) →
          ∀(d : Type) →
          ∀(f : a → c) →
          ∀(g : b → d) →
          F a b →
            F c d
        ) →
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
    : ∀(F : Type → Type → Type) →
      ∀ ( bimapF
        : ∀(a : Type) →
          ∀(b : Type) →
          ∀(c : Type) →
          ∀(d : Type) →
          ∀(f : a → c) →
          ∀(g : b → d) →
          F a b →
            F c d
        ) →
      ∀(a : Type) →
      T1 F a →
        F a (T1 F a)
    = λ(F : Type → Type → Type) →
      λ ( bimapF
        : ∀(a : Type) →
          ∀(b : Type) →
          ∀(c : Type) →
          ∀(d : Type) →
          ∀(f : a → c) →
          ∀(g : b → d) →
          F a b →
            F c d
        ) →
      λ(a : Type) →
        let C = T1 F a

        in  λ(c : C) →
              let fmapFix
                  : F a (F a C) → F a C
                  = bimapF a (F a C) a C (λ(x : a) → x) (fixT1 F bimapF a)

              in  c (F a C) fmapFix

let K0
    : ((Type → Type) → Type → Type) → Type → Type
    = λ(F : (Type → Type) → Type → Type) →
      λ(a : Type) →
        ∀(r : Type → Type) → (∀(b : Type) → F r b → r b) → r a

in  { T0, T1, fixT1, unfixT1, K0 }
