-- Floating-point computations with few digits of precision.
-- This is a proof of concept. Performance will be very slow for high precision.
let Float = { mantissa : Integer, exponent : Integer }

let Digits = 3

let Float/show
    : Float → Text
    = λ(x : Float) → "TODO"

in  True