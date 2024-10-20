let N = ./numerics.dhall

let power = N.power

let _ = assert : power 1 1 ≡ 1

let _ = assert : power 1 2 ≡ 1

let _ = assert : power 2 1 ≡ 2

let _ = assert : power 10 2 ≡ 100

let _ = assert : power 3 4 ≡ 81

let _ = assert : power 1 0 ≡ 1

let _ = assert : power 0 1 ≡ 0

let _ = assert : power 0 0 ≡ 1

let log = N.log

let _ = assert : log 2 4 ≡ 2

let _ = assert : log 1 4 ≡ 3

let _ = assert : log 0 4 ≡ 3

let _ = assert : log 10 0 ≡ 0

let _ = assert : log 10 1 ≡ 0

let _ = assert : log 10 10 ≡ 1

let _ = assert : log 10 99 ≡ 1

let _ = assert : log 10 100 ≡ 2

let _ = assert : log 10 101 ≡ 2

let unsafeDivMod = N.divmod

let _ = assert : unsafeDivMod 1 10 ≡ { div = 0, rem = 1 }

let _ = assert : unsafeDivMod 10 2 ≡ { div = 5, rem = 0 }

let _ = assert : unsafeDivMod 10 3 ≡ { div = 3, rem = 1 }

let powersOf2Until = N.powersOf2Until

let _ = assert : powersOf2Until 15 1 ≡ [ 1, 2, 4, 8 ]

let _ = assert : powersOf2Until 16 1 ≡ [ 1, 2, 4, 8, 16 ]

let _ = assert : powersOf2Until 17 1 ≡ [ 1, 2, 4, 8, 16 ]

let _ = assert : powersOf2Until 11 2 ≡ [ 1, 2, 4 ]

let egyptian_div_mod = N.divrem

let _ = assert : egyptian_div_mod 1 10 ≡ { div = 0, rem = 1 }

let _ = assert : egyptian_div_mod 10 2 ≡ { div = 5, rem = 0 }

let _ = assert : egyptian_div_mod 10 3 ≡ { div = 3, rem = 1 }

let _ = assert : egyptian_div_mod 10 1 ≡ { div = 10, rem = 0 }

let _ = assert : egyptian_div_mod 10 10 ≡ { div = 1, rem = 0 }

let _ = assert : egyptian_div_mod 10 11 ≡ { div = 0, rem = 10 }

let _ = assert : egyptian_div_mod 10 2 ≡ { div = 5, rem = 0 }

let _ = assert : egyptian_div_mod 11 2 ≡ { div = 5, rem = 1 }

in  True
