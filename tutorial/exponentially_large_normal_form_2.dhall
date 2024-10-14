-- Given a pair of Integer values (a, b), the "torsor (a, b)" is any pair (x, y) of Natural numbers such that a - b = x - y.
let Integer/positive =
      https://prelude.dhall-lang.org/Integer/positive
        sha256:7bdbf50fcdb83d01f74c7e2a92bf5c9104eff5d8c5b4587e9337f0caefcfdbe3

let Integer/abs =
      https://prelude.dhall-lang.org/Integer/abs
        sha256:35212fcbe1e60cb95b033a4a9c6e45befca4a298aa9919915999d09e69ddced1

let Integer/add =
      https://prelude.dhall-lang.org/Integer/add
        sha256:7da1306a0bf87c5668beead2a1db1b18861e53d7ce1f38057b2964b649f59c3b

let Integer/subtract =
      https://prelude.dhall-lang.org/Integer/subtract
        sha256:a34d36272fa8ae4f1ec8b56222fe8dc8a2ec55ec6538b840de0cbe207b006fda

let stop = ./Float/reduce_growth.dhall

let InputType = { a : Integer, b : Integer }

let TorsorType = { x : Natural, y : Natural }

let zeroTorsor = { x = 0, y = 0 }

let computeTorsorSingle
    : Integer → TorsorType
    = {- -} stop.reduce_growth
        Integer
        stop.predicate_Integer
        TorsorType
        zeroTorsor
        {- -}( λ(i : Integer) →
            if    Integer/positive i
            then  { x = Integer/clamp i, y = 0 }
            else  { x = 0, y = Integer/abs i }
        )

let computeTorsor
    : InputType → TorsorType
    = {- -} stop.reduce_growth
        InputType
        (λ(input : InputType) → stop.predicate_Integer input.a)
        TorsorType
        zeroTorsor
        {- -} ( λ(input : InputType) →
            computeTorsorSingle (Integer/subtract input.b input.a)
        )

let computeTorsor4
    : Integer → Integer → Integer → Integer → TorsorType
    = λ(a : Integer) →
      λ(b : Integer) →
      λ(c : Integer) →
      λ(d : Integer) →
        computeTorsor { a = Integer/add a b, b = Integer/add b d }

in  { computeTorsorSingle, computeTorsor, computeTorsor4 }
