let LeibnizEqual =
      λ(T : Type) → λ(a : T) → λ(b : T) → ∀(f : T → Type) → f a → f b

let x
    : LeibnizEqual Natural 123 123
    = λ(f : Natural → Type) → λ(p : f 123) → p

let refl
    : ∀(T : Type) → ∀(a : T) → LeibnizEqual T a a
    = λ(T : Type) → λ(a : T) → λ(f : T → Type) → λ(p : f a) → p

let x
    : LeibnizEqual Natural 123 123
    = refl Natural 123

let _
    : LeibnizEqual Bool ./NeedToValidate.dhall True
    = -- assert that NeedToValidate.dhall evaluates to True
      refl Bool True

let symmetry =
      λ(T : Type) →
      λ(a : T) →
      λ(b : T) →
      λ(equal_a_b : LeibnizEqual T a b) →
        let result
            : LeibnizEqual T b a
            = equal_a_b (λ(t : T) → LeibnizEqual T t a) (refl T a)

        in  result

let transitivity =
      λ(T : Type) →
      λ(a : T) →
      λ(b : T) →
      λ(c : T) →
      λ(equal_a_b : LeibnizEqual T a b) →
      λ(equal_b_c : LeibnizEqual T b c) →
        let result
            : LeibnizEqual T a c
            = equal_b_c (λ(t : T) → LeibnizEqual T a t) equal_a_b

        in  result

let extensional_equality_values_only =
      λ(T : Type) →
      λ(a : T) →
      λ(b : T) →
      λ(U : Type) →
      λ(g : T → U) →
      λ(equal_a_b : LeibnizEqual T a b) →
        let result
            : LeibnizEqual U (g a) (g b)
            = equal_a_b (λ(t : T) → LeibnizEqual U (g a) (g t)) (refl U (g a))

        in  result

let extensional_equality_functions_only =
      λ(T : Type) →
      λ(a : T) →
      λ(U : Type) →
      λ(g : T → U) →
      λ(h : T → U) →
      λ(equal_g_h : LeibnizEqual (T → U) g h) →
        let result
            : LeibnizEqual U (g a) (h a)
            = equal_g_h
                (λ(t : T → U) → LeibnizEqual U (g a) (t a))
                (refl U (g a))

        in  result

let extensional_equality =
      λ(T : Type) →
      λ(a : T) →
      λ(b : T) →
      λ(U : Type) →
      λ(g : T → U) →
      λ(h : T → U) →
      λ(equal_a_b : LeibnizEqual T a b) →
      λ(equal_g_h : LeibnizEqual (T → U) g h) →
        let result
            : LeibnizEqual U (g a) (h b)
            = transitivity
                U
                (g a)
                (g b)
                (h b)
                (extensional_equality_values_only T a b U g equal_a_b)
                (extensional_equality_functions_only T b U g h equal_g_h)

        in  result

let toAssertType =
      λ(T : Type) →
      λ(a : T) →
      λ(b : T) →
      λ(x : LeibnizEqual T a b) →
        let Equals_a = λ(x : T) → a ≡ x

        let refl_a = assert : a ≡ a

        let result
            : a ≡ b
            = x Equals_a refl_a

        in  result
{-  -- This does not type-check because Dhall does not use equality evidence for type-checking.
-- The error is: the result must have type ResT x y eq, but it has type ResT x x refl.

let eliminatorJ
  : ∀(A : Type) → ∀(ResT : ∀(x : A) → ∀(y : A) → LeibnizEqual A x y → Type) → ∀(f : ∀(x : A) → ResT x x (refl A x)) → ∀(x : A) → ∀(y : A) → ∀(eq : LeibnizEqual A x y) → ResT x y eq
  = λ(A : Type) → λ(ResT : ∀(x : A) → ∀(y : A) → LeibnizEqual A x y → Type) → λ(f : ∀(x : A) → ResT x x (refl A x)) → λ(x : A) → λ(y : A) → λ(eq : LeibnizEqual A x y) →
    let refl_y : ∀(f : A → Type) → f x → f y = λ(f : A → Type) → λ(fx : f x) →
    let fx : ResT x x (refl A x) = f x
    let result1 : ResT x y refl_y = eq (λ(a : A) → ResT x a (refl A x)) fx
    in result1
 -}
in  { LeibnizEqual
    , reflexivity = refl
    , symmetry
    , transitivity
    , extensional_equality
    , toAssertType
    -- , eliminatorJ
    }
