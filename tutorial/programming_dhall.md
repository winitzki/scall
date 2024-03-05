# Advanced functional programming in Dhall

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

Dhall implements a pure type system Fω with a few additional features, using a Haskell-like syntax.

For a theoretical introduction to various forms of lambda calculus, System F, and System Fω, see:

- https://github.com/sgillespie/lambda-calculus/blob/master/doc/system-f.md
- https://gallium.inria.fr/~remy/mpri/
- https://www.cl.cam.ac.uk/teaching/1415/L28/lambda.pdf

Here is an example of a Dhall program:

```dhall
let f = λ(x : Natural) → λ(y : Natural) → x + y + 2
let id = λ(A : Type) → λ(x : A) → x
  in f 10 (id Natural 20)
    -- This evaluates to 32 of type Natural.
```

The result is a powerful, purely functional programming language that can be used not only for flexible but strictly verified configuration files, and also for teaching and illustrating language-independent aspects of FP theory.

Currently, Dhall has no type inference: all types must be specified explicitly.
Although this makes Dhall programs more verbose, it makes for less "magic" in the syntax, which may help in learning some of the more advanced concepts of FP.
This is the primary focus of the present book.

## Differences between Dhall and other FP languages

Mostly, Dhall follows the Haskell syntax and semantics.

Because the Dhall language is not Turing-complete and always evaluates all well-typed terms to a normal form, there is no analog of Haskell's "bottom" (undefined) value.
So, there is no difference between strict and lazy values in Dhall.
One can equally well imagine that all Dhall values are lazy, or that they are all strict.

For example, any well-typed Dhall program that returns a value of type `Natural` will always return a _literal_ `Natural` value.
This is because there is no other normal form for `Natural` values, and a well-typed Dhall program always evaluates to a normal form.
The program cannot return a `Natural` value that will be computed "later", or an "undefined" `Natural` value, or a "random" `Natural` value, or anything like that. 

### Syntactic differences

Although Dhall broadly resembles Haskell, there are some minor syntactic differences between Dhall and most other FP languages.

#### Identifiers

Identifiers may contain slash characters; for example, `List/map` is a valid name.

#### Integers and natural numbers

Integers must have a sign (`+1` or `-1`) while `Natural` numbers may not have a sign

#### Product types

Product types are implemented only through records. For example, `{ x = 1, y = True }` is a record value, and its type is `{ x : Natural, y : Bool }` (a "record type").

Records can be nested: the record value `{ x = 1, y = { z = True, t = "abc" } }` has type `{ x : Natural, y : { z : Bool, t : Text } }`.

There is no built-in tuple type, such as Haskell's and Scala's `(Int, String)`.
Records with names must be used instead.
For instance, the (Haskell / Scala) tuple type `(Int, String)` may be translated to Dhall as the record type `{ _1 : Int, _2 : String }`.

Record types are "structural": two record types are distinguished only via their field names and types.
There is no way of assigning a permanent name to the record type itself, as it is done in other languages, in order to distinguish that type from other record types.

For example, the values `x` and `y` have the same type in the following Dhall code:

```dhall
let RecordType1 = { a : Natural, b : Bool }
let x : RecordType1 = { a = 1, b = True }
let RecordType2 = { b : Bool, a : Natural }
let y : RecordType2 = { a = 2, b = False }
```

#### Co-product types

Co-product types are implemented via tagged unions, for example, `< X : Natural | Y : Bool >`.
Here `X` and `Y` are constructor names for the union type.

Values of co-product types are created via constructor functions.
Constructor functions are written using record-like access notation.
For example, `< X : Natural | Y : Bool >.X` is a function of type `Natural → < X : Natural | Y : Bool >`. 
Applying that function to a value of type `Natural` will create a value of the union type `< X : Natural | Y : Bool >`.

Union types can have empty constructors.
For example, the union type `< X : Natural | Y >` has values written as `< X : Natural | Y >.X 123` or `< X : Natural | Y >.Y` and the type of both those values is `< X : Natural | Y >`.

Union types are "structural": two union types are distinguished only via their constructor names and types.
There is no way of assigning a permanent name to the union type itself, as it is done in other languages, in order to distinguish that type from other union types.

#### Pattern matching

The only built-in type constructors are `Optional` and `List`.

Pattern matching on union types is implemented via the `merge` function.
For example, a `zip` function for `Optional` types is implemented as:

```dhall
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
```

#### Unit type and void type

The empty union type `< >` has _no_ values and can be used as the void type.

The empty record type `{ }` can be used as the unit type. It has only one value, written as `{=}`.

An equivalent way of denoting the unit type is by a union type with a single constructor, for example `< One >` or with any other name instead of "One".
The type `< One >` has a single distinct value, denoted in Dhall by `< One >.One`.

In this way, one can define differently named unit types.

#### Miscellaneous features

- All function arguments (including all type parameters) must be introduced explicitly via the `λ` syntax, with explicitly given types.

- Multiple `let x = y in z` bindings may be written next to each other without writing `in`, and types of variables may be omitted.
For example:

```dhall
let a = 1
let b = 2
  in a + b  -- This evaluates to 3.
```

Because of this feature, we will write snippets of Dhall code in the form `let a = ...` without the trailing `in`.
It is implied that the `let` declarations are part of a larger Dhall program.

Dhall does not support the Haskell-like concise definition syntax such as  `f x = x + 1`, where the argument is given on the left-hand side and types are inferred automatically.
Dhall functions need to be written via a `λ` symbol with an explicit type annotation:

```dhall
let f = λ(x : Natural) → x + 1
```

In Dhall, the standard `map` function for `List` values has the type signature:

```dhall
List/map: ∀(a : Type) → ∀(b : Type) → (a → b) → List a → List b
```

When applying this function, the code must specify both type parameters `a`, `b`:

```dhall
List/map Natural Natural (λ(x : Natural) → x + 1) [1, 2, 3]
   -- Returns [2, 3, 4].
```

A polymorphic identity function can be written (with a complete type annotation) as:

```dhall
let identity
  : ∀(A : Type) → ∀(x : A) → A 
  = λ(A : Type) → λ(x : A) → x
```

The type of polymorphic `fmap` functions may be written as:

```dhall
∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b
```

Dhall does not require capitalizing the names of types and type parameters.
In this tutorial, we will usually capitalize type constructors (such as `List`).

See the [Dhall cheat sheet](https://docs.dhall-lang.org/howtos/Cheatsheet.html) for more examples of basic Dhall usage.

The [Dhall standard prelude](https://store.dhall-lang.org/Prelude-v23.0.0/) defines a number of general-purpose functions.

### Semantic differences

The main semantic difference is that most primitive types (`Text`, `Double`, `Bytes`, `Date`, etc.) are almost completely opaque to the user.
The user may specify literal values of those types but can do little else with those values.

- `Bool` values support the boolean algebra operations and can be used in `if` expressions.
- `Natural` numbers can be added, multiplied, and compared for equality.
- The types `Natural`, `Integer`, `Double`, `Date`, `Time`, `TimeZone` may be converted to `Text`.
- `List` values may be concatenated and support some other functions (`List/map`, `List/length` and so on).
- `Text` strings may be concatenated and support a search/replace operation. But Dhall cannot compare them for equality or compute the length of a `Text` string. Neither can Dhall compare `Double` or other types with each other. Comparison functions are only available for `Bool` and `Natural` types.

All well-typed functions in Dhall are total (not partial).
A pattern-matching expression will not typecheck unless it handles all parts of the union type being matched.

Another difference from most other FP languages is that Dhall does not support recursive definitions (neither for types nor for values).
The only recursive type directly supported by Dhall is the built-in type `List`, and its functionality is intentionally limited, so that Dhall's termination guarantees remain in force.

User-defined recursive types and functions must be encoded in a non-recursive way. Later chapters in this book will show how to use the Church encoding for that purpose. In practice, this means the user is limited to finite data structures and fold-like functions on them.
General recursion is not possible (because it cannot have a termination guarantee).

Dhall is a purely functional language with no side effects.
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

### The `assert` keyword and equality types

For types other than `Bool` and `Natural`, equality testing is not available as a function.
However, values of any types may be tested for equality via Dhall's `assert` feature.
That feature may be used for basic sanity checks:

```dhall
let x : Text = "123"
let _ = assert : x === "123"
  in x ++ "1"
    -- Returns "1231".
```

The `assert` construction is a special Dhall syntax that implements a limited form of the "equality type" (known from dependently typed languages).

The Dhall expression `a === b` is a type.
That type has no values (is void) if the normal forms of `a` and `b` are different.
For example, the types `1 === 2` and `λ(a : Text) → a === True` are void. 

If `a` and `b` evaluate to the same normal form, the type `a === b` is not void, and there exists a value of that type.
However, that value cannot be written explicitly; the only way to refer to that value is by using the `assert` keyword.

The syntax is `assert : a === b`.
This expression evaluates to a value of type `a === b` if the two sides are equal after reducing them to their normal forms.

We can assign that value to a variable if we'd like:

```dhall
let t = assert : 1 + 2 === 0 + 3
```

In this example, the two sides of an `assert` are equal after reducing them to normal forms, so the type `1 === 1` is not void and has a value that we assigned to `t`.

The Dhall typechecker will raise a type error if the two sides of an `assert` are not evaluated to the same normal form, _at typechecking time_.

This means `assert` can only be used on literal values or on expressions that statically evaluate to literal values.
One cannot use `assert` for implementing a function comparing, say, two arbitrary `Text` values given as arguments.
Try writing this code:

```dhall
let compareTextValues : Text → Text → Bool
  = λ(a : Text) → λ(b : Text) → 
    let _ = assert : a === b
      in True
```

This code will _fail to typecheck_ because, within the definition of `compareTextValues`, the normal forms of the function parameters `a` and `b` are just the symbols `a` and `b`, and these two symbols are not equal.

The `assert` keyword is often used for unit tests.
In that case, we do not need to keep the values of the equality type.
We just need to verify that the equality type is not void.
So, we may write unit tests like this:

```dhall
let f = λ(a : Text) → "(" ++ a ++ ")"
let _ = assert : f "x" === "(x)"
let _ = assert : f "" === "()"
  in ... -- Further code.
```

### Type constructors

Type constructors in Dhall are written as functions from `Type` to `Type`.

For example, a type constructor that would be written in Haskell or Scala as `type P a = (a, a)` must be encoded in Dhall as an explicit function, taking a parameter `a` of type `Type` and returning another type.

Because Dhall does not have nameless tuples, we will use a record with field names `_1` and `_2`:

```dhall
let P = λ(a : Type) → { _1 : a, _2 : a }
```

The output of the `λ` function is a record type `{ _1 : a, _2 : a }`.

The type of `P` itself is `Type → Type`.

Type constructors involving more than one type parameter are usually written as curried functions.

Here is an example of how we could define a type constructor similar to Haskell's and Scala's `Either`:

```dhall
let Either = λ(a : Type) → λ(b : Type) → < Left : a | Right : b >
```

The type of `Either` is `Type → Type → Type`.


### Types, kinds, sorts

The Dhall type system is a "pure type system", meaning that types and values are treated largely in the same way.
For instance, we may write `let a : Bool = True` to define a variable of type `Bool`, and we may also write `let b = Bool` to define a variable whose value is the type `Bool` itself.
Then the type of `b` will be `Type`.

To see the type of an expression, one can write `:type` in the Dhall interpreter:

```dhall
$ dhall repl
Welcome to the Dhall v1.42.1 REPL! Type :help for more information.
⊢ :type True

Bool

⊢ :type Bool

Type
```

Dhall defines functions with the `λ` syntax:

```dhall
λ(t : Natural) → t + 1
```

The same syntax works if `t` were a type parameter (a variable of type `Type`):

```dhall
λ(t : Type) → λ(x : t) → { first = x, second = x }
```

Records and union types may contain types as well as values:


```dhall
⊢ :type { a = 1, b = Bool }

{ a : Natural, b : Type } 

⊢ :type < A : Bool | B : Type >.B Text

< A : Bool | B : Type >
```

The symbol `Type` is itself treated as a special value whose type is `Kind`.
Other possible values of type `Kind` are type constructor types, such as `Type → Type`, as well as other type expressions involving the symbol `Type`.

```dhall
⊢ :type (Type → Type) → Type

Kind

⊢ :type { a : Type }

Kind
```

As we have just seen, the type of `{ a = 1, b = Bool }` is the record type `{ a : Natural, b : Type }`. The type of _that_ is `Kind`:

```dhall
⊢ :type { a : Natural, b : Type }

Kind
```

Any function that returns something containing `Type` will itself have the output type `Kind`:

```dhall
⊢ :type λ(t : Bool) → if t then Type else Type → Type

∀(t : Bool) → Kind
```

In turn, the symbol `Kind` is a special value of type `Sort`.
Other type expressions involving `Kind` are also of type `Sort`:

```dhall
⊢ :type Kind

Sort

⊢ :type Kind → Kind → Type

Sort
```

However, the symbol `Sort` _does not_ itself have a type.
It is a type error to use `Sort` in Dhall code.

This important design decision prevents Dhall from having to define an infinite hierarchy of "type universes".
The result is a type system that has just enough abstraction to support treating types as values, but does not run into the complications with polymorphism over kinds or other type universes.

Because of this, Dhall does not support any code that operates on `Kind` values ("kind polymorphism").
Very little can be done with Dhall expressions of type `Sort`, such as `Kind` or `Kind → Kind`.
One can define variables having those values, but that's about it.

For instance, it is a type error to write a function that returns the symbol `Kind` as its output value:

```dhall
⊢ :let a = Kind

a : Sort

⊢ λ(_: Kind) → a

Error: ❰Sort❱ has no type, kind, or sort

1│ λ(_: Kind) →  a
```

This is because the symbol `Kind` has type `Sort` but the symbol `Sort` itself does not have a type, while Dhall requires a valid function type to itself have a type.

There was at one time an effort to add full "kind polymorphism" to Dhall, which would allow functions to manipulate `Kind` values.
But that effort was abandoned after it was discovered that it would break the consistency of Dhall's type system.
For more details, see the discussion around this PR comment: https://github.com/dhall-lang/dhall-haskell/pull/563#issuecomment-426474106

### The universal type quantifier `∀` vs. the function symbol `λ`

Dhall uses the universal type quantifier (`∀` or equivalently `forall`) to denote _types_ of functions.
So, any expression of the form `∀(x : something1) → something2` is a _type_ expression: it is something that always means a type.
That type can be used, for instance, as a type annotation for some function.

For example, the function that appends "..." to a string argument is written like this:

```dhall
let f = λ(x : Text) → "${x}..."
```

The type of `f` can be written as `∀(x : Text) → Text`.
This type does not need the name `x` and can be also written in a shorter syntax as just `Text → Text`.
But Dhall will internally rewrite that to the longer form `∀(_ : Text) → Text`.

If we want, we may write the definition of `f` together with a type annotation:

```dhall
let f : ∀(x : Text) → Text
  = λ(x : Text) → "${x}..."
```

Another way to see that `∀` always denotes types is to try writing an expression `∀(x : Text) → "${x}..."`.
Dhall will reject that expression with the error message `"Invalid function output"`.
The expression `∀(x : Text) → something2` must be a type of a function, and `something2` must be the output type of that function. So, `something2` must be a type and cannot be a `Text` value.

While the symbol `∀` denotes types, the symbol `λ` (equivalently the backslash, `\`) denotes _functions themselves_, that is, _values_ of some function type.

An expression of the form `λ(x : something1) → something2` is a function that can be applied to any `x` of type `something1` and will compute a result, `something2`. (That result could itself be a value or a type.)

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
identity :: a → a
identity = \x → x
```

The corresponding Scala code is:

```scala
def identity[A]: A => A  = { x => x }
```

In Dhall, the type parameter must be specified explicitly both in the type expression and in the function expression.

This makes code more verbose, but also helps remove "magic" from the syntax.
All type parameters and all value parameters are always written explicitly.

### Dependent types

Curried functions types support dependence between an argument type and any previously given argument values.

For example, consider the following function type:

```dhall
∀(F : Type → Type) → ∀(A : Type) → ∀(x : A) → F A
```

In that type, the argument `x` has type `A`, which is given by a previous argument.
The output type `F A` depends on the first two arguments.

Since Dhall is a "pure type system", types and values are treated similarly in many ways.
So, one can define functions from values to types in the same way as one defines any other functions:

```dhall
let f
  : ∀(x : Bool) → Type
  = λ(x : Bool) → if x then Natural else Text 
```

The result of evaluating `f False` is the type `Text`.

Such functions can be used in type signatures to create functions of dependent types (that is, functions whose types depend on the values of their input arguments):

```dhall
∀(x : Bool) → ∀(y : f x) → Text
```

Here, the type of the argument `y` must be `Natural` or `Text` depending on the _value_ of the argument `x`.

For an example of using dependent types for implementing safe division, see below in the section about arithmetic operations.

One must keep in mind that Dhall's implementation of dependent types is limited to the simplest use cases.

The following example shows that Dhall does not recognize that a value of a dependent type is well-typed inside an `if` branch.

```dhall
⊢ :let g : ∀(x : Bool) → f x → Text = λ(x : Bool) → λ(y : f x) → if x then "" else y

Error: ❰if❱ branches must have matching types
```

The `if/then/else` construction fails to typecheck even though we expect both `if` branches to return `Text` values.
If we are in the `if/then` branch, we return a `Text` value (an empty string).
If we are in the `if/else` branch, the value `x` is `False` and so `y` must have type `f False = Text`.
But Dhall does not implement this logic and cannot see that the branches will have matching types.

Because of this and other limitations, one can make only an occasional use of dependent types in Dhall. 

### The void type

Dhall's empty union type `< >` cannot have any values.
Values of union types may be created only via constructors, but the type `< >` has no constructors.
So, no value of type `< >` will ever exist in any Dhall program.

If a value of the void type existed, one would be able to derive from it a value of any other type.
This property of the void type can be expressed formally via the function called `absurd`.
That function can compute a value of an arbitrary type `A` given a value of type `< >`:

```dhall
let absurd : ∀(A : Type) → < > → A
  = λ(A : Type) → λ(x : < >) → (merge {=} x) : A 
```

The type signature of `absurd` can be rewritten equivalently as:

```dhall
let absurd : < > → ∀(A : Type) → A
  = λ(x : < >) → λ(A : Type) → (merge {=} x) : A 
```

This type signature suggests a type equivalence between `< >` and the function type `∀(A : Type) → A`.

Indeed, the type `∀(A : Type) → A` is void (this can be proved via parametricity arguments).
So, the type expression `∀(A : Type) → A` is equivalent to the simpler `< >` and can be used equally well to denote the void type.

Because any Dhall expression is fully parametrically polymorphic, parametricity arguments will apply to all Dhall code.

For instance, a Dhall function cannot take a parameter `λ(A : Type)` and then check whether `A` is equal to `Natural`, say.
It is not possible in Dhall to compare types as values.
For this reason, any Dhall function of the form `λ(A : Type) → ...` must work in the same way for all types `A`.

As another example of automatic parametricity, consider the unit type `{}` and its equivalent form `∀(A : Type) → A → A`.

## Function combinators

The standard combinators for functions are forward and backward composition, currying / uncurrying, and argument flipping.

Implementing them in Dhall is straightforward.
Instead of pairs, we use the record type `{ _1 : a, _2 : b }`. 

```dhall
let before
  : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (a → b) → (b → c) → (a → c)
  = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : a → b) → λ(g : b → c) → λ(x : a) →
    g(f(x)) 

let after
  : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (b → c) → (a → b) → (a → c)
  = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : b → c) → λ(g : a → b) → λ(x : a) →
    f(g(x)) 

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
```

## Arithmetic with `Natural` numbers

The Dhall prelude supports a limited number of operations for `Natural` numbers.
It can add, subtract, multiply, compare, and test them for being even or odd.
However, division and other arithmetic operations are not directly supported.
We will now show how to implement some of those operations.

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
let unsafeDiv : Natural → Natural → Natural =
  let Natural/lessThan = https://prelude.dhall-lang.org/Natural/lessThan
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
let Nonzero: Natural → Type = λ(y: Natural) → if Natural/isZero y then < > else {}
```

This is a type function that returns one or another type given a `Natural` value.
For example, `Nonzero 0` returns the void type `< >`, but `Nonzero 10` returns the unit type `{}`.
This definition is straightforward because types and values are treated quite similarly in Dhall.

We will use that function to implement safe division:

```dhall
let safeDiv = λ(x: Natural) → λ(y: Natural) → λ(_: Nonzero y) → unsafeDiv x y
```

To use `safeDiv`, we need to specify a third argument of the unit type (denoted by `{}` in Dhall).

That argument can have only one value, namely, the empty record, denoted in Dhall by `{=}`.

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

The advantage of using this technique is that we will guarantee, at typechecking time, that programs will never divide by zero.

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
Our implementation of division already requires a slow iteration.
So, we will not pursue further optimizations.

### Binary logarithm

The "binary logarithm" (`log2`) of a natural number `n` is the smallest number of binary bits needed to represent `n`.
For example, `log2 3` is `2` because `3` is represented as two binary bits: `0b11`, while `log2 4` is `3` because `4` is `0b100` and requires 3 bits.

To compute this function, we find the smallest natural number `b` such that `2` to the power `b` is larger than `n`. We start with `b = 1` and multiply `b` by `2` as many times as needed until we get a value larger than `n`.

As before, we need to supply an upper bound on the iteration count.
We supply `n` as that bound and make sure that the final result remains constant once we reach it, even if we perform further iterations.

The code is:

```dhall
let log2 : Natural → Natural = λ(n: Natural) →
  let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual
  let Accum = { b : Natural, log2 : Natural }
  let acc0 = { b = 1, log2 = 0 } -- At all times, b == pow(2, log2).
  let update = λ(acc: Accum) →
     if lessThanEqual n acc.b
     then { b = acc.b * 2, log2 = acc.log2 + 1 }
     else acc 
  let result : Accum = Natural/fold n Accum update acc0
    in result.log2 
```

## Functors and bifunctors

### Functors and `fmap`

A functor (in the jargon of the functional programming community) is a type constructor `F` with an `fmap` function having the standard type signature and obeying the functor laws.

Those type constructors are also called "covariant functors".
For type constructors, "covariant" means "has a lawful `fmap` method".

A simple example of a functor is a record with two values of type `A` and a value of a fixed type `Bool`.

In Haskell, that type constructor and its `fmap` function are defined by:

```haskell
data F a = F a a Bool
fmap :: (a → b) → F a → F b
fmap f (F x y t) = F (f x) (F y) t 
```

In Scala:

```scala
final case class F[A](x: A, y: A, t: Boolean)

def fmap[A, B](f: A => B)(fa: F[A]): F[B] =
  F(f(fa.x), f(fa.y), fa.t)
```

The corresponding Dhall code is:

```dhall
let F : Type → Type
  = λ(A : Type) → { x : A, y : A, t : Bool }
let fmap
  : ∀(A : Type) → ∀(B : Type) → (A → B) → F A → F B
  = λ(A : Type) → λ(B : Type) → λ(f : A → B) → λ(fa : F A) →
    { x = f fa.x, y = f fa.y, t = fa.t }
```

To test:

```dhall
let example : F Natural = { x = 1, y = 2, t = True }
let after_fmap : F Text = fmap Natural Text (λ(x : Natural) → if Natural/even x then "even" else "odd") example
let test = assert : after_fmap === { x = "odd", y = "even", t = True }
```

As another example, let us define `fmap` for a type constructor that involves a union type:

```dhall
let G : Type → Type
  = λ(A : Type) → < Left : Text | Right : A >
let fmap
  : ∀(A : Type) → ∀(B : Type) → (A → B) → G A → G B
  = λ(A : Type) → λ(B : Type) → λ(f : A → B) → λ(ga : G A) →
    merge { Left = λ(t : Text) → (G B).Left t
          , Right = λ(x : A) → (G B).Right (f x)
          } ga
```

Dhall requires the union type's constructors to be explicitly derived from the full union type.
In Haskell or Scala, we would simply write `Left(t)` and `Right(f(x))` and let the compiler fill in the type parameters.
But Dhall requires us to write a complete type annotation such as `< Left : Text | Right : B >.Left t` and `< Left : Text | Right : B >.Right (f x)` in order to specify the complete union type being constructed.

In the code shown above, we were able to shorten those constructors to `(G B).Left` and `(G B).Right`.

### Bifunctors and `bimap`

Bifunctors are type constructors with two type parameters that are covariant in both type parameters.
For example, `type P a b = (a, a, b, Int)` is a bifunctor.

Dhall encodes bifunctors as functions with two curried arguments:

```dhall
let P : Type → Type → Type
  = λ(A : Type) → λ(B : Type) → { x : A, y : A, z : B, t : Integer }
```

Bifunctors have a `bimap` method that transforms both type parameters at once:

```dhall
let bimap
  : ∀(A : Type) → ∀(B : Type) → ∀(C : Type) → ∀(D : Type) → (A → C) → (B → D) → P A B → P C D
  = λ(A : Type) → λ(B : Type) → λ(C : Type) → λ(D : Type) → λ(f : A → C) → λ(g : B → D) → λ(pab : P A B) →
    { x = f pab.x, y = f pab.y, z = g pab.z, t = pab.t }
```

Given `bimap`, one can then define two `fmap` methods that work only on the first or on the second of `P`'s type parameters.

```dhall
let fmap1
  : ∀(A : Type) → ∀(C : Type) → ∀(D : Type) → (A → C) → P A D → P C D
  = λ(A : Type) → λ(C : Type) → λ(D : Type) → λ(f : A → C) →
    bimap A D C D f (identity D)
let fmap2
  : ∀(A : Type) → ∀(B : Type) → ∀(D : Type) → (A → C) → P A B → P A D
  = λ(A : Type) → λ(B : Type) → λ(D : Type) → λ(g : B → D) →
    bimap A B A D (identity A) g
```

Here, we have used the polymorphic identity function defined earlier.

The code for `fmap` and `bimap` can be derived mechanically from the type definition of a functor or a bifunctor.
For instance, Haskell will do that if the programmer just writes `deriving Functor` after the definition.
But Dhall does not have any code generation facilities.

## Typeclasses

Typeclasses can be implemented in Dhall via evidence values used as explicit function arguments.

### `Monoid`

The `Monoid` typeclass is usually defined in Haskell as:

```haskell
class Monoid m where
  mempty :: m
  mappend :: m → m → m
```

In Scala, a corresponding definition is:

```scala
trait Monoid[M] {
 def empty: M
 def combine: (M, M) => M 
}
```

An evidence value needs to contain a value of type `m` and a function of type `m → m → m`.
A Dhall record type containing values of those types could be `{ empty : m, append : m → m → m }`.
A value of that type provides evidence that the type `m` has the required methods for a monoid.

To use the typeclass more easily, it is convenient to define a type constructor `Monoid` such that the above record type is obtained as `Monoid m`:

```dhall
let Monoid = λ(m : Type) → { empty : m, append : m → m → m }
```

With this definition, `Monoid Bool` is the type `{ mempty : Bool, append : Bool → Bool → Bool }`.
Values of that type are evidence values for a monoid structure in the type `Bool`.

Now we can create evidence values for specific types and use them in programs.

Let us implement some `Monoid` evidence values for the types `Bool`, `Natural`, `Text`, and `List`:

```dhall
let monoidBool : Monoid Bool = { empty = True, append = λ(x : Bool) → λ(y : Bool) → x && y }
let monoidNatural : Monoid Natural = { empty = 0, append = λ(x : Natural) → λ(y : Natural) → x + y }
let monoidText : Monoid Text = { empty = "", append = λ(x : Text) → λ(y : Text) → x ++ y }
let monoidList : ∀(a : Type) → Monoid (List a) = λ(a : Type) → { empty = [] : List a, append = λ(x : List a) → λ(y : List a) → x # y }
```

We can now use those evidence values to implement functions with a type parameter constrained to be a monoid.
An example is a function `foldMap` for `List`, written in the Haskell syntax as:

```haskell
foldMap :: Monoid m => (a -> m) -> List a -> m
foldMap f as = foldr (\a -> \b -> append (fa) b) mempty as
```

The corresponding Dhall code is:

```dhall
let foldMap
  : ∀(m : Type) → Monoid m → ∀(a : Type) → (a → m) → List a → m
  = λ(m : Type) → λ(monoid_m : Monoid m) → λ(a : Type) → λ(f : a → m) → λ(as : List a) →
    List/fold a as m (λ(x : a) → λ(y : m) → monoid_m.append (f x) y) monoid_m.empty
```

### `Functor`

The `Functor` typeclass is a constraint for a _type constructor_.
So, the type parameter of `Functor` must be of the kind `Type → Type`.

The required data for an evidence value is a polymorphic `fmap` function for that type constructor.
Let us now package that information into a `Functor` typeclass similarly to how we did with `Monoid`.

Define the type constructor for evidence values:

```dhall
let Functor = λ(F : Type → Type) → { fmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b }
```

Here is a `Functor` evidence value for `List`. The `fmap` function is already available in the Dhall standard prelude:

```dhall
let functorList : Functor List = { fmap = https://prelude.dhall-lang.org/List/map }
```

As another example, let us write the evidence values for the type constructors `F` and `G` shown in the section "Functors and bifunctors":

```dhall
let functorF : Functor F = { fmap = λ(A : Type) → λ(B : Type) → λ(f : A → B) → λ(fa : F A) →
    { x = f fa.x, y = f fa.y, t = fa.t }
  }
let functorG : Functor G = { fmap = λ(A : Type) → λ(B : Type) → λ(f : A → B) → λ(ga : G A) →
    merge { Left = λ(t : Text) → (G B).Left t
          , Right = λ(x : A) → (G B).Right (f x)
          } ga  
  }
```

### `Monad`

The `Monad` typeclass may be defined via the methods `pure` and `bind`.

Define the type constructor for evidence values:

```dhall
let Monad = λ(F : Type → Type) →
  { pure : ∀(a : Type) → a → F a
  , bind : ∀(a : Type) → F a → ∀(b : Type) → (a → F b) → F b
  }
```

As an example, let us define a `Monad` evidence value for `List`:

```dhall
let monadList : Monad List =
  let List/concatMap = https://prelude.dhall-lang.org/List/concatMap
  in
  { pure = λ(a : Type) → λ(x : a) → [x]
  , bind : λ(a : Type) → λ(fa : List a) → λ(b : Type) → λ(f : a → List b) →
    List/concatMap a b f fa
  }
```

We have defined the `Monad` typeclass via the `pure` and `bind` methods.
Let us implement a function that provides the `join` method for any member of `Monad`.

In Haskell, we would define `join` via `bind` as:

```haskell
monadJoin :: Monad F => F (F a) -> F a
monadJoin ffa = bind ffa id
```

In this Haskell code, `id` is an identity function of type `F a → F a`.

The corresponding Dhall code is analogous, except we need to write out all type parameters:

```dhall
let monadJoin = λ(F : Type → Type) → λ(monadF : Monad F) → λ(a : Type) → λ(ffa : F (F a)) →
  monadF.bind (F a) ffa a (identity (F a))  
```

We can use this function to obtain a `join` method for `List` like this:

```dhall
let List/join
  : ∀(a : Type) → List (List a) → List a
  = monadJoin List monadList 
```

### Inheritance of typeclasses

Sometimes one typeclass includes methods from another.
For example, `Semigroup` is similar to `Monoid`: it has the `append` method but no `empty` method.
We could say that the `Monoid` typeclass inherits `append` from `Semigroup` and adds the `empty` method.

To express this kind of inheritance in Dhall, we can use Dhall's record-updating features.
Dhall has the operator `//\\` that combines all fields from two record types into one larger record type.
The corresponding operator `/\` combines fields from two record values.
For example:

```dhall
⊢ { a : Text, b : Bool } //\\ { c : Natural }

{ a : Text, b : Bool, c : Natural }

⊢ { a = 1 } /\ { b = True }

{ a = 1, b = True }
```

In these cases, the field names must be different (otherwise it is a type error).

We can use these operators for making typeclass definitions and evidence values shorter.

Consider this Dhall code for the `Semigroup` typeclass:

```dhall
let Semigroup = λ(m : Type) → { append : m → m → m }
let semigroupText : Semigroup Text = { append = λ(x : Text) → λ(y : Text) → x ++ y }
```

We can use this definition to rewrite the `Monoid` typeclass via "record-based inheritance".
Then the `Semigroup` evidence value for the type `Text` is written as:

```dhall
let Monoid = λ(m : Type) → Semigroup m //\\ { empty : m }
let monoidText : Monoid Text = semigroupText /\ { empty = "" } 
```

Similarly, we may rewrite the `Monad` typeclass to make it more clear that any monad is also a (covariant) functor:

```dhall
let Monad = λ(F : Type → Type) →
  Functor F //\\
      { pure : ∀(a : Type) → a → F a
      , bind : ∀(a : Type) → F a → ∀(b : Type) → (a → F b) → F b
      }
```

As an example, let us define a `Monad` evidence value for `List`:

```dhall
let monadList : Monad List =
  let List/concatMap = https://prelude.dhall-lang.org/List/concatMap
  in functorList /\
      { pure = λ(a : Type) → λ(x : a) → [x]
      , bind : λ(a : Type) → λ(fa : List a) → λ(b : Type) → λ(f : a → List b) →
        List/concatMap a b f fa
      }
```

## Church encoding for recursive types and type constructors

### Recursion scheme

Dhall does not directly support defining recursive types or recursive functions.
The only supported recursive type is a built-in `List` type. 
However, user-defined recursive types and a certain limited class of recursive functions can be implemented in Dhall via the Church encoding techniques. 

A beginner's tutorial about Church encoding is in the Dhall documentation: https://docs.dhall-lang.org/howtos/How-to-translate-recursive-code-to-Dhall.html
Here we will summarize that technique more briefly.

In languages that directly support recursive types, one defines types such as lists or trees via "type equations".
That is, one writes definitions of the form `T = F T` where `F` is some type constructor and `T` is the type being defined.

For example, suppose `T` is the type of lists with integer values.
A recursive definition of `T` in Haskell could look like this:

```haskell
data T = Nil | Cons Int T
```

This definition of `T` has the form of a "type equation", `T = F T`, where `F` is a (non-recursive) type constructor defined by: 

```haskell
type F a = Nil | Cons Int a
```

The type constructor `F` is called the **recursion scheme** for the definition of `T`.

Dhall does not accept recursive type equations, but it will accept the definition of `F` (because it is non-recursive).
The definition of `F` is written in Dhall as:

```dhall
let F = λ(a : Type) → < Nil |  Cons : { head : Integer, tail : a } >
```

Then the Church encoding of `T` is written in Dhall as:

```dhall
let C = ∀(r : Type) → (F r → r) → r 
```

The type `C` is still non-recursive, so Dhall will accept this definition.

Note that we are using `∀(r : Type)` and not `λ(r : Type)` when we define `C`.
The type `C` is not a type constructor; it is a type of a function with a type parameter.
When we define `F` as above, it turns out that the type `C` equivalent to the type of (finite) lists with integer values.

### Simple recursive types

The Church encoding construction works generally for any recursion scheme `F`.
Given a recursion scheme `F`, one defines a non-recursive type `C = ∀(r : Type) → (F r → r) → r`.
Then the type `C` is equivalent to the type `T` that we would have defined by `T = F T` in a language that supports recursively defined types.

It is far from obvious why a type of the form `∀(r : Type) → (F r → r) → r` is equivalent to a type `T` defined recursively by `T = F T`.
More precisely, the type `∀(r : Type) → (F r → r) → r` is equivalent to the _least fixed point_ of the type equation `T = F T`.
A mathematical proof of that property is given in the paper ["Recursive types for free"](https://homepages.inf.ed.ac.uk/wadler/papers/free-rectypes/free-rectypes.txt) by P. Wadler.
In this book, we will focus on the practical uses of Church encoding.

Here are some examples of Church encoding for simple recursive types.

The type `ListInt` (a list with integer values):

```dhall
let F = λ(r : Type) → < Nil | Cons : { head : Integer, tail : r } >
let ListInt = ∀(r : Type) → (F r → r) → r
```

We can use certain type equivalence identities to rewrite the type `ListInt` in a form more convenient for practical applications.

The first type equivalence is that a function from a union type is equivalent to a product of functions.
So, the type `F r → r`, written in full as:

```dhall
< Nil | Cons : { head : Integer, tail : r } > → r
```

is equivalent to a pair of functions of types `< Nil > → r` and `{ head : Integer, tail : r } → r`.

The type `< Nil >` is a named unit type, so `< Nil > → r` is equivalent to just `r`.

The second type equivalence is that a function from a record type is equivalent to a curried function.
For instance, the type:

```dhall
{ head : Integer, tail : r } → r
```

is equivalent to `Integer → r → r`.

Using these type equivalences, we may rewrite the type `ListInt` as:

```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r
```

It is now less apparent that we are dealing with a type of the form `∀(r : Type) → (F r → r) → r`.
However, working with curried functions needs shorter code than working with union types and record types.

The type `TreeInt` (a binary tree with integer leaf values) is defined in Dhall by:

```dhall
let F = λ(r : Type) → < Leaf: Integer | Branch : { left : r, right : r } >
let TreeInt = ∀(r : Type) → (F r → r) → r
```

Since `F r` is a union type with two parts, the type of functions `F r → r` can be replaced by a pair of functions.

We can also replace functions from a record type by curried functions.

Then we obtain an equivalent definition of `TreeInt` that is easier to work with:

```dhall
let TreeInt = ∀(r : Type) → (Integer → r) → (r → r → r) → r
```

These examples show that any type constructor `F` defined via products (records) and co-products (union types) will give rise to a Church encoding that can be rewritten purely via curried functions, without using any records or union types.

We will call that the **curried form** of the Church encoding.
The curried form is more convenient for practical programming.
But when we are looking for general properties of Church encodings, it is better to use the form `∀(r : Type) → (F r → r) → r`.

## Working with Church-encoded data

A Church-encoded data type is always of the form `∀(r : Type) → ... → r`, that is, a curried higher-order function with a type parameter.
A value `x` of that type is a function whose code may be written like this:

```dhall
let x
  : ∀(r : Type) → (F r → r) → r
  = λ(r : Type) → λ(frr : F r → r) →
     let y : r = ... -- Need to insert some code here.
        in y
```

Working with data encoded in this way is not straightforward.
It takes some work to figure out convenient ways of creating values of those types and of working with them.

We will now show how to implement constructors for Church-encoded data, how to perform aggregations (or "folds"), and how to implement pattern matching.

For simplicity, we now consider an ordinary Church-encoded type `C = ∀(r : Type) → (F r → r) → r` defined via a recursion scheme `F`.
Later we will see that the same techniques work for Church-encoded type constructors and other more complicated types.

An important requirement is that the recursion scheme `F` should be a _covariant_ type constructor.
If this is not so, Church encoding does not work as expected.

We will assume that `F` has a known and lawful `fmap` function that we denote by `fmapF`.
So, all Dhall code below assumes a given set of definitions of this form:

```dhall
let F : Type → Type = ...

let C = ∀(r : Type) → (F r → r) → r

let fmapF : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b = ...
```

### The isomorphism `C = F C`: the functions `fix` and `unfix` 

The type `C` is a fixed point of the type equation `C = F C`.
This means we should have two functions, `fix : F C → C` and `unfix : C → F C`, that are inverses of each other.
These two functions implement an isomorphism between `C` and `F C`.

Because this is a general property of all Church encodings, we can write the code for `fix` and `unfix` generally, for all recursion schemes `F` and the corresponding types `C`.

The basic technique of working directly with any Church-encoded data `c : C` is to use `c` as a curried higher-order function.
That function has two arguments: a type parameter `r` and a function of type `F r → r`.
If we need to compute a value of some other type `D` out of `c`, we specify `D` as the type parameter to `c` and then provide a function of type `F D → D` as the second argument.
As long as we are able to provide a function of type `F D → D`, we can convert `c` into a value of type `D`:

```dhall
let d : D =
    let fdd : F D → D = ...
        in c D fdd
```

We will use this technique to implement `fix` and `unfix`.
For clarity, we split the code into smaller chunks annotated by their types:

```dhall
let fix : F C → C = λ(fc : F C) → λ(r : Type) → λ(frr : F r → r) →
    let c2r : C → r = λ(c : C) → c r frr
    let fmap_c2r : F C → F r = fmapF C r c2r
    let fr : F r = fmap_c2r fc
        in frr fr

let fmap_fix : F (F C) → F C = fmapF (F C) C fix

let unfix : C → F C = λ(c : C) → c (F C) fmap_fix
```

The paper ["Recursive types for free"](https://homepages.inf.ed.ac.uk/wadler/papers/free-rectypes/free-rectypes.txt) proves via parametricity that `fix` and `unfix` are inverses of each other.

Another property proved in that paper is the identity `c C fix = c` for all `c : C`.

### Constructors

The function `fix` (sometimes also called `build`) provides a general way of creating values of type `C`.

### Pattern matching

The function `unfix` (sometimes also called `unroll` or `unfold`) provides a general way of pattern matching on values of type `C`.

### Aggregations ("folds")

The type `C` itself is the type of fold-like functions.

To see the similarity, compare the curried form of the `ListInt` type:

```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r
```

with the type signature of the `foldRight` function for the type `List Integer`:

```dhall
foldRight : ∀(r : Type) → (List Integer) → r → (Integer → r → r) → r
```

So, implementing `foldRight` for the Church-encoded type `ListInt` is simple:

```dhall
let foldRight
  : ∀(r : Type) → ListInt → r → (Integer → r → r) → r
  = λ(r : Type) → λ(p : ListInt) → λ(init : r) → λ(update : Integer → r → r) →
    p r init update
```

or even shorter:

```dhall
let foldRight
  : ∀(r : Type) → ListInt → r → (Integer → r → r) → r
  = λ(r : Type) → λ(p : ListInt) → p r
```

The similarity between the types of `foldRight` and `ListInt` becomes more apparent if we flip the curried arguments of `foldRight`:


```dhall
flip_foldRight
  : ListInt → ∀(r : Type) → r → (Integer → r → r) → r
  = λ(p : ListInt) → p
```

This is just an identity function of type `ListInt → ListInt`.

We note that `foldRight` is a non-recursive function.
In this way, the Church encoding enables fold-like aggregations to be implemented without recursion.


### Where did the recursion go?

The technique of Church encoding may be perplexing. If we are actually working with recursive types and recursive functions, why do we no longer
see any recursion in the code? In `foldRight`, why is there no code that iterates over a list of integers in a loop?

## Church encodings for more complicated types

### Mutually recursive types

If two or more types are defined recursively through each other, one needs a separate recursion scheme and a separate the Church encoding for each of the types.

As an example, consider this Haskell definition:

```haskell
data Layer = Name String | OneLayer Layer | TwoLayers Layer2 Layer2
data Layer2 = Name2 String | ManyLayers [ Layer ]   
```

The type `Layer` is defined via itself and `Layer2`, while `Layer2` is defined via `Layer`.

We need two recursion schemes (`F` and `F2`) to describe this definition. In terms of the recursion schemes, the type definitions should look like this:

```haskell
data Layer = Layer (F Layer Layer2)
data Layer2 = Layer2 (F2 Layer Layer2)
```

We will achieve this formulation if we define `F` and `F2` (still in Haskell) by:

```haskell
data F a b = Name String |  OneLayer a | TwoLayers b b
data F2 a b = Name2 String | ManyLayers [ a ]
```

The recursion schemes `F` and `F2` are non-recursive type constructors with two type parameters each. The Dhall code for this example is:

```dhall
let F = λ(a : Type) → λ(b : Type) → < Name : Text | OneLayer : b | TwoLayers: { left : b, right : b } >
let F2 = λ(a : Type) → λ(b : Type) → < Name2 : Text | ManyLayers : List a >
```

Then we define the types `Layer` and `Layer2` in Dhall via the Church encodings:

```dhall
let Layer  = ∀(a : Type) → ∀(b : Type) → (F a b → a) → (F2 a b → b) → a
let Layer2 = ∀(a : Type) → ∀(b : Type) → (F a b → a) → (F2 a b → b) → b
```

The definitions appear very similar, except for the output types of the functions.
But that difference is crucial.

### Recursive type constructors

A recursive definition of a type constructor is not of the form `T = F T` but of the form `T a = F (T a) a`, or `T a b = F (T a b) a b`, etc., with extra type parameters.

For this to work, the recursion scheme `F` must have one more type parameter than `T`.

For example, take the Haskell definition of a binary tree with leaves of type `a`:

```haskell
data Tree a = Leaf a | Branch (Tree a) (Tree a)
```

The corresponding recursion scheme `F` is:

```haskell
data F a r = Leaf a | Branch r r
```

The Dhall code for `F` is:

```dhall
let F = λ(a : Type) → λ(r : Type) →
   < Leaf : a | Branch : { left : r, right : r } >
```

The Church encoding for `Tree` looks like this:

```dhall
let Tree = λ(a : Type) → ∀(r : Type) → (F a r → r) → r
```

It is important that the type parameter `a` is used with `λ`.
This makes `Tree` a type constructor.

The quantified type `∀(r : Type)` is not a type parameter of `Tree`; it is part of the definition of the type of `Tree`.

The code is written similarly in case of more type parameters.
Consider a Haskell definition of a binary tree with two type parameters and two different kinds of leaf:

```haskell
data TreeAB a b = LeafA a | LeafB b | Branch (TreeAB a b) (TreeAB a b)
```

The corresponding recursion scheme is:

```haskell
data F a b r = LeafA a | LeafB b | Branch r r
```

The Dhall code for this example is:

```dhall
let F = λ(a : Type) → λ(b : Type) → λ(r : Type) →
   < LeafA : a | LeafB : b | Branch : { left : r, right : r } >
let TreeAB = λ(a : Type) → λ(b : Type) → ∀(r : Type) → (F a b r → r) → r
```

### Existential types

### Co-Church encoding of co-inductive types

### Church encodings of nested types and GADTs

## Constructing functors and contrafunctors from parts

## Filterable functors and contrafunctors

## Applicative covariant and contravariant functors

## Monads

## Traversable functors

## Free monads

## Free applicative functors

## Dhall as a scripting DSL