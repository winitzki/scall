λ(elementKind : Kind) →
λ(exampleType1 : elementKind) →
λ(exampleType2 : elementKind) →
λ(computeTypeForExampleValue1 : elementKind → Type) →
λ(exampleValue1 : computeTypeForExampleValue1 exampleType1) →
  let TList =
        ∀(list : Kind) →
        ∀(cons : elementKind → list → list) →
        ∀(nil : list) →
          list

  let _ = TList : Sort

  let nil
      : TList
      = λ(list : Kind) →
        λ(cons : elementKind → list → list) →
        λ(nil : list) →
          nil

  let cons
      : elementKind → TList → TList
      = λ(head : elementKind) →
        λ(tail : TList) →
        λ(list : Kind) →
        λ(cons : elementKind → list → list) →
        λ(nil : list) →
          cons head (tail list cons nil)

  let exampleList
      : TList
      = cons exampleType1 (cons exampleType2 nil)

  let headOrDefault
      : TList → elementKind → elementKind
      = λ(tlist : TList) →
        λ(default : elementKind) →
          let cons = λ(t : elementKind) → λ(rest : elementKind) → t

          let nil = default

          in  tlist elementKind cons nil

  let headOfExampleList
                        -- This should be equal to exampleType1 but we cannot use `assert` to verify that.
                        =
        headOrDefault exampleList exampleType2

  let _ = exampleValue1 : computeTypeForExampleValue1 headOfExampleList

  in  True
