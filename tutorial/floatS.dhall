-- Floating-point computations. Numbers are represented by mantissa, exponent, and signs.
-- The precision is set via the variable `Digits`.
-- This is a proof of concept. Performance will be very slow.
let D =
      ./division.dhall
        sha256:857179c7a39a87159955b75efbeb39c70bddfa3fd47e44a6267c5b64e38d4bf1

let Result = { div : Natural, rem : Natural }

let divmod
    : Natural → Natural → Result
    = (./EgyptianDivision.dhall).egyptian_div_mod

let List/take =
      https://prelude.dhall-lang.org/List/take
        sha256:b3e08ee8c3a5bf3d8ccee6b2b2008fbb8e51e7373aef6f1af67ad10078c9fbfa

let List/drop =
      https://prelude.dhall-lang.org/List/drop
        sha256:af983ba3ead494dd72beed05c0f3a17c36a4244adedf7ced502c6512196ed0cf

let List/replicate =
      https://prelude.dhall-lang.org/List/replicate
        sha256:d4250b45278f2d692302489ac3e78280acb238d27541c837ce46911ff3baa347

let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let Natural/lessThanEqual =
      https://prelude.dhall-lang.org/Natural/lessThanEqual
        sha256:1a5caa2b80a42b9f58fff58e47ac0d9a9946d0b2d36c54034b8ddfe3cb0f3c99

let Matural/min =
      https://prelude.dhall-lang.org/Natural/min
        sha256:f25f9c462e4dbf0eb15f9ff6ac840c6e9c82255a7f4f2ab408bdab338e028710

let Matural/max =
      https://prelude.dhall-lang.org/Natural/max
        sha256:1f3b18da330223ab039fad11693da72c7e68d516f50502c73f41a89a097b62f7

let Integer/subtract =
      https://prelude.dhall-lang.org/Integer/subtract
        sha256:a34d36272fa8ae4f1ec8b56222fe8dc8a2ec55ec6538b840de0cbe207b006fda

let Integer/positive =
      https://prelude.dhall-lang.org/Integer/positive
        sha256:7bdbf50fcdb83d01f74c7e2a92bf5c9104eff5d8c5b4587e9337f0caefcfdbe3

let Integer/equal =
      https://prelude.dhall-lang.org/Integer/equal
        sha256:2d99a205086aa77eea17ae1dab22c275f3eb007bccdc8d9895b93497ebfc39f8

let Integer/add =
      https://prelude.dhall-lang.org/Integer/add
        sha256:7da1306a0bf87c5668beead2a1db1b18861e53d7ce1f38057b2964b649f59c3b

let Bool/not =
      https://prelude.dhall-lang.org/Bool/not
        sha256:723df402df24377d8a853afed08d9d69a0a6d86e2e5b2bac8960b0d4756c7dc4

let Integer/abs =
      https://prelude.dhall-lang.org/Integer/abs
        sha256:35212fcbe1e60cb95b033a4a9c6e45befca4a298aa9919915999d09e69ddced1

let Text/concat =
      https://prelude.dhall-lang.org/Text/concat
        sha256:731265b0288e8a905ecff95c97333ee2db614c39d69f1514cb8eed9259745fc0

let Base = 10

let HalfBase = (divmod Base 2).div

let MaxPrintedWithoutExponent = 3

let Digits = 3

let FloatExtraData =
      { leadDigit : Natural, topPower : Natural, remaining : Natural }

let FloatBare =
      { mantissa : Natural
      , exponent : Natural
      , exponentPositive : Bool
      , mantissaPositive : Bool
      }

let Float = FloatBare ⩓ FloatExtraData

let Float/addExtraData
    : FloatBare → Float
    = λ(args : FloatBare) →
        let topPower = D.log Base args.mantissa

        let r = divmod args.mantissa (D.power Base topPower)

        in  { topPower, leadDigit = r.div, remaining = r.rem } ∧ args

let FloatBare/create
    : Integer → Integer → FloatBare
    = λ(x : Integer) →
      λ(exp : Integer) →
        { mantissa = Integer/abs x
        , mantissaPositive = Integer/positive x
        , exponent = Integer/abs exp
        , exponentPositive = Integer/positive exp
        }

let Float/zero = Float/addExtraData (FloatBare/create +0 +0)

let normalizeStep
    : FloatBare → FloatBare
    = λ(x : FloatBare) →
        if    Natural/isZero x.mantissa
        then  Float/zero.(FloatBare)
        else  if Natural/lessThan x.mantissa Base
        then  x
        else  let r = divmod x.mantissa Base

              in  if    Natural/isZero r.rem
                  then    x
                        ⫽ { mantissa = r.div }
                        ⫽ ( if        Natural/isZero x.exponent
                                  ||  x.exponentPositive
                            then  { exponent = x.exponent + 1
                                  , exponentPositive = True
                                  }
                            else  if Natural/lessThan x.exponent 2
                            then  { exponent = 0, exponentPositive = True }
                            else  { exponent = Natural/subtract 1 x.exponent
                                  , exponentPositive = False
                                  }
                          )
                  else  x

let _ = assert : normalizeStep Float/zero.(FloatBare) ≡ Float/zero.(FloatBare)

let _ = assert : normalizeStep (FloatBare/create -0 -1) ≡ Float/zero.(FloatBare)

let FloatBare/normalize
    : FloatBare → FloatBare
    = λ(args : FloatBare) →
        Natural/fold (1 + args.mantissa) FloatBare normalizeStep args

let _ =
        assert
      : FloatBare/normalize (FloatBare/create +0 +0) ≡ FloatBare/create +0 +0

let _ =
        assert
      : FloatBare/normalize (FloatBare/create +0 -0) ≡ FloatBare/create +0 +0

let _ =
        assert
      : FloatBare/normalize (FloatBare/create +1 +1) ≡ FloatBare/create +1 +1

let _ =
        assert
      : FloatBare/normalize (FloatBare/create +1 +0) ≡ FloatBare/create +1 +0

let _ =
        assert
      : FloatBare/normalize (FloatBare/create +100 +0) ≡ FloatBare/create +1 +2

let _ =
        assert
      : FloatBare/normalize (FloatBare/create +100 +0) ≡ FloatBare/create +1 +2

let _ =
        assert
      :   FloatBare/normalize (FloatBare/create -100100 -100)
        ≡ FloatBare/create -1001 -98

let _ =
        assert
      : FloatBare/normalize (FloatBare/create -0 -1) ≡ FloatBare/create +0 +0

let _ =
        assert
      : FloatBare/normalize (FloatBare/create +0 -1) ≡ FloatBare/create +0 +0

let Float/create
    : Integer → Integer → Float
    = λ(x : Integer) →
      λ(exp : Integer) →
        Float/addExtraData (FloatBare/normalize (FloatBare/create x exp))

let _ = assert : Float/create +0 +0 ≡ Float/zero

let Float/normalize
    : Float → Float
    = λ(f : Float) → Float/addExtraData (FloatBare/normalize f.(FloatBare))

let showSign = λ(x : Bool) → if x then "+" else "-"

let _ = assert : Natural/lessThan 1 Base ≡ True

let _ = assert : Natural/lessThan 1 Digits ≡ True

let MaxBase = D.power Base Digits

let AssertLessThan =
      λ(x : Natural) →
      λ(limit : Natural) →
        if    Natural/lessThan x limit
        then  {}
        else  { error :
                    Natural/show x
                  ≡ "Invalid argument ${Natural/show
                                          x}, must be less than ${Natural/show
                                                                    limit}."
              }

let Text/repeat =
      λ(n : Natural) →
      λ(x : Text) →
        Natural/fold n Text (λ(a : Text) → a ++ x) ""

let Float/positive =
      λ(x : Float) → x.mantissaPositive || Natural/isZero x.mantissa

let Float/isZero = λ(x : Float) → Natural/isZero x.mantissa

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

let Compared = < Equal | Greater | Less >

let Natural/compare
    : Natural → Natural → Compared
    = λ(x : Natural) →
      λ(y : Natural) →
        if    Natural/isZero (Natural/subtract x y)
        then  if    Natural/isZero (Natural/subtract y x)
              then  Compared.Equal
              else  Compared.Greater
        else  Compared.Less

let Compared/reverse =
      λ(x : Compared) →
        merge
          { Equal = Compared.Equal
          , Greater = Compared.Less
          , Less = Compared.Greater
          }
          x

let _ = assert : Natural/compare 10 20 ≡ Compared.Less

let _ = assert : Natural/compare 20 20 ≡ Compared.Equal

let _ = assert : Natural/compare 20 10 ≡ Compared.Greater

let Float/abs
    : Float → Float
    = λ(x : Float) → x ⫽ { mantissaPositive = True }

let Float/compareUnsigned =
      λ(a : Float) →
      λ(b : Float) →
        if    Float/isZero a
        then  if Float/isZero b then Compared.Equal else Compared.Less
        else  if Float/isZero b
        then  Compared.Greater
        else  let fixedExponents
                  : { x : Natural, y : Natural }
                  = if    a.exponentPositive
                    then  if    b.exponentPositive
                          then  { x = a.exponent + a.topPower
                                , y = b.exponent + b.topPower
                                }
                          else  { x = a.exponent + a.topPower + b.exponent
                                , y = b.topPower
                                }
                    else  if b.exponentPositive
                    then  { x = a.topPower
                          , y = a.exponent + b.exponent + b.topPower
                          }
                    else  { x = b.exponent + a.topPower
                          , y = a.exponent + b.topPower
                          }

              in  if    Natural/lessThan fixedExponents.x fixedExponents.y
                  then  Compared.Less
                  else  if Natural/lessThan fixedExponents.y fixedExponents.x
                  then  Compared.Greater
                  else  let fixed
                            : { x : Natural, y : Natural }
                            = if    a.exponentPositive
                              then  if    b.exponentPositive
                                    then  { x =
                                                a.mantissa
                                              * D.power Base a.exponent
                                          , y =
                                                b.mantissa
                                              * D.power Base b.exponent
                                          }
                                    else  { x =
                                                a.mantissa
                                              * D.power
                                                  Base
                                                  (a.exponent + b.exponent)
                                          , y = b.mantissa
                                          }
                              else  if b.exponentPositive
                              then  { x = a.mantissa
                                    , y =
                                          b.mantissa
                                        * D.power Base (b.exponent + a.exponent)
                                    }
                              else  { x = a.mantissa * D.power Base b.exponent
                                    , y = b.mantissa * D.power Base a.exponent
                                    }

                        in  Natural/compare fixed.x fixed.y

let Float/compare
    : Float → Float → Compared
    = λ(x : Float) →
      λ(y : Float) →
        if    x.mantissaPositive
        then  if    y.mantissaPositive
              then  Float/compareUnsigned x y
              else  Compared.Greater
        else  if y.mantissaPositive
        then  Compared.Less
        else  Float/compareUnsigned y x

let _ =
        assert
      :   Float/compare (Float/create +123 +0) (Float/create +12 +1)
        ≡ Compared.Greater

let _ =
        assert
      :   Float/compare (Float/create +123 +0) (Float/create +12 +2)
        ≡ Compared.Less

let _ =
        assert
      :   Float/compare (Float/create +120 +0) (Float/create +12 +1)
        ≡ Compared.Equal

let _ =
        assert
      :   Float/compare (Float/create +123 -100) (Float/create +12 -99)
        ≡ Compared.Greater

let _ =
        assert
      :   Float/compare (Float/create +123 -100) (Float/create +12 -98)
        ≡ Compared.Less

let _ =
        assert
      :   Float/compare (Float/create +120 -100) (Float/create +12 -99)
        ≡ Compared.Equal

let _ =
        assert
      :   Float/compare (Float/create +120 -100) (Float/create -12 -99)
        ≡ Compared.Greater

let _ =
        assert
      :   Float/compare (Float/create -120 -100) (Float/create +12 -99)
        ≡ Compared.Less

let Float/roundDownward =
      λ(a : Float) →
      λ(prec : Natural) →
        if    Natural/lessThan a.topPower prec
        then  a
        else  let power = D.power Base (Natural/subtract prec (a.topPower + 1))

              let roundLastDigits = (divmod a.mantissa power).div * power

              in  Float/normalize (a ⫽ { mantissa = roundLastDigits })

let _ =
        assert
      : Float/roundDownward (Float/create +12341 +0) 4 ≡ Float/create +12340 +0

let _ =
        assert
      : Float/roundDownward (Float/create +12341 +0) 5 ≡ Float/create +12341 +0

let _ =
        assert
      : Float/roundDownward (Float/create +12341 -10) 4 ≡ Float/create +1234 -9

let _ = assert : Float/roundDownward (Float/create +12341 +0) 0 ≡ Float/zero

let Float/round =
      λ(a : Float) →
      λ(prec : Natural) →
        if    Natural/lessThan a.topPower prec
        then  a
        else  let powerMinus1 = D.power Base (Natural/subtract prec a.topPower)

              let roundLastDigits =
                      ( divmod
                          (a.mantissa + HalfBase * powerMinus1)
                          (powerMinus1 * Base)
                      ).div
                    * Base
                    * powerMinus1

              in  Float/normalize (a ⫽ { mantissa = roundLastDigits })

let _ = assert : Float/round (Float/create +12345 +0) 4 ≡ Float/create +12350 +0

let _ = assert : Float/round (Float/create +12345 +0) 5 ≡ Float/create +12345 +0

let _ = assert : Float/round (Float/create +12345 -10) 4 ≡ Float/create +1235 -9

let _ = assert : Float/round (Float/create +12345 +0) 0 ≡ Float/zero

let Float/addUnsigned
    : Float → Float → Natural → Float
    = λ(a : Float) → λ(b : Float) → λ(prec : Natural) → Float/zero

in  { T = Float
    , base = Base
    , digits = Digits
    , show = Float/show
    , create = Float/create
    , isPositive = Float/positive
    , compare = Float/compare
    , Compared
    , abs = Float/abs
    , isZero = Float/isZero
    , roundDownward = Float/roundDownward
    , round = Float/round
    , doc =
        ''
        The type `Float` represents floating-point numbers at base = ${Natural/show
                                                                         Base}.
        ''
    }
