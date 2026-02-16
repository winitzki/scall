-- Powers of natural numbers.
-- powerNatLoop is a simple implementation, fast when both arguments are < 100000.
-- powerNatSq is asymptotically faster and can work with arguments around 10000000 within seconds.
-- powerNatSqRev is about 2x faster than powerNatSq because it multiplies the powers of $n$ in the order of increasing values. But it still has the same asymptotic complexity.
let Natural/greaterThan =
      https://prelude.dhall-lang.org/Natural/greaterThan
        sha256:f702abcdfcd7ad73619b9285d7e41c3a1d017fb6b8d037cf40bd93bf30c09b2c

let powerNatLoop
    : Natural → Natural → Natural
    = λ(n : Natural) →
      λ(p : Natural) →
        Natural/fold p Natural (λ(prev : Natural) → prev * n) 1

let _ = assert : powerNatLoop 2 13 ≡ 8192

let _ = assert : powerNatLoop 123 0 ≡ 1

let _ = assert : powerNatLoop 123 1 ≡ 123

let powerNatSq
    : Natural → Natural → Natural
    = λ(n : Natural) →
      λ(p : Natural) →
        let PairT
            : Type
            = { of_2 : Natural, of_n : Natural }

        let powers
            : List PairT
            = let Accum
                  : Type
                  = { powers : List PairT, next : PairT }

              let update
                  : Accum → Accum
                  = λ(acc : Accum) →
                      if    Natural/greaterThan acc.next.of_2 p
                      then  acc
                      else  { powers =
                                  acc.powers
                                # [ { of_2 = acc.next.of_2
                                    , of_n = acc.next.of_n
                                    }
                                  ]
                            , next =
                              { of_2 = acc.next.of_2 * 2
                              , of_n = acc.next.of_n * acc.next.of_n
                              }
                            }

              let init
                  : Accum
                  = { powers = [] : List PairT, next = { of_2 = 1, of_n = n } }

              in  (Natural/fold p Accum update init).powers

        let ResultT
            : Type
            = { result : Natural, rest : Natural }

        let update
            : PairT → ResultT → ResultT
            = λ(p : PairT) →
              λ(prev : ResultT) →
                if    Natural/greaterThan p.of_2 prev.rest
                then  prev
                else  { result = prev.result * p.of_n
                      , rest = Natural/subtract p.of_2 prev.rest
                      }

        in  ( List/fold PairT powers ResultT update { result = 1, rest = p }
            ).result

let _ = assert : powerNatSq 123 0 ≡ 1

let _ = assert : powerNatSq 123 1 ≡ 123

let _ = assert : powerNatSq 2 10 ≡ 1024

let powerNatSqRev
    : Natural → Natural → Natural
    = λ(n : Natural) →
      λ(p : Natural) →
        let PairT
            : Type
            = { of_2 : Natural, of_n : Natural }

        let powers
            : List PairT
            = let Accum
                  : Type
                  = { powers : List PairT, next : PairT }

              let update
                  : Accum → Accum
                  = λ(acc : Accum) →
                      if    Natural/greaterThan acc.next.of_2 p
                      then  acc
                      else  { powers =
                                  acc.powers
                                # [ { of_2 = acc.next.of_2
                                    , of_n = acc.next.of_n
                                    }
                                  ]
                            , next =
                              { of_2 = acc.next.of_2 * 2
                              , of_n = acc.next.of_n * acc.next.of_n
                              }
                            }

              let init
                  : Accum
                  = { powers = [] : List PairT, next = { of_2 = 1, of_n = n } }

              in  (Natural/fold p Accum update init).powers

        let ResultT
            : Type
            = { needed : List Natural, rest : Natural }

        let update
            : PairT → ResultT → ResultT
            = λ(p : PairT) →
              λ(prev : ResultT) →
                if    Natural/greaterThan p.of_2 prev.rest
                then  prev
                else  { needed = prev.needed # [ p.of_n ]
                      , rest = Natural/subtract p.of_2 prev.rest
                      }

        let neededPowers
            : ResultT
            = List/fold
                PairT
                powers
                ResultT
                update
                { needed = [] : List Natural, rest = p }

        in  List/fold
              Natural
              neededPowers.needed
              Natural
              (λ(x : Natural) → λ(y : Natural) → x * y)
              1

let _ = assert : powerNatSqRev 123 0 ≡ 1

let _ = assert : powerNatSqRev 123 1 ≡ 123

let _ = assert : powerNatSqRev 2 14 ≡ 16384

in  { powerNatLoop, powerNatSq, powerNatSqRev }
