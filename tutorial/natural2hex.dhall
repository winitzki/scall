-- This converts a number with 20,000 digits to hexadecimal in about 30 seconds on a fast Mac M1.

let lessThanEqual =
      https://prelude.dhall-lang.org/Natural/lessThanEqual
        sha256:1a5caa2b80a42b9f58fff58e47ac0d9a9946d0b2d36c54034b8ddfe3cb0f3c99

let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let generate =
      https://prelude.dhall-lang.org/List/generate
        sha256:78ff1ad96c08b88a8263eea7bc8381c078225cfcb759c496f792edb5a5e0b1a4

let iterate =
      https://prelude.dhall-lang.org/List/iterate
        sha256:e4999ccce190a2e2a6ab9cb188e3af6c40df474087827153005293f11bfe1d26

let List/index =
      https://prelude.dhall-lang.org/List/index
        sha256:e657b55ecae4d899465c3032cb1a64c6aa6dc2aa3034204f3c15ce5c96c03e63

let Text/concatMap =
      https://prelude.dhall-lang.org/Text/concatMap
        sha256:7a0b0b99643de69d6f94ba49441cd0fa0507cbdfa8ace0295f16097af37e226f

let Optional/default =
      https://prelude.dhall-lang.org/Optional/default
        sha256:5bd665b0d6605c374b3c4a7e2e2bd3b9c1e39323d41441149ed5e30d86e889ad

let stop = ./Float/reduce_growth.dhall

let DivMod = { div : Natural, rem : Natural }

let unsafeDivMod
    : Natural → Natural → DivMod
    = let Accum = DivMod

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

let unsafeDivModStop
    : Natural → Natural → DivMod
    = stop.reduce_growth_Natural_Natural
        DivMod
        { rem = 0, div = 0 }
        unsafeDivMod

let concatMapStop
    : (Natural → Text) → List Natural → Text
    = λ(f : Natural → Text) →
        stop.reduce_growth_List Text "" Natural (Text/concatMap Natural f)

let indexTextStop
    : Natural → List Text → Optional Text
    = stop.reduce_growth_Natural
        (List Text → Optional Text)
        (λ(_ : List Text) → None Text)
        ( λ(i : Natural) →
            stop.reduce_growth_List
              (Optional Text)
              (None Text)
              Text
              (λ(digits : List Text) → List/index i Text digits)
        )

let indexTextStop1
    : Natural → List Text → Optional Text
    = stop.reduce_growth_Natural
        (List Text → Optional Text)
        (λ(_ : List Text) → None Text)
        (λ(i : Natural) → λ(digits : List Text) → List/index i Text digits)

let indexTextStop2
    : Natural → List Text → Optional Text
    = λ(i : Natural) →
        stop.reduce_growth_List
          (Optional Text)
          (None Text)
          Text
          (λ(digits : List Text) → List/index i Text digits)

let log
    : Natural → Natural → Natural
    = λ(base : Natural) →
      λ(n : Natural) →
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

        in  result.log

let hex_digits =
      [ "0"
      , "1"
      , "2"
      , "3"
      , "4"
      , "5"
      , "6"
      , "7"
      , "8"
      , "9"
      , "A"
      , "B"
      , "C"
      , "D"
      , "E"
      , "F"
      ]

let tohex =
      λ(x : Natural) →
        let Accum = { digits_so_far : List Natural, remainder : Natural }

        let init
            : Accum
            = { digits_so_far = [] : List Natural, remainder = x }

        let update
            : Natural → Accum → Accum
            = λ(p : Natural) →
              λ(acc : Accum) →
                let divmod = unsafeDivMod acc.remainder p

                in  { digits_so_far = acc.digits_so_far # [ divmod.div ]
                    , remainder = divmod.rem
                    }

        let powers_of_16 =
              iterate (log 16 x) Natural (λ(p : Natural) → p * 16) 1

        let digits
            : Accum
            = List/fold Natural powers_of_16 Accum update init

        in  digits

let _ =
        assert
      : tohex 10 ≡ { digits_so_far = [ 10 ] : List Natural, remainder = 0 }

let _ =
        assert
      : tohex 64 ≡ { digits_so_far = [ 4, 0 ] : List Natural, remainder = 0 }

let Natural/toHex
    : Natural → Text
    = λ(x : Natural) →
        if    Natural/isZero x
        then  "0x0"
        else      "0x"
              ++  Text/concatMap
                    Natural
                    ( λ(d : Natural) →
                        Optional/default Text "?" (indexTextStop1 d hex_digits)
                    )
                    (tohex x).digits_so_far

let _ = assert : Natural/toHex 0 ≡ "0x0"

let _ = assert : Natural/toHex 1 ≡ "0x1"

let _ = assert : Natural/toHex 10 ≡ "0xA"

let _ = assert : Natural/toHex 16 ≡ "0x10"

let _ = assert : Natural/toHex 64 ≡ "0x40"

let _ = assert : Natural/toHex 1023 ≡ "0x3FF"

let _ = assert : Natural/toHex 1025 ≡ "0x401"

let _ = assert : Natural/toHex 12345 ≡ "0x3039"

in  { Natural/toHex }
