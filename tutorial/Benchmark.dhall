-- Helper function to benchmark a given function against data of growing size.
let benchmark
    : ∀(iterations : Natural) →
      ∀(inputType : Type) →
      ∀(makeInputOfSize : Natural → inputType) →
      ∀(outputType : Type) →
      ∀(f : inputType → outputType) →
      ∀(size : Natural) →
        Type
    = λ(iterations : Natural) →
      λ(inputType : Type) →
      λ(makeInputOfSize : Natural → inputType) →
      λ(outputType : Type) →
      λ(f : inputType → outputType) →
      λ(size : Natural) →
        let input = makeInputOfSize size

        let init = f (makeInputOfSize 2)

        let obtainedList =
              List/build
                outputType
                ( λ(list : Type) →
                  λ(cons : outputType → list → list) →
                  λ(nil : list) →
                     
                      let tail =
                            Natural/fold
                              (Natural/subtract iterations 1)
                              list
                              (λ(rest : list) → cons (f input) rest)
                              nil

                      in  cons init tail
                )

        in  obtainedList ≡ obtainedList

let warmup
    : ∀(inputType : Type) →
      ∀(makeInputOfSize : Natural → inputType) →
      ∀(outputType : Type) →
      ∀(f : inputType → outputType) →
        Type
    = λ(inputType : Type) →
      λ(makeInputOfSize : Natural → inputType) →
      λ(outputType : Type) →
      λ(f : inputType → outputType) →
        benchmark 3 inputType makeInputOfSize outputType f 3

in  { benchmark, warmup }
