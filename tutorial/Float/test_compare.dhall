let C = ./compare.dhall

let Float/compare = C.Float/compare

let Compared = C.Compared

let Float/create = (./Type.dhall).Float/create

let _ =
        assert
      :   Float/compare (Float/create +123 +0) (Float/create +12 +1)
        ≡ Compared.Greater

let _ =
        assert
      :   Float/compare (Float/create +123 +0) (Float/create +12 +2)
        ≡ Compared.Less

let _ =
        assert
      :   Float/compare (Float/create +120 +0) (Float/create +12 +1)
        ≡ Compared.Equal

let _ =
        assert
      :   Float/compare (Float/create +123 -100) (Float/create +12 -99)
        ≡ Compared.Greater

let _ =
        assert
      :   Float/compare (Float/create +123 -100) (Float/create +12 -98)
        ≡ Compared.Less

let _ =
        assert
      :   Float/compare (Float/create +120 -100) (Float/create +12 -99)
        ≡ Compared.Equal

let _ =
        assert
      :   Float/compare (Float/create +120 -100) (Float/create -12 -99)
        ≡ Compared.Greater

let _ =
        assert
      :   Float/compare (Float/create -120 -100) (Float/create +12 -99)
        ≡ Compared.Less

in  True
