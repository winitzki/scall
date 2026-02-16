let T =
      ./Type.dhall
        sha256:eb9b0c4b594668945020e2dc430bc312b998f90ff2b8f6ba2a861c2836c144c5

let Integer/add =
      https://prelude.dhall-lang.org/Integer/add
        sha256:7da1306a0bf87c5668beead2a1db1b18861e53d7ce1f38057b2964b649f59c3b

let stop =
      ./reduce_growth.dhall
        sha256:9129f3a6766ab3cc8435482c1aa3cb84ef1a6cee80636121e2d1b377b0551ecc

let Float = T.Float

let Float/isZero = T.Float/isZero

let Float/zero = T.Float/zero

let Float/create = T.Float/create

let Float/round =
      ( ./rounding.dhall
          sha256:b38a8d34468e4cab1e087f8ba6a9d92571dc847e6e8811cee35f4400c918aa5b
      ).Float/round

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

              let exponent = Integer/add a.exponent b.exponent

              in  Float/round
                    (Float/create (mantissaApplySign mantissaUnsigned) exponent)
                    prec

let _ =
        assert
      :   Float/multiply (Float/create +123456 +0) (Float/create +123456 +0) 4
        ≡ Float/create +1524 +7

in  Float/multiply
