{-
    Results for noop (the ListInt has not been constructed, linearity in S not yet visible):

    Raw table (N, S, t_sec median):
    5000000	50000	1.048820917
    5000000	75000	1.082226
    5000000	100000	1.118392209
    7500000	50000	1.537466167
    7500000	75000	1.567287458
    7500000	100000	1.594985625
    10000000	50000	2.034146375
    10000000	75000	2.05142675
    10000000	100000	2.090238875

    --- Fit: t ≈ const * N^a * S^b (OLS on log(time)) ---
    const = 3.44157e-07
    a     = 0.925781   (exponent on N)
    b     = 0.06077   (exponent on S)


    Results for ListNat/headOptional:

    Raw table (N, S, t_sec median):
    5000	5000	3.75016775
    5000	7500	6.005081166
    5000	10000	8.722073667
    7500	5000	5.598937333
    7500	7500	9.005040041
    7500	10000	13.029334292
    10000	5000	7.461262416
    10000	7500	11.978271334
    10000	10000	17.395150666

    --- Fit: t ≈ const * N^a * S^b (OLS on log(time)) ---
    const = 2.48113e-08
    a     = 0.994704   (exponent on N)
    b     = 1.21572   (exponent on S)
    R² (log t) = 0.9995


    Results for ListNat/sum:

    Raw table (N, S, t_sec median):
    5000	5000	4.41895675
    5000	7500	6.926563292
    5000	10000	9.95645325
    7500	5000	6.595416041
    7500	7500	10.374043
    7500	10000	14.710602791
    10000	5000	8.870872291
    10000	7500	14.130289166
    10000	10000	19.717812291

    --- Fit: t ≈ const * N^a * S^b (OLS on log(time)) ---
    const = 4.37683e-08
    a     = 1.00498   (exponent on N)
    b     = 1.15817   (exponent on S)
    R² (log t) = 0.9995


    Results for ListNat/length:

    Raw table (N, S, t_sec median):
    5000	5000	4.376460959
    5000	7500	6.954222042
    5000	10000	9.76308425
    7500	5000	6.523111375
    7500	7500	10.449921667
    7500	10000	14.602839625
    10000	5000	8.795718292
    10000	7500	13.906055334
    10000	10000	19.569782916

    --- Fit: t ≈ const * N^a * S^b (OLS on log(time)) ---
    const = 4.47411e-08
    a     = 1.00272   (exponent on N)
    b     = 1.15711   (exponent on S)
    R² (log t) = 0.9999


    Results for ListNat/nonEmpty:

    Raw table (N, S, t_sec median):
    5000	5000	3.880173292
    5000	7500	6.104844833
    5000	10000	8.656271583
    7500	5000	5.856735459
    7500	7500	9.195726541
    7500	10000	13.014530709
    10000	5000	7.802544792
    10000	7500	12.283167167
    10000	10000	17.400565667

    --- Fit: t ≈ const * N^a * S^b (OLS on log(time)) ---
    const = 3.92206e-08
    a     = 1.0081   (exponent on N)
    b     = 1.153   (exponent on S)
    R² (log t) = 0.9997

    -}
let lib =
      ./SortNatLib.dhall
        sha256:bf096188342c8e307414afa71a04dc2cddb8ff2b8cb433f6646891e505f0f77c

let benchmark = ./Benchmark.dhall sha256:0d403a5bda315d97c19410cbc90ea4b1239945c71440ca28feefc61e71349e3d

let nilNat = lib.nilNat

let ListNat = lib.ListNat

let consNat = lib.consNat

let concatNat = lib.concatNat

let ListNat/fromList = lib.ListNat/fromList

let ListNat/toList = lib.ListNat/toList

let ListNat/length = lib.ListNat/length

let makeListNat = lib.makeListNat

let PairLists = lib.PairLists

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

let test1 = mkTest ListNat noop

let test2 = mkTest Natural ListNat/sum

let test3 = mkTest Natural ListNat/length

let test4 = mkTest Bool ListNat/nonEmpty

let test5 = mkTest (Optional Natural) ListNat/headOptional

let test6 = mkTest ListNat identity

let test7 = mkTest ListNat (λ(x : ListNat) → ListNat/concat x x)

in  { test1, test2, test3, test4, test5, test6, test7 }
