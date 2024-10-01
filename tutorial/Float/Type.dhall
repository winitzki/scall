let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let Natural/lessThanEqual =
      https://prelude.dhall-lang.org/Natural/lessThanEqual
        sha256:1a5caa2b80a42b9f58fff58e47ac0d9a9946d0b2d36c54034b8ddfe3cb0f3c99

let Integer/abs =
      https://prelude.dhall-lang.org/Integer/abs
        sha256:35212fcbe1e60cb95b033a4a9c6e45befca4a298aa9919915999d09e69ddced1

let Integer/positive =
      https://prelude.dhall-lang.org/Integer/positive
        sha256:7bdbf50fcdb83d01f74c7e2a92bf5c9104eff5d8c5b4587e9337f0caefcfdbe3

let FloatExtraData =
      { leadDigit : Natural, topPower : Natural, remaining : Natural }

let FloatBare =
      { mantissa : Natural
      , exponent : Natural
      , exponentPositive : Bool
      , mantissaPositive : Bool
      }

let Base = 10

let Float = FloatBare ⩓ FloatExtraData

let D = ./Arithmetic.dhall

let divmod = D.divrem

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

let Float/isPositive =
      λ(x : Float) → x.mantissaPositive || Natural/isZero x.mantissa

let Float/isZero = λ(x : Float) → Natural/isZero x.mantissa

let Float/create
    : Integer → Integer → Float
    = λ(x : Integer) →
      λ(exp : Integer) →
        Float/addExtraData (FloatBare/normalize (FloatBare/create x exp))

let _ = assert : Float/create +0 +0 ≡ Float/zero

let Float/normalize
    : Float → Float
    = λ(x : Float) → Float/addExtraData (FloatBare/normalize x.(FloatBare))

let Float/pad
    : Float → Natural → Float
    = λ(x : Float) →
      λ(padding : Natural) →
        if    Float/isZero x || Natural/isZero padding
        then  x
        else  let p = D.power Base padding

              let newExponentPositive =
                        x.exponentPositive
                    ||  Natural/lessThanEqual x.exponent padding

              let newExponent =
                    if    x.exponentPositive
                    then  x.exponent + padding
                    else  let e = Natural/subtract padding x.exponent

                          in  if    Natural/isZero e
                              then  Natural/subtract x.exponent padding
                              else  e

              in    x
                  ⫽ { mantissa = x.mantissa * p
                    , topPower = x.topPower + padding
                    , exponent = newExponent
                    , exponentPositive = newExponentPositive
                    , remaining = x.remaining * p
                    }

in  { Base
    , Float
    , Float/create
    , Float/isPositive
    , Float/normalize
    , Float/pad
    , Float/zero
    }
