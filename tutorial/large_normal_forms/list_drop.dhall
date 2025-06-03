-- Normal form explosion when applying List/drop to a literal list.
let List/drop = https://prelude.dhall-lang.org/List/drop

let List/replicate = https://prelude.dhall-lang.org/List/replicate

let data = List/replicate 15 Natural 1

in  λ(n : Natural) → List/drop n Natural data
