let L =
      ./ListIntCurry.dhall
        sha256:80e799ff90db01e3f2639cb8f12bc02e9fca2b7860fdd70dd9a16c943b910d8d

let length = 10000

let longList1
    : L.ListInt
    = Natural/fold length L.ListInt (L.cons +1) L.nil

in  { list = longList1, length }
