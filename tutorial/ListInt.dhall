let FmapT = λ(F : Type → Type) → ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b

let LFix
    : (Type → Type) → Type
    = λ(F : Type → Type) → ∀(r : Type) → (F r → r) → r

let Functor = λ(F : Type → Type) → { fmap : FmapT F }

let Integer/add =
      https://prelude.dhall-lang.org/Integer/add
        sha256:7da1306a0bf87c5668beead2a1db1b18861e53d7ce1f38057b2964b649f59c3b

let fix
    : ∀(F : Type → Type) → Functor F → F (LFix F) → LFix F
    = λ(F : Type → Type) →
      λ(functorF : Functor F) →
        let C = LFix F

        in  λ(fc : F C) →
            λ(r : Type) →
            λ(frr : F r → r) →
              let c2r
                  : C → r
                  = λ(c : C) → c r frr

              let fmap_c2r
                  : F C → F r
                  = functorF.fmap C r c2r

              let fr
                  : F r
                  = fmap_c2r fc

              in  frr fr

let unfix
    : ∀(F : Type → Type) → Functor F → LFix F → F (LFix F)
    = λ(F : Type → Type) →
      λ(functorF : Functor F) →
        let C = LFix F

        let fmap_fix
            : F (F C) → F C
            = functorF.fmap (F C) C (fix F functorF)

        in  λ(c : C) → c (F C) fmap_fix

let F = λ(r : Type) → < Nil | Cons : { head : Integer, tail : r } >

let ListInt = LFix F

let functorF
    : Functor F
    = { fmap =
          λ(a : Type) →
          λ(b : Type) →
          λ(f : a → b) →
          λ(fa : F a) →
            merge
              { Nil = (F b).Nil
              , Cons =
                  λ(pair : { head : Integer, tail : a }) →
                    (F b).Cons (pair ⫽ { tail = f pair.tail })
              }
              fa
      }

let cons
    : Integer → ListInt → ListInt
    = λ(h : Integer) →
      λ(t : ListInt) →
        fix F functorF ((F ListInt).Cons { head = h, tail = t })

let nil
    : ListInt
    = fix F functorF (F ListInt).Nil

let headOptional
    : ListInt → Optional Integer
    = λ(c : ListInt) →
        merge
          { Cons = λ(list : { head : Integer, tail : ListInt }) → Some list.head
          , Nil = None Integer
          }
          (unfix F functorF c)

let tailOptional
    : ListInt → Optional ListInt
    = λ(c : ListInt) →
        merge
          { Cons = λ(list : { head : Integer, tail : ListInt }) → Some list.tail
          , Nil = None ListInt
          }
          (unfix F functorF c)

let _ = assert : headOptional (cons -456 (cons +123 nil)) ≡ Some -456

let _ = assert : tailOptional (cons -456 (cons +123 nil)) ≡ Some (cons +123 nil)

let ListInt/sum
    : ListInt → Integer
    = λ(list : ListInt) →
        list
          Integer
          ( λ(p : F Integer) →
              merge
                { Nil = +0
                , Cons =
                    λ(q : { head : Integer, tail : Integer }) →
                      Integer/add q.head q.tail
                }
                p
          )

let _ = assert : ListInt/sum (cons +1 (cons +2 nil)) ≡ +3

let ListInt/concat
    : ListInt → ListInt → ListInt
    = λ(left : ListInt) →
      λ(right : ListInt) →
        left
          ListInt
          ( λ(p : F ListInt) →
              merge
                { Nil = right
                , Cons =
                    λ(q : { head : Integer, tail : ListInt }) →
                      cons q.head q.tail
                }
                p
          )

let _ =
        assert
      :   ListInt/concat (cons +1 (cons +2 nil)) (cons +3 nil)
        ≡ cons +1 (cons +2 (cons +3 nil))

in  { ListInt
    , ListInt/sum
    , ListInt/concat
    , cons
    , headOptional
    , fix
    , Functor
    , unfix
    , LFix
    , nil
    }
