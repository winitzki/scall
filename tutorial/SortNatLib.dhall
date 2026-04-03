let ListNat = ∀(r : Type) → r → (Natural → r → r) → r

let lessThan = https://prelude.dhall-lang.org/Natural/lessThan

let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual

let List/iterate = https://prelude.dhall-lang.org/List/iterate

let nilNat
    : ListNat
    = λ(r : Type) → λ(nil : r) → λ(cons : Natural → r → r) → nil

let consNat
    : Natural → ListNat → ListNat
    = λ(n : Natural) →
      λ(c : ListNat) →
      λ(r : Type) →
      λ(a1 : r) →
      λ(a2 : Natural → r → r) →
        a2 n (c r a1 a2)

let concatNat
    : ListNat → ListNat → ListNat
    = λ(left : ListNat) →
      λ(right : ListNat) →
        left
          ListNat
          right
          (λ(head : Natural) → λ(tail : ListNat) → consNat head tail)

let ListNat/fromList
    : List Natural → ListNat
    = λ(list : List Natural) → List/fold Natural list ListNat consNat nilNat

let ListNat/build
    : ListNat → ListNat
    = λ(list : ListNat) → list

let ListNat/toList
    : ListNat → List Natural
    = λ(list : ListNat) →
        list
          (List Natural)
          ([] : List Natural)
          (λ(head : Natural) → λ(tail : List Natural) → [ head ] # tail)

let PairLists = { _1 : ListNat, _2 : ListNat }

let -- Given limit : Natural and a sorted list, return separately the initial fragment that is not above the limit and the rest.
    -- This function should be O(1), need to validate that.
    ListNat/partitionSorted
    : Natural → ListNat → PairLists
    = λ(limit : Natural) →
      λ(list : ListNat) →
        list
          PairLists
          { _1 = nilNat, _2 = nilNat }
          ( λ(head : Natural) →
            λ(tail : PairLists) →
              if    lessThan head limit
              then  { _1 = consNat head tail._1, _2 = tail._2 }
              else  { _1 = nilNat, _2 = consNat head tail._2 }
          )

let mergeSorted
    : ListNat → ListNat → ListNat
    = λ(left : ListNat) →
      λ(right : ListNat) →
        let State = { result : ListNat, rightRest : ListNat }

        let initialState = { result = nilNat, rightRest = right }

        let updateState
            : Natural → State → State
            = λ(head : Natural) →
              λ(tail : State) →
                let partitioned =
                    -- We are merging [ ..., head1, tail1 ] with [ rightRest, tail2 ] where we already   merged tail1 & tail2 into result, and we have head2 = rightRest.
                    -- Now we partitioned rightRest into two parts, _1 and _2. We know that head1 is between _1 and _2. So, the picture is:
                    -- [ ..., head1  ] and [ _1, _2, result ]. The new result must be head1, _2, result. The new rightRest is _1.
                      ListNat/partitionSorted head tail.rightRest

                in  { result =
                        consNat head (concatNat partitioned._2 tail.result)
                    , rightRest = partitioned._1
                    }

        let run = left State initialState updateState

        in  concatNat run.rightRest run.result

let testMergeSorted =
      λ(left : List Natural) →
      λ(right : List Natural) →
      λ(expected : List Natural) →
          ListNat/toList
            (mergeSorted (ListNat/fromList left) (ListNat/fromList right))
        ≡ expected

let _ =
        assert
      :   concatNat (consNat 1 (consNat 2 nilNat)) (consNat 3 nilNat)
        ≡ consNat 1 (consNat 2 (consNat 3 nilNat))

let _ =
        assert
      : ListNat/fromList [ 1, 2, 3 ] ≡ consNat 1 (consNat 2 (consNat 3 nilNat))

let _ = assert : ListNat/fromList ([] : List Natural) ≡ nilNat

let _ =
        assert
      : ListNat/toList (consNat 1 (consNat 2 (consNat 3 nilNat))) ≡ [ 1, 2, 3 ]

let _ = assert : ListNat/toList nilNat ≡ ([] : List Natural)

let _ =
        assert
      :   ListNat/toList
            (ListNat/partitionSorted 3 (ListNat/fromList [ 1, 2, 3, 4, 5 ]))._1
        ≡ [ 1, 2 ]

let _ =
        assert
      :   ListNat/toList
            (ListNat/partitionSorted 3 (ListNat/fromList [ 1, 2, 3, 4, 5 ]))._2
        ≡ [ 3, 4, 5 ]

let _ = assert : testMergeSorted [ 5 ] [ 6 ] [ 5, 6 ]

let _ = assert : testMergeSorted [ 6 ] [ 5 ] [ 5, 6 ]

let _ = assert : testMergeSorted [ 6 ] [ 6 ] [ 6, 6 ]

let _ = assert : testMergeSorted ([] : List Natural) [ 1, 2 ] [ 1, 2 ]

let _ = assert : testMergeSorted [ 1, 2 ] ([] : List Natural) [ 1, 2 ]

let _ = assert : testMergeSorted [ 1, 2 ] [ 6 ] [ 1, 2, 6 ]

let _ = assert : testMergeSorted [ 1, 2 ] [ 3, 4 ] [ 1, 2, 3, 4 ]

let _ = assert : testMergeSorted [ 1, 2 ] [ 1 ] [ 1, 1, 2 ]

let _ = assert : testMergeSorted [ 1, 2 ] [ 1, 2 ] [ 1, 1, 2, 2 ]

let _ = assert : testMergeSorted [ 1, 2 ] [ 1, 2, 3 ] [ 1, 1, 2, 2, 3 ]

let _ =
        assert
      : testMergeSorted [ 1, 4, 6, 7 ] [ 2, 3, 5 ] [ 1, 2, 3, 4, 5, 6, 7 ]

let _ =
        assert
      : testMergeSorted
          [ 1, 2, 2, 6, 7, 8 ]
          [ 2, 3, 4, 5, 6, 7 ]
          [ 1, 2, 2, 2, 3, 4, 5, 6, 6, 7, 7, 8 ]

let makeListNat
    : Natural → Natural → Natural → ListNat
    = λ(size : Natural) →
      λ(init : Natural) →
      λ(delta : Natural) →
        ( Natural/fold
            size
            { result : ListNat, index : Natural }
            ( λ(acc : { result : ListNat, index : Natural }) →
                { result = consNat acc.index acc.result
                , index = Natural/subtract delta acc.index
                }
            )
            { result = nilNat, index = init + Natural/subtract 1 size * delta }
        ).result

let _ = assert : ListNat/toList (makeListNat 5 0 2) ≡ [ 0, 2, 4, 6, 8 ]

let testBinaryFunctionOnListNat =
      λ(iterations : Natural) →
      λ(f : ListNat → ListNat → ListNat) →
      λ(list0 : ListNat) →
      λ(list1 : ListNat) →
      λ(expected : ListNat) →
        let expected_result =
              List/iterate iterations ListNat (λ(_ : ListNat) → expected) nilNat

        let obtained_result =
              List/iterate
                iterations
                ListNat
                (λ(_ : ListNat) → f list0 list1)
                nilNat

        in  expected_result ≡ obtained_result

let ListNat/length
    : ListNat → Natural
    = λ(l : ListNat) → l Natural 0 (λ(_ : Natural) → λ(n : Natural) → n + 1)

let ListNat/uncons
    : ListNat → Optional { head : Natural, tail : ListNat }
    = λ(l : ListNat) →
        l
          (Optional { head : Natural, tail : ListNat })
          (None { head : Natural, tail : ListNat })
          ( λ(h : Natural) →
            λ(u : Optional { head : Natural, tail : ListNat }) →
              merge
                { None = Some { head = h, tail = nilNat }
                , Some =
                    λ(p : { head : Natural, tail : ListNat }) →
                      Some { head = h, tail = consNat p.head p.tail }
                }
                u
          )

let
    {-  `ListNat/reverse` reverses the list in one catamorphism.

        Here `r` is instantiated as `ListNat`. For each `cons`, the second parameter `revTail` is *not*
        the tail as a sublist; it is the result of folding the tail with the same algebra—i.e. the
        reversal of the tail, already computed. Appending the current head after that reversed suffix
        (`concatNat revTail (consNat h nilNat)`) is exactly the usual `reverse = foldr (λ x xs → xs ++ [x]) []`.

        Again there is no recursive function name: the list value drives the “iteration”.
    -}
    ListNat/reverse
    : ListNat → ListNat
    = λ(l : ListNat) →
        l
          ListNat
          nilNat
          ( λ(h : Natural) →
            λ(revTail : ListNat) →
              concatNat revTail (consNat h nilNat)
          )

let ListNat/mergeSorted
    : ListNat → ListNat → ListNat
    =
      {-  Merge of two sorted Church lists cannot be written as a single self-referential fold
          algebra in Dhall (no recursive `let`, no fixpoint). The usual two-pointer merge is
          therefore simulated as a finite state machine: each transition inspects the fronts of
          `leftRest` and `rightRest` via `ListNat/uncons`, pushes the smaller head onto `outRev`
          (which accumulates the merged stream in *reverse* order), and stops early by setting
          `done` once remainders are flushed.

          Iteration count `length left + length right` is enough steps: every step consumes at
          least one element until `finish` runs; after `finish`, `step` is a no-op. The outer
          loop uses `Natural/fold`, which is not a recursive Dhall function but bounded iteration
          on a `Natural`.
      -} λ(left : ListNat) →
         λ(right : ListNat) →
           let State
               : Type
               = {-  `leftRest` / `rightRest`: suffixes still to merge (each is a full `ListNat` value).

                     `outRev`: merged elements so far, stored with the most recently merged element at
                     the “head” side of this Church list (we only ever prepend via `consNat`), so the
                     logical merge order is the reverse of `outRev`.

                     `done`: once `True`, `step` leaves the state unchanged so spare `Natural/fold`
                     iterations do not corrupt the result after we have appended all remaining elements
                     in `finish`.
                 -}
                 { leftRest : ListNat
                 , rightRest : ListNat
                 , outRev : ListNat
                 , done : Bool
                 }

           let finish
               : State → State
               = {-  Endgame when at least one side has no head in the main loop: there is nothing left
                     to compare pairwise, so we append whatever is still on the left and right into the
                     output stream.

                     Remaining runs are reversed and concatenated onto `outRev` in the same way as
                     `prependReversed` chains in `NaturalMergeSort.dhall`: conceptually
                     `reverse(leftRest) ++ reverse(rightRest) ++ outRev` for built-in lists. That places
                     the still-sorted leftovers in correct order relative to the part already merged into
                     `outRev`. We then clear both rests and mark `done` so later steps are idempotent.
                 -}
                 λ(state : State) →
                   { leftRest = nilNat
                   , rightRest = nilNat
                   , outRev =
                       concatNat
                         (ListNat/reverse state.leftRest)
                         ( concatNat
                             (ListNat/reverse state.rightRest)
                             state.outRev
                         )
                   , done = True
                   }

           let step
               : State → State
               = {-  One transition of the merge automaton (pure function, no recursion).

                     • If `done`, return the state unchanged.

                     • Otherwise `uncons` the left list. If it is empty, `finish` flushes the right (and
                       any prior `outRev`) in sorted order relative to the merge invariant.

                     • If the left has a head, `uncons` the right. If empty, symmetric `finish`.

                     • If both have heads, compare them with `lessThan`. The strictly smaller head is
                       prepended to `outRev`, and only that side’s `Rest` field advances. Equality is
                       treated in the `else` branch (take from the left), which yields a stable merge
                       relative to left-first ordering when keys tie.

                     Prepending to `outRev` is why the final result applies `ListNat/reverse` once at
                     the end of `mergeSorted`.
                 -}
                 λ(state : State) →
                   if    state.done
                   then  state
                   else  merge
                           { None = finish state
                           , Some =
                               λ ( leftCell
                                 : { head : Natural, tail : ListNat }
                                 ) →
                                 merge
                                   { None = finish state
                                   , Some =
                                       λ ( rightCell
                                         : { head : Natural, tail : ListNat }
                                         ) →
                                         if    lessThan
                                                 rightCell.head
                                                 leftCell.head
                                         then    state
                                               ⫽ { rightRest = rightCell.tail
                                                 , outRev =
                                                     consNat
                                                       rightCell.head
                                                       state.outRev
                                                 }
                                         else    state
                                               ⫽ { leftRest = leftCell.tail
                                                 , outRev =
                                                     consNat
                                                       leftCell.head
                                                       state.outRev
                                                 }
                                   }
                                   (ListNat/uncons state.rightRest)
                           }
                           (ListNat/uncons state.leftRest)

           let
               {-  Upper bound on how many `step` applications we need: each original element is removed
                   from `leftRest` or `rightRest` at most once before `finish` clears the rest in one shot.
                   Extra iterations (if any) leave `done = True` and `step` becomes the identity on state.
               -}
               iterations =
                 ListNat/length left + ListNat/length right

           let
               {-  Run the transition function `iterations` times from the initial configuration that holds
                   the full input lists and an empty `outRev`. The result’s `outRev` is still reversed
                   merge order, so the expression below reverses it once to obtain ascending sorted order.
               -}
               finalState
               : State
               = Natural/fold
                   iterations
                   State
                   step
                   { leftRest = left
                   , rightRest = right
                   , outRev = nilNat
                   , done = False
                   }

           in  ListNat/reverse finalState.outRev

let _ =
        assert
      :   ListNat/mergeSorted
            (ListNat/fromList [ 1, 3, 5 ])
            (ListNat/fromList [ 2, 4, 6 ])
        ≡ ListNat/fromList [ 1, 2, 3, 4, 5, 6 ]

in  { ListNat
    , nilNat
    , consNat
    , concatNat
    , ListNat/fromList
    , ListNat/build
    , ListNat/toList
    , PairLists
    , ListNat/partitionSorted
    , mergeSorted
    , testMergeSorted
    , makeListNat
    , List/iterate
    , testBinaryFunctionOnListNat
    , ListNat/length
    , ListNat/uncons
    , ListNat/reverse
    , ListNat/mergeSorted
    }
