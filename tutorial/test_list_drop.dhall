--| Remove first `n` elements of a list
let Natural/greaterThanEqual =
        missing
          sha256:30ebfab0febd7aa0ccccfdf3dc36ee6d50f0117f35dd4a9b034750b7e885a1a4
      ? https://prelude.dhall-lang.org/Natural/greaterThanEqual.dhall

let drop
    : ∀(n : Natural) → ∀(a : Type) → List a → List a
    = λ(n : Natural) →
      λ(a : Type) →
      λ(xs : List a) →
        List/fold
          { index : Natural, value : a }
          (List/indexed a xs)
          (List a)
          ( λ(x : { index : Natural, value : a }) →
            λ(xs : List a) →
              if    Natural/greaterThanEqual x.index n
              then  [ x.value ] # xs
              else  xs
          )
          ([] : List a)

let _ = assert : drop 2 Natural [ 2, 3, 5 ] ≡ [ 5 ]

let _ = assert : drop 5 Natural [ 2, 3, 5 ] ≡ ([] : List Natural)

let dropc
    : ∀(n : Natural) → ∀(a : Type) → List a → List a
    = λ(n : Natural) →
      λ(a : Type) →
      λ(xs : List a) →
        List/reverse
          a
          ( List/fold
              a
              (List/reverse a xs)
              { remains : Natural, result : List a }
              ( λ(x : a) →
                λ(acc : { remains : Natural, result : List a }) →
                  if    Natural/isZero acc.remains
                  then  acc // { result = [ x ] # acc.result }
                  else  acc // { remains = Natural/subtract 1 acc.remains }
              )
              { remains = n, result = [] : List a }
          ).result

let _ = assert : dropc 2 Natural [ 2, 3, 5 ] ≡ [ 5 ]

let _ = assert : dropc 5 Natural [ 2, 3, 5 ] ≡ ([] : List Natural)

let index : ∀(n : Natural) → ∀(a : Type) → List a → Optional a
    = λ(n : Natural) → λ(a : Type) → λ(xs : List a) → List/head a (drop n a xs)

let indexc =
      λ(n : Natural) → λ(a : Type) → λ(xs : List a) → List/head a (dropc n a xs)

let data
         -- let data = [ "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p" ]
         =
      [ "a", "b", "c", "d", "e"  ]

let lookup
    : Natural → Optional Text
    = λ(n : Natural) → indexc  n Text data

in  { drop, dropc, indexc, lookup }
