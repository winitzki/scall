-- standard lists, non-empty lists, non-empty binary trees, branch-data binary trees
let Ch = ./Church.dhall

let ChurchNaturals = ./ChurchNaturals.dhall

let ChurchNatural = ChurchNaturals.ChurchNatural

let ChurchZip = ./ChurchZip.dhall

let Natural/max = https://prelude.dhall-lang.org/Natural/max

let Hylo = ./ChurchHylo.dhall

let B = ./Bifunctor.dhall

let Either = B.Either

let inRight = B.inRight

let inLeft = B.inLeft

let mkPair = B.mkPair

let Pair = B.Pair

let Triple = B.Triple

let mkTriple = B.mkTriple

let S_list = λ(a : Type) → λ(r : Type) → Either {} (Pair a r)

let C_list = Ch.T1 S_list

let S_nonempty_list = λ(a : Type) → λ(r : Type) → Either a (Pair a r)

let C_nonempty_list = Ch.T1 S_nonempty_list

let S_nonempty_tree = λ(a : Type) → λ(r : Type) → Either a (Pair r r)

let C_nonempty_tree = Ch.T1 S_nonempty_tree

let S_branch_data_tree = λ(a : Type) → λ(r : Type) → Either {} (Triple a r r)

let C_branch_data_tree = Ch.T1 S_branch_data_tree

let bimap_S_list
    : B.Bimap S_list
    = let S = S_list

      in  λ(a : Type) →
          λ(b : Type) →
          λ(c : Type) →
          λ(d : Type) →
          λ(f : a → c) →
          λ(g : b → d) →
          λ(sab : S a b) →
            merge
              { Left = λ(x : {}) → (S c d).Left x
              , Right =
                  λ(pair : Pair a b) →
                    let p
                        : Pair c d
                        = mkPair c (f pair._1) d (g pair._2)

                    in  (S c d).Right p
              }
              sab
            : S c d

let bimap_S_nonempty_list
    : B.Bimap S_nonempty_list
    = let S = S_nonempty_list

      in  λ(a : Type) →
          λ(b : Type) →
          λ(c : Type) →
          λ(d : Type) →
          λ(f : a → c) →
          λ(g : b → d) →
          λ(sab : S a b) →
            merge
              { Left = λ(x : a) → (S c d).Left (f x)
              , Right =
                  λ(pair : Pair a b) →
                    let p
                        : Pair c d
                        = mkPair c (f pair._1) d (g pair._2)

                    in  (S c d).Right p
              }
              sab
            : S c d

let bimap_S_nonempty_tree
    : B.Bimap S_nonempty_tree
    = let S = S_nonempty_tree

      in  λ(a : Type) →
          λ(b : Type) →
          λ(c : Type) →
          λ(d : Type) →
          λ(f : a → c) →
          λ(g : b → d) →
          λ(sab : S a b) →
            merge
              { Left = λ(x : a) → (S c d).Left (f x)
              , Right =
                  λ(pair : Pair b b) →
                    let p
                        : Pair d d
                        = mkPair d (g pair._1) d (g pair._2)

                    in  (S c d).Right p
              }
              sab
            : S c d

let bimap_S_branch_data_tree
    : B.Bimap S_branch_data_tree
    = let S = S_branch_data_tree

      in  λ(a : Type) →
          λ(b : Type) →
          λ(c : Type) →
          λ(d : Type) →
          λ(f : a → c) →
          λ(g : b → d) →
          λ(sab : S a b) →
            merge
              { Left = λ(x : {}) → (S c d).Left x
              , Right =
                  λ(pair : Triple a b b) →
                    let p
                        : Triple c d d
                        = mkTriple c (f pair._1) d (g pair._2) d (g pair._3)

                    in  (S c d).Right p
              }
              sab
            : S c d

let depth_S_list
    : B.Depth S_list
    = let S = S_list

      in  λ(a : Type) →
          λ(san : S a Natural) →
            merge
              { Left = λ(_ : {}) → 0
              , Right = λ(pair : Pair a Natural) → pair._2 + 1
              }
              san

let depth_S_nonempty_list
    : B.Depth S_nonempty_list
    = let S = S_nonempty_list

      in  λ(a : Type) →
          λ(san : S a Natural) →
            merge
              { Left = λ(_ : a) → 0
              , Right = λ(pair : Pair a Natural) → pair._2 + 1
              }
              san

let depth_S_branch_data_tree
    : B.Depth S_branch_data_tree
    = let S = S_branch_data_tree

      in  λ(a : Type) →
          λ(san : S a Natural) →
            merge
              { Left = λ(_ : {}) → 0
              , Right =
                  λ(triple : Triple a Natural Natural) →
                    Natural/max triple._2 triple._3 + 1
              }
              san

let depth_S_nonempty_tree
    : B.Depth S_nonempty_tree
    = let S = S_nonempty_tree

      in  λ(a : Type) →
          λ(san : S a Natural) →
            merge
              { Left = λ(_ : a) → 0
              , Right =
                  λ(pair : Pair Natural Natural) →
                    Natural/max pair._1 pair._2 + 1
              }
              san

let zip0_S_branch_data_tree
    : B.Zip0 S_branch_data_tree
    = let S = S_branch_data_tree

      in  λ(a : Type) →
          λ(b : Type) →
          λ(r : Type) →
          λ(sar : S a r) →
          λ(sbr : S b r) →
            let Sab = S (Pair a b) r

            in  merge
                  { Left = λ(x : {}) → Sab.Left x
                  , Right =
                      λ(triple_a : Triple a r r) →
                        merge
                          { Left = λ(x : {}) → Sab.Left x
                          , Right =
                              λ(triple_b : Triple b r r) →
                                Sab.Right
                                  ( mkTriple
                                      (Pair a b)
                                      (mkPair a triple_a._1 b triple_b._1)
                                      r
                                      triple_a._2
                                      r
                                      triple_a._3
                                  )
                          }
                          sbr
                  }
                  sar
                : Sab

let zip0_S_nonempty_tree
    : B.Zip0 S_nonempty_tree
    = let S = S_nonempty_tree

      in  λ(a : Type) →
          λ(b : Type) →
          λ(r : Type) →
          λ(sar : S a r) →
          λ(sbr : S b r) →
            let Sab = S (Pair a b) r

            in  merge
                  { Left =
                      λ(x : a) →
                        merge
                          { Left = λ(y : b) → Sab.Left (mkPair a x b y)
                          , Right = λ(pair_b : Pair r r) → Sab.Right pair_b
                          }
                          sbr
                  , Right = λ(pair_a : Pair r r) → Sab.Right pair_a
                  }
                  sar
                : Sab

let zip0_S_list
    : B.Zip0 S_list
    = let S = S_list

      in  λ(a : Type) →
          λ(b : Type) →
          λ(r : Type) →
          λ(sar : S a r) →
          λ(sbr : S b r) →
            let Sab = S (Pair a b) r

            in  merge
                  { Left = λ(x : {}) → Sab.Left x
                  , Right =
                      λ(pair_a : Pair a r) →
                        merge
                          { Left = λ(x : {}) → Sab.Left x
                          , Right =
                              λ(pair_b : Pair b r) →
                                Sab.Right
                                  ( mkPair
                                      (Pair a b)
                                      (mkPair a pair_a._1 b pair_b._1)
                                      r
                                      pair_a._2
                                  )
                          }
                          sbr
                  }
                  sar
                : Sab

let zip0_S_nonempty_list_padding
    : B.Zip0 S_nonempty_list
    = let S = S_nonempty_list

      in  λ(a : Type) →
          λ(b : Type) →
          λ(r : Type) →
          λ(sar : S a r) →
          λ(sbr : S b r) →
            let Sab = S (Pair a b) r

            in  merge
                  { Left =
                      λ(x : a) →
                        merge
                          { Left = λ(y : b) → Sab.Left (mkPair a x b y)
                          , Right =
                              λ(pair_b : Pair b r) →
                                Sab.Right
                                  ( mkPair
                                      (Pair a b)
                                      (mkPair a x b pair_b._1)
                                      r
                                      pair_b._2
                                  )
                          }
                          sbr
                  , Right =
                      λ(pair_a : Pair a r) →
                        merge
                          { Left =
                              λ(y : b) →
                                Sab.Right
                                  ( mkPair
                                      (Pair a b)
                                      (mkPair a pair_a._1 b y)
                                      r
                                      pair_a._2
                                  )
                          , Right =
                              λ(pair_b : Pair b r) →
                                Sab.Right
                                  ( mkPair
                                      (Pair a b)
                                      (mkPair a pair_a._1 b pair_b._1)
                                      r
                                      pair_a._2
                                  )
                          }
                          sbr
                  }
                  sar
                : Sab

let zip0_S_nonempty_list_truncating
    : B.Zip0 S_nonempty_list
    = let S = S_nonempty_list

      in  λ(a : Type) →
          λ(b : Type) →
          λ(r : Type) →
          λ(sar : S a r) →
          λ(sbr : S b r) →
            let Sab = S (Pair a b) r

            in  merge
                  { Left =
                      λ(x : a) →
                        merge
                          { Left = λ(y : b) → Sab.Left (mkPair a x b y)
                          , Right =
                              λ(pair_b : Pair b r) →
                                Sab.Left (mkPair a x b pair_b._1)
                          }
                          sbr
                  , Right =
                      λ(pair_a : Pair a r) →
                        merge
                          { Left = λ(y : b) → Sab.Left (mkPair a pair_a._1 b y)
                          , Right =
                              λ(pair_b : Pair b r) →
                                Sab.Right
                                  ( mkPair
                                      (Pair a b)
                                      (mkPair a pair_a._1 b pair_b._1)
                                      r
                                      pair_a._2
                                  )
                          }
                          sbr
                  }
                  sar
                : Sab

let bizip_S_list
    : B.Bizip S_list
    = let S = S_list

      in  λ(a : Type) →
          λ(b : Type) →
          λ(p : Type) →
          λ(q : Type) →
            let Sabpq = S (Pair a b) (Pair p q)

            in  λ(sap : S a p) →
                λ(sbq : S b q) →
                  merge
                    { Left = λ(x : {}) → Sabpq.Left x
                    , Right =
                        λ(pair_a : Pair a p) →
                          merge
                            { Left = λ(x : {}) → Sabpq.Left x
                            , Right =
                                λ(pair_b : Pair b q) →
                                  Sabpq.Right
                                    ( mkPair
                                        (Pair a b)
                                        (mkPair a pair_a._1 b pair_b._1)
                                        (Pair p q)
                                        (mkPair p pair_a._2 q pair_b._2)
                                    )
                            }
                            sbq
                    }
                    sap
                  : Sabpq

let bizip_S_nonempty_list
    : B.Bizip S_nonempty_list
    = let S = S_nonempty_list

      in  λ(a : Type) →
          λ(b : Type) →
          λ(p : Type) →
          λ(q : Type) →
            let Sabpq = S (Pair a b) (Pair p q)

            in  λ(sap : S a p) →
                λ(sbq : S b q) →
                  merge
                    { Left =
                        λ(x : a) →
                          merge
                            { Left = λ(y : b) → Sabpq.Left (mkPair a x b y)
                            , Right =
                                λ(pair_b : Pair b q) →
                                  Sabpq.Left (mkPair a x b pair_b._1)
                            }
                            sbq
                    , Right =
                        λ(pair_a : Pair a p) →
                          merge
                            { Left =
                                λ(y : b) → Sabpq.Left (mkPair a pair_a._1 b y)
                            , Right =
                                λ(pair_b : Pair b q) →
                                  Sabpq.Right
                                    ( mkPair
                                        (Pair a b)
                                        (mkPair a pair_a._1 b pair_b._1)
                                        (Pair p q)
                                        (mkPair p pair_a._2 q pair_b._2)
                                    )
                            }
                            sbq
                    }
                    sap
                  : Sabpq

let bizip_S_branch_data_tree
    : B.Bizip S_branch_data_tree
    = let S = S_branch_data_tree

      in  λ(a : Type) →
          λ(b : Type) →
          λ(p : Type) →
          λ(q : Type) →
            let Sabpq = S (Pair a b) (Pair p q)

            in  λ(sap : S a p) →
                λ(sbq : S b q) →
                  merge
                    { Left = λ(x : {}) → Sabpq.Left x
                    , Right =
                        λ(pair_a : Triple a p p) →
                          merge
                            { Left = λ(x : {}) → Sabpq.Left x
                            , Right =
                                λ(pair_b : Triple b q q) →
                                  Sabpq.Right
                                    ( mkTriple
                                        (Pair a b)
                                        (mkPair a pair_a._1 b pair_b._1)
                                        (Pair p q)
                                        (mkPair p pair_a._2 q pair_b._2)
                                        (Pair p q)
                                        (mkPair p pair_a._3 q pair_b._3)
                                    )
                            }
                            sbq
                    }
                    sap
                  : Sabpq

let bizipK_via_bizip =
      λ(S : B.Bifunctor) →
      λ(bizip : B.Bizip S) →
      λ(C : B.Functor) →
      λ(fmapC : B.Map C) →
      λ(a : Type) →
      λ(b : Type) →
        let Sab = S (Pair a b) (Pair (C a) (C b))

        in  λ(saca : S a (C a)) →
            λ(sbcb : S b (C b)) →
              bizip a b (C a) (C b) saca sbcb : Sab

let bizipK_S_list
    : B.BizipK S_list
    = bizipK_via_bizip S_list bizip_S_list

let bizipK_S_nonempty_list_truncating
    : B.BizipK S_nonempty_list
    = bizipK_via_bizip S_nonempty_list bizip_S_nonempty_list

let bizipK_S_branch_data_tree
    : B.BizipK S_branch_data_tree
    = bizipK_via_bizip S_branch_data_tree bizip_S_branch_data_tree

let bizipK_S_nonempty_list_padding
    : B.BizipK S_nonempty_list
    = let S = S_nonempty_list

      in  λ(C : B.Functor) →
          λ(fmapC : B.Map C) →
          λ(a : Type) →
          λ(b : Type) →
            let Sab = S (Pair a b) (Pair (C a) (C b))

            in  λ(saca : S a (C a)) →
                λ(sbcb : S b (C b)) →
                  merge
                    { Left =
                        λ(x : a) →
                          merge
                            { Left = λ(y : b) → Sab.Left (mkPair a x b y)
                            , Right =
                                λ(pair_b : Pair b (C b)) →
                                  let ca
                                      : C a
                                      = fmapC b a (λ(_ : b) → x) pair_b._2

                                  in  Sab.Right
                                        ( mkPair
                                            (Pair a b)
                                            (mkPair a x b pair_b._1)
                                            (Pair (C a) (C b))
                                            (mkPair (C a) ca (C b) pair_b._2)
                                        )
                            }
                            sbcb
                    , Right =
                        λ(pair_a : Pair a (C a)) →
                          merge
                            { Left =
                                λ(y : b) →
                                  let cb
                                      : C b
                                      = fmapC a b (λ(_ : a) → y) pair_a._2

                                  in  Sab.Right
                                        ( mkPair
                                            (Pair a b)
                                            (mkPair a pair_a._1 b y)
                                            (Pair (C a) (C b))
                                            (mkPair (C a) pair_a._2 (C b) cb)
                                        )
                            , Right =
                                λ(pair_b : Pair b (C b)) →
                                  Sab.Right
                                    ( mkPair
                                        (Pair a b)
                                        (mkPair a pair_a._1 b pair_b._1)
                                        (Pair (C a) (C b))
                                        (mkPair (C a) pair_a._2 (C b) pair_b._2)
                                    )
                            }
                            sbcb
                    }
                    saca
                  : Sab

let bizipK_S_nonempty_tree
    : B.BizipK S_nonempty_tree
    = let S = S_nonempty_tree

      in  λ(C : B.Functor) →
          λ(fmapC : B.Map C) →
          λ(a : Type) →
          λ(b : Type) →
            let Sab = S (Pair a b) (Pair (C a) (C b))

            in  λ(saca : S a (C a)) →
                λ(sbcb : S b (C b)) →
                  merge
                    { Left =
                        λ(x : a) →
                          merge
                            { Left = λ(y : b) → Sab.Left (mkPair a x b y)
                            , Right =
                                λ(pair_b : Pair (C b) (C b)) →
                                  let ca1
                                      : C a
                                      = fmapC b a (λ(_ : b) → x) pair_b._1

                                  let ca2
                                      : C a
                                      = fmapC b a (λ(_ : b) → x) pair_b._2

                                  in  Sab.Right
                                        ( mkPair
                                            (Pair (C a) (C b))
                                            (mkPair (C a) ca1 (C b) pair_b._1)
                                            (Pair (C a) (C b))
                                            (mkPair (C a) ca2 (C b) pair_b._2)
                                        )
                            }
                            sbcb
                    , Right =
                        λ(pair_a : Pair (C a) (C a)) →
                          merge
                            { Left =
                                λ(y : b) →
                                  let cb1
                                      : C b
                                      = fmapC a b (λ(_ : a) → y) pair_a._1

                                  let cb2
                                      : C b
                                      = fmapC a b (λ(_ : a) → y) pair_a._2

                                  in  Sab.Right
                                        ( mkPair
                                            (Pair (C a) (C b))
                                            (mkPair (C a) pair_a._1 (C b) cb1)
                                            (Pair (C a) (C b))
                                            (mkPair (C a) pair_a._2 (C b) cb2)
                                        )
                            , Right =
                                λ(pair_b : Pair (C b) (C b)) →
                                  Sab.Right
                                    ( mkPair
                                        (Pair (C a) (C b))
                                        (mkPair (C a) pair_a._1 (C b) pair_b._1)
                                        (Pair (C a) (C b))
                                        (mkPair (C a) pair_a._2 (C b) pair_b._2)
                                    )
                            }
                            sbcb
                    }
                    saca
                  : Sab

let print_C_list
    : ∀(a : Type) → (a → Text) → C_list a → Text
    = λ(a : Type) →
      λ(show : a → Text) →
      λ(c : C_list a) →
        let items =
              c
                Text
                ( λ(frr : S_list a Text) →
                    merge
                      { Left = λ(x : {}) → ""
                      , Right = λ(p : Pair a Text) → show p._1 ++ ", " ++ p._2
                      }
                      frr
                )

        in  "[" ++ items ++ "]"

let list_build
    : ∀(a : Type) → S_list a (C_list a) → C_list a
    = Ch.fixT1 S_list bimap_S_list

let list_nil
    : ∀(a : Type) → C_list a
    = λ(a : Type) → list_build a ((S_list a (C_list a)).Left {=})

let list_cons
    : ∀(a : Type) → a → C_list a → C_list a
    = λ(a : Type) →
      λ(x : a) →
      λ(c : C_list a) →
        list_build a ((S_list a (C_list a)).Right (mkPair a x (C_list a) c))

let list_example_1_2
    : C_list Natural
    = list_cons Natural 1 (list_cons Natural 2 (list_nil Natural))

let list_example_1
    : C_list Natural
    = list_cons Natural 1 (list_nil Natural)

let list_example_1_2_3_4
    : C_list Natural
    = list_cons
        Natural
        1
        ( list_cons
            Natural
            2
            (list_cons Natural 3 (list_cons Natural 4 (list_nil Natural)))
        )

let test = assert : print_C_list Natural Natural/show (list_nil Natural) ≡ "[]"

let test = assert : print_C_list Natural Natural/show list_example_1 ≡ "[1, ]"

let test =
      assert : print_C_list Natural Natural/show list_example_1_2 ≡ "[1, 2, ]"

let test =
        assert
      :   print_C_list Natural Natural/show list_example_1_2_3_4
        ≡ "[1, 2, 3, 4, ]"

let nonempty_tree_build
    : ∀(a : Type) → S_nonempty_tree a (C_nonempty_tree a) → C_nonempty_tree a
    = Ch.fixT1 S_nonempty_tree bimap_S_nonempty_tree

let nonempty_tree_leaf
    : ∀(a : Type) → ∀(x : a) → C_nonempty_tree a
    = λ(a : Type) →
      λ(x : a) →
        nonempty_tree_build a ((S_nonempty_tree a (C_nonempty_tree a)).Left x)

let nonempty_tree_branch
    : ∀(a : Type) → C_nonempty_tree a → C_nonempty_tree a → C_nonempty_tree a
    = λ(a : Type) →
      λ(left : C_nonempty_tree a) →
      λ(right : C_nonempty_tree a) →
        nonempty_tree_build
          a
          ( (S_nonempty_tree a (C_nonempty_tree a)).Right
              (mkPair (C_nonempty_tree a) left (C_nonempty_tree a) right)
          )

let print_C_nonempty_tree
    : ∀(a : Type) → (a → Text) → C_nonempty_tree a → Text
    = λ(a : Type) →
      λ(show : a → Text) →
      λ(c : C_nonempty_tree a) →
        let items =
              c
                Text
                ( λ(frr : S_nonempty_tree a Text) →
                    merge
                      { Left = λ(x : a) → show x
                      , Right = λ(p : Pair Text Text) → "[${p._1} ${p._2}]"
                      }
                      frr
                )

        in  "[${items}]"

let nonempty_tree_123 =
      nonempty_tree_branch
        Natural
        (nonempty_tree_leaf Natural 1)
        ( nonempty_tree_branch
            Natural
            (nonempty_tree_leaf Natural 2)
            (nonempty_tree_leaf Natural 3)
        )

let test =
        assert
      :   print_C_nonempty_tree Natural Natural/show nonempty_tree_123
        ≡ "[[1 [2 3]]]"

let nonempty_tree_1234 =
      nonempty_tree_branch
        Natural
        ( nonempty_tree_branch
            Natural
            (nonempty_tree_leaf Natural 1)
            ( nonempty_tree_branch
                Natural
                (nonempty_tree_leaf Natural 2)
                (nonempty_tree_leaf Natural 3)
            )
        )
        (nonempty_tree_leaf Natural 4)

let test =
        assert
      :   print_C_nonempty_tree Natural Natural/show nonempty_tree_1234
        ≡ "[[[1 [2 3]] 4]]"

let print_C_nonempty_list
    : ∀(a : Type) → (a → Text) → C_nonempty_list a → Text
    = λ(a : Type) →
      λ(show : a → Text) →
      λ(c : C_nonempty_list a) →
        let items =
              c
                Text
                ( λ(frr : S_nonempty_list a Text) →
                    merge
                      { Left = λ(x : a) → show x
                      , Right = λ(p : Pair a Text) → "${show p._1}, ${p._2}"
                      }
                      frr
                )

        in  "[${items}]"

let nonempty_list_build
    : ∀(a : Type) → S_nonempty_list a (C_nonempty_list a) → C_nonempty_list a
    = Ch.fixT1 S_nonempty_list bimap_S_nonempty_list

let nonempty_list_leaf
    : ∀(a : Type) → ∀(x : a) → C_nonempty_list a
    = λ(a : Type) →
      λ(x : a) →
        nonempty_list_build a ((S_nonempty_list a (C_nonempty_list a)).Left x)

let nonempty_list_cons
    : ∀(a : Type) → a → C_nonempty_list a → C_nonempty_list a
    = λ(a : Type) →
      λ(x : a) →
      λ(c : C_nonempty_list a) →
        nonempty_list_build
          a
          ( (S_nonempty_list a (C_nonempty_list a)).Right
              (mkPair a x (C_nonempty_list a) c)
          )

let nonempty_list_example_1_2
    : C_nonempty_list Natural
    = nonempty_list_cons Natural 1 (nonempty_list_leaf Natural 2)

let nonempty_list_example_1
    : C_nonempty_list Natural
    = nonempty_list_leaf Natural 1

let nonempty_list_example_1_2_3_4
    : C_nonempty_list Natural
    = nonempty_list_cons
        Natural
        1
        ( nonempty_list_cons
            Natural
            2
            (nonempty_list_cons Natural 3 (nonempty_list_leaf Natural 4))
        )

let test =
        assert
      :   print_C_nonempty_list Natural Natural/show nonempty_list_example_1
        ≡ "[1]"

let test =
        assert
      :   print_C_nonempty_list Natural Natural/show nonempty_list_example_1_2
        ≡ "[1, 2]"

let test =
        assert
      :   print_C_nonempty_list
            Natural
            Natural/show
            nonempty_list_example_1_2_3_4
        ≡ "[1, 2, 3, 4]"

let branch_data_tree_build
    : ∀(a : Type) →
      S_branch_data_tree a (C_branch_data_tree a) →
        C_branch_data_tree a
    = Ch.fixT1 S_branch_data_tree bimap_S_branch_data_tree

let branch_data_tree_nil
    : ∀(a : Type) → C_branch_data_tree a
    = λ(a : Type) →
        branch_data_tree_build
          a
          ((S_branch_data_tree a (C_branch_data_tree a)).Left {=})

let branch_data_tree_branch
    : ∀(a : Type) →
      a →
      C_branch_data_tree a →
      C_branch_data_tree a →
        C_branch_data_tree a
    = λ(a : Type) →
      λ(x : a) →
      λ(left : C_branch_data_tree a) →
      λ(right : C_branch_data_tree a) →
        branch_data_tree_build
          a
          ( (S_branch_data_tree a (C_branch_data_tree a)).Right
              ( mkTriple
                  a
                  x
                  (C_branch_data_tree a)
                  left
                  (C_branch_data_tree a)
                  right
              )
          )

let print_branch_data_tree
    : ∀(a : Type) → (a → Text) → C_branch_data_tree a → Text
    = λ(a : Type) →
      λ(show : a → Text) →
      λ(c : C_branch_data_tree a) →
        let items =
              c
                Text
                ( λ(frr : S_branch_data_tree a Text) →
                    merge
                      { Left = λ(x : {}) → "*"
                      , Right =
                          λ(p : Triple a Text Text) →
                            "${show p._1}[${p._2} ${p._3}]"
                      }
                      frr
                )

        in  "[" ++ items ++ "]"

let branch_data_tree_one =
      λ(a : Type) →
      λ(x : a) →
        branch_data_tree_branch
          a
          x
          (branch_data_tree_nil a)
          (branch_data_tree_nil a)

let branch_data_tree_1_2_3
    : C_branch_data_tree Natural
    = let nil = branch_data_tree_nil Natural

      in  branch_data_tree_branch
            Natural
            1
            nil
            ( branch_data_tree_branch
                Natural
                2
                (branch_data_tree_nil Natural)
                (branch_data_tree_one Natural 3)
            )

let branch_data_tree_1_2_3_4_5
    : C_branch_data_tree Natural
    = let nil = branch_data_tree_nil Natural

      in  branch_data_tree_branch
            Natural
            1
            ( branch_data_tree_branch
                Natural
                2
                nil
                ( branch_data_tree_branch
                    Natural
                    3
                    (branch_data_tree_one Natural 4)
                    nil
                )
            )
            (branch_data_tree_one Natural 5)

let test =
        assert
      :   print_branch_data_tree
            Natural
            Natural/show
            (branch_data_tree_nil Natural)
        ≡ "[*]"

let test =
        assert
      :   print_branch_data_tree
            Natural
            Natural/show
            (branch_data_tree_one Natural 1)
        ≡ "[1[* *]]"

let test =
        assert
      :   print_branch_data_tree Natural Natural/show branch_data_tree_1_2_3
        ≡ "[1[* 2[* 3[* *]]]]"

let test =
        assert
      :   print_branch_data_tree Natural Natural/show branch_data_tree_1_2_3_4_5
        ≡ "[1[2[* 3[4[* *] *]] 5[* *]]]"

let list_zip0 =
      λ(a : Type) →
      λ(left : C_list a) →
      λ(b : Type) →
      λ(right : C_list b) →
        ChurchZip.zip0 S_list zip0_S_list a b left right : C_list (Pair a b)

let test =
        assert
      :   print_C_list
            (Pair Natural Natural)
            ( λ(p : Pair Natural Natural) →
                "(${Natural/show p._1}, ${Natural/show p._2})"
            )
            (list_zip0 Natural list_example_1_2 Natural list_example_1_2)
        ≡ "[(1, 1), (2, 1), ]"

let test =
        assert
      :   print_C_list
            (Pair Natural Natural)
            ( λ(p : Pair Natural Natural) →
                "(${Natural/show p._1}, ${Natural/show p._2})"
            )
            (list_zip0 Natural list_example_1_2 Natural list_example_1_2_3_4)
        ≡ "[(1, 1), (2, 1), ]"

let test_zip0_does_not_recurse_on_second_list_at_all =
        assert
      :   print_C_list
            (Pair Natural Natural)
            ( λ(p : Pair Natural Natural) →
                "(${Natural/show p._1}, ${Natural/show p._2})"
            )
            (list_zip0 Natural list_example_1_2_3_4 Natural list_example_1_2)
        ≡ "[(1, 1), (2, 1), (3, 1), (4, 1), ]"

let test =
        assert
      :   print_C_list
            (Pair Natural Natural)
            ( λ(p : Pair Natural Natural) →
                "(${Natural/show p._1}, ${Natural/show p._2})"
            )
            (list_zip0 Natural (list_nil Natural) Natural list_example_1_2)
        ≡ "[]"

let test =
        assert
      :   print_C_list
            (Pair Natural Natural)
            ( λ(p : Pair Natural Natural) →
                "(${Natural/show p._1}, ${Natural/show p._2})"
            )
            (list_zip0 Natural list_example_1_2 Natural (list_nil Natural))
        ≡ "[]"

in  { S_list
    , S_nonempty_list
    , S_nonempty_tree
    , S_branch_data_tree
    , bimap_S_list
    , bimap_S_nonempty_list
    , bimap_S_nonempty_tree
    , bimap_S_branch_data_tree
    , depth_S_list
    , depth_S_nonempty_list
    , depth_S_nonempty_tree
    , depth_S_branch_data_tree
    , zip0_S_list
    , zip0_S_nonempty_list_truncating
    , zip0_S_nonempty_list_padding
    , zip0_S_nonempty_tree
    , zip0_S_branch_data_tree
    , bizip_S_list
    , bizip_S_nonempty_list
    , bizip_S_branch_data_tree
    , bizipK_S_list
    , bizipK_S_nonempty_list_truncating
    , bizipK_S_branch_data_tree
    , bizipK_via_bizip
    , bizipK_S_nonempty_list_padding
    , bizipK_S_nonempty_tree
    , list_build
    , list_nil
    , list_cons
    , list_example_1
    , list_example_1_2
    , list_example_1_2_3_4
    , print_C_list
    , nonempty_list_build
    , nonempty_list_leaf
    , nonempty_tree_build
    , nonempty_tree_leaf
    , branch_data_tree_nil
    , branch_data_tree_build
    , print_branch_data_tree
    , print_C_nonempty_list
    , print_C_nonempty_tree
    }
