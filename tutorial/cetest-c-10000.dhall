let L =
      ./ListIntCurry.dhall
        sha256:80e799ff90db01e3f2639cb8f12bc02e9fca2b7860fdd70dd9a16c943b910d8d

let longList1 =
      ./cetest-longlist-c-10000.dhall
        sha256:b7e176ef374fa2a1138e5bdbde10e8b8ba78161dde8fd2589e0835d92f10eba5

let sum1 = L.ListInt/sum longList1.list

let _ = assert : sum1 ≡ Natural/toInteger longList1.length

let sum2 = L.ListInt/sum longList1.list

let _ = assert : sum2 ≡ Natural/toInteger longList1.length

let longList2 = L.cons +2 longList1.list

let _ = assert : L.headOptional longList2 ≡ Some +2

let longList3 = L.ListInt/concat longList1.list longList1.list

let _ = assert : L.headOptional longList3 ≡ Some +1

let longList4 = L.ListInt/concat longList1.list longList1.list

let _ = assert : L.headOptional longList4 ≡ Some +1

let sum3 = L.ListInt/sum longList3

let _
      -- Computing the sum of the long list:
      =
      assert : sum3 ≡ Natural/toInteger (longList1.length * 2)

let sum4 = L.ListInt/sum longList3

let _
      -- Again computing the sum of the long list:
      =
      assert : sum4 ≡ Natural/toInteger (longList1.length * 2)

in  True
