let Text/concat =
      https://prelude.dhall-lang.org/Text/concat
        sha256:731265b0288e8a905ecff95c97333ee2db614c39d69f1514cb8eed9259745fc0

let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let Natural/lessThanEqual =
      https://prelude.dhall-lang.org/Natural/lessThanEqual
        sha256:1a5caa2b80a42b9f58fff58e47ac0d9a9946d0b2d36c54034b8ddfe3cb0f3c99

let Integer/abs =
      https://prelude.dhall-lang.org/abs
        sha256:35212fcbe1e60cb95b033a4a9c6e45befca4a298aa9919915999d09e69ddced1

let Integer/nonNegative =
      https://prelude.dhall-lang.org/Integer/nonNegative
        sha256:b463373f070df6b1c8c7082051e0810fee38b360bab35256187c8c2b6af5c663

let stop = ./reduce_growth.dhall

let T = ./Type.dhall

let divmod = T.divmod

let Float = T.Float

let Float/isZero = T.Float/isZero

let Float/create = T.Float/create

let Float/normalize = T.Float/normalize

let Base = T.Base

let MaxPrintedWithoutExponent = 3

let Digits = 3

let showSign = λ(x : Bool) → if x then "+" else "-"

let _ = assert : Natural/lessThan 1 Base ≡ True

let _ = assert : Natural/lessThan 1 Digits ≡ True

let Text/repeat =
      λ(n : Natural) →
      λ(x : Text) →
        Natural/fold n Text (λ(a : Text) → a ++ x) ""

let padRemainingDigits
    : { digits : Natural, length : Natural } → Text
    = λ(args : { digits : Natural, length : Natural }) →
        if    Natural/isZero args.digits
        then  ""
        else  let remainingPower = 1 + T.log Base args.digits

              let padding =
                    Text/repeat
                      (Natural/subtract remainingPower args.length)
                      "0"

              in  padding ++ Natural/show args.digits

let _ = assert : padRemainingDigits { digits = 123, length = 3 } ≡ "123"

let _ = assert : padRemainingDigits { digits = 123, length = 4 } ≡ "0123"

let Float/showNormalized
    : Float → Text
    = stop.reduce_growth
        Float
        (λ(x : Float) → stop.predicate_Natural x.mantissa)
        Text
        ""
        ( λ(f : Float) →
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
                            , Natural/show (f.topPower + Integer/abs f.exponent)
                            ]

                  let `number is above 1000 despite negative exponent, so print as 1.234...e+...` =
                        λ(f : Float) →
                          Text/concat
                            [ Natural/show f.leadDigit
                            , "."
                            , padRemainingDigits
                                { digits = f.remaining, length = f.topPower }
                            , "e+"
                            , Natural/show
                                ( Natural/subtract
                                    (Integer/abs f.exponent)
                                    f.topPower
                                )
                            ]

                  let `number is above 1 but below 1000, so print it as 123.456...` =
                        λ(f : Float) →
                          let r =
                                divmod
                                  f.mantissa
                                  (T.power Base (Integer/abs f.exponent))

                          let headDigits = r.div

                          let remaining =
                                padRemainingDigits
                                  { digits = r.rem
                                  , length = Integer/abs f.exponent
                                  }

                          in  Text/concat
                                [ Natural/show headDigits, ".", remaining ]

                  let `number is below 1/1000, so print it as 1.23...e-...` =
                        λ(f : Float) →
                          let newExponent =
                                Natural/subtract
                                  f.topPower
                                  (Integer/abs f.exponent)

                          in  Text/concat
                                [ Natural/show f.leadDigit
                                , "."
                                , padRemainingDigits
                                    { digits = f.remaining
                                    , length = f.topPower
                                    }
                                , "e-"
                                , Natural/show newExponent
                                ]

                  let `number is above 1/1000 but below 1, so print the number as 0.00123...` =
                        λ(f : Float) →
                          Text/concat
                            [ "0."
                            , padRemainingDigits
                                { digits = f.mantissa
                                , length = Integer/abs f.exponent
                                }
                            ]

                  let `number is integer and below 1000, so print the number as integer` =
                        λ(f : Float) →
                          Text/concat
                            [ Natural/show
                                (   T.power Base (Integer/clamp f.exponent)
                                  * f.mantissa
                                )
                            , "."
                            ]

                  let print_number =
                        if    Integer/nonNegative f.exponent
                        then  let largeIntegerLog =
                                    f.topPower + Integer/clamp f.exponent

                              in  if    Natural/lessThanEqual
                                          largeIntegerLog
                                          MaxPrintedWithoutExponent
                                  then  `number is integer and below 1000, so print the number as integer`
                                  else  `number is above 1000 with positive exponent, so print as 1.234...e+...`
                        else  if Natural/lessThan
                                   (   MaxPrintedWithoutExponent
                                     + Integer/abs f.exponent
                                   )
                                   f.topPower
                        then  `number is above 1000 despite negative exponent, so print as 1.234...e+...`
                        else  if Natural/lessThanEqual
                                   (Integer/abs f.exponent)
                                   f.topPower
                        then  `number is above 1 but below 1000, so print it as 123.456...`
                        else  if Natural/lessThan
                                   (f.topPower + MaxPrintedWithoutExponent)
                                   (Integer/abs f.exponent)
                        then  `number is below 1/1000, so print it as 1.23...e-...`
                        else  `number is above 1/1000 but below 1, so print the number as 0.00123...`

                  in  Text/concat
                        [ showSign f.mantissaPositive, print_number f ]
        )

let _ =
      let x = Float/create +100001 -1

      in  let _ = assert : x.topPower ≡ 5

          let _ = assert : x.leadDigit ≡ 1

          let _ = assert : x.exponent ≡ -1

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

in  Float/show
