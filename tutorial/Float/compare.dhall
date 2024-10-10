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

let Float/abs = T.Float/abs

let TorsorType = { x : Natural, y : Natural }

let computeTorsorForBothNonzero
    -- We define "torsor(a, b)" as a pair of `Natural` numbers (x, y) such that floor(log_10(a)) - floor(log_10(b)) = x - y.
    : Float → Float → TorsorType
    = λ(a : Float) →
      λ(b : Float) →
        if    a.exponentPositive
        then  if    b.exponentPositive
              then  { x = a.exponent + a.topPower, y = b.exponent + b.topPower }
              else  { x = a.exponent + a.topPower + b.exponent, y = b.topPower }
        else  if b.exponentPositive
        then  { x = a.topPower, y = a.exponent + b.exponent + b.topPower }
        else  { x = b.exponent + a.topPower, y = a.exponent + b.topPower }

let compareUnsignedNonzeroWithTorsor =
      λ(a : Float) →
      λ(b : Float) →
      λ(fixedExponents : TorsorType) →
        if    Natural/lessThan fixedExponents.x fixedExponents.y
        then  Compared.Less
        else  if Natural/lessThan fixedExponents.y fixedExponents.x
        then  Compared.Greater
        else  let fixed
                  : TorsorType
                  = if    a.exponentPositive
                    then  if    b.exponentPositive
                          then  { x = a.mantissa * D.power Base a.exponent
                                , y = b.mantissa * D.power Base b.exponent
                                }
                          else  { x =
                                      a.mantissa
                                    * D.power Base (a.exponent + b.exponent)
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

let compareUnsignedNonzero =
      λ(a : Float) →
      λ(b : Float) →
        compareUnsignedNonzeroWithTorsor a b (computeTorsorForBothNonzero a b)

let Float/compare
    : Float → Float → Compared
    = λ(x : Float) →
      λ(y : Float) →
        if    Float/isZero x
        then  if    Float/isZero y
              then  Compared.Equal
              else  if y.mantissaPositive
              then  Compared.Less
              else  Compared.Greater
        else  if x.mantissaPositive
        then  if    Float/isZero y
              then  Compared.Greater
              else  if y.mantissaPositive
              then  compareUnsignedNonzero x y
              else  Compared.Greater
        else  if Float/isZero y || y.mantissaPositive
        then  Compared.Less
        else  compareUnsignedNonzero y x

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

in  { Compared
    , Compared/reverse
    , Float/compare
    , Natural/compare
    , TorsorType
    , compareUnsignedNonzeroWithTorsor
    , computeTorsorForBothNonzero
    }
