let T = ./Type.dhall

let Float = T.Float

let Float/isZero = T.Float/isZero

let Float/multiply =
      \(a : Float) ->
      \(b : Float) ->
      \(prec : Natural) ->
        if Float/isZero a || Float/isZero b then Float/zero else Float/zero

in  { Float/multiply }
