let Float/add = ./add.dhall

let Float/show = ./show.dhall

let Float/normalize = (./Type.dhall).Float/normalize

let Float/create = (./Type.dhall).Float/create

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
