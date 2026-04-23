let lib =
      ./SortNatLib.dhall
        sha256:bf096188342c8e307414afa71a04dc2cddb8ff2b8cb433f6646891e505f0f77c

let bench =
      ./Benchmark.dhall
        sha256:dd03a83a9605b1a1ad04c3806e9eb3f5ff6e21bbf3ec43b2b6b78b4bc28e38cf

let benchmark = bench.benchmark

let nilNat = lib.nilNat

let ListNat = lib.ListNat

let consNat = lib.consNat

let concatNat = lib.concatNat

let ListNat/fromList = lib.ListNat/fromList

let ListNat/toList = lib.ListNat/toList

let ListNat/partitionSorted = lib.ListNat/partitionSorted

let mergeSorted = lib.mergeSorted

let testMergeSorted = lib.testMergeSorted

let makeListNat = lib.makeListNat

let ListNat/mergeSorted = lib.ListNat/mergeSorted

let PairLists = lib.PairLists

let iterations = env:N ? 3

let size = env:S ? 3

let makePair
    : Natural → PairLists
    = λ(n : Natural) → { _1 = makeListNat n 0 2, _2 = makeListNat n 1 2 }

let runMerge =
      λ(pair : PairLists) → ListNat/toList (ListNat/mergeSorted pair._1 pair._2)

let _ =
        assert
      : benchmark iterations PairLists makePair (List Natural) runMerge size

in  True
