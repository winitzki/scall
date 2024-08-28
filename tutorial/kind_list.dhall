let TList = ∀(list : Kind) → ∀(cons : Type → list → list) → ∀(nil : list) → list

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

-- let _ = assert : someText ===  TOpt.`Some` Text  -- Cannot do that.

let shouldBeTextType = merge { None = {}, Some = λ(t : Type) → t } someText

in  "abc" : shouldBeTextType


-- Questions:
--   Can we use TList freely, or its "Sort" typing will show up somewhere?
--   Can we implement more utility functions for TList?
--   Can we implement value-level HList whose type is TList and check the type?
--   Can we use LeibnizEqualT to assert that someText ===  TOpt.`Some` Text in some way?
