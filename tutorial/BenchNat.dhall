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

let iterations = env:N ? 3

let size = env:S ? 3

let _ =
        assert
      : benchmark
          iterations
          ListNat
          (λ(size : Natural) → makeListNat size 0 1)
          (Optional Natural)
          ListNat/headOptional
          size

in  True
