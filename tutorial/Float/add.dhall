let Natural/lessThanEqual =
      https://prelude.dhall-lang.org/Natural/lessThanEqual
        sha256:1a5caa2b80a42b9f58fff58e47ac0d9a9946d0b2d36c54034b8ddfe3cb0f3c99

let Natural/min =
      https://prelude.dhall-lang.org/Natural/min
        sha256:f25f9c462e4dbf0eb15f9ff6ac840c6e9c82255a7f4f2ab408bdab338e028710

let Natural/max =
      https://prelude.dhall-lang.org/Natural/max
        sha256:1f3b18da330223ab039fad11693da72c7e68d516f50502c73f41a89a097b62f7

let T = ./Type.dhall

let Base = T.Base

let Pair = T.Pair

let Float/show = ./show.dhall

let Float/create = T.Float/create

let Float = T.Float

let Float/normalize = T.Float/normalize

let Float/isZero = T.Float/isZero

let Float/zero = T.Float/zero

let Float/pad = T.Float/pad

let Float/abs = T.Float/abs

let Float/negate = T.Float/negate

let C = ./compare.dhall

let TorsorType = C.TorsorType

let zeroTorsor
    : TorsorType
    = { x = 0, y = 0 }

let identity = C.identity

let computeTorsorForBothNonzero = C.computeTorsorForBothNonzero

let compareUnsignedNonzero = C.compareUnsignedNonzero

let stop = ./reduce_growth.dhall

let predicate_Float = λ(x : Float) → stop.predicate_Natural x.mantissa

let predicate_2Floats =
      λ(x : Pair Float Float) → stop.predicate_Natural x._1.mantissa

let predicate_TorsorType = λ(t : TorsorType) → stop.predicate_Natural t.x

let R = ./rounding.dhall

let Float/round = R.Float/round

let divmod = T.divmod

let clampDigits -- Make sure x has exactly prec digits. The value x_log_floor must be precomputed.
                -- TODO add rounding to the clamping operation.
                =
      λ(x_log_floor : Natural) →
      λ(prec : Natural) →
        stop.reduce_growth
          Natural
          stop.predicate_Natural
          Natural
          0
          ( λ(x : Natural) →
              let h = 1 + x_log_floor

              in  if    Natural/lessThanEqual h prec
                  then  x * T.power Base (Natural/subtract h prec)
                  else  (divmod x (T.power Base (Natural/subtract prec h))).div
          )

let totalUnderflow
    -- Detect if `a` is negligible compared to `b` within given precision.
    -- The value of `torsor a b` is given.
    : Natural → TorsorType → Bool
    = λ(prec : Natural) →
        stop.reduce_growth
          TorsorType
          predicate_TorsorType
          Bool
          False
          ( λ(torsor : TorsorType) →
                  Natural/isZero prec
              ||  Natural/lessThanEqual (1 + prec + torsor.x) torsor.y
          )

let flipTorsor
    : TorsorType → TorsorType
    = stop.reduce_growth
        TorsorType
        predicate_TorsorType
        TorsorType
        zeroTorsor
        (λ(torsor : TorsorType) → torsor ⫽ { x = torsor.y, y = torsor.x })

let torsorXLessEqualY =
      stop.reduce_growth
        TorsorType
        predicate_TorsorType
        Bool
        False
        (λ(torsor : TorsorType) → Natural/lessThanEqual torsor.x torsor.y)

let Natural/plus =
      stop.reduce_growth_noop
        Natural
        stop.predicate_Natural
        (Natural → Natural)
        (λ(_ : Natural) → 0)
        (λ(a : Natural) → λ(b : Natural) → a + b)

let Natural/minus =
      stop.reduce_growth_noop
        Natural
        stop.predicate_Natural
        (Natural → Natural)
        (λ(_ : Natural) → 0)
        (λ(a : Natural) → λ(b : Natural) → Natural/subtract b a)

let addOrSubtractUnsignedAIsGreater
    -- Compute a + b or a - b, assuming that a >= b > 0.
    : Natural →
      (Natural → Natural → Natural) →
      TorsorType →
      Pair Float Float →
        Float
    = λ(prec : Natural) →
      λ(addOrSubtract : Natural → Natural → Natural) →
        stop.reduce_growth
          TorsorType
          predicate_TorsorType
          (Pair Float Float → Float)
          (λ(_ : Pair Float Float) → Float/zero)
          ( λ(torsor : TorsorType) →
              stop.reduce_growth
                (Pair Float Float)
                predicate_2Floats
                Float
                Float/zero
                ( λ(pair : Pair Float Float) →
                    let a = pair._1

                    let b = pair._2

                    let difference = Natural/subtract torsor.y torsor.x

                    let commonSize = prec + 1

                    let baseline =
                          Natural/max
                            (a.topPower + 1)
                            ( Natural/min
                                commonSize
                                (difference + b.topPower + 1)
                            )

                    let aIsTooSmall =
                          Natural/lessThanEqual (a.topPower + 1) commonSize

                    let aClamped =
                          if    aIsTooSmall
                          then  Float/pad
                                  a
                                  (Natural/subtract (a.topPower + 1) baseline)
                          else  Float/round a commonSize

                    let clampTo = if aIsTooSmall then baseline else commonSize

                    let bClamped =
                          clampDigits
                            b.topPower
                            (Natural/subtract difference clampTo)
                            b.mantissa

                    let resultWithNewMantissaOnly =
                            aClamped
                          ⫽ { mantissa =
                                addOrSubtract aClamped.mantissa bClamped
                            }

                    in  T.Float/addExtraData
                          resultWithNewMantissaOnly.(T.FloatBare)
                )
          )

let addUnsignedAIsGreaterNoUnderflowCheck =
      λ(a : Float) →
      λ(b : Float) →
      λ(torsor : TorsorType) →
      λ(prec : Natural) →
        addOrSubtractUnsignedAIsGreater
          prec
          Natural/plus
          torsor
          { _1 = a, _2 = b }

let addUnsignedBothNonzero
    -- Compute a + b, assuming that both are > 0.
    : Natural → TorsorType → Pair Float Float → Float
    = λ(prec : Natural) →
        stop.reduce_growth
          TorsorType
          predicate_TorsorType
          (Pair Float Float → Float)
          (λ(_ : Pair Float Float) → Float/zero)
          ( λ(torsor : TorsorType) →
              stop.reduce_growth
                (Pair Float Float)
                predicate_2Floats
                Float
                Float/zero
                ( λ(pair : Pair Float Float) →
                    let a = pair._1

                    let b = pair._2

                    in  if    totalUnderflow (Natural/subtract 1 prec) torsor
                        then  b
                        else  if totalUnderflow
                                   (Natural/subtract 1 prec)
                                   (flipTorsor torsor)
                        then  a
                        else  let xSmaller = torsorXLessEqualY torsor

                              let x = if xSmaller then b else a

                              let y = if xSmaller then a else b

                              let t =
                                    if    xSmaller
                                    then  flipTorsor
                                    else  identity TorsorType

                              in  addUnsignedAIsGreaterNoUnderflowCheck
                                    x
                                    y
                                    (t torsor)
                                    prec
                )
          )

let subtractUnsignedAMinusB
    -- Compute a - b, assuming that a >= b > 0.
    : Natural → TorsorType → Pair Float Float → Float
    = λ(prec : Natural) →
        stop.reduce_growth
          TorsorType
          predicate_TorsorType
          (Pair Float Float → Float)
          (λ(_ : Pair Float Float) → Float/zero)
          ( λ(torsor : TorsorType) →
              stop.reduce_growth
                (Pair Float Float)
                predicate_2Floats
                Float
                Float/zero
                ( λ(pair : Pair Float Float) →
                    let a = pair._1

                    let b = pair._2

                    in  if    totalUnderflow (Natural/subtract 1 prec) torsor
                        then  b
                        else  addOrSubtractUnsignedAIsGreater
                                prec
                                Natural/minus
                                torsor
                                pair
                )
          )

let negate_reduced =
      stop.reduce_growth Float predicate_Float Float Float/zero Float/negate

let addFloatPair =
      λ(prec : Natural) →
        stop.reduce_growth
          (Pair Float Float)
          predicate_2Floats
          Float
          Float/zero
          ( λ(pair_ab : Pair Float Float) →
              let a = pair_ab._1

              let b = pair_ab._2

              in  if    Float/isZero a
                  then  b
                  else  if Float/isZero b
                  then  a
                  else  let torsor = computeTorsorForBothNonzero pair_ab

                        let absA = Float/abs a

                        let absB = Float/abs b

                        let applySign =
                              stop.reduce_growth
                                Float
                                predicate_Float
                                Float
                                Float/zero
                                ( λ(result : Float) →
                                    if    a.mantissaPositive
                                    then  result
                                    else  negate_reduced result
                                )

                        let result =
                              if        a.mantissaPositive && b.mantissaPositive
                                    ||      a.mantissaPositive == False
                                        &&  b.mantissaPositive == False
                              then  applySign
                                      ( addUnsignedBothNonzero
                                          prec
                                          torsor
                                          pair_ab
                                      )
                              else  let pair_abs_ab = { _1 = absA, _2 = absB }

                                    let pair_abs_ba = { _1 = absB, _2 = absA }

                                    let compared =
                                          compareUnsignedNonzero pair_abs_ab

                                    let checkZero =
                                          merge
                                            { Less =
                                              { postprocess = negate_reduced
                                              , flip = flipTorsor
                                              , reverse = True
                                              }
                                            , Equal =
                                              { postprocess =
                                                  λ(_ : Float) → Float/zero
                                              , flip = identity TorsorType
                                              , reverse = False
                                              }
                                            , Greater =
                                              { postprocess = identity Float
                                              , flip = identity TorsorType
                                              , reverse = False
                                              }
                                            }
                                            compared

                                    in  checkZero.postprocess
                                          ( applySign
                                              ( subtractUnsignedAMinusB
                                                  prec
                                                  (checkZero.flip torsor)
                                                  ( if    checkZero.reverse
                                                    then  pair_abs_ba
                                                    else  pair_abs_ab
                                                  )
                                              )
                                          )

                        in  Float/normalize result
          )

let Float/add
    : Float → Float → Natural → Float
    = λ(a : Float) →
      λ(b : Float) →
      λ(prec : Natural) →
        addFloatPair prec { _1 = a, _2 = b }

let _ = assert : clampDigits (T.log 10 123) 0 123 ≡ 0

let _ = assert : clampDigits (T.log 10 123) 1 123 ≡ 1

let _ = assert : clampDigits (T.log 10 123) 2 123 ≡ 12

let _ = assert : clampDigits (T.log 10 123) 3 123 ≡ 123

let _ = assert : clampDigits (T.log 10 123) 4 123 ≡ 1230

let _ = assert : clampDigits (T.log 10 123) 10 123 ≡ 1230000000

let mkTorsor =
      λ(x : Float) →
      λ(y : Float) →
        computeTorsorForBothNonzero { _1 = x, _2 = y }

let _ =
        assert
      : mkTorsor (Float/create +1 +0) (Float/create +1 +0) ≡ { x = 0, y = 0 }

let _ =
        assert
      : mkTorsor (Float/create +10 +0) (Float/create +1 +0) ≡ { x = 1, y = 0 }

let _ =
        assert
      : mkTorsor (Float/create +1 +0) (Float/create +10 +0) ≡ { x = 0, y = 1 }

let _ =
        assert
      : mkTorsor (Float/create +10 +0) (Float/create +100 +0) ≡ { x = 0, y = 1 }

let _ =
        assert
      : mkTorsor (Float/create +99 +0) (Float/create +100 +0) ≡ { x = 0, y = 1 }

let _ =
        assert
      :   mkTorsor (Float/create +123 +5) (Float/create +100 +0)
        ≡ { x = 5, y = 0 }

let _ =
        assert
      : mkTorsor (Float/create +1 -5) (Float/create +1 +0) ≡ { x = 0, y = 5 }

let _ =
        assert
      : mkTorsor (Float/create +1 -5) (Float/create +1 -10) ≡ { x = 5, y = 0 }

let _ =
        assert
      :   mkTorsor (Float/create -123 +5) (Float/create +123 -10)
        ≡ { x = 15, y = 0 }

let _ =
        assert
      :   mkTorsor (Float/create -123 +5) (Float/create -123 -10)
        ≡ { x = 15, y = 0 }

let checkTotalUnderflow =
      λ(x : Integer) →
      λ(ex : Integer) →
      λ(y : Integer) →
      λ(ey : Integer) →
      λ(prec : Natural) →
        totalUnderflow prec (mkTorsor (Float/create x ex) (Float/create y ey))

let checkFlipUnderflow =
      λ(x : Integer) →
      λ(ex : Integer) →
      λ(y : Integer) →
      λ(ey : Integer) →
      λ(prec : Natural) →
        totalUnderflow
          prec
          (flipTorsor (mkTorsor (Float/create x ex) (Float/create y ey)))

let _ = assert : checkTotalUnderflow +1 +0 +1000 +0 0 ≡ True

let _ = assert : checkTotalUnderflow +1000 +0 +1 +0 0 ≡ True

let _ = assert : checkTotalUnderflow +1 +0 +1000 +0 1 ≡ True

let _ = assert : checkFlipUnderflow +1 +0 +1000 +0 1 ≡ False

let _ = assert : checkTotalUnderflow +1000 +0 +1 +0 1 ≡ False

let _ = assert : checkFlipUnderflow +1000 +0 +1 +0 1 ≡ True

let _ = assert : checkTotalUnderflow +1 +0 +1000 +0 2 ≡ True

let _ = assert : checkTotalUnderflow +1000 +0 +1 +0 2 ≡ False

let _ = assert : checkTotalUnderflow +1 +0 +1000 +0 3 ≡ False

let _ = assert : checkTotalUnderflow +1000 +0 +1 +0 3 ≡ False

let _ = assert : checkTotalUnderflow +1 +0 +1000 +0 4 ≡ False

let _ = assert : checkTotalUnderflow +1000 +0 +1 +0 4 ≡ False

let _ = assert : checkTotalUnderflow -123 -10 -123 +5 10 ≡ True

let _ = assert : checkTotalUnderflow -1 -10 -1 +5 13 ≡ True

let _ = assert : checkTotalUnderflow -1 -10 -1 +5 14 ≡ True

let _ = assert : checkTotalUnderflow -1 -10 -1 +5 15 ≡ False

let _ = assert : checkTotalUnderflow +123456789 +6 +1 +0 8 ≡ False

let _ = assert : checkTotalUnderflow +1 +0 +123456789 +6 8 ≡ True

let _ = assert : checkFlipUnderflow +1 +0 +123456789 +6 8 ≡ False

let _ = assert : checkFlipUnderflow +123456789 +6 +1 +0 8 ≡ True

let _ = assert : checkTotalUnderflow +1 +0 +123456789 +6 13 ≡ True

let _ = assert : checkTotalUnderflow +1 +0 +123456789 +6 14 ≡ False

let _ = assert : checkTotalUnderflow +1 +0 +123456789 +6 15 ≡ False

let unsigned
             -- Adding numbers without underflow check, which forces rounding.
             =
      λ(x : Integer) →
      λ(ex : Integer) →
      λ(y : Integer) →
      λ(ey : Integer) →
      λ(prec : Natural) →
        Float/normalize
          ( addUnsignedAIsGreaterNoUnderflowCheck
              (Float/create x ex)
              (Float/create y ey)
              (mkTorsor (Float/create x ex) (Float/create y ey))
              prec
          )

let _ = assert : unsigned +123456789 +0 +1 +0 10 ≡ Float/create +123456790 +0

let _ = assert : unsigned +123456789 +0 +1 +0 5 ≡ Float/create +123457 +3

let _ = assert : unsigned +123456789 +0 +1 +0 6 ≡ Float/create +1234568 +2

let _ = assert : unsigned +123456789 +0 +1 +0 7 ≡ Float/create +12345679 +1

let _ = assert : unsigned +123456789 +0 +1 +0 8 ≡ Float/create +123456790 +0

let _ = assert : unsigned +123456789 +0 +1 +0 9 ≡ Float/create +123456790 +0

let _ = assert : unsigned +123456789 +6 +1 +0 4 ≡ Float/create +12346 +10

let _ = assert : unsigned +1234567890 +0 +9 +0 10 ≡ Float/create +1234567899 +0

let _ = assert : unsigned +1234567890 +0 +9 +0 8 ≡ Float/create +1234567890 +0

let _ = assert : unsigned +1234567890 +0 +9 +0 9 ≡ Float/create +1234567899 +0

let _ = assert : unsigned +123456789000000 +0 +1 +0 3 ≡ Float/create +1235 +11

let _ = assert : unsigned +123456789000000 +0 +1 +0 4 ≡ Float/create +12346 +10

let _ = assert : unsigned +321 +0 +123 +0 10 ≡ Float/create +444 +0

let _ = assert : unsigned +321 +2 +12345 +0 0 ≡ Float/create +4 +4

let _ = assert : unsigned +321 +2 +12345 +0 1 ≡ Float/create +44 +3

let _ = assert : unsigned +321 +2 +12345 +0 10 ≡ Float/create +44445 +0

let _ = assert : unsigned +321 +2 +12345 +0 2 ≡ Float/create +444 +2

let _ = assert : unsigned +321 +2 +12345 +0 3 ≡ Float/create +4444 +1

let _ = assert : unsigned +321 +20 +12345 +18 3 ≡ Float/create +4444 +19

let _ = assert : unsigned +321 -20 +12345 -22 3 ≡ Float/create +4444 -21

let _ = assert : unsigned +32154 +0 +12345 +0 10 ≡ Float/create +44499 +0

in  Float/add
