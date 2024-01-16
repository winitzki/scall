let Ch = ./Church.dhall

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

let Pair = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }

let mkPair =
      λ(a : Type) →
      λ(x : a) →
      λ(b : Type) →
      λ(y : b) →
        { _1 = x, _2 = y } : Pair a b

let Hylo1Int =
      λ(S : Type → Type → Type) →
      λ ( fmapS2
        : ∀(c : Type) → ∀(a : Type) → ∀(b : Type) → ∀(f : a → b) → S c a → S c b
        ) →
      λ(limit : ChurchNatural) →
      λ(a : Type) →
      λ(p : Type) →
      λ(coalg : p → S a p) →
      λ(r : Type) →
      λ(alg : S a r → r) →
      λ(default : r) →
      λ(x : p) →
        let result
            : r
            = let loop
                  : (p → r) → p → r
                  = λ(f : p → r) → λ(x : p) → alg (fmapS2 a p r f (coalg x))

              in  limit (p → r) (λ(_ : p) → default) loop x

        in  result

let Hylo1Ch
    : ∀(S : Type → Type → Type) →
      ∀ ( bimapS
        : ∀(a : Type) →
          ∀(b : Type) →
          ∀(c : Type) →
          ∀(d : Type) →
          ∀(f : a → c) →
          ∀(g : b → d) →
          S a b →
            S c d
        ) →
      ∀ ( bizipS
        : ∀(a : Type) →
          ∀(b : Type) →
          ∀(p : Type) →
          ∀(q : Type) →
          S a p →
          S b q →
            S (Pair a b) (Pair p q)
        ) →
      Ch.T1 S {} →
      ∀(a : Type) →
      ∀(p : Type) →
      (p → S a p) →
      p →
        Ch.T1 S a
    = λ(S : Type → Type → Type) →
      λ ( bimapS
        : ∀(a : Type) →
          ∀(b : Type) →
          ∀(c : Type) →
          ∀(d : Type) →
          ∀(f : a → c) →
          ∀(g : b → d) →
          S a b →
            S c d
        ) →
      λ ( bizipS
        : ∀(a : Type) →
          ∀(b : Type) →
          ∀(p : Type) →
          ∀(q : Type) →
          S a p →
          S b q →
            S (Pair a b) (Pair p q)
        ) →
      λ(limit : Ch.T1 S {}) →
      λ(a : Type) →
      λ(p : Type) →
      λ(coalg : p → S a p) →
        let Ca = Ch.T1 S a

        let result
            : p → Ca
            = let forgetfulZip
                  : S a p → S {} (p → Ca) → S a Ca
                  = λ(sap : S a p) →
                    λ(spca : S {} (p → Ca)) →
                      let q1
                          : S (Pair a {}) (Pair p (p → Ca))
                          = bizipS a {} p (p → Ca) sap spca

                      let q2
                          : S a Ca
                          = bimapS
                              (Pair a {})
                              (Pair p (p → Ca))
                              a
                              Ca
                              (λ(pair : { _1 : a, _2 : {} }) → pair._1)
                              ( λ(pair : { _1 : p, _2 : p → Ca }) →
                                  pair._2 pair._1
                              )
                              q1

                      in  q2

              let fix
                  : S {} (p → Ca) → p → Ca
                  = λ(spca : S {} (p → Ca)) →
                    λ(x : p) →
                      let saca
                          : S a Ca
                          = forgetfulZip (coalg x) spca

                      let xc
                          : Ca
                          = Ch.fixT1 S bimapS a saca

                      in  xc

              in  limit (p → Ca) fix

        in  result

in  { HyloInt, Hylo1Int, Hylo1Ch }
