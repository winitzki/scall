let Bifunctor = Type → Type → Type

let Pair
    : Bifunctor
    = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }

let Functor = Type → Type

let Map
    : Functor → Type
    = λ(S : Functor) → ∀(a : Type) → ∀(b : Type) → (a → b) → S a → S b

let Zip
    : Functor → Type
    = λ(S : Functor) → ∀(a : Type) → ∀(b : Type) → S a → S b → S (Pair a b)

let Pure
    : Functor → Type
    = λ(S : Functor) → ∀(a : Type) → a → S a

let Bimap
    : Bifunctor → Type
    = λ(S : Bifunctor) →
        ∀(a : Type) →
        ∀(b : Type) →
        ∀(c : Type) →
        ∀(d : Type) →
        ∀(f : a → c) →
        ∀(g : b → d) →
        S a b →
          S c d

let Bizip
    : Bifunctor → Type
    = λ(S : Bifunctor) →
        ∀(a : Type) →
        ∀(b : Type) →
        ∀(p : Type) →
        ∀(q : Type) →
        S a p →
        S b q →
          S (Pair a b) (Pair p q)

let BizipK
    : Bifunctor → Functor → Type
    = λ(S : Bifunctor) →
      λ(C : Functor) →
        ∀(a : Type) →
        ∀(b : Type) →
        S a (C a) →
        S b (C b) →
          S (Pair a b) (Pair (C a) (C b))

let Traverse
    : Functor → Functor → Type
    = λ(L : Functor) →
      λ(F : Functor) →
        ∀(pureF : Pure F) →
        ∀(zipF : Zip F) →
        ∀(a : Type) →
        ∀(b : Type) →
        ∀(f : a → F b) →
        L a →
          F (L b)

let Bitraverse
    : Bifunctor → Functor → Type
    = λ(L : Bifunctor) →
      λ(F : Functor) →
        ∀(pureF : Pure F) →
        ∀(zipF : Zip F) →
        ∀(a : Type) →
        ∀(b : Type) →
        ∀(c : Type) →
        ∀(d : Type) →
        ∀(f : a → F c) →
        ∀(g : b → F d) →
        L a b →
          F (L c d)

in  { Functor
    , Bifunctor
    , Bimap
    , Pair
    , Traverse
    , Bitraverse
    , Pure
    , Zip
    , Bizip
    , BizipK
    , Map
    }
