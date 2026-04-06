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

let listNatString -- test
    : ListT
    = consT Natural (consT Text nilT)

-- let mapToListT : forall(A: Type) -> List A -> (A -> Type) -> ListT
-- = \(A: Type) -> \(la : List A) -> \(transform: A -> Type) ->
-- λ(list : Kind) →
--       λ(cons : Type → list → list) →
--       λ(nil : list) → 
--         List/fold A la list (λ(x : A) → transform x list cons nil)

-- let ListT/atIndex : Natural → ListT → Type
-- = λ(index : Natural) →
--   λ(list : ListT) → 
--   list { i : Natural, t : Type } (λ(x : { i : Natural, t : Type }) → λ(y : list) → x)   { i = 0, t = Type }) → x)

in  { consT, nilT }
