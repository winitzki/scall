let L =
      ./ListInt.dhall
        sha256:071fb634c6674fa576912e9b0ba9f013cc5645b4bdbce1ab7de2de9e14cf8fc6

let longList1 =
      ./cetest-longlist-10000.dhall
        sha256:961c70bd92c37557d1c3f0366abe4f3d28f83b759ff18fbf4a64e1e809f40d9c

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
