let NEL = λ(a : Type) → ∀(r : Type) → (a → r) → (a → r → r) → r

let one : ∀(a : Type) → a → NEL a = λ(a : Type) → λ(x : a) → λ(r : Type) → λ(ar : a → r) → λ(_ : a → r → r) → ar x

let cons : ∀(a : Type) → a → NEL a → NEL a =
 λ(a : Type) → λ(x : a) → λ(prev : NEL a) → λ(r : Type) → λ(ar : a → r) → λ(arr : a → r → r) →
  arr x (prev r ar arr)

let example1 : NEL Natural = cons Natural 1 (cons Natural 2 (one Natural 3))
let example2 : NEL Natural = cons Natural 3 (cons Natural 2 (one Natural 1))

let foldNEL : ∀(a : Type) → NEL a → ∀(r : Type) → (a → r) → (a → r → r) → r =
    λ(a : Type) → λ(nel : NEL a) → nel

let test = assert : example1 === foldNEL Natural example1 (NEL Natural) (one Natural) (cons Natural)

let concatNEL: ∀(a : Type) → NEL a → NEL a → NEL a =
    λ(a : Type) → λ(nel1 : NEL a) → λ(nel2 : NEL a) →
        foldNEL a nel1 (NEL a) (λ(x : a) → cons a x nel2) (cons a)
let test = assert : concatNEL Natural example1 example2 === cons Natural 1 (cons Natural 2 (cons Natural 3 (cons Natural 3 (cons Natural 2 (one Natural 1)))))

let snoc : ∀(a : Type) → a → NEL a → NEL a =
    λ(a : Type) → λ(x : a) → λ(prev : NEL a) →
    foldNEL a prev (NEL a) (λ(y : a) → cons a y (one a x)) (cons a)

let test = assert : example1 === snoc Natural 3 (snoc Natural 2 (one Natural 1))

let reverseNEL : ∀(a : Type) → NEL a → NEL a =
    λ(a : Type) → λ(nel : NEL a) → foldNEL a nel (NEL a) (one a) (snoc a)
let test = assert : reverseNEL Natural example1 === example2
let test = assert : reverseNEL Natural example2 === example1

let List/zip = https://prelude.dhall-lang.org/List/zip

let Pair = ∀(a : Type) → ∀(b : Type) → {_1 : a, _2 : b}
let pair = λ(a : Type) → λ(x : a)  → λ(b : Type) → λ(y : b) → {_1 = a, _2 = b}

let test = assert : List/zip Natural  [1, 2, 3] Text ["a", "b", "c"] === [ { _1 = 1, _2 = "a" }, { _1 = 2, _2 = "b" }, { _1 = 3, _2 = "c" } ]

let zipCont : ∀(a : Type) → ∀(b : Type) → ∀(r : Type) → ((a → r) → r) → ((b → r) → r) → ({ _1 : a, _2 : b } → r) → r  =
λ(a : Type) → λ(b : Type) → λ(r : Type) → λ(arr : (a → r) → r) → λ(brr : (b → r) → r) → λ(abr : {_1 : a, _2: b} → r) →
    let p : a → r = λ(x : a) →
      let q : b → r = λ(y : b) → abr {_1 = x , _2 = y}
        in brr  q
    in arr p

let ChurchK : (Type → Type → Type) → Type → Type =
  λ(F : Type → Type → Type) → λ(a : Type) → ∀(r : Type) → (F a r → r) → r

let zipF_type : (Type → Type → Type) → Type = -- clarify why we are using lambda and forall at different places!
 λ(F : Type → Type → Type) → ∀(a : Type) → ∀(b : Type) → ∀(r : Type) → F a r → F b r → F { _1 : a, _2 : b } r 

let zip
 : ∀(F: Type → Type → Type) → zipF_type F → ∀(a : Type) → ChurchK F a → ∀(b : Type) → ChurchK F b → ChurchK F { _1 : a, _2 : b }
= λ(F : Type → Type → Type) → λ(zipF : zipF_type F) → λ(a : Type) → λ(ca : ChurchK F a) → λ(b : Type) → λ(cb : ChurchK F b)
 → λ(r : Type) → λ(fabr: F { _1 : a, _2 : b } r → r) → 

    let p : ({ _1 : F a r, _2 : F b r } → r) → r  = zipCont (F a r) (F b r) r (ca r) (cb r)

    let j : { _1 : F a r, _2 : F b r } → F { _1 : a, _2 : b } r  = λ(g : { _1 : F a r, _2 : F b r }) → zipF a b r g._1 g._2

    let q : (F { _1 : a, _2 : b } r → r) → r =  λ(f : F { _1 : a, _2 : b } r → r)  →  p (λ(g : { _1 : F a r, _2 : F b r })  → f (j g))

    in q fabr  -- After beta-reduction, this simplifies to:   ca r (λ(x : F a r) → cb r (λ(y : F b r) → fabr (zipF a b r x y)))

-- Implement this for trees with integer values and show that this does not work as expected.

let F = λ(a : Type) → λ(r : Type) → < Leaf: a | Branch : { left : r, right : r } >

let TreeInt = ∀(r : Type) → (F Integer r → r) → r

let leaf : Integer → TreeInt = λ(x : Integer) → λ(r : Type) → λ(frr : F Integer r → r) → frr ((F Integer r).Leaf x)
let branch : TreeInt → TreeInt → TreeInt = λ(left : TreeInt) → λ(right : TreeInt) → λ(r : Type) → λ(frr : F  Integer r → r) → frr ((F Integer r).Branch { left = left r frr, right = right r frr })

let example1 = branch (leaf +1) (branch (leaf +2) (leaf +3))
let example2 = branch (leaf +10) (branch (leaf +20) (leaf +30))

let print : TreeInt → Text = λ(tree: ∀(r : Type) → (F Integer r → r) → r) →
  let frr : F Integer Text → Text = λ(fr : F Integer Text) →
    merge {
        Leaf = λ(t : Integer) → "(${Integer/show t})",
        Branch = λ(b : { left : Text, right : Text }) → "(${b.left} ${b.right})" } fr
    in tree Text frr

let test = assert : print example1 === "((+1) ((+2) (+3)))"

let FTree = λ(a : Type) → λ(r : Type) → < Leaf: a | Branch : { left : r, right : r } >
let zipFTree : ∀(a : Type) → ∀(b : Type) → ∀(r : Type) → FTree a r → FTree b r  → FTree { _1 : a, _2 : b } r  =  
  λ(a : Type) → λ(b : Type) → λ(r : Type) → λ(fa : FTree a r) → λ(fb: FTree b r) → merge  { 
    Leaf = λ(x : a) → merge {
      Leaf = λ(y : b) → (FTree { _1 : a, _2 : b }  r).Leaf {_1 = x, _2 = y},
      Branch = λ(y : { left : r, right : r })  → (FTree { _1 : a, _2 : b }  r).Branch y,
      } fb, 
    Branch = λ(x : { left : r, right : r }) →  merge { 
      Leaf = λ(y : b) → (FTree { _1 : a, _2 : b }  r).Branch x,
      Branch = λ(y : { left : r, right : r }) → (FTree { _1 : a, _2 : b }  r).Branch y,
      } fb,
    } fa
let CTree = λ(a : Type) → ChurchK FTree a
let zipTreeA : ∀(a : Type) → ChurchK FTree a → ∀(b : Type) → ChurchK FTree b → ChurchK FTree { _1 : a, _2 : b } =  
 zip F zipFTree 
 
  
    in True
