let HList = ∀(x : Type) → x → (x → Type → x) → x

let nil = λ(x : Type) → λ(n : x) → λ(upd : x → Type → x) → n

let cons =
      λ(t : Type) →
      λ(h : HList) →
      λ(x : Type) →
      λ(n : x) →
      λ(upd : x → Type → x) →
        upd (h x n upd) t

let example1
    : HList
    = cons Natural (cons Bool nil)

in  True
