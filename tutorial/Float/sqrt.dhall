let N = ./Numerics.dhall

let T = ./Type.dhall

let Float = T.Float

let Float/add = ./add.dhall

let Float/divide = ./divide.dhall

let Float/multiply = ./multiply.dhall

let Float/sqrt
    : Float → Natural → Float
    = λ(p : Float) →
      λ(prec : Natural) →
        let iterations = 1 + N.log 2 prec
           let Accum = { x : Float, prec : Natural }
        in  let init
                : Accum
                =

                let init_x
                 -- if a < 2 then ( 3.0 + 10.0 * a ) / 15.0  else if a < 16.0 then (15.0 + 3 * a) / 15.0   else  (45.0 + a) / 14.0
                 =


                in { x = init_x, prec = 4 }

            let update  : Accum -> Accum =
                  λ(acc : Accum) →
                  let  x =
                    Float/multiply
                      (Float/add acc.x (Float/divide p acc.x acc.prec) acc.prec)
                      (T.Float/create +5 -1)
                      acc.prec
                      let  prec = acc.prec * 2
                      in { x, prec }

            in  (Natural/fold iterations update init).x

let _ = assert : Float/sqrt (T.Float/create +2 +0) 5 ≡ Float/create +14142 -4

in  Float/sqrt
