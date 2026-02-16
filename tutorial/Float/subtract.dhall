let T =
      ./Type.dhall
        sha256:eb9b0c4b594668945020e2dc430bc312b998f90ff2b8f6ba2a861c2836c144c5

let Float = T.Float

let Float/negate = T.Float/negate

let Float/add =
      ./add.dhall
        sha256:e0ec80c5c98820b0c9166f75cdc96df64b570f795a392c257e109df1203d7b25

let Float/subtract
    -- Subtracting b - a, like Natural/subtract.
    : Float → Float → Natural → Float
    = λ(a : Float) → Float/add (Float/negate a)

in  Float/subtract
