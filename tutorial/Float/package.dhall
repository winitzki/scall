-- Package.dhall - Export module for Float arithmetic operations
let T =
      ./Type.dhall
        sha256:eb9b0c4b594668945020e2dc430bc312b998f90ff2b8f6ba2a861c2836c144c5

let add =
      ./add.dhall
        sha256:e0ec80c5c98820b0c9166f75cdc96df64b570f795a392c257e109df1203d7b25

let subtract =
      ./subtract.dhall
        sha256:e49bf29c5be07cdf7311fbdacd8f5da7c043295722385391a23afb05f91e39e8

let multiply =
      ./multiply.dhall
        sha256:a51ab0cfd7690c82b7db49b887644b6a4afda241539da7b10e040c15598eb208

let divide =
      ./divide.dhall
        sha256:07d3a50e5c14319b95164881c396c18091b25a6573a798ded3aedbf176850166

let compare =
      ./compare.dhall
        sha256:da183a6c2829465ad3e4b2dffdbe499040458ce8ff8f16b2a665cf9cb6977637

let rounding =
      ./rounding.dhall
        sha256:b38a8d34468e4cab1e087f8ba6a9d92571dc847e6e8811cee35f4400c918aa5b

let show =
      ./show.dhall
        sha256:4cb171d3b191cb0e5c5a477e6e230da297600ff20e275c84dd79a04d531bb434

let sqrt =
      ./sqrt.dhall
        sha256:49a1e98f7d80ebcec73931db4480d99319928028ffa1286da197b7a54dc6b1d1

let reduce_growth =
      ./reduce_growth.dhall
        sha256:9129f3a6766ab3cc8435482c1aa3cb84ef1a6cee80636121e2d1b377b0551ecc

in  { -- Core types
      Float = T.Float
    , FloatBare = T.FloatBare
    , Base = T.Base
    , Pair = T.Pair
    , Float/create = T.Float/create
    , Float/zero = T.Float/zero
    , Float/normalize = T.Float/normalize
    , Float/ofNatural = T.Float/ofNatural
    , Float/pad = T.Float/pad
    , Float/addExtraData = T.Float/addExtraData
    , FloatBare/create = T.FloatBare/create
    , FloatBare/normalize = T.FloatBare/normalize
    , Float/leadDigit = T.Float/leadDigit
    , Float/mantissa = T.Float/mantissa
    , Float/topPower = T.Float/topPower
    , Float/exponent = T.Float/exponent
    , Float/add = add
    , Float/subtract = subtract
    , Float/multiply = multiply
    , Float/divide = divide
    , Float/negate = T.Float/negate
    , Float/abs = T.Float/abs
    , Float/isPositive = T.Float/isPositive
    , Float/isZero = T.Float/isZero
    , Float/compare = compare.Float/compare
    , Compared = compare.Compared
    , Compared/reverse = compare.Compared/reverse
    , Float/round = rounding.Float/round
    , Float/truncate = rounding.Float/truncate
    , Float/show = show
    , Float/sqrt = sqrt
    , divmod = T.divmod
    , log = T.log
    , power = T.power
    , reduce_growth = reduce_growth.reduce_growth
    , reduce_growth_noop = reduce_growth.reduce_growth_noop
    , predicate_Natural = reduce_growth.predicate_Natural
    , predicate_Integer = reduce_growth.predicate_Integer
    }
