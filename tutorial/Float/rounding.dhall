let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let T =
      ./Type.dhall
        sha256:eb9b0c4b594668945020e2dc430bc312b998f90ff2b8f6ba2a861c2836c144c5

let stop =
      ./reduce_growth.dhall
        sha256:9129f3a6766ab3cc8435482c1aa3cb84ef1a6cee80636121e2d1b377b0551ecc

let Base = T.Base

let Float/create = T.Float/create

let Float = T.Float

let Float/normalize = T.Float/normalize

let Float/zero = T.Float/zero

let divmod = T.divmod

let HalfBase = (divmod Base 2).div

let Float/truncate
    : Float → Natural → Float
    = stop.reduce_growth
        Float
        (λ(x : Float) → stop.predicate_Natural x.mantissa)
        (Natural → Float)
        (λ(_ : Natural) → Float/zero)
        ( λ(a : Float) →
          λ(prec : Natural) →
            if    Natural/lessThan a.topPower prec
            then  a
            else  let power =
                        T.power Base (Natural/subtract prec (a.topPower + 1))

                  let roundLastDigits = (divmod a.mantissa power).div * power

                  in  Float/normalize (a ⫽ { mantissa = roundLastDigits })
        )

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

let Float/round
    : Float → Natural → Float
    = stop.reduce_growth
        Float
        (λ(x : Float) → stop.predicate_Natural x.mantissa)
        (Natural → Float)
        (λ(_ : Natural) → Float/zero)
        ( λ(a : Float) →
          λ(prec : Natural) →
            if    Natural/lessThan a.topPower prec
            then  a
            else  let powerMinus1 =
                        T.power Base (Natural/subtract prec a.topPower)

                  let roundLastDigits =
                          ( divmod
                              (a.mantissa + HalfBase * powerMinus1)
                              (powerMinus1 * Base)
                          ).div
                        * Base
                        * powerMinus1

                  in  Float/normalize (a ⫽ { mantissa = roundLastDigits })
        )

let _ = assert : Float/round (Float/create +12345 +0) 4 ≡ Float/create +12350 +0

let _ =
        assert
      : Float/truncate (Float/create +12345 +0) 4 ≡ Float/create +12340 +0

let _ = assert : Float/round (Float/create +12345 +0) 5 ≡ Float/create +12345 +0

let _ = assert : Float/round (Float/create +12345 -10) 4 ≡ Float/create +1235 -9

let _ =
        assert
      :   Float/round (Float/create +1234567899 +0) 10
        ≡ Float/create +1234567899 +0

let _ =
        assert
      :   Float/round (Float/create +1234567899 +0) 9
        ≡ Float/create +1234567900 +0

let _ =
        assert
      :   Float/round (Float/create +1234567899 +0) 8
        ≡ Float/create +1234567900 +0

let _ =
        assert
      :   Float/round (Float/create +1234567899 +0) 7
        ≡ Float/create +1234568000 +0

let _ =
      let power = +100000000000

      in    assert
          :   Float/round (Float/create +1234567899 power) 7
            ≡ Float/create +1234568000 power

let _ =
        assert
      : Float/truncate (Float/create +12345 -10) 4 ≡ Float/create +1234 -9

let _ = assert : Float/round (Float/create +12345 +0) 0 ≡ Float/zero

in  { Float/round, Float/truncate }
