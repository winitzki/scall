let L =
      ./ListInt.dhall
        sha256:071fb634c6674fa576912e9b0ba9f013cc5645b4bdbce1ab7de2de9e14cf8fc6

let longList1 =
      ./cetest-longlist-200000.dhall
        sha256:409849e348d60aa0e3db3aa78efb48574d4c89e4d68730bb0d1b7e9d4a93ec35

let sum1
    : Integer
    = L.ListInt/sum longList1.list

let _ = assert : sum1 === Natural/toInteger longList1.length

in  True
