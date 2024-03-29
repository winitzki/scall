let List/replicate =
      https://prelude.dhall-lang.org/List/replicate
        sha256:d4250b45278f2d692302489ac3e78280acb238d27541c837ce46911ff3baa347

let Natural/lessThan = https://prelude.dhall-lang.org/Natural/lessThan

let Natural/min =
      https://prelude.dhall-lang.org/Natural/min
        sha256:f25f9c462e4dbf0eb15f9ff6ac840c6e9c82255a7f4f2ab408bdab338e028710

let List/take =
      https://prelude.dhall-lang.org/List/take
        sha256:b3e08ee8c3a5bf3d8ccee6b2b2008fbb8e51e7373aef6f1af67ad10078c9fbfa

let Prelude/List/zip =
      https://prelude.dhall-lang.org/List/zip
        sha256:85ed955eabf3998767f4ad2a28e57d40cd4c68a95519d79e9b622f1d26d979da

let zipEqualLength
    : ∀(a : Type) → List a → ∀(b : Type) → List b → List { _1 : a, _2 : b }
    = λ(a : Type) →
      λ(small : List a) →
      λ(b : Type) →
      λ(large : List b) →
        let target_type = { _1 : a, _2 : b }

        let builder
            : ∀(list : Type) → (target_type → list → list) → list → list
            = λ(list : Type) →
              λ(cons : target_type → list → list) →
              λ(nil : list) →
                let Accum = { other : List b, prev : list }

                let init
                    : Accum
                    = { other = large, prev = nil }

                let reduce
                    : a → Accum → Accum
                    = λ(x : a) →
                      λ(prev : Accum) →
                        { other =
                            List/take
                              (Natural/subtract 1 (List/length b prev.other))
                              b
                              prev.other
                        , prev =
                            merge
                              { None = nil
                              , Some =
                                  λ(y : b) → cons { _1 = x, _2 = y } prev.prev
                              }
                              (List/last b prev.other)
                        }

                in  (List/fold a small Accum reduce init).prev

        in  List/build target_type builder

let List/zip
    : ∀(a : Type) → List a → ∀(b : Type) → List b → List { _1 : a, _2 : b }
    = λ(a : Type) →
      λ(xs : List a) →
      λ(b : Type) →
      λ(ys : List b) →
        if    Natural/lessThan (List/length a xs) (List/length b ys)
        then  zipEqualLength a xs b (List/take (List/length a xs) b ys)
        else  zipEqualLength a (List/take (List/length b ys) a xs) b ys

let small = env:small

let large = env:large

let min = Natural/min small large

let smallList = List/replicate small Natural 1

let largeList = List/replicate large Natural 1

let _ =
        assert
      :   List/zip Natural [ 1, 2 ] Natural [ 10, 20, 30 ]
        ≡ [ { _1 = 1, _2 = 10 }, { _1 = 2, _2 = 20 } ]

let _ =
        assert
      :   List/zip Natural [ 1, 2, 3 ] Natural [ 10, 20 ]
        ≡ [ { _1 = 1, _2 = 10 }, { _1 = 2, _2 = 20 } ]

let _ =
        assert
      :   List/length
            { _1 : Natural, _2 : Natural }
            (Prelude/List/zip Natural smallList Natural largeList)
        ≡ min

in  min
