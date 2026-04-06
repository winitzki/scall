let LFixModule =
      ./LFix.dhall
        sha256:7e6e71b0cefa3a6a6b3aa3bf4af44ee939a391474d3f7d29cd67bab0fdc5872a

let LFix = LFixModule.LFix

let fix = LFixModule.fix

let unfix = LFixModule.unfix

let Functor =
      ./Functor.dhall
        sha256:bb981c881a1ca4619412614558ac0618607cd16367d0964b5bc259fac63bf454

let LFixYoneda = λ(F : Type → Type) → ∀(r : Type) → (F r → r) → F r

let fromLFixYoneda
    : ∀(F : Type → Type) → LFixYoneda F → LFix F
    = λ(F : Type → Type) →
      λ(lfixYoneda : LFixYoneda F) →
      λ(r : Type) →
      λ(frr : F r → r) →
        frr (lfixYoneda r frr)

let toLFixYoneda
    : ∀(F : Type → Type) → Functor F → LFix F → LFixYoneda F
    = λ(F : Type → Type) →
      λ(functorF : Functor F) →
      λ(lfix : LFix F) →
      λ(r : Type) →
      λ(frr : F r → r) →
        lfix (F r) (functorF.fmap (F r) r frr)

let fixY
    : ∀(F : Type → Type) → Functor F → F (LFixYoneda F) → LFixYoneda F
    = λ(F : Type → Type) →
      λ(functorF : Functor F) →
      λ(f : F (LFixYoneda F)) →
        toLFixYoneda
          F
          functorF
          ( fix
              F
              functorF
              (functorF.fmap (LFixYoneda F) (LFix F) (fromLFixYoneda F) f)
          )

in  { LFixYoneda, fromLFixYoneda, toLFixYoneda, fixY }
