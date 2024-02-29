# Programming Dhall

This book is an advanced-level tutorial on [Dhall](https://dhall-lang.org) for software engineers already familiar with the functional programming (FP) paradigm,
as practiced in languages such as OCaml, Haskell, Scala, and others.

The official documentation and user guides for Dhall is found at https://docs.dhall-lang.org.

This text follows the [Dhall standard 23.0.0](https://github.com/dhall-lang/dhall-lang/releases/tag/v23.0.0).

## Overview

Dhall is a language for programmable configuration files, primarily intended to replace templated JSON, templated YAML, and other programmable configuration formats.
The Dhall type-checker and interpreter guarantee that any well-typed Dhall program will be evaluated in finite time to a unique, correct "normal form" expression.
Evaluation of a well-typed Dhall program will never create infinite loops or throw exceptions due to missing or invalid values or wrong types at run time.
Invalid programs will be rejected at the type-checking phase (analogous to "compile time").
The price for those safety guarantees is that the Dhall language is _not_ Turing-complete.

Dhall adopts a hard-core FP approach and implements a pure type system F-omega with a few additional features, using a Haskell-like syntax. Example:

```dhall
let f = λ(x : Natural) → λ(y : Natural) → x + y + 2
let id = λ(A : Type) → λ(x : A) → x
  in f 10 (id Natural 20)
    -- This evaluates to 32 of type Natural.
```

The result is a powerful, purely functional programming language that can be used not only for flexible but strictly verified configuration files, and also for teaching and illustrating language-independent aspects of FP theory.

Currently, Dhall has no type inference: all types must be specified explicitly.
Although this makes Dhall programs more verbose, it makes for less "magic" in the syntax, which may help in learning some of the more advanced concepts of FP.

In this tutorial, we will focus on conceptual clarity. Programs will not be optimized for speed.

## Differences between Dhall and other FP languages

Mostly, Dhall follows the Haskell syntax and semantics.

Because the Dhall language is not Turing-complete and always evaluates all well-typed terms to a normal form, there is no analog of Haskell's "bottom" (undefined) value.
So, there is no difference between strict and lazy values in Dhall.
One can equally well imagine that all Dhall values are lazy, or that they are all strict.

For example, any well-typed Dhall program that returns a value of type `Natural` will always return a _literal_ `Natural` value.
This is because there is no other normal form for `Natural` values, and a well-typed Dhall program always evaluates to a normal form.
The program cannot return a `Natural` value that will be computed "later", or an "undefined" `Natural` value, or a "random" `Natural` value, or anything like that. 

### Syntactic differences

There are some syntactic differences between Dhall and most other FP languages:

- Integers must have a sign (`+1` or `-1`)
- Identifiers may contain a slash character (`List/map`)
- Product types are implemented via records. Co-product types are implemented via tagged unions. That is, product and co-product types are unnamed (anonymous) but _must_ have named parts. Examples: the record `{ x = 1, y = True }` has type `{ x : Natural, y : Bool }`. The union type `< X : Natural | Y >` has values written as `< X : Natural | Y >.X 123` or `< X : Natural | Y >.Y`.
- The empty record type `{ }` has only one value, written as `{=}`, and can be used as the unit type.
- The empty union type `< >` has _no_ values and can be used as the void type.
- Pattern matching on union types is implemented via the `merge` function.
- All function arguments (including all type parameters) must be introduced explicitly via the `λ` syntax, with explicitly given types.

Dhall does not support the Haskell-like concise definition syntax such as  `f x = x + 1`, where the argument is given on the left-hand side and types are inferred automatically.
Instead, that function needs to be written as:

```dhall
let f = λ(x : Natural) → x + 1
```

In Dhall, the standard `map` function for `List` values has the type signature:

```dhall
List/map: ∀(a : Type) → ∀(b : Type) → (a → b) → List a → List b
```

When applying this function,
the code must specify both type parameters:

```dhall
List/map Natural Natural (λ(x : Natural) → x + 1) [1, 2, 3]
   -- Returns [2, 3, 4].
```

A polymorphic identity function can be written as `λ(A : Type) → λ(x : A) → x`.

The type of polymorphic `map` functions may be written as:

```dhall
∀(F : Type → Type) → ∀(A : Type) → ∀(B : Type) → (A → B) → F A → F B
```

See the [Dhall cheat sheet](https://docs.dhall-lang.org/howtos/Cheatsheet.html) for more examples of basic Dhall usage.

The [Dhall standard prelude](https://store.dhall-lang.org/Prelude-v23.0.0/) defines a number of general-purpose functions.

### Semantic differences

The main semantic difference is that most primitive types (`Text`, `Double`, `Bytes`, `Date`, etc.) are almost completely opaque to the user.
The user may specify literal values of those types but can do little else with those values.

- `Bool` values support the boolean algebra operations and can be used in `if` expressions.
- `Natural` numbers can be added, multiplied, and compared for equality.
- The types `Natural`, `Integer`, `Double`, `Date`, `Time`, `TimeZone` may be converted to `Text`.
- `List` values may be concatenated and support some other functions (`List/map`, `List/length` and so on).
- `Text` strings may be concatenated and support a search/replace operation. But Dhall cannot compare them for equality or compute the length of a `Text` string. (Neither can Dhall compare `Double` or other types with each other.)

For types other than `Bool` and `Natural`, equality testing is not available as a function.
However, literal values of those types can be tested for equality via Dhall's `assert` feature.
That feature may be used for basic sanity checks:

```dhall
let x : Text = "123"
let _ = assert : x === "123"
  in x ++ "1"
    -- Returns "1231".
```

The `assert` construction is a special Dhall syntax that implements a limited form of the "equality type" (known from dependently typed languages).
The Dhall typechecker will raise a type error if the two sides of an `assert` are not evaluated to the same normal form, _at typechecking time_.

This means `assert` can only be used on literal values or on expressions that statically evaluate to literal values.
One cannot use `assert` for implementing comparisons between, say, two arbitrary `Text` values.
Try writing this code:

```dhall
let compareTextValues : Text → Text → Bool
  = λ(a : Text) → λ(b : Text) → 
    let _ = assert : a === b
      in True
```

This code will fail to typecheck because, within the definition of `compareTextValues`, the normal forms of the function parameters `a` and `b` are just the symbols `a` and `b`, and these two symbols are not equal.

Another difference from most other FP languages is that Dhall does not support recursive definitions (neither for types nor for values).
The only recursive type directly supported by Dhall is the built-in type `List`, and its functionality is intentionally limited, so that Dhall's termination guarantees remain in force.

User-defined recursive types and functions must be encoded in a non-recursive way. Later chapters in this book will show how to use the Church encoding for that purpose. In practice, this means the user is limited to finite data structures and fold-like functions on them.
General recursion is not possible (because it cannot have a termination guarantee).

Another feature is that Dhall is a purely functional language with no side effects.
There are no mutable values, no exceptions, no multithreading, no writing to disk, etc.
A Dhall program is a single expression that evaluates to a normal form, and that's that.
The resulting normal form can be used via imports in another Dhall program, or converted to JSON, YAML, and other formats.

### Modules and imports

Dhall has a simple file-based module system.
Each Dhall file must contain the definition of a single Dhall value (usually in the form `let x = ... in ...`).
That single value may be imported into another Dhall file by specifying the path to the first Dhall file.

The second Dhall file can directly use that value as a sub-expression.
For convenience, the imported value may be assigned to a variable with a meaningful name.

Here is an example where the first file contains a list of numbers, and the second file contains code that computes the sum of those numbers:

```dhall
-- This file is /tmp/first.dhall
[1, 2, 3, 4]
```

```dhall
-- This file is /tmp/sum.dhall
let input_list = ./first.dhall  -- Import from relative path.
let List/sum = https://prelude.dhall-lang.org/Natural/sum
  in List/sum input_list
```

Running `dhall` on the second file will compute and print the result:

```bash
$ dhall --file /tmp/sum.dhall
10
```

One can import Dhall values from files, HTTP URLs, and environment variables.
Here is an example of importing the Dhall list value `[1, 1, 1]` from an environment variable called `XS`:

```bash
$ echo "let xs = env:XS in List/length Natural xs" | XS="[1, 1, 1]" dhall
3
```

The Dhall import system implements strict limitations on what can be imported to ensure that users can prevent malicious code from being injected into a Dhall program. See [the documentation](https://docs.dhall-lang.org/discussions/Safety-guarantees.html) for more details.

## Some features of the Dhall type system

### The universal type quantifier

Dhall uses the universal type quantifier (`∀` or equivalently `forall`) to denote _types_ of generic functions.
So, any expression of the form `∀(x : something1) → something2` is a _type_.
That expression can be used, for instance, as a type annotation for some value.

This should not be confused with the symbol `λ` (equivalently the backslash, `\`) that denotes _functions themselves_, that is, _values_ of some function type.
Any expression of the form `λ(x : something1) → something2` is a function that can be applied to any `x` of type `something1` and will compute a result.

Now, that result may be itself a type or a value.

The polymorphic identity function is an example that helps remember the difference between `∀` and `λ`.

The identity function takes a value `x` of an arbitrary type and again returns the same value `x`.

```dhall
let identity
  : ∀(A : Type) → A → A
  = λ(A : Type) → λ(x : A) → x
```

Here we denoted the type parameter by the capital `A`. (Dhall does not require that types be capitalized.)

Defined like this, `identity` is a function of type `∀(A : Type) → A → A`. The function itself is the expression `λ(A : Type) → λ(x : A) → x`.

In Dhall, a function type `A → B` is equivalent to `∀(x : A) → B`.

So, the type expression `∀(A : Type) → A → A` is equivalent to `∀(A : Type) → ∀(x : A) → A`.

The corresponding Haskell code is:

```haskell
identity :: a -> a
identity = \x -> x
```

The corresponding Scala code is:

```scala
def identity[A]: A => A  = { x => x }
```

In Dhall, the type parameter must be specified explicitly both in the type expression and in the function expression.

This makes code more verbose, but also helps remove "magic" from the syntax.
All type parameters and all value parameters are always written explicitly.

### The void type

Dhall's empty union type `< >` cannot have any values.
Values of union types may be created only via constructors, but the type `< >` has no constructors.
So, no value of type `< >` will ever exist in any Dhall program.

If a value of the void type existed, one would be able to derive from it a value of any other type.
This property of the void type can be expressed formally via the function called `absurd`.
That function can compute a value of an arbitrary type `A` given a value of type `< >`:

```dhall
let absurd : ∀(A : Type) -> < > -> A
  = λ(A : Type) → λ(x : < >) → (merge {=} x) : A 
```

The type signature of `absurd` can be rewritten equivalently as:

```dhall
let absurd : < > -> ∀(A : Type) -> A
  = λ(x : < >) → λ(A : Type) → (merge {=} x) : A 
```

This type signature suggests a type equivalence between `< >` and the function type `∀(A : Type) -> A`.

Indeed, the type `∀(A : Type) -> A` is void (this can be proved via parametricity arguments).
So, the type expression `∀(A : Type) -> A` is equivalent to the simpler `< >` and can be used equally well to denote the void type.

Because any Dhall expression is fully parametrically polymorphic, parametricity arguments will apply to all Dhall code.

## Arithmetic with `Natural` numbers

The Dhall prelude supports a limited number of operations for `Natural` numbers.
It can add, subtract, multiply, compare, and test them for being even or odd.
However, division and other arithmetic operations are not directly supported.

### Using `Natural/fold`

The function `Natural/fold` is a general facility for creating loops with a fixed number of iterations:

```bash
$ dhall repl
⊢ :type Natural/fold

Natural →
∀(natural : Type) →
∀(succ : natural → natural) →
∀(zero : natural) →
  natural
```

Evaluating `Natural/fold n A s z` will repeatedly apply the function `s : A → A` to the initial value `z : A`.
The application of `s` will be repeated `n` times, evaluating `s(s(...(s(z))...))`.

For example:
```bash
$ dhall repl
⊢ let succ = λ(a : Text) → a ++ "1230" in Natural/fold 4 Text succ "x" 

"x1230123012301230"
```

This facility can be used in Dhall to encode many arithmetic operations for natural numbers that are usually implemented via loops.
However, `Natural/fold` is not a `while`-loop: it cannot iterate until some condition holds.
The number of iterations must be specified in advance (as the first argument of `Natural/fold`).

When the exact number of iterations is not known in advance, one must estimate that number from above and design the algorithm to allow it to run more iterations than necessary without changing the result. 

### Integer division

For example, let us implement division for natural numbers.

A simple iterative algorithm that uses only subtraction runs like this. Given `x : Natural` and `y : Natural`, we subtract `y` from `x` as many times as needed until the result becomes negative. The value `x div y` is the number of times we subtracted.

This algorithm can be directly implemented in Dhall only if we specify, in advance, the maximum required number of iterations.
A safe upper bound is the value `x` itself.
So, we have to perform the iteration using the function call `Natural/fold x ...`.

In most cases, the actual required number of iterations will be smaller than `x`.
We maintain a boolean flag `done` and set it to `True` once we reach the final result.
Then we write code to ensure that any further iterations will not modify the final result. 

The code is:

```dhall
-- unsafeDiv x y means x / y but it will return wrong results when y = 0.
let unsafeDiv : Natural -> Natural -> Natural =

  let Natural/lessThanEqual
    : Natural → Natural → Bool
    = λ(x : Natural) → λ(y : Natural) → Natural/isZero (Natural/subtract y x)
  let Natural/lessThan
    : Natural → Natural → Bool
    = λ(a : Natural) → λ(b : Natural) → Natural/lessThanEqual a b && Natural/lessThanEqual b a == False

  let Accum = {result: Natural, sub: Natural, done: Bool}
    in λ(x: Natural) → λ(y: Natural) →
         let r: Accum = Natural/fold x Accum (λ(acc: Accum) →
             if acc.done then acc
             else if Natural/lessThan acc.sub y then acc // {done = True}
             else acc // {result = acc.result + 1, sub = Natural/subtract y acc.sub}) {result = 0, sub = x, done = False}
         in r.result
in
  assert : unsafeDiv 3 2 === 1
```

### Safe division with dependently-typed `assert`

The function `unsafeDiv` works but produces nonsensical results when dividing by zero. For instance, `unsafeDiv 2 0` returns `2`.
We would like to prevent using that function with zero values.

Although the type system of Dhall is limited, it has enough facilities to ensure that we never divide by zero.

The first step is to define a dependent type that will be void (with no values) if a given natural number is zero, and unit otherwise:

```dhall
let Nonzero: Natural -> Type = λ(y: Natural) → if Natural/isZero y then < > else { }
```

This is a type function that returns one or another type given a `Natural` value.
For example, `Nonzero 0` returns the void type `< >`, but `Nonzero 10` returns the unit type `{ }`.
This definition is straightforward because types and values are treated quite similarly in Dhall.

We will use that function to implement safe division:

```dhall
let safeDiv = λ(x: Natural) → λ(y: Natural) → λ(_: Nonzero y) → unsafeDiv x y
```

To use `safeDiv`, we need to specify a third argument of the unit type (denoted by `{}` in Dhall).

That argument can have only one value, namely, `{=}`.

If we try dividing by zero, we will be obliged to pass a third argument of type `< >`, but there are no such values. Passing an argument of any other type will raise a type error.

```dhall
safeDiv 4 2 {=}  -- Returns 2.

safeDiv 4 0 {=}  -- Raises a type error. 
```

The main limitation of this `safeDiv` is that it can work only for literal values of the second argument.
This is so because the situation `y == 0` is excluded at type-checking time.
So, we cannot simply use `safeDiv` inside a function that takes an argument `y : Natural` and then divides by `y`.
Any usage of `safeDiv x y` will require us somehow to obtain a value of type `Nonzero y`.
That value serves as a witness that the number `y` is not zero.
Any function that uses `saveDiv` for dividing by an unknown value `y` will have to require an additional witness argument of type `Nonzero y`.

### Integer square root

The "integer-valued square root" of a natural number `n` is the largest natural number `r` such that `r * r <= n`. 

A simple algorithm for determining `r` is to subtract `1` from `n` repeatedly, until the result `r` satisfies `r * r <= n`.

As before, Dhall requires is to specify an upper bound on the number of iterations up front.
Let us specify `n` as the upper bound.

We will begin with `n` and iterate applying a function `stepDown`.
That function will decrement its argument `r` by `1` unless the condition `r * r <= n` is satisfied. 

The code is:

```dhall
let sqrt = λ(n: Natural) →
  let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual
  let stepDown = λ(r: Natural) → if (lessThanEqual (r * r) n) then r else Natural/subtract 1 r 
    in Natural/fold n Natural stepDown n 
  in 
    assert : sqrt 25 === 5
```

There are faster algorithms of computing the square root, but those algorithms require division.
Our implementation of division already includes a slow iteration.
So, we will not pursue further optimizations.

## Functors, contrafunctors, profunctors

## Typeclasses

## Church encoding for recursive types and type constructors

## Filterable functors and contrafunctors

## Applicative covariant and contravariant functors

## Monads

## Traversable functors

## Church encoding of existential types

## Church encoding of GADTs

## Free monads

## Free applicative functors

## Dhall as a scripting DSL