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
                          Natural/fold
                            iterations
                            { _1 : outputType, _2 : Natural }
                            ( λ(result : { _1 : outputType, _2 : Natural }) →
                                { _1 = f xx, _2 = result._2 + 1 }
                            )
                            { _1 = f xx, _2 = 0 }

                    in  computedResult._2 ≡ iterations
              , None = 0 ≡ 0
              }
              opt

in  benchmark
