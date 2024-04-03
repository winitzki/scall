# Advanced Functional Programming in System Fω using Dhall

## Preface

This book is an advanced-level tutorial on [Dhall](https://dhall-lang.org) for software engineers already familiar with the functional programming (FP) paradigm,
as practiced in languages such as OCaml, Haskell, Scala, and others.

Although most code examples are in Dhall, the material of the book has a wider applicability.
It describes a certain flavor of purely functional programming (without side effects and with guaranteed termination) that can be implemented in any FP language that supports polymorphic types.

Dhall is a powerful, purely functional programming language that has several applications:
- a generator for flexible, programmable, but strictly validated YAML and JSON configuration files
- an industry-strength System Fω interpreter for studying various language-independent aspects of functional programming
- a high-level scripting DSL interfacing with a runtime that implements low-level details 

This book focuses on the last two applications.

## Overview of Dhall

Dhall is a language for programmable configuration files, primarily intended to replace templated JSON, templated YAML, and other programmable or templated configuration formats.

The Dhall project documentation covers many aspects of using Dhall with YAML and JSON.
This book focuses on other applications of Dhall.

This text follows the [Dhall standard 23.0.0](https://github.com/dhall-lang/dhall-lang/releases/tag/v23.0.0).
For an introduction to Dhall, see [Dhall's official documentation](https://docs.dhall-lang.org).

Here is an example of a Dhall program:

```dhall
let f = λ(x : Natural) → λ(y : Natural) → x + y + 2
let id = λ(A : Type) → λ(x : A) → x
  in f 10 (id Natural 20)
    -- This evaluates to 32 of type Natural.
```

See the [Dhall cheat sheet](https://docs.dhall-lang.org/howtos/Cheatsheet.html) for more examples of basic Dhall usage.

The [Dhall standard prelude](https://prelude.dhall-lang.org/) defines a number of general-purpose functions
such as `Natural/lessThan` and `List/map`.

### Safety guarantees

The Dhall interpreter guarantees that any well-typed Dhall program will be evaluated in finite time to a unique, correct "normal form" expression.
Evaluation of a well-typed Dhall program will never create infinite loops or throw exceptions due to missing or invalid values or wrong types at run time.

Invalid programs will be rejected at the type-checking phase (analogous to "compile time").
(The type-checking itself is also guaranteed to terminate.)
The price for those safety guarantees is that the Dhall language is _not_ Turing-complete.
But this is not a significant limitation for the intended scope of Dhall usage, as this book will show.

### Dhall and System Fω

From the point of view of type theory, Dhall implements a type system similar to System Fω with some additional features, using a Haskell-like syntax.

For a theoretical introduction to various forms of lambda calculus, System F, and System Fω, see:

- [D. Rémy. Functional programming and type systems](https://gallium.inria.fr/~remy/mpri/)
- [Lectures on Advanced Functional Programming, Cambridge, 2014-2015](https://www.cl.cam.ac.uk/teaching/1415/L28/materials.html), in particular the [notes on lambda calculus](https://www.cl.cam.ac.uk/teaching/1415/L28/lambda.pdf)

Most of that theory is beyond the scope of this book.
Instead, the book focuses on issues arising in practical programming.

## Differences from other FP languages

### Identifiers

Identifiers may contain slash characters; for example, `List/map` is a valid name.

This is helpful when organizing library functions into modules.
One can have suggestive names such as `List/map`, `Optional/map`, etc.

### Number types

Integers must have a sign (`+1` or `-1`) while `Natural` numbers may not have a sign (`123`).

Values of types `Natural` and `Integer` have unbounded size.
There is no overflow.
Dhall does not support 32-bit or 64-bit integers with overflow, as is common in other programming languages.

Dhall supports other numeric types, such as `Double` or `Time`, but there is very little one can do with those values other than print them.
For instance, Dhall does not directly support floating-point arithmetic on `Double` values.

### Product types

Product types are implemented only through records.
For example, `{ x = 1, y = True }` is a record value, and its type is `{ x : Natural, y : Bool }` (a "record type").

There are no built-in tuple types, such as Haskell's and Scala's `(Int, String)`.
Records with names must be used instead.
For instance, the (Haskell / Scala) tuple type `(Int, String)` may be translated to Dhall as the record type `{ _1 : Int, _2 : String }`.

Records can be nested: the record value `{ x = 1, y = { z = True, t = "abc" } }` has type `{ x : Natural, y : { z : Bool, t : Text } }`.

Record types are "structural": two record types are distinguished only via their field names and types, and record fields are unordered.
There is no way of assigning a permanent name to the record type itself, as it is done in Haskell and Scala in order to distinguish one record type from another.

For example, the values `x` and `y` have the same type in the following Dhall code:

```dhall
let RecordType1 = { a : Natural, b : Bool }
let x : RecordType1 = { a = 1, b = True }
let RecordType2 = { b : Bool, a : Natural }
let y : RecordType2 = { a = 2, b = False }
```

The names `RecordType1` and `RecordType2` are no more than type aliases.
Dhall does not distinguish them from the literal type expression `{ a : Natural, b : Bool }`.
(The order of record fields is not significant.) 

### Co-product types

Co-product types are implemented via tagged unions, for example: `< X : Natural | Y : Bool >`.
Here `X` and `Y` are **constructors** for the given union type.

Values of co-product types are created via constructor functions.
Constructor functions are written using record-like access notation.
For example, the expression `< X : Natural | Y : Bool >.X` is viewed by Dhall as a function of type `Natural → < X : Natural | Y : Bool >`. 
Applying that function to a value of type `Natural` will create a value of the union type `< X : Natural | Y : Bool >`:

```dhall
let x : < X : Natural | Y : Bool > = < X : Natural | Y : Bool >.X 123
```

Constructors may have at most one argument.
Constructors with multiple curried arguments (as in Haskell: `P1 Int Int | P2 Bool`) are not supported in Dhall.
Record types must be used instead of multiple arguments.
For example, Haskell's union type `P1 Int Int | P2 Bool` may be replaced by Dhall's union type `< P1 : { _1 : Integer, _2 : Integer }, P2 : Bool >`.

Union types can have empty constructors.
For example, the union type `< X : Natural | Y >` has values written either as `< X : Natural | Y >.X 123` or `< X : Natural | Y >.Y`.
Both these values have type `< X : Natural | Y >`.

Union types are "structural": two union types are distinguished only via their constructor names and types, and constructors are unordered.
There is no way of assigning a permanent name to the union type itself, as it is done in Haskell and Scala in order to distinguish that union type from others.

For convenience, a Dhall program may define names for types, for example:

```dhall
let MyType1 = < X : Natural | Y : Bool >
let x : MyType1 = MyType1.X 123
```

But the name `MyType1` is no more than a type alias.
Dhall will consider `MyType1` to be the same as the literal type expressions `< X : Natural | Y : Bool >` and `< Y : Bool | X : Natural >`.
(The order of a union type's constructors is not significant.) 

### Pattern matching

Pattern matching is available for union types as well as for the built-in `Optional` types.
Dhall implements pattern matching via `merge` expressions.
The `merge` expressions are similar to `case` expressions in Haskell and `match/case` expressions in Scala.

One difference is that each case of a `merge` expression must specify an explicit function with a full type annotation.

As an example, consider a union type defined in Haskell by:

```haskell
data P = X Int | Y Bool | Z
```

A function `toString` that prints a value of that type can be written in Haskell as:

```haskell
toString :: P -> String
toString x = case x of
  X x -> "X " ++ show x
  Y y -> "Y " ++ show y
  Z -> "Z"
```

The corresponding type is defined in Dhall by:

```dhall
let P = < X : Natural | Y : Bool | Z >
```

Here is the Dhall code for a function `toText : < X : Natural | Y : Bool | Z > → Text` that prints a value of type `P`:

```dhall
let toText : P → Text = λ(x : P) →
  merge { X = λ(x : Natural) → "X " ++ Natural/show x
        , Y = λ(y : Bool) → "Y " ++  (if y then "True" else "False")
        , Z = "Z"
        } x
```

Another example of using Dhall's `merge` is when implementing a `zip` function for `Optional` types:

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

### The void type and its use

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

Of course, the function `absurd` will never be actually applied to an argument value in any program, because one cannot construct a value of type `< >`.
Nevertheless, the existence of a function of type `∀(A : Type) → < > → A` is useful in some situations.

The type signature of `absurd` can be rewritten equivalently as:

```dhall
let absurd : < > → ∀(A : Type) → A
  = λ(x : < >) → λ(A : Type) → merge {=} x : A 
```

This type signature suggests a type equivalence between `< >` and the function type `∀(A : Type) → A`.

Indeed, the type `∀(A : Type) → A` is void (this can be proved via parametricity arguments).
So, the type expression `∀(A : Type) → A` is equivalent to `< >` and can be used equally well to denote the void type.

Because any Dhall expression is fully parametrically polymorphic, parametricity arguments will apply to all Dhall code.
See the Appendix "Naturality and parametricity" for more details.

One use case for the void type is to provide a "TODO" functionality.
While writing Dhall code, we may want to leave a certain value temporarily unimplemented.
However, we still need to satisfy Dhall's type checker and provide a value that appears to have the right type.

To achieve that, we write our code as a function with an argument of the void type:

```dhall
let our_program = λ(void : < >) →  .... 
```

Now suppose we need a value `x` of any given type `X` in our code, but we do not yet know how to implement that value.
Then we write `let x : X = absurd void X` in the body of `our_program`.
The typechecker will accept this program.
Of course, we can never supply a value for the `void : < >` argument.
So, our program will not be evaluated until we replace the `absurd void X` by correct code computing a value of type `X`.

So, we can shorten the code if we define `let TODO = absurd void`.
We can then write `TODO X` and pretend to obtain a value of any type `X`.

Note that the partially applied function `absurd void` is a value of type `∀(A : Type) → A`.
So, we may directly require `TODO` as an argument of type `∀(A : Type) → A` in our program:

```dhall
let our_program = λ(TODO : ∀(A : Type) → A) →  .... let x = TODO X in ....
```

### The unit type

Dhall's empty record type `{}` is a natural way of defining a unit type.
The type `{}` has only one value, written as `{=}` (an empty record with no fields).

An equivalent way of denoting the unit type is via a union type with a single constructor, for example: `< One >` (or with any other name instead of "One").
The type `< One >` has a single distinct value, denoted in Dhall by `< One >.One`.
In this way, one can define differently named unit types for convenience.

An equivalent definition is the function type `∀(A : Type) → A → A`.
This is another example of automatic parametricity in Dhall.
The only way of implementing a function with that type is `λ(A : Type) → λ(x : A) → x`.
There is no other, inequivalent Dhall code that could implement a different function of that type.

### Type constructors

Type constructors in Dhall are written as functions from `Type` to `Type`.

In Haskell or Scala, one would define a type constructor as `type PairAAInt a = (a, a, Int)`.
The analogous type constructor is encoded in Dhall as an explicit function, taking a parameter `a` of type `Type` and returning another type.

Because Dhall does not have nameless tuples, we will use a record with field names `_1`, `_2`, and `_3`:

```dhall
let PairAAInt = λ(a : Type) → { _1 : a, _2 : a, _3 : Integer }
```

The output of the `λ` function is a record type `{ _1 : a, _2 : a, _3 : Integer }`.

The type of `PairAAInt` itself is `Type → Type`.
For more clarity, we may write that as a type annotation:

```dhall
let PairAAInt : Type → Type = λ(a : Type) → { _1 : a, _2 : a, _3 : Integer }
```

Type constructors involving more than one type parameter are usually written as curried functions.

Here is an example of how we could define a type constructor similar to Haskell's and Scala's `Either`:

```dhall
let Either = λ(a : Type) → λ(b : Type) → < Left : a | Right : b >
```

The type of `Either` is `Type → Type → Type`.

As with all Dhall types, type constructor names such as `PairAAInt` or `Either` are no more than type aliases.
Dhall distinguishes types and type constructors not by assigned names but by the type expressions themselves.

### Function types

Function types are written as `∀(x : arg_t) → res_t`, where `arg_t` is the argument type and `res_t` is a type expression that describes the type of the result value.

Function _values_ corresponding to that function type are written like this: `λ(x : arg_t) → expr`, where `expr` is a function body (which must be of type `res_t`).

Note that the type `res_t` may or may not use the bound variable `x`.
In simple cases, `res_t` will not depend on `x`.
Then the function type can be written in a simpler form: `arg_t → res_t`.

For example, consider a function that adds `1` to a `Natural` argument:

```dhall
let inc = λ(x : Natural) → x + 1
```

We may write a type annotation to `inc` like this:

```dhall
let inc : Natural → Natural = λ(x : Natural) → x + 1
```

We may also write a fully detailed type annotation if we like:

```dhall
let inc : ∀(x : Natural) → Natural = λ(x : Natural) → x + 1
```

All functions have one argument.
To implement functions with more than one argument, one can use curried functions or record types.

For example, a function that adds 3 numbers can be written in different ways according to convenience:

```dhall
let add3_curried : Natural → Natural → Natural → Natural
  = λ(x : Natural) → λ(y : Natural) → λ(z : Natural) → x + y + z

let add3_record : { x : Natural, y : Natural, z : Natural } → Natural
  = λ(record : { x : Natural, y : Natural, z : Natural }) → record.x + record.y + record.z
```

#### Functions with type parameters

The most often used case where a function's result type depends on an argument is when functions have type parameters.

For instance, consider a function that takes a pair of `Natural` numbers and swaps the order of numbers in the pair.
We use a record type `{ _1 : Natural, _2 : Natural }` to represent a pair of `Natural` numbers.
For brevity, we will define a value `Pair` to denote that type:

```dhall
let Pair : Type = { _1 : Natural, _2 : Natural }
let swap : Pair → Pair = λ(p : Pair) → { _1 = p._2, _2 = p._1 }
```

Now we generalize `swap` to support two arbitrary types of values in the pair.
The two types will become type parameters (`a` and `b`).
The type parameters are given as additional curried arguments.
The new implementation of `Pair` and `swap` becomes:

```dhall
let Pair : Type → Type → Type = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }
let swap : ∀(a : Type) → ∀(b : Type) → Pair a b → Pair b a
  = λ(a : Type) → λ(b : Type) → λ(p : Pair a b) → { _1 = p._2, _2 = p._1 }
```

In this example, the type signature of `swap` has two type parameters (`a`, `b`) and the output type depends on those type parameters.

In Dhall, all function arguments (including all type parameters) must be introduced explicitly via the `λ` syntax, with explicitly given types.

However, a `let` binding does not necessarily require a type annotation.
We may just write `let Pair = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }`.
This is the only type inference currently implemented in Dhall.
For complicated type signatures, it helps to write type annotations because type errors will be detected earlier.

## Miscellaneous features

Multiple `let x = y in z` bindings may be written next to each other without writing `in`, and type annotations may be omitted.
For example:

```dhall
let a = 1
let b = 2
  in a + b  -- This evaluates to 3.
```

Because of this feature, we will write snippets of Dhall code in the form `let a = ...` without the trailing `in`.
It is implied that those `let` declarations are part of a larger Dhall program.

We can also use a standalone `let` declaration in the Dhall interpreter (the syntax is `:let`).
For instance, we may define the type constructor `Pair` shown above:

```dhall
$ dhall repl
Welcome to the Dhall v1.42.1 REPL! Type :help for more information.
⊢ :let Pair = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }

Pair : ∀(a : Type) → ∀(b : Type) → Type
```

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
let List/map = https://prelude.dhall-lang.org/List/map
in List/map Natural Natural (λ(x : Natural) → x + 1) [1, 2, 3]
   -- Returns [2, 3, 4].
```

A polymorphic identity function can be written (with a complete type annotation) as:

```dhall
let identity : ∀(A : Type) → ∀(x : A) → A 
  = λ(A : Type) → λ(x : A) → x
```

The polymorphic type of the standard `fmap` function may be written as:

```dhall
∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b
```

Dhall does not require capitalizing the names of types and type parameters.
In this book, we will usually capitalize type constructors (such as `List`) but not simple type parameters (`a`, `b`, etc.).

### Type inference

Dhall has almost no type inference.
The only exception are the `let` bindings, such as `let x = 1 in ...`, where the type annotation for `x` may be omitted.
Other than in `let` bindings, all types of bound variables must be written explicitly.

Although this makes Dhall programs more verbose, it makes for less "magic" in the syntax.
In particular, Dhall requires us to write out all type parameters and all type quantifiers, carefully distinguishing between `∀(x : A)` and `λ(x : A)`.
This verbosity may help in learning some of the more advanced concepts of functional programming.

### Strict / lazy evaluation

All well-typed functions in Dhall are total (never partial).
For instance, a pattern-matching expression will not typecheck unless it handles all parts of the union type being matched.

The Dhall language always typechecks all terms and evaluates all well-typed terms to a normal form.
There is no analog of Haskell's "bottom" (or "undefined") value, no "null" values, no exceptions or other run-time errors.
All errors are detected at the typechecking stage (analogous to "compile-time" in other languages).

For this reason, there is no difference between eager ("strict") and lazy ("non-strict") values in Dhall.
One can equally well imagine that all Dhall values are lazily evaluated, or that they are all eagerly evaluated.
The final result of evaluating a Dhall program will be the same. 

For example, any well-typed Dhall program that returns a value of type `Natural` will always return a _literal_ `Natural` value.
This is because there is no other normal form for `Natural` values, and a well-typed Dhall program always evaluates to a normal form.

In addition, if that Dhall program is self-contained (has no external imports), it will always return _the same_ `Natural` value.
The program cannot return a `Natural` value that will be computed "later", or an "undefined" `Natural` value, or a random `Natural` value, or anything else like that. 

However, it is important that Dhall's _typechecking_ is eager.
A type error in defining a variable `x` (for example, `let x : Natural = "abc"`) will prevent the entire program from evaluating, even if that `x` is never used.

### No computations with custom data

In Dhall, the majority of built-in types (`Text`, `Double`, `Bytes`, `Date`, etc.) are completely opaque to the user.
The user may specify literal values of those types but can do little else with those values.

- `Bool` values support the boolean operations and can be used as conditions in `if` expressions.
- `Natural` numbers can be added, multiplied, and compared for equality.
- `List` values may be concatenated and support some other functions (`List/map`, `List/length` and so on).
- `Text` strings may be concatenated and support a search/replace operation.
- The types `Natural`, `Integer`, `Double`, `Date`, `Time`, `TimeZone` may be converted to `Text`.

Dhall cannot compare `Text` strings for equality or compute the length of a `Text` string.
Neither can Dhall compare `Double` or the date / time types with each other.
Comparison functions are only available for `Bool` and `Natural` types.
(Comparison functions for `Integer` is defined in the standard prelude.)

Another difference from most other FP languages is that Dhall does not support recursive definitions (neither for types nor for values).
The only recursive type directly supported by Dhall is the built-in type `List`.
The only way to write a loop is to use the built-in functions `List/fold` and `Natural/fold` and functions derived from them.

User-defined recursive types and functions must be encoded in a non-recursive way.
Later chapters in this book will show how to use the Church encoding for that purpose.
In practice, this means the user is limited to finite data structures and fold-like functions on them.
General recursion is not possible (because it cannot guarantee termination).

Dhall is a purely functional language with no side effects.
There are no mutable values, no exceptions, no multithreading, no writing to disk, etc.

A Dhall program may contain only a single expression that evaluates to a normal form.
The resulting normal form can be used via imports in another Dhall program, or converted to JSON, YAML, and other formats.

### Modules and imports

Dhall has a simple file-based module system.
Each Dhall file must contain the definition of a _single_ Dhall value (often in the form `let x = ... in ...`).
That single value may be imported into another Dhall file by specifying the path to the first Dhall file.
The second Dhall file can directly use that value as a sub-expression.
For convenience, the imported value may be assigned to a variable with a meaningful name.

Here is an example: the first file contains a list of numbers, and the second file contains code that computes the sum of those numbers.

```dhall
-- This file is `/tmp/first.dhall`.
[1, 2, 3, 4]
```

```dhall
-- This file is `/tmp/sum.dhall`.
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

Although a Dhall file has only one value, that value may be a record with many fields.
Record fields may contain values and/or types.
In that way, a Dhall module may export a number of values and/or types:

```dhall
-- This file is `/tmp/SimpleModule.dhall`.
let UserName = Text
let UserId = Natural
let printUser = λ(name : UserName) → λ(id : UserId) → "User: ${name}[${id}]"

let validate : Bool = ./NeedToValidate.dhall -- Import that value from another module.
let test = assert : validate === True   -- Cannot import this module unless `validate` is `True`.

in {
  UserName,
  UserId,
  printUser,
}
```

When this Dhall file is evaluated, the resulting value is a record of type `{ UserName : Type, UserId : Type, printUser : Text → Natural → Text }`.
So, this module exports two types (`UserName`, `UserId`) and a function `printUser`.

We can use this module in another Dhall file like this:

```dhall
-- This file is `/tmp/UseSimpleModule.dhall`.
let S = ./SimpleModule.dhall -- Just call it S for short.
let name : S.UserName = "first_user"
let id : S.UserId = 1001
let printed : Text = S.printUser name id
... -- Continue writing code.
```

In the file `UseSimpleModule.dhall`, we use the types and the values exported from `SimpleModule.dhall`.
The code will not compile unless all types match, including the imported values.

Note that all fields of a Dhall record are always public.
To make values in a Dhall module private, we simply do not put them into the final exported record.
Values declared using `let x = ...` inside a Dhall module will not be exported.

In the example just shown, the file `SimpleModule.dhall` defined the values `test` and `validate`.
Those values are type-checked and computed inside the module but not exported.
In this way, sanity checks or unit tests included within the module will be validated but will remain invisible to other modules.

The Dhall import system implements strict limitations on what can be imported to ensure that users can prevent malicious code from being injected into a Dhall program.
See [this documentation](https://docs.dhall-lang.org/discussions/Safety-guarantees.html) for more details.

#### Frozen imports and caching

Imports from files, from Internet URLs, and from environment variables constitutes a limited form of "read-only" side effects in Dhall.

For example, Dhall's tests use [a randomness source](https://test.dhall-lang.org/random-string), which is a test-only server that returns a new random string each time it is called.
This Dhall program:

```dhall
https://test.dhall-lang.org/random-string as Text
```
will return a different result each time it is evaluated.

```bash
$ echo "https://test.dhall-lang.org/random-string as Text" | dhall
''
Gajnrpgc4cHWeoYEUaDvAx5qOHPxzSmy
''
scall (feature/tutorial) $ echo "https://test.dhall-lang.org/random-string as Text" | dhall
''
tH8kPRKgH3vgbjbRaUYPQwSiaIsfaDYT
''
```

If a Dhall program needs to guarantee that imported code remains unchanged, the import expression can be annotated by the import's SHA256 hash value.
Such imports are called "frozen".
Dhall will refuse to process a frozen import if the external resource has a different SHA256 hash value than specified in the Dhall code.

For example, one of the standard tests for Dhall includes the following file called `simple.dhall` that contains just the number `3`:

```dhall
-- simple.dhall
3
```
That file may be imported via the following frozen import:

```dhall
./simple.dhall sha256:15f52ecf91c94c1baac02d5a4964b2ed8fa401641a2c8a95e8306ec7c1e3b8d2
```
This import expression is annotated by the SHA256 hash value corresponding to the Dhall expression `3`.
If the user modifies the file `simple.dhall` to contain a Dhall expression that evaluates to something other than `3`, the hash value will be different and the frozen import will fail.

The hash value is computed from the _normal form_ of a Dhall expression, and the normal form is computed only after successful type-checking.
For this reason, the hash value remains unchanged under refactoring.
For instance, we may add or remove comments; reformat the file; change the order of fields in records; rename, add, or remove local variables; change import URLs; etc.
The hash value will remain the same as long as the final evaluated expression in its normal form remains the same.

## Some features of the Dhall type system

### Working with records polymorphically

"Polymorphic records" is a feature of some programming languages where, say, a record of type `{ x : Natural, y : Bool }` is considered to be a subtype of the record type `{ y : Bool }`.
A function that requires its argument to have type `{ y : Bool }` will then accept an argument of type `{ x : Natural, y : Bool }`.
(The value `x` will be simply ignored.)

In those languages, the record type `{ y : Bool }` is actually treated as the type of "any record having a Boolean field `y` and possibly other unknown fields that we will ignore".

Dhall supports neither subtyping nor polymorphic records, but does include some limited facilities to make working with records easier.

TODO

### The `assert` keyword and equality types

For types other than `Bool` and `Natural`, equality testing is not available as a function.
However, values of any types may be tested for equality at compile time via Dhall's `assert` feature.
That feature is designed for basic sanity checks:

```dhall
let x : Text = "123"
let _ = assert : x === "123"
  in x ++ "1"
    -- Returns "1231".
```

The `assert` construction is a special Dhall syntax that implements a limited form of the "equality type" (known from dependently typed languages).

In other words, the Dhall expression `a === b` is a special sort of type.
(The Unicode symbol `≡` may be used instead of `===`.)

The type `a === b` has no values (is void) if `a` and `b` have different normal forms (as Dhall expressions).
For example, the types `1 === 2` and `λ(a : Text) → a === True` are void.
(We will never be able to create any values of those types.) 

If `a` and `b` evaluate to the same normal form, the type `a === b` is considered to be non-void.
That is, there exists a value of the type `a === b`.

If we want to write that value explicitly, we need to use the `assert` keyword with the following syntax: `assert : a === b`.
This expression is valid only if the two sides are equal after reducing them to their normal forms.
If the two sides are not equal, this expression _fails to type-check_, meaning that the entire program will fail to compile.

When an `assert` value is valid, we can assign that value to a variable:

```dhall
let test1 = assert : 1 + 2 === 0 + 3
```

In this example, the two sides of the type `1 + 2 === 0 + 3` are equal after reducing them to normal forms.
The resulting type `3 === 3` is non-void and has a value assigned to `test1`.

It is not actually possible to print the value of type `3 === 3` or to examine it in any other way.
We just know that that value exists (because the `assert` expression was accepted by Dhall).

The Dhall typechecker will raise a type error _at typechecking time_ if the two sides of an `assert` are not evaluated to the same normal forms.

Some examples:

```dhall
let x = 1
let y = 2
let _ = assert : x + 1 === y     -- OK.
let print = λ(n : Natural) → λ(prefix : Text) → prefix ++ Natural/show n
let _ = assert : print (x + 1) === print y    -- OK
```

In the last line, the `assert` expression was used to compare two partially evaluated functions, `print (x + 1)` and `print y`.
The normal form of `print (x + 1)` is the Dhall expression `λ(prefix : Text) → prefix ++ "2"`.
The normal form of `print y` is the same Dhall expression.
So, the assertion is valid.

Because `assert` expressions are checked at compile time, they cannot be used for implementing a _function_ comparing, say, two arbitrary `Text` values given as arguments.
Try writing this code:

```dhall
let compareTextValues : Text → Text → Bool
  = λ(a : Text) → λ(b : Text) → 
    let _ = assert : a === b    -- Error: the two sides are not equal.
      in True
```

This code will _fail to typecheck_ because, within the definition of `compareTextValues`, the normal forms of the function parameters `a` and `b` are just the symbols `a` and `b`, and those two symbols are not equal.
Because this code fails to typecheck, we cannot use it to implement a function returning `False` when two text strings are not equal.

The `assert` keyword is most often used to implement unit tests.
In that case, we do not need to keep the values of the equality type.
We just need to verify that the equality type is not void.
So, we may write unit tests like this:

```dhall
let f = λ(a : Text) → "(" ++ a ++ ")"
let _ = assert : f "x" === "(x)"    -- OK.
let _ = assert : f "" === "()"    -- OK.
  in ... -- Further code.
```

### Types, kinds, sorts

Dhall treats types and values largely in the same way (except when typechecking, that is, when verifying that each value has the correct type).

For instance, we may write `let a : Bool = True` to define a variable of type `Bool`, and we may also write `let b = Bool` to define a variable whose value is the type `Bool` itself.

Then we may use `b` in typechecking expressions such as `True : b`.
The type of `b` itself will be `Type`.

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
(This is done in fully dependently-typed languages such as Agda and Idris.
In those languages, `Type`'s type is denoted by `Type 1`, the type of `Type 1` is `Type 2`, and so on to infinity.
Dhall denotes `Type 1` by `Kind` and `Type 2` by `Sort`.)

The result is a type system that has just enough abstraction to support treating types as values, but does not run into the complications with polymorphism over infinitely many type universes.

Because of this design, Dhall does not support any code that operates on `Kind` values ("kind polymorphism").
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

This is because Dhall requires a valid function type itself to have a type.
The symbol `Kind` has type `Sort` but the symbol `Sort` itself does not have a type.

There was at one time an effort to implement full "kind polymorphism" in Dhall.
That would allow functions to manipulate `Kind` values.
But that effort was abandoned after it was discovered that it would [break the consistency of Dhall's type system](https://github.com/dhall-lang/dhall-haskell/pull/563#issuecomment-426474106).

### The universal type quantifier (∀) vs. the function symbol (λ)

Dhall uses the symbol `λ` (or equivalently the backslash `\`) to denote functions and the symbol ∀ (or equivalently the keyword `forall`) to denote _types_ of functions.

An expression of the form `λ(x : sometype1) → something2` is a function: it is something that can be applied to an argument to compute a new value.

An expression of the form `∀(x : sometype1) → sometype2` is a _type_: it is something that can be used as a type annotation for some values.

Expressions of the form `∀(x : sometype1) → sometype2` are used as type annotations for functions of the form `λ(x : sometype1) → something2`.

For example, the function that appends "..." to a string argument is written like this:

```dhall
let f = λ(x : Text) → "${x}..."
```

The type of `f` can be written as `∀(x : Text) → Text`.
If we like, we may write the definition of `f` together with a type annotation:

```dhall
let f : ∀(x : Text) → Text
  = λ(x : Text) → "${x}..."
```

A side note: The type expression `∀(x : Text) → Text` does not need the name `x` and can be also written in a shorter syntax as just `Text → Text`.
But Dhall will internally rewrite that to the normal form `∀(_ : Text) → Text`.

An expression of the form `λ(x : sometype1) → something2` is a function that can be applied to any `x` of type `sometype1` and will compute a result, `something2`.
(That result could itself be a value or a type.)
The _type_ of the expression `λ(x : sometype1) → something2` is `∀(x : sometype1) → sometype2` where `sometype2` is the type of `something2`.

Another way to see that `∀` always denotes types is to try writing an expression `∀(x : Text) → "abc"`.
Dhall will reject that expression with the error message "Invalid function output".
The expression `∀(x : Text) → something2` must be a _type_ of a function, and `something2` must be the output type of that function.
So, `something2` must be a type and cannot be a value.
But in the example `∀(x : Text) → "abc"`, the output type of the function is a text string `"abc"`, which is not a type.

In Dhall, this requirement is expressed by saying that `something2` should have type `Type`, `Kind`, or `Sort`.

As another example of the error "Invalid function output", consider code like `∀(x : Type) → λ(y : Type) → x` with .
This code has the form `∀(x : Type) → something` where `something` is a lambda-expression, which is not a type.

Valid code examples are `∀(x : Type) → ∀(y : Type) → x` and `λ(x : Type) → ∀(y : Type) → x`.

The type of `λ(x : sometype1) → something2` is `∀(x : sometype1) → sometype2`, where `sometype2` is the type of the expression `something2`.

The polymorphic identity function is an example that helps remember the difference between `∀` and `λ`.

The identity function takes a value `x` of an arbitrary type and again returns the same value `x`.

```dhall
let identity
 : ∀(A : Type) → ∀(x : A) → A
  = λ(A : Type) → λ(x : A) → x
```

Here we denoted the type parameter by the capital `A`.
(Dhall does not require that types be capitalized.)

Defined like this, `identity` is a function of type `∀(A : Type) → A → A`.
The function itself is the expression `λ(A : Type) → λ(x : A) → x`.

A function type of the form `A → B` can be also written in a longer syntax as `∀(x : A) → B` if we like.

Type expressions `∀(A : Type) → A → A` and `∀(A : Type) → ∀(x : A) → A` are equivalent.

The corresponding Haskell code is:

```haskell
identity :: a → a
identity = \x → x
```

The corresponding Scala code is:

```scala
def identity[A]: A => A  = { x => x }
```

In Dhall, the type parameters must be specified explicitly, both when defining a function and when calling it:

```dhall
let identity = λ(A : Type) → λ(x : A) → x
let x = identity Natural 123  -- Writing just `identity 123` is a type error.
```

All type parameters and all value parameters need to be written explicitly.
This makes Dhall code more verbose, but also helps remove "magic" from the syntax.

### Dependent types

Dependent types are, by definition, types that depend on _values_.

Curried functions types support dependence between an argument type and any previously given argument values.

For example, the type of the polymorphic identity function is:

```dhall
let example1 = ∀(A : Type) → ∀(x : A) → A
```

In this function type, the second curried argument (`x : A`) has a type that is given by the first curried argument (`A : Type`).

As another example, consider the following function type:

```dhall
let example2 = ∀(F : Type → Type) → ∀(A : Type) → ∀(x : A) → F A
```

In the type `example2`, the argument `x` has type `A`, which is given by a previous argument.
The output type `F A` depends on the first two arguments.

Both `example1` and `example2` are types that describe functions from types to values.

In Dhall, one can also define functions from values to types in the same way as one defines any other functions:

```dhall
let f
 : ∀(x : Bool) → Type
  = λ(x : Bool) → if x then Natural else Text 
```

The result of evaluating `f False` is the _type_ `Text` itself.
The type of `f` is an example of a "dependent type", that is, a type that depends on a value `x`.

Such functions can be used in type signatures to create dependently-typed functions (that is, functions whose types depend on the values of their input arguments):

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
If we are in the `if/else` branch, we return a value of type `if x then Natural else Text`.
That type depends on the value `x`.
In the `else` branch, `x` is `False` because the `if/then/else` construction begins with `if x`.
So, the `else` branch must have type `f False = Text`.
But Dhall does not implement this logic and cannot see that both branches will have the same type `Text`.

Because of this and other limitations, dependent types in Dhall can be used only in sufficiently simple cases. 

## Function combinators

The standard combinators for functions are forward and backward composition, currying / uncurrying, and argument flipping.

Implementing them in Dhall is straightforward.
Instead of pairs, we use the record type `{ _1 : a, _2 : b }`. 

```dhall
let compose_forward
 : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (a → b) → (b → c) → (a → c)
  = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : a → b) → λ(g : b → c) → λ(x : a) →
    g (f (x))

let compose_backward
 : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (b → c) → (a → b) → (a → c)
  = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : b → c) → λ(g : a → b) → λ(x : a) →
    f( g (x)) 

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

Dhall's `Natural` numbers have arbitrary precision but support a limited number of operations.
The standard prelude includes functions that can add, subtract, multiply, compare, and test `Natural` numbers for being even or odd.

We will now show how to implement other arithmetic operations for `Natural` numbers such as division or logarithm.
In an ordinary programming language, we would use loops to implement those operations.
But Dhall will only accept loops that are guaranteed in advance to terminate.
So, we will need to know in advance how many iterations are needed for any given computation.

### Using `Natural/fold` to replace loops

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
⊢ let succ = λ(a : Text) → a ++ "1230" in Natural/fold 4 Text succ "Q" 

"Q1230123012301230"
```

In this way, Dhall can perform many arithmetic operations for natural numbers that are usually implemented via loops.
However, `Natural/fold` is not a `while`-loop: it cannot iterate arbitrarily many times until some condition holds.
The number of iterations must be specified in advance, given as the first argument of `Natural/fold`.

Also, `Natural/fold` cannot stop early: it will always carry out the specified number of iterations.

When the exact number of iterations is not known in advance, one must estimate that number from above and design the algorithm to allow it to run more iterations than necessary without changing the result.
(Implementations of Dhall may optimize `Natural/fold` so that iterations stop when the result stops changing.)

### Integer division

Let us implement division for natural numbers.

A simple iterative algorithm that uses only subtraction runs like this.
Given `x : Natural` and `y : Natural`, we subtract `y` from `x` as many times as needed until the result becomes negative.
The value `x div y` is the number of times we subtracted.

This algorithm can be directly implemented in Dhall, but we need to specify in advance the maximum required number of iterations.
A safe upper bound is the value `x` itself.
So, we have to perform the iteration using the function call `Natural/fold x ...`.

In most cases, the actual required number of iterations will be smaller than `x`.
For clarity, we will maintain a boolean flag `done` and set it to `True` once we reach the final result.
Then we write code to ensure that any further iterations will not modify the final result. 

The code is:

```dhall
-- unsafeDiv x y means x / y but it will return wrong results when y = 0.
let unsafeDiv : Natural → Natural → Natural =
  let Natural/lessThan = https://prelude.dhall-lang.org/Natural/lessThan
  let Accum = { result : Natural, sub : Natural, done : Bool }
    in λ(x : Natural) → λ(y : Natural) →
         let init : Accum = {result = 0, sub = x, done = False}
         let update : Accum → Accum = λ(acc : Accum) →
             if acc.done then acc
             else if Natural/lessThan acc.sub y then acc // {done = True}
             else acc // {result = acc.result + 1, sub = Natural/subtract y acc.sub}
         let r : Accum = Natural/fold x Accum update init
         in r.result
in
  assert : unsafeDiv 3 2 === 1
```

### Safe division via dependently-typed evidence

The function `unsafeDiv` works but produces wrong results when dividing by zero.
For instance, `unsafeDiv 2 0` returns `2`.
We would like to prevent using that function when the second argument is zero.

To ensure that we never divide by zero, we may use a technique based on dependently-typed "evidence values".

The first step is to define a dependent type that will be void (with no values) if a given natural number is zero:

```dhall
let Nonzero : Natural → Type = λ(y : Natural) → if Natural/isZero y then < > else {}
```

This `Nonzero` is a type function that returns one or another type given a `Natural` value.
For example, `Nonzero 0` returns the void type `< >`, but `Nonzero 10` returns the unit type `{}`.
This definition is straightforward because types and values are treated similarly in Dhall, so it is easy to define a function that returns a type.

We will use that function to implement safe division (`safeDiv`):

```dhall
let safeDiv = λ(x: Natural) → λ(y: Natural) → λ(_: Nonzero y) → unsafeDiv x y
```

The required value of type `Nonzero y` is an "evidence" that the first argument (`y`) is nonzero.

When we use `safeDiv` for dividing by a nonzero value, we specify a third argument of type `{}`.
That argument can have only one value, namely, the empty record, denoted in Dhall by `{=}`.
So, instead of `unsafeDiv 5 2` we now write `safeDiv 5 2 {=}`.

If we try dividing by zero, we will be obliged to pass a third argument of type `< >`, but there are no such values.
Passing an argument of any other type will raise a type error.

```dhall
safeDiv 4 2 {=}  -- Returns 2.

safeDiv 4 0 {=}  -- Raises a type error. 
```

In this way, dependently-typed evidence values enforce value constraints at compile time.

#### Better error messages for failed assertions

If we write `safeDiv 4 0 {=}`, we get a type error that says "the value `{=}` has type `{}`, but we expected type `<>`".
This message is not particularly helpful.
We can define the dependent type `Nonzero` in a different way, so that the error message clearly shows why the assertion failed.
For that, we replace the void type `< >` by the equivalent void type of the form `"a" === "b"` where `"a"` and `"b"` are strings that are guaranteed to be different.
Those strings will be printed by Dhall as part of the error message.

To implement this idea, let us replace the definition of `Nonzero` by this code:

```dhall
let Nonzero = λ(y : Natural) →
  if Natural/isZero y
  then "error" === "attempt to divide by zero"
  else {}

let safeDiv = λ(x: Natural) → λ(y: Natural) → λ(_: Nonzero y) → unsafeDiv x y
```

When we evaluate `safeDiv 4 0 {=}`, we now get a good error message:

```
safeDiv 4 0 {=}

Error: Wrong type of function argument

- "error" ≡ "attempt to divide by zero"
```

Another example is an assertion that a natural number should be less than a given limit.
We implement that assertion as a dependent type constructor `AssertLessThan`.
The error message will be computed as a function of the given arguments:

```dhall
let AssertLessThan = λ(x : Natural) → λ(limit : Natural) →
  let Natural/lessThan = https://prelude.dhall-lang.org/Natural/lessThan
  in
  if Natural/lessThan x limit
  then {}
  else "error" === "the argument ${Natural/show x} must be less than ${Natural/show limit}"
```

Suppose we need a function that needs to constrain its natural argument to be below `100`.
Then we require an evidence argument of type `AssertLessThan x 100`:

```dhall
let myFunc = λ(x : Natural) → λ(_ : AssertLessThan x 100) →
  x -- Or some other code.
```

There are no errors if we evaluate `myFunc 1 {=}` or `myFunc 50 {=}`.
But writing `myFunc 200 {=}` gives a type error:

```
myFunc 200 {=}

Error: Wrong type of function argument

- "error" ≡ "the argument 200 must be less than 100"
```

The error message clearly describes the problem.

#### Limitations

The main limitation of this technique is that it can work only with literal values.

For instance, any usage of `safeDiv x y` will require us somehow to obtain a value of type `Nonzero y` that is type-checked at compile time.
That value serves as a witness that the number `y` is not zero.
Values of type `Nonzero y` can be type-checked only if `y` is a literal `Natural` value.
This is so because the check `Natural/isZero y` is done at type-checking time.

What if we need to use `safeDiv` inside a function that takes an argument `y : Natural` and then calls `safeDiv x y`?
That function cannot call `safeDiv x y {=}` because the witness value `{=}` needs to be type-checked at compile time.
We also cannot test whether `y` is zero at run time and then call `safeDiv` only when `y` is nonzero.
This code:

```dhall
λ(y : Natural) → if Natural/isZero y then 0 else safeDiv 10 y {=}
```

will produce a type error because `{=}` is not of type `Nonzero y`.

Neither can we use the `Optional` type to create a value of type `Optional (Nonzero y)` that will be `None` when `y` equals zero.
Dhall will not accept code like this:

```dhall
-- Type error:
λ(y : Natural) → if Natural/isZero y then None (Nonzero y) else (Some {=} : Optional (Nonzero y))
```

Here, Dhall does not recognize that `Nonzero y` is the unit type (`{}`) within the `else` clause.
To recognize that, the interpreter would need to deduce that the condition under `if` is the same as the condition defined in `Nonzero`.
But Dhall's typechecking is insufficiently powerful to handle dependent types in such generality.

So, any function that uses `saveDiv` for dividing by an unknown value `y` will also require an additional witness argument of type `Nonzero y`.

We also cannot divide by a number `y` imported from a different Dhall file, unless that file also exports a witness value of type `Nonzero y`.

The advantage of using this technique is that we will guarantee, at typechecking time, that programs will never divide by zero.

### Integer square root

The integer-valued square root of a natural number `n` is the largest natural number `r` such that `r * r <= n`. 

A simple algorithm for determining `r` is to start from `1` and increment repeatedly, until the result `r` satisfies `r * r > n`.

As before, Dhall requires is to specify an upper bound on the number of iterations up front.
Let us specify `n` as the upper bound.

We will begin with `n` and iterate applying a function `stepUp`.
That function will increment its argument `r` by `1` while checking the condition `r * r <= n`. 

The code is:

```dhall
let sqrt = λ(n: Natural) →
  let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual
  let stepUp = λ(r : Natural) → if (lessThanEqual (r * r) n) then r + 1 else r 
    in Natural/subtract 1 (Natural/fold (n + 1) Natural stepUp n)
  in 
    assert : sqrt 25 === 5
```

There are faster algorithms of computing the square root, but those algorithms require division.
Our implementation of division already requires a slow iteration.
So, we will not attempt to optimize the performance of this code.

### Integer logarithm

The "bit width" (`bitWidth`) of a natural number `n` is the smallest number of binary bits needed to represent `n`.
For example, `bitWidth 3` is `2` because `3` is represented as two binary bits: `0b11`, while `bitWidth 4` is `3` because `4` is `0b100` and requires 3 bits.

To compute this function, we find the smallest natural number `b` such that `2` to the power `b` is larger than `n`. We start with `b = 1` and multiply `b` by `2` as many times as needed until we get a value larger than `n`.

As before, we need to supply an upper bound on the iteration count.
We supply `n` as that bound and make sure that the final result remains constant once we reach it, even if we perform further iterations.

The code is:

```dhall
let bitWidth : Natural → Natural = λ(n : Natural) →
  let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual
  let Accum = { b : Natural, bitWidth : Natural }
  let init = { b = 1, bitWidth = 0 } -- At all times, b == pow(2, bitWidth).
  let update = λ(acc : Accum) →
     if lessThanEqual acc.b n
     then { b = acc.b * 2, bitWidth = acc.bitWidth + 1 }
     else acc 
  let result : Accum = Natural/fold n Accum update init
    in result.bitWidth 
```

The function `bitWidth` may be generalized to compute integer-valued logarithms with a natural base.
We note that if we subtract `1` from the result of `bitWidth` then we will obtain the integer part of the base-2 logarithm.
So, we replace the base 2 in `bitWidth` by an arbitrary base and obtain this code:

```dhall
let log : Natural → Natural → Natural = λ(base : Natural) → λ(n : Natural) →
  let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual
  let Accum = { b : Natural, log : Natural }
  let init = { b = 1, log = 0 } -- At all times, b == pow(base, log).
  let update = λ(acc : Accum) →
     if lessThanEqual acc.b n
     then { b = acc.b * base, log = acc.log + 1 }
     else acc 
  let result : Accum = Natural/fold n Accum update init
    in Natural/subtract 1 result.log

in 
  assert : log 10 100 ≡ 2
```

### Greatest common divisor (`gcd`)

The greatest common divisor (`gcd x y`) is computed by a simple algorithm using subtraction.

When `x` and `y` are equal, we have `gcd x x = x`.
Otherwise, one of `x` and `y` is larger; say, `x`. Then we define recursively `gcd x y = gcd (x - y) y`.

To implement this in Dhall, we supply the larger of `x` and `y` as an upper bound on the iteration count.
The iteration will keep the pair `x, y` sorted so that `x` is always greater or equal to `y`.
At each step, a pair `x, y` is replaced by `x - y, y` and sorted again.
Eventually, `y` becomes equal to `0`.
After that, the value `x` is equal to the required value of `gcd`.

```dhall
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
```

## Functors of various kinds

### Functors and `fmap`

In the jargon of the functional programming community, a **functor** is a type constructor `F` with an `fmap` function having the standard type signature and obeying the functor laws.

Those type constructors are also called "covariant functors".
For type constructors, "covariant" means "has a lawful `fmap` method".

Note that this definition of "covariant" does not need subtyping and depends only on the structure of the type expression.

A simple example of a functor is a record with two values of type `A` and a value of a fixed type `Bool`.
In Haskell, that type constructor and its `fmap` function are defined by:

```haskell
data F a = F a a Bool
fmap :: (a → b) → F a → F b
fmap f (F x y t) = F (f x) (F y) t 
```

In Scala, the equivalent code is:

```scala
final case class F[A](x: A, y: A, t: Boolean)

def fmap[A, B](f: A => B)(fa: F[A]): F[B] =
  F(f(fa.x), f(fa.y), fa.t)
```

The corresponding Dhall code is:

```dhall
let F : Type → Type
  = λ(a : Type) → { x : a, y : a, t : Bool }
let fmap
 : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fa : F a) →
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
  = λ(a : Type) → < Left : Text | Right : a >
let fmap
 : ∀(a : Type) → ∀(b : Type) → (a → b) → G a → G b
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(ga : G a) →
    merge { Left = λ(t : Text) → (G b).Left t
          , Right = λ(x : a) → (G b).Right (f x)
          } ga
```

Dhall requires the union type's constructors to be explicitly derived from the full union type.
In Haskell or Scala, we would simply write `Left(t)` and `Right(f(x))` and let the compiler fill in the type parameters.
But Dhall requires us to write a complete type annotation such as `< Left : Text | Right : b >.Left t` and `< Left : Text | Right : b >.Right (f x)` in order to specify the complete union type being constructed.

In the code shown above, we were able to shorten those constructors to `(G b).Left` and `(G b).Right`.

### Contravariant functors ("contrafunctors")


The complementary kind of type constructors is contravariant functors: they cannot have a lawful `fmap` method.
Instead, they have a `cmap` method with a type signature that flips one of the function arrows:

```dhall
cmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F b → F a
```

We will call contravariant type constructors **contrafunctors** for short.

TODO examples

### Bifunctors and `bimap`

Bifunctors are type constructors with two type parameters that are covariant in both type parameters.
For example, `type P a b = (a, a, b, Int)` is a bifunctor.

Dhall encodes bifunctors as functions with two curried arguments:

```dhall
let P : Type → Type → Type
  = λ(a : Type) → λ(b : Type) → { x : a, y : a, z : b, t : Integer }
```

Bifunctors have a `bimap` method that transforms both type parameters at once:

```dhall
let bimap
 : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → ∀(d : Type) → (a → c) → (b → d) → P a b → P c d
  = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(d : Type) → λ(f : a → c) → λ(g : b → d) → λ(pab : P a b) →
    { x = f pab.x, y = f pab.y, z = g pab.z, t = pab.t }
```

Given `bimap`, one can then define two `fmap` methods that work only on the first or on the second of `P`'s type parameters.

```dhall
let fmap1
 : ∀(a : Type) → ∀(c : Type) → ∀(d : Type) → (a → c) → P a d → P c d
  = λ(a : Type) → λ(c : Type) → λ(d : Type) → λ(f : a → c) →
    bimap a d c d f (identity d)

let fmap2
 : ∀(a : Type) → ∀(b : Type) → ∀(d : Type) → (b → d) → P a b → P a d
  = λ(a : Type) → λ(b : Type) → λ(d : Type) → λ(g : b → d) →
    bimap a b a d (identity a) g
```

Here, we have used the polymorphic identity function defined earlier.

The code for `fmap` and `bimap` can be derived mechanically from the type definition of a functor or a bifunctor.
For instance, Haskell will do that if the programmer just writes `deriving Functor` after the definition.
But Dhall does not have any code generation facilities.
The code of those methods must be written in Dhall programs that need to use functors or bifunctors.

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
Examples are the standard functions `reduce` and `foldMap` for `List`, written in the Haskell syntax as:

```haskell
reduce :: Monoid m => List m -> m
reduce xs = foldr (\x -> \y -> mappend y x) mempty xs

foldMap :: Monoid m => (a -> m) -> List a -> m
foldMap f xs = foldr (\x -> \y -> mappend y (f x)) mempty xs
```

Note that Dhall's `List/fold` implements a "right fold", similarly to Haskell's `foldr` and Scala's `foldRight`.
For this reason, the code shown above appends `y` to `x` and not `x` to `y`.
The corresponding Dhall code for `reduce` and `foldMap` is:

```dhall
let reduce
 : ∀(m : Type) → Monoid m → List m → m
  = λ(m : Type) → λ(monoid_m : Monoid m) → λ(xs : List m) →
    List/fold m xs m (λ(x : m) → λ(y : m) → monoid_m.append y x) monoid_m.empty

let foldMap
 : ∀(m : Type) → Monoid m → ∀(a : Type) → (a → m) → List a → m
  = λ(m : Type) → λ(monoid_m : Monoid m) → λ(a : Type) → λ(f : a → m) → λ(xs : List a) →
    List/fold a xs m (λ(x : a) → λ(y : m) → monoid_m.append y (f x)) monoid_m.empty
```

This code shows how to implement typeclass constraints in Dhall.

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

### `Contrafunctor`

The typeclass for contrafunctors is defined by:

```dhall
let Contrafunctor = λ(F : Type → Type) → { cmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F b → F a }
```

TODO examples

### `Pointed` functors and contrafunctors

TODO

examples:

Pointed instances for Optional, for Pair (a, a), and for List

Examples: pointed functor or pointed contrafunctor

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
  { pure = λ(a : Type) → λ(x : a) → [ x ]
  , bind = λ(a : Type) → λ(fa : List a) → λ(b : Type) → λ(f : a → List b) →
    List/concatMap a b f fa
  }
```

We have defined the `Monad` typeclass via the `pure` and `bind` methods.
Let us implement a function that provides the `join` method for any member of the `Monad` typeclass.

In Haskell, we would define `join` via `bind` as:

```haskell
monadJoin :: Monad F => F (F a) -> F a
monadJoin ffa = bind ffa id
```

In this Haskell code, `id` is an identity function of type `F a → F a`.

The corresponding Dhall code is similar, except we need to write out all type parameters:

```dhall
let monadJoin = λ(F : Type → Type) → λ(monadF : Monad F) → λ(a : Type) → λ(ffa : F (F a)) →
  monadF.bind (F a) ffa a (identity (F a))  
```

We can use this function to obtain a `join` method for `List` like this:

```dhall
let List/join : ∀(a : Type) → List (List a) → List a
  = monadJoin List monadList 
```

### `Applicative` functors and contrafunctors

TODO use pointed

TODO example and traverse function

TODO examples of contravariant or cross-variant applicatives

### Inheritance of typeclasses

Sometimes one typeclass includes methods from another.
For example, `Semigroup` is similar to `Monoid`: it has the `append` method but no `empty` method.
We could say that the `Monoid` typeclass inherits `append` from `Semigroup` and adds the `empty` method.
The `Monad` typeclass could inherit `fmap` from the `Functor` typeclass and `pure` from the `Pointed` typeclass.

To express this kind of inheritance in Dhall, we can use Dhall's record-typing features.
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

We can use this definition to rewrite the `Monoid` typeclass using the record type from the `Semigroup` typeclass.
Then the `Monoid` evidence value for the type `Text` is written as:

```dhall
let Monoid = λ(m : Type) → Semigroup m //\\ { empty : m }
let monoidText : Monoid Text = semigroupText /\ { empty = "" } 
```

Similarly, we may rewrite the `Monad` typeclass to make it more clear that any monad is also a covariant and pointed functor:

```dhall
let Monad = λ(F : Type → Type) →
  Functor F //\\ Pointed F //\\
      { bind : ∀(a : Type) → F a → ∀(b : Type) → (a → F b) → F b }
```

As an example, let us define a `Monad` evidence value for `List`:

TODO make pointed instance for List

```dhall
let monadList : Monad List =
  let List/concatMap = https://prelude.dhall-lang.org/List/concatMap
  in functorList /\
      { pure = λ(a : Type) → λ(x : a) → [ x ]
      , bind = λ(a : Type) → λ(fa : List a) → λ(b : Type) → λ(f : a → List b) →
        List/concatMap a b f fa
      }
```

## Church encoding for recursive types and type constructors

### Recursion schemes

Dhall does not directly support defining recursive types or recursive functions.
The only supported recursive type is a built-in `List` type. 
However, user-defined recursive types and a certain limited class of recursive functions can be implemented in Dhall via the Church encoding techniques. 

Dhall's documentation contains a [beginner's tutorial on Church encoding](https://docs.dhall-lang.org/howtos/How-to-translate-recursive-code-to-Dhall.html).
Here, we summarize that technique more briefly.

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
let F : Type → Type = λ(a : Type) → < Nil |  Cons : { head : Integer, tail : a } >
```

By definition, the **Church encoding** of `T` is the following type expression:

```dhall
let C : Type = ∀(r : Type) → (F r → r) → r 
```

The type `C` is still non-recursive, so Dhall will accept this definition.

Note that we are using `∀(r : Type)` and not `λ(r : Type)` when we define `C`.
The type `C` is not a type constructor; it is a type of a function with a type parameter.
When we define `F` as above, it turns out that the type `C` equivalent to the type of (finite) lists with integer values.

The Church encoding construction works in the same way for any recursion scheme `F`.
Given a recursion scheme `F`, one defines a non-recursive type `C`:

```dhall
let C = ∀(r : Type) → (F r → r) → r
```
Then the type `C` is equivalent to the type `T` that we would have defined by `T = F T` in a language that supports recursively defined types.

It is not obvious why the type `C = ∀(r : Type) → (F r → r) → r` is equivalent to a type `T` defined recursively by `T = F T`.
More precisely, the type `C` is the "least fixpoint" of the type equation `C = F C`.
A mathematical proof of that property is given in the paper ["Recursive types for free"](https://homepages.inf.ed.ac.uk/wadler/papers/free-rectypes/free-rectypes.txt) by P. Wadler.
In this book, we will focus on the practical uses of Church encoding.

### Simple recursive types

Here are some examples of Church encoding for simple recursive types.

The type `ListInt` (a list with integer values):

```dhall
let F = λ(r : Type) → < Nil | Cons : { head : Integer, tail : r } >
let ListInt = ∀(r : Type) → (F r → r) → r
```

The type `TreeText` (a binary tree with `Text` strings in leaves):

```dhall
let F = λ(r : Type) → < Leaf : Text | Branch : { left : r, right : r } >
let TreeText = ∀(r : Type) → (F r → r) → r
```

### Church encoding of non-recursive types

If a recursion scheme does not actually depend on its type parameter, the Church encoding leaves the type unchanged.

For example, consider this recursion scheme:

```dhall
let F = λ(t : Type) → { x : Text, y : Bool }
```
Here the type `F t` does not actually depend on `t`.

The corresponding Church encoding gives the type:

```dhall
let C = ∀(r : Type) → ({ x : Text, y : Bool } → r) → r
```

The general properties of the Church encoding always enforce that `C` is a fixpoint of the type equation `C = F C`.
This remains true even when `F` does not depend on its type parameter.
So, now we have `F C = { x : Text, y : Bool }` independently of `C`.
The type equation `C = F C` is non-recursive and simply says that `C = { x : Text, y : Bool }`.

More generally, the type `∀(r : Type) → (p → r) → r` is equivalent to just `p`, because it is the Church encoding of the type equation `T = p`.

We see that Church encodings generally do not bring any advantages for simple, non-recursive types.

### The Yoneda and Church-Yoneda identities

The type equivalence `∀(r : Type) → (p → r) → r ≅ p` is a special case of the **covariant Yoneda identity**:

```dhall
∀(r : Type) → (p → r) → G r  ≅  G p
```
Here, it is assumed that `G` is a covariant type constructor and `p` is a fixed type (not depending on `r`).

Note that the Church encoding formula, `∀(r : Type) → (F r → r) → r`, is not of the same form as the Yoneda identity because the function argument `F r` depends on `r`.
The Yoneda identity does not apply to types of that form.

There is a generalized **Church-Yoneda identity** that combines both forms of types:

```dhall
∀(r : Type) → (F r → r) → G r  ≅  G C
```
Here, `C = ∀(r : Type) → (F r → r) → r` is the Church-encoded fixpoint of `F`.

This identity is mentioned in the proceedings of the conference ["Fixed Points in Computer Science 2010"](https://hal.science/hal-00512377/document) on page 78 as "proposition 1" in the paper by T. Uustalu.

The Yoneda identity and the Church-Yoneda identity are proved via the so-called "parametricity theorem".
See the Appendix "Naturality and parametricity" for more details.

The Church-Yoneda identity is useful for proving certain properties of Church-encoded types.
In this book, we will use that identity to prove the Church encoding formula for mutually recursive types.

### Church encoding in the curried form

We can use certain type equivalence identities to rewrite the type `ListInt` in a form more convenient for practical applications.

The first type equivalence is that a function from a union type is equivalent to a product of functions.
So, the type `F r → r`, written in full as:

```dhall
< Nil | Cons : { head : Integer, tail : r } > → r
```
is equivalent to a pair of functions of types `{ head : Integer, tail : r } → r` and  `< Nil > → r`.

The type `< Nil >` is a named unit type, so `< Nil > → r` is equivalent to just `r`.

The second type equivalence is that a function from a record type is equivalent to a curried function.
For instance, the type:

```dhall
{ head : Integer, tail : r } → r
```
is equivalent to `Integer → r → r`.

Using these type equivalences, we may rewrite the type `ListInt` in the **curried form** as:

```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r
```

It is now less clear that we are dealing with a type of the form `∀(r : Type) → (F r → r) → r`.
However, working with curried functions often needs shorter code than working with union types and record types.

As an example, let us rewrite the type `TreeText` defined above in a curried form.
Begin with the definition already shown:

```dhall
let F = λ(r : Type) → < Leaf : Text | Branch : { left : r, right : r } >
let TreeText = ∀(r : Type) → (F r → r) → r
```

Since `F r` is a union type with two parts, the type of functions `F r → r` can be replaced by a pair of functions.

We can also replace functions from a record type by curried functions.

Then we obtain an equivalent definition of `TreeText` that is easier to work with:

```dhall
let TreeText = ∀(r : Type) → (Text → r) → (r → r → r) → r
```

These examples show how any type constructor `F` defined via products (records) and co-products (union types) gives rise to a Church encoding that can be rewritten purely via curried functions, without using any records or union types.

We will call that the **curried form** of the Church encoding.

The curried form is more convenient for practical programming.
But when we are studying general properties of Church encodings, it is better to use the form `∀(r : Type) → (F r → r) → r`.

Historical note: The curried form of the Church encoding is known as the Boehm-Berarducci encoding.
See [this discussion by O. Kiselyov](https://okmij.org/ftp/tagless-final/course/Boehm-Berarducci.html) for more details.

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

The Church-encoded type `C` is a fixpoint of the type equation `C = F C`.
This means we should have two functions, `fix : F C → C` and `unfix : C → F C`, that are inverses of each other.
These two functions implement an isomorphism between `C` and `F C`.
This isomorphism shows that the types `C` and `F C` are equivalent, which is one way of understanding why `C` is a fixpoint of the type equation `C = F C`.

Because this isomorphism is a general property of all Church encodings, we can write the code for `fix` and `unfix` generally, for all recursion schemes `F` and the corresponding types `C`.

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

The definitions of `fix` and `unfix` are non-recursive and are accepted by Dhall.

The paper ["Recursive types for free"](https://homepages.inf.ed.ac.uk/wadler/papers/free-rectypes/free-rectypes.txt) proves via parametricity that `fix` and `unfix` are inverses of each other.

Another property proved in that paper is the identity `c C fix = c` for all `c : C`.

### Data constructors

The function `fix : F C → C` (sometimes also called `build`) provides a general way of creating new values of type `C` out of previously known values, or from scratch.

As the type `F C` is almost always a union type, it is convenient to rewrite the function type `F C → C` as a product of simpler functions.
We can write this in a mathematical notation:

`F C → C  ≅  (F1 C → C) × (F2 C → C) × ... `

where each of `F1 C`, `F2 C`, etc., are product types such as `C × C` or `Text × C`, etc.

Each of the simpler functions (`F1 C → C`, `F2 C → C`, etc.) is a specific constructor that we can assign a name for convenience.
In this way, we replace a single function `fix` by a product of constructors that can be used to create values the complicated type `C` more easily.

To illustrate this technique, consider two examples: `ListInt` and `TreeText`.

Begin with the curried Church encodings of those types:

```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r

let TreeText = ∀(r : Type) → (Text → r) → (r → r → r) → r
```

From this, we can simply read off the types of the constructor functions (which we will call `nil`, `cons`, `leaf`, and `branch` according to the often used names of those constructors):

```dhall
let nil : ListInt = ...
let cons : Integer → ListInt → ListInt = ...

let leaf : Text → TreeText = ...
let branch : TreeText → TreeText → TreeText = ...
```

In principle, the code for the constructors can be derived mechanically from the general code of `fix`.
But in most cases, it is easier to write the constructors manually, by implementing the required type signatures guided by the types.

Each of the constructor functions needs to return a value of the Church-encoded type, and we write out its type signature.
Then, each constructor applies the corresponding part of the curried Church-encoded type to suitable arguments.

```dhall
let nil : ListInt
   = λ(r : Type) → λ(a1 : r) → λ(a2 : Integer → r → r) →
     a1
let cons : Integer → ListInt → ListInt
   = λ(n : Integer) → λ(c : ListInt) → λ(r : Type) → λ(a1 : r) → λ(a2 : Integer → r → r) →
     a2 n (c r a1 a2)

let leaf : Text → TreeText
   = λ(t : Text) → λ(r : Type) → λ(a1 : Text → r) → λ(a2 : r → r → r) →
     a1 t
let branch: TreeText → TreeText → TreeText
   = λ(left : TreeText) → λ(right : TreeText) → λ(r : Type) → λ(a1 : Text → r) → λ(a2 : r → r → r) →
     a2 (left r a1 a2) (right r a1 a2)
```

Now we can create values of Church-encoded types by writing nested constructor calls:

```dhall
-- The list [+123, -456, +789]
let example1 : ListInt = cons +123 (cons -456 (cons +789 nil))

{- The tree    /\
              /\ c
             a  b
-}
let example2 : TreeText = branch ( branch (leaf "a") (leaf "b") ) (leaf "c")
```

### Aggregations ("folds")

The type `C` itself is a type of fold-like functions.

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

The code can be made even shorter:

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

For an arbitrary Church-encoded data type `C`, the "fold" function is the identity function of type `C → C` with first two arguments flipped.
In practice, it is easier to "inline" that identity function: that is, to use the data type `C` itself as the "fold"-like function.

Recursive data types such as lists and trees support certain useful operations such as `map`, `concat`, `filter`, or `traverse`.
In most FP languages, those operations are implemented via recursive code.
To use those operations in Dhall, we need to reformulate them as fold-like aggregations.

**"Fold-like" aggregations** iterate over the data while some sort of accumulator value is updated at each step.
The result value of the aggregation is the last computed value of the accumulator.

Let us show some examples of how this is done.

### Sum of values in a `ListInt`

Suppose we have a value `list` in the curried-form Church encoding of `ListInt`:

```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r

let list : ListInt = ...
```

The task is to compute the sum of the absolute values of all integers in `list`.
So, we need to implement a function `sumListInt : ListInt → Natural`.
An example test could be:

```dhall
let example1 : ListInt = cons +123 (cons -456 (cons +789 nil))
let _ = assert : sumListInt example1 === 1368
```

The function `sumListInt` is a "fold-like" aggregation operation.
To run the aggregation, we simply apply the value `list` to some arguments.
What are those arguments?

The type `ListInt` is a curried function with three arguments.

The first argument must be the type `r` of the result value; in our case, we need to set `r = Natural`.

The second argument is of type `r` and the third argument of type `Integer → r → r`.
In our case, these types become `Natural` and `Integer → Natural → Natural`.

So, it remains to supply those arguments that we will call `init : Natural` and `update : Integer → Natural → Natural`.
The code of `sumListInt` will look like this:

```dhall
let init : Natural = ...
let update : Integer → Natural → Natural = ...
let sumListInt : ListInt → Natural = λ(list : ListInt) → list Natural init update
```

The meaning of `init` is the result of `sumListInt` when the list is empty.
The meaning of `update` is the next accumulator value (of type Natural) computed from a current item from the list (of type `Integer`) and a value of type `Natural` that has been accumulated so far (by aggregating the tail of the list).

In our case, it is natural to set `init` is zero.
The `update` function is implemented via the standard Prelude function `Integer/abs`:

```dhall
let abs = https://prelude.dhall-lang.org/Integer/abs
let update : Integer → Natural → Natural = λ(i : Integer) → λ(previous : Natural) → previous + abs i
```

The complete test code is:

```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r
let init : Natural = 0
let abs = https://prelude.dhall-lang.org/Integer/abs
let update : Integer → Natural → Natural = λ(i : Integer) → λ(previous : Natural) → previous + abs i
let sumListInt : ListInt → Natural = λ(list : ListInt) → list Natural init update
let example1 : ListInt = cons +123 (cons -456 (cons +789 nil))

let _ = assert : sumListInt example1 === 1368
```

### Pretty-printing a binary tree

Consider the curried form of the Church encoding for binary trees with `Text`-valued leaves:

```dhall
let TreeText = ∀(r : Type) → (Text → r) → (r → r → r) → r
```

The task is to print a text representation of the tree where branching is indicated via nested parentheses, such as `"((a b) c)"`.
The result will be a function `printTree` whose code needs to begin like this:

```dhall
let printTree : TreeText → Text = λ(tree: ∀(r : Type) → (Text → r) → (r → r → r) → r) → ...
```

The pretty-printing operation is "fold-like" because a pretty-printed tree can be aggregated from pretty-printed subtrees.

We implement a fold-like operation simply by applying the value `tree` to some arguments.

The first argument must be the type `r` of the result value.
Since the pretty-printing operation will return a `Text` value, we set the type parameter `r` to `Text`.

Then it remains to supply two functions of types `Text → r` and `r → r → r` (where `r = Text`).
Let us call those functions `printLeaf : Text → Text` and `printBraches : Text → Text → Text`.
These two functions describe how to create the text representation for a larger tree either from a leaf or from the _already computed_ text representations of two subtrees.

A leaf is printed as just its `Text` value:

```dhall
let printLeaf : Text → Text = λ(leaf : Text) → leaf
```

For the two branches, we add parentheses and add a space between the two subtrees:

```dhall
let printBraches : Text → Text → Text = λ(left : Text) → λ(right : Text) → "(${left} ${right})"
```

Note that the functions `printLeaf` and `printBranches` are non-recursive.

The complete code of `printTree` is:

```dhall
let printLeaf : Text → Text = λ(leaf : Text) → leaf

let printBranches : Text → Text → Text = λ(left : Text) → λ(right : Text) → "(${left} ${right})"

let printTree : TreeText → Text = λ(tree: ∀(r : Type) → (Text → r) → (r → r → r) → r) →
    tree Text printLeaf printBranches

let example2 : TreeText = branch ( branch (leaf "a") (leaf "b") ) (leaf "c")    

let test = assert : printTree example2 === "((a b) c)"
```

In a similar way, many recursive functions can be reduced to fold-like operations and then implemented for Church-encoded data non-recursively.

### Where did the recursion go?

The technique of Church encoding may be perplexing.
If we are actually implementing recursive types and recursive functions, why do we no longer see any recursion or iteration in the code?

In the code of `sumListInt` and `printTree`, where are the parts that iterate over the data?

In fact, the functions `sumListInt` and `printTree` are _not_ recursive.
The possibility of iteration over the data stored in the list or in the tree is provided by the types `ListInt` and `TreeText` themselves.
But the iteration is not provided via loops or recursion.
Instead, it is hard-coded in the values `list : ListInt` and `tree: TreeText`.

To see how, consider the value `example1` shown above:

```dhall
let example1 : ListInt = cons +123 (cons -456 (cons +789 nil))
```

The value `example1` corresponds to a list with three items: `[+123, -456, +789]`.

When we expand the constructors `cons` and `nil`, we will find that `example1` is a higher-order function.
The Dhall interpreter can print that function's normal form for us:

```dhall
⊢ example1

λ(r : Type) →
λ(a1 : r) →
λ(a2 : Integer → r → r) →
  a2 +123 (a2 -456 (a2 +789 a1))
```

The function `example1` includes nested calls to `a1` and `a2`, that correspond to the two constructors (`nil` and `cons`) of `ListInt`.
The code of `example1` applies the function `a2` three times, which corresponds to having three items in the list.
But there is no loop in `example1`.
It is just hard-coded in the expression `example1` that `a2` needs to be applied three times to some arguments.

When we compute an aggregation such as `sumListInt example1`, we apply `example1` to three arguments.
The last of those arguments is a certain function that we called `update`.
When we apply `example1` to its arguments, the code of `example1` will call `update` three times.
This is how the Church encoding actually performs iteration.

Similarly, consider the value `example2` shown above.
When we expand the constructors `branch` and `leaf`, we will find that `example2` is a higher-order function:

```dhall
⊢ example2

λ(r : Type) →
λ(a1 : Text → r) →
λ(a2 : r → r → r) →
  a2 (a2 (a1 "a") (a1 "b")) (a1 "c")
```

The code of `example2` includes nested calls to `a1` and `a2`, which correspond to the constructors `leaf` and `branch`.
There are three calls to `a1` and two calls to `a2`, meaning that the tree has three leaf values and two branch points.
It's hard-coded in `example2` to make exactly that many calls to the arguments `a1` and `a2`.
When we apply `example2` to arguments `r`, `leaf`, and `branch`, the code of `example2` will call `leaf` three times and `branch` two times.

This explains how the Church encoding replaces iterative computations by non-recursive code.
A data structure that contains, say, 1000 data values is Church-encoded into a certain higher-order function.
That function will be hard-coded to call its arguments 1000 times.

In this way, it is guaranteed that all recursive structures will be finite and all operations on those structures will terminate.
That's why Dhall is able to accept Church encodings of recursive types and perform iterative and recursive operations on Church-encoded data without compromising any safety guarantees.

As another example, we will show how to compute the size of a Church-encoded data structure.

### Computing the size of a recursive data structure

The curried Church encoding for binary trees with `Natural`-valued leaves is:

```dhall
let TreeNat = ∀(r : Type) → (Natural → r) → (r → r → r) → r
```

The present task is to compute various numerical measures characterizing the tree's data.

We will consider three possible size computations:

- The sum of all natural numbers stored in the tree. (`treeSum`)
- The total number of leaves in the tree. (`treeCount`)
- The maximum depth of leaves. (`treeDepth`)

Each computation is a fold-like aggregation, so we will implement all of them via similar-looking code:

```dhall
let treeSum : TreeNat → Natural =
   let leafSum = ???
   let branchSum = ???
     in ∀(tree : TreeNat) → tree Natural leafSum branchSum

let treeCount : TreeNat → Natural =
   let leafCount = ???
   let branchCount = ???
     in ∀(tree : TreeNat) → tree Natural leafCount branchCount

let treeDepth : TreeNat → Natural =
   let leafDepth = ???
   let branchDepth = ???
     in ∀(tree : TreeNat) → tree Natural leafDepth branchDepth
```

The difference is only in the definitions of the functions `leafSum`, `branchSum`, and so on.

### Pattern matching

When working with recursive types in ordinary functional languages, one often uses pattern matching.
For example, here is a simple Haskell function that detects whether a given tree is a single leaf:

```haskell
data TreeInt = Leaf Int | Branch TreeInt TreeInt

isSingleLeaf: TreeInt -> Bool
isSingleLeaf t = case t of
    Leaf _ -> true
    Branch _ _ -> false
```

Another example is a Haskell function that returns the first value in the list if it exists:

```haskell
headMaybe :: [a] -> Maybe a
headMaybe []     = Nothing
headMaybe (x:xs) = Just x
```

The Dhall translation of `TreeInt` and `ListInt` are Church-encoded types:

```dhall
let F = λ(r : Type) → < Leaf: Integer | Branch : { left : r, right : r } >
let TreeInt = ∀(r : Type) → (F r → r) → r
```

and

```dhall
let F = λ(r : Type) → < Nil | Cons : { head : Integer, tail : r } >
let ListInt = ∀(r : Type) → (F r → r) → r
```

Values of type `TreeInt` and `ListInt` are functions, so we cannot perform pattern matching on such values.
How can we implement functions like `isSingleLeaf` and `headMaybe` in Dhall?

The general method for translating pattern matching into Church-encoded types `C` consists of two steps.
The first step is to apply the standard function `unfix` of type `C → F C`.
The function `unfix` (sometimes also called `unroll` or `unfold`) is available for all Church-encoded types; we have shown its implementation above.

Given a value `c : C` of a Church-encoded type, the value `unfix c` will have type `F C`, which is typically a union type.
The second step is to use the ordinary pattern-matching (Dhall's `merge`) on that value.

As an example, consider the type `ListInt` defined by:

```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r
```


This technique allows us to translate `isSingleLeaf` and `headMaybe` to Dhall. Let us look at some examples.

For `C = TreeInt`, the type `F C` is the union type `< Leaf: Integer | Branch : { left : TreeInt, right : TreeInt } >`. The function `isSingleLeaf` is
implemented via pattern matching on that type:

```dhall
let F = λ(r : Type) → < Leaf: Integer | Branch : { left : r, right : r } >

let TreeInt = ∀(r : Type) → (F r → r) → r

let fmapF : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b =
    λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fa : F a) → merge {
      Leaf = (F b).Leaf,
      Branch = λ(branch : { left : a, right : a }) → (F b).Branch { left = f branch.left, right = f branch.right }
    } fa

-- Assume the definition of `unfix` as shown above.

let isSingleLeaf : TreeInt → Bool = λ(c : TreeInt) →
    merge {
      Leaf = λ(_ : Integer) → true,
      Branch = λ(_ : { left : TreeInt, right : TreeInt }) → false
    } (unfix c)
  in isSingleLeaf
```

For `C = ListInt`, the type `F C` is the union type `< Nil | Cons : { head : Integer, tail : ListInt } >`. The function `headOptional` that replaces
Haskell's `headMaybe` is written in Dhall like this:

```dhall
let F = λ(r : Type) → < Nil | Cons : { head : Integer, tail : r } >

let ListInt = ∀(r : Type) → (F r → r) → r

let fmapF : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b =
    λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fa : F a) → merge {
      Nil = (F b).Nil,
      Cons = λ(pair : { head : Integer, tail : a }) → (F b).Cons (pair // { tail = f pair.tail })
    } fa

-- Assume the definition of `unfix` as shown above.

let headOptional : ListInt → Optional Integer = λ(c : ListInt) →
    merge {
      Cons = λ(list : { head : Integer, tail : ListInt }) → Some (list.head),
      Nil = None Integer
    } (unfix c)
  in headOptional (cons -456 (cons +123 nil))
```

The result is computed as `Some -456`.

### Performance

Note that `unfix` is implemented by applying the Church-encoded argument to some function.
In practice, this means that `unfix` will to traverse the entire data structure.
This may be counter-intuitive.
For example, `headOptional` (as shown above) will need to traverse the entire list of type `ListInt` before
it can determine whether the list is not empty.

Church-encoded data are higher-order functions, and it is not possible to pattern match on them directly.
The data traversal is necessary to enable pattern matching for Church-encoded types.

As a result, the performance of programs will be often significantly slower when working with large Church-encoded data structures.
For example, concatenating or reversing lists of type `ListInt` takes time quadratic in the list length.

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

### Type constructors

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

### Example: Concatenating and reversing non-empty lists

Dhall's `List` data structure already has concatenation and reversal operations (`List/concat` and `List/reverse`).
As an example, let us implement those operations for _non-empty_ lists using a Church encoding.

Non-empty lists (`NEL: Type → Type`) can be defined recursively as:

```haskell
data NEL a = One a | Cons a (NEL a)
```

The recursion scheme corresponding to this definition is:

```haskell
data F a r = One a | Cons a r
```

Convert this definition to Dhall and write the corresponding Church encoding:

```dhall
let F = ∀(a : Type) → ∀(r : Type) → < One : a |  Cons : { head : a, tail: r } >
let NEL = ∀(a : Type) → ∀(r : Type) → (F a r → r) → r
```

It will be more convenient to rewrite the type `NEL` without using union or record types. An equivalent definition is:

```dhall
let NEL = λ(a : Type) → ∀(r : Type) → (a → r) → (a → r → r) → r
```

The standard constructors for `NEL` are:

- a function (`one`) that creates a list of one element
- a function (`cons`) that prepends a given value of type `a` to a list of type `NEL a`

Non-empty list values can be now built as `cons Natural 1 (cons Natural 2 (one Natural 3))` and so on.

```dhall
let one : ∀(a : Type) → a → NEL a =
    λ(a : Type) → λ(x : a) → λ(r : Type) → λ(ar : a → r) → λ(_ : a → r → r) → ar x
let cons : ∀(a : Type) → a → NEL a → NEL a =
    λ(a : Type) → λ(x : a) → λ(prev : NEL a) → λ(r : Type) → λ(ar : a → r) → λ(arr : a → r → r) → arr x (prev r ar arr)
let example1 : NEL Natural = cons Natural 1 (cons Natural 2 (one Natural 3))
let example2 : NEL Natural = cons Natural 3 (cons Natural 2 (one Natural 1))
```

The folding function is just an identity function:

```dhall
let foldNEL : ∀(a : Type) → NEL a → ∀(r : Type) → (a → r) → (a → r → r) → r =
    λ(a : Type) → λ(nel : NEL a) → nel
```

To see that this is a "right fold", apply `foldNEL` to some functions `ar : a → r` and `arr : a → r → r` and a three-element list such as `example1`. The result
will be `arr 1 (arr 2 (ar 3))`; the first function evaluation is at the right-most element of the list.

Folding with `one` and `cons` gives again the initial list:

```dhall
assert : example1 === foldNEL Natural example1 (NEL Natural) (one Natural) (cons Natural)
```

To concatenate two lists, we right-fold the first list and substitute the second list instead of the right-most element:

```dhall
let concatNEL: ∀(a : Type) → NEL a → NEL a → NEL a =
    λ(a : Type) → λ(nel1 : NEL a) → λ(nel2 : NEL a) →
        foldNEL a nel1 (NEL a) (λ(x : a) → cons a x nel2) (cons a)
let test = assert : concatNEL Natural example1 example2 === cons Natural 1 (cons Natural 2 (cons Natural 3 (cons Natural 3 (cons Natural 2 (one Natural 1)))))
```

To reverse a list, we right-fold over it and accumulate a new list by appending elements to it.

So, we will need a new constructor (`snoc`) that appends a given value of type `a` to a list of type `NEL a`, rather than prepending as `cons` does.

```dhall
let snoc : ∀(a : Type) → a → NEL a → NEL a =
    λ(a : Type) → λ(x : a) → λ(prev : NEL a) →
    foldNEL a prev (NEL a) (λ(y : a) → cons a y (one a x)) (cons a)
let test = assert example1 === snoc Natural 3 (snoc Natural 2 (one Natural 1))
```

Now we can write the reversing function:

```dhall
let reverseNEL : ∀(a : Type) → NEL a → NEL a =
    λ(a : Type) → λ(nel : NEL a) → foldNEL a nel (NEL a) (one a) (snoc a)
let test = assert : reverseNEL Natural example1 === example2
let test = assert : reverseNEL Natural example2 === example1
```


### Example: Sizing a Church-encoded type constructor

The functions `concatNEL` and `reverseNEL` shown in the previous section are specific to list-like sequences and cannot be straightforwardly generalized to other recursive types, such as trees.

We will now consider functions that can work with all Church-encoded type constructors.
The first examples are functions that compute the total size and the maximum depth of a data structure.

Suppose we are given an arbitrary recursion scheme `F` with two type parameters. It defines a type constructor `C` via Church encoding as:

```dhall
let F = ∀(a : Type) → ∀(r : Type) → ...
let C = ∀(a : Type) → ∀(r : Type) → (F a r → r) → r
```

We imagine that a value `p : C a` is a data structure that stores zero or more values of type `a`.

The "total size" of `p` is the number of the values of type `a` that it stores. For example, if `p` is a list of 5 elements then the size of `p` is 5. The size
of a `TreeInt` value `branch (branch (leaf +10) (leaf +20)) (leaf +30)` is 3 because it stores three numbers.

The "maximum depth" of `p` is the depth of nested recursion required to obtain that value. For example, if `p` is a `TreeInt`
value `branch (branch (leaf +10) (leaf +20)) (leaf +30)` then the depth of `p` is 2. The depth of a single-leaf tree (such as `leaf +10`) is 0.

The goal is to implement these functions generically, for all Church-encoded data structures at once.

Both of those functions need to traverse the entire data structure and to accumulate a `Natural` value. Let us begin with `size`:

```dhall
let size : ∀(a : Type) → ∀(ca : C a) → Natural =
  λ(a : Type) → λ(ca : C a) →
    let sizeF : F a Natural → Natural = ??? 
    in ca Natural sizeF
```

The function `sizeF` should count the number of data items stored in `F a Natural`. The values of type `Natural` inside `F` represent the sizes of nested
instances of `C a`; those sizes have been already computed.

It is clear that the function `sizeF` will need to be different for each recursion scheme `F`.
For a given value `fa : f a Natural`, the result of `sizeF fa` will be equal to the number of values of type `a` stored in `fa` plus the sum of all natural
numbers stored in `fa`.

For example, non-empty lists are described by `F a r = < One : a | Cons : { head : a, tail: r } >`.
The corresponding `sizeF` function is:

```dhall
let sizeF : < One : a | Cons : { head : a, tail: Natural } > → Natural = λ(fa : < One : a | Cons : { head : a, tail: Natural } >) → merge {
      One = λ(x : a) → 1,
      Cons = λ(x : { head : a, tail: Natural }) → 1 + x.tail,
   } fa
```

Binary trees are described by `F a r = < Leaf : a | Branch : { left : r, right: r } >`.
The corresponding `sizeF` function is:

```dhall
let sizeF : < Leaf : a | Branch : { left : Natural, right: Natural } > → Natural = λ(fa : < Leaf : a | Branch : { left : Natural, right: Natural } >) → merge {
      Leaf = λ(x : a) → 1,
      Branch = λ(x : { left : Natural, right: Natural }) → x.left + x.right,
   } fa
```

Having realized that `sizeF` needs to be supplied for each recursion scheme `F`, we can implement `size` like this:

```dhall
let size : ∀(a : Type) → ∀(sizeF : ∀(b : Type) → F b Natural → Natural) → ∀(ca : C a) → Natural =
  λ(a : Type) → λ(ca : C a) → λ(sizeF : ∀(b : Type) → F b Natural → Natural) →
    ca Natural (sizeF a)
```

Turning now to the `depth` function, we proceed similarly and realize that the only difference is in the `sizeF` function.
Instead of `sizeF` described above, we need `depthF` with the same type signature `∀(b : Type) → F b Natural → Natural`.
For the depth calculation, `depthF` should return 1 plus the maximum of all values of type `Natural` that are present. If no such values are present, it just
returns 1.

For non-empty lists (and also for empty lists), the `depthF` function is the same as `sizeF` (because the recursion depth is the same as the list size).

For binary trees, the corresponding `depthF` function is:

```dhall
let depthF : < Leaf : a | Branch : { left : Natural, right: Natural } > → Natural = λ(fa : < Leaf : a | Branch : { left : Natural, right: Natural } >) → Natural/subtract 1 (merge {
      Leaf = λ(x : a) → 1,
      Branch = λ(x : { left : Natural, right: Natural }) → 1 + Natural/max x.left x.right,
   } fa)
```

Here, the functions `Natural/max` and `Natural/subtract` come from Dhall's standard prelude.

### Example: implementing `fmap`

A type constructor `F` is **covariant** if it admits an `fmap` function with the type signature:

```dhall
fmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b
```

satisfying the appropriate laws (the identity and the composition laws).

Type constructors such as lists and trees are covariant in their type arguments.

As an example, let us implement the `fmap` method for the type constructor `Tree` in the curried Church encoding:

```dhall
let Tree = λ(a : Type) → ∀(r : Type) → (a → r) → (r → r → r) → r
let fmapTree
  : ∀(a : Type) → ∀(b : Type) → (a → b) → Tree a → Tree b
   = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(treeA : Tree a) →
     λ(r : Type) → λ(leafB : b → r) → λ(branch : r → r → r) →
       let leafA : a → r = λ(x : a) → leafB (f x)
         in treeA r leafA branch
```

This code only needs to convert a function argument of type `b → r` to a function of type `a → r`.
All other arguments are just copied over.

We can generalize this code to the Church encoding of an arbitrary recursive type constructor with a recursion scheme `F`.
We need to convert a function argument of type `F b r → r` to one of type `F a r → r`.
This can be done if `F` is a covariant bifunctor with a known `bimap` function (which we call `bimap_F`).

The code is:

```dhall
let F : Type → Type → Type = λ(a : Type) → λ(b : Type) → ... -- Define the recursion scheme.
let bimap_F
 : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → ∀(d : Type) → (a → c) → (b → d) → F a b → F c d
  = ... -- Define the bimap function for F.
let C : Type → Type = λ(a : Type) → ∀(r : Type) → (F a r → r) → r

let fmapC
 : ∀(a : Type) → ∀(b : Type) → (a → b) → C a → C b
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(ca : C a) →
    λ(r : Type) → λ(fbrr : F b r → r) →
      let farr : F a r → r = λ(far : F a r) →
        let fbr : F b r = bimap_F a r b r f (identity r) far
          in fbrr fbr
            in ca r farr
```

### Generic forms of Church encoding

Dhall's type system is powerful enough to be able to express the Church encoding's type generically, as a function of an arbitrary recursion scheme.

For simple types:

```dhall
let Church : (Type → Type) → Type
  = λ(F : Type → Type) → ∀(r : Type) → (F r → r) → r
```

For type constructors:

```dhall
let Church1 : (Type → Type → Type) → Type
  = λ(F : Type → Type → Type) → λ(a : Type) → ∀(r : Type) → (F a r → r) → r
```

Implementations of several standard functions in Church encoding (such as `fix`, `unfix`, `fmap` and others) can be written once and for all, as functions of `F` and methods such as `fmap_F` or `bimap_F`.

### Existentially quantified types

By definition, a value `x` has an **existentially quantified** type, denoted mathematically by `∃ t. P t`, where `P` is a type constructor, if `x` is a pair `(u, y)` where `u` is some specific type and `y` is a value of type `P u`.

An example is the following type definition in Haskell:

```haskell
data F a = forall t. Hidden (t -> Bool, t -> a)
```

The corresponding code in Scala is:

```scala
sealed trait F[_]
final case class Hidden[A, T](init: T => Boolean, transform: T => A) extends F[A]
```

The mathematical notation for the type of `F` is `F a = ∃ t. (t → Bool) × (t → a)`.

As we will discuss later in this book, the type `F` is an example of the "free functor" construction.
For now, we focus on the way the type parameter `t` is used in the Haskell code just shown. (In the Scala code, the corresponding type parameter is `T`.)

The type parameter `t` is bound by the quantifier and is visible only inside the type expression `∃ t. (t → Bool) × (t → a)`.
To create a value `x` of type `F a`, we will need to supply two functions, of types `t → Bool` and `t → a`, with a specific (somehow chosen) type `t`.
But when working with a value `x : F a`, we will not directly see the type `t` anymore.
The type of `x` is `F a` and does not show what `t` is.
(The type `t` is not a type parameter of `F a`.)
However, the type parameter `t` still "exists" inside the value `x`.
This motivation helps us remember the meaning of the name "existential".

Existential type quantifiers is not directly supported by Dhall.
Types using `∃` have to be Church-encoded in a special way, as we will now show.

We begin with this type expression:

```dhall
∀(r : Type) → (F a → r) → r
```

As `F a` does not depend on `r`, this Church encoding is simply equivalent to `F a` by the covariant Yoneda identity. (We discussed that above in the section "Church encoding of non-recursive types".)

This is just the first step towards a useful encoding.
Now we look at the function type `F a → r` more closely.

A value `x : F a` must be created as a pair of type `{ _1 : t → Bool, _2 : t → a }` with a chosen type `t`.
A function `f : F a → r` must produce a result value of type `r` from any value `x`, for any type `t`.

In fact, `f` may not inspect the type `t` or make choices based on `t` because the type `t` is existentially quantified and is hidden inside `x`.
So, the function `f` must work for all types `t` in the same way.

It means that the function `f` must have `t` as a _type parameter_.
The type of that function must be written as `f : ∀(t : Type) → { _1 : t → Bool, _2 : t → a } → r`. 

So, the final code for the Church encoding of `F` becomes:

```dhall
let F = λ(a : Type) → ∀(r : Type) → (∀(t : Type) → { _1 : t → Bool, _2 : t → a } → r) → r
```

It is important that the universal quantifier `∀(t : Type)` is _inside_ the type of an argument of `F`.
Otherwise, the encoding would not work.

To see an example of how to construct a value of type `F a`, let us set `a = Natural`.
The type `F Natural` then becomes `∀(r : Type) → (∀(t : Type) → { _1 : t → Bool, _2 : t → Natural } → r) → r`.
We construct a value `x : F Natural` like this:

```dhall
let x
 : ∀(r : Type) → (∀(t : Type) → { _1 : t → Bool, _2 : t → Natural } → r) → r
  = λ(r : Type) → λ(pack : ∀(t : Type) → { _1 : t → Bool, _2 : t → Natural } → r) →
    pack Integer { _1 = λ(x : Integer) → Integer/greaterThan x 10, _2 = λ(x : Integer) → Integer/clamp x }
```

In this code, we apply the given argument `pack` of type `∀(t : Type) → { _1 : t → Bool, _2 : t → Natural } → r` to some arguments.

It is clear that we may produce a value `x : F Natural` given any specific type `t` and any value of type `{ _1 : t → Bool, _2 : t → Natural }`.  
This exactly corresponds to the information contained within a value of an existentially quantified type `∃ t. (t → Bool) × (t → Natural)`.

To generalize this example to arbitrary existentially quantified types, we replace the specific type `{ _1 : t → Bool, _2 : t → a }` by an arbitrary type constructor `P t`.
It follows that the Church encoding of `∃ t. P t` is:

```dhall
let exists_t_in_P = ∀(r : Type) → (∀(t : Type) → P t → r) → r
```

To create a value of type `exists_t_in_P`, we just need to supply a specific type `t` together with a value of type `P t`.

```dhall
let our_type_t : Type = ...   -- Can be any specific type here.
let our_value : P t = ...   -- Any specific value here.
let e : exists_t_in_P = λ(r : Type) → λ(pack : ∀(t : Type) → P t → r) → pack our_type_t our_value
```

Heuristically, the function application `pack X y` will "pack" a given type `X` under the "existentially quantified wrapper" together with a value `y`.

#### Constructors for existential types

To work with existential types more conveniently, let us implement generic functions for creating existentially quantified types and for producing and consuming values of those types.
The three functions are called `Exists`, `pack`, and `unpack`.

The function call `Exists P` creates the type corresponding to the Church encoding of the type `∃ t. P t`.
The argument of `Exists` is a type constructor `P`.

```dhall
let Exists : (Type → Type) → Type
  = λ(P : Type → Type) → ∀(r : Type) → (∀(t : Type) → P t → r) → r
```

The function `Exists` replaces the mathematical notation `∃ t. P t` by a similar formula: `Exists (λ(t : Type) → P t)`. 

The function `pack` creates a value of type `Exists P` from a type `t`, a type constructor `P`, and a value of type `P t`.

```dhall
let pack : ∀(P : Type → Type) → ∀(t : Type) → P t → Exists P
  = λ(P : Type → Type) → λ(t : Type) → λ(pt : P t) →
      λ(r : Type) → λ(pack_ : ∀(t_ : Type) → P t_ → r) → pack_ t pt
```

The function `unpack` performs transformations of type `Exists P → r`, where `r` is some arbitrary result type.
So, `unpack` needs to be able to convert a value of type `P t` into a value of type `r` regardless of the actual type `t` encapsulated inside `Exists P`.
To achieve that, one of the arguments of `unpack` will be a function of type `∀(t : Type) → P t → r`.
Other arguments of `unpack` are the type constructor `P`, a value of type `Exists P`, and the result type `r`.

```dhall
let unpack : ∀(P : Type → Type) → Exists P → ∀(r : Type) → (∀(t : Type) → P t → r) → r 
  = λ(P : Type → Type) → λ(ep : Exists P) → λ(r : Type) → λ(unpack_ : ∀(t : Type) → P t → r) →
      ep r unpack_
```

We notice that `unpack` does nothing more than rearrange the curried arguments and substitute them into the function `ep`.
This is so because `unpack P` is the same as the identity function of type `Exists P → Exists P`.
So, we can just use values of type `Exists P` as functions, instead of using `unpack`.

#### Functions of existential types

The fact that `unpack` is an identity function allows us to simplify the function type `Exists P → q`, where `q` is some fixed type.

To see how, let us consider `P` as fixed and rewrite the type of `unpack P` by swapping some curried arguments.
We will denote the resulting function by `inE`:

```dhall
inE : ∀(r : Type) → (∀(t : Type) → P t → r) → (Exists P → r)
  = λ(r : Type) → λ(unpack_ : ∀(t : Type) → P t → r) → λ(ep : Exists P) →
    ep r unpack_
```

This type signature suggests that the function type `Exists P → r` (written in full as `(∀(a : Type) → (∀(t : Type) → P t → a) → a) → r`) is equivalent to a simpler type `∀(t : Type) → P t → r`.

Indeed, this type equivalence (an isomorphism) can be proved rigorously.
The function `inE` shown above is one side of the isomorphism.
The other is the function `outE`:

```dhall
outE : ∀(r : Type) → (Exists P → r) → ∀(t : Type) → P t → r
  = λ(r : Type) → λ(consume : Exists P → r) → λ(t : Type) → λ(pt : P t) →
    let ep : Exists P = pack P t pt
      in consume ep
```

We will prove below that the functions `inE` and `outE` are inverses of each other.
We will also prove that `pack` and `unpack` are inverses in a certain sense.
(See the section "Naturality and parametricity").

Because of this type isomorphism, it is not necessary to use a complicated type `Exists P → r`.
Instead, we may use the simpler and equivalent type `∀(t : Type) → P t → r`.

#### Differences between existential and universal quantifiers

The only way of working with values of existentially quantified types, such as `ep : Exists P`, is by using the functions `pack` and `unpack`.

To create a value `ep`, we call `pack P t pt` with a specific type `t` and a specific value `pt : P t`.
The type `t` is set when we create the value `ep` and may be different for different such values.

But the specific type `t` used while constructing `ep` will no longer be exposed to the code outside of `ep`.
One could say that the type `t` "exists only inside" the scope of `ep` and is hidden (or encapsulated) within that scope.

This behavior is quite different from that of values of universally quantified types.
For example, the polymorphic `identity` function has type `identity : ∀(t : Type) → t → t`.
When we apply `identity` to a specific type, we get a value such as:

```dhall
let idText : Text → Text = identity Text
```

When constructing `idText`, we use the type `Text` as the type parameter.
After that, the type `Text` is exposed to the outside code because it is part of the type of `idText`.

When constructing a value `ep : Exists P`, we also need to use a specific type as `t` (say, `t = Text` or other type).
But that type is then hidden inside `ep`, because the externally visible type of `ep` is `Exists P` and does not contain `t` anymore.  

It is actually not hidden that `ep` _has_ a type parameter `t` inside.
The hidden information is the actual value of `t` used while constructing `ep`.
Let us clarify how that works.

Code that uses `ep` must use `unpack` with an argument function `unpack_ : ∀(t : Type) → P t → r`.
We must implement `unpack_` ourselves.
Most often, `P t` will be a data type that stores some values of type `t`.
Because the code of `unpack_` receives `t` and `P t` as arguments, we will be able to extract some values of type `t` and to pass those values around (while computing the result value of some type `r`).
For instance, a value `x` of type `t` can be further substituted into a function of type `∀(t : Type) → ∀(x : t) → ...` because that function can accept an argument `x` of any type.
But all such functions are constrained to work _in the same way_ for all types `t`.
Such functions will not be able to identify specific types `t` or make decisions based on specific values `x : t`.
In this sense, type quantifiers ensure encapsulation of the type `t` inside `ep`.

## Co-inductive ("infinite") types

### Greatest fixpoints: Motivation

Recursive types are usually specified via type equations of the form `T = F T`.
So far, we have used the Church encoding technique for representing such recursive types in Dhall.
But Church encodings always give the **least fixpoints** of type equations.
The least fixpoints give types that are also known as "inductive types".
Another useful kind of fixpoints are **greatest fixpoints**, also known as "co-inductive" types.

Intuitively, the least fixpoint is the smallest data type `T` that satisfies `T = F T`.
The greatest fixpoint is the largest possible data type that satisfies the same equation.

Least fixpoints are always _finite_ structures.
Iteration over the data stored in those structures will always terminate.

Greatest fixpoints are, as a rule, lazily evaluated data structures that imitate infinite recursion.
Iteration over those data structures is not expected to terminate.
Those data structures are used only in ways that do not involve a full traversal of all data.
It is useful to imagine that those data structures are "infinite", even though the amount of data stored in memory is finite at all times.

As an example, consider the recursion scheme `F` for the data type `List Text`.
The mathematical notation for `F` is `F r = 1 + Text × r`, and a Dhall definition is:

```dhall
let F = ∀(r : Type) → < Nil | Cons { head : Text, tail : r } >
```

The type `List Text` is the least fixpoint of `T = F T`.
A data structure of type `List Text` always stores a finite number of `Text` strings (although the list's length is not bounded in advance).

The greatest fixpoint of `T = F T` is a (potentially infinite) stream of `Text` values.
The stream could terminate after a finite number of strings, but it could also go on indefinitely.

Of course, we cannot specify infinite streams by literally storing an infinite number of strings in memory.
One way of implementing such streams is by giving an initial value of some type `r` (the "seed") and a function that computes the next string on demand (the "step").
In the present example, that function will have type `r → < Nil | Cons { head : Text, tail : r } >`.
When that function is applied to a value of type `r`, the function will decide either to return `Nil` (i.e., decide to stop the stream) or to return a `Text` string together with a new value of type `r`.

The type `r` represents the internal state of the stream's decision process and may be different for different streams.
However, the type `r` is not visible to the code outside the stream.
That code can only extract values of type `r` and pass those values around without being able to do anything else with them.
This is enforced by the type quantifiers:
To operate on a stream, we will have to write code of the form `λ(r : Type) → ...`.
That code will have to work in the same way for all types `r` and will not be able to inspect those types or values of those types.

### Encoding of greatest fixpoints with existential types

This motivates the following implementation of the greatest fixpoint of `T = F T` in the general case:

We take some unknown type `r` and implement `T` as a pair of types `r` and `r → F r`.
To hide the type `r` from outside code, we need to impose an existential quantifier on `r`.

So, the mathematical notation for the greatest fixpoint of `T = F T` is `T = ∃ r. r × (r → F r)`.
The corresponding Dhall code uses the type constructor `Exists` that we defined in a previous section.

To use `Exists`, we need to supply a type constructor that creates the type expression `r × (r → F r)`.
We will call that type constructor `GF_T` and use it to define `GFix`:

```dhall
let GF_T = λ(F : Type → Type) → λ(r : Type) → { seed : r, step : r → F r }
let GFix = λ(F : Type → Type) → Exists (GF_T F)
```

Note that the "step" function is non-recursive.
It advances the stream by only one step.
So, the entire definition `GFix` is non-recursive and will be accepted by Dhall.
Nevertheless, `GFix F` is equivalent to a recursive type.

To see `GFix` as a higher-order function, we expand that definition in Dhall's REPL:

```dhall
⊢ GFix

λ(F : Type → Type) →
  ∀(r : Type) → (∀(t : Type) → { seed : t, step : t → F t } → r) → r
```

A rigorous proof that `GFix F` is indeed the greatest fixpoint of `T = F T` is shown in the paper "Recursive types for free".
Hre, we will focus on the practical use of the greatest fixpoints.

### The fixpoint isomorphisms

To show that `GFix F` is a fixpoint of `T = F T`, we write two functions, `fix : F T → T` and `unfix : T → F T`, which are inverses of each other.
(This is proved in the paper "Recursive types for free".)

To implement these functions, we need to assume that `F` has a known `fmap` method:

```dhall
let fmap_F : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b = ...
```

We begin by implementing `unfix : GFix F → F (GFix F) = λ(g : GFix F) → ...` (that function is called `out` in the paper "Recursive types for free").

Let us write the type of `g` in detail:

```dhall
g : ∀(r : Type) → (∀(t : Type) → { seed : t, step : t → F t } → r) → r
```

One way of consuming such a value is by applying the function `g` to some arguments.

We need to return a value of type `F (GFix F)` as the final result of `unfix g`.
The return type of `g` is an arbitrary type `r` (which is the first argument of `g`).
Because we need to return a value of type `F (GFix F)`, we set `r = F (GFix F)`.

The second argument of `g` is a function of type `∀(t : Type) → { seed : t, step : t → F t } → F (GFix F)`.
If we could produce such a function `f`, we would complete the code of `unfix`:

```dhall
let unfix : GFix F → F (GFix F)
  = λ(g : ∀(r : Type) → (∀(t : Type) → { seed : t, step : t → F t } → r) → r) →
    let f
     : ∀(t : Type) → { seed : t, step : t → F t } → F (GFix F)
      = λ(t : Type) → λ(p : { seed : t, step : t → F t }) → ???
        in g (F (GFix F)) f
```

Within the body of `f`, we have a type `t` and two values `p.seed : t` and `p.step : t → F t`.
So, we can create a value of type `GFix F` using that data as `pack (GF_T F) t p`.
However, `f` is required to return a value of type `F (GFix F)` instead.
To achieve that, we use a trick: we first create a function of type `t → GFix F`.

```dhall
let k : t → GFix F = λ(x : t) → pack (GF_T F) t p
```

Then we will apply `fmap_F` to that function, which will give us a function of type `F t → F (GFix F)`.

```dhall
let fk : F t → F (GFix F) = fmap_F t (GFix F) k
```

Finally, we apply the function `fk` to `p.step p.seed`, which is a value of type `F t`.
The result is a value of type `F (GFix F)` as required.

The complete Dhall code is:

```dhall
let unfix : GFix F → F (GFix F)
  = λ(g : ∀(r : Type) → (∀(t : Type) → { seed : t, step : t → F t } → r) → r) →
    let f
     : ∀(t : Type) → { seed : t, step : t → F t } → F (GFix F)
      = λ(t : Type) → λ(p : { seed : t, step : t → F t }) →
        let k : t → GFix F = λ(x : t) → pack (GF_T F) t p
        let fk : F t → F (GFix F) = fmap_F t (GFix F) k
          in fk (p.step p.seed)
            in g (F (GFix F)) f
```

Implementing the function `fix : F (GFix F) → GFix F` is simpler, once we have `unfix`.
We first compute `fmap_F unfix : F (GFix F) → F (F (GFix F))`.
Then we create a value of type `GFix F` by using `pack` with `t = F (GFix F)`: 

```dhall
let fix : F (GFix F) → GFix F
  = λ(fg : F (GFix F)) →
    let fmap_unfix : F (GFix F) → F (F (GFix F)) = fmap_F (GFix F) (F (GFix F)) unfix
      in pack (GF_T F) (F (GFix F)) { seed = fg, step = fmap_unfix }
```

### Data constructors

To create values of type `GFix F` more conveniently, we will now implement a function called `makeGFix`.
The code of that function uses the generic `pack` function (see the section about existential types) to create values of type `∃ r. r × (r → F r)`.

```dhall
let makeGFix = λ(F : Type → Type) → λ(r : Type) → λ(x : r) → λ(rfr : r → F r) →
  let P = λ(r : Type) → { seed : r, step : r → F r }
    in pack (GF_T F) r { init = x, step = rfr } 
```

Creating a value of type `GFix F` requires an initial "seed" value and a "step" function.
We imagine that the code will run the "step" function as many times as needed, in order to retrieve more values from the data structure.

The required reasoning is quite different from that of creating values of the least fixpoint types.
The main difference is that the `seed` value needs to carry enough information for the `step` function to decide which new data to create at any place in the data structure.

Because the type `T = GFix F` is a fixpoint of `T = F T`, we always have the function `fix : F T → T`.
That function, similarly to the case of Church encodings, the function `fix` provides a set of constructors for `GFix F`.
Those constructors are "finite": they cannot create an infinite data structure.
For that, we need the general constructor `makeGFix`.

We can also apply `unfix` to a value of type `GFix F` and obtain a value of type `F (GFix F)`.
We can then perform pattern-matching directly on that value, since `F` is typically a union type.

So, similarly to the case of Church encodings, `fix` provides constructors and `unfix` provides pattern-matching for co-inductive types.

To build more intuition for working with co-inductive types, we will now implement a number of functions for a specific example.

#### Example of a co-inductive type: Streams

Consider the greatest fixpoint of the recursion scheme for `List`:

```dhall
let F = λ(a : Type) → λ(r : Type) → < Nil | Cons : { head : a, tail : r } >
let fmap_F
 : ∀(x : Type) → ∀(a : Type) → ∀(b : Type) → (a → b) → F x a → F x b
  = λ(x : Type) → λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fa : F x a) →
    merge { Nil = (F x b).Nil
          , Cons = λ(cons : { head : x, tail : a }) →
            (F x b).Cons { head = cons.head, tail = f cons.tail }
          } fa
let Stream = λ(a : Type) → GFix (F a)
let makeStream = makeGFix F
```

Values of type `Stream a` are higher-order functions with quantified types.

For more clarity about how to create and use values of type `Stream a`, let us expand the definitions of `Stream` and `makeStream` using Dhall's REPL:

```dhall
⊢ Stream

λ(a : Type) →
  ∀(r : Type) →
  ( ∀(t : Type) →
    { seed : t, step : t → < Cons : { head : a, tail : t } | Nil > } → r
  ) → r

⊢ makeStream

λ(a : Type) → λ(r : Type) → λ(x : r) →
λ(rfr : r → < Cons : { head : a, tail : r } | Nil >) →
λ(res : Type) →
λ ( pack_
 : ∀(t_ : Type) →
    { seed : t_, step : t_ → < Cons : { head : a, tail : t_ } | Nil > } →
      res
  ) →
  pack_ r { seed = x, step = rfr }
```

The type of `makeStream` can be simplified to:

```dhall
makeStream : λ(a : Type) → λ(r : Type) →
  λ(x : r) → λ(rfr : r → < Cons : { head : a, tail : r } | Nil > )
    → Stream a
```

We see that `makeStream` constructs a value of type `Stream a` out of an arbitrary type `a`, a type `r` (the internal state of the stream), an initial "seed" value of type `r`, and a "step" function of type `r → < Cons : { head : a, tail : r } | Nil >`.

The type `Stream a` is heuristically understood as a potentially infinite stream of data items (values of type `a`).
Of course, we cannot store infinitely many values in memory.
Values are retrieved one by one, by running the "step" function as many times as needed, or until "step" returns `Nil` (indicating the end of the stream).

Given a value `s : Stream a`, how can we run the "step" function?
We need to apply `s` (which is a function) to an argument of the following type:

```dhall
∀(t : Type) → { seed : t, step : t → < Cons : { head : a, tail : t } | Nil > } → r
```

So, we need to provide a function of that type.
That function's code will be of the form:

```dhall
λ(t : Type) → λ(stream : { seed : t, step : t → < Cons : { head : a, tail : t } | Nil > }) → ...
```

So, the code may apply `stream.step` to values of type `t`.
One value of type `t` is already given as `stream.seed`.
Other such values can be obtained after calling `stream.step` one or more times.

As we step through the stream, the seed values (of type `t`) are changing but the "step" function always remains the same.

#### Pattern-matching on streams

Consider the tasks of extracting the "head" and the "tail" of a stream.

The "head" is either empty (if the stream is empty) or a value of type `a`.
We implement a function (`headTailOption`) that applies `stream.step` to `stream.seed` and performs pattern-matching on the resulting value of type `< Cons : { head : a, tail : t } | Nil >`.
If that value is non-empty, the function returns the corresponding values `head` and `tail` wrapped into the type `Optional { head : a, tail : Stream a }`.
Otherwise the function returns `None` of that type.

```dhall
let headTailOption
 : ∀(a : Type) → Stream a → Optional { head : a, tail : Stream a }
  = λ(a : Type) → λ(s : Stream a) →
    let headTail = λ(h : Type) → λ(t : Type) → { head : h, tail : t }
    let ResultT = headTail a (Stream a)
    let unpack_ = λ(t : Type) → λ(state : { seed : t, step : t → < Cons : headTail a t | Nil > }) → 
      merge {
         Cons = λ(cons : headTail a t) →
           Some { head = cons.head
                , tail = makeStream a t cons.tail state.step
                }
         , Nil = None ResultT
      } (state.step state.seed)
        in s (Optional ResultT) unpack_
```

Given a value of type `Stream a`, we may apply `headTailOption` several times to extract further data items from the stream, or to discover that the stream has finished.

#### Converting a stream to a `List`

Let us now implement a function `streamToList` that converts `Stream a` to `List a`.
That function will be used to extract the values stored in a stream, taking at most a given number of values.
The limit length must be specified as an additional argument.
Since streams may be infinite, it is impossible to convert a `Stream` to a `List` without limiting the length of the resulting list.
So, the type signature of `streamToList` must be something like `Stream a → Natural → List a`.

```dhall
let streamToList : ∀(a : Type) → Stream a → Natural → List a
 = λ(a : Type) → λ(s : Stream a) → λ(limit : Natural) →
   let Accum = { list : List a, stream : Optional (Stream a) }
   let init : Accum = { list = [] : List a, stream = Some s }
   let update : Accum → Accum = λ(prev : Accum) →
     let headTail : Optional { head : a, tail : Stream a } = merge { None = None { head : a, tail : Stream a }
                                                                   , Some = λ(str : Stream a) → headTailOption a str
                                                                   } prev.stream
       in merge { None = prev // { stream = None (Stream a) }
                , Some = λ(ht : { head : a, tail : Stream a } ) →  { list = prev.list # [ ht.head ], stream = Some ht.tail } } headTail
         in (Natural/fold limit Accum update init).list
```

#### Creating finite streams

Let us now see how to create streams.
We begin with finite streams.

To create an empty stream, we specify a "step" function that immediately returns `Nil` and ignores its argument.
We still need to supply a "seed" value, even though we will never use it.
Let us supply a value of the `Unit` type (in Dhall, `{}`):

```dhall
let Stream/nil : ∀(a : Type) → Stream a
  = λ(a : Type) → 
    let r = {}
    let seed : r = {=}
      in makeStream a r seed (λ(_ : r) → (F a r).Nil)
```

How can we create a finite stream, say, `[1, 2, 3]`?
We need a "seed" value that has enough information for the "step" function to produce all the subsequent data items of the stream.
So, it appears natural to use the list `[1, 2, 3]` itself (of type `List Natural`) as the "seed" value.
The "step" function will compute the tail of the list and stop the stream when the list becomes empty.

Let us we implement a general function of type `List a → Stream a` that converts a finite list into a finite stream.
We will need a helper function `headTail` that computes the head and the tail of a `List` if it is non-empty.

```dhall
let HeadTailT = λ(a : Type) → < Cons : { head : a, tail : List a } | Nil >

let headTail : ∀(a : Type) → List a → HeadTailT a
  = λ(a : Type) → λ(list : List a) →
    let getTail = https://prelude.dhall-lang.org/List/drop 1 a
      in merge { None = (HeadTailT a).Nil
               , Some = λ(h : a) → (HeadTailT a).Cons { head = h, tail = getTail list }
      } (List/head a list)

let listToStream : ∀(a : Type) → List a → Stream a
  = λ(a : Type) → λ(list : List a) → makeStream a (List a) list (headTail a)
```

#### Creating infinite streams

Creating an infinite stream involves deciding how the next data item should be computed.
A simple example is an infinite stream generated from a seed value `x : r` by applying a function `f : r → r`.
The "step" function will never return `Nil`, which will make the stream unbounded.

```dhall
let streamFunction
 : ∀(a : Type) → ∀(seed : a) → ∀(f : a → a) → Stream a
  = λ(a : Type) → λ(seed : a) → λ(f : a → a) →
    let FA = < Cons : { head : a, tail : a } | Nil >
    let step : a → FA = λ(x : a) → FA.Cons { head = x, tail = f x }
      in makeStream a a seed step
```

We can compute a finite prefix of an infinite stream:

```dhall
⊢ streamToList Natural (streamFunction Natural 1 (λ(x : Natural) → x * 2)) 5

[ 1, 2, 4, 8, 16 ]
```

One can also implement streams that repeat a certain sequence infinitely many times: for example, `1, 2, 3, 1, 2, 3, 1, `...
For that, the "seed" type can be `List Natural` and the "step" function can be similar to that in the code of `listToStream`.
The initial "seed" value is the list `[ 1, 2, 3 ]`.
Whenever the "seed" value becomes an empty list, it is reset to the initial list `[ 1, 2, 3 ]`.

```dhall
let repeatForever : ∀(a : Type) → List a → Stream a
  = λ(a : Type) → λ(list : List a) →
    let getTail = https://prelude.dhall-lang.org/List/drop 1 a
    let mkStream = λ(h : { head : a, tail : List a }) → 
      let step : List a → HeadTailT a = λ(prev : List a) →
        merge { None = (HeadTailT a).Cons { head = h.head, tail = h.tail }
              , Some = λ(x : a) → (HeadTailT a).Cons { head = x, tail = getTail prev }
        } (List/head a prev)
        in makeStream a (List a) list step
    -- Check whether `list` is empty. If so, return an empty stream.
      in merge { Nil = Stream/nil a
               , Cons = λ(h : { head : a, tail : List a }) → mkStream h
               } (headTail a list)

let _ = assert : streamToList Natural (repeatForever Natural [ 1, 2, 3 ]) 7
        ≡ [ 1, 2, 3, 1, 2, 3, 1 ]
```

#### Concatenating streams

The standard Dhall function for concatenating lists is `List/concat`.
Let us now implement an analogous function for concatenating streams.
The function `Stream/concat` will take two streams and will return a new stream that works by first letting the first stream run.
If the first stream ever finishes, the second stream will start (otherwise the first stream will continue running forever).

We can implement this behavior if the seed type of the new stream is a union type showing which stream is now running.
As the internal state of the new stream, we just store the first or the second stream.

```dhall
let Stream/concat : ∀(a : Type) → Stream a → Stream a → Stream a
  = λ(a : Type) → λ(first : Stream a) → λ(second : Stream a) →
    let State = < InFirst : Stream a | InSecond : Stream a >
    let StepT = < Cons : { head : a, tail : State } | Nil >
    let stepSecond = λ(str : Stream a) → merge {
              None = StepT.Nil
            , Some = λ(ht : { head : a, tail : Stream a }) → StepT.Cons { head = ht.head, tail = State.InSecond ht.tail }
          } (headTailOption a str)
    let step : State → StepT = λ(state : State) →
      merge {
          InFirst = λ(str : Stream a) → merge {
              None = stepSecond second    -- The first stream is finished. Switch to the second stream.
            , Some = λ(ht : { head : a, tail : Stream a }) → StepT.Cons { head = ht.head, tail = State.InFirst ht.tail }
          } (headTailOption a str) 
        , InSecond = stepSecond
      } state
        in makeStream a State (State.InFirst first) step
```

#### Size-limited streams

We can truncate an arbitrary stream after a given number `n` of items, creating a new value of type `Stream a` that has at most `n` data items.

```dhall
let Stream/truncate : ∀(a : Type) → Stream a → Natural → Stream a
 = λ(a : Type) → λ(stream : Stream a) → λ(n : Natural) →
   let State = { remaining : Natural, stream : Stream a}    -- Internal state of the new stream.
   let StepT = < Nil | Cons : { head : a, tail : State } >
   let step : State → StepT = λ(state : State) →
       if Natural/isZero state.remaining then StepT.Nil else merge {
              None = StepT.Nil
            , Some = λ(ht : { head : a, tail : Stream a }) → 
             StepT.Cons { head = ht.head, tail = { remaining = Natural/subtract 1 state.remaining, stream =  ht.tail } }
          } (headTailOption a state.stream) 
         in makeStream a State { remaining = n, stream = stream } step
```

This is different from `streamToList` because we are not traversing the stream; we just need to modify the stream's seed and the step function.
So, `Stream/truncate` is a `O(1)` operation.

#### The `cons` constructor for streams. Performance issues

The `cons` operation for lists will prepend a single value to a list.
The analogous operation for streams can be implemented as a special case of concatenating streams:

```dhall
let Stream/cons : ∀(a : Type) → a → Stream a → Stream a
 = λ(a : Type) → λ(x : a) → λ(stream : Stream a) → Stream/concat a (listToStream a [ a ]) stream
```

We may use `Stream/nil` and `Stream/cons` to create finite streams, similar to how the constructors `nil` and `cons` create lists.

Are finite streams better than (Church-encoded) lists?

We have seen that the performance of Church-encoded data is slow when doing pattern-matching or concatenation.
For instance, pattern-matching a Church-encoded list will take time `O(N)`, where `N` is the size of the list.
Even just finding out whether a Church-encoded list is empty will still need to traverse the entire list.

The situation for streams is different but not "better".
Since streams may be infinite, no operation on a stream could ever require traversing the entire data structure.
At most, an operation may step the stream once.
So, all stream operations like pattern-matching or concatenating are not iterative and, _at first sight_, take `O(1)` time.

However, streams are higher-order functions operating with complicated types.
We need to consider the complexity of code that represents a stream.
For instance, the `Stream/cons` operation creates a new stream whose state type is a union of two stream types.
So, if we use the `Stream/cons` constructor many times, we will obtain a stream with a "large" state type (a deeply nested union type).
Pattern-matching operations with that type will take `O(N)` time in the Dhall interpreter.

The result is a stream where _every_ operation (even just producing the next item) takes `O(N)` time.

### Sliding-window aggregation (`scan`)

### Size-limited aggregation. Hylomorphisms with bounded recursion depth

We have seen the function `streamToList` that extracts at most a given number of values from the stream.
This function can be seen as an example of a **size-limited aggregation**: a function that aggregates data from the stream in some way but reads no more than a given number of data items from the stream.
The size limit is important for guaranteeing termination.

We will now generalize size-limited aggregations from lists to arbitrary greatest fixpoint types.
The result will be a `fold`-like function whose recursion depth is limited in advance.
That limitation will ensure that all computations terminate, as Dhall requires.

The type signature of `fold` is a generalization of `List/fold` to arbitrary recursion schemes.
We have seen `fold`'s type signature when we considered fold-like aggregations for Church-encoded data:

```dhall
fold : Church F → ∀(r : Type) → (F r → r) → r
```

We generalize the idea of a **fold-like aggregation** to mean any function applied to some data type `P` that iterates over the values stored in `P` in some way.
The general type signature of a fold-like aggregation is `P → ∀(r : Type) → (F r → r) → r`.

The implementation of `fold` will be different for each data structure `P`.
If `P` is the Church encoding of the least fixpoint of `F` then `P`'s `fold` is an identity function because the type `Church F` is the same as `∀(r : Type) → (F r → r) → r`.
If `P` is the greatest fixpoint (`GFix F`), the analogous signature of `P`'s `fold` would be:

```dhall
fold_GFix : GFix F → ∀(r : Type) → (F r → r) → r
```

Note that this type is a function from an existential type in `GFix F`.
Function types of that kind are equivalent to simpler function types (see the section "Functions of existential types" above):

```dhall
GFix F → Q  =  Exists (GF_T F) → Q
  =  ∀(t : Type) → GF_T F → Q
```

We use this equivalence with `Q = ∀(r : Type) → (F r → r) → r` and obtain this type signature:

```dhall
fold_GFix : ∀(t : Type) → { seed : t, step : t → F t } → ∀(r : Type) → (F r → r) → r
```

We may equivalently rewrite that type by replacing a record by two curried arguments:

```dhall
fold_GFix_curried : ∀(t : Type) → t → (t → F t) → ∀(r : Type) → (F r → r) → r
```

Functions of that type are called **hylomorphisms**.
See, for example, [this tutorial](https://blog.sumtypeofway.com/posts/recursion-schemes-part-5.html).

The immediate problem for Dhall is that hylomorphisms do not (and cannot) guarantee termination.
Let us examine that problem is more detail.

#### Example: why hylomorphisms terminate (in Haskell)

For the purposes of this book, a hylomorphism is just the `fold` function operating on the greatest fixpoint of a given recursion scheme `F`.
We would like to implement a hylomorphism with that type signature that works in a uniform way for all `F`.
This is possible if we use explicit recursion (which Dhall does not support).
Here is Haskell code adapted from [B. Milewski's blog post](https://bartoszmilewski.com/2018/12/20/open-season-on-hylomorphisms/):

```haskell
hylo :: Functor f => (t -> f t) -> (f r -> r) -> t -> r
hylo coalg alg = alg . fmap (hylo coalg alg) . coalg
```

The code of `hylo` calls `hylo` recursively under `fmap`, and there seems to be no explicit termination for the recursion.
To see how this code could ever terminate, we will consider a specific example.
In that example, both `t` and `r` are the type of binary trees with string-valued leaves (we have denoted that type by `TreeText` before).
The type constructor `f` will be the recursion scheme for `TreeText`.

Our Haskell definitions for `TreeText`, its recursion scheme `F`, and the `fmap` method for `F` are:

```haskell
data TreeText = Leaf String | Branch TreeText TreeText

data F r = FLeaf String | FBranch r r

fmap :: (a -> b) -> F a -> F b
fmap f (FLeaf t) = FLeaf t
fmap f (FBranch x y) = FBranch (f x) (f y)
```

The type `TreeText` is the least fixpoint of `F` and has the standard methods `fix : F TreeText → TreeText` and `unfix : TreeText → F TreeText`.
Haskell implementations of `fix` and `unfix` are little more than identity functions that reassign types:

```haskell
fix :: F TreeText -> TreeText
fix FLeaf t -> Leaf t
fix FBranch x y -> Branch x y

unfix :: TreeText -> F TreeText
unfix Leaf t -> FLeaf t
unfix Branch x y -> FBranch x y
```

We may substutite `fix` and `unfix` as the `alg` and `coalg` arguments of `hylo` as shown above, because the types match.
The result (`hylo unfix fix`) will be a function of type `TreeText → TreeText`.
Because `fix` and `unfix` leave data unchanged, the function `hylo unfix fix` will be just an identity function of type `TreeText → TreeText`.
In this example of applying `hylo`, the trees remain unchanged because we are unpacking the tree's recursive type (`TreeText → F TreeText`) and then packing it back (`F TreeText → TreeText`) with no changes.
(We are using this example only to understand how the recursion can terminate when applying `hylo`.)

Choose some value `t0` of type `TreeText`:

```haskell
t0 :: TreeText
t0 = Branch (Leaf "a") (Leaf "b")
```

Denote `hylo unfix fix` by just `h` for brevity.
The recursive code of `h` is just `h = fix . fmap h . unfix`.
Now we expand the recursive definition of `h` three times in the expression `h t0`:

```haskell
h t0
  == (fix . fmap h . unfix) t0
  == fix (fmap h (unfix t0))
  == fix (fmap (fix . fmap h . unfix) (unfix t0))
  == fix ( (fmap fix . fmap (fmap h) . fmap unfix) (unfix t0))
  == fix (fmap fix (fmap (fmap h) (fmap unfix (unfix t0))))
```

The argument of `fmap (fmap h)` in the last line is `fmap unfix (unfix t0)`.
This is a value of type `F (F TreeText)` that we may temporarily denote by `c0`.
Then `h t0` is given by:

```haskell
h t0 == fix (fmap fix (fmap (fmap h) c0))
```

Let us compute `c0`:

```haskell
unfix t0 == FBranch (Leaf "a") (Leaf "b")

c0 = fmap unfix (unfix t0) == FBranch (FLeaf "a") (FLeaf "b")
```

We note that each application of `unfix` replaces one layer of `TreeText`'s constructors by one layer of `F`'s constructors.
All constructors of `TreeText` will be eliminated after applying `unfix`, `fmap unfix`, etc., as many times as the recursion depth of `t0`.

At that point, the value `c0` no longer contains any constructors of `TreeText`; it is built only with `F`'s constructors.
For that reason, `c0` will _remains unchanged_ under application of `fmap (fmap f)` with _any_ function `f : TreeText → TreeText`.
In other words:

```haskell
fmap (fmap f) c0 == c0
```

It follows that the computation `fmap (fmap f) c0` does not use the value `f`.

Our code for `h t0` needs to compute `fmap (fmap h) c0`.
Because that computation does not need the value `h`, Haskell will not perform any more recursive calls to `h`.
This is where the recursion terminates in the computation `h t0`.

If the value `t0` had been a more deeply nested tree, we would need to expand the recursive definition of `h` more times.
The required number of recursive calls is equal to the "depth" of the value `t0`.
(The subsection "Example: Sizing a Church-encoded type constructor" showed how to compute that depth for Church-encoded data types.)

We can now generalize this example to an arbitrary application of a hylomorphism.
For brevity, we denote `h = hylo coalg alg`.
The function `h : t -> r` is then defined by `h = alg . fmap h . coalg`.
When we apply `h` to some value `t0 : t`, we get: `h t0 = alg (fmap h (coalg t0))`.

The recursion will terminate if, at some recursion depth, the function call `fmap (fmap (... (fmap f)...)) c` does not actually need to use the function `f`.
We will then have `fmap (fmap (... (fmap f)...)) c == c`.
This will terminate the recursion.

The type of `c` will be `f (f (... (f t)))`.
It is a data structure generated by repeated applications of `coalg`, `fmap coalg`, `fmap (fmap coalg)`, etc., to the initial value `t0`.
These repeated applications generate a data structure of deeply nested type `f (f (... (f t)))`.
The hylomorphism terminates only if the data structure generated out of the initial "seed" value `t0` is finite. 

However, it is impossible to assure up front that the data structure is finite.
So, in general the hylomorphism code does not guarantee termination and is not acceptable in Dhall.
(In fact, a function with that type signature cannot be implemented in Dhall.)

#### Depth-limited hylomorphisms

Implementing hylomorphisms in Dhall requires modifyig the type signature shown above, explicitly ensuring termination.
One possibility is to add a `Natural`-valued bound on the depth of recursion and a "default" value (of type `t → r`).
The default value will be used when the recursion bound is smaller than the recursion depth of the data.
If the recursion bound is large enough, the hylomorphism will be actually independent of the default value.

To show how that works, we will first write Haskell code for the depth-limited hylomorphism.
Then we will translate that code to Dhall.

The idea of depth-limited hylomorphism is to expand the recursive definition (`h = alg . fmap h . coalg`, where we denoted `h = hylo coalg alg`) only a given number of times.
To be able to do that, we begin by setting `h = default` as the initial value (where `default : t → r` is a given default value) and then expand the recursive definition repeatedly.
For convenience, let us denote the intermediate results by `h_1`, `h_2`, `h_3`, ...:

```haskell
h_0 = default 
h_1 = alg . fmap h_0 . coalg
h_2 = alg . fmap h_1 . coalg
h_3 = alg . fmap h_2 . coalg
...
```

All the intermediate values `h_1`, `h_2`, `h_3`, ..., are still of type `t → r`.
After repeating this procedure `n` times (where `n` is a given natural number), we will obtain a function `h_n : t → r`.
The example shown in the previous subsection explains that applying `h_n` to a value `t` will give a result (of type `r`) that does not depend on the `default` value, as long as the recursion depth `n` is large enough.

Let us now implement this logic in Dhall:

```dhall
let hylo_N
 : Natural → ∀(t : Type) → t → (t → F t) → ∀(r : Type) → (F r → r) → (t → r) → r
  = λ(limit : Natural) → λ(t : Type) → λ(seed : t) → λ(coalg : t → F t) → λ(r : Type) → λ(alg : F r → r) → λ(default : t → r) →
    let update : (t → r) → t → r = λ(f : t → r) → compose_backward (alg (compose_backward (fmap_F f) coalg))
    let transform : t → r = Natural/fold limit (t → r) update default
      in transform seed
```

The function `hylo_N` is a general fold-like aggregation function that can be used with the greatest fixpoints of arbitrary recursion schemes `F`. 
Termination is assured because we specify a limit for the recursion depth in advance.
This function will be used later in this book for implementing the `zip` method for Church-encoded type constructors.

For now, let us see an example of using `hylo_N`.  TODO

#### Hylomorphisms driven by a Church-encoded template

In the code for `hylo_N`, the total number of iterations was limited by a given natural number.
To drive the iterations, we used the standard `fold` method (`Natural/fold`) for natural numbers.

Note that `Natural` is a recursive type whose `fold` method is a Dhall built-in.
Could we drive iterations via the `fold` method for a different recursive type?

Suppose we already have a value of the Church-encoded least fixpoint type (`Church F`).
That value can serve as a "recursion template" that at the same time provides depth limits and all necessary default values.

We will denote the template-driven hylomorphism by `hylo_T`.
The type signature is `Church F → GFix F → Church F`.
We will again expand the type signature and unpack the existential types into a curried argument.
The Dhall code is:

```dhall
let hylo_T
 : Church F → ∀(t : Type) → t → (t → F t) → ∀(r : Type) → (F r → r) → r
  = λ(template : Church F) → λ(t : Type) → λ(seed : t) → λ(coalg : t → F t) → λ(r : Type) → λ(alg : F r → r) →
    let F/ap : ∀(a : Type) → ∀(b : Type) → F (a → b) → F a → F b = ... -- Implement this function for F.
    let reduce : F (t → r) → t → r
      = λ(ftr : F (t → r)) → λ(arg : t) → alg (F/ap t r ftr (coalg t))
    let transform : t → r = template (t → r) reduce
      in transform seed 
```

For this code, we need to have a function `F/ap` with type `F (a → b) → F a → F b`.
In many cases, such a function exists.
This function is typical of "applicative functors", which we will study later in this book.

As long as the recursion scheme `F` is applicative, we will be able to implement `hylo_T` for `F`.

TODO example of usage

### Converting from the least fixpoint to the greatest fixpoint

A hylomorphisms can be seen as a conversion from the greatest fixpoint to the least fixpoint of the same recursion scheme.
Previous sections showed how to adapt hylomorphisms to recursion-less Dhall programming style.

The converse transformation (from the least fixpoint to the greatest fixpoint) can be implemented in Dhall directly, without changing the type signature.
Creating a value of the type `GFix F` requires a value of some type `t` and a function of type `t → F t`.
The least fixpoint type `Church F` already has that function (`unfix`).

TODO


## Functors and contrafunctors

In the jargon of functional programmers, a **functor** is just a covariant type constructor with `fmap`.
We can define a typeclass `Functor` that carries the required `fmap` method: 

```dhall
let Functor = λ(F : Type → Type) → { fmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b }
```

The complementary kind of type constructors is contravariant functors: they cannot have a lawful `fmap` method.
Instead, they have a `cmap` method with a type signature that flips one of the function arrows:

```dhall
cmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F b → F a
```

We will call contravariant type constructors **contrafunctors** for short.
The corresponding typeclass is defined by:

```dhall
let Contrafunctor = λ(F : Type → Type) → { cmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F b → F a }
```

A simple example of a contrafunctor is the type constructor `a → Text`.
Here is its definition and the code for a contrafunctor typeclass instance:

```dhall
let F = λ(a : Type) → a → Text
let contrafunctorF : Contrafunctor F
  = { cmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fb : F b) → λ(x : a) → fb (f x) } 
```

If a type constructor has several type parameters, it can be covariant with respect to some of those type parameters and contravariant with respect to others.
For example, the type constructor `F` defined by:

```dhall
let F = λ(a : Type) → λ(b : Type) → < Left : a | Right : b → Text >
```

is covariant in `a` and contravariant in `b`.

In this book, we will need **bifunctors** (type constructors covariant in two type parameters) and **profunctors** (type constructors contravariant in the first type parameter and covariant in the second).

To characterize such type constructors via a typeclass, we could specify `fmap` and `cmap` functions separately with respect to each type parameter.
It turns out that one can combine the `fmap` and `cmap` methods into a single method that works at once on all type parameters.
For bifunctors, that method is called `bimap`, and for profunctors, `xmap`.

The corresponding Dhall definitions of the typeclasses `Bifunctor` and `Profunctor` are:

```dhall
let Bifunctor : (Type → Type) → Type
  = λ(F : Type → Type) → { bimap : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → ∀(d : Type) → (a → c) → (b → d) → F a b → F c d }
let Profunctor : (Type → Type) → Type
  = λ(F : Type → Type) → { xmap : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → ∀(d : Type) → (c → a) → (b → d) → F a b → F c d }
```

### Constructing functors and contrafunctors from parts

TODO

## Filterable functors and contrafunctors

## Applicative covariant and contravariant functors

## Traversable functors

## Monads

## Monad transformers

## Free monads

## Free instances of other typeclasses

### Free semigroup and free monoid

### Free functor

### Free filterable

### Free applicative

### Nested types and GADTs

## Dhall as a scripting DSL

## Appendix: Naturality and parametricity

The properties known as "naturality" and "parametricity" are rigorous mathematical expressions of a programmer's intuition about functions with type parameters.

This appendix will describe some results of the theory that studies those properties, applied to Dhall programs.

To make the presentation easier to follow, we will denote all types by capital letters and all values by lowercase letters.

### Natural transformations

Type signatures of the form `∀(A : Type) → F A → G A`, where `F` and `G` are some type constructors, are often seen in practice.
Examples are functions like `List/head`, `Optional/concat`, and many others.

```dhall
⊢ :type List/head

∀(a : Type) → List a → Optional a

⊢ :type https://prelude.dhall-lang.org/Optional/concat

∀(a : Type) → ∀(x : Optional (Optional a)) → Optional a
```
In the last example, the type signature of `Optional/concat` is of the form `∀(A : Type) → F A → G A` if we define the type constructor `F` as `F a = Optional (Optional a)` and set `G = Optional`. 

Functions of type `∀(A : Type) → F A → G A` are called **natural transformations** when both `F` and `G` are covariant functors, or when both are contravariant.


If a function has several type parameters, it may be a natural transformation separately with respect to some (or all) of the type parameters.

To see how it works, consider the method `List/map` that has the following type signature:

```dhall
let List/map = https://prelude.dhall-lang.org/List/map
  in List/map : ∀(A : Type) → ∀(B : Type) → (A → B) → List A → List B
```

To see that `List/map` is a natural transformation, we first fix the type parameter `B`.
That is, we remove `∀(B : Type)` from the type signature and assume that the type `B` is defined and fixed.
Then we rewrite the type signature of `List/map` as `∀(A : Type) → F A → G A`, where the type constructors `F` and `G` are defined by `F X = X → B` and `G X = List X → List B`.
Both types `F X` and `G X` are _contravariant_ with respect to `X`.
So, `List/map` is a (contravariant) natural transformation with respect to the type parameter `A`.

Considering now the type parameter `B` as varying, we fix `A` and rewrite the type signature of `List/map` as `∀(B : Type) → K B → L B`, where `K` and `L` are defined by `K X = A → X` and `L X = List A → List X`.
Both `K` and `L` are covariant.
So, `List/map` is a (covariant) natural transformation with respect to the type parameter `B`.

### Naturality laws

Suppose both `F` and `G` are covariant functors and consider a natural transformation `t : ∀(A : Type) → F A → G A`.

Typically, a covariant functor represents a data structure, so that `F A` is a type that can store data of an arbitrary type `A`.
One expects that a natural transformation `t` takes some of the data of type `A` stored in `F A` and somehow arranges for that data to be stored in `G A`.
The function `t` may omit or duplicate or reorder some data items, but no data may be changed.
This is because the function `t` does not know anything about the type `A`.
The code of `t` can make decisions neither based on specific values `x : A` stored in `F A`, nor based on the type `A` itself.
The function `t` must work in the same way for all types `A` and for all values of those types.

The mathematical formulation of that property is called the **naturality law** of `t`.
It is an equation written like this: For any types `A` and `B`, and for any function `f : A → B`:

$$ t . fmap_F f  = fmap_G f . t  $$

To represent this very concise mathematical formula in Dhall, we write the following definitions:

```dhall
-- Define the type constructor F and its fmap method:
let F : Type → Type = ...
let fmap_F : ∀(A : Type) → ∀(B : Type) → (A → B) → F A → F B = ... 
-- Define the type constructor G and its fmap method:
let G : Type → Type = ...
let fmap_G : ∀(A : Type) → ∀(B : Type) → (A → B) → G A → G B = ... 
-- Define the natural transformation t:
let t : ∀(A : Type) → F A → G A = ...
let naturality_law =
  λ(A : Type) → λ(B : Type) → λ(f : A → B) → λ(p : F A) → 
    assert : fmap_G A B f (t A p) === t B (fmap_F A B f p)
```

A naturality law of `t` describes what happens when we apply the transformation `t` to a data container.
We can apply `t` to transform `F A → G A`, followed an `fmap`-based transformation (`G A → G B`).

(An `fmap` function does not change the container's shape or data ordering but only replaces each data item of type `A` separately by another data item of type `B`.)

We can also first apply `fmap_F` to transform `F A → F B` and then apply `t` to transform `F B → G B`.
The final results of type `G B` will be the same.

So, naturality laws describe program refactorings where the programmer decides to change the order of function applications.
(For example, first apply `List/map A B f` and then `List/head B`; or first apply `List/head A` and then `Optional/map A B f`.)
Because the naturality law holds, the results of the program are guaranteed to remain the same after refactoring.

If a natural transformation has several type parameters, there will be a separate naturality law with respect to each of the type parameters.
To write that kind of naturality law, we need to fix all type parameters except one.

As an example, consider the function `List/map` as a natural transformation with respect to the type parameter `A`.
To write the naturality law, we keep `B` fixed and introduce arbitrary types `X`, `Y` and an arbitrary function `f : X → Y`:

TODO

### Fully parametric code

As a motivation, consider a simple function with a type parameter:

```dhall
let f
 : ∀(A : Type) → A → A → A
  = λ(A : Type) → λ(x : A) → λ(y : A) → x
```

Because the type `A` is unknown, the function `f` cannot examine the values `x` and `y` and perform any nontrivial computation with them (cannot even check whether `x == y`).

Neither can the code of `f` examine the type `A` itself and make decisions based on that.
The code of `f` cannot check whether the type parameter `A` is equal to `Natural`, say.
This is so because Dhall does not support comparing types or pattern-matching on type parameters.

Here is an imaginary example of a function that does not work in the same way for all types:

```dhall
let f_strange  -- This cannot work in Dhall.
 : ∀(A : Type) → A → A → A
  = λ(A : Type) → λ(x : A) → λ(y : A) →
    if A == Natural then x else y
```
This function implements a different logic for `A == Natural` as opposed to other types.
This sort of code can be written in a language where types can be examined at run time.
But Dhall does not support such functionality.

For this reason, a Dhall function with a type parameter `A` must work in the same way for all `A`.
This property is known as the "full polymorphic parametricity" of the function's code.
We will call it "full parametricity" for short.

The **parametricity theorem** says that any fully parametric function will automatically satisfy a certain mathematical law.
The form of that law is determined by the type signature of the function.
(So, all functions of that type will satisfy the same law.)

For natural transformations (functions of type `∀(A : Type) → F A → G A`), the corresponding law will be the naturality law.

So, the parametricity theorem guarantees that all Dhall functions of type `∀(A : Type) → F A → G A` are natural transformations obeying the naturality law, as long as the type constructors `F` and `G` are both covariant or both contravariant.

For functions of more complicated type signatures, the parametricity theorem gives a law of a more complicated form than naturality laws.

To see an example of such a law, consider a function with type signature `∀(A : Type) → (F A → G A) → A`, where `F` and `G` are both non-constant and covariant type constructors.
This is not a type signature of a natural transformation because it _cannot_ be rewritten in the form `K A → L A` where `K` and `L` are either both covariant or both contravariant.

For functions `t : ∀(A : Type) → (F A → G A) → A`, the parametricity theorem gives the following law:

For any types `A` and `B`, and for any functions `f : A → B`, `p : F A → G A`, and `q : F B → G B`, such that `p` and `q` are "`f`-compatible", we must have `f (t A p) === t B q`.

Here, we need to define the special property of being "`f`-compatible" as follows: Functions `p` and `q` are "`f`-compatible" if, for any value `x : F A`, we have:

```dhall
fmap_G A B f (p x) === q (fmap_F A B f x)
```
This equation is similar to a naturality law except for using two different functions (`p` and `q`).
It is important to note that this equation defines a _many-to-many relation_ between the functions `p` and `q`.
This equation cannot be used to express `p` through `q` or `q` through `p` via a simple function.

Because of this complication, the law of `t` does not have the form of a single equation.
The law says that the equation `f (t A p) === t B q` holds for any `p` and `q` that are in a certain relation to each other and to `f`.
(We called that relation "`f`-compatible" just for the purposes of this example.)

One may say that the parametricity theorem gives a "relational law" for functions `t`; the form of that law generalizes naturality laws for the complicated type signature of `t`.

To summarize: the parametricity theorem applies to all Dhall values.
For any Dhall type signature that involves type parameters, the parametricity theorem gives a law automatically satisfied by all Dhall values of that type signature.

The form of the law can be written in advance, without knowing the code of the Dhall function, because the law is determined by the type signature alone.

The general formulation and proof of the parametricity theorem are beyond the scope of this book, which focuses on practical applications.
For more details, see ["The Science of Functional Programming" by the same author](https://leanpub.com/sofp) where the parametricity theorem is proved for a fully parametric programs written in a sub-language of Dhall.

### Existential types: `unpack` and then `pack` is identity

In this subsection, we fix an arbitrary type constructor `P : Type → Type` and study values of type `ExistsP`.

By assuming that `P` is always fixed, we may simplify the definitions of `pack` and `unpack`: 

```dhall
let ExistsP = ∀(R : Type) → (∀(T : Type) → P T → R) → R

let unpackP : ExistsP → ∀(R : Type) → (∀(T : Type) → P T → R) → R 
  = λ(ep : ExistsP) → λ(R : Type) → λ(unpack_ : ∀(T : Type) → P T → R) →
      ep R unpack_

let packP : ∀(T : Type) → P T → ExistsP
  = λ(T : Type) → λ(pt : P T) →
      λ(R : Type) → λ(pack_ : ∀(T_ : Type) → P T_ → R) → pack_ T pt
```

Values of type `ExistsP` are built using `packP` and consumed using `unpackP`.

In a certain sense, `packP` and `unpackP` are (one-sided) inverse functions:
We expect that "unpacking" a value `ep : ExistsP` and then "packing" it back will recover the original value `ep`.
We can write this expectation in Dhall as an equation for `ep`:

```dhall
let ep : ExistsP = ...  -- Create any value of type ExistsP. Then:

unpackP ExistsP ep packP === ep
```

Because `unpackP` is little more than an identity function of type `ExistsP → ExistsP`, we can simplify the last equation to just `ep ExistsP packP === ep`.
We would like to prove that the above equation holds for arbitrary `ep : ExistsP`.

To prove that, we need to use the naturality law of `ep`.
([The author is grateful to Dan Doel for assistance with the proof](https://cstheory.stackexchange.com/questions/54124).)

We note that `ExistsP` is the type of a covariant natural transformation with respect to the type parameter `R`.
So, all Dhall values `ep : ExistsP` will satisfy the corresponding naturality law.
The law says that, for any types `R` and `S` and for any functions `f : R → S` and `g : ∀(T : Type) → P T → R`, we will have:

```dhall
f (ep R g) === ep S (λ(T : Type) → λ(pt : P T) → f (g T pt))
```

Both sides of the naturality law apply `ep` to some arguments, while we would like to prove an equation of the form `ep ExistsP packP === ep`.
To make progress, we apply both sides of that equation to arbitrary arguments `U : Type` and `u : ∀(T : Type) → P T → U`.
If `ep packP` is the same function as `ep` then `ep packP U u` will be always the same value as `ep U u`.
Write the corresponding equation:

```dhall
ep ExistsP packP U u === ep U u
```

Our goal is to derive this equation as a consequence of the naturality law of `ep`.
For that, we just need to choose suitable parameters `R`, `S`, `f`, and `g` in that law.
We choose `R = ExistsP`, `S = U`, `f ep = ep U u`, and `g = packP`.
Then the left-hand side of the naturality law becomes:

```dhall
f (ep R g) == ep R g U u = ep ExistsP packP U u
```
This is the left-hand side of the equation we need to prove.

The right-hand side of the naturality law becomes:

```dhall
ep S (λ(T : Type) → λ(pt : P T) → f (g T pt))
  == ep U (λ(T : Type) → λ(pt : P T) → (g T pt) U u)
  == ep U (λ(T : Type) → λ(pt : P T) → packP T pt U u)
```

This will be equal to `ep U u` (the right-hand side of the equation we need to prove) if we could show that:

```dhall
λ(T : Type) → λ(pt : P T) → packP T pt U u  ===  u
```

Substitute the definition of `packP` and get:

```dhall
λ(T : Type) → λ(pt : P T) → packP T pt U u
  = λ(T : Type) → λ(pt : P T) → u T pt
```

Because `u` is a function of type `∀(T : Type) → P T → U`, the code of `u` has the form `λ(T : Type) → λ(pt : P T) → ...`.

So, the function `λ(T : Type) → λ(pt : P T) → u T pt` is the same as just `u`.

(This is a special case of the general fact that the expression `λ(x : A) → f x` is the same function as `f`.)

Finally, we found what we needed:

```dhall
ep U (λ(T : Type) → λ(pt : P T) → packP T pt U u)
  === ep U (λ(T : Type) → λ(pt : P T) → u T pt)
  === ep U u
```

This completes the proof that `ep ExistsP packP U u === ep U u`.

### Equivalence of types for functions of existential type

To simplify the code, we still keep `P` fixed in this section and use the definitions `ExistsP` and `packP` shown before.

We will now show that the functions `inE` and `outE` defined in section "Functions of existential types" are inverses of each other.

The functions `inE` and `outE` are defined by: TODO

To check that the functions `inE R` and `outE R` are inverses of each other (for fixed `P` and `R`), we need to compute the composition of these functions in both directions.
The first direction is when we apply `inE` and then `outE`.
Take an arbitrary `k : ∀(T : Type) → P T → R` and first apply `inE` to it, then `outE`:

```dhall
outE R (inE R k)
  -- Use the definition of `inE`.
  === outE R (λ(ep : ExistsP) → ep R k)
  -- Use the definition of `outE`.
  === λ(T : Type) → λ(pt : P T) → (λ(ep : ExistsP) → ep R k) (packP T)
```

The result is a function of type `λ(T : Type) → λ(pt : P T) → R`.
We need to show that this function is equal to `k`.
To do that, apply that function to arbitrary values `T : Type` and `pt : P T`.
The result should be equal to `k T pt`:

```dhall
outE R (inE R k) t pt
  === (λ(ep : ExistsP) → ep R k) (packP T)
  === (packP T) R k
  -- Use the definition of `packP`.
  === (λ(R : Type) → λ(pack_ : ∀(T_ : Type) → P T_ → R) → pack_ T pt) R k
  === k t pt
```

This proves the first direction of the isomorphism.

The other direction is when we apply `outE` and then `inE`.
Take an arbitrary value `consume : ExistsP → R` and first apply `outE` to it, then `inE`:

```dhall
inE R (outE R consume)
  === inE R (λ(T : Type) → λ(pt : P T) → consume (packP T))
  === λ(ep : ExistsP) → ep R (λ(T : Type) → λ(pt : P T) → consume (packP T))
```

The result is a function of type `ExistsP → R`.
We need to show that this function is equal to `consume`.

Apply that function to an arbitrary value `ep : ExistsP`:

```dhall
inE R (outE R consume) ep
  === ep R (λ(T : Type) → λ(pt : P T) → consume (packP T))
```

We need to show that the last result is equal to `consume ep`.
For that, we will use the naturality law of `ep`.


TODO
