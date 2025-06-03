-- Normal form explosion when applying List/take to a literal list.
let List/take = https://prelude.dhall-lang.org/List/take

let List/replicate = https://prelude.dhall-lang.org/List/replicate

let data = List/replicate 15 Natural 1

in  λ(n : Natural) → List/take n Natural data
