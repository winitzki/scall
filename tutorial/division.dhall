-- Routines for integer division.
let unsafeDiv
    : Natural → Natural → Natural
    = let Natural/lessThan = https://prelude.dhall-lang.org/Natural/lessThan

      let Accum = { result : Natural, sub : Natural, done : Bool }

      in  λ(x : Natural) →
          λ(y : Natural) →
            let init
                : Accum
                = { result = 0, sub = x, done = False }

            let update
                : Accum → Accum
                = λ(acc : Accum) →
                    if    acc.done
                    then  acc
                    else  if Natural/lessThan acc.sub y
                    then  acc ⫽ { done = True }
                    else    acc
                          ⫽ { result = acc.result + 1
                            , sub = Natural/subtract y acc.sub
                            }

            let r
                : Accum
                = Natural/fold x Accum update init

            in  r.result

let unsafeDivMod
    : Natural → Natural → { div : Natural, rem : Natural }
    = let Natural/lessThan =
            https://prelude.dhall-lang.org/Natural/lessThan
              sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

      let Accum = { div : Natural, rem : Natural }

      in  λ(x : Natural) →
          λ(y : Natural) →
            let init
                : Accum
                = { div = 0, rem = x }

            let update
                : Accum → Accum
                = λ(acc : Accum) →
                    let _ = "Loop invariant: x == div * y + rem"

                    in  if    Natural/lessThan acc.rem y
                        then  acc
                        else  { div = acc.div + 1
                              , rem = Natural/subtract y acc.rem
                              }

            in  Natural/fold x Accum update init

let bitWidth
    : Natural → Natural
    = λ(n : Natural) →
        let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual

        let Accum = { b : Natural, bitWidth : Natural }

        let init = { b = 1, bitWidth = 0 }

        let update =
              λ(acc : Accum) →
                if    lessThanEqual acc.b n
                then  { b = acc.b * 2, bitWidth = acc.bitWidth + 1 }
                else  acc

        let result
            : Accum
            = Natural/fold n Accum update init

        in  result.bitWidth

let log
    : Natural → Natural → Natural
    = λ(base : Natural) →
      λ(n : Natural) →
        let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual

        let Accum = { b : Natural, log : Natural }

        let init = { b = 1, log = 0 }

        let update =
              λ(acc : Accum) →
                if    lessThanEqual acc.b n
                then  { b = acc.b * base, log = acc.log + 1 }
                else  acc

        let result
            : Accum
            = Natural/fold n Accum update init

        in  Natural/subtract 1 result.log

let gcd
    : Natural → Natural → Natural
    = λ(x : Natural) →
      λ(y : Natural) →
        let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual

        let Pair = { x : Natural, y : Natural }

        let swap
            : Pair → Pair
            = λ(currentPair : Pair) → { x = currentPair.y, y = currentPair.x }

        let sortPair
            : Pair → Pair
            = λ(currentPair : Pair) →
                if    lessThanEqual currentPair.y currentPair.x
                then  currentPair
                else  swap currentPair

        let step
            : Pair → Pair
            = λ(currentPair : Pair) →
                  currentPair
                ⫽ { x = Natural/subtract currentPair.y currentPair.x }

        let update
            : Pair → Pair
            = λ(currentPair : Pair) → sortPair (step currentPair)

        let init = sortPair { x, y }

        let max_iter = init.x

        let result
            : Pair
            = Natural/fold max_iter Pair update init

        in  result.x

let power =
      λ(x : Natural) →
      λ(y : Natural) →
        Natural/fold y Natural (λ(p : Natural) → p * x) 1

in  { unsafeDiv, gcd, log, bitWidth, power, unsafeDivMod }
