let Exists
    : (Type → Type) → Type
    = λ(P : Type → Type) → ∀(r : Type) → (∀(t : Type) → P t → r) → r

let GF_T = λ(F : Type → Type) → λ(r : Type) → { seed : r, step : r → F r }

let GFix = λ(F : Type → Type) → Exists (GF_T F)

let pack
    : ∀(P : Type → Type) → ∀(t : Type) → P t → Exists P
    = λ(P : Type → Type) →
      λ(t : Type) →
      λ(pt : P t) →
      λ(res : Type) →
      λ(pack_ : ∀(t_ : Type) → P t_ → res) →
        pack_ t pt

let unpack
    : ∀(P : Type → Type) → Exists P → ∀(r : Type) → (∀(t : Type) → P t → r) → r
    = λ(P : Type → Type) →
      λ(ep : Exists P) →
      λ(r : Type) →
      λ(unpack_ : ∀(t : Type) → P t → r) →
        ep r unpack_

let F = λ(a : Type) → λ(r : Type) → < Nil | Cons : { head : a, tail : r } >

let fmap_F
    : ∀(x : Type) → ∀(a : Type) → ∀(b : Type) → (a → b) → F x a → F x b
    = λ(x : Type) →
      λ(a : Type) →
      λ(b : Type) →
      λ(f : a → b) →
      λ(fa : F x a) →
        merge
          { Nil = (F x b).Nil
          , Cons =
              λ(cons : { head : x, tail : a }) →
                (F x b).Cons (cons ⫽ { tail = f cons.tail })
          }
          fa

let unfix
    : ∀(F : Type → Type) →
      ∀(fmap_F : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b) →
      GFix F →
        F (GFix F)
    = λ(F : Type → Type) →
      λ(fmap_F : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b) →
      λ ( g
        : ∀(r : Type) → (∀(t : Type) → { seed : t, step : t → F t } → r) → r
        ) →
        let f
            : ∀(t : Type) → { seed : t, step : t → F t } → F (GFix F)
            = λ(t : Type) →
              λ(p : { seed : t, step : t → F t }) →
                let k
                    : t → GFix F
                    = λ(x : t) → pack (GF_T F) t p

                let fk
                    : F t → F (GFix F)
                    = fmap_F t (GFix F) k

                in  fk (p.step p.seed)

        in  g (F (GFix F)) f

let fix
    : ∀(F : Type → Type) →
      ∀(fmap_F : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b) →
      F (GFix F) →
        GFix F
    = λ(F : Type → Type) →
      λ(fmap_F : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b) →
      λ(fg : F (GFix F)) →
        let fmap_unfix
            : F (GFix F) → F (F (GFix F))
            = fmap_F (GFix F) (F (GFix F)) (unfix F fmap_F)

        in  pack (GF_T F) (F (GFix F)) { seed = fg, step = fmap_unfix }

let makeGFix =
      λ(F : Type → Type) →
      λ(r : Type) →
      λ(x : r) →
      λ(rfr : r → F r) →
        pack (GF_T F) r { seed = x, step = rfr }

let Stream = λ(a : Type) → GFix (F a)

let makeStream = λ(a : Type) → makeGFix (F a)

let headTailOption
    : ∀(a : Type) → Stream a → Optional { head : a, tail : Stream a }
    = λ(a : Type) →
      λ(s : Stream a) →
        let headTail = λ(h : Type) → λ(t : Type) → { head : h, tail : t }

        let ResultT = headTail a (Stream a)

        let unpack_ =
              λ(t : Type) →
              λ ( state
                : { seed : t, step : t → < Cons : headTail a t | Nil > }
                ) →
                merge
                  { Cons =
                      λ(cons : headTail a t) →
                        Some
                          { head = cons.head
                          , tail = makeStream a t cons.tail state.step
                          }
                  , Nil = None ResultT
                  }
                  (state.step state.seed)

        in  s (Optional ResultT) unpack_

let nil
    : ∀(a : Type) → Stream a
    = λ(a : Type) →
        let r = {}

        let seed
            : r
            = {=}

        in  makeStream a r seed (λ(_ : r) → (F a r).Nil)

let _ =
        assert
      :   headTailOption Text (nil Text)
        ≡ None { head : Text, tail : Stream Text }

let streamToList
    : ∀(a : Type) → Stream a → Natural → List a
    = λ(a : Type) →
      λ(s : Stream a) →
      λ(limit : Natural) →
        let Accum = { list : List a, stream : Optional (Stream a) }

        let init
            : Accum
            = { list = [] : List a, stream = Some s }

        let update
            : Accum → Accum
            = λ(prev : Accum) →
                let headTail
                    : Optional { head : a, tail : Stream a }
                    = merge
                        { None = None { head : a, tail : Stream a }
                        , Some = λ(str : Stream a) → headTailOption a str
                        }
                        prev.stream

                in  merge
                      { None = prev ⫽ { stream = None (Stream a) }
                      , Some =
                          λ(ht : { head : a, tail : Stream a }) →
                            { list = prev.list # [ ht.head ]
                            , stream = Some ht.tail
                            }
                      }
                      headTail

        in  (Natural/fold limit Accum update init).list

let listToStream
    : ∀(a : Type) → List a → Stream a
    = λ(a : Type) →
      λ(list : List a) →
        let getTail = https://prelude.dhall-lang.org/List/drop 1 a

        let FA = < Cons : { head : a, tail : List a } | Nil >

        let step
            : List a → FA
            = λ(prev : List a) →
                merge
                  { None = FA.Nil
                  , Some = λ(h : a) → FA.Cons { head = h, tail = getTail prev }
                  }
                  (List/head a prev)

        in  makeStream a (List a) list step

let example0 = streamToList Text (listToStream Text [ "a", "b", "c", "d" ]) 5

let _ = assert : example0 ≡ [ "a", "b", "c", "d" ]

let example1 = streamToList Text (listToStream Text [ "a", "b", "c", "d" ]) 3

let example1a = streamToList Text (listToStream Text [ "a", "b", "c", "d" ]) 4

let _ = assert : example1 ≡ [ "a", "b", "c" ]

let _ = assert : example1a ≡ [ "a", "b", "c", "d" ]

let streamFunction
    : ∀(a : Type) → ∀(seed : a) → ∀(f : a → a) → Stream a
    = λ(a : Type) →
      λ(seed : a) →
      λ(f : a → a) →
        let FA = < Cons : { head : a, tail : a } | Nil >

        let step
            : a → FA
            = λ(x : a) → FA.Cons { head = x, tail = f x }

        in  makeStream a a seed step

let example2 =
      streamToList Natural (streamFunction Natural 0 (λ(x : Natural) → x + 1)) 4

let _ = assert : example2 ≡ [ 0, 1, 2, 3 ]

in  True
