let L =
      ./ListIntCurry.dhall
        sha256:80e799ff90db01e3f2639cb8f12bc02e9fca2b7860fdd70dd9a16c943b910d8d

let longList1 =
      ./cetest-longlist-c-100000.dhall
        sha256:fb5208a6befceacd8205297d02d8a3fafd19628f7cd3289a44f05963c1824dbd

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

let _ = assert : sum3 ≡ Natural/toInteger (longList1.length * 2)

let sum4 = L.ListInt/sum longList3

let _ = assert : sum4 ≡ Natural/toInteger (longList1.length * 2)

in  True
