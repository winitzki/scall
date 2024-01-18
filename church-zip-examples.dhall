-- standard lists, non-empty lists, non-empty binary trees, branch-data binary trees
let Ch = ./Church.dhall

let ChurchNaturals = ./ChurchNaturals.dhall

let ChurchNatural = ChurchNaturals.ChurchNatural

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

let C_list =  Ch.T1 S_list

let S_nonempty_list = λ(a : Type) → λ(r : Type) → Either a (Pair a r)

let C_nonempty_list =  Ch.T1 S_nonempty_list
let S_nonempty_tree = λ(a : Type) → λ(r : Type) → Either a (Pair r r)
let C_nonempty_tree =  Ch.T1 S_nonempty_tree
let S_branch_data_tree = λ(a : Type) → λ(r : Type) → Either {} (Triple a r r)
let C_branch_data_tree =  Ch.T1 S_branch_data_tree
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
-- define bizipK_S_list via bizip_S_list and also for branch_data_tree because there is no other implementation.
let bizipK_S_list
    : B.BizipK S_list 
    = let S = S_list

      in  
       λ(C : B.Functor) →
        \(fmapC : B.Map C) →
        \(a : Type) →
        \(b : Type) →
        let Sab = S (Pair a b) (Pair (C a) (C b))
        in \(saca: S a (C a)) →
        \(sbcb: S b (C b)) →
                merge
                    { Left = λ(x : {}) → Sab.Left x
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
            
           : Sab
            --       : Sabpq


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
    }
