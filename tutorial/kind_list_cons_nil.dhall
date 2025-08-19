let ListT = ./kind_list.dhall

let _ = ListT : Sort

let nilT
    : ListT
    = λ(list : Kind) → λ(cons : Type → list → list) → λ(nil : list) → nil

let consT
    : Type → ListT → ListT
    = λ(head : Type) →
      λ(tail : ListT) →
      λ(list : Kind) →
      λ(cons : Type → list → list) →
      λ(nil : list) →
        cons head (tail list cons nil)

let listNatString
    : ListT
    = consT Natural (consT Text nilT)

let mapListT : forall(A: Type) -> List A -> (A -> Type) -> ListT
= \(A: Type) -> \(as: List A) -> \(transform: A -> Type) ->
λ(list : Kind) →
      λ(cons : Type → list → list) →
      λ(nil : list) →

in  { consT, nilT }
