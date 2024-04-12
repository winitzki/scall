let _ =
      let f = λ(x : Natural) → λ(y : Natural) → x + y + 2

      let id = λ(A : Type) → λ(x : A) → x

      in  assert : f 10 (id Natural 20) ≡ 32

let _ =
      let zip
          : ∀(a : Type) →
            Optional a →
            ∀(b : Type) →
            Optional b →
              Optional { _1 : a, _2 : b }
          = λ(a : Type) →
            λ(oa : Optional a) →
            λ(b : Type) →
            λ(ob : Optional b) →
              let Pair = { _1 : a, _2 : b }

              in  merge
                    { None = None Pair
                    , Some =
                        λ(x : a) →
                          merge
                            { None = None Pair
                            , Some = λ(y : b) → Some { _1 = x, _2 = y }
                            }
                            ob
                    }
                    oa

      in    assert
          : zip Natural (Some 1) Natural (Some 2) ≡ Some { _1 = 1, _2 = 2 }

let f = λ(x : Natural) → x + 1

let _ = ∀(a : Type) → ∀(b : Type) → (a → b) → List a → List b

let fmapF_type =
      λ(F : Type → Type) → ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b

let result =
      let List/map = https://prelude.dhall-lang.org/List/map

      in  List/map Natural Natural (λ(x : Natural) → x + 1) [ 1, 2, 3 ]

let _ = assert : result ≡ [ 2, 3, 4 ]

let identity
    : ∀(A : Type) → ∀(x : A) → A
    = λ(A : Type) → λ(x : A) → x

let f = λ(a : Text) → "(" ++ a ++ ")"

let _ = assert : f "x" ≡ "(x)"

let _ = assert : f "" ≡ "()"

let Either = λ(a : Type) → λ(b : Type) → < Left : a | Right : b >

let f
    : ∀(x : Text) → Text
    = λ(x : Text) → "${x}..."

let absurd
    : ∀(A : Type) → <> → A
    = λ(A : Type) → λ(x : <>) → merge {=} x : A

let Void = ∀(A : Type) → A

let before
    : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (a → b) → (b → c) → a → c
    = λ(a : Type) →
      λ(b : Type) →
      λ(c : Type) →
      λ(f : a → b) →
      λ(g : b → c) →
      λ(x : a) →
        g (f x)

let after
    : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (b → c) → (a → b) → a → c
    = λ(a : Type) →
      λ(b : Type) →
      λ(c : Type) →
      λ(f : b → c) →
      λ(g : a → b) →
      λ(x : a) →
        f (g x)

let flip
    : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (a → b → c) → b → a → c
    = λ(a : Type) →
      λ(b : Type) →
      λ(c : Type) →
      λ(f : a → b → c) →
      λ(x : b) →
      λ(y : a) →
        f y x

let curry
    : ∀(a : Type) →
      ∀(b : Type) →
      ∀(c : Type) →
      ({ _1 : a, _2 : b } → c) →
      a →
      b →
        c
    = λ(a : Type) →
      λ(b : Type) →
      λ(c : Type) →
      λ(f : { _1 : a, _2 : b } → c) →
      λ(x : a) →
      λ(y : b) →
        f { _1 = x, _2 = y }

let uncurry
    : ∀(a : Type) →
      ∀(b : Type) →
      ∀(c : Type) →
      (a → b → c) →
      { _1 : a, _2 : b } →
        c
    = λ(a : Type) →
      λ(b : Type) →
      λ(c : Type) →
      λ(f : a → b → c) →
      λ(p : { _1 : a, _2 : b }) →
        f p._1 p._2

let unsafeDiv
    : Natural → Natural → Natural
    = let Natural/lessThan = https://prelude.dhall-lang.org/Natural/lessThan

      let Accum = { result : Natural, sub : Natural, done : Bool }

      in  λ(x : Natural) →
          λ(y : Natural) →
            let init
                : Accum
                = { result = 0, sub = x, done = False }

            let update
                : Accum → Accum
                = λ(acc : Accum) →
                    if    acc.done
                    then  acc
                    else  if Natural/lessThan acc.sub y
                    then  acc ⫽ { done = True }
                    else    acc
                          ⫽ { result = acc.result + 1
                            , sub = Natural/subtract y acc.sub
                            }

            let r
                : Accum
                = Natural/fold x Accum update init

            in  r.result

let _ = assert : unsafeDiv 3 2 ≡ 1

let bitWidth
    : Natural → Natural
    = λ(n : Natural) →
        let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual

        let Accum = { b : Natural, bitWidth : Natural }

        let init = { b = 1, bitWidth = 0 }

        let update =
              λ(acc : Accum) →
                if    lessThanEqual acc.b n
                then  { b = acc.b * 2, bitWidth = acc.bitWidth + 1 }
                else  acc

        let result
            : Accum
            = Natural/fold n Accum update init

        in  result.bitWidth

let _ = assert : bitWidth 1 ≡ 1

let _ = assert : bitWidth 2 ≡ 2

let _ = assert : bitWidth 3 ≡ 2

let _ = assert : bitWidth 4 ≡ 3

let log
    : Natural → Natural → Natural
    = λ(base : Natural) →
      λ(n : Natural) →
        let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual

        let Accum = { b : Natural, log : Natural }

        let init = { b = 1, log = 0 }

        let update =
              λ(acc : Accum) →
                if    lessThanEqual acc.b n
                then  { b = acc.b * base, log = acc.log + 1 }
                else  acc

        let result
            : Accum
            = Natural/fold n Accum update init

        in  Natural/subtract 1 result.log

let _ = assert : log 2 4 ≡ 2

let _ = assert : log 10 0 ≡ 0

let _ = assert : log 10 1 ≡ 0

let _ = assert : log 10 10 ≡ 1

let _ = assert : log 10 99 ≡ 1

let _ = assert : log 10 100 ≡ 2

let _ = assert : log 10 101 ≡ 2

let gcd
    : Natural → Natural → Natural
    = λ(x : Natural) →
      λ(y : Natural) →
        let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual

        let Pair = { x : Natural, y : Natural }

        let swap
            : Pair → Pair
            = λ(currentPair : Pair) → { x = currentPair.y, y = currentPair.x }

        let sortPair
            : Pair → Pair
            = λ(currentPair : Pair) →
                if    lessThanEqual currentPair.y currentPair.x
                then  currentPair
                else  swap currentPair

        let step
            : Pair → Pair
            = λ(currentPair : Pair) →
                  currentPair
                ⫽ { x = Natural/subtract currentPair.y currentPair.x }

        let update
            : Pair → Pair
            = λ(currentPair : Pair) → sortPair (step currentPair)

        let init = sortPair { x, y }

        let max_iter = init.x

        let result
            : Pair
            = Natural/fold max_iter Pair update init

        in  result.x

let _ = assert : gcd 4 1 ≡ 1

let _ = assert : gcd 4 2 ≡ 2

let _ = assert : gcd 4 3 ≡ 1

let _ = assert : gcd 4 4 ≡ 4

let _ = assert : gcd 5 4 ≡ 1

let _ = assert : gcd 5 0 ≡ 5

let _ = assert : gcd 15 12 ≡ 3

let Nonzero
    : Natural → Type
    = λ(y : Natural) → if Natural/isZero y then <> else {}

let safeDiv = λ(x : Natural) → λ(y : Natural) → λ(_ : Nonzero y) → unsafeDiv x y

let _ = assert : safeDiv 4 2 {=} ≡ 2

let sqrt =
      λ(n : Natural) →
        let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual

        let stepUp =
              λ(r : Natural) → if lessThanEqual (r * r) n then r + 1 else r

        in  Natural/subtract 1 (Natural/fold (n + 1) Natural stepUp 0)

let _ = assert : sqrt 25 ≡ 5

let _ = assert : sqrt 26 ≡ 5

let _ = assert : sqrt 24 ≡ 4

let _ = assert : sqrt 5 ≡ 2

let _ = assert : sqrt 4 ≡ 2

let _ = assert : sqrt 3 ≡ 1

let _ = assert : sqrt 2 ≡ 1

let _ = assert : sqrt 0 ≡ 0

let _ = assert : sqrt 1 ≡ 1

let _functors_examples =
      let F
          : Type → Type
          = λ(A : Type) → { x : A, y : A, t : Bool }

      let fmap
          : ∀(A : Type) → ∀(B : Type) → (A → B) → F A → F B
          = λ(A : Type) →
            λ(B : Type) →
            λ(f : A → B) →
            λ(fa : F A) →
              { x = f fa.x, y = f fa.y, t = fa.t }

      let example
          : F Natural
          = { x = 1, y = 2, t = True }

      let after_fmap
          : F Text
          = fmap
              Natural
              Text
              (λ(x : Natural) → if Natural/even x then "even" else "odd")
              example

      let test = assert : after_fmap ≡ { x = "odd", y = "even", t = True }

      let G
          : Type → Type
          = λ(A : Type) → < Left : Text | Right : A >

      let fmap
          : ∀(A : Type) → ∀(B : Type) → (A → B) → G A → G B
          = λ(A : Type) →
            λ(B : Type) →
            λ(f : A → B) →
            λ(ga : G A) →
              merge
                { Left = λ(t : Text) → (G B).Left t
                , Right = λ(x : A) → (G B).Right (f x)
                }
                ga

      let P
          : Type → Type → Type
          = λ(A : Type) → λ(B : Type) → { x : A, y : A, z : B, t : Integer }

      let bimap
          : ∀(A : Type) →
            ∀(B : Type) →
            ∀(C : Type) →
            ∀(D : Type) →
            (A → C) →
            (B → D) →
            P A B →
              P C D
          = λ(A : Type) →
            λ(B : Type) →
            λ(C : Type) →
            λ(D : Type) →
            λ(f : A → C) →
            λ(g : B → D) →
            λ(pab : P A B) →
              { x = f pab.x, y = f pab.y, z = g pab.z, t = pab.t }

      let fmap1
          : ∀(A : Type) → ∀(C : Type) → ∀(D : Type) → (A → C) → P A D → P C D
          = λ(A : Type) →
            λ(C : Type) →
            λ(D : Type) →
            λ(f : A → C) →
              bimap A D C D f (identity D)

      let fmap2
          : ∀(A : Type) → ∀(B : Type) → ∀(D : Type) → (B → D) → P A B → P A D
          = λ(A : Type) →
            λ(B : Type) →
            λ(D : Type) →
            λ(g : B → D) →
              bimap A B A D (identity A) g

      in  True

let compose_forward
    : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (a → b) → (b → c) → a → c
    = λ(a : Type) →
      λ(b : Type) →
      λ(c : Type) →
      λ(f : a → b) →
      λ(g : b → c) →
      λ(x : a) →
        g (f x)

let compose_backward
    : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (b → c) → (a → b) → a → c
    = λ(a : Type) →
      λ(b : Type) →
      λ(c : Type) →
      λ(f : b → c) →
      λ(g : a → b) →
      λ(x : a) →
        f (g x)

let flip
    : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (a → b → c) → b → a → c
    = λ(a : Type) →
      λ(b : Type) →
      λ(c : Type) →
      λ(f : a → b → c) →
      λ(x : b) →
      λ(y : a) →
        f y x

let curry
    : ∀(a : Type) →
      ∀(b : Type) →
      ∀(c : Type) →
      ({ _1 : a, _2 : b } → c) →
      a →
      b →
        c
    = λ(a : Type) →
      λ(b : Type) →
      λ(c : Type) →
      λ(f : { _1 : a, _2 : b } → c) →
      λ(x : a) →
      λ(y : b) →
        f { _1 = x, _2 = y }

let uncurry
    : ∀(a : Type) →
      ∀(b : Type) →
      ∀(c : Type) →
      (a → b → c) →
      { _1 : a, _2 : b } →
        c
    = λ(a : Type) →
      λ(b : Type) →
      λ(c : Type) →
      λ(f : a → b → c) →
      λ(p : { _1 : a, _2 : b }) →
        f p._1 p._2

let _ =
      λ(a : Type) →
      λ(b : Type) →
      λ(c : Type) →
      λ(d : Type) →
      λ(f : a → b) →
      λ(g : b → c) →
      λ(h : c → d) →
      λ(k : a → b → c) →
        { right_identity_law_forward =
            assert : compose_forward a b b f (identity b) ≡ f
        , left_identity_law_forward =
            assert : compose_forward a a b (identity a) f ≡ f
        , right_identity_law_backward =
            assert : compose_backward a a b f (identity a) ≡ f
        , left_identity_law_backward =
            assert : compose_backward a b b (identity b) f ≡ f
        , curry_uncurry = assert : curry a b c (uncurry a b c k) ≡ k
        , associativity_law_forward =
              assert
            :   compose_forward a b d f (compose_forward b c d g h)
              ≡ compose_forward a c d (compose_forward a b c f g) h
        , associativity_law_backward =
              assert
            :   compose_backward a b d (compose_backward b c d h g) f
              ≡ compose_backward a c d h (compose_backward a b c g f)
        , flip_flip = assert : flip b a c (flip a b c k) ≡ k
        }

let Monoid = λ(m : Type) → { empty : m, append : m → m → m }

let monoidBool
    : Monoid Bool
    = { empty = True, append = λ(x : Bool) → λ(y : Bool) → x && y }

let monoidNatural
    : Monoid Natural
    = { empty = 0, append = λ(x : Natural) → λ(y : Natural) → x + y }

let monoidText
    : Monoid Text
    = { empty = "", append = λ(x : Text) → λ(y : Text) → x ++ y }

let monoidList
    : ∀(a : Type) → Monoid (List a)
    = λ(a : Type) →
        { empty = [] : List a, append = λ(x : List a) → λ(y : List a) → x # y }

let reduce
    : ∀(m : Type) → Monoid m → List m → m
    = λ(m : Type) →
      λ(monoid_m : Monoid m) →
      λ(xs : List m) →
        List/fold
          m
          xs
          m
          (λ(x : m) → λ(y : m) → monoid_m.append x y)
          monoid_m.empty

let monoidLaws =
      λ(m : Type) →
      λ(monoid_m : Monoid m) →
      λ(x : m) →
      λ(y : m) →
      λ(z : m) →
        let plus = monoid_m.append

        let e = monoid_m.empty

        in  { monoid_left_id_law = plus e x ≡ x
            , monoid_right_id_law = plus x e ≡ x
            , monoid_assoc_law = plus x (plus y z) ≡ plus (plus x y) z
            }

let check_monoidBool_left_id_law =
      λ(x : Bool) →
      λ(y : Bool) →
      λ(z : Bool) →
        assert : (monoidLaws Bool monoidBool x y z).monoid_left_id_law

let foldMap
    : ∀(m : Type) → Monoid m → ∀(a : Type) → (a → m) → List a → m
    = λ(m : Type) →
      λ(monoid_m : Monoid m) →
      λ(a : Type) →
      λ(f : a → m) →
      λ(xs : List a) →
        List/fold
          a
          xs
          m
          (λ(x : a) → λ(y : m) → monoid_m.append (f x) y)
          monoid_m.empty

let Functor =
      λ(F : Type → Type) →
        { fmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b }

let Contrafunctor =
      λ(F : Type → Type) →
        { cmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F b → F a }

let functorList
    : Functor List
    = { fmap = https://prelude.dhall-lang.org/List/map }

let F
    : Type → Type
    = λ(A : Type) → { x : A, y : A, t : Bool }

let G
    : Type → Type
    = λ(A : Type) → < Left : Text | Right : A >

let functorF
    : Functor F
    = { fmap =
          λ(A : Type) →
          λ(B : Type) →
          λ(f : A → B) →
          λ(fa : F A) →
            { x = f fa.x, y = f fa.y, t = fa.t }
      }

let functorG
    : Functor G
    = { fmap =
          λ(A : Type) →
          λ(B : Type) →
          λ(f : A → B) →
          λ(ga : G A) →
            merge
              { Left = λ(t : Text) → (G B).Left t
              , Right = λ(x : A) → (G B).Right (f x)
              }
              ga
      }

let Const = λ(c : Type) → λ(a : Type) → c

let functorConst
    : ∀(c : Type) → Functor (Const c)
    = λ(c : Type) →
        { fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → identity (Const c a)
        }

let functorLaws =
      λ(F : Type → Type) →
      λ(functor_F : Functor F) →
      λ(a : Type) →
      λ(b : Type) →
      λ(c : Type) →
      λ(f : a → b) →
      λ(g : b → c) →
        let fmap = functor_F.fmap

        in  { functor_id_law = fmap a a (identity a) ≡ identity (F a)
            , functor_comp_law =
                let fg = compose_forward a b c f g

                let fmap_f = fmap a b f

                let fmap_g = fmap b c g

                let fmapf_fmapg =
                      compose_forward (F a) (F b) (F c) fmap_f fmap_g

                in  fmap a c fg ≡ fmapf_fmapg
            }

let contrafunctorLaws =
      λ(F : Type → Type) →
      λ(contrafunctor_F : Contrafunctor F) →
      λ(a : Type) →
      λ(b : Type) →
      λ(c : Type) →
      λ(f : a → b) →
      λ(g : b → c) →
        let cmap = contrafunctor_F.cmap

        in  { contrafunctor_id_law = cmap a a (identity a) ≡ identity (F a)
            , contrafunctor_comp_law =
                let gf = compose_backward a b c g f

                let cmap_f = cmap a b f

                let cmap_g = cmap b c g

                let cmapf_cmapg =
                      compose_backward (F c) (F b) (F a) cmap_f cmap_g

                in  cmap a c gf ≡ cmapf_cmapg
            }

let _ =
      let F
          : Type → Type
          = λ(a : Type) → { x : a, y : a, t : Bool }

      let functorF
          : Functor F
          = { fmap =
                λ(A : Type) →
                λ(B : Type) →
                λ(f : A → B) →
                λ(fa : F A) →
                  { x = f fa.x, y = f fa.y, t = fa.t }
            }

      let functor_laws_of_F =
            λ(a : Type) →
            λ(b : Type) →
            λ(c : Type) →
            λ(f : a → b) →
            λ(g : b → c) →
              { --  identity_law = assert : (functorLaws F functorF a b c f g).functor_id_law,
                composition_law =
                  assert : (functorLaws F functorF a b c f g).functor_comp_law
              }

      let identity_law_of_F =
            λ(a : Type) →
              let id_F =
                    λ(fa : { t : Bool, x : a, y : a }) →
                      { t = fa.t, x = fa.x, y = fa.y }

              in  assert : functorF.fmap a a (identity a) ≡ id_F

      let functor_laws_of_G =
            λ(a : Type) →
            λ(b : Type) →
            λ(c : Type) →
            λ(f : a → b) →
            λ(g : b → c) →
              { -- identity_law = -- assert : (functorLaws G functorG a b c f g).functor_id_law, composition_law = assert : (functorLaws G functorG a b c f g).functor_comp_law
                can_verify_laws = False
              }

      let C = λ(a : Type) → a → Text

      let contrafunctor_C
          : Contrafunctor C
          = { cmap =
                λ(a : Type) →
                λ(b : Type) →
                λ(f : a → b) →
                λ(fb : b → Text) →
                λ(x : a) →
                  fb (f x)
            }

      let contrafunctor_laws_of_C =
            λ(a : Type) →
            λ(b : Type) →
            λ(c : Type) →
            λ(f : a → b) →
            λ(g : b → c) →
              { identity_law =
                    assert
                  : ( contrafunctorLaws C contrafunctor_C a b c f g
                    ).contrafunctor_id_law
              , composition_law =
                    assert
                  : ( contrafunctorLaws C contrafunctor_C a b c f g
                    ).contrafunctor_comp_law
              , can_verify_laws = True
              }

      in  True

let Contrafunctor =
      λ(F : Type → Type) →
        { cmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F b → F a }

let contrafunctorConst
    : ∀(c : Type) → Contrafunctor (Const c)
    = λ(c : Type) →
        { cmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → identity (Const c a)
        }

let Id = λ(a : Type) → a

let functor_Id
    : Functor Id
    = { fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → f }

let Compose
    : (Type → Type) → (Type → Type) → Type → Type
    = λ(F : Type → Type) → λ(G : Type → Type) → λ(a : Type) → F (G a)

let functorFunctorCompose
    : ∀(F : Type → Type) →
      Functor F →
      ∀(G : Type → Type) →
      Functor G →
        Functor (Compose F G)
    = λ(F : Type → Type) →
      λ(functorF : Functor F) →
      λ(G : Type → Type) →
      λ(functorG : Functor G) →
        { fmap =
            λ(a : Type) →
            λ(b : Type) →
            λ(f : a → b) →
              let ga2gb
                  : G a → G b
                  = functorG.fmap a b f

              in  functorF.fmap (G a) (G b) ga2gb
        }

let functorContrafunctorCompose
    : ∀(F : Type → Type) →
      Functor F →
      ∀(G : Type → Type) →
      Contrafunctor G →
        Contrafunctor (Compose F G)
    = λ(F : Type → Type) →
      λ(functorF : Functor F) →
      λ(G : Type → Type) →
      λ(contrafunctorG : Contrafunctor G) →
        { cmap =
            λ(a : Type) →
            λ(b : Type) →
            λ(f : a → b) →
              let gb2ga
                  : G b → G a
                  = contrafunctorG.cmap a b f

              in  functorF.fmap (G b) (G a) gb2ga
        }

let contrafunctorFunctorCompose
    : ∀(F : Type → Type) →
      Contrafunctor F →
      ∀(G : Type → Type) →
      Functor G →
        Contrafunctor (Compose F G)
    = λ(F : Type → Type) →
      λ(contrafunctorF : Contrafunctor F) →
      λ(G : Type → Type) →
      λ(functorG : Functor G) →
        { cmap =
            λ(a : Type) →
            λ(b : Type) →
            λ(f : a → b) →
              let ga2gb
                  : G a → G b
                  = functorG.fmap a b f

              in  contrafunctorF.cmap (G a) (G b) ga2gb
        }

let contrafunctorContrafunctorCompose
    : ∀(F : Type → Type) →
      Contrafunctor F →
      ∀(G : Type → Type) →
      Contrafunctor G →
        Functor (Compose F G)
    = λ(F : Type → Type) →
      λ(contrafunctorF : Contrafunctor F) →
      λ(G : Type → Type) →
      λ(contrafunctorG : Contrafunctor G) →
        { fmap =
            λ(a : Type) →
            λ(b : Type) →
            λ(f : a → b) →
              let gb2ga
                  : G b → G a
                  = contrafunctorG.cmap a b f

              in  contrafunctorF.cmap (G b) (G a) gb2ga
        }

let Pair = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }

let Product
    : (Type → Type) → (Type → Type) → Type → Type
    = λ(F : Type → Type) → λ(G : Type → Type) → λ(a : Type) → Pair (F a) (G a)

let fProduct
    : ∀(a : Type) →
      ∀(b : Type) →
      (a → b) →
      ∀(c : Type) →
      ∀(d : Type) →
      (c → d) →
      Pair a c →
        Pair b d
    = λ(a : Type) →
      λ(b : Type) →
      λ(f : a → b) →
      λ(c : Type) →
      λ(d : Type) →
      λ(g : c → d) →
      λ(arg : Pair a c) →
        { _1 = f arg._1, _2 = g arg._2 }

let functorProduct
    : ∀(F : Type → Type) →
      Functor F →
      ∀(G : Type → Type) →
      Functor G →
        Functor (Product F G)
    = λ(F : Type → Type) →
      λ(functorF : Functor F) →
      λ(G : Type → Type) →
      λ(functorG : Functor G) →
        { fmap =
            λ(a : Type) →
            λ(b : Type) →
            λ(f : a → b) →
              fProduct
                (F a)
                (F b)
                (functorF.fmap a b f)
                (G a)
                (G b)
                (functorG.fmap a b f)
        }

let contrafunctorProduct
    : ∀(F : Type → Type) →
      Contrafunctor F →
      ∀(G : Type → Type) →
      Contrafunctor G →
        Contrafunctor (Product F G)
    = λ(F : Type → Type) →
      λ(contrafunctorF : Contrafunctor F) →
      λ(G : Type → Type) →
      λ(contrafunctorG : Contrafunctor G) →
        { cmap =
            λ(a : Type) →
            λ(b : Type) →
            λ(f : a → b) →
              fProduct
                (F b)
                (F a)
                (contrafunctorF.cmap a b f)
                (G b)
                (G a)
                (contrafunctorG.cmap a b f)
        }

let Either = λ(a : Type) → λ(b : Type) → < Left : a | Right : b >

let CoProduct
    : (Type → Type) → (Type → Type) → Type → Type
    = λ(F : Type → Type) → λ(G : Type → Type) → λ(a : Type) → Either (F a) (G a)

let fCoProduct
    : ∀(a : Type) →
      ∀(b : Type) →
      (a → b) →
      ∀(c : Type) →
      ∀(d : Type) →
      (c → d) →
      Either a c →
        Either b d
    = λ(a : Type) →
      λ(b : Type) →
      λ(f : a → b) →
      λ(c : Type) →
      λ(d : Type) →
      λ(g : c → d) →
      λ(arg : Either a c) →
        merge
          { Left = λ(x : a) → (Either b d).Left (f x)
          , Right = λ(y : c) → (Either b d).Right (g y)
          }
          arg

let functorCoProduct
    : ∀(F : Type → Type) →
      Functor F →
      ∀(G : Type → Type) →
      Functor G →
        Functor (CoProduct F G)
    = λ(F : Type → Type) →
      λ(functorF : Functor F) →
      λ(G : Type → Type) →
      λ(functorG : Functor G) →
        { fmap =
            λ(a : Type) →
            λ(b : Type) →
            λ(f : a → b) →
              fCoProduct
                (F a)
                (F b)
                (functorF.fmap a b f)
                (G a)
                (G b)
                (functorG.fmap a b f)
        }

let contrafunctorCoProduct
    : ∀(F : Type → Type) →
      Contrafunctor F →
      ∀(G : Type → Type) →
      Contrafunctor G →
        Contrafunctor (CoProduct F G)
    = λ(F : Type → Type) →
      λ(contrafunctorF : Contrafunctor F) →
      λ(G : Type → Type) →
      λ(contrafunctorG : Contrafunctor G) →
        { cmap =
            λ(a : Type) →
            λ(b : Type) →
            λ(f : a → b) →
              fCoProduct
                (F b)
                (F a)
                (contrafunctorF.cmap a b f)
                (G b)
                (G a)
                (contrafunctorG.cmap a b f)
        }

let Monad =
      λ(F : Type → Type) →
        { pure : ∀(a : Type) → a → F a
        , bind : ∀(a : Type) → F a → ∀(b : Type) → (a → F b) → F b
        }

let monadList
    : Monad List
    = let List/concatMap = https://prelude.dhall-lang.org/List/concatMap

      in  { pure = λ(a : Type) → λ(x : a) → [ x ]
          , bind =
              λ(a : Type) →
              λ(fa : List a) →
              λ(b : Type) →
              λ(f : a → List b) →
                List/concatMap a b f fa
          }

let monadJoin =
      λ(F : Type → Type) →
      λ(monadF : Monad F) →
      λ(a : Type) →
      λ(ffa : F (F a)) →
        monadF.bind (F a) ffa a (identity (F a))

let List/join
    : ∀(a : Type) → List (List a) → List a
    = monadJoin List monadList

let Semigroup = λ(m : Type) → { append : m → m → m }

let semigroupText
    : Semigroup Text
    = { append = λ(x : Text) → λ(y : Text) → x ++ y }

let Monoid = λ(m : Type) → Semigroup m ⩓ { empty : m }

let monoidText
    : Monoid Text
    = semigroupText ∧ { empty = "" }

let Monad =
      λ(F : Type → Type) →
          Functor F
        ⩓ { pure : ∀(a : Type) → a → F a
          , bind : ∀(a : Type) → F a → ∀(b : Type) → (a → F b) → F b
          }

let monadList
    : Monad List
    = let List/concatMap = https://prelude.dhall-lang.org/List/concatMap

      in    functorList
          ∧ { pure = λ(a : Type) → λ(x : a) → [ x ]
            , bind =
                λ(a : Type) →
                λ(fa : List a) →
                λ(b : Type) →
                λ(f : a → List b) →
                  List/concatMap a b f fa
            }

let _church_encoding_examples =
      let ListInt = ∀(r : Type) → r → (Integer → r → r) → r

      let foldRight
          : ∀(r : Type) → ListInt → r → (Integer → r → r) → r
          = λ(r : Type) →
            λ(p : ListInt) →
            λ(init : r) →
            λ(update : Integer → r → r) →
              p r init update

      in  True

let _church_defs =
      λ(F : Type → Type) →
      λ(fmapF : fmapF_type F) →
        let C = ∀(r : Type) → (F r → r) → r

        let fix
            : F C → C
            = λ(fc : F C) →
              λ(r : Type) →
              λ(frr : F r → r) →
                let c2r
                    : C → r
                    = λ(c : C) → c r frr

                let fmap_c2r
                    : F C → F r
                    = fmapF C r c2r

                let fr
                    : F r
                    = fmap_c2r fc

                in  frr fr

        let fmap_fix
            : F (F C) → F C
            = fmapF (F C) C fix

        let unfix
            : C → F C
            = λ(c : C) → c (F C) fmap_fix

        in  True

let _list_aggregation_test =
      let ListInt = ∀(r : Type) → r → (Integer → r → r) → r

      let nil
          : ListInt
          = λ(r : Type) → λ(a1 : r) → λ(a2 : Integer → r → r) → a1

      let cons
          : Integer → ListInt → ListInt
          = λ(n : Integer) →
            λ(c : ListInt) →
            λ(r : Type) →
            λ(a1 : r) →
            λ(a2 : Integer → r → r) →
              a2 n (c r a1 a2)

      let example1
          : ListInt
          = cons +123 (cons -456 (cons +789 nil))

      let abs = https://prelude.dhall-lang.org/Integer/abs

      let init
          : Natural
          = 0

      let update
          : Integer → Natural → Natural
          = λ(i : Integer) → λ(previous : Natural) → previous + abs i

      let sumListInt
          : ListInt → Natural
          = λ(list : ListInt) → list Natural init update

      let _ = assert : sumListInt example1 ≡ 1368

      in  True

let _tree_aggregation_test =
      let TreeText = ∀(r : Type) → (Text → r) → (r → r → r) → r

      let leaf
          : Text → TreeText
          = λ(t : Text) →
            λ(r : Type) →
            λ(a1 : Text → r) →
            λ(a2 : r → r → r) →
              a1 t

      let branch
          : TreeText → TreeText → TreeText
          = λ(left : TreeText) →
            λ(right : TreeText) →
            λ(r : Type) →
            λ(a1 : Text → r) →
            λ(a2 : r → r → r) →
              a2 (left r a1 a2) (right r a1 a2)

      let example2
          : TreeText
          = branch (branch (leaf "a") (leaf "b")) (leaf "c")

      let printLeaf
          : Text → Text
          = λ(leaf : Text) → leaf

      let printBranches
          : Text → Text → Text
          = λ(left : Text) → λ(right : Text) → "(${left} ${right})"

      let printTree
          : TreeText → Text
          = λ(tree : ∀(r : Type) → (Text → r) → (r → r → r) → r) →
              tree Text printLeaf printBranches

      let _ = assert : printTree example2 ≡ "((a b) c)"

      let show
          : < X : Natural | Y : Bool | Z > → Text
          = λ(x : < X : Natural | Y : Bool | Z >) →
              merge
                { X = λ(x : Natural) → "X ${Natural/show x}"
                , Y = λ(y : Bool) → "Y ${if y then "True" else "False"}"
                , Z = "Z"
                }
                x

      in  True

in  True
