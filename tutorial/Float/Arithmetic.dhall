let Natural/lessThanEqual =
      https://prelude.dhall-lang.org/Natural/lessThanEqual
        sha256:1a5caa2b80a42b9f58fff58e47ac0d9a9946d0b2d36c54034b8ddfe3cb0f3c99

let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let power =
      λ(x : Natural) →
      λ(y : Natural) →
        Natural/fold y Natural (λ(p : Natural) → p * x) 1

let log
    : Natural → Natural → Natural
    = λ(base : Natural) →
      λ(n : Natural) →
        let Accum = { b : Natural, log : Natural }

        let init = { b = 1, log = 0 }

        let update =
              λ(acc : Accum) →
                if    Natural/lessThanEqual acc.b n
                then  { b = acc.b * base, log = acc.log + 1 }
                else  acc

        let result
            : Accum
            = Natural/fold n Accum update init

        in  Natural/subtract 1 result.log

let Result = { div : Natural, rem : Natural }

let unsafeDivMod
    : Natural → Natural → Result
    = λ(x : Natural) →
      λ(y : Natural) →
        let init
            : Result
            = { div = 0, rem = x }

        let update
            : Result → Result
            = λ(acc : Result) →
                let _ = "Loop invariant: x == div * y + rem"

                in  if    Natural/lessThan acc.rem y
                    then  acc
                    else  { div = acc.div + 1
                          , rem = Natural/subtract y acc.rem
                          }

        in  Natural/fold x Result update init

let powersOf2Until
    -- create a list [1, 2, 4, 8, ..., 2^p] such that a < b * 2 ^ (p + 1) .
    : Natural → Natural → List Natural
    = λ(a : Natural) →
      λ(b : Natural) →
        let TableT = { result : List Natural, power2 : Natural }

        let succ =
              λ(prev : TableT) →
                if    Natural/lessThan a (prev.power2 * b * 2)
                then  prev
                else  let newPower2 = prev.power2 * 2

                      in  { result = prev.result # [ newPower2 ]
                          , power2 = newPower2
                          }

        in  (Natural/fold a TableT succ { result = [ 1 ], power2 = 1 }).result

let egyptian_div_mod
    : Natural → Natural → Result
    = λ(a : Natural) →
      λ(b : Natural) →
        let powers2 = powersOf2Until a b

        let update
            : Natural → Result → Result
            = λ(power2 : Natural) →
              λ(prev : Result) →
                if    Natural/lessThan prev.rem (power2 * b)
                then  prev
                else  { div = prev.div + power2
                      , rem = Natural/subtract (power2 * b) prev.rem
                      }

        in  List/fold Natural powers2 Result update { div = 0, rem = a }

let _ = assert : powersOf2Until 15 1 ≡ [ 1, 2, 4, 8 ]

let _ = assert : powersOf2Until 16 1 ≡ [ 1, 2, 4, 8, 16 ]

let _ = assert : powersOf2Until 17 1 ≡ [ 1, 2, 4, 8, 16 ]

let _ = assert : powersOf2Until 11 2 ≡ [ 1, 2, 4 ]

let _ = assert : egyptian_div_mod 10 1 ≡ { div = 10, rem = 0 }

let _ = assert : egyptian_div_mod 10 10 ≡ { div = 1, rem = 0 }

let _ = assert : egyptian_div_mod 10 11 ≡ { div = 0, rem = 10 }

let _ = assert : egyptian_div_mod 10 2 ≡ { div = 5, rem = 0 }

let _ = assert : egyptian_div_mod 11 2 ≡ { div = 5, rem = 1 }

in  { log, power, Result, divmod = unsafeDivMod, divrem = egyptian_div_mod }
