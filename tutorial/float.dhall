-- Floating-point computations with few digits of precision.
-- The precision is set via the variable `Digits`.
-- This is a proof of concept. Performance will be very slow.
let division =
      ./division.dhall
        sha256:b4199073281d52b9b9d708504213ba6e7b9c2e731a31312dee5cd740b49d1c4e

let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let Integer/positive =
      https://prelude.dhall-lang.org/Integer/positive
        sha256:7bdbf50fcdb83d01f74c7e2a92bf5c9104eff5d8c5b4587e9337f0caefcfdbe3

let Integer/equal =
      https://prelude.dhall-lang.org/Integer/equal
        sha256:2d99a205086aa77eea17ae1dab22c275f3eb007bccdc8d9895b93497ebfc39f8

let Integer/add =
      https://prelude.dhall-lang.org/Integer/add sha256:7da1306a0bf87c5668beead2a1db1b18861e53d7ce1f38057b2964b649f59c3b
        

let Bool/not =
      https://prelude.dhall-lang.org/Bool/not
        sha256:723df402df24377d8a853afed08d9d69a0a6d86e2e5b2bac8960b0d4756c7dc4

let Integer/abs =
      https://prelude.dhall-lang.org/Integer/abs
        sha256:35212fcbe1e60cb95b033a4a9c6e45befca4a298aa9919915999d09e69ddced1

let Natural/power = division.power

let Float =
      { mantissa : Natural
      , mantissaPositive : Bool
      , exponent : Natural
      , exponentPositive : Bool
      }

let showSign = λ(x : Bool) → if x then "+" else "-"

let Ten = 10

let Digits = 3

let MaxBase = Natural/power Ten Digits

let Float/create
    : Integer → Integer → Float
    = λ(mantissa : Integer) →
      λ(exponent_orig : Integer) →
        let m_original = Integer/abs mantissa

        let normalization =
              Natural/subtract
                Digits
                (1 + division.log Ten m_original)

        let factor = division.power Ten (Natural/subtract 1 normalization)

        let m =
              if    Natural/isZero normalization
              then m_original
              else  division.unsafeDiv (m_original + 5 * factor) (factor * Ten)

        let exponent = Integer/add exponent_orig  (Natural/toInteger normalization)

        let e = Integer/abs exponent

        in  { mantissa = m
            , mantissaPositive = Integer/positive mantissa
            , exponent = e
            , exponentPositive =
                Integer/positive exponent || Integer/equal +0 exponent
            }

let Float/show
    : Float → Text
    = λ(x : Float) →
        let extraZeros =
              Natural/subtract (1 + division.log Ten x.mantissa) Digits

        let padding = Natural/fold extraZeros Text (λ(a : Text) → a ++ "0") ""

        let xsign = showSign x.mantissaPositive

        let esign = showSign x.exponentPositive

        let showExp =
              if    x.exponentPositive || Natural/isZero x.exponent
              then  ""
              else  esign ++ Natural/show x.exponent

        in  "${xsign}${Natural/show x.mantissa}.${padding}${showExp}"

let example : Float =
      { mantissa = 1
      , mantissaPositive = True
      , exponent = 0
      , exponentPositive = True
      }

let _ = assert : example ≡ Float/create +1 +0

let _ = assert : Float/show example ≡ "+1.00"

let _ = assert : Float/show (example ⫽ { mantissaPositive = False }) ≡ "-1.00"

let _ = assert : Float/show (example ⫽ { exponentPositive = False }) ≡ "+1.00"

let _ = assert : Float/show (Float/create +2 +0) ≡ "+2.00"

let _ = assert : Float/show (Float/create +2 -0) ≡ "+2.00"

let _ = assert : Float/show (Float/create -2 -0) ≡ "-2.00"

let _ = assert : Float/show (Float/create +12 +0) ≡ "+12.0"

let _ = assert : Float/show (Float/create +123 +0) ≡ "+123."

let _ = assert : Float/show (Float/create +1234 +0) ≡ "+123."

let _ = assert : Float/show (Float/create +2 +1) ≡ "+20.0"


in  { T = Float, show = Float/show }
