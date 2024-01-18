let Ch = ./Church.dhall

let ChurchNaturals = ./ChurchNaturals.dhall

let ChurchNatural = ChurchNaturals.ChurchNatural

let Natural/max = https://prelude.dhall-lang.org/Natural/max

let Hylo = ./ChurchHylo.dhall

let B = ./Bifunctor.dhall

let Either = B.Either

let mkPair = B.mkPair

let Pair = B.Pair

let zip0 =
      λ(S : B.Bifunctor) →
        let C = Ch.T1 S

        in  λ(bizip0 : B.Zip0 S) →
            λ(a : Type) →
            λ(b : Type) →
            λ(ca : C a) →
            λ(cb : C b) →
              let result
                  : C (Pair a b)
                  = λ(r : Type) →
                    λ(sabrr : S (Pair a b) r → r) →
                      ca
                        r
                        ( λ(sar : S a r) →
                            cb r (λ(sbr : S b r) → sabrr (bizip0 a b r sar sbr))
                        )

              in  result

let zipCoalg
    : ∀(S : B.Bifunctor) →
      B.Bimap S →
        let C = Ch.T1 S

        in  B.BizipK S C →
            ∀(a : Type) →
            ∀(b : Type) →
            Pair (C a) (C b) →
              S (Pair a b) (Pair (C a) (C b))
    = λ(S : B.Bifunctor) →
      λ(bimapS : B.Bimap S) →
        let C = Ch.T1 S

        in  λ(bizipKS : B.BizipK S C) →
            λ(a : Type) →
            λ(b : Type) →
            λ(cacb : Pair (C a) (C b)) →
              let sca
                  : S a (C a)
                  = Ch.unfixT1 S bimapS a cacb._1

              let scb
                  : S b (C b)
                  = Ch.unfixT1 S bimapS b cacb._2

              in  bizipKS a b sca scb

let zip1
    : ∀(S : B.Bifunctor) →
        let C = Ch.T1 S

        in  B.Bimap S →
            B.BizipK S C →
            B.Depth S →
            ∀(a : Type) →
            ∀(b : Type) →
            C a →
            C b →
            Either a b →
              C (Pair a b)
    = λ(S : B.Bifunctor) →
        let C = Ch.T1 S

        in  λ(bimapS : B.Bimap S) →
            λ(bizipKS : B.BizipK S C) →
            λ(depth : B.Depth S) →
            λ(a : Type) →
            λ(b : Type) →
            λ(ca : C a) →
            λ(cb : C b) →
            λ(eab : Either a b) →
              let max_depth
                  : ChurchNatural
                  = ChurchNaturals.NaturalToChurch
                      ( Natural/max
                          (ca Natural (depth a))
                          (cb Natural (depth b))
                      )

              let zipCo
                  : Pair (C a) (C b) → S (Pair a b) (Pair (C a) (C b))
                  = zipCoalg S bimapS bizipKS a b

              let alg
                  : S (Pair a b) (C (Pair a b)) → C (Pair a b)
                  = Ch.fixT1 S bimapS (Pair a b)

              let default
                  : C (Pair a b)
                  = merge
                      { Left =
                          λ(x : a) →
                            Ch.mapT1
                              S
                              bimapS
                              b
                              (Pair a b)
                              (λ(y : b) → mkPair a x b y)
                              cb
                      , Right =
                          λ(y : b) →
                            Ch.mapT1
                              S
                              bimapS
                              a
                              (Pair a b)
                              (λ(x : a) → mkPair a x b y)
                              ca
                      }
                      eab

              in  Hylo.Hylo1Int
                    S
                    bimapS
                    max_depth
                    (Pair a b)
                    (Pair (C a) (C b))
                    zipCo
                    (mkPair (C a) ca (C b) cb)
                    (C (Pair a b))
                    alg
                    default

let zip2 =
      λ(S : B.Bifunctor) →
        let C = Ch.T1 S

        in  λ(bimapS : B.Bimap S) →
            λ(bizipS : B.Bizip S) →
            λ(bizipKS : B.BizipK S C) →
            λ(limit : C {}) →
            λ(a : Type) →
            λ(b : Type) →
            λ(ca : C a) →
            λ(cb : C b) →
              let zipCo
                  : Pair (C a) (C b) → S (Pair a b) (Pair (C a) (C b))
                  = zipCoalg S bimapS bizipKS a b

              in    Hylo.Hylo1Ch
                      S
                      bimapS
                      bizipS
                      limit
                      (Pair a b)
                      (Pair (C a) (C b))
                      zipCo
                      (mkPair (C a) ca (C b) cb)
                  : C (Pair a b)

in  { zip1, zipCoalg, zip0, zip2 }
