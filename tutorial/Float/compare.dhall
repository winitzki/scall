let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let Natural/lessThanEqual =
      https://prelude.dhall-lang.org/Natural/lessThanEqual
        sha256:1a5caa2b80a42b9f58fff58e47ac0d9a9946d0b2d36c54034b8ddfe3cb0f3c99

let Integer/positive =
      https://prelude.dhall-lang.org/Integer/positive
        sha256:7bdbf50fcdb83d01f74c7e2a92bf5c9104eff5d8c5b4587e9337f0caefcfdbe3

let Integer/lessThan =
      https://prelude.dhall-lang.org/Integer/lessThan
        sha256:eeaa0081d10c6c97464ef193c40f1aa5cbb12f0202972ab42f3d310c2fd6a3f0

let Integer/equal =
      https://prelude.dhall-lang.org/Integer/equal
        sha256:2d99a205086aa77eea17ae1dab22c275f3eb007bccdc8d9895b93497ebfc39f8

let Integer/abs =
      https://prelude.dhall-lang.org/Integer/abs
        sha256:35212fcbe1e60cb95b033a4a9c6e45befca4a298aa9919915999d09e69ddced1

let Integer/add =
      https://prelude.dhall-lang.org/Integer/add
        sha256:7da1306a0bf87c5668beead2a1db1b18861e53d7ce1f38057b2964b649f59c3b

let Integer/subtract =
      https://prelude.dhall-lang.org/Integer/subtract
        sha256:a34d36272fa8ae4f1ec8b56222fe8dc8a2ec55ec6538b840de0cbe207b006fda

let T = ./Type.dhall

let Pair = T.Pair

let TorsorType = { x : Natural, y : Natural }

let divmod = T.divmod

let Float = T.Float

let Float/zero = T.Float/zero

let Float/isZero = T.Float/isZero

let Float/isPositive = T.Float/isPositive

let Float/create = T.Float/create

let Float/normalize = T.Float/normalize

let Base = T.Base

let D = ./Arithmetic.dhall

let stop = ./reduce_growth.dhall

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

let Integer/compare
    : Integer → Integer → Compared
    = λ(x : Integer) →
      λ(y : Integer) →
        if    Integer/lessThan x y
        then  Compared.Less
        else  if Integer/equal x y
        then  Compared.Equal
        else  Compared.Greater

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

let Pair = T.Pair

let Integer/addNatural =
      λ(x : Integer) → λ(y : Natural) → Integer/add x (Natural/toInteger y)

let compareUnsignedNonzero
    : Pair Float Float → Compared
    = stop.reduce_growth
        (Pair Float Float)
        (λ(pair : Pair Float Float) → stop.predicate_Natural pair._1.mantissa)
        Compared
        Compared.Equal
        ( λ(pair : Pair Float Float) →
            let a = pair._1

            let b = pair._2

            let compareTopPowersWithExponentials =
                  Integer/compare
                    (Integer/addNatural a.exponent a.topPower)
                    (Integer/addNatural b.exponent b.topPower)

            in  merge
                  { Less = Compared.Less
                  , Greater = Compared.Greater
                  , Equal =
                      let subtractExponentials =
                            Integer/subtract a.exponent b.exponent

                      let power =
                            D.power Base (Integer/abs subtractExponentials)

                      in  if    Integer/positive subtractExponentials
                          then  Natural/compare a.mantissa (b.mantissa * power)
                          else  Natural/compare (a.mantissa * power) b.mantissa
                  }
                  compareTopPowersWithExponentials
        )

let identity = λ(t : Type) → λ(x : t) → x

let Float/compare
    : Float → Float → Compared
    = λ(x : Float) →
      λ(y : Float) →
        let QuickCompare = < Done : Compared | NegateResult : Bool >

        let maybeQuickCompare
            : QuickCompare
            = if    Float/isZero x
              then  if    Float/isZero y
                    then  QuickCompare.Done Compared.Equal
                    else  if Float/isPositive y
                    then  QuickCompare.Done Compared.Less
                    else  QuickCompare.Done Compared.Greater
              else  if Float/isPositive x
              then  if    Float/isZero y
                    then  QuickCompare.Done Compared.Greater
                    else  if Float/isPositive y
                    then  QuickCompare.NegateResult False
                    else  QuickCompare.Done Compared.Greater
              else  if Float/isZero y || Float/isPositive y
              then  QuickCompare.Done Compared.Less
              else  QuickCompare.NegateResult True

        in  merge
              { Done = λ(result : Compared) → result
              , NegateResult =
                  λ(needToNegate : Bool) →
                    ( if    needToNegate
                      then  Compared/reverse
                      else  identity Compared
                    )
                      (compareUnsignedNonzero { _1 = x, _2 = y })
              }
              maybeQuickCompare

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

let zeroTorsor = { x = 0, y = 0 }

let computeTorsor
    : Integer → TorsorType
    =    {-
    stop.reduce_growth
        Integer
        stop.predicate_Integer
        TorsorType
        zeroTorsor
            -}
        ( λ(i : Integer) →
            if    Integer/positive i
            then  { x = Integer/clamp i, y = 0 }
            else  { x = 0, y = Integer/abs i }
        )

let computeTorsorForBothNonzero
    -- We define "torsor(a, b)" as any pair of `Natural` numbers (x, y) such that floor(log_10(a)) - floor(log_10(b)) = x - y.
    : Pair Float Float → TorsorType
    =
    {-
    stop.reduce_growth
        (Pair Float Float)
        (λ(pair : Pair Float Float) → stop.predicate_Natural pair._1.mantissa)
        TorsorType
        zeroTorsor
      -}
        ( λ(pair : Pair Float Float) →
            let a = pair._1

            let b = pair._2

            in  computeTorsor
                  ( Integer/subtract
                      (Integer/addNatural b.exponent b.topPower)
                      (Integer/addNatural a.exponent a.topPower)
                  )
        )

in  { Compared
    , Compared/reverse
    , Float/compare
    , TorsorType
    , identity
    , Natural/compare
    , Integer/compare
    , computeTorsorForBothNonzero
    , computeTorsor
    , compareUnsignedNonzero
    }
