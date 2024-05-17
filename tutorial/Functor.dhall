-- Definition of the Functor typeclass.
let Fmap_t = λ(F : Type → Type) → ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b

let Functor = λ(F : Type → Type) → { fmap : Fmap_t F }

in { Fmap_t, Functor }
