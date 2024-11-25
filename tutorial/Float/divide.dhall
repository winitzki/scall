let T = ./Type.dhall

let Integer/subtract =
      https://prelude.dhall-lang.org/Integer/subtract
        sha256:a34d36272fa8ae4f1ec8b56222fe8dc8a2ec55ec6538b840de0cbe207b006fda

let stop = ./reduce_growth.dhall

let Float = T.Float

let Float/isZero = T.Float/isZero

let Float/pad = T.Float/pad

let Float/zero = T.Float/zero

let divmod = T.divmod

let Float/create = T.Float/create

let Float/round = (./rounding.dhall).Float/round

let Float/divide
                 -- Float/divide a b means to divide a / b, like divmod.
                 =
      λ(a : Float) →
      λ(b : Float) →
      λ(prec : Natural) →
        if    Float/isZero a || Float/isZero b
        then  Float/zero
        else  let padding = Natural/subtract a.topPower (prec + b.topPower)

              let aPadded = Float/pad a padding

              let mantissaUnsigned =
                    Natural/toInteger (divmod aPadded.mantissa b.mantissa).div

              let mantissaApplySign =
                    if    a.mantissaPositive == b.mantissaPositive
                    then  λ(x : Integer) → x
                    else  Integer/negate

              let exponent = Integer/subtract b.exponent aPadded.exponent

              in  Float/round
                    (Float/create (mantissaApplySign mantissaUnsigned) exponent)
                    prec

in  Float/divide
