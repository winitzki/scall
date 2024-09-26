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

let Base = 10

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
    = λ(f : Float) → f ⫽ FloatBare/normalize f.(FloatBare)

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

let Float/showNormalized
    : Float → Text
    = λ(f : Float) →
        let mantissaSign = showSign f.mantissaPositive

        let exponentSign = showSign f.exponentPositive

        in  if    Natural/isZero f.mantissa
            then  "0."
            else  if f.exponentPositive || Natural/isZero f.exponent
            then  let largeInteger = f.mantissa * D.power Base f.exponent

                  in  if    Natural/lessThan largeInteger maxDisplayedInteger
                      then  "${mantissaSign}${Natural/show largeInteger}."
                      else  let remainingDigits
                                : Text
                                = if    Natural/isZero f.remaining
                                  then  ""
                                  else  let remainingPower =
                                              D.log Base f.remaining

                                        let padding =
                                              Text/repeat
                                                ( Natural/subtract
                                                    remainingPower
                                                    f.topPower
                                                )
                                                "0"

                                        in  padding ++ Natural/show f.remaining

                            in  "${mantissaSign}${Natural/show
                                                    f.leadDigit}.${remainingDigits}e+${Natural/show
                                                                                         f.topPower}"
            else  ""

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

let _ = assert : test_show +101 -1 ≡ "+10.1"

let _ = assert : test_show -101 -1 ≡ "-10.1"

let _ = assert : test_show +100 -1 ≡ "+10."

let _ = assert : test_show -100 -1 ≡ "-10."

let _ = assert : test_show +1001 -1 ≡ "+100.1"

let _ = assert : test_show -1001 -1 ≡ "-100.1"

let _ = assert : test_show +10001 -1 ≡ "+1000.1"

let _ = assert : test_show -10001 -1 ≡ "-1000.1"

let _ = assert : test_show +100001 -1 ≡ "+1.00001e+4"

let _ = assert : test_show -100001 -1 ≡ "-1.00001e+4"

let _ = assert : test_show +110001 -1 ≡ "+1.10001e+4"

let _ = assert : test_show -110001 -1 ≡ "-1.10001e+4"

let _ = assert : test_show +100000 -1 ≡ "+1.e+4"

let _ = assert : test_show -100000 -1 ≡ "-1.e+4"

let _ = assert : test_show +110000 -1 ≡ "+1.1e+4"

let _ = assert : test_show -110000 -1 ≡ "-1.1e+4"

let _ = assert : test_show +123456789 -8 ≡ "+1.23456789"

let _ = assert : test_show -123456789 -8 ≡ "-1.23456789"

let _ = assert : test_show +123456789 -9 ≡ "+0.123456789"

let _ = assert : test_show -123456789 -9 ≡ "-0.123456789"

let _ = assert : test_show +123456789 -10 ≡ "+0.0123456789"

let _ = assert : test_show -123456789 -10 ≡ "-0.0123456789"

let _ = assert : test_show +123456789 -11 ≡ "+0.00123456789"

let _ = assert : test_show -123456789 -11 ≡ "-0.00123456789"

let _ = assert : test_show +123456789 -12 ≡ "+1.23456789e-4"

let _ = assert : test_show -123456789 -12 ≡ "-1.23456789e-4"

in  { T = Float
    , base = Base
    , digits = Digits
    , show = Float/show
    , create = Float/create
    , isPositive = Float/positive
    , isZero = Float/isZero
    , doc =
        ''
        The type `Float` type represents floating-point numbers with at most ${Digits} of mantissa at base = ${Base}.
        ''
    }
