let division =
      ./division.dhall
        sha256:857179c7a39a87159955b75efbeb39c70bddfa3fd47e44a6267c5b64e38d4bf1

let unsafeDiv = division.unsafeDiv

let gcd = division.gcd

let log = division.log

let bitWidth = division.bitWidth

let power = division.power

let divmod = division.unsafeDivMod

let _ = assert : unsafeDiv 3 2 ≡ 1

let _ = assert : gcd 4 1 ≡ 1

let _ = assert : gcd 4 2 ≡ 2

let _ = assert : gcd 4 3 ≡ 1

let _ = assert : gcd 4 4 ≡ 4

let _ = assert : gcd 5 4 ≡ 1

let _ = assert : gcd 5 0 ≡ 5

let _ = assert : gcd 15 12 ≡ 3

let _ = assert : bitWidth 1 ≡ 1

let _ = assert : bitWidth 2 ≡ 2

let _ = assert : bitWidth 3 ≡ 2

let _ = assert : bitWidth 4 ≡ 3

let _ = assert : log 2 4 ≡ 2

let _ = assert : log 1 4 ≡ 3

let _ = assert : log 0 4 ≡ 3

let _ = assert : log 10 0 ≡ 0

let _ = assert : log 10 1 ≡ 0

let _ = assert : log 10 10 ≡ 1

let _ = assert : log 10 99 ≡ 1

let _ = assert : log 10 100 ≡ 2

let _ = assert : log 10 101 ≡ 2

let _ = assert : gcd 4 1 ≡ 1

let _ = assert : gcd 4 2 ≡ 2

let _ = assert : gcd 4 3 ≡ 1

let _ = assert : gcd 4 4 ≡ 4

let _ = assert : gcd 5 4 ≡ 1

let _ = assert : gcd 5 0 ≡ 5

let _ = assert : gcd 15 12 ≡ 3

let _ = assert : power 1 1 ≡ 1

let _ = assert : power 1 2 ≡ 1

let _ = assert : power 2 1 ≡ 2

let _ = assert : power 10 2 ≡ 100

let _ = assert : power 3 4 ≡ 81

let _ = assert : power 1 0 ≡ 1

let _ = assert : power 0 1 ≡ 0

let _ = assert : power 0 0 ≡ 1

let _ = assert : divmod 1 10 ≡ { div = 0, rem = 1 }

let _ = assert : divmod 10 2 ≡ { div = 5, rem = 0 }

let _ = assert : divmod 10 3 ≡ { div = 3, rem = 1 }

let _ =
      ./floatN.dhall
        sha256:2d21552714de4f3cf44c006ae1d8222b9954379c989dbd030663478f495e9f47

let Nonzero =
      λ(y : Natural) →
        if    Natural/isZero y
        then  "error" ≡ "the safeDiv argument ${Natural/show y} must be nonzero"
        else  {}

let safeDiv = λ(x : Natural) → λ(y : Natural) → λ(_ : Nonzero y) → unsafeDiv x y

let _ = safeDiv 1 1 {=}

in  True
