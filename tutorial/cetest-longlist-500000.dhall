let L =
      ./ListInt.dhall
        sha256:071fb634c6674fa576912e9b0ba9f013cc5645b4bdbce1ab7de2de9e14cf8fc6

let length = 500000

let longList1
    : L.ListInt
    = Natural/fold length L.ListInt (L.cons +1) L.nil

in  { list = longList1, length }
