let T =
      ./Type.dhall
        sha256:eb9b0c4b594668945020e2dc430bc312b998f90ff2b8f6ba2a861c2836c144c5

let Integer/subtract =
      https://prelude.dhall-lang.org/Integer/subtract
        sha256:a34d36272fa8ae4f1ec8b56222fe8dc8a2ec55ec6538b840de0cbe207b006fda

let stop =
      ./reduce_growth.dhall
        sha256:9129f3a6766ab3cc8435482c1aa3cb84ef1a6cee80636121e2d1b377b0551ecc

let Float = T.Float

let Float/isZero = T.Float/isZero

let Float/pad = T.Float/pad

let Float/zero = T.Float/zero

let divmod = T.divmod

let Float/create = T.Float/create

let Float/round =
      ( ./rounding.dhall
          sha256:b38a8d34468e4cab1e087f8ba6a9d92571dc847e6e8811cee35f4400c918aa5b
      ).Float/round

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
