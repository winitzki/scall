let Ch = (./Church.dhall).T0

let ChurchNatural = (./ChurchNaturals.dhall).ChurchNatural

let HyloInt =
      λ(S : Type → Type) →
      λ(fmapS : ∀(a : Type) → ∀(b : Type) → ∀(f : a → b) → S a → S b) →
      λ(limit : ChurchNatural) →
      λ(p : Type) →
      λ(coalg : p → S p) →
      λ(r : Type) →
      λ(alg : S r → r) →
      λ(default : r) →
      λ(x : p) →
        let result
            : r
            = let loop
                  : (p → r) → p → r
                  = λ(f : p → r) → λ(x : p) → alg (fmapS p r f (coalg x))

              in  limit (p → r) (λ(_ : p) → default) loop x

        in  result

in  { HyloInt }
