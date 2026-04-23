-- Okasaki's paper on insertion operation on sets. https://www.cs.tufts.edu/comp/150FP/archive/chris-okasaki/redblack99.pdf
-- Also see the "delete" operation https://matt.might.net/articles/red-black-delete/ https://www.classes.cs.uchicago.edu/archive/2021/winter/22300-1/lectures/RedBlackDelete/index.html
let Functor =
      λ(F : Type → Type) →
        { fmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b }

let LFix
    : (Type → Type) → Type
    = λ(F : Type → Type) → ∀(r : Type) → (F r → r) → r

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

let Pair = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }

let ParaAlg = λ(F : Type → Type) → λ(r : Type) → F (Pair (LFix F) r) → r

let paramorphism
    : ∀(F : Type → Type) → Functor F → LFix F → ∀(r : Type) → ParaAlg F r → r
    = λ(F : Type → Type) →
      λ(functorF : Functor F) →
      λ(c : LFix F) →
      λ(r : Type) →
      λ(fpfrr : ParaAlg F r) →
        let algPair
            : F (Pair (LFix F) r) → Pair (LFix F) r
            = λ(fp : F (Pair (LFix F) r)) →
                let resultr
                    : r
                    = fpfrr fp

                let resultc
                    : LFix F
                    = fix
                        F
                        functorF
                        ( functorF.fmap
                            (Pair (LFix F) r)
                            (LFix F)
                            (λ(p : Pair (LFix F) r) → p._1)
                            fp
                        )

                in  { _1 = resultc, _2 = resultr }

        let resultPair
            : Pair (LFix F) r
            = c (Pair (LFix F) r) algPair

        in  resultPair._2

let Color = < Red | Black >

let BranchT =
      λ(a : Type) →
      λ(r : Type) →
        { color : Color, left : r, value : a, right : r }

let RBTreeF = λ(a : Type) → λ(r : Type) → < E | T : BranchT a r >

let functorRBTreeF =
      λ(a : Type) →
        { fmap =
            λ(r : Type) →
            λ(s : Type) →
            λ(f : r → s) →
            λ(t : RBTreeF a r) →
              merge
                { E = (RBTreeF a s).E
                , T =
                    λ(branches : BranchT a r) →
                      (RBTreeF a s).T
                        (   branches
                          ⫽ { left = f branches.left, right = f branches.right }
                        )
                }
                t
        }

let RBTree = λ(a : Type) → LFix (RBTreeF a)

let Show = λ(a : Type) → { show : a → Text }

let showNatural = { show = Natural/show }

let showColor
    : Show Color
    = { show = λ(color : Color) → merge { Red = "Red", Black = "Black" } color }

let showRBTree
    : ∀(a : Type) → Show a → Show (RBTree a)
    = λ(a : Type) →
      λ(showA : Show a) →
        { show =
            λ(t : RBTree a) →
              t
                Text
                ( λ(t : RBTreeF a Text) →
                    merge
                      { E = "E"
                      , T =
                          λ(branches : BranchT a Text) →
                                "(T "
                            ++  showColor.show branches.color
                            ++  " "
                            ++  branches.left
                            ++  " "
                            ++  showA.show branches.value
                            ++  " "
                            ++  branches.right
                            ++  ")"
                      }
                      t
                )
        }

let unfixRBTree = λ(a : Type) → unfix (RBTreeF a) (functorRBTreeF a)

let OrdT = < LT | EQ | GT >

let Ord = λ(a : Type) → ∀(x : a) → ∀(y : a) → OrdT

let empty
    : ∀(a : Type) → RBTree a
    = λ(a : Type) → λ(r : Type) → λ(alg : RBTreeF a r → r) → alg (RBTreeF a r).E

let branch
    : ∀(a : Type) → BranchT a (RBTree a) → RBTree a
    = λ(a : Type) →
      λ(branches : BranchT a (RBTree a)) →
      λ(r : Type) →
      λ(alg : RBTreeF a r → r) →
        alg
          ( (RBTreeF a r).T
              (   branches
                ⫽ { left = branches.left r alg, right = branches.right r alg }
              )
          )

let _ = assert : (showRBTree Natural showNatural).show (empty Natural) ≡ "E"

let _ =
        assert
      :   (showRBTree Natural showNatural).show
            ( branch
                Natural
                { color = Color.Red
                , left = empty Natural
                , value = 1
                , right = empty Natural
                }
            )
        ≡ "(T Red E 1 E)"

let member
    : ∀(a : Type) → Ord a → ∀(x : a) → ∀(t : RBTree a) → Bool
    = λ(a : Type) →
      λ(compareA : Ord a) →
      λ(x : a) →
      λ(t : RBTree a) →
        t
          Bool
          ( λ(t : RBTreeF a Bool) →
              merge
                { E = False
                , T =
                    λ(branches : BranchT a Bool) →
                      merge
                        { LT = branches.left, EQ = True, GT = branches.right }
                        (compareA x branches.value)
                }
                t
          )

let Natural/lessThan = https://prelude.dhall-lang.org/Natural/lessThan

let Natural/equal = https://prelude.dhall-lang.org/Natural/equal

let ordNat
    : Ord Natural
    = λ(x : Natural) →
      λ(y : Natural) →
        if    Natural/lessThan x y
        then  OrdT.LT
        else  if Natural/equal x y
        then  OrdT.EQ
        else  OrdT.GT

let makeT
    : ∀(a : Type) → BranchT a (RBTree a) → RBTree a
    = λ(a : Type) → λ(branches : BranchT a (RBTree a)) → branch a branches

let rebalance
    {- Rebalance the top of the tree where two red nodes are adjacent. In Haskell:

    rebalance B (T R (T R a x b) y c) z d  = T R (T B a x b) y (T B c z d)
    rebalance B (T R a x (T R b y c)) z d  = T R (T B a x b) y (T B c z d)
    rebalance B a x (T R (T R b y c) z d)  = T R (T B a x b) y (T B c z d)
    rebalance B a x (T R b y (T R c z d))  = T R (T B a x b) y (T B c z d)
    rebalance color a x b = T color a x b

    In Dhall, this corresponds to rebalance { color, left, value, right } and the result is again a record of the same type.
     -}
    : ∀(a : Type) → BranchT a (RBTree a) → BranchT a (RBTree a)
    = λ(a : Type) →
      λ ( branches
        : { color : Color, left : RBTree a, value : a, right : RBTree a }
        ) →
        let MatchType =
            -- Possibly one of the patterns T R (T R a x b) y c - TRLeft, or T R a x (T R b y c) - TRRight.
              < NoMatch
              | TRLeft :
                  { innerLeft : RBTree a
                  , innerValue : a
                  , innerRight : RBTree a
                  , value : a
                  , right : RBTree a
                  }
              | TRRight :
                  { left : RBTree a
                  , value : a
                  , innerLeft : RBTree a
                  , innerValue : a
                  , innerRight : RBTree a
                  }
              >

        let matchSubtree
            -- Check if a subtree matches one of the patterns in MatchType.
            : RBTree a → MatchType
            = λ(t : RBTree a) →
                merge
                  { E = MatchType.NoMatch
                  , T =
                      λ(branches : BranchT a (RBTree a)) →
                        merge
                          { Black = MatchType.NoMatch
                          , Red =
                              merge
                                { E =
                                    merge
                                      { E = MatchType.NoMatch
                                      , T =
                                          λ ( innerRightBranches
                                            : BranchT a (RBTree a)
                                            ) →
                                            merge
                                              { Black = MatchType.NoMatch
                                              , Red =
                                                  MatchType.TRRight
                                                    { left = branches.left
                                                    , value = branches.value
                                                    , innerLeft =
                                                        innerRightBranches.left
                                                    , innerValue =
                                                        innerRightBranches.value
                                                    , innerRight =
                                                        innerRightBranches.right
                                                    }
                                              }
                                              innerRightBranches.color
                                      }
                                      (unfixRBTree a branches.right)
                                , T =
                                    λ ( innerLeftBranches
                                      : BranchT a (RBTree a)
                                      ) →
                                      merge
                                        { Black = MatchType.NoMatch
                                        , Red =
                                            MatchType.TRLeft
                                              { innerLeft =
                                                  innerLeftBranches.left
                                              , innerValue =
                                                  innerLeftBranches.value
                                              , innerRight =
                                                  innerLeftBranches.right
                                              , value = branches.value
                                              , right = branches.right
                                              }
                                        }
                                        innerLeftBranches.color
                                }
                                (unfixRBTree a branches.left)
                          }
                          branches.color
                  }
                  (unfixRBTree a t)

        in  merge
              { Red = branches
              , Black =
                  -- Check whether "l" in `T B l z d` matches as `l = T R (T R a x b) y c` -  "TRLeft" or as `l = T R a x (T R b y c)` - "TRRight".
                  merge
                    { NoMatch
                      -- Check whether "r" in `T B a x r` matches as `r = T R (T R b y c) z d` -  "TRLeft" or as `r = T R b y (T R c z d)` - "TRRight".
                      =
                        merge
                          { NoMatch = branches
                          , TRLeft
                            -- We have `r = T R (T R b y c) z d`. We need to return `R (T B a x b) y (T B c z d)`.
                            =
                              λ ( values
                                : { innerLeft : RBTree a
                                  , innerValue : a
                                  , innerRight : RBTree a
                                  , value : a
                                  , right : RBTree a
                                  }
                                ) →
                                { color = Color.Red
                                , left =
                                    makeT
                                      a
                                      { color = Color.Black
                                      , left = branches.left
                                      , value = branches.value
                                      , right = values.innerLeft
                                      }
                                , value = values.innerValue
                                , right =
                                    makeT
                                      a
                                      { color = Color.Black
                                      , left = values.innerRight
                                      , value = values.value
                                      , right = values.right
                                      }
                                }
                          , TRRight
                            -- We have `r = T R b y (T R c z d)`. We need to return `R (T B a x b) y (T B c z d)`.
                            =
                              λ ( values
                                : { left : RBTree a
                                  , value : a
                                  , innerLeft : RBTree a
                                  , innerValue : a
                                  , innerRight : RBTree a
                                  }
                                ) →
                                { color = Color.Red
                                , left =
                                    makeT
                                      a
                                      { color = Color.Black
                                      , left = branches.left
                                      , value = branches.value
                                      , right = values.left
                                      }
                                , value = values.value
                                , right =
                                    makeT
                                      a
                                      { color = Color.Black
                                      , left = values.innerLeft
                                      , value = values.innerValue
                                      , right = values.innerRight
                                      }
                                }
                          }
                          (matchSubtree branches.right)
                    , TRLeft
                      -- We have `l = T R (T R a x b) y c`. We need to return `R (T B a x b) y (T B c z d)`.
                      =
                        λ ( values
                          : { innerLeft : RBTree a
                            , innerValue : a
                            , innerRight : RBTree a
                            , value : a
                            , right : RBTree a
                            }
                          ) →
                          { color = Color.Red
                          , left =
                              makeT
                                a
                                { color = Color.Black
                                , left = values.innerLeft
                                , value = values.innerValue
                                , right = values.innerRight
                                }
                          , value = values.value
                          , right =
                              makeT
                                a
                                { color = Color.Black
                                , left = values.right
                                , value = branches.value
                                , right = branches.right
                                }
                          }
                    , TRRight
                      -- We have `l = T R a x (T R b y c)`. We need to return `R (T B a x b) y (T B c z d)`.
                      =
                        λ ( values
                          : { left : RBTree a
                            , value : a
                            , innerLeft : RBTree a
                            , innerValue : a
                            , innerRight : RBTree a
                            }
                          ) →
                          { color = Color.Red
                          , left =
                              makeT
                                a
                                { color = Color.Black
                                , left = values.left
                                , value = values.value
                                , right = values.innerLeft
                                }
                          , value = values.innerValue
                          , right =
                              makeT
                                a
                                { color = Color.Black
                                , left = values.innerRight
                                , value = branches.value
                                , right = branches.right
                                }
                          }
                    }
                    (matchSubtree branches.left)
              }
              branches.color

let insert
    : ∀(a : Type) → Ord a → ∀(x : a) → ∀(t : RBTree a) → RBTree a
    = λ(a : Type) →
      λ(compareA : Ord a) →
      λ(x : a) →
      λ(t : RBTree a) →
        let Ba = BranchT a

        let Ta = RBTree a

        let makeBlack
            : Ba Ta → Ta
            = λ(branches : Ba Ta) →
                branch a (branches ⫽ { color = Color.Black })

        let ins
            -- Insert x into the given tree s; will always return a T node, so we make it return a Ba Ta.
            : Ta → Ba Ta
            = λ(s : Ta) →
                let palg
                    : ParaAlg (RBTreeF a) (Ba Ta)
                    = λ(b : RBTreeF a (Pair Ta (Ba Ta))) →
                        merge
                          { E =
                            { color = Color.Red
                            , left = empty a
                            , value = x
                            , right = empty a
                            }
                          , T =
                              λ ( branches
                                : { color : Color
                                  , left : Pair Ta (Ba Ta)
                                  , value : a
                                  , right : Pair Ta (Ba Ta)
                                  }
                                ) →
                                merge
                                  { LT =
                                      rebalance
                                        a
                                        (   branches
                                          ⫽ { left = makeT a branches.left._2
                                            , right = branches.right._1
                                            }
                                        )
                                  , EQ =
                                        branches
                                      ⫽ { left = branches.left._1
                                        , right = branches.right._1
                                        }
                                  , GT =
                                      rebalance
                                        a
                                        (   branches
                                          ⫽ { left = branches.left._1
                                            , right = makeT a branches.right._2
                                            }
                                        )
                                  }
                                  (compareA x branches.value)
                          }
                          b

                in  paramorphism (RBTreeF a) (functorRBTreeF a) s (Ba Ta) palg

        in  makeBlack (ins t)

let Set = RBTree

let Set/insert = insert

let Set/empty = empty

let Set/member = member

let Set/show = showRBTree

let Set/fromList
    : ∀(a : Type) → Ord a → List a → Set a
    = λ(a : Type) →
      λ(ordA : Ord a) →
      λ(xs : List a) →
        List/fold
          a
          xs
          (Set a)
          (λ(x : a) → λ(prev : Set a) → Set/insert a ordA x prev)
          (empty a)

let Set/toList
    : ∀(a : Type) → Set a → List a
    = λ(a : Type) →
      λ(xs : Set a) →
        xs
          (List a)
          ( λ(s : RBTreeF a (List a)) →
              merge
                { E = [] : List a
                , T =
                    λ(branches : BranchT a (List a)) →
                      branches.left # [ branches.value ] # branches.right
                }
                s
          )

in  { Set
    , Show
    , showNatural
    , ordNat
    , Ord
    , Set/fromList
    , Set/toList
    , Set/member
    , Set/insert
    , Set/show
    , Set/empty
    , branch
    , rebalance
    , unfixRBTree
    }
