let L =
      ./ListInt.dhall
        sha256:071fb634c6674fa576912e9b0ba9f013cc5645b4bdbce1ab7de2de9e14cf8fc6

let longList1 =
      ./cetest-longlist-500000.dhall
              sha256:314723d5e43f43261497825cb0bf8d4690d415df2410fa929b16143d05b6a75a

-- Reading takes 3.46 seconds.

let sum1    = L.ListInt/sum longList1.list

let _ = assert : sum1 ≡ Natural/toInteger longList1.length

-- Computing the sum takes 0.75 seconds.

let sum2    = L.ListInt/sum longList1.list

 let _ = assert : sum2 ≡ Natural/toInteger longList1.length
-- Computing the sum again takes 1.1 seconds now.

let longList2     = L.cons +2 longList1.list

-- Computing cons is O(1), too short to measure

let _ = assert : L.headOptional longList2 ≡ Some +2

let longList3 = L.ListInt/concat longList1.list longList1.list

 let _ = assert : L.headOptional longList3 ≡ Some +1

-- computing concat and headOptional: both O(1), too short to measure

let longList4 = L.ListInt/concat longList1.list longList1.list

 let _ = assert : L.headOptional longList4 ≡ Some +1

-- computing concat and headOptional once again: both O(1), too short to measure

let sum3  = L.ListInt/sum longList3

 let _ = assert : sum3 ≡ Natural/toInteger (longList1.length * 2)

-- computing the sum of the longer list takes 1.3 seconds

let sum4  = L.ListInt/sum longList3

 let _ = assert : sum4 ≡ Natural/toInteger (longList1.length * 2)

-- computing the sum of the longer list again takes 1.8 seconds

in  True
