-- Compute pi = 16 * arctan ( 1 / 5 ) - 4 * arctan (1 / 239)
-- arctan (1 / n) = 1 / n - 1 / (3 * n ^ 3) + 1 / (5 * n ^ 5) - ...
-- To run this script, supply precision as environment variable PRECISION.
-- For example: PRECISION=20 dhall --file ./compute_pi_machin.dhall
let T =
      ./Type.dhall
        sha256:eb9b0c4b594668945020e2dc430bc312b998f90ff2b8f6ba2a861c2836c144c5

let Float = T.Float

let Float/create = T.Float/create

let Float/show =
      ./show.dhall
        sha256:4cb171d3b191cb0e5c5a477e6e230da297600ff20e275c84dd79a04d531bb434

let Float/add =
      ./add.dhall
        sha256:e0ec80c5c98820b0c9166f75cdc96df64b570f795a392c257e109df1203d7b25

let Float/subtract =
      ./subtract.dhall
        sha256:e49bf29c5be07cdf7311fbdacd8f5da7c043295722385391a23afb05f91e39e8

let Float/multiply =
      ./multiply.dhall
        sha256:a51ab0cfd7690c82b7db49b887644b6a4afda241539da7b10e040c15598eb208

let Float/round =
      ( ./rounding.dhall
          sha256:b38a8d34468e4cab1e087f8ba6a9d92571dc847e6e8811cee35f4400c918aa5b
      ).Float/round

let Float/divide =
      ./divide.dhall
        sha256:07d3a50e5c14319b95164881c396c18091b25a6573a798ded3aedbf176850166

let Float/negate = T.Float/negate

let Float/ofNatural = T.Float/ofNatural

let Natural/log = T.log

let divmod = T.divmod

let identity =
      ( ./compare.dhall
          sha256:da183a6c2829465ad3e4b2dffdbe499040458ce8ff8f16b2a665cf9cb6977637
      ).identity

let arctan_1_n
    : Natural → Natural → Float
    = λ(n : Natural) →
      λ(prec_given : Natural) →
        let log10n = 1 + Natural/log 10 n

        let prec = prec_given + log10n

        let number_of_terms = (divmod prec (2 * log10n)).div + 1

        let Accum =
              { k : Natural
              , positive : Bool
              , current_power : Float
              , current_result : Float
              }

        let one_over_n =
              Float/divide (Float/ofNatural 1) (Float/ofNatural n) prec

        let init =
              { k = 1
              , positive = True
              , current_power = one_over_n
              , current_result = one_over_n
              }

        let update =
              λ(acc : Accum) →
                let k = acc.k + 2

                let positive = acc.positive == False

                let applySign =
                      if positive then identity Float else Float/negate

                let current_power =
                      Float/divide
                        acc.current_power
                        (Float/ofNatural (n * n))
                        prec

                let current_result =
                      Float/add
                        acc.current_result
                        ( applySign
                            ( Float/divide
                                current_power
                                (Float/ofNatural k)
                                prec
                            )
                        )
                        prec

                in  { k, positive, current_power, current_result }

        in  Float/round
              (Natural/fold number_of_terms Accum update init).current_result
              prec

let prec_given = env:PRECISION

let prec = prec_given + Natural/log 2 prec_given

let pi =
      Float/round
        ( Float/subtract
            (Float/multiply (Float/ofNatural 4) (arctan_1_n 239 prec) prec)
            (Float/multiply (Float/ofNatural 16) (arctan_1_n 5 prec) prec)
            prec
        )
        prec_given

in  Float/show pi
