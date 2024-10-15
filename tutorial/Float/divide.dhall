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

let Float/show = (./show.dhall).Float/show

let check =
      λ(a : Integer) →
      λ(ae : Integer) →
      λ(b : Integer) →
      λ(be : Integer) →
      λ(prec : Natural) →
      λ(expected : Text) →
          Float/show (Float/divide (Float/create a ae) (Float/create b be) prec)
        ≡ expected

let _ = assert : check +123456 +0 +123456 +0 4 "+1."

let _ = assert : check +123456 +0 +123 +0 2 "+1000."

let _ = assert : check +123456 +0 +123 +0 10 "+1003.707317"

let _ = assert : check +1 +0 +3 +0 10 "+0.3333333333"

let _ = assert : check +1 +10 +3 +0 3 "+3.33e+9"

let _ = assert : check -1 +10 +3 +0 2 "-3.3e+9"

let _ = assert : check -1 +10 -3 +0 2 "+3.3e+9"

let _ = assert : check +1 +10 -3 +0 2 "-3.3e+9"

let _ = assert : check +1 -10 -3 +0 2 "-3.3e-11"

let _ = assert : check +1 +0 +3 +10 2 "+3.3e-11"

let _ = assert : check +1 +0 -3 -10000 2 "-3.3e+9999"

let _ = assert : check +1 +100000 -3 -100000 2 "-3.3e+199999"

let _ = assert : check +1 +0 +239 +0 20 "+0.004184100418410041841"

in  { Float/divide }
