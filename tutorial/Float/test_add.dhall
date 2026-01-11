let Float/add =
      ./add.dhall
        sha256:e0ec80c5c98820b0c9166f75cdc96df64b570f795a392c257e109df1203d7b25

let Float/show =
      ./show.dhall
        sha256:4cb171d3b191cb0e5c5a477e6e230da297600ff20e275c84dd79a04d531bb434

let Float/normalize =
      ( ./Type.dhall
          sha256:eb9b0c4b594668945020e2dc430bc312b998f90ff2b8f6ba2a861c2836c144c5
      ).Float/normalize

let Float/create =
      ( ./Type.dhall
          sha256:eb9b0c4b594668945020e2dc430bc312b998f90ff2b8f6ba2a861c2836c144c5
      ).Float/create

let checkAdd =
      λ(x : Integer) →
      λ(ex : Integer) →
      λ(y : Integer) →
      λ(ey : Integer) →
      λ(prec : Natural) →
        Float/add (Float/create x ex) (Float/create y ey) prec

let checkAddShow =
      λ(x : Integer) →
      λ(ex : Integer) →
      λ(y : Integer) →
      λ(ey : Integer) →
      λ(prec : Natural) →
      λ(expected : Text) →
        Float/show (checkAdd x ex y ey prec) ≡ expected

let _ = assert : checkAdd +123456789 +0 +1 +0 10 ≡ Float/create +123456790 +0

let _
      -- Underflow is detected, and the initial number is left unchanged.
      =
      assert : checkAdd +123456789 +0 +1 +0 5 ≡ Float/create +123456789 +0

let _ = assert : checkAdd +123456789 +0 +1 +0 6 ≡ Float/create +123456789 +0

let _ = assert : checkAdd +123456789 +0 +1 +0 7 ≡ Float/create +123456789 +0

let _ = assert : checkAdd +123456789 +0 +1 +0 8 ≡ Float/create +123456789 +0

let _
      -- No underflow with 9 digits precision.
      =
      assert : checkAdd +123456789 +0 +1 +0 9 ≡ Float/create +123456790 +0

let _ = assert : checkAdd +123456789 +6 +1 +0 10 ≡ Float/create +123456789 +6

let _ = assert : checkAdd +123456789 +6 +1 +0 5 ≡ Float/create +123456789 +6

let _ = assert : checkAdd +1234567890 +0 +9 +0 10 ≡ Float/create +1234567899 +0

let _ = assert : checkAdd +1234567890 +0 +9 +0 8 ≡ Float/create +1234567890 +0

let _ = assert : checkAdd +1234567890 +0 +9 +0 9 ≡ Float/create +1234567890 +0

let _ = assert : checkAdd +321 +0 +123 +0 10 ≡ Float/create +444 +0

let _ = assert : checkAddShow +0 +0 -2 +0 10 "-2."

let _ = assert : checkAddShow +1 +0 +123456789 +6 10 "+1.23456789e+14"

let _ = assert : checkAddShow +1 +0 -2 +0 10 "-1."

let _ = assert : checkAddShow +1 +0 -20 +0 10 "-19."

let _ = assert : checkAddShow +12 +0 -1234 +0 10 "-1222."

let _ = assert : checkAddShow +1234 +0 -1 +0 10 "+1233."

let _ = assert : checkAddShow +1234 +0 -12 +0 10 "+1222."

let _ = assert : checkAddShow +1234 +0 -123 +0 10 "+1111."

let _ = assert : checkAddShow +12345678 -8 +123 -2 5 "+1.35345"

let _ = assert : checkAddShow +12345678 -8 +123 -2 6 "+1.353456"

let _ = assert : checkAddShow +12345678 -8 +123 -2 7 "+1.3534567"

let _ = assert : checkAddShow +12345678 -8 +123 -2 8 "+1.35345678"

let _ = assert : checkAddShow +12345678 -8 +123 -2 9 "+1.35345678"

let _ = assert : checkAddShow +123456789 +0 -123456789 +0 10 "0."

let _ = assert : checkAddShow +123456789 +6 +1 +0 10 "+1.23456789e+14"

let _ = assert : checkAddShow +20 +0 -1 +0 10 "+19."

let _ = assert : checkAddShow +3 +0 -2 +0 10 "+1."

let _ = assert : checkAddShow -1 +0 +20 +0 10 "+19."

let _ = assert : checkAddShow -123 -3 +12 -1 10 "+1.077"

let _ = assert : checkAddShow -12345678 -8 +123 -2 5 "+1.10655"

let _ = assert : checkAddShow -12345678 -8 +123 -2 9 "+1.10654322"

let _ = assert : checkAddShow -2 +0 +0 +0 10 "-2."

let _ = assert : checkAddShow -2 +0 +1 +0 10 "-1."

let _ = assert : checkAddShow -2 +0 +3 +0 10 "+1."

let _ = assert : checkAddShow -20 +0 +1 +0 10 "-19."

let _ = assert : checkAddShow -3 +0 +2 +0 10 "-1."

let _ =
      let a1 = Float/create -12345678 -8

      let a2 = Float/create +123 -2

      let _ = assert : Float/show a1 ≡ "-0.12345678"

      let _ = assert : Float/show a2 ≡ "+1.23"

      in  True

in  True
