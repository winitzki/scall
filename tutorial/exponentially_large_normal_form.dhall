let n = env:N

let h = λ(x : Natural) → λ(b : Bool) → if b then x else x + 1

let k =
      λ(x : Natural) →
        if    Natural/isZero x
        then  h x (Natural/isZero x)
        else  h (x + 1) (Natural/isZero x)

in  λ(a : Natural) → Natural/fold n Natural k a
