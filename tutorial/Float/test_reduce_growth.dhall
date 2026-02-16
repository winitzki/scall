let stop =
      ./reduce_growth.dhall
        sha256:9129f3a6766ab3cc8435482c1aa3cb84ef1a6cee80636121e2d1b377b0551ecc

let n = env:N

let h = λ(x : Natural) → λ(b : Bool) → if b then x else x + 1

let k1 =
      λ(x : Natural) →
        if    Natural/isZero x
        then  h x (Natural/isZero x)
        else  h (x + 1) (Natural/isZero x)

let k1_stop = stop.reduce_growth_Natural Natural 0 k1

let h2 = stop.reduce_growth_Natural (Bool → Natural) (λ(_ : Bool) → 0) h

let k2 =
      λ(x : Natural) →
        if    Natural/isZero x
        then  h2 x (Natural/isZero x)
        else  h2 (x + 1) (Natural/isZero x)

let k2_stop = stop.reduce_growth_Natural Natural 0 k2

let g = λ(x : Natural) → x + x + 1

let g_stop = stop.reduce_growth_Natural Natural 0 g

let k = k1_stop

in  λ(x : Natural) → Natural/fold n Natural k x
