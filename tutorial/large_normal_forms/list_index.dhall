-- Normal form explosion when applying List/index to a literal list.
let List/index = https://prelude.dhall-lang.org/List/index

let data =
      [ "a"
      , "b"
      , "c"
      , "d"
      , "e"
      , "f"
      , "g"
      , "h"
      , "i"
      , "j"
      , "k"
      , "l"
      , "m"
      , "n"
      , "o"
      , "p"
      , "q"
      ]

let lookup2
    : Natural → Optional Text
    = λ(n : Natural) → List/index n Text data

in  lookup2
