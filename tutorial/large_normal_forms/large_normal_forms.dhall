let Integer/positive = https://prelude.dhall-lang.org/Integer/positive

let Integer/abs = https://prelude.dhall-lang.org/Integer/abs

let Integer/add = https://prelude.dhall-lang.org/Integer/add

let Integer/subtract = https://prelude.dhall-lang.org/Integer/subtract

let TorsorType = { x : Natural, y : Natural }

let torsor1
    : Integer → TorsorType
    = λ(i : Integer) →
        if    Integer/positive i
        then  { x = Integer/clamp i, y = 0 }
        else  { x = 0, y = Integer/abs i }

let torsor2
    : Integer → Integer → TorsorType
    =
      -- Normal form is 15 KB.
      λ(a : Integer) → λ(b : Integer) → torsor1 (Integer/subtract a b)

let torsor4
    : Integer → Integer → Integer → Integer → TorsorType
    =
      -- Normal form is 790 KB. Growth is 50x compared to torsor2.
      λ(i : Integer) →
      λ(j : Integer) →
      λ(k : Integer) →
      λ(l : Integer) →
        torsor2 (Integer/add i j) (Integer/add k l)

let torsor8
    : Integer →
      Integer →
      Integer →
      Integer →
      Integer →
      Integer →
      Integer →
      Integer →
        TorsorType
    =
      -- Normal form is 22.8 MB. Growth is 28x compared to torsor4.
      λ(i1 : Integer) →
      λ(j1 : Integer) →
      λ(k1 : Integer) →
      λ(l1 : Integer) →
      λ(i2 : Integer) →
      λ(j2 : Integer) →
      λ(k2 : Integer) →
      λ(l2 : Integer) →
        torsor4
          (Integer/add i1 i2)
          (Integer/add j1 j2)
          (Integer/add k1 k2)
          (Integer/add l1 l2)

in  torsor8
