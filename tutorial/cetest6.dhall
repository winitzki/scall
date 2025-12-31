let L = ./ListInt.dhall

let length = env:N

let longList1
    : L.ListInt
    = Natural/fold length L.ListInt (cons +1) nil

let sum1
    : Integer
    = L.ListInt/sum longList1

let _ = assert : sum1 ≡ Natural/toInteger length

in  True
