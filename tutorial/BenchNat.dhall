{-
  Conclusions:

  - yoneda encoding brings no performance benefits
  - non-curried Church encoding is O(1) for head and non-empty check, while curried Church encoding is O(N) 
  - unfix operations are slow; concat is also apparently slow
  - benchmarking is very tricky, and results depend on the size of the resulting type, need to be careful with choosing the computation so that the result isn't too large


    -}
let lib =
      ./SortNatLib.dhall
        sha256:bf096188342c8e307414afa71a04dc2cddb8ff2b8cb433f6646891e505f0f77c

let benchmark =
      ./Benchmark.dhall sha256:798dbc8ddbffe10990047da18ffde52d4107a96994aa11c66817b1e248df1f64

let nilNat = lib.nilNat

let ListNat = lib.ListNat

let consNat = lib.consNat

let concatNat = lib.concatNat

let ListNat/fromList = lib.ListNat/fromList

let ListNat/toList = lib.ListNat/toList

let ListNat/length = lib.ListNat/length

let makeListNat = lib.makeListNat

let PairLists = lib.PairLists

let Y =
      ./ListNatYoneda.dhall
        sha256:71161a60e1c38761374897ca013ccc3ddb201d5e47868cc4ac928ebcbbda785c

let ListNat/headOptional
    : ListNat → Optional Natural
    = λ(list : ListNat) →
        list
          (Optional Natural)
          (None Natural)
          (λ(x : Natural) → λ(_ : Optional Natural) → Some x)

let _ = assert : ListNat/headOptional (ListNat/fromList [ 1, 2, 3 ]) ≡ Some 1

let _ = assert : ListNat/headOptional nilNat ≡ None Natural

let ListNat/nonEmpty =
      λ(list : ListNat) → list Bool False (λ(x : Natural) → λ(y : Bool) → True)

let _ = assert : ListNat/nonEmpty (ListNat/fromList [ 1, 2, 3 ]) ≡ True

let _ = assert : ListNat/nonEmpty nilNat ≡ False

let ListNat/sum
    : ListNat → Natural
    = λ(list : ListNat) →
        list Natural 0 (λ(x : Natural) → λ(y : Natural) → x + y)

let ListNat/concat = concatNat

let noop = λ(list : ListNat) → nilNat

let identity = λ(list : ListNat) → list

let
    -- let iterations = env:N ? 3
    -- let size = env:S ? 3
    mkTest =
      λ(outputType : Type) →
      λ(f : ListNat → outputType) →
      λ(iterations : Natural) →
      λ(size : Natural) →
        benchmark iterations ListNat (makeListNat size 0 1) outputType f

let makeListNatY
    : Natural → Natural → Natural → Y.ListNatY
    = λ(size : Natural) →
      λ(init : Natural) →
      λ(delta : Natural) →
        ( Natural/fold
            size
            { result : Y.ListNatY, index : Natural }
            ( λ(acc : { result : Y.ListNatY, index : Natural }) →
                { result = Y.ListNatY/cons acc.index acc.result
                , index = Natural/subtract delta acc.index
                }
            )
            { result = Y.ListNatY/nil
            , index = init + Natural/subtract 1 size * delta
            }
        ).result

let _ = assert : Y.ListNatY/toList (makeListNatY 5 0 2) ≡ [ 0, 2, 4, 6, 8 ]

let mkTestY =
      λ(outputType : Type) →
      λ(f : Y.ListNatY → outputType) →
      λ(iterations : Natural) →
      λ(size : Natural) →
        benchmark iterations Y.ListNatY (makeListNatY size 0 1) outputType f

let LN =
    -- List of Naturals implemented using non-curried Church encoding.
      Y.ListNat

let LN/nil = Y.ListNat/nil

let LN/cons = Y.ListNat/cons

let makeListNatL
    : Natural → Natural → Natural → LN
    = λ(size : Natural) →
      λ(init : Natural) →
      λ(delta : Natural) →
        ( Natural/fold
            size
            { result : LN, index : Natural }
            ( λ(acc : { result : LN, index : Natural }) →
                { result = LN/cons acc.index acc.result
                , index = Natural/subtract delta acc.index
                }
            )
            { result = LN/nil, index = init + Natural/subtract 1 size * delta }
        ).result

let LN/fromList = Y.ListNat/fromList

let LN/toList = Y.ListNat/toList

let mkTestL =
      λ(outputType : Type) →
      λ(f : LN → outputType) →
      λ(iterations : Natural) →
      λ(size : Natural) →
        benchmark iterations LN (makeListNatL size 0 1) outputType f

let _ = assert : LN/toList (makeListNatL 3 0 1) ≡ [ 0, 1, 2 ]

let LN/concat = Y.ListNat/concat

let LN/length = Y.ListNat/length

let LN/nonEmpty = Y.ListNat/nonEmpty

let LN/headOptional = Y.ListNat/headOptional

let LN/unfix = Y.ListNat/unfix

let LN/fix = Y.ListNat/fix

let LN/sum = Y.ListNat/sum

let LN/tail = λ(x : LN) → merge { Nil = LN/nil, Cons = λ(pair : { head : Natural, tail : LN }) → pair.tail } (LN/unfix x)

let _ = assert : LN/tail (makeListNatL 5 0 1) ≡ makeListNatL 4 1 1

let test1
          -- median time for iterations=1000 and size=1000 is 0.033490541 seconds
          =
      mkTest ListNat noop

let test2
          -- median time for iterations=1000 and size=1000 is 0.346441083 seconds
          =
      mkTest Natural ListNat/sum

let test3
          -- median time for iterations=1000 and size=1000 is 0.347617083 seconds
          =
      mkTest Natural ListNat/length

let test4
          -- median time for iterations=1000 and size=1000 is 0.309712459 seconds
          =
      mkTest Bool ListNat/nonEmpty

let test5
          -- median time for iterations=1000 and size=1000 is 0.296896667 seconds
          =
      mkTest (Optional Natural) ListNat/headOptional

let test6
          -- median time for iterations=1000 and size=1000 is 0.54549875 seconds
          =
      mkTest ListNat identity

let test7
          -- median time for iterations=1000 and size=1000 is 1.719045042 seconds
          =
      mkTest ListNat (λ(x : ListNat) → ListNat/concat x x)

let test8
          -- median time for iterations=1000 and size=1000 is 1.913241791 seconds
          =
      mkTest (Optional { head : Natural, tail : ListNat }) lib.ListNat/uncons

let test9
          -- median time for iterations=1000 and size=1000 is 0.558496334 seconds
          =
      mkTest ListNat (λ(x : ListNat) → consNat 0 x)

let test10
           -- median time for iterations=1000 and size=1000 is 0.033395042 seconds
           =
      mkTestY Y.ListNatY (λ(x : Y.ListNatY) → Y.ListNatY/nil)

let test11
           -- median time for iterations=1000 and size=1000 is 1.382443042 seconds
           =
      mkTestY Y.ListNatY (λ(x : Y.ListNatY) → Y.ListNatY/cons 0 x)

let test12
           -- median time for iterations=1000 and size=1000 is 0.571812583 seconds
           =
      mkTestY Natural Y.ListNatY/sum

let test13
           -- median time for iterations=1000 and size=1000 is 0.5927135 seconds
           =
      mkTestY Natural Y.ListNatY/length

let test14
           -- median time for iterations=1000 and size=1000 is 0.033361416 seconds
           =
      mkTestY Bool Y.ListNatY/nonEmpty

let test15
           -- median time for iterations=1000 and size=1000 is 0.033398708 seconds
           =
      mkTestY (Optional Natural) Y.ListNatY/headOptional

let test16
           -- median time for iterations=1000 and size=1000 is 3.28195725 seconds
           =
      mkTestY (Y.ListNatF Y.ListNatY) Y.ListNatY/unfix

let test17
           -- median time for iterations=1000 and size=1000 is 5.362828583 seconds
           =
      mkTestY Y.ListNatY (λ(x : Y.ListNatY) → Y.ListNatY/concat x x)

let test18
           -- median time for iterations=1000 and size=1000 is 4.706773625 seconds
           =
      mkTestL LN (λ(x : LN) → LN/concat x x)

let test19
           -- median time for iterations=1000 and size=1000 is 0.033338166 seconds
           =
      mkTestL LN (λ(x : LN) → LN/nil)

let test20
           -- median time for iterations=1000 and size=1000 is 1.380798625 seconds
           =
      mkTestL LN (λ(x : LN) → LN/cons 0 x)

let test21
           -- median time for iterations=1000 and size=1000 is 0.57173375 seconds
           =
      mkTestL Natural (λ(x : LN) → LN/length x)

let test22
           -- median time for iterations=1000 and size=1000 is 0.033640125 seconds
           =
      mkTestL Bool (λ(x : LN) → LN/nonEmpty x)

let test23
           -- median time for iterations=1000 and size=1000 is 0.033306167 seconds
           =
      mkTestL (Optional Natural) (λ(x : LN) → LN/headOptional x)

let test24
           -- median time for iterations=1000 and size=1000 is 5.002143709 seconds
           =
      mkTestL (Y.ListNatF LN) (λ(x : LN) → LN/unfix x)

let test25
           -- median time for iterations=1000 and size=1000 is 0.569281584 seconds
           =
      mkTestL Natural (λ(x : LN) → LN/sum x)

let test26
           -- median time for iterations=1000 and size=1000 is 0.03483125 seconds
           =
      mkTestL (Optional Natural) (λ(x : LN) → LN/headOptional (LN/concat x x))

let test27
           -- median time for iterations=1000 and size=1000 is 1.536003875 seconds
           =
      mkTestL Natural (λ(x : LN) → LN/sum (LN/concat x x))

let test28
           -- median time for iterations=1000 and size=1000 is 0.034080875 seconds
           =
      mkTestL (Optional Natural) (λ(x : LN) → LN/headOptional (LN/tail x))
let test29
           -- median time for iterations=1000 and size=1000 is 1.321234125 seconds
           =
      mkTestL Natural (λ(x : LN) → LN/sum (LN/tail x))

in  { test1
    , test2
    , test3
    , test4
    , test5
    , test6
    , test7
    , test8
    , test9
    , test10
    , test11
    , test12
    , test13
    , test14
    , test15
    , test16
    , test17
    , test18
    , test19
    , test20
    , test21
    , test22
    , test23
    , test24
    , test25
    , test26
    , test27
    , test28
    , test29
    }
