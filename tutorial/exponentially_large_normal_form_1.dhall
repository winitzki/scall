let Integer/positive =      https://prelude.dhall-lang.org/Integer/positive
let Integer/abs =      https://prelude.dhall-lang.org/Integer/abs
let Integer/add =      https://prelude.dhall-lang.org/Integer/add
let Integer/subtract =      https://prelude.dhall-lang.org/Integer/subtract
let TorsorType = { x : Natural, y : Natural }

let torsor1 : Integer → TorsorType
  = λ(i : Integer) →
            if    Integer/positive i
            then  { x = Integer/clamp i, y = 0 }
            else  { x = 0, y = Integer/abs i }

let InputType = { a : Integer, b : Integer }

let torsor2 : InputType → TorsorType
  = λ(input : InputType) →
            torsor1 (Integer/subtract input.a input.b)

let torsor4 : Integer → Integer → Integer → Integer → TorsorType
  = λ(i : Integer) →λ(j : Integer) → λ(k : Integer) →λ(l : Integer) →
        torsor2 { a = Integer/add i j, b = Integer/add k l }

in torsor4
