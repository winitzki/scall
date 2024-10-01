-- Floating-point computations. Numbers are represented by mantissa, exponent, and signs.
-- The precision is set via the variable `Digits`.
-- This is a proof of concept. Performance will be very slow.
let D =
      ./division.dhall
        sha256:857179c7a39a87159955b75efbeb39c70bddfa3fd47e44a6267c5b64e38d4bf1

let Result = { div : Natural, rem : Natural }

let divmod
    : Natural → Natural → Result
    = (./EgyptianDivision.dhall).egyptian_div_mod

let List/take =
      https://prelude.dhall-lang.org/List/take
        sha256:b3e08ee8c3a5bf3d8ccee6b2b2008fbb8e51e7373aef6f1af67ad10078c9fbfa

let List/drop =
      https://prelude.dhall-lang.org/List/drop
        sha256:af983ba3ead494dd72beed05c0f3a17c36a4244adedf7ced502c6512196ed0cf

let List/replicate =
      https://prelude.dhall-lang.org/List/replicate
        sha256:d4250b45278f2d692302489ac3e78280acb238d27541c837ce46911ff3baa347

let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let Natural/lessThanEqual =
      https://prelude.dhall-lang.org/Natural/lessThanEqual
        sha256:1a5caa2b80a42b9f58fff58e47ac0d9a9946d0b2d36c54034b8ddfe3cb0f3c99

let Natural/min =
      https://prelude.dhall-lang.org/Natural/min
        sha256:f25f9c462e4dbf0eb15f9ff6ac840c6e9c82255a7f4f2ab408bdab338e028710

let Natural/max =
      https://prelude.dhall-lang.org/Natural/max
        sha256:1f3b18da330223ab039fad11693da72c7e68d516f50502c73f41a89a097b62f7

let Integer/subtract =
      https://prelude.dhall-lang.org/Integer/subtract
        sha256:a34d36272fa8ae4f1ec8b56222fe8dc8a2ec55ec6538b840de0cbe207b006fda

let Integer/positive =
      https://prelude.dhall-lang.org/Integer/positive
        sha256:7bdbf50fcdb83d01f74c7e2a92bf5c9104eff5d8c5b4587e9337f0caefcfdbe3

let Integer/equal =
      https://prelude.dhall-lang.org/Integer/equal
        sha256:2d99a205086aa77eea17ae1dab22c275f3eb007bccdc8d9895b93497ebfc39f8

let Integer/add =
      https://prelude.dhall-lang.org/Integer/add
        sha256:7da1306a0bf87c5668beead2a1db1b18861e53d7ce1f38057b2964b649f59c3b

let Bool/not =
      https://prelude.dhall-lang.org/Bool/not
        sha256:723df402df24377d8a853afed08d9d69a0a6d86e2e5b2bac8960b0d4756c7dc4

let Integer/abs =
      https://prelude.dhall-lang.org/Integer/abs
        sha256:35212fcbe1e60cb95b033a4a9c6e45befca4a298aa9919915999d09e69ddced1

let Text/concat =
      https://prelude.dhall-lang.org/Text/concat
        sha256:731265b0288e8a905ecff95c97333ee2db614c39d69f1514cb8eed9259745fc0

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

in  { T = Float
    , base = Base
    ,  show = Float/show
     , digits = Digits
    , create = Float/create
    , isPositive = Float/positive
    , compare = Float/compare
    , negate = Float/negate
    , Compared
    , abs = Float/abs
    , isZero = Float/isZero
    ,  truncate = Float/truncate
       , round = Float/round
        , add = Float/add
        , subtract = Float/subtract
      , pad = Float/pad
    , doc =
        ''
        The type `Float` represents floating-point numbers at base = ${Natural/show
                                                                         Base}.
        ''
    }
