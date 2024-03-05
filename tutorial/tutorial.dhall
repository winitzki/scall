let _ =
let f = λ(x : Natural) → λ(y : Natural) → x + y + 2
let id = λ(A : Type) → λ(x : A) → x
  in assert : f 10 (id Natural 20) === 32
    -- This evaluates to 32 of type Natural.

let _ =
let zip
  : ∀(a : Type) → Optional a → ∀(b : Type) → Optional b → Optional { _1 : a, _2 : b }
  = λ(a : Type) → λ(oa : Optional a) → λ(b : Type) → λ(ob : Optional b) →
    let Pair = { _1 : a, _2 : b }
    in
        merge { None = None Pair
              , Some = λ(x : a) →
                 merge { None = None Pair
                       , Some = λ(y : b) → Some { _1 = x, _2 = y }
                       } ob 
              } oa
              in assert : zip Natural (Some 1) Natural (Some 2) === Some { _1 = 1, _2 = 2 }


let f = λ(x : Natural) → x + 1

let _ =  ∀(a : Type) → ∀(b : Type) → (a → b) → List a → List b

let result = 
let List/map = https://prelude.dhall-lang.org/List/map
    in List/map Natural Natural (λ(x : Natural) → x + 1) [1, 2, 3]
        
let _ = assert : result    === [2, 3, 4]

let identity
  : ∀(A : Type) → ∀(x : A) → A 
  = λ(A : Type) → λ(x : A) → x

-- let compareTextValues : Text → Text → Bool
--   = λ(a : Text) → λ(b : Text) → 
--     let _ = assert : a === b  -- Error: Assertion failed
--       in True

let f = λ(a : Text) → "(" ++ a ++ ")"
let _ = assert : f "x" === "(x)"
let _ = assert : f "" === "()"

let Either = λ(a : Type) → λ(b : Type) → < Left : a | Right : b >

let f : ∀(x : Text) → Text
  = λ(x : Text) → "${x}..."


let absurd : ∀(A : Type) → < > → A
  = λ(A : Type) → λ(x : < >) → merge {=} x : A 

let before
  : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (a → b) → (b → c) → (a → c)
  = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : a → b) → λ(g : b → c) → λ(x : a) →
    g (f (x)) 

let after
  : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (b → c) → (a → b) → (a → c)
  = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : b → c) → λ(g : a → b) → λ(x : a) →
    f (g (x)) 

let flip
  : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (a → b → c) → (b → a → c)
  = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : a → b → c) → λ(x : b) → λ(y : a) →
    f y x

let curry
  : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → ({ _1 : a, _2 : b } → c) → (a → b → c)
  = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : { _1 : a, _2 : b } → c) → λ(x : a) → λ(y : b) →
    f { _1 = x, _2 = y }

let uncurry
  : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (a → b → c) → ({ _1 : a, _2 : b } → c)
  = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : a → b → c) → λ(p : { _1 : a, _2 : b }) →
    f p._1 p._2

let unsafeDiv : Natural → Natural → Natural =
  let Natural/lessThan = https://prelude.dhall-lang.org/Natural/lessThan
  let Accum = { result : Natural, sub : Natural, done : Bool }
    in λ(x : Natural) → λ(y : Natural) →
         let init : Accum = {result = 0, sub = x, done = False}
         let update : Accum → Accum = λ(acc: Accum) →
             if acc.done then acc
             else if Natural/lessThan acc.sub y then acc // {done = True}
             else acc // {result = acc.result + 1, sub = Natural/subtract y acc.sub}
         let r : Accum = Natural/fold x Accum update init
         in r.result

let _ =
  assert : unsafeDiv 3 2 === 1

let log2 : Natural → Natural = λ(n : Natural) →
  let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual
  let Accum = { b : Natural, log2 : Natural }
  let init = { b = 1, log2 = 0 } -- At all times, b == pow(2, log2).
  let update = λ(acc : Accum) →
     if lessThanEqual acc.b n
     then { b = acc.b * 2, log2 = acc.log2 + 1 }
     else acc 
  let result : Accum = Natural/fold n Accum update init
    in result.log2 

let _ = assert : log2 1 === 1
let _ = assert : log2 2 === 2
let _ = assert : log2 3 === 2
let _ = assert : log2 4 === 3

let gcd : Natural → Natural → Natural = λ(x : Natural) → λ(y : Natural) →
  let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual
  let Pair = { x : Natural, y : Natural }
  let swap : Pair → Pair = λ(currentPair : Pair) → { x = currentPair.y, y = currentPair.x }
  let sortPair : Pair → Pair = λ(currentPair : Pair) →
    if lessThanEqual currentPair.y currentPair.x then currentPair else swap currentPair
  let step : Pair → Pair = λ(currentPair : Pair) →
    currentPair // { x = Natural/subtract currentPair.y currentPair.x }
  let update : Pair → Pair = λ(currentPair : Pair) → sortPair (step currentPair)
  let init = sortPair { x = x, y = y }
  let max_iter = init.x
  let result : Pair = Natural/fold max_iter Pair update init
    in result.x

let _ = assert : gcd 4 1 === 1
let _ = assert : gcd 4 2 === 2
let _ = assert : gcd 4 3 === 1
let _ = assert : gcd 4 4 === 4
let _ = assert : gcd 5 4 === 1
let _ = assert : gcd 5 0 === 5
let _ = assert : gcd 15 12 === 3

let Nonzero : Natural → Type = λ(y : Natural) → if Natural/isZero y then < > else {}
let safeDiv = λ(x: Natural) → λ(y: Natural) → λ(_: Nonzero y) → unsafeDiv x y

let _ = assert : safeDiv 4 2 {=} === 2

let sqrt = λ(n: Natural) →
  let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual
  let stepDown = λ(r : Natural) → if (lessThanEqual (r * r) n) then r else Natural/subtract 1 r 
    in Natural/fold n Natural stepDown n 
 
let  _ = assert : sqrt 25 === 5

let  _ = assert : sqrt 26 === 5
let  _ = assert : sqrt 24 === 4
let  _ = assert : sqrt 1 === 1
let  _ = assert : sqrt 0 === 0

let _functors_examples =

    let F : Type → Type
    = λ(A : Type) → { x : A, y : A, t : Bool }
    let fmap
    : ∀(A : Type) → ∀(B : Type) → (A → B) → F A → F B
    = λ(A : Type) → λ(B : Type) → λ(f : A → B) → λ(fa : F A) →
        { x = f fa.x, y = f fa.y, t = fa.t }

let example : F Natural = { x = 1, y = 2, t = True }
let after_fmap : F Text = fmap Natural Text (λ(x : Natural) → if Natural/even x then "even" else "odd") example
let test = assert : after_fmap === { x = "odd", y = "even", t = True }

let G : Type → Type
  = λ(A : Type) → < Left : Text | Right : A >
let fmap
  : ∀(A : Type) → ∀(B : Type) → (A → B) → G A → G B
  = λ(A : Type) → λ(B : Type) → λ(f : A → B) → λ(ga : G A) →
    merge { Left = λ(t : Text) → (G B).Left t
          , Right = λ(x : A) → (G B).Right (f x)
          } ga

let P : Type → Type → Type
  = λ(A : Type) → λ(B : Type) → { x : A, y : A, z : B, t : Integer }


  let bimap
  : ∀(A : Type) → ∀(B : Type) → ∀(C : Type) → ∀(D : Type) → (A → C) → (B → D) → P A B → P C D
  = λ(A : Type) → λ(B : Type) → λ(C : Type) → λ(D : Type) → λ(f : A → C) → λ(g : B → D) → λ(pab : P A B) →
    { x = f pab.x, y = f pab.y, z = g pab.z, t = pab.t }

    let fmap1
  : ∀(A : Type) → ∀(C : Type) → ∀(D : Type) → (A → C) → P A D → P C D
  = λ(A : Type) → λ(C : Type) → λ(D : Type) → λ(f : A → C) →
    bimap A D C D f (identity D)

let fmap2
  : ∀(A : Type) → ∀(B : Type) → ∀(D : Type) → (B → D) → P A B → P A D
  = λ(A : Type) → λ(B : Type) → λ(D : Type) → λ(g : B → D) →
    bimap A B A D (identity A) g


    in True

let _typeclasses_examples =

let Monoid = λ(m : Type) → { empty : m, append : m → m → m }

let monoidBool : Monoid Bool = { empty = True, append = λ(x : Bool) → λ(y : Bool) → x && y }
let monoidNatural : Monoid Natural = { empty = 0, append = λ(x : Natural) → λ(y : Natural) → x + y }
let monoidText : Monoid Text = { empty = "", append = λ(x : Text) → λ(y : Text) → x ++ y }
let monoidList : ∀(a : Type) → Monoid (List a) = λ(a : Type) → { empty = [] : List a, append = λ(x : List a) → λ(y : List a) → x # y }

let foldMap
  : ∀(m : Type) → Monoid m → ∀(a : Type) → (a → m) → List a → m
  = λ(m : Type) → λ(monoid_m : Monoid m) → λ(a : Type) → λ(f : a → m) → λ(xs : List a) →
    List/fold a xs m (λ(x : a) → λ(y : m) → monoid_m.append (f x) y) monoid_m.empty


let Functor = λ(F : Type → Type) → { fmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b }
let functorList : Functor List = { fmap = https://prelude.dhall-lang.org/List/map }

    let F : Type → Type
    = λ(A : Type) → { x : A, y : A, t : Bool }

let G : Type → Type
  = λ(A : Type) → < Left : Text | Right : A >

let functorF : Functor F = { fmap = λ(A : Type) → λ(B : Type) → λ(f : A → B) → λ(fa : F A) →
    { x = f fa.x, y = f fa.y, t = fa.t }
  }
let functorG : Functor G = { fmap = λ(A : Type) → λ(B : Type) → λ(f : A → B) → λ(ga : G A) →
    merge { Left = λ(t : Text) → (G B).Left t
          , Right = λ(x : A) → (G B).Right (f x)
          } ga  
  }

let Monad = λ(F : Type → Type) →
  { pure : ∀(a : Type) → a → F a
  , bind : ∀(a : Type) → F a → ∀(b : Type) → (a → F b) → F b
  }

  let monadList : Monad List =
  let List/concatMap = https://prelude.dhall-lang.org/List/concatMap
  in
  { pure = λ(a : Type) → λ(x : a) → [ x ]
  , bind = λ(a : Type) → λ(fa : List a) → λ(b : Type) → λ(f : a → List b) →
    List/concatMap a b f fa
  }

  let monadJoin = λ(F : Type → Type) → λ(monadF : Monad F) → λ(a : Type) → λ(ffa : F (F a)) →
  monadF.bind (F a) ffa a (identity (F a))

  let List/join
  : ∀(a : Type) → List (List a) → List a
  = monadJoin List monadList 


let Semigroup = λ(m : Type) → { append : m → m → m }
let semigroupText : Semigroup Text = { append = λ(x : Text) → λ(y : Text) → x ++ y }

let Monoid = λ(m : Type) → Semigroup m //\\ { empty : m }
let monoidText : Monoid Text = semigroupText /\ { empty = "" } 

let Monad = λ(F : Type → Type) →
  Functor F //\\
      { pure : ∀(a : Type) → a → F a
      , bind : ∀(a : Type) → F a → ∀(b : Type) → (a → F b) → F b
      }


      let monadList : Monad List =
  let List/concatMap = https://prelude.dhall-lang.org/List/concatMap
  in functorList /\
      { pure = λ(a : Type) → λ(x : a) → [ x ]
      , bind = λ(a : Type) → λ(fa : List a) → λ(b : Type) → λ(f : a → List b) →
        List/concatMap a b f fa
      }

    in True

let _church_encoding_examples =

let ListInt = ∀(r : Type) → r → (Integer → r → r) → r

    let foldRight
  : ∀(r : Type) → ListInt → r → (Integer → r → r) → r
  = λ(r : Type) → λ(p : ListInt) → λ(init : r) → λ(update : Integer → r → r) →
    p r init update

    in True

in True