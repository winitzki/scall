-- Helper function to benchmark a given function against data of growing size.
let constTrue
    : Natural → Bool
    = λ(iterations : Natural) →
        Natural/isZero (Natural/subtract 1 (Natural/subtract iterations 1))

let constSome
    : Natural → ∀(a : Type) → a → Optional a
    = λ(iterations : Natural) →
      λ(a : Type) →
      λ(x : a) →
        if constTrue iterations then Some x else None a

let recover
    : Natural → ∀(a : Type) → a → a → a
    = λ(iterations : Natural) →
      λ(a : Type) →
      λ(default : a) →
      λ(x : a) →
        let opt = constSome iterations a x

        in  merge { Some = λ(xx : a) → xx, None = default } opt

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
        let opt =
            -- The value is the same as "Some input" but once-only beta-normalization is enforced at "merge" site.
              constSome iterations inputType input

        in  merge
              { Some =
                  λ(xx : inputType) →
                    let computedResult =
                        -- let results =
                        -- List/build
                        --   outputType
                        --   ( λ(list : Type) →
                        --     λ(cons : outputType → list → list) →
                        --     λ(nil : list) →
                        --       Natural/fold
                        --         iterations
                        --         list
                        --         (λ(result : list) → cons (f xx) result)
                        --         nil
                        --   ) in  List/length outputType results ≡ iterations
                        -- A slowdown in Natural/fold is due to comparisons of previous and next accumulator values at each iteration.
                        -- Let us simplify the accumulator type so that these comparisons become trivial but never true (just the accumulator).
                        -- We force the evaluation of f using constSome, even though the result of f is discarded.
                          Natural/fold
                            iterations
                            Natural
                            ( λ(prev : Natural) →
                                merge
                                  { None = prev
                                  , Some = λ(_ : outputType) → prev + 1
                                  }
                                 (constSome iterations outputType (f xx))
                            )
                            0

                    in  computedResult ≡ iterations
              , None = 0 ≡ 0
              }
              opt

in  benchmark
