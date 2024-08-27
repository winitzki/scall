let Monoid = λ(m : Type) → { empty : m, append : m → m → m }

let monoidBoolOr
    : Monoid Bool
    = { empty = False, append = λ(x : Bool) → λ(y : Bool) → x || y }

let Functor = (./Functor.dhall).Functor

let Foldable =
      λ(F : Type → Type) → { reduce : ∀(M : Type) → Monoid M → F M → M }

let hylo_Nat
    : ∀(F : Type → Type) →
      Functor F →
      Natural →
      ∀(t : Type) →
      t →
      (t → F t) →
      ∀(r : Type) →
      (F r → r) →
      (t → r) →
        r
    = λ(F : Type → Type) →
      λ(functorF : Functor F) →
      λ(limit : Natural) →
      λ(t : Type) →
      λ(seed : t) →
      λ(coalg : t → F t) →
      λ(r : Type) →
      λ(alg : F r → r) →
      λ(stopgap : t → r) →
        let update
            : (t → r) → t → r
            = λ(f : t → r) → λ(y : t) → alg (functorF.fmap t r f (coalg y))

        let transform
            : t → r
            = Natural/fold limit (t → r) update stopgap

        in  transform seed

let hylo_max_depth
    : ∀(F : Type → Type) →
      Functor F →
      Foldable F →
      Natural →
      ∀(t : Type) →
      (t → F t) →
      t →
        Natural
    = λ(F : Type → Type) →
      λ(functorF : Functor F) →
      λ(foldableF : Foldable F) →
      λ(limit : Natural) →
      λ(t : Type) →
      λ(coalg : t → F t) →
      λ(p : t) →
        let replace
            : t → Bool
            = λ(_ : t) → True

        let findTrue
            : F Bool → Bool
            = foldableF.reduce Bool monoidBoolOr

        let Acc = { depth : Natural, hylo : t → Bool }

        let update
            : Acc → Acc
            = λ(acc : Acc) →
                let newHylo
                    : t → Bool
                    = λ(x : t) →
                        findTrue (functorF.fmap t Bool acc.hylo (coalg x))

                let hasValuesT = acc.hylo p

                in  if    hasValuesT
                    then  { depth = acc.depth + 1, hylo = newHylo }
                    else  acc

        let init
            : Acc
            = { depth = 0, hylo = replace }

        let result = Natural/fold limit Acc update init

        in  result.depth

let hylo_N
    : ∀(F : Type → Type) →
      Functor F →
      Foldable F →
      Natural →
      ∀(t : Type) →
      t →
      (t → F t) →
      ∀(r : Type) →
      (F r → r) →
      (t → r) →
        r
    = λ(F : Type → Type) →
      λ(functorF : Functor F) →
      λ(foldableF : Foldable F) →
      λ(limit : Natural) →
      λ(t : Type) →
      λ(seed : t) →
      λ(coalg : t → F t) →
      λ(r : Type) →
      λ(alg : F r → r) →
      λ(stopgap : t → r) →
        let replace
            : t → Bool
            = λ(_ : t) → True

        let findTrue
            : F Bool → Bool
            = foldableF.reduce Bool monoidBoolOr

        let Acc = { depthHylo : t → Bool, resultHylo : t → r }

        let update
            : Acc → Acc
            = λ(acc : Acc) →
                let newDepthHylo
                    : t → Bool
                    = λ(x : t) →
                        findTrue (functorF.fmap t Bool acc.depthHylo (coalg x))

                let newResultHylo
                    : t → r
                    = λ(y : t) →
                        alg (functorF.fmap t r acc.resultHylo (coalg y))

                let hasValuesT = acc.depthHylo seed

                in  if    hasValuesT
                    then  { depthHylo = newDepthHylo
                          , resultHylo = newResultHylo
                          }
                    else  acc

        let init
            : Acc
            = { depthHylo = replace, resultHylo = stopgap }

        let result = Natural/fold limit Acc update init

        in  result.resultHylo seed

in  { hylo_Nat, hylo_max_depth, hylo_N, Foldable, Monoid }
