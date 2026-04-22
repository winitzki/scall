let LFixModule =
      ./LFix.dhall
        sha256:7e6e71b0cefa3a6a6b3aa3bf4af44ee939a391474d3f7d29cd67bab0fdc5872a

let LFix = LFixModule.LFix

let fix = LFixModule.fix

let unfix = LFixModule.unfix

let Functor =
      ./Functor.dhall
        sha256:bb981c881a1ca4619412614558ac0618607cd16367d0964b5bc259fac63bf454

let LFixYonedaModule =
      ./LFixYoneda.dhall
        sha256:7ab2bf260aaaa5d1871d9eddb72b288a4f488fbbfc4114d4fcc8e420af7d6d21

let ListNatF = λ(r : Type) → < Nil | Cons : { head : Natural, tail : r } >

let functorListNatF
    : Functor ListNatF
    = { fmap =
          λ(a : Type) →
          λ(b : Type) →
          λ(f : a → b) →
          λ(lf : ListNatF a) →
            merge
              { Nil = (ListNatF b).Nil
              , Cons =
                  λ(pair : { head : Natural, tail : a }) →
                    (ListNatF b).Cons { head = pair.head, tail = f pair.tail }
              }
              lf
      }

let ListNat = LFix ListNatF

let ListNat/nil
    : ListNat
    = λ(r : Type) → λ(frr : ListNatF r → r) → frr (ListNatF r).Nil

let ListNat/cons
    : Natural → ListNat → ListNat
    = λ(head : Natural) →
      λ(tail : ListNat) →
      λ(r : Type) →
      λ(frr : ListNatF r → r) →
        frr ((ListNatF r).Cons { head, tail = tail r frr })

let ListNat/toList
    : ListNat → List Natural
    = λ(listNat : ListNat) →
        listNat
          (List Natural)
          ( λ(lf : ListNatF (List Natural)) →
              merge
                { Nil = [] : List Natural
                , Cons =
                    λ(pair : { head : Natural, tail : List Natural }) →
                      [ pair.head ] # pair.tail
                }
                lf
          )

let ListNat/fromList
    : List Natural → ListNat
    = λ(list : List Natural) →
        List/fold Natural list ListNat ListNat/cons ListNat/nil

let _ =
        assert
      :   ListNat/toList (ListNat/fromList ([] : List Natural))
        ≡ ([] : List Natural)

let _ = assert : ListNat/toList (ListNat/fromList [ 1, 2, 3 ]) ≡ [ 1, 2, 3 ]

let ListNat/nonEmpty
    : ListNat → Bool
    = λ(listNat : ListNat) →
        listNat
          Bool
          ( λ(lf : ListNatF Bool) →
              merge
                { Nil = False
                , Cons = λ(_ : { head : Natural, tail : Bool }) → True
                }
                lf
          )

let _ = assert : ListNat/nonEmpty (ListNat/fromList [ 1, 2, 3 ]) ≡ True

let _ = assert : ListNat/nonEmpty (ListNat/fromList ([] : List Natural)) ≡ False

let ListNat/headOptional
    : ListNat → Optional Natural
    = λ(listNat : ListNat) →
        listNat
          (Optional Natural)
          ( λ(lf : ListNatF (Optional Natural)) →
              merge
                { Nil = None Natural
                , Cons =
                    λ(pair : { head : Natural, tail : Optional Natural }) →
                      Some pair.head
                }
                lf
          )

let _ = assert : ListNat/headOptional (ListNat/fromList [ 1, 2, 3 ]) ≡ Some 1

let _ =
        assert
      :   ListNat/headOptional (ListNat/fromList ([] : List Natural))
        ≡ None Natural

let ListNat/fix
    : ListNatF ListNat → ListNat
    = fix ListNatF functorListNatF

let ListNat/length
    : ListNat → Natural
    = λ(listNat : ListNat) →
        listNat
          Natural
          ( λ(lf : ListNatF Natural) →
              merge
                { Nil = 0
                , Cons =
                    λ(pair : { head : Natural, tail : Natural }) → pair.tail + 1
                }
                lf
          )

let ListNat/unfix
    : ListNat → ListNatF ListNat
    = unfix ListNatF functorListNatF

let ListNat/tail
    : ListNat → ListNat
    = λ(listNat : ListNat) →
        merge
          { Nil = ListNat/nil
          , Cons = λ(pair : { head : Natural, tail : ListNat }) → pair.tail
          }
          (ListNat/unfix listNat)

let _ =
      assert : ListNat/tail (ListNat/fromList ([] : List Natural)) ≡ ListNat/nil

let _ =
        assert
      : ListNat/toList (ListNat/tail (ListNat/fromList [ 1, 2, 3 ])) ≡ [ 2, 3 ]

let ListNat/sum
    : ListNat → Natural
    = λ(listNat : ListNat) →
        listNat
          Natural
          ( λ(lf : ListNatF Natural) →
              merge
                { Nil = 0
                , Cons =
                    λ(pair : { head : Natural, tail : Natural }) →
                      pair.head + pair.tail
                }
                lf
          )

let _ = assert : ListNat/sum (ListNat/fromList [ 1, 2, 3 ]) ≡ 6

let _ = assert : ListNat/sum (ListNat/fromList ([] : List Natural)) ≡ 0

let ListNat/concat
    : ListNat → ListNat → ListNat
    = λ(left : ListNat) →
      λ(right : ListNat) →
        left
          ListNat
          ( λ(lf : ListNatF ListNat) →
              merge
                { Nil = right
                , Cons =
                    λ(pair : { head : Natural, tail : ListNat }) →
                      ListNat/cons pair.head pair.tail
                }
                lf
          )

let _ =
        assert
      :   ListNat/toList
            ( ListNat/concat
                (ListNat/fromList [ 1, 2, 3 ])
                (ListNat/fromList [ 4, 5, 6 ])
            )
        ≡ [ 1, 2, 3, 4, 5, 6 ]

let
    -- Same things implemented using the Church-Yoneda encoding.
    ListNatY =
      LFixYonedaModule.LFixYoneda ListNatF

let ListNatY/nil
    : ListNatY
    = λ(r : Type) → λ(frr : ListNatF r → r) → (ListNatF r).Nil

let ListNatY/cons
    : Natural → ListNatY → ListNatY
    = λ(head : Natural) →
      λ(tail : ListNatY) →
      λ(r : Type) →
      λ(frr : ListNatF r → r) →
        (ListNatF r).Cons { head, tail = frr (tail r frr) }

let ListNatY/consl
    : Natural → ListNatY → ListNatY
    = λ(head : Natural) →
      λ(tail : ListNatY) →
        LFixYonedaModule.toLFixYoneda
          ListNatF
          functorListNatF
          (ListNat/cons head (LFixYonedaModule.fromLFixYoneda ListNatF tail))

let ListNatY/fromList
    : List Natural → ListNatY
    = λ(list : List Natural) →
        List/fold Natural list ListNatY ListNatY/cons ListNatY/nil

let ListNatY/fromListl
    : List Natural → ListNatY
    = λ(list : List Natural) →
        List/fold Natural list ListNatY ListNatY/consl ListNatY/nil

let ListNatY/toList
    : ListNatY → List Natural
    = λ(listNatY : ListNatY) →
        let result
            : ListNatF (List Natural)
            = listNatY
                (List Natural)
                ( λ(lf : ListNatF (List Natural)) →
                    merge
                      { Nil = [] : List Natural
                      , Cons =
                          λ(pair : { head : Natural, tail : List Natural }) →
                            [ pair.head ] # pair.tail
                      }
                      lf
                )

        in  merge
              { Nil = [] : List Natural
              , Cons =
                  λ(pair : { head : Natural, tail : List Natural }) →
                    [ pair.head ] # pair.tail
              }
              result

let _ =
        assert
      :   ListNatY/toList (ListNatY/fromList ([] : List Natural))
        ≡ ([] : List Natural)

let _ =
        assert
      :   ListNatY/toList (ListNatY/fromListl ([] : List Natural))
        ≡ ([] : List Natural)

let _ = assert : ListNatY/toList (ListNatY/fromList [ 1, 2, 3 ]) ≡ [ 1, 2, 3 ]

let _ -- make sure this is the same as the previous one
      =
      assert : ListNatY/toList (ListNatY/fromListl [ 1, 2, 3 ]) ≡ [ 1, 2, 3 ]

let example1 = ListNat/fromList [ 1, 2, 3 ]

let example1Y = LFixYonedaModule.toLFixYoneda ListNatF functorListNatF example1

let _ = assert : ListNat/toList example1 ≡ [ 1, 2, 3 ]

let _ = assert : ListNatY/toList example1Y ≡ [ 1, 2, 3 ]

let example2Y = ListNatY/fromList [ 1, 2, 3 ]

let _ = assert : ListNatY/toList example2Y ≡ [ 1, 2, 3 ]

let example3Y = ListNatY/fromListl [ 1, 2, 3 ]

let _ = assert : ListNatY/toList example3Y ≡ [ 1, 2, 3 ]

let ListNatY/unfix
    : ListNatY → ListNatF ListNatY
    = λ(listNatY : ListNatY) →
        listNatY
          ListNatY
          ( λ(lf : ListNatF ListNatY) →
              merge
                { Nil = ListNatY/nil
                , Cons =
                    λ(pair : { head : Natural, tail : ListNatY }) →
                      ListNatY/cons pair.head pair.tail
                }
                lf
          )

let ListNatY/fix
    : ListNatF ListNatY → ListNatY
    = λ(lf : ListNatF ListNatY) →
        merge
          { Nil = ListNatY/nil
          , Cons =
              λ(pair : { head : Natural, tail : ListNatY }) →
                ListNatY/cons pair.head pair.tail
          }
          lf

let ListNatY/nonEmpty
    : ListNatY → Bool
    = λ(listNatY : ListNatY) →
        let fBool
            : ListNatF Bool
            = listNatY Bool (λ(lf : ListNatF Bool) → False)

        in  merge
              { Nil = False
              , Cons = λ(pair : { head : Natural, tail : Bool }) → True
              }
              fBool

let _ = assert : ListNatY/nonEmpty (ListNatY/fromList [ 1, 2, 3 ]) ≡ True

let _ =
      assert : ListNatY/nonEmpty (ListNatY/fromList ([] : List Natural)) ≡ False

let ListNatY/headOptional
    : ListNatY → Optional Natural
    = λ(listNatY : ListNatY) →
        let fOptional
            : ListNatF (Optional Natural)
            = listNatY
                (Optional Natural)
                (λ(lf : ListNatF (Optional Natural)) → None Natural)

        in  merge
              { Nil = None Natural
              , Cons =
                  λ(pair : { head : Natural, tail : Optional Natural }) →
                    Some pair.head
              }
              fOptional

let _ = assert : ListNatY/headOptional (ListNatY/fromList [ 1, 2, 3 ]) ≡ Some 1

let _ =
        assert
      :   ListNatY/headOptional (ListNatY/fromList ([] : List Natural))
        ≡ None Natural

let ListNatY/length
    : ListNatY → Natural
    = λ(listNatY : ListNatY) →
        let fNatural
            : ListNatF Natural
            = listNatY
                Natural
                ( λ(lf : ListNatF Natural) →
                    merge
                      { Nil = 0
                      , Cons =
                          λ(pair : { head : Natural, tail : Natural }) →
                            pair.tail + 1
                      }
                      lf
                )

        in  merge
              { Nil = 0
              , Cons =
                  λ(pair : { head : Natural, tail : Natural }) → pair.tail + 1
              }
              fNatural

let _ = assert : ListNatY/length (ListNatY/fromList [ 1, 2, 3 ]) ≡ 3

let _ = assert : ListNatY/length (ListNatY/fromList ([] : List Natural)) ≡ 0

let ListNatY/tail
    : ListNatY → ListNatY
    = λ(listNatY : ListNatY) →
        merge
          { Nil = ListNatY/nil
          , Cons = λ(pair : { head : Natural, tail : ListNatY }) → pair.tail
          }
          (ListNatY/unfix listNatY)

let _ =
        assert
      :   ListNatY/toList (ListNatY/tail (ListNatY/fromList [ 1, 2, 3 ]))
        ≡ [ 2, 3 ]

let _ =
        assert
      :   ListNatY/toList
            (ListNatY/tail (ListNatY/fromList ([] : List Natural)))
        ≡ ([] : List Natural)

let ListNatY/sum
    : ListNatY → Natural
    = λ(listNatY : ListNatY) →
        let fsum
            : ListNatF Natural
            = listNatY
                Natural
                ( λ(lf : ListNatF Natural) →
                    merge
                      { Nil = 0
                      , Cons =
                          λ(pair : { head : Natural, tail : Natural }) →
                            pair.head + pair.tail
                      }
                      lf
                )

        in  merge
              { Nil = 0
              , Cons =
                  λ(pair : { head : Natural, tail : Natural }) →
                    pair.head + pair.tail
              }
              fsum

let _ = assert : ListNatY/sum (ListNatY/fromList [ 1, 2, 3 ]) ≡ 6

let _ = assert : ListNatY/sum (ListNatY/fromList ([] : List Natural)) ≡ 0

let ListNatY/concat
    : ListNatY → ListNatY → ListNatY
    = λ(left : ListNatY) →
      λ(right : ListNatY) →
        LFixYonedaModule.toLFixYoneda
          ListNatF
          functorListNatF
          ( LFixYonedaModule.fromLFixYoneda
              ListNatF
              left
              ListNat
              ( λ(lf : ListNatF ListNat) →
                  merge
                    { Nil = LFixYonedaModule.fromLFixYoneda ListNatF right
                    , Cons =
                        λ(pair : { head : Natural, tail : ListNat }) →
                          ListNat/cons pair.head pair.tail
                    }
                    lf
              )
          )

let _ =
        assert
      :   ListNatY/toList
            ( ListNatY/concat
                (ListNatY/fromList [ 1, 2, 3 ])
                (ListNatY/fromList [ 4, 5, 6 ])
            )
        ≡ [ 1, 2, 3, 4, 5, 6 ]

in  { ListNat
    , ListNatF
    , functorListNatF
    , ListNat/cons
    , ListNat/nil
    , ListNat/fromList
    , ListNat/toList
    , ListNat/unfix
    , ListNat/nonEmpty
    , ListNat/headOptional
    , ListNat/length
    , ListNat/tail
    , ListNat/fix
    , ListNat/sum
    , ListNat/concat
    , ListNatY
    , ListNatY/cons
    , ListNatY/nil
    , ListNatY/fromList
    , ListNatY/toList
    , ListNatY/unfix
    , ListNatY/tail
    , ListNatY/fix
    , ListNatY/nonEmpty
    , ListNatY/headOptional
    , ListNatY/length
    , ListNatY/sum
    , ListNatY/concat
    }
