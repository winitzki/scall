-- Helper function to benchmark a given function against data of growing size.
let benchmark
    : Natural →
      ∀(inputType : Type) →
      inputType →
      ∀(outputType : Type) →
      (inputType → outputType) →
        Type
    = λ(iterations : Natural) →
      λ(inputType : Type) →
      λ(input : inputType) →
      λ(outputType : Type) →
      λ(f : inputType → outputType) →
        let output = f input

        let computedResult =
              Natural/fold
                iterations
                { _1 : outputType, _2 : Natural }
                ( λ(result : { _1 : outputType, _2 : Natural }) →
                    { _1 = f input, _2 = result._2 + 1 }
                )
                { _1 = output, _2 = 0 }

        in  computedResult ≡ { _1 = output, _2 = iterations }

in  benchmark
