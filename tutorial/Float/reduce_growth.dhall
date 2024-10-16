let reduce_growth
    -- Reduce growth of normal forms by preventing expansion of arguments under a lambda.
    -- The function requires a predicate that always returns True but such that Dhall cannot detect that property when expanding under a lambda.
    -- Examples are provided for Bool, Integer, and Natural input types.

    : ∀(T : Type) → (T → Bool) → ∀(R : Type) → R → (T → R) → T → R
    = λ(T : Type) →
      λ(predicate : T → Bool) →
      λ(R : Type) →
      λ(default : R) →
      λ(f : T → R) →
      λ(x : T) →
        merge
          { Some = f, None = default }
          (if predicate x then Some x else None T)

let reduce_growth_noop
    -- This function is provided for comparison. It does not change the expansion behavior.
    : ∀(T : Type) → (T → Bool) → ∀(R : Type) → R → (T → R) → T → R
    = λ(T : Type) →
      λ(predicate : T → Bool) →
      λ(R : Type) →
      λ(default : R) →
      λ(f : T → R) →
        f

let predicate_Natural
    : Natural → Bool
    = λ(x : Natural) →
        Natural/isZero (Natural/subtract 1 (Natural/subtract x 1))

let predicate_Integer
    : Integer → Bool
    = λ(x : Integer) → predicate_Natural (Integer/clamp x)

let predicate_Bool
    : Bool → Bool
    = λ(x : Bool) → predicate_Natural (if x then 1 else 0)

let reduce_growth_Natural
    : ∀(R : Type) → R → (Natural → R) → Natural → R
    = λ(R : Type) →
      λ(default : R) →
      λ(f : Natural → R) →
        reduce_growth Natural predicate_Natural R default f

let reduce_growth_Integer
    : ∀(R : Type) → R → (Integer → R) → Integer → R
    = λ(R : Type) →
      λ(default : R) →
      λ(f : Integer → R) →
        reduce_growth Integer predicate_Integer R default f

let reduce_growth_Bool
    : ∀(R : Type) → R → (Bool → R) → Bool → R
    = λ(R : Type) →
      λ(default : R) →
      λ(f : Bool → R) →
        reduce_growth Bool predicate_Bool R default f

in  { reduce_growth
    , reduce_growth_noop
    , predicate_Natural
    , predicate_Integer
    , predicate_Bool
    , reduce_growth_Natural
    , reduce_growth_Integer
    , reduce_growth_Bool
    }
