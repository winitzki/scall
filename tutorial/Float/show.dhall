let Text/concat =
      https://prelude.dhall-lang.org/Text/concat
        sha256:731265b0288e8a905ecff95c97333ee2db614c39d69f1514cb8eed9259745fc0

let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let Natural/lessThanEqual =
      https://prelude.dhall-lang.org/Natural/lessThanEqual
        sha256:1a5caa2b80a42b9f58fff58e47ac0d9a9946d0b2d36c54034b8ddfe3cb0f3c99

let T = ./Type.dhall

let divmod = T.divmod

let Float = T.Float

let Float/isZero = T.Float/isZero

let Float/create = T.Float/create

let Float/normalize = T.Float/normalize

let Base = T.Base

let D = ./Arithmetic.dhall

let MaxPrintedWithoutExponent = 3

let Digits = 3

let showSign = λ(x : Bool) → if x then "+" else "-"

let _ = assert : Natural/lessThan 1 Base ≡ True

let _ = assert : Natural/lessThan 1 Digits ≡ True

let MaxBase = D.power Base Digits

let Text/repeat =
      λ(n : Natural) →
      λ(x : Text) →
        Natural/fold n Text (λ(a : Text) → a ++ x) ""

let maxDisplayedInteger = D.power Base (MaxPrintedWithoutExponent + 1)

let padRemainingDigits
    : { digits : Natural, length : Natural } → Text
    = λ(args : { digits : Natural, length : Natural }) →
        if    Natural/isZero args.digits
        then  ""
        else  let remainingPower = 1 + D.log Base args.digits

              let padding =
                    Text/repeat
                      (Natural/subtract remainingPower args.length)
                      "0"

              in  padding ++ Natural/show args.digits

let _ = assert : padRemainingDigits { digits = 123, length = 3 } ≡ "123"

let _ = assert : padRemainingDigits { digits = 123, length = 4 } ≡ "0123"

let Float/showNormalized
    : Float → Text
    = λ(f : Float) →
        if    Natural/isZero f.mantissa
        then  "0."
        else  let `number is above 1000 with positive exponent, so print as 1.234...e+...` =
                    λ(f : Float) →
                      Text/concat
                        [ Natural/show f.leadDigit
                        , "."
                        , padRemainingDigits
                            { digits = f.remaining, length = f.topPower }
                        , "e+"
                        , Natural/show (f.topPower + f.exponent)
                        ]

              let `number is above 1000 despite negative exponent, so print as 1.234...e+...` =
                    λ(f : Float) →
                      Text/concat
                        [ Natural/show f.leadDigit
                        , "."
                        , padRemainingDigits
                            { digits = f.remaining, length = f.topPower }
                        , "e+"
                        , Natural/show (Natural/subtract f.exponent f.topPower)
                        ]

              let `number is above 1 but below 1000, so print it as 123.456...` =
                    λ(f : Float) →
                      let r = divmod f.mantissa (D.power Base f.exponent)

                      let headDigits = r.div

                      let remaining =
                            padRemainingDigits
                              { digits = r.rem, length = f.exponent }

                      in  Text/concat
                            [ Natural/show headDigits, ".", remaining ]

              let `number is below 1/1000, so print it as 1.23...e-...` =
                    λ(f : Float) →
                      let newExponent = Natural/subtract f.topPower f.exponent

                      in  Text/concat
                            [ Natural/show f.leadDigit
                            , "."
                            , padRemainingDigits
                                { digits = f.remaining, length = f.topPower }
                            , "e-"
                            , Natural/show newExponent
                            ]

              let `number is above 1/1000 but below 1, so print the number as 0.00123...` =
                    λ(f : Float) →
                      Text/concat
                        [ "0."
                        , padRemainingDigits
                            { digits = f.mantissa, length = f.exponent }
                        ]

              let rest =
                    if    f.exponentPositive || Natural/isZero f.exponent
                    then  let largeInteger =
                                f.mantissa * D.power Base f.exponent

                          in  if    Natural/lessThan
                                      largeInteger
                                      maxDisplayedInteger
                              then  Text/concat
                                      [ Natural/show largeInteger, "." ]
                              else  `number is above 1000 with positive exponent, so print as 1.234...e+...`
                                      f
                    else  if Natural/lessThan
                               (MaxPrintedWithoutExponent + f.exponent)
                               f.topPower
                    then  `number is above 1000 despite negative exponent, so print as 1.234...e+...`
                            f
                    else  if Natural/lessThanEqual f.exponent f.topPower
                    then  `number is above 1 but below 1000, so print it as 123.456...`
                            f
                    else  if Natural/lessThan
                               (f.topPower + MaxPrintedWithoutExponent)
                               f.exponent
                    then  `number is below 1/1000, so print it as 1.23...e-...`
                            f
                    else  `number is above 1/1000 but below 1, so print the number as 0.00123...`
                            f

              in  Text/concat [ showSign f.mantissaPositive, rest ]

let _ =
      let x = Float/create +100001 -1

      in  let _ = assert : x.topPower ≡ 5

          let _ = assert : x.leadDigit ≡ 1

          let _ = assert : x.exponent ≡ 1

          in  assert : x.remaining ≡ 1

let _ = assert : Float/isZero (Float/create +0 +1) ≡ True

let _ = assert : Float/isZero (Float/create -0 +1) ≡ True

let _ = assert : Float/isZero (Float/create +1 -100) ≡ False

let _ = assert : Float/isZero (Float/create -1 -100) ≡ False

let _ = assert : Float/normalize (Float/create +1 +1) ≡ Float/create +1 +1

let _ = assert : Float/normalize (Float/create +1 +0) ≡ Float/create +1 +0

let _ = assert : Float/normalize (Float/create +100 +0) ≡ Float/create +1 +2

let _ = assert : Float/normalize (Float/create +0 -1) ≡ Float/create +0 +0

let _ = assert : Float/normalize (Float/create -0 -1) ≡ Float/create +0 +0

let _ = assert : Float/normalize (Float/create +0 -0) ≡ Float/create +0 +0

let _ = assert : Float/normalize (Float/create +100 +0) ≡ Float/create +1 +2

let _ =
        assert
      : Float/normalize (Float/create -100100 -100) ≡ Float/create -1001 -98

let Float/show = λ(f : Float) → Float/showNormalized (Float/normalize f)

let test_show = λ(x : Integer) → λ(e : Integer) → Float/show (Float/create x e)

let _ = assert : test_show +0 -1234 ≡ "0."

let _ = assert : test_show -0 -1234 ≡ "0."

let _ = assert : test_show +1 -0 ≡ "+1."

let _ = assert : test_show -1 -0 ≡ "-1."

let _ = assert : test_show +10 -0 ≡ "+10."

let _ = assert : test_show -10 -0 ≡ "-10."

let _ = assert : test_show +10 +1 ≡ "+100."

let _ = assert : test_show -10 +1 ≡ "-100."

let _ = assert : test_show +10 +2 ≡ "+1000."

let _ = assert : test_show -10 +2 ≡ "-1000."

let _ = assert : test_show +10 +3 ≡ "+1.e+4"

let _ = assert : test_show -10 +3 ≡ "-1.e+4"

let _ = assert : test_show +10 -1 ≡ "+1."

let _ = assert : test_show -10 -1 ≡ "-1."

let _ = assert : test_show +100 -1 ≡ "+10."

let _ = assert : test_show -100 -1 ≡ "-10."

let _ = assert : test_show +12 -1 ≡ "+1.2"

let _ = assert : test_show +100000 -1 ≡ "+1.e+4"

let _ = assert : test_show -100000 -1 ≡ "-1.e+4"

let _ = assert : test_show +100001 -1 ≡ "+1.00001e+4"

let _ = assert : test_show -100001 -1 ≡ "-1.00001e+4"

let _ = assert : test_show +110001 -1 ≡ "+1.10001e+4"

let _ = assert : test_show -110001 -1 ≡ "-1.10001e+4"

let _ = assert : test_show +123456789 -8 ≡ "+1.23456789"

let _ = assert : test_show -123456789 -8 ≡ "-1.23456789"

let _ = assert : test_show +123456789 -9 ≡ "+0.123456789"

let _ = assert : test_show -123456789 -9 ≡ "-0.123456789"

let _ = assert : test_show +11 -4 ≡ "+0.0011"

let _ = assert : test_show +1 -3 ≡ "+0.001"

let _ = assert : test_show +10 -4 ≡ "+0.001"

let _ = assert : test_show +10 -2 ≡ "+0.1"

let _ = assert : test_show +123456789 -10 ≡ "+0.0123456789"

let _ = assert : test_show -123456789 -10 ≡ "-0.0123456789"

let _ = assert : test_show +123456789 -11 ≡ "+0.00123456789"

let _ = assert : test_show -123456789 -11 ≡ "-0.00123456789"

let _ = assert : test_show +123456789 -12 ≡ "+1.23456789e-4"

let _ = assert : test_show -123456789 -12 ≡ "-1.23456789e-4"

let _ = assert : test_show +101 -1 ≡ "+10.1"

let _ = assert : test_show -101 -1 ≡ "-10.1"

let _ = assert : test_show +1001 -1 ≡ "+100.1"

let _ = assert : test_show -1001 -1 ≡ "-100.1"

let _ = assert : test_show +10001 -1 ≡ "+1000.1"

let _ = assert : test_show -10001 -1 ≡ "-1000.1"

let _ = assert : test_show +110000 -1 ≡ "+1.1e+4"

let _ = assert : test_show -110000 -1 ≡ "-1.1e+4"

in  { Float/show }