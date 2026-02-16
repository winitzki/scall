let C =
      ./compare.dhall
        sha256:da183a6c2829465ad3e4b2dffdbe499040458ce8ff8f16b2a665cf9cb6977637

let Float/compare = C.Float/compare

let Compared = C.Compared

let Float/create =
      ( ./Type.dhall
          sha256:eb9b0c4b594668945020e2dc430bc312b998f90ff2b8f6ba2a861c2836c144c5
      ).Float/create

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
