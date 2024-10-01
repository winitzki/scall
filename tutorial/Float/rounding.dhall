let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let T = ./Type.dhall

let Base = T.Base

let D = ./Arithmetic.dhall

let Float/create = T.Float/create

let Float = T.Float

let Float/normalize = T.Float/normalize

let Float/zero = T.Float/zero

let divmod = T.divmod

let HalfBase = (divmod Base 2).div

let Float/truncate =
      λ(a : Float) →
      λ(prec : Natural) →
        if    Natural/lessThan a.topPower prec
        then  a
        else  let power = D.power Base (Natural/subtract prec (a.topPower + 1))

              let roundLastDigits = (divmod a.mantissa power).div * power

              in  Float/normalize (a ⫽ { mantissa = roundLastDigits })

let _ =
        assert
      : Float/truncate (Float/create +12341 +0) 4 ≡ Float/create +12340 +0

let _ =
        assert
      : Float/truncate (Float/create +12341 +0) 5 ≡ Float/create +12341 +0

let _ =
        assert
      : Float/truncate (Float/create +12341 -10) 4 ≡ Float/create +1234 -9

let _ = assert : Float/truncate (Float/create +12341 +0) 0 ≡ Float/zero

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

let _ =
        assert
      : Float/truncate (Float/create +12345 +0) 4 ≡ Float/create +12340 +0

let _ = assert : Float/round (Float/create +12345 +0) 5 ≡ Float/create +12345 +0

let _ = assert : Float/round (Float/create +12345 -10) 4 ≡ Float/create +1235 -9

let _ =
        assert
      : Float/truncate (Float/create +12345 -10) 4 ≡ Float/create +1234 -9

let _ = assert : Float/round (Float/create +12345 +0) 0 ≡ Float/zero

in  { Float/round, Float/truncate }
