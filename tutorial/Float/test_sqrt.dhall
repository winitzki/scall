let T = ./Type.dhall

let C = ./compare.dhall

let List/map =
      https://prelude.dhall-lang.org/List/map
        sha256:dd845ffb4568d40327f2a817eb42d1c6138b929ca758d50bc33112ef3c885680

let List/zip =
      https://prelude.dhall-lang.org/List/zip
        sha256:85ed955eabf3998767f4ad2a28e57d40cd4c68a95519d79e9b622f1d26d979da

let List/replicate =
      https://prelude.dhall-lang.org/List/replicate
        sha256:d4250b45278f2d692302489ac3e78280acb238d27541c837ce46911ff3baa347

let Float/show = ./show.dhall

let Float/sqrt = ./sqrt.dhall

let test_data =
      [ 1
      , 199
      , 2
      , 299
      , 3
      , 399
      , 4
      , 499
      , 5
      , 599
      , 6
      , 699
      , 7
      , 799
      , 8
      , 899
      , 9
      , 999
      , 10
      , 19
      , 1999
      , 20
      , 29
      , 2999
      , 30
      , 39
      , 3999
      , 40
      , 49
      , 4999
      , 50
      , 59
      , 60
      , 69
      , 70
      , 79
      , 80
      , 89
      , 90
      , 99
      ]

let prec = 15

let sqrt_data =
      List/map
        Natural
        T.Float
        ( λ(i : Natural) →
            Float/sqrt (T.Float/create (Natural/toInteger i) +0) prec
        )
        test_data

let sqrt_data_squared =
      List/map
        T.Float
        T.Float
        (λ(x : T.Float) → ./multiply.dhall x x (prec + 5))
        sqrt_data

let roundoff_errors
    : List T.Float
    = List/map
        { _1 : Natural, _2 : T.Float }
        T.Float
        ( λ(p : { _1 : Natural, _2 : T.Float }) →
            T.Float/abs
              ( ./subtract.dhall
                  (T.Float/ofNatural 1)
                  (./divide.dhall p._2 (T.Float/ofNatural p._1) (prec + 5))
                  (prec + 5)
              )
        )
        (List/zip Natural test_data T.Float sqrt_data_squared)

let compare =
      λ(x : T.Float) →
        C.Float/compare
          x
          ( T.Float/create
              +1
              (Integer/negate (Natural/toInteger (Natural/subtract 1 prec)))
          )

let roundoff_errors_compared_to_precision =
      List/map T.Float C.Compared compare roundoff_errors

let _ =
    {- assert
          :   List/zip
                Natural
                test_data
                Text
                (List/map T.Float Text Float/show roundoff_errors)
            ≡ [ { _1 = 0, _2 = "" } ]
    -}   True

let _ =
        assert
      :   List/replicate (List/length Natural test_data) C.Compared C.Compared.Less
        ≡ roundoff_errors_compared_to_precision

let _ = assert : Float/sqrt (T.Float/create +4 +0) 4 ≡ T.Float/create +2 +0

let _ = assert : Float/sqrt (T.Float/create +2 +0) 5 ≡ T.Float/create +14142 -4

let _ =
        assert
      : Float/sqrt (T.Float/create +2 +0) 10 ≡ T.Float/create +1414213562 -9

let _ = assert : Float/sqrt (T.Float/create +2 +10) 5 ≡ T.Float/create +14142 +1

let _ = assert : Float/sqrt (T.Float/create +2 -10) 5 ≡ T.Float/create +14142 -9

let _ = assert : Float/sqrt (T.Float/create +16 +0) 5 ≡ T.Float/create +4 +0

let _ = assert : Float/sqrt (T.Float/create +49 +0) 5 ≡ T.Float/create +7 +0

let _ = assert : Float/sqrt (T.Float/create +9 +0) 5 ≡ T.Float/create +3 +0

let showsqrt =
      λ(x : Integer) →
      λ(ex : Integer) →
      λ(prec : Natural) →
        Float/show (Float/sqrt (T.Float/create x ex) prec)

let _ = assert : showsqrt +62501 -4 8 ≡ "+2.50002"

let _ = assert : showsqrt +62501 +0 8 ≡ "+250.002"

let _ = assert : showsqrt +62501 +4 8 ≡ "+2.50002e+4"

let _ = assert : showsqrt +62591 -20 13 ≡ "+2.501819338002e-8"

let _ = assert : showsqrt +999 -2 15 ≡ "+3.16069612585582"

in  True
