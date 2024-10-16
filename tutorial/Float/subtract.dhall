let T = ./Type.dhall

let Float = T.Float

let Float/negate = T.Float/negate

let Float/add = ./add.dhall

let Float/subtract
    -- Subtracting b - a, like Natural/subtract.
    : Float → Float → Natural → Float
    = λ(a : Float) → Float/add (Float/negate a)

in  Float/subtract
