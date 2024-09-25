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

let FloatUnsigned =
      { mantissa : Natural, exponent : Natural, exponentPositive : Bool }

let Float = FloatUnsigned ⩓ { mantissaPositive : Bool }

let showSign = λ(x : Bool) → if x then "+" else "-"

let Base = 10

let MaxPrintedWithoutExponent = 3

let _ = assert : Natural/lessThan 1 Base ≡ True

let Digits = 3

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

let Float/show
    : Float → Text
    = λ(f : Float) →
        if    Natural/isZero f.mantissa
        then  "0."
        else  let mantissaSign = showSign f.mantissaPositive

              let exponentSign = showSign f.exponentPositive

              in  if    f.exponentPositive
                  then  let largeInteger = f.mantissa * D.power Base f.exponent

                        let maxDisplayedInteger =
                              D.power Base (MaxPrintedWithoutExponent + 1)

                        in  if    Natural/lessThan
                                    largeInteger
                                    maxDisplayedInteger
                            then  "${mantissaSign}${Natural/show largeInteger}."
                            else
                            let topPower = D.log Base largeInteger
                            let baseToTopPower = D.power Base topPower
                            let divideByTopPower = divmod largeInteger baseToTopPower
                            let firstDigit
                                      : Natural
                                      = divideByTopPower.div

                                  let remainingDigitsWithTrailingZeros
                                      : Natural
                                      = divideByTopPower.mod
                                let remainingDigitsWithoutTrailingZeros : Natural = Natural/fold remainingDigitsWithTrailingZeros Natural (\(x: Natural) -> let divideByBase = divmod x Base in if Natural/isZero divideByBase.rem then divideByBase.div else x ) remainingDigitsWithTrailingZeros

                                  in  "${mantissaSign}${Natural/show
                                                          firstDigit}.${Natural/show
                                                                          remainingDigitsWithoutTrailingZeros}."
                  else  "${mantissaSign}${Natural/show f.mantissa}."

let _ =
        assert
      :   Float/show
            { mantissa = 0
            , exponent = 1234
            , mantissaPositive = True
            , exponentPositive = False
            }
        ≡ "0."

let _ =
        assert
      :   Float/show
            { mantissa = 1
            , exponent = 0
            , mantissaPositive = True
            , exponentPositive = False
            }
        ≡ "+1."

let _ =
        assert
      :   Float/show
            { mantissa = 1
            , exponent = 0
            , mantissaPositive = False
            , exponentPositive = False
            }
        ≡ "-1."

let _ =
        assert
      :   Float/show
            { mantissa = 10
            , exponent = 0
            , mantissaPositive = True
            , exponentPositive = False
            }
        ≡ "+10."

let _ =
        assert
      :   Float/show
            { mantissa = 10
            , exponent = 0
            , mantissaPositive = False
            , exponentPositive = False
            }
        ≡ "-10."

let _ =
        assert
      :   Float/show
            { mantissa = 10
            , exponent = 1
            , mantissaPositive = True
            , exponentPositive = False
            }
        ≡ "+1."

let _ =
        assert
      :   Float/show
            { mantissa = 10
            , exponent = 1
            , mantissaPositive = False
            , exponentPositive = False
            }
        ≡ "-1."

let _ =
        assert
      :   Float/show
            { mantissa = 10
            , exponent = 1
            , mantissaPositive = True
            , exponentPositive = True
            }
        ≡ "+100."

let _ =
        assert
      :   Float/show
            { mantissa = 10
            , exponent = 1
            , mantissaPositive = False
            , exponentPositive = True
            }
        ≡ "-100."

let _ =
        assert
      :   Float/show
            { mantissa = 10
            , exponent = 2
            , mantissaPositive = True
            , exponentPositive = True
            }
        ≡ "+1000."

let _ =
        assert
      :   Float/show
            { mantissa = 10
            , exponent = 2
            , mantissaPositive = False
            , exponentPositive = True
            }
        ≡ "-1000."

let _ =
        assert
      :   Float/show
            { mantissa = 10
            , exponent = 2
            , mantissaPositive = True
            , exponentPositive = True
            }
        ≡ "+1.e+4"

let _ =
        assert
      :   Float/show
            { mantissa = 10
            , exponent = 2
            , mantissaPositive = False
            , exponentPositive = True
            }
        ≡ "-1.e+4"

let _ =
        assert
      :   Float/show
            { mantissa = 101
            , exponent = 1
            , mantissaPositive = True
            , exponentPositive = False
            }
        ≡ "+10.1"

let _ =
        assert
      :   Float/show
            { mantissa = 101
            , exponent = 1
            , mantissaPositive = False
            , exponentPositive = False
            }
        ≡ "-10.1"

let _ =
        assert
      :   Float/show
            { mantissa = 1001
            , exponent = 1
            , mantissaPositive = True
            , exponentPositive = False
            }
        ≡ "+100.1"

let _ =
        assert
      :   Float/show
            { mantissa = 1001
            , exponent = 1
            , mantissaPositive = False
            , exponentPositive = False
            }
        ≡ "-100.1"

let _ =
        assert
      :   Float/show
            { mantissa = 10001
            , exponent = 1
            , mantissaPositive = True
            , exponentPositive = False
            }
        ≡ "+1000.1"

let _ =
        assert
      :   Float/show
            { mantissa = 10001
            , exponent = 1
            , mantissaPositive = False
            , exponentPositive = False
            }
        ≡ "-1000.1"

let _ =
        assert
      :   Float/show
            { mantissa = 100001
            , exponent = 1
            , mantissaPositive = True
            , exponentPositive = False
            }
        ≡ "+1.00001e+4"

let _ =
        assert
      :   Float/show
            { mantissa = 100001
            , exponent = 1
            , mantissaPositive = False
            , exponentPositive = False
            }
        ≡ "-1.00001e+4"

let _ =
        assert
      :   Float/show
            { mantissa = 123456789
            , exponent = 8
            , mantissaPositive = True
            , exponentPositive = False
            }
        ≡ "+1.23456789"

let _ =
        assert
      :   Float/show
            { mantissa = 123456789
            , exponent = 8
            , mantissaPositive = False
            , exponentPositive = False
            }
        ≡ "-1.23456789"

let _ =
        assert
      :   Float/show
            { mantissa = 123456789
            , exponent = 9
            , mantissaPositive = True
            , exponentPositive = False
            }
        ≡ "+0.123456789"

let _ =
        assert
      :   Float/show
            { mantissa = 123456789
            , exponent = 9
            , mantissaPositive = False
            , exponentPositive = False
            }
        ≡ "-0.123456789"

let _ =
        assert
      :   Float/show
            { mantissa = 123456789
            , exponent = 10
            , mantissaPositive = True
            , exponentPositive = False
            }
        ≡ "+0.0123456789"

let _ =
        assert
      :   Float/show
            { mantissa = 123456789
            , exponent = 10
            , mantissaPositive = False
            , exponentPositive = False
            }
        ≡ "-0.0123456789"

let _ =
        assert
      :   Float/show
            { mantissa = 123456789
            , exponent = 11
            , mantissaPositive = True
            , exponentPositive = False
            }
        ≡ "+0.00123456789"

let _ =
        assert
      :   Float/show
            { mantissa = 123456789
            , exponent = 11
            , mantissaPositive = False
            , exponentPositive = False
            }
        ≡ "-0.00123456789"

let _ =
        assert
      :   Float/show
            { mantissa = 123456789
            , exponent = 12
            , mantissaPositive = True
            , exponentPositive = False
            }
        ≡ "+1.23456789e-4"

let _ =
        assert
      :   Float/show
            { mantissa = 123456789
            , exponent = 12
            , mantissaPositive = False
            , exponentPositive = False
            }
        ≡ "-1.23456789e-4"

in  { T = Float
    , base = Base
    , digits = Digits
    , show = Float/show
    , create = Float/create
    , doc =
        ''
        The type `Float` type represents floating-point numbers with at most ${Digits} of mantissa at base = ${Base}.
        ''
    }
