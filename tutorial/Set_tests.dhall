let S =
      ./Set.dhall
        sha256:c4b7d4e9db3c41df015864b22ac5b15a48b754d3e0ea422fb29ebcbac8796807

let Set/toList = S.Set/toList

let Set = S.Set

let Set/fromList = S.Set/fromList

let Set/member = S.Set/member

let Set/insert = S.Set/insert

let Set/show = S.Set/show

let Set/empty = S.Set/empty

let Show = S.Show

let showNatural = S.showNatural

let ordNat = S.ordNat

let Ord = S.Ord

let example0 = Set/empty Natural

let _ = assert : Set/member Natural ordNat 0 example0 ≡ False

let example1 = Set/insert Natural ordNat 1 example0

let _ = assert : Set/member Natural ordNat 0 example1 ≡ False

let _ = assert : Set/member Natural ordNat 1 example1 ≡ True

let _ = assert : Set/member Natural ordNat 2 example1 ≡ False

let _ = assert : Set/member Natural ordNat 3 example1 ≡ False

let _ = assert : Set/member Natural ordNat 4 example1 ≡ False

let example12 = Set/insert Natural ordNat 2 example1

let _ = assert : Set/member Natural ordNat 0 example12 ≡ False

let _ = assert : Set/member Natural ordNat 1 example12 ≡ True

let _ = assert : Set/member Natural ordNat 2 example12 ≡ True

let _ = assert : Set/member Natural ordNat 3 example12 ≡ False

let _ = assert : Set/member Natural ordNat 4 example12 ≡ False

let example31 =
      Set/insert Natural ordNat 1 (Set/insert Natural ordNat 3 example0)

let _ = assert : Set/member Natural ordNat 1 example31 ≡ True

let _ = assert : Set/member Natural ordNat 3 example31 ≡ True

let example13 =
      Set/insert Natural ordNat 1 (Set/insert Natural ordNat 3 example0)

let _ = assert : Set/member Natural ordNat 0 example13 ≡ False

let _ = assert : Set/member Natural ordNat 1 example13 ≡ True

let _ = assert : Set/member Natural ordNat 2 example13 ≡ False

let _ = assert : Set/member Natural ordNat 3 example13 ≡ True

let _ = assert : Set/member Natural ordNat 4 example13 ≡ False

let _ =
        assert
      :   (Set/show Natural showNatural).show example12
        ≡ "(T Black E 1 (T Red E 2 E))"

let _ =
        assert
      :   (Set/show Natural showNatural).show example13
        ≡ "(T Black (T Red E 1 E) 3 E)"

let _ =
        assert
      :   (Set/show Natural showNatural).show example31
        ≡ "(T Black (T Red E 1 E) 3 E)"

let testRoundtrip =
      λ(x : List Natural) →
      λ(y : List Natural) →
        Set/toList Natural (Set/fromList Natural ordNat x) ≡ y

let _ = assert : testRoundtrip [ 1, 2, 3 ] [ 1, 2, 3 ]

let _ = assert : testRoundtrip [ 3, 2, 1 ] [ 1, 2, 3 ]

let _ =
        assert
      :   (Set/show Natural showNatural).show
            (Set/fromList Natural ordNat [ 3, 2, 1 ])
        ≡ "(T Black (T Black E 1 E) 2 (T Black E 3 E))"

let _ =
        assert
      : testRoundtrip
          [ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ]
          [ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ]

let _
      -- This takes a longer time.
      =
        assert
      : testRoundtrip
          [ 3, 1, 9, 3, 2, 3, 4, 8, 10, 7, 4, 5, 6, 8 ]
          [ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ]

in  True
