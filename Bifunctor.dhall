let Bifunctor = Type → Type → Type

let Either = λ(a : Type) → λ(b : Type) → < Left : a | Right : b >

let inLeft =
      λ(a : Type) →
      λ(b : Type) →
      λ(x : a) →
        (Either a b).Left x : < Left : a | Right : b >

let inRight =
      λ(a : Type) →
      λ(b : Type) →
      λ(y : b) →
        (Either a b).Right y : < Left : a | Right : b >

let Pair
    : Bifunctor
    = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }

let mkPair =
      λ(a : Type) →
      λ(x : a) →
      λ(b : Type) →
      λ(y : b) →
        { _1 = x, _2 = y } : Pair a b

let Triple =
      λ(a : Type) → λ(b : Type) → λ(c : Type) → { _1 : a, _2 : b, _3 : c }

let mkTriple =
      λ(a : Type) →
      λ(x : a) →
      λ(b : Type) →
      λ(y : b) →
      λ(c : Type) →
      λ(z : c) →
        { _1 = x, _2 = y, _3 = z } : Triple a b c

let Functor = Type → Type

let Depth = λ(S : Bifunctor) → ∀(a : Type) → S a Natural → Natural

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

let Zip0
    : Bifunctor → Type
    = λ(S : Bifunctor) →
        ∀(a : Type) → ∀(b : Type) → ∀(r : Type) → S a r → S b r → S (Pair a b) r

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
        ∀(fmapC : Map C) →
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
    , Either
    , inRight
    , inLeft
    , Pair
    , mkPair
    , Triple
    , mkTriple
    , Depth
    , Traverse
    , Bitraverse
    , Pure
    , Zip
    , Zip0
    , Bizip
    , BizipK
    , Map
    }
