let L =
      ./ListInt.dhall
        sha256:071fb634c6674fa576912e9b0ba9f013cc5645b4bdbce1ab7de2de9e14cf8fc6

let longList1 =
      ./cetest-longlist-400000.dhall
        sha256:8b3c6a891f10a0cc82c72eda74bebd6ba01a2f4d2be0fff65bc5ed1008974e74

let sum1
    : Integer
    = L.ListInt/sum longList1.list

let _ = assert : sum1 === Natural/toInteger longList1.length

in  True
