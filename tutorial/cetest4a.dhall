let L =
      ./ListInt.dhall
        sha256:071fb634c6674fa576912e9b0ba9f013cc5645b4bdbce1ab7de2de9e14cf8fc6

let longList1 =
      ./cetest-longlist-200000.dhall
        sha256:409849e348d60aa0e3db3aa78efb48574d4c89e4d68730bb0d1b7e9d4a93ec35

-- Reading takes 1.37 seconds.

let sum1    = L.ListInt/sum longList1.list

let _ = assert : sum1 ≡ Natural/toInteger longList1.length

-- Computing the sum takes 0.42 seconds.

let sum2    = L.ListInt/sum longList1.list

 let _ = assert : sum2 ≡ Natural/toInteger longList1.length
-- Computing the sum again takes 0.2 seconds now.

-- let longList2     = L.cons +2 longList1.list

-- Computing cons is O(1), too short to measure

-- let _ = assert : L.headOptional longList2 ≡ Some +2

let longList3 = L.ListInt/concat longList1.list longList1.list

 let _ = assert : L.headOptional longList3 ≡ Some +1

-- computing concat and headOptional: both O(1), too short to measure

let sum3  = L.ListInt/sum longList3

 let _ = assert : sum3 ≡ Natural/toInteger (longList1.length * 2)

-- computing the sum of the longer list takes 0.5 seconds

in  True
