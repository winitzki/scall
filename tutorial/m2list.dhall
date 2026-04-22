-- Church encoding of List as a fixpoint of F a . F a where F a r = Optional (Pair a r)
let Pair = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }

let mkPair = λ(a : Type) →  λ(b : Type) → λ(x : a) → λ(y : b) → { _1 = x, _2 = y }

let F = λ(a : Type) → λ(r : Type) → Optional (Pair a r)

let FList = λ(a : Type) → ∀(r : Type) → (F a r → r) → r

let QList = λ(a : Type) → ∀(r : Type) → (F a (F a r) → r) → r

let qnil : ∀(a : Type) → QList a =  λ(a : Type) → λ(r : Type) → λ(f : F a (F a r) → r) → f (None (Pair a (F a r)))
-- this does not work!
let qcons = λ(a : Type) → λ(x : a) → λ(xs : QList a) → λ(r : Type) → λ(f : F a (F a r) → r) → f (Some { _1 = x, _2 = 9 })

let qhead = λ(a : Type) → λ(xs : QList a) → xs a a (λ(x : F a (F a a)) → x)

let qtail = λ(a : Type) → λ(xs : QList a) → xs a (QList a) (λ(x : F a (F a (QList a))) → x)

let qisnil = λ(a : Type) → λ(xs : QList a) → xs a Bool (λ(x : F a (F a Bool)) → x == None (Pair a (F a Bool)))

let qiscons = λ(a : Type) → λ(xs : QList a) → not (qisnil xs)

in  True
