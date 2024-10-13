let T = ./Type.dhall

let Float = T.Float

let Float/isZero = T.Float/isZero

let Float/zero = T.Float/zero

let Float/create = T.Float/create

let Float/round = (./rounding.dhall).Float/round

let Float/multiply =
      λ(a : Float) →
      λ(b : Float) →
      λ(prec : Natural) →
        if    Float/isZero a || Float/isZero b
        then  Float/zero
        else  let mantissaUnsigned = Natural/toInteger (a.mantissa * b.mantissa)

              let mantissaApplySign =
                    if    a.mantissaPositive == b.mantissaPositive
                    then  λ(x : Integer) → x
                    else  Integer/negate

              let exponent = a.exponent + b.exponent

              in  Float/round
                    ( Float/create
                        (mantissaApplySign mantissaUnsigned)
                        (Natural/toInteger exponent)
                    )
                    prec

in  { Float/multiply }
