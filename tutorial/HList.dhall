{-
Implement a heterogeneous list indexed by a list of types.

Idris code:

data HList : List Type -> Type  where
  HNil : HList []
  HCons : t -> HList ts -> HList (t :: ts)

item : HList [Nat, String]
item = [ 12, "hi" ]

-}
let ListT = ./kind_list.dhall

let cons_nil_ListT = ./kind_list_cons_nil.dhall

let consT
    : Type → ListT → ListT
    = cons_nil_ListT.consT

let nilT
    : ListT
    = cons_nil_ListT.nilT

let HList
    : ListT → Type
    = λ(listT : ListT) →
        ∀(r : ListT → Type) →
        ∀(hnil : r nilT) →
        ∀(hcons : ∀(t : Type) → t → ∀(ts : ListT) → r ts → r (consT t ts)) →
          r listT

let nilH
    : HList nilT
    = λ(r : ListT → Type) →
      λ(hnil : r nilT) →
      λ(hcons : ∀(t : Type) → t → ∀(ts : ListT) → r ts → r (consT t ts)) →
        hnil

let consH =
      λ(headT : Type) →
      λ(head : headT) →
      λ(tailT : ListT) →
      λ(tail : HList tailT) →
      λ(r : ListT → Type) →
      λ(hnil : r nilT) →
      λ(hcons : ∀(t : Type) → t → ∀(ts : ListT) → r ts → r (consT t ts)) →
        hcons headT head tailT (tail r hnil hcons)

let listNatString
    : ListT
    = consT Natural (consT Text nilT)

let listString
    : ListT
    = consT Text nilT

let item
    : HList listNatString
    = consH Natural 12 listString (consH Text "hi" nilT nilH)

in  { ListH = HList, nilH, consH }
