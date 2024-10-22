let T = ./Type.dhall

let Float/show = ./show.dhall

let Q = ./sqrt.dhall

let Float/sqrt = Q.Float/sqrt

let debug_iterations = Q.debug_iterations

let compute_init_approximation = Q.compute_init_approximation

let _ =
        assert
      :   Float/show (compute_init_approximation (T.Float/create +1 -4))
        ≡ Float/show (T.Float/create +12 -3)

let _ =
        assert
      :   Float/show (compute_init_approximation (T.Float/create +1 +10))
        ≡ "+1.2e+5"

let _ =
        assert
      : Float/show (compute_init_approximation (T.Float/create +1 +0)) ≡ "+1.2"

let _ =
        assert
      : Float/show (compute_init_approximation (T.Float/create +1 +2)) ≡ "+12."

let _ =
        assert
      : Float/show (compute_init_approximation (T.Float/create +1 -2)) ≡ "+0.12"

let _ =
        assert
      :   Float/show (compute_init_approximation (T.Float/create +3 -10))
        ≡ "+1.6e-5"

let _ =
        assert
      : Float/show (compute_init_approximation (T.Float/create +16 +0)) ≡ "+3.9"

let _ =
        assert
      : Float/sqrt (T.Float/create +2 +0) 10 ≡ T.Float/create +1414213562 -9

let _ = assert : Float/sqrt (T.Float/create +2 +10) 5 ≡ T.Float/create +14142 +1

let _ = assert : Float/sqrt (T.Float/create +2 -10) 5 ≡ T.Float/create +14142 -9

let _ = assert : Float/sqrt (T.Float/create +16 +0) 5 ≡ T.Float/create +4 +0

let _ = assert : Float/sqrt (T.Float/create +49 +0) 5 ≡ T.Float/create +7 +0

let _ = assert : Float/sqrt (T.Float/create +9 +0) 5 ≡ T.Float/create +3 +0

in  True
