let Integer/add =
      https://prelude.dhall-lang.org/Integer/add
        sha256:7da1306a0bf87c5668beead2a1db1b18861e53d7ce1f38057b2964b649f59c3b

let ListInt = ∀(r : Type) → r → (Integer → r → r) → r

let nil
    : ListInt
    = λ(r : Type) → λ(a1 : r) → λ(a2 : Integer → r → r) → a1

let cons
    : Integer → ListInt → ListInt
    = λ(n : Integer) →
      λ(c : ListInt) →
      λ(r : Type) →
      λ(a1 : r) →
      λ(a2 : Integer → r → r) →
        a2 n (c r a1 a2)

let headOptional
    : ListInt → Optional Integer
    = λ(c : ListInt) →
        c
          (Optional Integer)
          (None Integer)
          ( λ(i : Integer) →
            λ(_ : Optional Integer) →
              Some i
          )

let _ = assert : headOptional nil ≡ None Integer
let _ = assert : headOptional (cons +123 nil) ≡ Some +123
let _ = assert : headOptional (cons -456 (cons +123 nil)) ≡ Some -456
let _ = assert : headOptional (cons +789 (cons -456 (cons +123 nil))) ≡ Some +789


let lastOptional
    : ListInt → Optional Integer
    = λ(c : ListInt) →
        c
          (Optional Integer)
          (None Integer)
          ( λ(i : Integer) →
            λ(o : Optional Integer) →
              merge { None = Some i, Some = λ(prev : Integer) → Some prev } o
          )

let _ = assert : lastOptional nil ≡ None Integer
let _ = assert : lastOptional (cons +123 nil) ≡ Some +123
let _ = assert : lastOptional (cons -456 (cons +123 nil)) ≡ Some +123
let _ = assert : lastOptional (cons +789 (cons -456 (cons +123 nil))) ≡ Some +123


let ListInt/sum
    : ListInt → Integer
    = λ(list : ListInt) →
        list
          Integer
          +0
          (λ(i : Integer) → λ(prev : Integer) → Integer/add i prev)

let _ = assert : ListInt/sum (cons +1 (cons +2 nil)) ≡ +3

let ListInt/concat
    : ListInt → ListInt → ListInt
    = λ(left : ListInt) →
      λ(right : ListInt) →
        left ListInt right (λ(i : Integer) → λ(prev : ListInt) → cons i prev)

let _ =
        assert
      :   ListInt/concat (cons +1 (cons +2 nil)) (cons +3 nil)
        ≡ cons +1 (cons +2 (cons +3 nil))

in  { ListInt, ListInt/sum, ListInt/concat, cons, headOptional, nil }
