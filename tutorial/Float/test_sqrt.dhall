let T = ./Type.dhall

let List/map =
      https://prelude.dhall-lang.org/List/map
        sha256:dd845ffb4568d40327f2a817eb42d1c6138b929ca758d50bc33112ef3c885680

let Float/show = ./show.dhall

let Float/sqrt = ./sqrt.dhall

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

let test_data =
      [ 1
      , 2
      , 3
      , 4
      , 5
      , 6
      , 7
      , 8
      , 9
      , 10
      , 19
      , 20
      , 29
      , 30
      , 39
      , 40
      , 49
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
      , 999
      ]

let sqrt_data =
      List/map
        Natural
        T.Float
        ( λ(i : Natural) →
            Float/sqrt (T.Float/create (Natural/toInteger i) +0) 15
        )
        test_data

let _ =
        assert
      :   List/map T.Float Text Float/show sqrt_data
        ≡ [ "+1."
          , "+1.41421356237312"
          , "+1.73205080756922"
          , "+2."
          , "+2.2360679774998"
          , "+2.44948974278318"
          , "+2.64575131106461"
          , "+2.82842712474623"
          , "+3."
          , "+3.16227766016838"
          , "+4.35889894354067"
          , "+4.47213595499958"
          , "+5.38516480713451"
          , "+5.47722557505166"
          , "+6.2449979983984"
          , "+6.32455532033676"
          , "+7."
          , "+7.07106781186548"
          , "+7.68114574786861"
          , "+7.74596669241483"
          , "+8.30662386291808"
          , "+8.36660026534076"
          , "+8.88819441731559"
          , "+8.94427190999916"
          , "+9.4339811320566"
          , "+9.48683298050514"
          , "+9.9498743710662"
          , "+31.6069612585582"
          ]

in  True
