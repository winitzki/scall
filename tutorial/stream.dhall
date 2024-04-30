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
                (F x b).Cons { head = cons.head, tail = f cons.tail }
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

let HeadTailT = λ(a : Type) → < Cons : { head : a, tail : List a } | Nil >

let headTail
    : ∀(a : Type) → List a → HeadTailT a
    = λ(a : Type) →
      λ(list : List a) →
        let getTail = https://prelude.dhall-lang.org/List/drop 1 a

        in  merge
              { None = (HeadTailT a).Nil
              , Some =
                  λ(h : a) →
                    (HeadTailT a).Cons { head = h, tail = getTail list }
              }
              (List/head a list)

let listToStream
    : ∀(a : Type) → List a → Stream a
    = λ(a : Type) → λ(list : List a) → makeStream a (List a) list (headTail a)

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

let _ =
        assert
      :   streamToList
            Natural
            (streamFunction Natural 1 (λ(x : Natural) → x * 2))
            5
        ≡ [ 1, 2, 4, 8, 16 ]

let repeatForever
    : ∀(a : Type) → List a → Stream a
    = λ(a : Type) →
      λ(list : List a) →
        let getTail = https://prelude.dhall-lang.org/List/drop 1 a

        let mkStream =
              λ(h : { head : a, tail : List a }) →
                let step
                    : List a → HeadTailT a
                    = λ(prev : List a) →
                        merge
                          { None =
                              (HeadTailT a).Cons
                                { head = h.head, tail = h.tail }
                          , Some =
                              λ(x : a) →
                                (HeadTailT a).Cons
                                  { head = x, tail = getTail prev }
                          }
                          (List/head a prev)

                in  makeStream a (List a) list step

        in  merge
              { Nil = nil a
              , Cons = λ(h : { head : a, tail : List a }) → mkStream h
              }
              (headTail a list)

let _ =
        assert
      :   streamToList Natural (repeatForever Natural [ 1, 2, 3 ]) 7
        ≡ [ 1, 2, 3, 1, 2, 3, 1 ]

let Stream/concat
    : ∀(a : Type) → Stream a → Stream a → Stream a
    = λ(a : Type) →
      λ(first : Stream a) →
      λ(second : Stream a) →
        let State = < InFirst : Stream a | InSecond : Stream a >

        let StepT = < Cons : { head : a, tail : State } | Nil >

        let stepSecond =
              λ(str : Stream a) →
                merge
                  { None = StepT.Nil
                  , Some =
                      λ(ht : { head : a, tail : Stream a }) →
                        StepT.Cons
                          { head = ht.head, tail = State.InSecond ht.tail }
                  }
                  (headTailOption a str)

        let step
            : State → StepT
            = λ(state : State) →
                merge
                  { InFirst =
                      λ(str : Stream a) →
                        merge
                          { None = stepSecond second
                          , Some =
                              λ(ht : { head : a, tail : Stream a }) →
                                StepT.Cons
                                  { head = ht.head
                                  , tail = State.InFirst ht.tail
                                  }
                          }
                          (headTailOption a str)
                  , InSecond = stepSecond
                  }
                  state

        in  makeStream a State (State.InFirst first) step

let _ =
        assert
      :   streamToList
            Text
            ( Stream/concat
                Text
                (listToStream Text example0)
                (listToStream Text example1)
            )
            10
        ≡ [ "a", "b", "c", "d", "a", "b", "c" ]

let _ =
        assert
      :   streamToList
            Natural
            ( Stream/concat
                Natural
                (repeatForever Natural [ 1, 2, 3 ])
                (listToStream Natural [ 10, 20 ])
            )
            6
        ≡ [ 1, 2, 3, 1, 2, 3 ]

let Stream/truncate
    : ∀(a : Type) → Stream a → Natural → Stream a
    = λ(a : Type) →
      λ(stream : Stream a) →
      λ(n : Natural) →
        let State = { remaining : Natural, stream : Stream a }

        let StepT = < Nil | Cons : { head : a, tail : State } >

        let step
            : State → StepT
            = λ(state : State) →
                if    Natural/isZero state.remaining
                then  StepT.Nil
                else  merge
                        { None = StepT.Nil
                        , Some =
                            λ(ht : { head : a, tail : Stream a }) →
                              StepT.Cons
                                { head = ht.head
                                , tail =
                                  { remaining =
                                      Natural/subtract 1 state.remaining
                                  , stream = ht.tail
                                  }
                                }
                        }
                        (headTailOption a state.stream)

        in  makeStream a State { remaining = n, stream } step

let _ =
        assert
      :   streamToList
            Natural
            (Stream/truncate Natural (repeatForever Natural [ 1, 2, 3 ]) 5)
            6
        ≡ [ 1, 2, 3, 1, 2 ]

let Stream/scan =
      λ(a : Type) →
      λ(sa : Stream a) →
      λ(b : Type) →
      λ(init : b) →
      λ(update : a → b → b) →
        let State = { source : Stream a, current : b }

        let initState
            : State
            = { source = sa, current = init }

        let ResultT = < Cons : { head : b, tail : State } | Nil >

        let step
            : State → ResultT
            = λ(s : State) →
                merge
                  { None = ResultT.Nil
                  , Some =
                      λ(headTail : { head : a, tail : Stream a }) →
                        let newCurrent = update headTail.head s.current

                        in  ResultT.Cons
                              { head = newCurrent
                              , tail =
                                { source = headTail.tail, current = newCurrent }
                              }
                  }
                  (headTailOption a s.source)

        in  makeStream b State initState step

let runningSum
    : Stream Natural → Stream Natural
    = λ(sn : Stream Natural) →
        Stream/scan
          Natural
          sn
          Natural
          0
          (λ(x : Natural) → λ(sum : Natural) → x + sum)

let Monoid = λ(m : Type) → { empty : m, append : m → m → m }

let Stream/scanMap
    : ∀(m : Type) → Monoid m → ∀(a : Type) → (a → m) → Stream a → Stream m
    = λ(m : Type) →
      λ(monoidM : Monoid m) →
      λ(a : Type) →
      λ(map : a → m) →
      λ(sa : Stream a) →
        Stream/scan
          a
          sa
          m
          monoidM.empty
          (λ(x : a) → λ(y : m) → monoidM.append (map x) y)

let _ =
        assert
      :   streamToList
            Natural
            (runningSum (repeatForever Natural [ 1, 2, 3 ]))
            7
        ≡ [ 1, 3, 6, 7, 9, 12, 13 ]

let _ =
        assert
      :   streamToList
            Natural
            (runningSum (listToStream Natural ([] : List Natural)))
            7
        ≡ ([] : List Natural)

let identity
    : ∀(A : Type) → ∀(x : A) → A
    = λ(A : Type) → λ(x : A) → x

let Contrafunctor =
      λ(F : Type → Type) →
        { cmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F b → F a }

let Bifunctor
    : (Type → Type → Type) → Type
    = λ(F : Type → Type → Type) →
        { bimap :
            ∀(a : Type) →
            ∀(b : Type) →
            ∀(c : Type) →
            ∀(d : Type) →
            (a → c) →
            (b → d) →
            F a b →
              F c d
        }

let Fmap_t =
      λ(F : Type → Type) → ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b

let Functor = λ(F : Type → Type) → { fmap : Fmap_t F }

let Profunctor
    : (Type → Type → Type) → Type
    = λ(F : Type → Type → Type) →
        { xmap :
            ∀(a : Type) →
            ∀(b : Type) →
            ∀(c : Type) →
            ∀(d : Type) →
            (c → a) →
            (b → d) →
            F a b →
              F c d
        }

let HT = λ(h : Type) → λ(t : Type) → < Cons : { head : h, tail : t } | Nil >

let bifunctorHT
    : Bifunctor HT
    = { bimap =
          λ(a : Type) →
          λ(b : Type) →
          λ(c : Type) →
          λ(d : Type) →
          λ(f : a → c) →
          λ(g : b → d) →
          λ(pab : HT a b) →
            merge
              { Cons =
                  λ(ht : { head : a, tail : b }) →
                    (HT c d).Cons { head = f ht.head, tail = g ht.tail }
              , Nil = (HT c d).Nil
              }
              pab
      }

let Pack_t =
      λ(r : Type) →
      λ(h : Type) →
        ∀(t : Type) → { seed : t, step : t → HT h t } → r

let contrafunctor_Pack_t
    : ∀(r : Type) → Contrafunctor (Pack_t r)
    = λ(r : Type) →
        { cmap =
            λ(a : Type) →
            λ(b : Type) →
            λ(f : a → b) →
            λ(pb : Pack_t r b) →
            λ(t : Type) →
            λ(state : { seed : t, step : t → HT a t }) →
              pb
                t
                { seed = state.seed
                , step =
                    λ(x : t) →
                      bifunctorHT.bimap a t b t f (identity t) (state.step x)
                }
        }

let Stream/map
    : ∀(a : Type) → ∀(b : Type) → (a → b) → Stream a → Stream b
    = λ(a : Type) →
      λ(b : Type) →
      λ(f : a → b) →
      λ(sa : Stream a) →
      λ(r : Type) →
      λ(pack_b : ∀(t : Type) → { seed : t, step : t → HT b t } → r) →
        let pack_a
            : Pack_t r a
            = (contrafunctor_Pack_t r).cmap a b f pack_b

        in  sa r pack_a

let functorStream
    : Functor Stream
    = { fmap = Stream/map }

let _ =
        assert
      :   streamToList
            Natural
            ( Stream/map
                Natural
                Natural
                (λ(x : Natural) → x * 10)
                (listToStream Natural [ 1, 2, 3 ])
            )
            5
        ≡ [ 10, 20, 30 ]


let runningList : ∀(a : Type) → Stream a → Stream (List a)
  = λ(a : Type) → λ(sa : Stream a) → 
  Stream/scan a sa (List a) ([] : List a) (λ(x : a) → λ(current : List a) → current # [ x ] )

let ex1 : Stream ( List Natural ) = runningList Natural (repeatForever Natural [ 1, 2, 3 ])
let _ = assert : streamToList (List Natural) ex1 5
        ≡ [ [ 1 ], [1, 2], [1, 2, 3], [ 1, 2, 3, 1], [ 1, 2, 3, 1, 2] ]

in  True
