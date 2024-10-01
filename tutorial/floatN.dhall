-- Floating-point computations. Numbers are represented by lists of high-base digits.
-- The precision is set via the variable `Digits`.
-- This is a proof of concept. Performance will be very slow.
let D =
      ./division.dhall
        sha256:857179c7a39a87159955b75efbeb39c70bddfa3fd47e44a6267c5b64e38d4bf1

let List/take =
      https://prelude.dhall-lang.org/List/take
        sha256:b3e08ee8c3a5bf3d8ccee6b2b2008fbb8e51e7373aef6f1af67ad10078c9fbfa

let List/drop =
      https://prelude.dhall-lang.org/List/drop
        sha256:af983ba3ead494dd72beed05c0f3a17c36a4244adedf7ced502c6512196ed0cf

let List/replicate =
      https://prelude.dhall-lang.org/List/replicate
        sha256:d4250b45278f2d692302489ac3e78280acb238d27541c837ce46911ff3baa347

let Natural/lessThan =
      https://prelude.dhall-lang.org/Natural/lessThan
        sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c

let Integer/subtract =
      https://prelude.dhall-lang.org/Integer/subtract
        sha256:a34d36272fa8ae4f1ec8b56222fe8dc8a2ec55ec6538b840de0cbe207b006fda

let Integer/positive =
      https://prelude.dhall-lang.org/Integer/positive
        sha256:7bdbf50fcdb83d01f74c7e2a92bf5c9104eff5d8c5b4587e9337f0caefcfdbe3

let Integer/equal =
      https://prelude.dhall-lang.org/Integer/equal
        sha256:2d99a205086aa77eea17ae1dab22c275f3eb007bccdc8d9895b93497ebfc39f8

let Integer/add =
      https://prelude.dhall-lang.org/Integer/add
        sha256:7da1306a0bf87c5668beead2a1db1b18861e53d7ce1f38057b2964b649f59c3b

let Bool/not =
      https://prelude.dhall-lang.org/Bool/not
        sha256:723df402df24377d8a853afed08d9d69a0a6d86e2e5b2bac8960b0d4756c7dc4

let Integer/abs =
      https://prelude.dhall-lang.org/Integer/abs
        sha256:35212fcbe1e60cb95b033a4a9c6e45befca4a298aa9919915999d09e69ddced1

let FloatUnsignedNonzero =
      { mantissa : List Natural, exponent : Natural, exponentPositive : Bool }

let FloatSignedNonzero = FloatUnsignedNonzero ⩓ { mantissaPositive : Bool }

let Float = < Zero | NZ : FloatSignedNonzero >

let showSign = λ(x : Bool) → if x then "+" else "-"

let Base = 10

let HalfBase = D.safeDiv Base 2 {=}

let _ = assert : Natural/lessThan 1 Base ≡ True

let Digits = 3

let _ = assert : Natural/lessThan 1 Digits ≡ True

let MaxBase = D.power Base Digits

let _ = "toDigits does not need to be limited by a small number once the official dhall gets the Natural/fold optimization"
let MaxNatural = MaxBase * Base

let AssertLessThan =
      λ(x : Natural) →
      λ(limit : Natural) →
        if    Natural/lessThan x limit
        then  {}
        else  { error :
                    Natural/show x
                  ≡ "invalid argument, must be less than " ++ Natural/show limit
              }

let toDigits
    : ∀(n : Natural) → AssertLessThan n MaxNatural → List Natural
    = λ(n : Natural) →
      λ(_ : AssertLessThan n MaxNatural) →
        if    Natural/isZero n
        then  [ 0 ]
        else  let Accum = { digits : List Natural, remains : Natural }

              let init
                  : Accum
                  = { digits = [] : List Natural, remains = n }

              let update
                  : Accum → Accum
                  = λ(prev : Accum) →
                      if    Natural/isZero prev.remains
                      then  prev
                      else  let d = D.unsafeDivMod prev.remains Base

                            in  { digits = [ d.rem ] # prev.digits
                                , remains = d.div
                                }

              let _ =
                    "We introduce max_iterations as a function of n because if this expression is a compile-time constant then the normal form of toDigits is a few gigabytes in size"

              let max_iterations = if Natural/isZero n then 1 else Digits + 2

              in  (Natural/fold max_iterations Accum update init).digits

let _ = assert : toDigits 0 {=} ≡ [ 0 ]

let _ = assert : toDigits 1 {=} ≡ [ 1 ]

let _ = assert : toDigits 10 {=} ≡ [ 1, 0 ]

let _ = assert : toDigits 4 {=} ≡ [ 4 ]

let _ = assert : toDigits 12 {=} ≡ [ 1, 2 ]

let _ = assert : toDigits 123 {=} ≡ [ 1, 2, 3 ]

let Float/createNonzero
    : ∀(mantissa : Integer) →
      Integer →
      AssertLessThan (Integer/abs mantissa) MaxNatural →
        FloatSignedNonzero
    = λ(mantissa : Integer) →
      λ(exponent_orig : Integer) →
      λ(ev : AssertLessThan (Integer/abs mantissa) MaxNatural) →
        let absMantissa = Integer/abs mantissa

        let digits = List/take Digits Natural (toDigits absMantissa ev)

        let normalization = Natural/subtract 1 (List/length Natural digits)

        let pad_digits =
              List/replicate
                (Natural/subtract (List/length Natural digits) Digits)
                Natural
                0

        let exponent =
              Integer/add exponent_orig (Natural/toInteger normalization)

        let e = if Natural/isZero absMantissa then 0 else Integer/abs exponent

        in  { mantissa = digits # pad_digits
            , mantissaPositive =
                Integer/positive mantissa || Natural/isZero absMantissa
            , exponent = e
            , exponentPositive = Integer/positive exponent || Natural/isZero e
            }

let Float/create
    : ∀(mantissa : Integer) →
      Integer →
      AssertLessThan (Integer/abs mantissa) MaxNatural →
        Float
    = λ(mantissa : Integer) →
      λ(exponent_orig : Integer) →
      λ(ev : AssertLessThan (Integer/abs mantissa) MaxNatural) →
        if    Natural/isZero (Integer/abs mantissa)
        then  Float.Zero
        else  Float.NZ (Float/createNonzero mantissa exponent_orig ev)

let printDigits
    : List Natural → Text
    = λ(ds : List Natural) →
        List/fold
          Natural
          ds
          Text
          (λ(d : Natural) → λ(prev : Text) → Natural/show d ++ prev)
          ""

let _ = assert : printDigits ([] : List Natural) ≡ ""

let _ = assert : printDigits [ 1 ] ≡ "1"

let _ = assert : printDigits [ 1, 2, 3 ] ≡ "123"

let Float/showNonzero
    : FloatSignedNonzero → Text
    = λ(x : FloatSignedNonzero) →
        let extraZeros =
              Natural/subtract (List/length Natural x.mantissa) Digits

        let padding = Natural/fold extraZeros Text (λ(a : Text) → a ++ "0") ""

        let xsign = showSign x.mantissaPositive

        let adjustedExponent =
              Integer/subtract
                +0
                ( if    x.exponentPositive
                  then  Natural/toInteger x.exponent
                  else  Integer/negate (Natural/toInteger x.exponent)
                )

        let esign =
              showSign
                (     Integer/positive adjustedExponent
                  ||  Integer/equal +0 adjustedExponent
                )

        let showExp =
              if    Integer/equal +0 adjustedExponent
              then  ""
              else  "e" ++ Integer/show adjustedExponent

        in      xsign
            ++  printDigits (List/take 1 Natural x.mantissa)
            ++  "."
            ++  printDigits (List/drop 1 Natural x.mantissa)
            ++  padding
            ++  showExp

let Float/show
    : Float → Text
    = λ(x : Float) →
        merge
          { Zero = "0.0", NZ = λ(f : FloatSignedNonzero) → Float/showNonzero f }
          x

let _ =
        assert
      :   Float/create +12 +0 {=}
        ≡ Float.NZ
            { mantissa = [ 1, 2, 0 ]
            , mantissaPositive = True
            , exponent = 1
            , exponentPositive = True
            }

let _ =
        assert
      :   Float/create +12 +1 {=}
        ≡ Float.NZ
            { mantissa = [ 1, 2, 0 ]
            , mantissaPositive = True
            , exponent = 2
            , exponentPositive = True
            }

let _ =
        assert
      :   Float/create +12 -0 {=}
        ≡ Float.NZ
            { mantissa = [ 1, 2, 0 ]
            , mantissaPositive = True
            , exponent = 1
            , exponentPositive = True
            }

let _ =
        assert
      :   Float/create +12 -2 {=}
        ≡ Float.NZ
            { mantissa = [ 1, 2, 0 ]
            , mantissaPositive = True
            , exponent = 1
            , exponentPositive = False
            }

let _ =
        assert
      :   Float/create +12 -3 {=}
        ≡ Float.NZ
            { mantissa = [ 1, 2, 0 ]
            , mantissaPositive = True
            , exponent = 2
            , exponentPositive = False
            }

let _ =
        assert
      :   Float/create +1 +2 {=}
        ≡ Float.NZ
            { mantissa = [ 1, 0, 0 ]
            , mantissaPositive = True
            , exponent = 2
            , exponentPositive = True
            }

let _ =
        assert
      :   Float/create +12 -1 {=}
        ≡ Float.NZ
            { mantissa = [ 1, 2, 0 ]
            , mantissaPositive = True
            , exponent = 0
            , exponentPositive = True
            }

let _ =
        assert
      :   Float/create +123 -3 {=}
        ≡ Float.NZ
            { mantissa = [ 1, 2, 3 ]
            , mantissaPositive = True
            , exponent = 1
            , exponentPositive = False
            }

let example = Float/createNonzero +1 +0 {=}

let _ = assert : Float/showNonzero example ≡ "+1.00"

let _ =
        assert
      : Float/showNonzero (example ⫽ { mantissaPositive = False }) ≡ "-1.00"

let _ =
        assert
      : Float/showNonzero (example ⫽ { exponentPositive = False }) ≡ "+1.00"

let _ = assert : Float/showNonzero (Float/createNonzero +0 +10 {=}) ≡ "+0.00"

let _ = assert : Float/showNonzero (Float/createNonzero +0 -10 {=}) ≡ "+0.00"

let _ = assert : Float/showNonzero (Float/createNonzero +2 +0 {=}) ≡ "+2.00"

let _ = assert : Float/showNonzero (Float/createNonzero +2 -0 {=}) ≡ "+2.00"

let _ = assert : Float/showNonzero (Float/createNonzero -2 -0 {=}) ≡ "-2.00"

let _ = assert : Float/showNonzero (Float/createNonzero +12 +0 {=}) ≡ "+1.20e+1"

let _ =
      assert : Float/showNonzero (Float/createNonzero +123 +0 {=}) ≡ "+1.23e+2"

let _ =
      assert : Float/showNonzero (Float/createNonzero +1234 +0 {=}) ≡ "+1.23e+2"

let _ = assert : Float/showNonzero (Float/createNonzero +2 +1 {=}) ≡ "+2.00e+1"

let _ =
      assert : Float/showNonzero (Float/createNonzero -123 -0 {=}) ≡ "-1.23e+2"

let _ =
      assert : Float/showNonzero (Float/createNonzero +123 -1 {=}) ≡ "+1.23e+1"

let _ = assert : Float/showNonzero (Float/createNonzero -123 -2 {=}) ≡ "-1.23"

let _ =
      assert : Float/showNonzero (Float/createNonzero +123 -3 {=}) ≡ "+1.23e-1"

let _ = assert : Float/show (Float/create -123 -2 {=}) ≡ "-1.23"

let _ = assert : Float/show (Float/create +123 -3 {=}) ≡ "+1.23e-1"

let _ = assert : Float/show (Float/create +0 -10 {=}) ≡ "0.0"

in  { T = Float, base = Base, digits = Digits, show = Float/show, create = Float/create, AssertLessThan, doc = ''
The NonzeroFloat type is represented by a list with a fixed number of digits = ${Digits} of mantissa, base = ${Base}.
The first digit of mantissa is always nonzero.
(Zero floats are represented by a different part of the union type.)
'' }
