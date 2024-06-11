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

in  { LeibnizEqual, refl, symmetry, transitivity, extensional_equality }
