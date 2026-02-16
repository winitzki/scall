let L =
      ./ListInt.dhall
        sha256:071fb634c6674fa576912e9b0ba9f013cc5645b4bdbce1ab7de2de9e14cf8fc6

let longList1 =
      ./cetest-longlist-100000.dhall
        sha256:c7f742889b29736e8f30890d8a44fd967cda07a45947e3c59ec6b25e0027d70f

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
