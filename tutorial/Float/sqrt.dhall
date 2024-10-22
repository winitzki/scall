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

let Natural/lessThanEqual =
      https://prelude.dhall-lang.org/Natural/lessThanEqual
        sha256:1a5caa2b80a42b9f58fff58e47ac0d9a9946d0b2d36c54034b8ddfe3cb0f3c99

let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let Optional/default =
      https://prelude.dhall-lang.org/Optional/default
        sha256:5bd665b0d6605c374b3c4a7e2e2bd3b9c1e39323d41441149ed5e30d86e889ad

let List/index =
      https://prelude.dhall-lang.org/List/index
        sha256:e657b55ecae4d899465c3032cb1a64c6aa6dc2aa3034204f3c15ce5c96c03e63

let N = ./numerics.dhall

let T = ./Type.dhall

let Float = T.Float

let Float/add = ./add.dhall

let Float/divide = ./divide.dhall

let Float/multiply = ./multiply.dhall

let Integer/divide =
      λ(i : Integer) →
      λ(n : Natural) →
        N.Integer/mapSign (λ(p : Natural) → (T.divmod p n).div) i

let stop = ./reduce_growth.dhall

let compute_init_approximation =
      λ(x : Float) →
        let exp = T.Float/exponent x

        let exp_for_lead_digit =
              Integer/add (Natural/toInteger (T.Float/topPower x)) exp

        let shift =
              if Natural/even (Integer/abs exp_for_lead_digit) then +0 else -1

        let new_exponent =
              Integer/divide (Integer/add shift exp_for_lead_digit) 2

        let extra_precision = 1

        let extra_precision_factor = N.power T.Base extra_precision

        let lookup =
              λ(i : Natural) →
              λ(list : List Natural) →
                Optional/default Natural 0 (List/index i Natural list)

        let lead_digits =
              lookup
                (T.Float/leadDigit x)
                ( if    Natural/even (Integer/abs exp_for_lead_digit)
                  then  [ 0, 12, 15, 18, 21, 23, 25, 27, 29, 31 ]
                  else  [ 0, 38, 49, 59, 67, 74, 81, 87, 93, 97 ]
                )

        let corrected_exponent =
              Integer/subtract (Natural/toInteger extra_precision) new_exponent

        in  T.Float/create (Natural/toInteger lead_digits) corrected_exponent

let Float/sqrt
    : Float → Natural → Float
    = stop.reduce_growth
        Float
        (λ(x : Float) → stop.predicate_Natural x.mantissa)
        (Natural → Float)
        (λ(_ : Natural) → T.Float/zero)
        ( λ(p : Float) →
            stop.reduce_growth
              Natural
              stop.predicate_Natural
              Float
              T.Float/zero
              ( λ(prec : Natural) →
                  let iterations = 1 + N.log 2 prec

                  let Accum = { x : Float, prec : Natural }

                  in  let init
                          : Accum
                          = { x = compute_init_approximation p, prec = 1 }

                      let update
                          : Accum → Accum
                          = λ(acc : Accum) →
                              let prec = acc.prec * 2

                              let x =
                                    Float/multiply
                                      ( Float/add
                                          acc.x
                                          (Float/divide p acc.x prec)
                                          prec
                                      )
                                      (T.Float/create +5 -1)
                                      prec

                              in  { x, prec }

                      in  (./rounding.dhall).Float/round
                            (Natural/fold iterations Accum update init).x
                            prec
              )
        )

in  Float/sqrt
