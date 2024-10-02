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

let D = ./Arithmetic.dhall

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

let computeTorsorForBothNonzero = C.computeTorsorForBothNonzero

let compareUnsignedNonzeroWithTorsor = C.compareUnsignedNonzeroWithTorsor

let R = ./rounding.dhall

let Float/round = R.Float/round

let divmod = T.divmod

let clampDigits -- Make sure x has exactly prec digits. The value x_log_floor must be precomputed.
                =
      λ(x : Natural) →
      λ(x_log_floor : Natural) →
      λ(prec : Natural) →
        let h = 1 + x_log_floor

        in  if    Natural/lessThanEqual h prec
            then  x * D.power Base (Natural/subtract h prec)
            else  (divmod x (D.power Base (Natural/subtract prec h))).div

let totalUnderflow
    -- Detect if `a` is negligible compared to `b` within given precision.
    -- The value of `torsor a b` is given.
    : TorsorType → Natural → Bool
    = λ(torsor : TorsorType) →
      λ(prec : Natural) →
            Natural/isZero prec
        ||  Natural/lessThanEqual (1 + prec + torsor.x) torsor.y

let flipTorsor =
      λ(torsor : TorsorType) → torsor ⫽ { x = torsor.y, y = torsor.x }

let torsorXLessEqualY =
      λ(torsor : TorsorType) → Natural/lessThanEqual torsor.x torsor.y

let Natural/plus = λ(a : Natural) → λ(b : Natural) → a + b

let addOrSubtractUnsignedAIsGreater =
      λ(a : Float) →
      λ(b : Float) →
      λ(torsor : TorsorType) →
      λ(prec : Natural) →
      λ(addOrSubtract : Natural → Natural → Natural) →
        let difference = Natural/subtract torsor.y torsor.x

        let commonSize = prec + 1

        let resultWithNewMantissaOnly =
              if    Natural/lessThanEqual (a.topPower + 1) commonSize
              then  let baseline =
                          Natural/max
                            (a.topPower + 1)
                            ( Natural/min
                                commonSize
                                (difference + b.topPower + 1)
                            )

                    let bClamped =
                          clampDigits
                            b.mantissa
                            b.topPower
                            (Natural/subtract difference baseline)

                    let aPadded =
                          Float/pad
                            a
                            (Natural/subtract (a.topPower + 1) baseline)

                    in    aPadded
                        ⫽ { mantissa = addOrSubtract aPadded.mantissa bClamped }
              else  let aTruncated = Float/round a commonSize

                    let bClamped =
                          clampDigits
                            b.mantissa
                            b.topPower
                            (Natural/subtract difference commonSize)

                    in    aTruncated
                        ⫽ { mantissa =
                              addOrSubtract aTruncated.mantissa bClamped
                          }

        in  T.Float/addExtraData resultWithNewMantissaOnly.(T.FloatBare)

let addUnsignedToGreaterNoUnderflowCheck =
      λ(a : Float) →
      λ(b : Float) →
      λ(torsor : TorsorType) →
      λ(prec : Natural) →
        addOrSubtractUnsignedAIsGreater a b torsor prec Natural/plus

let addUnsignedBothNonzero
    -- Compute a + b, assuming that both are > 0.
    : Float → Float → TorsorType → Natural → Float
    = λ(a : Float) →
      λ(b : Float) →
      λ(torsor : TorsorType) →
      λ(prec : Natural) →
        if    totalUnderflow torsor (Natural/subtract 1 prec)
        then  b
        else  if totalUnderflow (flipTorsor torsor) (Natural/subtract 1 prec)
        then  a
        else  if torsorXLessEqualY torsor
        then  addUnsignedToGreaterNoUnderflowCheck b a (flipTorsor torsor) prec
        else  addUnsignedToGreaterNoUnderflowCheck a b torsor prec

let subtractUnsignedFromGreaterBothNonzero
    -- Compute b - a (like Natural/subtract), assuming that b > a > 0.
    : Float → Float → TorsorType → Natural → Float
    = λ(a : Float) →
      λ(b : Float) →
      λ(torsor : TorsorType) →
      λ(prec : Natural) →
        if    totalUnderflow torsor (Natural/subtract 1 prec)
        then  b
        else  addOrSubtractUnsignedAIsGreater a b torsor prec Natural/subtract

let Float/add
    : Float → Float → Natural → Float
    = λ(a : Float) →
      λ(b : Float) →
      λ(prec : Natural) →
        if    Float/isZero a
        then  b
        else  if Float/isZero b
        then  a
        else  let torsor = computeTorsorForBothNonzero a b

              let absA = Float/abs a

              let absB = Float/abs b

              let applySign =
                    λ(result : Float) →
                      if a.mantissaPositive then result else Float/negate result

              let result =
                    if        a.mantissaPositive && b.mantissaPositive
                          ||      a.mantissaPositive == False
                              &&  b.mantissaPositive == False
                    then  applySign (addUnsignedBothNonzero a b torsor prec)
                    else  let compared =
                                compareUnsignedNonzeroWithTorsor
                                  absA
                                  absB
                                  torsor

                          in  merge
                                { Less =
                                    applySign
                                      ( subtractUnsignedFromGreaterBothNonzero
                                          absA
                                          absB
                                          torsor
                                          prec
                                      )
                                , Equal = Float/zero
                                , Greater =
                                    applySign
                                      ( subtractUnsignedFromGreaterBothNonzero
                                          absB
                                          absA
                                          torsor
                                          prec
                                      )
                                }
                                compared

              in  Float/normalize result

let Float/subtract
    -- Subtracting b - a, like Natural/subtract.
    : Float → Float → Natural → Float
    = λ(a : Float) → Float/add (Float/negate a)

let _ = assert : clampDigits 123 (D.log 10 123) 0 ≡ 0

let _ = assert : clampDigits 123 (D.log 10 123) 1 ≡ 1

let _ = assert : clampDigits 123 (D.log 10 123) 2 ≡ 12

let _ = assert : clampDigits 123 (D.log 10 123) 3 ≡ 123

let _ = assert : clampDigits 123 (D.log 10 123) 4 ≡ 1230

let _ = assert : clampDigits 123 (D.log 10 123) 10 ≡ 1230000000

let _ =
        assert
      :   computeTorsorForBothNonzero (Float/create +1 +0) (Float/create +1 +0)
        ≡ { x = 0, y = 0 }

let _ =
        assert
      :   computeTorsorForBothNonzero (Float/create +10 +0) (Float/create +1 +0)
        ≡ { x = 1, y = 0 }

let _ =
        assert
      :   computeTorsorForBothNonzero (Float/create +1 +0) (Float/create +10 +0)
        ≡ { x = 0, y = 1 }

let _ =
        assert
      :   computeTorsorForBothNonzero
            (Float/create +10 +0)
            (Float/create +100 +0)
        ≡ { x = 1, y = 2 }

let _ =
        assert
      :   computeTorsorForBothNonzero
            (Float/create +99 +0)
            (Float/create +100 +0)
        ≡ { x = 1, y = 2 }

let _ =
        assert
      :   computeTorsorForBothNonzero
            (Float/create +123 +5)
            (Float/create +100 +0)
        ≡ { x = 7, y = 2 }

let _ =
        assert
      :   computeTorsorForBothNonzero (Float/create +1 -5) (Float/create +1 +0)
        ≡ { x = 0, y = 5 }

let _ =
        assert
      :   computeTorsorForBothNonzero (Float/create +1 -5) (Float/create +1 -10)
        ≡ { x = 10, y = 5 }

let _ =
        assert
      :   computeTorsorForBothNonzero
            (Float/create -123 +5)
            (Float/create +123 -10)
        ≡ { x = 17, y = 2 }

let _ =
        assert
      :   computeTorsorForBothNonzero
            (Float/create -123 +5)
            (Float/create -123 -10)
        ≡ { x = 17, y = 2 }

let checkTotalUnderflow =
      λ(x : Integer) →
      λ(ex : Integer) →
      λ(y : Integer) →
      λ(ey : Integer) →
      λ(prec : Natural) →
        totalUnderflow
          (computeTorsorForBothNonzero (Float/create x ex) (Float/create y ey))
          prec

let checkFlipUnderflow =
      λ(x : Integer) →
      λ(ex : Integer) →
      λ(y : Integer) →
      λ(ey : Integer) →
      λ(prec : Natural) →
        totalUnderflow
          ( flipTorsor
              ( computeTorsorForBothNonzero
                  (Float/create x ex)
                  (Float/create y ey)
              )
          )
          prec

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

let unsigned =
      λ(x : Integer) →
      λ(ex : Integer) →
      λ(y : Integer) →
      λ(ey : Integer) →
      λ(prec : Natural) →
        Float/normalize
          ( addUnsignedToGreaterNoUnderflowCheck
              (Float/create x ex)
              (Float/create y ey)
              ( computeTorsorForBothNonzero
                  (Float/create x ex)
                  (Float/create y ey)
              )
              prec
          )

let _ = assert : unsigned +321 +0 +123 +0 10 ≡ Float/create +444 +0

let _ = assert : unsigned +32154 +0 +12345 +0 10 ≡ Float/create +44499 +0

let _ = assert : unsigned +321 +2 +12345 +0 10 ≡ Float/create +44445 +0

let _ = assert : unsigned +321 +2 +12345 +0 3 ≡ Float/create +4444 +1

let _ = assert : unsigned +321 +2 +12345 +0 2 ≡ Float/create +444 +2

let _ = assert : unsigned +321 +2 +12345 +0 1 ≡ Float/create +44 +3

let _ = assert : unsigned +321 +2 +12345 +0 0 ≡ Float/create +4 +4

let _ = assert : unsigned +321 -20 +12345 -22 3 ≡ Float/create +4444 -21

let _ = assert : unsigned +321 +20 +12345 +18 3 ≡ Float/create +4444 +19

let _ = assert : unsigned +123456789000000 +0 +1 +0 3 ≡ Float/create +1235 +11

let _ = assert : unsigned +123456789000000 +0 +1 +0 4 ≡ Float/create +12346 +10

let _ = assert : unsigned +123456789 +6 +1 +0 4 ≡ Float/create +12346 +10

let checkAdd =
      λ(x : Integer) →
      λ(ex : Integer) →
      λ(y : Integer) →
      λ(ey : Integer) →
      λ(prec : Natural) →
        Float/add (Float/create x ex) (Float/create y ey) prec

let _ = assert : checkAdd +321 +0 +123 +0 10 ≡ Float/create +444 +0

let _ = assert : unsigned +123456789 +0 +1 +0 5 ≡ Float/create +123457 +3

let _ = assert : unsigned +123456789 +0 +1 +0 6 ≡ Float/create +1234568 +2

let _ = assert : unsigned +123456789 +0 +1 +0 7 ≡ Float/create +12345679 +1

let _ = assert : unsigned +123456789 +0 +1 +0 8 ≡ Float/create +123456790 +0

let _ = assert : unsigned +123456789 +0 +1 +0 9 ≡ Float/create +123456790 +0

let _ = assert : unsigned +123456789 +0 +1 +0 10 ≡ Float/create +123456790 +0

let _ = assert : unsigned +1234567890 +0 +9 +0 10 ≡ Float/create +1234567899 +0

let _ = assert : unsigned +1234567890 +0 +9 +0 9 ≡ Float/create +1234567899 +0

let _ = assert : unsigned +1234567890 +0 +9 +0 8 ≡ Float/create +1234567890 +0

let _ = assert : checkAdd +1234567890 +0 +9 +0 8 ≡ Float/create +1234567890 +0

let _ = assert : checkAdd +1234567890 +0 +9 +0 9 ≡ Float/create +1234567890 +0

let _ = assert : checkAdd +1234567890 +0 +9 +0 10 ≡ Float/create +1234567899 +0

let _ = assert : checkAdd +123456789 +6 +1 +0 5 ≡ Float/create +123456789 +6

let _ = assert : checkAdd +123456789 +6 +1 +0 10 ≡ Float/create +123456789 +6

in  { Float/add, Float/subtract }
