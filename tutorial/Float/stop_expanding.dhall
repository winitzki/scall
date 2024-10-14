-- Prevent expanding arguments under a lambda.
-- The function requires a predicate that always returns True but such that Dhall cannot detect that property when expanding under a lambda.
-- Examples are provided for Bool, Integer, and Natural input types.
let Optional/filter =
      https://prelude.dhall-lang.org/Optional/filter
        sha256:54f0a487d578801819613fe000050c038c632edf1f9ccc57677e98ae0ef56b83

let expanding
    : ∀(T : Type) → (T → Bool) → ∀(R : Type) → R → (T → R) → T → R
    = λ(T : Type) →
      λ(predicate : T → Bool) →
      λ(R : Type) →
      λ(default : R) →
      λ(f : T → R) →
      λ(x : T) →
        merge
          { Some = λ(x : T) → f x, None = default }
          (Optional/filter T predicate (Some x))

let predicateNatural
    : Natural → Bool
    = λ(x : Natural) →
        Natural/isZero (Natural/subtract 2 (Natural/subtract x 1))

let predicateInteger
    : Integer → Bool
    = λ(x : Integer) → predicateNatural (Integer/clamp x)

let predicateBool
    : Bool → Bool
    = λ(x : Bool) → predicateNatural (if x then 1 else 0)

let expandingNatural
    : ∀(R : Type) → R → (Natural → R) → Natural → R
    = λ(R : Type) →
      λ(default : R) →
      λ(f : Natural → R) →
        expanding Natural predicateNatural R default f

let expandingInteger
    : ∀(R : Type) → R → (Integer → R) → Integer → R
    = λ(R : Type) →
      λ(default : R) →
      λ(f : Integer → R) →
        expanding Integer predicateInteger R default f

let expandingBool
    : ∀(R : Type) → R → (Bool → R) → Bool → R
    = λ(R : Type) →
      λ(default : R) →
      λ(f : Bool → R) →
        expanding Bool predicateBool R default f

in  { expanding
    , predicateNatural
    , predicateInteger
    , predicateBool
    , expandingNatural
    , expandingInteger
    , expandingBool
    }
