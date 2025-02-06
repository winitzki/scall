let Refined : ∀(T : Type) → ∀(f : T → Bool) → Type
  = λ(T : Type) → λ(f : T → Bool) →
       ∀(R : Type) → (∀(t : T) → (f t === True) → R) → R

let toRefined
  : ∀(T : Type) → ∀(f : T → Bool) → ∀(t : T) → (f t === True) → Refined T f
  = λ(T : Type) → λ(f : T → Bool) → λ(t : T) → λ(evidence : f t === True) →
    λ(R : Type) → λ(run : ∀(t : T) → (f t === True) → R) → run t evidence 

let refinedToBaseType
  : ∀(T : Type) → ∀(f : T → Bool) → Refined T f → T
  = λ(T : Type) → λ(f : T → Bool) → λ(refined : Refined T f) →
    refined T (λ(t : T) → λ(_ : f t === True) → t)

let nonzero = λ(n : Natural) → Natural/isZero n == False

let NonzeroNatural : Type = Refined Natural nonzero

let makeNonzeroNatural = toRefined Natural nonzero

let x = 123

let xNonZero : NonzeroNatural = makeNonzeroNatural x (assert : nonzero x === True)

let xFromRefined : Natural = refinedToBaseType Natural nonzero xNonZero

let _ = assert : x === xFromRefined


let ConsistsOfHello : Text → Type = λ(t : Text) → Text/replace "hello" "" t === ""

let StringHello : Type = ∀(R : Type) → (∀(t : Text) → ConsistsOfHello t → R) → R

let toStringHello
  : ∀(t : Text) → ConsistsOfHello t → StringHello
  = λ(t : Text) → λ(evidence : ConsistsOfHello t) →
    λ(R : Type) → λ(run : ∀(t : Text) → ConsistsOfHello t → R) → run t evidence

let fromStringHello : StringHello → Text
  = λ(hello : StringHello) → hello Text (λ(t : Text) → λ(_ : ConsistsOfHello t) → t)

let example : Text = "hellohello"

let exampleToHello : StringHello = toStringHello example (assert: ConsistsOfHello example)

let exampleFromHello : Text = fromStringHello exampleToHello

let _ = assert : example === exampleFromHello


let RefinedAssert
  : ∀(T : Type) → ∀(U : Type) → ∀(f : T → U) → ∀(result : U) → Type
  = λ(T : Type) → λ(U : Type) → λ(f : T → U) → λ(result : U) →
    ∀(R : Type) → (∀(t : T) → (f t === result) → R) → R

in True

