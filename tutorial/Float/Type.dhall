let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let Natural/lessThanEqual =
      https://prelude.dhall-lang.org/Natural/lessThanEqual
        sha256:1a5caa2b80a42b9f58fff58e47ac0d9a9946d0b2d36c54034b8ddfe3cb0f3c99

let Integer/abs =
      https://prelude.dhall-lang.org/Integer/abs
        sha256:35212fcbe1e60cb95b033a4a9c6e45befca4a298aa9919915999d09e69ddced1

let Integer/add =
      https://prelude.dhall-lang.org/Integer/add
        sha256:7da1306a0bf87c5668beead2a1db1b18861e53d7ce1f38057b2964b649f59c3b

let Integer/subtract =
      https://prelude.dhall-lang.org/Integer/subtract
        sha256:a34d36272fa8ae4f1ec8b56222fe8dc8a2ec55ec6538b840de0cbe207b006fda

let Integer/positive =
      https://prelude.dhall-lang.org/Integer/positive
        sha256:7bdbf50fcdb83d01f74c7e2a92bf5c9104eff5d8c5b4587e9337f0caefcfdbe3

let FloatExtraData =
      { leadDigit : Natural, topPower : Natural, remaining : Natural }

let FloatBare =
      { mantissa : Natural, mantissaPositive : Bool, exponent : Integer }

let stop = ./reduce_growth.dhall

let Base = 10

let Float = FloatBare ⩓ FloatExtraData

let D = ./Arithmetic.dhall

let divmod = D.divrem

let dummyFloat =
      { mantissa = 0
      , mantissaPositive = False
      , exponent = +0
      , leadDigit = 0
      , topPower = 0
      , remaining = 0
      }

let Float/addExtraData
    : FloatBare → Float
    = stop.reduce_growth
        FloatBare
        (λ(x : FloatBare) → stop.predicate_Natural x.mantissa)
        Float
        dummyFloat
        ( λ(args : FloatBare) →
            let topPower = D.log Base args.mantissa

            let r = divmod args.mantissa (D.power Base topPower)

            in  args ⫽ { topPower, leadDigit = r.div, remaining = r.rem }
        )

let FloatBare/create
    : Integer → Integer → FloatBare
    = λ(x : Integer) →
      λ(exp : Integer) →
        { mantissa = Integer/abs x
        , mantissaPositive = Integer/positive x
        , exponent = exp
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
                        ⫽ { mantissa = r.div
                          , exponent = Integer/add x.exponent +1
                          }
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

let Float/isPositive = λ(x : Float) → x.mantissaPositive

let Float/isZero = λ(x : Float) → Natural/isZero x.mantissa

let Float/create
    : Integer → Integer → Float
    = λ(x : Integer) →
      λ(exp : Integer) →
        Float/addExtraData (FloatBare/normalize (FloatBare/create x exp))

let _ = assert : Float/create +0 +0 ≡ Float/zero

let Float/normalize
    : Float → Float
    = stop.reduce_growth
        Float
        (λ(x : Float) → stop.predicate_Natural x.mantissa)
        Float
        Float/zero
        (λ(x : Float) → Float/addExtraData (FloatBare/normalize x.(FloatBare)))

let Float/pad
    : Float → Natural → Float
    = λ(x : Float) →
      λ(padding : Natural) →
        if    Float/isZero x || Natural/isZero padding
        then  x
        else  let p = D.power Base padding

              in    x
                  ⫽ { mantissa = x.mantissa * p
                    , topPower = x.topPower + padding
                    , exponent =
                        Integer/subtract (Natural/toInteger padding) x.exponent
                    , remaining = x.remaining * p
                    }

let _ =
        assert
      :   (Float/pad (Float/create +123 +0) 2).(FloatBare)
        ≡ FloatBare/create +12300 -2

let Float/negate =
      λ(a : Float) →
        if    Float/isZero a
        then  a
        else    a
              ⫽ { mantissaPositive = if a.mantissaPositive then False else True
                }

let Float/abs
    : Float → Float
    = λ(x : Float) → x ⫽ { mantissaPositive = True }

in  { Base
    , divmod
    , Float
    , FloatBare
    , Float/abs
    , Float/addExtraData
    , Float/create
    , Float/isPositive
    , Float/isZero
    , Float/negate
    , Float/normalize
    , Float/pad
    , Float/zero
    }
