let Float/divide = ./divide.dhall

let Float/create = (./Type.dhall).Float/create

let Float/show = ./show.dhall

let check =
      λ(a : Integer) →
      λ(ae : Integer) →
      λ(b : Integer) →
      λ(be : Integer) →
      λ(prec : Natural) →
      λ(expected : Text) →
          Float/show (Float/divide (Float/create a ae) (Float/create b be) prec)
        ≡ expected

let _ = assert : check +123456 +0 +123456 +0 4 "+1."

let _ = assert : check +123456 +0 +123 +0 2 "+1000."

let _ = assert : check +123456 +0 +123 +0 10 "+1003.707317"

let _ = assert : check +1 +0 +3 +0 10 "+0.3333333333"

let _ = assert : check +1 +10 +3 +0 3 "+3.33e+9"

let _ = assert : check -1 +10 +3 +0 2 "-3.3e+9"

let _ = assert : check -1 +10 -3 +0 2 "+3.3e+9"

let _ = assert : check +1 +10 -3 +0 2 "-3.3e+9"

let _ = assert : check +1 -10 -3 +0 2 "-3.3e-11"

let _ = assert : check +1 +0 +3 +10 2 "+3.3e-11"

let _ = assert : check +1 +0 -3 -10000 2 "-3.3e+9999"

let _ = assert : check +1 +100000 -3 -100000 2 "-3.3e+199999"

let _ = assert : check +1 +100000 -3 +100000 2 "-0.33"

let _
      -- Should not be slow even when exponents are very large.
      =
      let power = +1000000000000000000000000000000000000

      in  assert : check +1 power -3 power 2 "-0.33"

let Integer/subtract =
      https://prelude.dhall-lang.org/Integer/subtract
        sha256:a34d36272fa8ae4f1ec8b56222fe8dc8a2ec55ec6538b840de0cbe207b006fda

let _
      -- Should not be slow even when exponents are very large.
      =
      let power = +1000000000000000000000000000000000000

      in    assert
          : check
              +1
              (Integer/negate power)
              -3
              (Integer/subtract +1 power)
              2
              "-3.3e-${Natural/show (Integer/clamp power * 2)}"

let _
      -- Should not be slow even when exponents are very large.
      =
      let power = +1000000000000000000000000000000000000

      in    assert
          : check
              +1
              power
              -3
              (Integer/subtract +1 (Integer/negate power))
              2
              "-3.3e+${Natural/show (Integer/clamp power * 2)}"

let _ = assert : check +1 +0 +239 +0 20 "+0.004184100418410041841"

in  True
