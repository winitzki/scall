-- Hand-coded Church naturals with utility functions.
let ChurchNatural
    : Type
    = ∀(r : Type) → r → (r → r) → r

let Church0
    : ChurchNatural
    = λ(r : Type) → λ(x : r) → λ(k : r → r) → x

let Church1
    : ChurchNatural
    = λ(r : Type) → λ(x : r) → λ(k : r → r) → k x

let ChurchPlus1
    : ChurchNatural → ChurchNatural
    = λ(n : ChurchNatural) → λ(r : Type) → λ(x : r) → λ(k : r → r) → k (n r x k)

let NaturalToChurch
    : Natural → ChurchNatural
    = λ(n : Natural) →
      λ(r : Type) →
      λ(x : r) →
      λ(k : r → r) →
        Natural/fold n r k x

let ChurchToNatural
    : ChurchNatural → Natural
    = λ(n : ChurchNatural) → n Natural 0 (λ(x : Natural) → x + 1)

let test = assert : ChurchToNatural Church0 ≡ 0

let test = assert : ChurchToNatural Church1 ≡ 1

let test = assert : ChurchToNatural (ChurchPlus1 Church0) ≡ 1

let test = assert : ChurchToNatural (ChurchPlus1 Church1) ≡ 2

let test = assert : ChurchToNatural (NaturalToChurch 10) ≡ 10

in  { ChurchNatural, Church0, Church1, ChurchPlus1, NaturalToChurch, ChurchToNatural }
