let reduce_growth
    -- Reduce growth of normal forms by preventing expansion of arguments under a lambda.
    -- Transforms a function of type T → R into another function of the same type.
    -- The usage requires a predicate that always returns True but such that Dhall
    --   cannot statically recognize that property when expanding under a lambda.
    -- Example predicates are provided for Bool, Integer, Natural, and List input types.
    -- The usage also requires a default value of the output type.
    -- The default value will never be used in actual evaluation.
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

let predicate_List
    : ∀(T : Type) → List T → Bool
    = λ(T : Type) → λ(list : List T) → predicate_Natural (List/length T list)

let reduce_growth_Natural
    : ∀(R : Type) → R → (Natural → R) → Natural → R
    = λ(R : Type) →
      λ(default : R) →
      λ(f : Natural → R) →
        reduce_growth Natural predicate_Natural R default f

let reduce_growth_Natural_Natural
    : ∀(R : Type) → R → (Natural → Natural → R) → Natural → Natural → R
    = λ(R : Type) →
      λ(default : R) →
      λ(f : Natural → Natural → R) →
        reduce_growth_Natural
          (Natural → R)
          (λ(n : Natural) → default)
          (λ(n : Natural) → reduce_growth_Natural R default (f n))

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

let reduce_growth_List
    : ∀(R : Type) → R → ∀(T : Type) → (List T → R) → List T → R
    = λ(R : Type) →
      λ(default : R) →
      λ(T : Type) →
      λ(f : List T → R) →
        reduce_growth (List T) (predicate_List T) R default f

in  { reduce_growth
    , reduce_growth_noop
    , predicate_Natural
    , predicate_Integer
    , predicate_Bool
    , reduce_growth_Natural
    , reduce_growth_Natural_Natural
    , reduce_growth_Integer
    , reduce_growth_List
    , reduce_growth_Bool
    }
