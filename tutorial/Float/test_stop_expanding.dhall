let stop = ./stop_expanding.dhall

let n = env:N

let h = λ(x : Natural) → λ(b : Bool) → if b then x else x + 1

let k1 =
      λ(x : Natural) →
        if    Natural/isZero x
        then  h x (Natural/isZero x)
        else  h (x + 1) (Natural/isZero x)

let k1_stop = stop.expandingNatural Natural 0 k1

let h2 = stop.expandingNatural (Bool → Natural) (λ(_ : Bool) → 0) h

let k2 =
      λ(x : Natural) →
        if    Natural/isZero x
        then  h2 x (Natural/isZero x)
        else  h2 (x + 1) (Natural/isZero x)

let k2_stop = stop.expandingNatural Natural 0 k2

let g = λ(x : Natural) → x + x + 1

let g_stop = stop.expandingNatural Natural 0 g

let k = k2_stop

in  λ(x : Natural) → Natural/fold n Natural k x
