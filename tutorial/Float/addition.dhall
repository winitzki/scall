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
    -- Detect if `a` is negligible compared with `b`, within given precision.
    -- The value of `torsor a b` is given.
    : TorsorType → Natural → Bool
    = λ(torsor : TorsorType) →
      λ(prec : Natural) →
        Natural/lessThanEqual (1 + prec + torsor.x) torsor.y

let flipTorsor = λ(torsor : TorsorType) → { x = torsor.y, y = torsor.x }

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

        in  if    Natural/lessThanEqual a.topPower commonSize
            then  let baseline =
                        Natural/max
                          a.topPower
                          (Natural/min commonSize (difference + b.topPower))

                  let bClamped = clampDigits b.mantissa b.topPower baseline

                  let aPadded = Float/pad a baseline

                  in    aPadded
                      ⫽ { mantissa = addOrSubtract aPadded.mantissa bClamped }
            else  let aTruncated = Float/round a commonSize

                  let bClamped =
                        clampDigits
                          b.mantissa
                          b.topPower
                          (Natural/subtract difference commonSize)

                  in    aTruncated
                      ⫽ { mantissa = addOrSubtract aTruncated.mantissa bClamped
                        }

let addUnsignedToGreater =
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
        if    totalUnderflow torsor prec
        then  b
        else  if totalUnderflow (flipTorsor torsor) prec
        then  a
        else  if torsorXLessEqualY torsor
        then  addUnsignedToGreater b a (flipTorsor torsor) prec
        else  addUnsignedToGreater a b torsor prec

let subtractUnsignedFromGreaterBothNonzero
    -- Compute b - a (like Natural/subtract), assuming that b > a > 0.
    : Float → Float → TorsorType → Natural → Float
    = λ(a : Float) →
      λ(b : Float) →
      λ(torsor : TorsorType) →
      λ(prec : Natural) →
        if    totalUnderflow torsor prec
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

              in  if        a.mantissaPositive && b.mantissaPositive
                        ||      a.mantissaPositive == False
                            &&  b.mantissaPositive == False
                  then  applySign (addUnsignedBothNonzero a b torsor prec)
                  else  let compared =
                              compareUnsignedNonzeroWithTorsor absA absB torsor

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

let Float/subtract
    -- Subtracting b - a, like Natural/subtract.
    : Float → Float → Natural → Float
    = λ(a : Float) →
      λ(b : Float) →
      λ(prec : Natural) →
        if    Float/isZero a
        then  Float/negate b
        else  if Float/isZero b
        then  a
        else  let torsor = computeTorsorForBothNonzero a b

              let absA = Float/abs a

              let absB = Float/abs b

              let applySign =
                    λ(result : Float) →
                      if b.mantissaPositive then result else Float/negate result

              in  if        a.mantissaPositive && b.mantissaPositive == False
                        ||  a.mantissaPositive == False && b.mantissaPositive
                  then  applySign (addUnsignedBothNonzero absA absB torsor prec)
                  else  let compared =
                              compareUnsignedNonzeroWithTorsor absA absB torsor

                        in  merge
                              { Greater =
                                  Float/negate
                                    ( applySign
                                        ( subtractUnsignedFromGreaterBothNonzero
                                            absB
                                            absA
                                            torsor
                                            prec
                                        )
                                    )
                              , Equal = Float/zero
                              , Less =
                                  applySign
                                    ( subtractUnsignedFromGreaterBothNonzero
                                        absA
                                        absB
                                        torsor
                                        prec
                                    )
                              }
                              compared

in  { Float/add, Float/subtract }
