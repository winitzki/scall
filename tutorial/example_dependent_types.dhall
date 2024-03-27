let f
    : ∀(x : Bool) → Type
    = λ(x : Bool) → if x then Natural else Text

let g
    : ∀(x : Bool) → f x → Text
    = λ(x : Bool) → λ(y : f x) → if x then "" else y

let _ = g True 3

let _ = g False "abc"

in  True
