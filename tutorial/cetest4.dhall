let L = ./ListInt.dhall

let length = env:N

let longList1
    : L.ListInt
    = Natural/fold length L.ListInt (L.cons +1) L.nil

let sum1
    : Integer
    = L.ListInt/sum longList1

let _ = assert : sum1 ≡ Natural/toInteger length

let longList2
    : L.ListInt
    = L.cons +2 longList1

let _ = assert : L.headOptional longList2 ≡ Some +2

let longList3 = L.ListInt/concat longList1 longList1

let sum3
    : Integer
    = L.ListInt/sum longList3

let _ = assert : sum3 ≡ Natural/toInteger (length * 2)

in  True
