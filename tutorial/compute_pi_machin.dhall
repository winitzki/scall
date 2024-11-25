-- Compute pi = 16 * arctan ( 1 / 5 ) - 4 * arctan (1 / 239)
-- arctan (1 / n) = 1 / n - 1 / (3 * n ^ 3) + 1 / (5 * n ^ 5) - ...
-- To run this script, supply precision as environment variable PRECISION.
-- For example: PRECISION=20 dhall --file ./compute_pi_machin.dhall
let T = ./Float/Type.dhall

let Float = T.Float

let Float/create = T.Float/create

let Float/show = ./Float/show.dhall

let Float/add = ./Float/add.dhall

let Float/subtract = ./Float/subtract.dhall

let Float/multiply = ./Float/multiply.dhall

let Float/round = (./Float/rounding.dhall).Float/round

let Float/divide = ./Float/divide.dhall

let Float/negate = T.Float/negate

let Float/ofNatural = T.Float/ofNatural

let Natural/log = T.log

let divmod = T.divmod

let identity = (./Float/compare.dhall).identity

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
