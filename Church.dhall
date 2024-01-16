-- Church.T0 F : Type is the Church encoding of the fixpoint µ(x : Type) → F x
-- Church.T1 F : Type → Type is the Church encoding of the fixpoint µ(x : Type) → F a x
-- Church.K0 F : Type → Type is the Church encoding of the fixpoint µ(X : Type → Type) → F X a
  { T0 = λ(F : Type → Type) → ∀(r : Type) → (F r → r) → r
  , T1 = λ(F : Type → Type → Type) → λ(a : Type) → ∀(r : Type) → (F a r → r) → r
  , K0 =
      λ(F : (Type → Type) → Type → Type) →
      λ(a : Type) →
        ∀(r : Type → Type) → (∀(b : Type) → F r b → r b) → r a
  }
: { T0 : (Type → Type) → Type
  , T1 : (Type → Type → Type) → Type → Type
  , K0 : ((Type → Type) → Type → Type) → Type → Type
  }
