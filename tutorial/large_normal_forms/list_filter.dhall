-- Normal form explosion when applying List/filter to a literal list.
let List/filter = https://prelude.dhall-lang.org/List/filter

let Natural/equal = https://prelude.dhall-lang.org/Natural/equal

let data =
      [ "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n" ]

let T = { index : Natural, value : Text }

let indexed
    : List T
    = List/indexed Text data

let lookup1 =
      λ(n : Natural) →
        List/filter T (λ(t : T) → Natural/equal t.index n) indexed

in  lookup1
