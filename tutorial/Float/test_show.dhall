let Float/create = (./Type.dhall).Float/create

let Float/show = ./show.dhall

let test_show = λ(x : Integer) → λ(e : Integer) → Float/show (Float/create x e)

let _ = assert : test_show +0 -1234 ≡ "0."

let _ = assert : test_show -0 -1234 ≡ "0."

let _ = assert : test_show +1 -0 ≡ "+1."

let _ = assert : test_show -1 -0 ≡ "-1."

let _ = assert : test_show +10 -0 ≡ "+10."

let _ = assert : test_show -10 -0 ≡ "-10."

let _ = assert : test_show +10 +1 ≡ "+100."

let _ = assert : test_show -10 +1 ≡ "-100."

let _ = assert : test_show +10 +2 ≡ "+1000."

let _ = assert : test_show -10 +2 ≡ "-1000."

let _ = assert : test_show +10 +3 ≡ "+1.e+4"

let _ = assert : test_show -10 +3 ≡ "-1.e+4"

let _ = assert : test_show +10 -1 ≡ "+1."

let _ = assert : test_show -10 -1 ≡ "-1."

let _ = assert : test_show +100 -1 ≡ "+10."

let _ = assert : test_show -100 -1 ≡ "-10."

let _ = assert : test_show +12 -1 ≡ "+1.2"

let _ = assert : test_show +100000 -1 ≡ "+1.e+4"

let _ = assert : test_show -100000 -1 ≡ "-1.e+4"

let _ = assert : test_show +100001 -1 ≡ "+1.00001e+4"

let _ = assert : test_show -100001 -1 ≡ "-1.00001e+4"

let _ = assert : test_show +110001 -1 ≡ "+1.10001e+4"

let _ = assert : test_show -110001 -1 ≡ "-1.10001e+4"

let _ = assert : test_show +123456789 -8 ≡ "+1.23456789"

let _ = assert : test_show -123456789 -8 ≡ "-1.23456789"

let _ = assert : test_show +123456789 -9 ≡ "+0.123456789"

let _ = assert : test_show -123456789 -9 ≡ "-0.123456789"

let _ = assert : test_show +11 -4 ≡ "+0.0011"

let _ = assert : test_show +1 -3 ≡ "+0.001"

let _ = assert : test_show +10 -4 ≡ "+0.001"

let _ = assert : test_show +10 -2 ≡ "+0.1"

let _ = assert : test_show +123456789 -10 ≡ "+0.0123456789"

let _ = assert : test_show -123456789 -10 ≡ "-0.0123456789"

let _ = assert : test_show +123456789 -11 ≡ "+0.00123456789"

let _ = assert : test_show -123456789 -11 ≡ "-0.00123456789"

let _ = assert : test_show +123456789 -12 ≡ "+1.23456789e-4"

let _ = assert : test_show -123456789 -12 ≡ "-1.23456789e-4"

let _ = assert : test_show +101 -1 ≡ "+10.1"

let _ = assert : test_show -101 -1 ≡ "-10.1"

let _ = assert : test_show +1001 -1 ≡ "+100.1"

let _ = assert : test_show -1001 -1 ≡ "-100.1"

let _ = assert : test_show +10001 -1 ≡ "+1000.1"

let _ = assert : test_show -10001 -1 ≡ "-1000.1"

let _ = assert : test_show +110000 -1 ≡ "+1.1e+4"

let _ = assert : test_show -110000 -1 ≡ "-1.1e+4"

let _
      -- Should not be slow even if the exponent is large.
      =
        assert
      :   test_show +1 +1000000000000000000000000000000000000
        ≡ "+1.e+1000000000000000000000000000000000000"

in  True
