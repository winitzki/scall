-- Questions:
--   Can we use TList freely, or its "Sort" typing will show up somewhere? Can't export a record containing TList!
--   Can we implement more utility functions for TList? Yes.
--   Can we implement value-level HList whose type is TList and check the type? Doing this in HList.dhall.
--   Can we use LeibnizEqualT to assert that someText ===  TOpt.`Some` Text in some way?
let TList = ∀(list : Kind) → ∀(cons : Type → list → list) → ∀(nil : list) → list

let _ = TList : Sort

let example
    : TList
    = λ(list : Kind) →
      λ(cons : Type → list → list) →
      λ(nil : list) →
        cons Text (cons Bool (cons Natural nil))

let TOpt = < None | Some : Type >

let THeadOpt
    : TList → TOpt
    = λ(tl : TList) →
        let cons = λ(t : Type) → λ(_ : TOpt) → TOpt.`Some` t

        let nil = TOpt.None

        in  tl TOpt cons nil

let someText = THeadOpt example

let shouldBeTextType = merge { None = {}, Some = λ(t : Type) → t } someText

let _ = "abc" : shouldBeTextType

let nilT
    : TList
    = λ(list : Kind) → λ(cons : Type → list → list) → λ(nil : list) → nil

let consT
    : Type → TList → TList
    = λ(head : Type) →
      λ(tail : TList) →
      λ(list : Kind) →
      λ(cons : Type → list → list) →
      λ(nil : list) →
        cons head (tail list cons nil)

let listNatString
    : TList
    = consT Natural (consT Text nilT)

in  TList
