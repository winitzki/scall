let L =
      ./ListIntCurry.dhall
        sha256:80e799ff90db01e3f2639cb8f12bc02e9fca2b7860fdd70dd9a16c943b910d8d

let longList1 =
      ./cetest-longlist-c-500000.dhall
        sha256:e8cde77e55e41772590b6f141a8ebec0879d773d20599d7980bdf2810bc3a2f6

-- Reading longList1 takes .


let sum1 = L.ListInt/sum longList1.list
let _ = assert : sum1 ≡ Natural/toInteger longList1.length

-- Computing the sum takes 0.75 seconds.

let sum2    = L.ListInt/sum longList1.list

 let _ = assert : sum2 ≡ Natural/toInteger longList1.length
-- Computing the sum again takes 1.1 seconds now.

let longList2 = L.cons +2 longList1.list

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
