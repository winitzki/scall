let L =
      ./ListInt.dhall
        sha256:071fb634c6674fa576912e9b0ba9f013cc5645b4bdbce1ab7de2de9e14cf8fc6

let longList1 =
      ./cetest-longlist-100000.dhall
        sha256:c7f742889b29736e8f30890d8a44fd967cda07a45947e3c59ec6b25e0027d70f

let sum1
    : Integer
    = L.ListInt/sum longList1.list

let _ = assert : sum1 === Natural/toInteger longList1.length

in  True
