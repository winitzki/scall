let List/map
    : ∀(a : Type) → ∀(b : Type) → (a → b) → List a → List b
    = λ(a : Type) →
      λ(b : Type) →
      λ(f : a → b) →
      λ(xs : List a) →
        List/build
          b
          ( λ(list : Type) →
            λ(cons : b → list → list) →
              List/fold a xs list (λ(x : a) → cons (f x))
          )

let Natural/lessThanEqual
    : Natural → Natural → Bool
    = λ(x : Natural) → λ(y : Natural) → Natural/isZero (Natural/subtract y x)

let Natural/greaterThanEqual
    : Natural → Natural → Bool
    = λ(x : Natural) → λ(y : Natural) → Natural/lessThanEqual y x

let drop
    : ∀(n : Natural) → ∀(a : Type) → List a → List a
    = λ(n : Natural) →
      λ(a : Type) →
      λ(xs : List a) →
        List/fold
          { index : Natural, value : a }
          (List/indexed a xs)
          (List a)
          ( λ(x : { index : Natural, value : a }) →
            λ(xs : List a) →
              if    Natural/greaterThanEqual x.index n
              then  [ x.value ] # xs
              else  xs
          )
          ([] : List a)

let List/index
    : Natural → ∀(a : Type) → List a → Optional a
    = λ(n : Natural) → λ(a : Type) → λ(xs : List a) → List/head a (drop n a xs)

let Optional/getOrElse
    : ∀(a : Type) → a → Optional a → a
    = λ(a : Type) →
      λ(default : a) →
      λ(o : Optional a) →
        merge { Some = λ(x : a) → x, None = default } o

let toUppercase
    : Text → Text
    = List/fold
        (Text → Text)
        [ Text/replace "a" "A"
        , Text/replace "b" "B"
        , Text/replace "c" "C"
        , Text/replace "d" "D"
        , Text/replace "e" "E"
        , Text/replace "f" "F"
        , Text/replace "g" "G"
        , Text/replace "h" "H"
        , Text/replace "i" "I"
        , Text/replace "j" "J"
        , Text/replace "k" "K"
        , Text/replace "l" "L"
        , Text/replace "m" "M"
        , Text/replace "n" "N"
        , Text/replace "o" "O"
        , Text/replace "p" "P"
        , Text/replace "q" "Q"
        , Text/replace "r" "R"
        , Text/replace "s" "S"
        , Text/replace "t" "T"
        , Text/replace "u" "U"
        , Text/replace "v" "V"
        , Text/replace "w" "W"
        , Text/replace "x" "X"
        , Text/replace "y" "Y"
        , Text/replace "z" "Z"
        ]
        Text
        (λ(replacement : Text → Text) → replacement)

in  { List/map
    , Natural/greaterThanEqual
    , toUppercase
    , Optional/getOrElse
    , List/index
    }
