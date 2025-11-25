# Programming in System Fω using Dhall

## Preface

This book is an advanced-level tutorial on [Dhall](https://dhall-lang.org) for software engineers already familiar with the functional programming (FP) paradigm,
as practiced in languages such as OCaml, Haskell, Scala, and others.

Dhall is positioned as an open-source language for programmable configuration files.
Its primary design goal is to replace various templating languages for JSON, YAML, and other configuration formats, providing a powerful and safe programming language.
The ["Design choices" document](https://docs.dhall-lang.org/discussions/Design-choices.html) discusses some other considerations behind the design of Dhall.

This book views Dhall as:
- a powerful templating system for flexible and strictly validated configuration files in JSON, YAML, and other text-based formats;
- a formally specified and tested interpreter for a small purely functional programming language, useful for studying various language-independent aspects of advanced functional programming;
- a high-level scripting DSL for interfacing with a custom runtime that may implement side effects and other low-level details.

The book focuses on the last two use cases.

Although most of the code examples are in Dhall, much of the material of the book has a wider applicability.
The book studies a certain flavor of purely functional programming without side effects and with guaranteed termination,
which is known in the academic literature as "System Fω".
That type system is the foundation of Haskell, Scala, and other advanced functional programming languages.

From the point of view of programming language theory, Dhall implements System Fω with some additional features, using a Haskell-like syntax.

For a more theoretical introduction to various forms of typed lambda calculus, System F, and System Fω, see:

- [D. Rémy. Functional programming and type systems.](https://gallium.inria.fr/~remy/mpri/)
- [Lectures on Advanced Functional Programming, Cambridge, 2014-2015](https://www.cl.cam.ac.uk/teaching/1415/L28/materials.html), in particular the [notes on lambda calculus.](https://www.cl.cam.ac.uk/teaching/1415/L28/lambda.pdf)

Most of that theory is beyond the scope of this book, which is focused on issues arising in practical programming.
The book contains many code examples, which have been validated automatically by the Dhall interpreter.

The Appendix of the book contains some theoretical material that proves the correctness of certain code constructions, notably the Church encodings of fixpoint types and the parametricity properties of existential types.

_The text of this book was written and edited without using any LLMs._

## Overview of Dhall

This book corresponds to the [Dhall standard 23.0.0](https://github.com/dhall-lang/dhall-lang/releases/tag/v23.0.0) and Dhall version `1.42.2`.

Dhall is a small, purely functional language.
It will be easy to learn Dhall for readers already familiar with functional programming.

The syntax of Dhall is similar to that of Haskell.
One major difference is Dhall's syntax for functions, which resembles the notation of System F and System Fω.
Namely, System F's notation $ \Lambda t. ~ \lambda (x:t). ~ f ~ t~ x $ and System Fω's notation
$ \lambda (t:*). ~ \lambda (x:t).~ f~ t~ x $ correspond to the Dhall syntax `λ(t : Type) → λ(x : t) → f t x`.

Here is an example of a complete Dhall program:

```dhall
let f = λ(x : Natural) → λ(y : Natural) → x + y + 2
let id = λ(A : Type) → λ(x : A) → x
in f 10 (id Natural 20)
  -- This is a complete program; it evaluates to 32 of type Natural.
```

See the [Dhall cheat sheet](https://docs.dhall-lang.org/howtos/Cheatsheet.html) for more examples of basic Dhall usage.

The [Dhall standard prelude](https://prelude.dhall-lang.org/) defines a number of general-purpose functions
such as `Natural/lessThan` or `List/map`.

### Identifiers and variables

Dhall variables are immutable constant values with names, introduced via the "`let`" syntax.
We will call them "variables", even though they stand for constants that cannot vary.

For example, `let x = 1 in ...` defines the variable `x` that can be used in the code that follows.
Names of variables are arbitrary identifiers, like in most programming languages.

Identifiers in Dhall may contain dash and slash characters.
Examples of valid identifiers are `List/map` and `start-here`.

The slash character is often used in Dhall's standard library, providing suggestive function names such as `List/map`, `Optional/map`, etc.
However, Dhall does not treat those names specially.
It is not required that functions working with `List` should have names such as `List/map` or `List/length`.

Identifiers with dashes can be used, for example, as record field names, as it is sometimes needed in configuration files:

```dhall
⊢ { first-name = "John", last-name = "Reynolds" }

{ first-name = "John", last-name = "Reynolds" }
```
However, identifiers may not _start_ with a dash or a slash character.

Identifiers may contain arbitrary characters (even keywords or whitespace) if escaped in backquotes.

```dhall
⊢ let `: a b c` = 1 in 2 + `: a b c`

3

⊢ let `forall` = 3 in `forall`

3
```

The standalone underscore character (`_`) is used in Haskell, Scala, other languages as syntax for a special "unused" variable.
But in Dhall, the symbol `_` is a variable like any other:

```dhall
⊢ let _ = 123 in _ + _

246
```

Of course, one might still use the symbol `_` in Dhall code as a convention for unused variables.
However, the Dhall interpreter will not treat the variable `_` in any special way and will not verify that the variable `_` actually remains unused.

### Primitive types

Integers must have a sign: for example, `+1` or `-1` have type `Integer`.
The integer values `-0` and `+0` are the same.

`Natural` numbers must have no sign (for example, `123`).

Values of types `Natural` and `Integer` have unbounded size.
There is no overflow.
Dhall does not have built-in 32-bit or 64-bit integers with overflow, as it is common in other programming languages.

Dhall supports other numerical types, such as `Double` or `Time`, but there is little one can do with values of those types (other than print them).
For instance, Dhall does not directly support floating-point arithmetic on `Double` values, or arithmetic operations with dates and times.

Strings have type `Text` and support interpolation:

```dhall
⊢ let x = 123 in "The answer is ${Natural/show x}."

"The answer is 123."
```

Strings also support search/replace and concatenation, but no other operations.
For instance, it is currently not possible in Dhall to compare strings lexicographically, or even to compute the length of a string.

These restrictions are intentional and are designed to keep users from writing Dhall programs whose behavior is based on analyzing string values, such as parsing external input or creating "stringly-typed" data structures.

Nevertheless, it is possible in Dhall to "assert" certain conditions that string values should satisfy.
For example, one can assert that a string value should be non-empty, or begin with certain characters, or consist entirely of alphanumeric characters.
These and other similar conditions may be enforced statically via the "assert" feature that we will discuss later.
A Dhall program will fail to compile if an asserted condition does not hold.

### Functions and function types

An example of a function in Dhall is:

```dhall
let inc = λ(x : Natural) → x + 1
```
Dhall does not support Haskell's concise function definition syntax such as  `inc x = x + 1`, where arguments are given on the left-hand side and types are inferred automatically.
Functions must be defined via `λ` symbols as we have just seen.

Generally, functions in Dhall look like `λ(x : input_type) → body`, where:

- `x` is a bound variable representing the function's input argument,
- `input_type` is the type of the function's input argument, and
- `body` is an expression that may use `x`; this expression computes the output value of the function.

A function's type has the form `∀(x : input_type) → output_type`, where:

- `input_type` is the type of the function's input value, and
- `output_type` is the type of the function's output value.

The function `inc` shown above has type `∀(x : Natural) → Natural`.

The Unicode symbols `∀`, `λ`, and `→` may be used interchangeably with their equivalent ASCII representations: `forall`, `\`, and `->`.
In this book, we will use the Unicode symbols for brevity.

Usually, the function body is an expression that uses the bound variable `x`.
However, the type expression `output_type` might itself also depend on the input _value_ `x`.
We will discuss such functions in more detail later.
In many cases, the output type does not depend on `x`.
Then the function's type may be written in a simpler form: `input_type → output_type`.

For example, the function `inc` shown above may be written with a type annotation like this:

```dhall
let inc : Natural → Natural = λ(x : Natural) → x + 1
```

We may also write a fully detailed type annotation if we like:

```dhall
let inc : ∀(x : Natural) → Natural = λ(x : Natural) → x + 1
```

It is not required that the name `x` in the type annotation (`∀(x : Natural)`) should be the same as the name `x` in the function (`λ(x : Natural)`).
So, the following code is just as valid (although may be confusing):

```dhall
let inc : ∀(a : Natural) → Natural = λ(b : Natural) → b + 1
```

#### Curried functions

All Dhall functions have just one argument.
To implement functions with more than one argument, one can use either curried functions or record types (see below).

For example, a function that adds 3 numbers can be written in different ways according to convenience:

```dhall
let add3_curried : Natural → Natural → Natural → Natural
  = λ(x : Natural) → λ(y : Natural) → λ(z : Natural) → x + y + z

let add3_record : { x : Natural, y : Natural, z : Natural } → Natural
  = λ(record : { x : Natural, y : Natural, z : Natural }) →
    record.x + record.y + record.z
```

Most functions in the Dhall standard library are curried.
Currying allows function argument types to depend on some of the previous curried arguments.

#### Functions at type level

Types in Dhall are treated somewhat similar to values of type `Type`.
For example, the symbol `Natural` is considered to have type `Type`:

```dhall
⊢ Natural

Natural : Type
```

Because of this, we can define functions whose input is a type (rather than a value).
The output of a function can also be a type, or again a function having types as inputs and/or outputs, and so on.

We will discuss functions of types in more detail later in this chapter.
For now, we note that an argument's type may depend on a previous curried argument, which can be itself a type.
This allows Dhall to implement a rich set of type-level features:

- Functions with type parameters: for example, `λ(A : Type) → λ(x : A) → ...`
- Type constructors, via functions of type `Type → Type` (both the input and the output is a type).
- Type constructor parameters: for example, `λ(F : Type → Type) → λ(A : Type) → λ(x : F A) → ...`
- Dependent types: functions whose inputs are values and outputs are types.


#### Shadowing and the syntax for de Bruijn indices

De Bruijn indices are numbers that disambiguate shadowed variables in function bodies.

Consider a curried function that shadows a variable:

```dhall
let _ = λ(x : Natural) → λ(x : Natural) → 123 + x
```
The inner function shadows the outer `x`; inside the inner function body, `123 + x` refers to the inner `x`.
The outer `x` is a **shadowed variable**.

In most programming languages, one cannot directly refer to shadowed variables.
Instead, one needs to rename the inner `x` to `t`:

```dhall
let _ = λ(x : Natural) → λ(t : Natural) → 123 + t
```
Now the function body could (if necessary) refer to the outer `x`.

What if we do not want (or cannot) rename `x` to `t`
but still want to refer to the outer `x`? In Dhall, we write `x@1` for that:

```dhall
let _ = λ(x : Natural) → λ(x : Natural) → 123 + x@1
```
The `1` in `x@1` is called the **de Bruijn index** of the outer variable `x`.

De Bruijn indices are non-negative integers.
We usually do not write `x@0`, we write just `x` instead.

Each de Bruijn index points to a specific nested lambda in some outer scope for a variable with a given name.
For example, look at this code:

```dhall
let _ = λ(x : Natural) → λ(t : Natural) → λ(x : Natural) → 123 + x@1
```
The variable `x@1` still points to the outer `x`. The presence of another lambda with the argument `t` does not matter for counting the nesting depth for `x`.

It is invalid to use a de Bruijn index that is greater than the total number of nested lambdas.
For example, these expressions are invalid at the top level (as there cannot be any outer scope):
```dhall
λ(x : Natural) → 123 + x@1  -- Where is x@1 ???

λ(x : Natural) → λ(x : Natural) → 123 + x@2  -- Where is x@2 ???
```
At the top level, these expressions are just as invalid as the expression `123 + x` because `x` remains undefined.

The variable `x` in the expression `123 + x` is considered a "free variable", meaning that it should have been defined in the outer scope.

Similarly, `x@1` in `λ(x : Natural) → 123 + x@1` is a free variable.

At the top level, all variables must be bound. One cannot evaluate expressions with free variables at the top level.
Expressions with free variables must be within bodies of some functions that bind their free variables.

In principle, nonzero de Bruijn indices could be always eliminated by renaming some bound variables.
The advantage of using de Bruijn indices is that we can refer to every variable in every scope without need to rename any variables.

To illustrate the usage of de Bruijn indices, consider how we would proceed with a symbolic evaluation of a function applied to an argument.

When a function of the form `λ(x...) → ...` is applied to an argument, we will need to substitute that `x`.
It turns out that we may need to change some de Bruijn indices in the function body.

As an example, suppose we would like to evaluate a function applied to an argument in this code:

```dhall
( λ(x : Natural) → λ(y : Natural) → λ(x : Natural) → x + x@1 + x@2 ) y  -- Symbolic derivation.
```
This expression has free variables `y` and `x@2`, so it can occur only under some functions that bind `x` and `y`.
For the purposes of this example, let us define `function1` that contains the above code as its function body:

```dhall
let function1 = λ(x : Natural) → λ(y : Natural) →
  ( λ(x : Natural) → λ(y : Natural) → λ(x : Natural) → x + x@1 + x@2 ) y
```

Suppose we need to evaluate this expression (that is, evaluate "under a lambda").
In other words, we need to simplify the body of `function1` before applying that function.

To evaluate this expression correctly, we cannot simply substitute `y` instead of `x` in the body of the function. Note that:

- The outer `x` corresponds to `x@1` within the expression, so we need to substitute `y` instead of `x@1` while keeping `x` and `x@2` unchanged.
- Another `y` is already bound in an inner scope; so, we need to write `y@1` instead of `y`, in order to refer to the free variable `y` in the outside scope.
- When we remove the outer `x`, the free variable `x@2` will need to become `x@1`.

So, the result of evaluating the above expression must be:

```dhall
λ(y : Natural) → λ(x : Natural) → x + y@1 + x@1  -- Symbolic derivation.
```

We can verify that Dhall actually performs this evaluation:

```dhall
⊢ λ(x : Natural) → λ(y : Natural) → ( λ(x : Natural) → λ(y : Natural) → λ(x : Natural) → x + x@1 + x@2 ) y

λ(x : Natural) →
λ(y : Natural) →
λ(y : Natural) →
λ(x : Natural) →
  x + y@1 + x@1
```

### Product types (records)

Product types are implemented via records.
For example, `{ x = 123, y = True }` is a "record value" whose type is `{ x : Natural, y : Bool }`.
Types of that form are called "record types".

There are no built-in tuple types, such as Haskell's and Scala's `(Int, String)`.
Records with field names must be used instead.
For instance, the (Haskell / Scala) tuple type `(Int, String)` may be translated into Dhall as the following record type: `{ _1 : Integer, _2 : Text }`.
That record type has two fields named `_1` and `_2`.
The two parts of the tuple may be accessed via those names and the "dot" operator:

```dhall
⊢ :let tuple = { _1 = +123, _2 = "abc" }

tuple : { _1 : Integer, _2 : Text }

⊢ tuple._1

+123
```

Records can be nested: the record value `{ x = 1, y = { z = True, t = "abc" } }` has type `{ x : Natural, y : { z : Bool, t : Text } }`.
Fields of nested record types may be accessed via the "dot" operator:

```dhall
⊢ :let a = { x = 1, y = { z = True, t = "abc" } }

a : { x : Natural, y : { t : Text, z : Bool } }

⊢ a.y.t

"abc"
```

It is important that Dhall's records have **structural typing**: two record types are distinguished only via their field names and types, while record fields are unordered.
So, the record types `{ x : Natural, y : Bool }` and `{ y : Bool, x : Natural }` are the same, while the types `{ x : Natural, y : Bool }` and `{ x : Text, y : Natural }` are different and unrelated to each other.
There is no **nominal typing**: that is, no way of assigning a permanent unique name to a certain record type, as it is done in Haskell, Scala, and other languages in order to distinguish one record type from another.

For convenience, a Dhall program may define local names for types:

```dhall
let RecordType1 = { a : Natural, b : Bool }
let x : RecordType1 = { a = 1, b = True }
let RecordType2 = { b : Bool, a : Natural }
let y : RecordType2 = { a = 2, b = False }
```
But the names `RecordType1` and `RecordType2` are no more than (locally defined) values that are used as type aliases.
Dhall does not distinguish `RecordType1` and `RecordType2` from each other or from the literal type expression `{ a : Natural, b : Bool }`,
as the order of record fields is not significant.
So, the values `x` and `y` actually have the same type in this code.

It will be convenient to define a `Pair` type constructor:
```dhall
let Pair = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }
```

### Co-product types ("union types")

Co-product types (called "union types" in Dhall) are implemented via tagged unions, for example: `< X : Natural | Y : Bool >`.
Here `X` and `Y` are called the **constructors** of the given union type.

Values of union types are created via constructor functions.
Constructor functions are written using the "dot" operator.
For example, the Dhall expression `< X : Natural | Y : Bool >.X` is a function of type `Natural → < X : Natural | Y : Bool >`.
Applying that function to a value of type `Natural` will create a value of the union type `< X : Natural | Y : Bool >`, as shown in this example:

```dhall
let x : < X : Natural | Y : Bool > = < X : Natural | Y : Bool >.X 123
```

Constructor names are often capitalized (`X`, `Y`, etc.), but Dhall does not enforce that convention.

Constructors may have _at most one_ argument.
Constructors with multiple curried arguments (as in Haskell: `P1 Int Int | P2 Bool`) are _not_ supported in Dhall.
Record types must be used instead of multiple arguments.
For example, Haskell's union type `P1 Int Int | P2 Bool` may be replaced by Dhall's union type `< P1 : { _1 : Integer, _2 : Integer } | P2 : Bool >`.

Union types can have empty constructors (that is, constructors without arguments).
For example, the union type `< X : Natural | Y >` has an empty constructor (`Y`).
The corresponding value is written as `< X : Natural | Y >.Y`.
This is the only value that can be created via that constructor.

Union types can be nested, for example, `< T | X : < Y | Z : Natural > >`.
Here is an (artificial) example of creating a value of that type:

```dhall
let nested = < T | X : < Y | Z : Natural > >.X (< Y | Z : Natural >.Z 123)
```

It is important that Dhall's union types use **structural typing**: two union types are distinguished only via their constructor names and types, while constructors are unordered.
So, the union types `< X : Natural | Y >` and `< Y | X : Natural >` are the same, while the types `< X : Natural | Y >` and `< X : Text | Y : Natural >` are different and unrelated to each other.
There is no **nominal typing** for union types; that is, no way of assigning a permanent unique name to a certain union type, as it is done in Haskell, Scala, and other languages to distinguish one union type from another.

For convenience, Dhall programs often define local names for union types:

```dhall
let MyXY = < X : Natural | Y : Bool >
let x : MyXY = MyXY.X 123
```
The constructor expression `MyXY.X` is a function of type `Bool → MyXY`.
But the name `MyXY` is no more than a (locally defined) value that is used as a type alias.
Dhall considers `MyXY` to be the same type as the literal type expressions `< X : Natural | Y : Bool >` and `< Y : Bool | X : Natural >`,
as the order of a union type's constructors is not significant.

Dhall requires the union type's constructors to be explicitly annotated by the full union types.
In Haskell or Scala, one may simply write `Left(x)` and `Right(y)` and the compiler will automatically find the relevant union type.
But Dhall requires us to write `< Left : Text | Right : Bool >.Left x` or `< Left : Text | Right : Bool >.Right y`, fully specifying the union type whose value is being constructed.

An advantage of this syntax is that there is no need to keep the constructor names unique across all union types in scope (as it is necessary in Haskell and Scala).
In Dhall, each union type may define arbitrary constructor names.
For example, consider this code:

```dhall
let Union1 = < Left : Text | Right >
let Union2 = < Left : Text | Right : Bool >
let u : Union1 = Union1.Left "abc"
let v : Union2 = Union2.Left "fgh"
let x : Union1 = Union1.Right
let y : Union2 = Union2.Right True
```

The types `Union1` and `Union2` are different because the constructors named `Right` require different data types within `Union1` and `Union2`.
Constructor names are always written together with the union type.
So, there is no conflict between the constructors `Union1.Left` and `Union2.Left`, or between `Union1.Right` and `Union2.Right`.
(A conflict would occur if we could write simply `Left` and `Right` for those constructors, but Dhall does not support that.)

It will be convenient to define an `Either` type constructor:
```dhall
let Either = λ(a : Type) → λ(b : Type) → < Left : a | Right : b >
```

### Pattern matching

Pattern matching for union types is implemented via the `merge` keyword.
Dhall's `merge` expressions are similar to `match/with` expressions in OCaml, `case/of` expressions in Haskell, and `match/case` expressions in Scala.
One difference is that each case of a `merge` expression must specify an explicit function with a full type annotation.

As an example, consider a union type defined in Haskell by:

```haskell
data P = X Int | Y Bool | Z    -- Haskell.
```
A function `toString` that prints values of type `P` can be written in Haskell via pattern matching:

```haskell
-- Haskell
toString :: P -> String
toString x = case x of
  X x -> "X " ++ show x
  Y y -> "Y " ++ show y
  Z -> "Z"
```
The corresponding type is defined in Dhall by:

```dhall
let P = < X : Integer | Y : Bool | Z >
```

Here is the Dhall code for a function that prints values of type `P`:

```dhall
let Bool/show = https://prelude.dhall-lang.org/Bool/show
let pToText : P → Text = λ(x : P) →
  merge {
          X = λ(x : Integer) → "X " ++ Integer/show x,
          Y = λ(y : Bool) → "Y " ++ Bool/show y,
          Z = "Z",
        } x
```

The `merge` keyword works like a curried function whose first argument is a _record value_ and the second argument is a value of a union type.
The field names of the record must correspond to all the constructor names in the union type.
The values inside the record are functions describing what to compute in each case where the union type's constructor has arguments.
For no-argument constructors (e.g., for the constructor `Z` in the example shown above) the value inside the record does not need to be a function.

The second argument of `merge` is a value of a union type on which the pattern matching is being done.

Note that `merge` in Dhall is a special keyword, not a function, although its syntax (e.g., `merge { X = 0 } x`) looks like that of a curried function with two arguments.
It is a syntax error to write `merge { X = 0 }` without specifying a value (`x`) of a union type.


### The void type

The **void type** is a type that cannot have any values.

Dhall's empty union type (denoted by `<>`) is an example of a void type.
Values of union types may be created only via constructors, but the union type `<>` has no constructors.
So, no Dhall code will ever be able to create a value of type `<>`.

If a value of the void type existed, one would be able to compute from it a value of _any other type_.
Although this may appear absurd, it is indeed an important property of the void type.
This property can be expressed formally via the function that is often denoted by `absurd`.
That function computes a value of an arbitrary type `A` given a value of the void type `<>`:

```dhall
let absurd : <> → ∀(A : Type) → A
  = λ(x : <>) → λ(A : Type) → merge {=} x : A
```

This implementation depends on a special feature of Dhall: a `merge` expression with a type annotation.
(The annotation `: A` in `merge {=} x : A` is for the entire `merge` expression, not for `x`.)
This annotation makes the type checker accept an _empty_ `merge` expression as a value of type `A`.

We need to keep in mind that the function `absurd` can never be actually applied to an argument value in any program, because one cannot construct any values of type `<>`.
Nevertheless, the existence of the void type and a function of type `<> → ∀(A : Type) → A` is useful in some situations, as we will see below.

The type signature of `absurd` suggests a type equivalence between `<>` and the function type `∀(A : Type) → A`.

Indeed, the type `∀(A : Type) → A` is void.
If we could have some expression `x` of that type, we would have then apply `x` to the void type and compute a value `x <>` of type `<>`.
But that is impossible, as the type `<>` has no values.

So, the type `∀(A : Type) → A` can be used equally well to denote the void type.

One use case for the void type is to provide a "TODO" functionality.
While writing Dhall code, we may want to leave a certain value temporarily unimplemented.
However, we still need to satisfy Dhall's type checker and provide a value that appears to have the right type.

To achieve that, we rewrite our code as a function with an additional argument of the void type:

```dhall
let our_program = λ(void : <>) → True
```

Now suppose we need a value `x` of some type `X` in our code, but we do not yet know how to implement that value.
We write `let x : X = absurd void X` in the body of `our_program`:

```dhall
let our_program = λ(void : <>) →
  let x : Integer = absurd void Integer
  in { x }    -- Whatever.
```
The typechecker will accept this code.
Of course, we can never supply a value for the `void : <>` argument.
So, our program cannot be evaluated until we replace the `absurd void X` by correct code computing a value of type `X`.

To shorten the code, define `let TODO = absurd void`.
We can then write `TODO X` and pretend to obtain a value of any type `X`.

Note that the partially applied function `absurd void` is a value of type `∀(A : Type) → A`.
So, we may directly require `TODO` as an argument of type `∀(A : Type) → A` in our program:

```dhall
let our_program = λ(TODO : ∀(A : Type) → A) →
  let x = TODO Natural in { result = x + 123 }     -- Whatever.
```
We will be able to evaluate `our_program` only after replacing all `TODO` placeholders with suitable code.

### The unit type

A **unit type** is a type that has only one distinct value.

Dhall's empty record type `{}` is a unit type.
The type `{}` has only one value, written as `{=}`.
This syntax denotes a record with no fields or values.

Another way of defining a unit type is via a union type with a single constructor, for example: `< One >` (or with any other name instead of "One").
The type `< One >` has a single distinct value, denoted in Dhall by `< One >.One`.
In this way, one can define unit types with different names, when convenient.

Another equivalent definition of a unit type is via the following function type:
```dhall
let UnitId = ∀(A : Type) → A → A
```
The only possible function of that type is the **polymorphic identity function**:
```dhall
let identity : UnitId = λ(A : Type) → λ(x : A) → x
```
There is no other, inequivalent Dhall code that could implement a different function of that type.
(This is a consequence of parametricity.)
So, the type `UnitId` is a unit type because it has only one distinct value.

The unit type is denoted by `()` in Haskell, by `Unit` in Scala, by `unit` in OCaml, and [similarly in other languages](https://en.wikipedia.org/wiki/Unit_type).

### Type constructors

Type constructors in Dhall are written as functions from `Type` to `Type`.

In Haskell, one could define a type constructor as `type AAInt a = (a, a, Int)`.
The analogous type constructor in Scala looks like `type AAint[A] = (A, A, Int)`.
To encode this type constructor in Dhall, one writes an explicit function taking a parameter `a` of type `Type` and returning another type.

Because Dhall does not have nameless tuples, we will use a record with field names `_1`, `_2`, and `_3` to represent a tuple with three parts:

```dhall
let AAInt = λ(a : Type) → { _1 : a, _2 : a, _3 : Integer }
```
Then `AAInt` is a function that takes an arbitrary type (`a`) as its argument.
The output of the function is the record type `{ _1 : a, _2 : a, _3 : Integer }`.

The type of `AAInt` itself is `Type → Type`.
For more clarity, we may write that as a type annotation:

```dhall
let AAInt : Type → Type = λ(a : Type) → { _1 : a, _2 : a, _3 : Integer }
```

Type constructors involving more than one type parameter are usually written as curried functions.
Here is an example of defining a type constructor similar to Haskell's and Scala's `Either`:

```dhall
let Either = λ(a : Type) → λ(b : Type) → < Left : a | Right : b >
```

Because Dhall does not have built-in tuple types, it is convenient to define a `Pair` type constructor:

```dhall
let Pair = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }
```

The types of `Either` and `Pair` is `Type → Type → Type`.

As with all Dhall types, type constructor names such as `AAInt`, `Either`, or `Pair` are just type aliases.
Dhall distinguishes types and type constructors not by assigned names but by the type expressions themselves (**structural typing**).

### The "Optional" type constructor

An `Optional` type (similar to Haskell's `Maybe` and Scala's `Option`) could be defined in Dhall like this:

```dhall
let MyOptional = λ(a : Type) → < MyNone | MySome : a >
let x : MyOptional Natural = (MyOptional Natural).MySome 123
let y : MyOptional Text = (MyOptional Text).MyNone
```

The built-in `Optional` type constructor is a less verbose equivalent of this code.
One writes `None Text` instead of `(MyOptional Text).MyNone`, and `Some 123` instead  of `(MyOptional Natural).MySome 123`.
(The type parameter `Natural` is determined automatically by Dhall.)
Other than that, the built-in `Optional` type behaves as if it were a union type with constructor names `None` and `Some`.

Here is an example of using Dhall's `merge` for implementing a `getOrElse` function for `Optional` types:

```dhall
let getOrElse : ∀(a : Type) → Optional a → a → a
  = λ(a : Type) → λ(oa : Optional a) → λ(default : a) →
    merge {
            None = default,
            Some = λ(x : a) → x
          } oa
```

The `Optional` type is a built-in Dhall type rather than a library-defined type for pragmatic reasons.
First, a built-in type is integrated with the typechecker and supports more concise code (`Some 123` instead of `(Optional Natural).Some 123`).
Second, the `Optional` type plays a special role when exporting data to JSON and YAML formats: record fields with `None` values are typically omitted from the generated configuration files.

### Functions with type parameters

Functions with type parameters (also known as **generic functions**) are written as functions with extra arguments of type `Type`.

To see how this works, first consider a function that takes a pair of `Natural` numbers and swaps the order of numbers in the pair.
We use a record type `{ _1 : Natural, _2 : Natural }` to represent a pair of `Natural` numbers.
For brevity, we will define a type alias (`PairNatNat`) to denote that type:

```dhall
let PairNatNat : Type = { _1 : Natural, _2 : Natural }
let swapNatNat : PairNatNat → PairNatNat = λ(p : PairNatNat) → { _1 = p._2, _2 = p._1 }
```

Note that the code of `swapNatNat` does not depend on having values of type `Natural`.
The same logic would work with any two types.
So, we can generalize `swapNatNat` to a function `swap` that supports arbitrary types of values in the pair.
The two types will become type parameters; we will denote them by `a` and `b`.
The input type of `swap` will be `{ _1 : a, _2 : b }` (which is the same as `Pair a b `) instead of `{ _1 : Natural, _2 : Natural }`, and the output type will be `{ _1 : b, _2 : a }` (which is the same as `Pair b a`).
The type parameters are given as additional curried arguments of `swap`.
The new code is:

```dhall
let swap : ∀(a : Type) → ∀(b : Type) → Pair a b → Pair b a
  = λ(a : Type) → λ(b : Type) → λ(p : Pair a b) → { _1 = p._2, _2 = p._1 }
```
Note that some parts of the type signature of `swap` depend on the type parameters (namely, the types `Pair a b` and `Pair b a`).
To be able to express that dependence, we need to specify the type parameter names in the type signature of `swap` as `∀(a : Type)` and `∀(b : Type)`.

Compare this with the type signature of `Pair`, which is written as `Type → Type → Type`.
We could write, if we like, `Pair : ∀(a : Type) → ∀(b : Type) → Type`. That would be the same type signature in a longer syntax.
But we cannot shorten the type signature of `swap` to `Type → Type → Pair a b → Pair b a`, because the names `a` and `b` would have become undefined.
The type signature of `swap` requires a longer form that introduces the names `a` and `b`.

As another example, consider functions that extract the first or the second element of a pair.
These functions also work in the same way for all types.
So, it is useful to declare those functions as "generic functions" having type parameters:

```dhall
let take_1 : ∀(a : Type) → ∀(b : Type) → Pair a b → a
  = λ(a : Type) → λ(b : Type) → λ(p : Pair a b) → p._1
let take_2 : ∀(a : Type) → ∀(b : Type) → Pair a b → b
  = λ(a : Type) → λ(b : Type) → λ(p : Pair a b) → p._2
```

A further example is the standard `map` function for `List`.
The type signature of that function is `∀(a : Type) → ∀(b : Type) → (a → b) → List a → List b`.

When applying that function, the code must specify both type parameters (`a`, `b`):

```dhall
let List/map = https://prelude.dhall-lang.org/List/map
in List/map Natural Natural (λ(x : Natural) → x + 1) [1, 2, 3]
  -- This is a complete program that returns [2, 3, 4].
```

A **polymorphic identity function** is written (with a full type annotation) as:

```dhall
let identity : ∀(A : Type) → ∀(x : A) → A
  = λ(A : Type) → λ(x : A) → x
```
The type of the polymorphic identity function is of the form `∀(x : arg_t) → res_t` if we set `x = A`, `arg_t = Type`, and `res_t = ∀(x : A) → A`.
Note that `res_t` is again a function type, and this time its result type (`A`) does not depend on the value of the argument (`x`).
So, this type can be rewritten in the short form as `A → A`.
We will usually write the identity function as:

```dhall
let identity : ∀(A : Type) → A → A
  = λ(A : Type) → λ(x : A) → x
```

In Dhall, all function arguments (including all type parameters) must be introduced explicitly via the `λ` syntax.
Each argument must have a type annotation, for example: `λ(x : Natural)`, `λ(a : Type)`, and so on.

However, a `let` binding does not require a type annotation.
So, one may just write `let Pair = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }`.
The type of `Pair` will be determined automatically.

For complicated type signatures, it still helps to write type annotations, because Dhall will then detect some type errors earlier.

### Modules and imports

Dhall has a simple file-based module system.
The module system is built on the principle that each Dhall file must contain a valid program that is evaluated to a _single_ result value.
(Programs are often written in the form `let x = ... let y = ... in ...`, but the result is still a single value.)

A file's value may be imported into another Dhall file by specifying the path to the first Dhall file.
The second Dhall file can then directly use that value as a sub-expression in any further code.
For convenience, the imported value is usually assigned to a variable with a meaningful name.

In this way, each Dhall file is seen as a "module" that exports a single value.
There are no special keywords to denote the exported value; there is only one such value in any case.

Here is an example: the first file contains a list of numbers, and the second file computes the sum of those numbers.

```dhall
-- This file is `./first.dhall`.
[1, 2, 3, 4]
```

```dhall
-- This file is `./sum.dhall`.
let input_list = ./first.dhall  -- Import from a file at a relative path.
let sum = https://prelude.dhall-lang.org/Natural/sum  -- Import from URL.
in sum input_list
```

Running `dhall` on the second file will compute and show the result:

```bash
$ dhall --file ./sum.dhall
10
```

Although each Dhall module exports only one value, that value may be a record with many fields.
Record fields may contain values and/or types.
In that way, Dhall modules may export a number of values and/or types:

```dhall
-- This file is `./SimpleModule.dhall`.
let UserName = Text
let UserId = Natural
let printUser = λ(name : UserName) → λ(id : UserId) → "User: ${name}[${Natural/show id}]"

let validate : Bool = ./NeedToValidate.dhall -- Import that value from another module.
let test = assert : validate ≡ True   -- Cannot import this module unless `validate` is `True`.

in {
  UserName,
  UserId,
  printUser,
}
```
When this Dhall file is evaluated, the result is a record of type `{ UserName : Type, UserId : Type, printUser : Text → Natural → Text }`.
We could say that this module exports two types (`UserName`, `UserId`) and a function `printUser`.
But technically it just exports a single value (a record).
Exporting a record is a common technique in Dhall libraries.

This module may be imported in another Dhall program like this:

```dhall
let S = ./SimpleModule.dhall -- Just call it `S` for short.
let name : S.UserName = "first_user"
let id : S.UserId = 1001
let printed : Text = S.printUser name id
-- Continue writing code.
```
Here we used the types and the values exported from `SimpleModule.dhall`.
This code will not compile unless all types match, including the imported values.

All fields of a Dhall record are always public.
To make values in a Dhall module private, we simply do not include those values into the final exported record.
Local values declared using `let x = ...` inside a Dhall module will not be exported (unless they are included in the final exported record).

In the example just shown, the file `SimpleModule.dhall` defines the local values `test` and `validate`.
Those values are typechecked and computed inside the module, but are not exported.
In this way, sanity checks or unit tests included within a module will be validated but will remain invisible to other modules.


Other than importing values from files, Dhall supports importing values from Web URLs and from environment variables.
Here is an example of importing the Dhall list value `[1, 1, 1]` from an environment variable called `XS`:

```bash
$ echo "let xs = env:XS in List/length Natural xs" | XS="[1, 1, 1]" dhall
3
```
In this way, Dhall programs may perform computations with external inputs.

However, most often the imported Dhall values are not simple data but records containing types, values, and functions.

Dhall denotes imports via special syntax:
- If a Dhall value begins with `/`, it is an import of a file from an absolute path.
- If a Dhall value begins with `./`, it is an import of a file from a relative path (relative to the directory containing the currently evaluated Dhall file).
- If a Dhall value begins with `http://` or `https://`, it is an import from a Web URL.
- A Dhall value of the form `env:XYZ` is an import from a shell environment variable `XYZ` (in Bash, this would be `$XYZ`). It is important to use no spaces around the `:` character, because `env : XYZ` means a value `env` of type `XYZ`.

It is important that the import paths, environment variable names, and SHA256 hash values are _not strings_.
They are hard-coded and cannot be manipulated at run time.

It is not possible to import a file whose name is computed by concatenating some strings.
For instance, one cannot write anything like `let x = ./Dir/${filename}`, with the intention of substituting the value `filename` as part of the path to the imported file.

The contents of the imported resource my be treated as plain text or as binary data, instead of treating it as Dhall code.
This is achieved with the syntax `as Text` or `as Bytes`.

For example, environment variables typically contain plain text rather than Dhall code.
So, they should be imported `as Text`:

```dhall
⊢ env:SHELL as Text

"/bin/bash"
```

But one cannot read an external resource as a string and then use that string as a URL or file path for another import.


Instead, it is possible to read the _path_ to an imported resource as a value of a special type.
The syntax `as Location` enables that option:

```dhall
⊢ env:SHELL as Location

< Environment : Text | Local : Text | Missing | Remote : Text >.Environment "SHELL"
```
The result is a value of a union type that describes all supported external resources.

However, `Location` values cannot be reused to perform further imports.


Apart from requiring hard-coded import paths, Dhall imposes other limitations on what can be imported to help users maintain a number of security guarantees:

- All imported modules are required to be valid (must pass typechecking and optionally SHA256 hash matching).
- All imported resources are loaded and validated at typechecking time, before any evaluation may start.
- Circular imports are not allowed: no resource may import itself, either directly or via other imports.
- Imported values are referentially transparent: a repeated import of the same external resource is guaranteed to give the same value (if the import is successful).
- The authentication headers must be either hard-coded or imported; they cannot be computed at evaluation time. 
- CORS restrictions are implemented, so Web URL imports that require authentication headers will not leak those headers to other Web servers.

See [the Dhall documentation on safety guarantees](https://docs.dhall-lang.org/discussions/Safety-guarantees.html) for more details.


#### Organizing modules and submodules

The Dhall standard library (the ["prelude"](https://prelude.dhall-lang.org)) stores code in subdirectories organized by type name.
For instance, functions working with the `Natural` type are in the `Natural/` subdirectory, functions working with lists are in the `List/` subdirectory, and so on.
This convention helps make the code for imports more visual:

```dhall
let Integer/add = https://prelude.dhall-lang.org/Integer/add
let List/concatMap = https://prelude.dhall-lang.org/List/concatMap
let Natural/greaterThan = https://prelude.dhall-lang.org/Natural/greaterThan
let Natural/lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual
let Optional/default = https://prelude.dhall-lang.org/Optional/default
let Optional/map = https://prelude.dhall-lang.org/Optional/map     -- And so on.
```

The import mechanism can be used as a module system that supports creating libraries of reusable code.
For example, suppose we put some Dhall code into files named `./Dir1/file1.dhall` and `./Dir1/file2.dhall`.
We can then import those files like this:
```
let Dir1/file1 = ./Dir1/file1.dhall
let Dir1/file2 = ./Dir1/file2.dhall
in ???
```
Code in `file1.dhall` could also import `file2.dhall` using a relative path, for example like this: `let x = ./file2.dhall`.

Keep in mind that the names such as `Integer/add` or `Dir1/file1` are just a visually helpful convention.
The fact that both files `file1.dhall` and `file2.dhall` are located in the same subdirectory (`Dir1`) has no special significance.
Any file can import any other file, as long as an import path (absolute or relative) is given.

An import such as `let Dir1/file1 = ./Dir1/file1.dhall` might appear to suggest that `file1` is a submodule of `Dir1` in some sense.
But actually Dhall treats all imports in the same way regardless of path; it does not have any special concept of "submodules".
The file `./Dir1/file1.dhall` is treated as a module like any other.

Names like `Dir1/file` are often used in Dhall libraries, but Dhall does not give any special treatment to such names.
Dhall will neither require nor verify that `let Dir1/file1 = ...` imports a file called `file1` in a subdirectory called `Dir1`.

To create a hierarchical library structure of modules and submodules, the Dhall standard library uses nested records.
Each module has a top-level file called `package.dhall` that defines a record with all values from that module.
Some of those values could be again records containing values from other modules (that also define their own `package.dhall` in turn).
The top level of Dhall's standard prelude is a file called `[package.dhall](https://prelude.dhall-lang.org/package.dhall)` that contains a record with all modules in the prelude.
A Dhall file may import the entire prelude and access its submodules like this:
```dhall
let p = https://prelude.dhall-lang.org/package.dhall -- Takes a while to import!
let x = p.Bool.not (p.Natural.greaterThan 1 2)     -- We can use any module now.
  in ???
```

The standard prelude is not treated specially by Dhall.
It is just an ordinary import from a Web URL.
A user's own libraries and modules may have a similar structure of nested records and may be imported as external resources in the same way.
In this way, users can organize their Dhall configuration files and supporting functions via shared libraries and modules.

#### Frozen imports and semantic hashing

Importing from external resources (files, Web URLs, or environment variables) is a form of a side effect because the contents of those resources may change at any time.
Dhall has a feature called **frozen imports** for ensuring
that the contents of an external resource does not change unexpectedly.
Frozen imports are annotated by the SHA256 hash value of the imported content's normal form after a full evaluation.

As an example, create a file called `simple.dhall` containing just the number `3`:

```dhall
-- This file is `simple.dhall`.
3
```
That file may be imported via the following frozen import:

```dhall
-- This file is `another.dhall`.
./simple.dhall sha256:15f52ecf91c94c1baac02d5a4964b2ed8fa401641a2c8a95e8306ec7c1e3b8d2
```
This import expression is annotated by the SHA256 hash value corresponding to the Dhall expression `3`.
If the user modifies the file `simple.dhall` so that it evaluates to anything other than `3`, the hash value will become different and the frozen import will fail.

A frozen import is guaranteed to produce the same value every time,
because the imported value's hash is always validated.
If the contents of the external resource changes and its SHA256 hash no longer matches the annotation, Dhall will raise an error.

Hash values are computed from the _normal form_ of Dhall expressions, and the normal forms are computed only after successful typechecking.
For this reason, the semantic hash of a Dhall program remains unchanged under any valid refactoring.
For instance, we may add or remove comments; reformat the file with fewer or with more spaces or empty lines; change the order of fields in records or the order of constructors in union types; rename, add, or remove local variables; and even change import URLs (as long as the imported content remains equivalent).
The hash value will remain the same as long as the normal form of the final evaluated expression remains the same.
This form of hashing is known as **semantic hashing**.

The Dhall interpreter will cache all frozen imports in the local filesystem, using the SHA256 semantic hash value as part of the file name.
This makes importing libraries faster after the first time.

Keep in mind that Dhall programs with non-frozen imports may produce different results when evaluated at different times.
An example of that behavior is found in Dhall's test suite.
It imports [a randomness source](https://test.dhall-lang.org/random-string), which is a Web service that returns a new random string each time it is called.
So, this Dhall program:

```dhall
https://test.dhall-lang.org/random-string as Text -- This is a complete program.
```
will return a different result _each time_ it is evaluated:

```bash
$ echo "https://test.dhall-lang.org/random-string as Text" | dhall
''
Gajnrpgc4cHWeoYEUaDvAx5qOHPxzSmy
''
$ echo "https://test.dhall-lang.org/random-string as Text" | dhall
''
tH8kPRKgH3vgbjbRaUYPQwSiaIsfaDYT
''
```

One cannot guarantee that any given Web URL always returns the same results (or always remains reachable).
To ensure that all expressions are referentially transparent, each successful import is cached on its first use within a Dhall program.
If a Dhall program imports the same external resource several times, all those imports will always have the same value when the program is evaluated.
For example, if a program imports `https://test.dhall-lang.org/random-string` several times, the first imported value will be internally cached and substituted for all subsequent imports of that resource.

For this reason, a Dhall program cannot query a Web URL several times and make decisions based on the changes in its response.

#### Import alternatives

Dhall has features for providing alternative external resources to be used when an import fails.
Any number of alternative imports may be specified, separated by the question mark operator (`?`):

```dhall
let Natural/lessThan = ./MyLessThanImplementation.dhall
  ? ./AnotherImplementationOfLessThan.dhall
  ? https://prelude.dhall-lang.org/Natural/lessThan
```
This mechanism resolves only "non-fatal" import failures: that is, failures to read an external resource.
A "fatal" import failure means that the external resource was available but gave a Dhall expression that failed to parse, to typecheck, to validate the given semantic hash, or violated some import restrictions (e.g., a circular import).

The operator for alternative imports (`?`) is designed for situations where the same Dhall resource might be stored in different files or at different URLs, some of which might be temporarily unavailable.
If all alternatives fail to read, the import fails (and the entire Dhall program fails to type-check).

Other than providing import alternatives, Dhall does not support any possibility of reacting to an import failure in a custom way.
The intention is to prevent Dhall programs from depending on side effects due to timing issues or network availability.
Dhall programs are intended to be pure and referentially transparent values even in the presence of imports.

The special keyword `missing` denotes an external resource that will _never_ be available.
It works as a neutral element of the `?` operation: `x ? missing` is the same as `x`.

The `missing` keyword is sometimes used as a trick to speed up import loading for frozen imports.
To achieve that, annotate a `missing` import with an SHA256 hash value and provide an alternative:
```dhall
let Natural/lessThan
  = missing sha256:3381b66749290769badf8855d8a3f4af62e8de52d1364d838a9d1e20c94fa70c
  ? https://prelude.dhall-lang.org/Natural/lessThan
```
If the function `Natural/lessThan` has been already cached, it will be retrieved from the cache without resolving any URLs.
Otherwise, there will be a URL lookup and the function will be loaded and cached on the local machine's filesystem.
The next time this Dhall program is run, there will be no URL lookups.

### Miscellaneous features

Multiple `let x = y in z` bindings may be written next to each other without writing "`in`".
For example:

```dhall
let a = 1
let b = 2
in a + b  -- This is a complete program that evaluates to 3.
```
Because of this syntax, Dhall programs often have the form `let ... let ... let ... in ...`, where the "`in`" occurs only at the end.
This book writes most snippets of Dhall code in the form `let a = ...` without the trailing `in`.
It is implied that all those `let` declarations are part of a larger program with "`in`" somewhere at the end.

When we are working with the Dhall interpreter, we may write a standalone `let` declaration.
The syntax is `:let`.

For instance, we may define the type constructor `Pair` shown above:

```dhall
$ dhall repl
Welcome to the Dhall v1.42.2 REPL! Type :help for more information.
⊢ :let Pair = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }

Pair : ∀(a : Type) → ∀(b : Type) → Type
```

Dhall does not require capitalizing the names of types and type parameters.
In this book, we capitalize all type constructors (such as `List`).
Simple type parameters are usually not capitalized in Dhall libraries (`a`, `b`, etc.), but we will sometimes write capitalized type parameters (`A`, `B`, etc.) for additional clarity.
Values are never capitalized in this book.

#### Almost no type inference

Dhall has almost no type inference.
The only exception are the `let` bindings, such as `let x = 1 in ...`, where the type annotation for `x` may be omitted.
Other than in `let` bindings, the types of all bound variables must be written explicitly.

Although this makes Dhall programs more verbose, it also removes the "magic" from the syntax of certain idioms in functional programming.
In particular, Dhall requires us to write out all type parameters and all type quantifiers, to choose carefully between `∀(x : A)` and `λ(x : A)`, and to write type annotations for _types_ (such as, `F : Type → Type`).
This verbosity has helped the author in learning some of the more advanced concepts of functional programming.

#### Strict and lazy evaluation. Partial functions

In a programming language, the **strict evaluation** strategy means that all sub-expressions in a program are evaluated even if they are not used to compute the final result of the program.
The **lazy evaluation** strategy means that sub-expressions are evaluated only if they are needed for computing the program's final result.

In this sense, the Dhall interpreter almost always uses lazy evaluation.
For example, it will be quick to run Dhall programs similar to this:
```dhall
let x = some_function 1000000 ??? -- Imagine that this is a long computation.
in 123
```
The value `x` will not be evaluated because it is not actually needed for computing the final value (`123`).

On the other hand, if we modify this program to include an `assert` test (that feature will be described below), the program will take a longer time to run:
```dhall
let x = some_function 1000000 ??? -- Imagine that this is a long computation.
let _ = assert : validate x ≡ True -- Validate the result in some way.
in 123
```
The value `x` now needs to be evaluated in order to verify that `validate x` actually returns `True`.

Validation of `assert` expressions will happen at typechecking time, and Dhall's _typechecking_ is strict (not lazy).
For this reason, the value `x` will get evaluated in the program shown above, even though `x` is not used anywhere later in the code.
Also, a type error such as `let x : Natural = "abc"` will prevent the entire program from evaluating, even if the ill-typed value `x` is never used in any expressions later in the program.

Another case where Dhall enforces strict evaluation is when importing values from external resources.
Each imported value is loaded and validated at typechecking time, even if the value is not used in the code that follows:

```dhall
let constZero = λ(x : Natural) → 0   -- This function ignores its argument.
let ??? = constZero ./nonexisting_file.dhall -- Error at typechecking time!
```

The typechecking stage is analogous to the compile-time stage in compiled programming languages.
At that stage, the Dhall interpreter resolves all imports and then typechecks all sub-expressions in the program, whether they are used or not.
(Imports must be resolved first, in order to be able to proceed with typechecking.)

When no type errors are found, the interpreter goes on evaluating the program to a normal form, using the lazy evaluation strategy.

It is important to keep in mind that in almost all cases a well-typed Dhall program should give the same result whether evaluated lazily or strictly.

The lazy and strict evaluation strategies will give different results in two situations:
- When a certain sub-expression creates a lazy side effect whose execution may influence the result value.
- When a certain "rogue" sub-expression _cannot_ be evaluated (because it will either crash the program or enter an infinite loop).

To implement the first case, we would need to create a Dhall expression containing a side effect.
The only side effect in Dhall is importing an external resource.
However, Dhall makes all imports strictly evaluated and validated at typechecking time.
So, imports are never lazily evaluated.
As Dhall has no other side effects, we see that the first case does not create a difference between lazy and strict evaluation strategies at evaluation time.

To see how the second case could work, suppose a program defines a rogue expression but does it in such a way that the rogue expression is _not_ actually needed for computing the final result.
Under the lazy evaluation strategy, the rogue expression will not be evaluated (because it is not used), and the program will complete successfully.
But the strict evaluation strategy will try to compute all expressions (whether or not they are used for obtaining the final result).
In that case, the program will fail due to failure evaluating the rogue expression.

Dhall goes quite far towards making rogue expressions impossible.
This is due to Dhall's specific choice of features and strict typechecking:

- A function call will typecheck only if all arguments have correct types.
- A pattern-matching expression will typecheck only if it handles _all_ parts of the union type being matched.
- All `if / then` constructions must have an `else` clause; both clauses must be values of the same type.
- There are no "undefined" values: Dhall has no analog of Haskell's "bottom" or of Java's "null" or of Scala's `???`.
- A program cannot create exceptions or other run-time errors.
- Infinite loops are not possible; every loop must have an upper bound on the number of iterations.
- All lists must have an upper bound on length.

These features eliminate large classes of programmer errors that could create rogue expressions inadvertently.
Nevertheless, two possibilities for creating rogue expressions still remain:
- Create such a large data structure that no realistic computer could fit it in memory.
- Start a computation that takes such a long time that no realistic user could wait for its completion.

These situations are impossible to avoid, because (even with Dhall's restrictions) it is not possible to determine in advance the maximum memory requirements and run times of an arbitrary given program.

Here are examples of implementing these two possibilities in Dhall:
```dhall
let doubleText = λ(x : Text) → x ++ x
-- It is important that Dhall uses lazy evaluation here.
-- A strict evaluation of `petabyte` would require over a petabyte of memory!
let petabyte : Text = Natural/fold 50 Text doubleText "x"

-- A strict evaluation of `slow` would require over a thousand years!
let slow : Natural = Natural/fold 1000000000000000000 Natural (λ(x : Natural) → Natural/subtract x 1) 1
```

Triggering one of these situations will implement (an artificial example of) a **partial function** in Dhall.
A function is partial if it works only for a certain subset of possible argument values; when applied to other values, the partial function will crash or will never complete evaluation.
Typically, a partial function applied to wrong values will create a rogue expression.

As an example, consider the following code that implements a partial function called `crash`.
Evaluating `crash False` returns an empty string.
But `crash True` tries to return a **petabyte** of text, which will almost certainly crash any computer:
```dhall
let crash = λ(b : Bool) → if b then petabyte else ""
let _ = assert : crash False ≡ ""   -- OK.
-- let _ = assert : crash True ≡ ""  -- This will crash!
```

Barring such artificial situations, rogue expressions and partial functions are impossible to implement in Dhall.

So, a Dhall programmer typically does not need to distinguish strict and lazy evaluation.
One can equally well imagine that all Dhall expressions are lazily evaluated, or that they are all strictly evaluated.
The result values of any Dhall program will be the same, as long as memory or time constraints are satisfied, and as long as all imported external resources are valid.


#### No computations with custom data

In Dhall, most built-in types (`Double`, `Bytes`, `Date`, `Time`, `TimeZone`) are completely opaque to the user.
One can specify literal values of those types, and the only operation available for them is printing their values as `Text` strings.
Those types are intended for creating strongly-typed configuration data schemas and for safely exporting data to configuration files.

The built-in types `Bool`, `List`, `Natural`, and `Text` support more operations.
In addition to specifying literal values of those types and printing them to `Text`, a Dhall program can:

- Use `Bool` values as conditions in `if` expressions or with the Boolean operations (`&&`, `||`, `==`).
- Add, multiply, and compare `Natural` numbers.
- Concatenate lists and compute certain other functions on lists (`List/indexed`, `List/length`).
- Concatenate, interpolate, and replace substrings in `Text` strings.

Dhall cannot compare `Text` strings for equality or compute the length of a `Text` string.
Neither can Dhall compare `Double` values or date/time values with each other.
Comparison functions are only possible for `Bool`, `Integer`, and `Natural` values.

#### No general recursion

Unlike most other programming languages, Dhall does not support recursive definitions, neither for types nor for values.
The only recursive data structure directly supported by Dhall is the built-in type `List` representing finite sequences.
The only way to write a loop in Dhall is to use the built-in functions `List/fold`, `Natural/fold`, and "fold-like" functions derived from them, such as `List/map` and others.
Loops written in that way are statically guaranteed to terminate because the total number of iterations is always fixed in advance.

Unlike other programming languages, Dhall does not support "while" or "until" loops.
Those loops perform as many iterations as needed to reach a given stopping condition.
In certain cases, it is not possible to find out in advance whether the stopping condition will ever be reached.
So, programs that contain "while" or "until" loops are not statically guaranteed to terminate.

A list may be created only if the required length of the list is known in advance.
It is not possible to write a program that creates a list by adding more and more elements until some condition is reached, without setting an upper limit in advance.

Similarly, all text strings are statically limited in length.

Although Dhall does not support recursion directly, one can use certain tricks (the Church encoding and existential types) to write non-recursive definitions that simulate recursive types, recursive functions, and "lazy infinite" data structures.
Later chapters in this book will show how that can be achieved.

In practice, this means Dhall programs are limited to working with finite data structures and fold-like iterative functions on them.
Recursive code can be translated into Dhall only if there is a termination guarantee: the total number of nested recursive calls must be bounded in advance.
Within these limitations, Dhall supports a wide variety of iterative and recursive computations.

#### No side effects

Dhall is a purely functional language with no side effects.
All values are referentially transparent.
There are no mutable values, no exceptions, no multithreading,
no writing to files, no printing, etc.

A valid Dhall program is a single expression that will be evaluated to a normal form by the Dhall interpreter.
The user may then print the result to the terminal, or convert it to JSON, YAML, and other formats.
But that happens outside the Dhall program.

The only feature of Dhall that is in some way similar to a side effect is the "import" feature:
a Dhall program can read Dhall values from external resources (files, Web URLs, and environment variables).
The result of evaluating a Dhall program will then depend on the contents of the external resources.

However, this dependency is invisible inside Dhall code, where each import looks like a constant value.
This is because Dhall implements one-time, cached, and read-only imports.
Dhall reads import values similarly to the way a mathematical function reads its arguments.
The paths to external resources must be hard-coded; they are not strings and cannot be computed at run time.
Even though the actual external resource may change during evaluation, the Dhall interpreter will ignore those changes and use only the first value obtained by reading the resource.
A repeated import of the same resource will always give the same Dhall value.
So, it is not possible to write a Dhall program that repeatedly reads a value from an external file and reacts in some way to changes in the file's contents.

A Dhall program also cannot have a custom behavior reacting to a failure _while importing_ the external resources.
There is only a simple mechanism providing fall-back alternative imports in case a resource is missing.
If a resource fails to read despite fall-backs, or fails to typecheck, or fails the integrity check, the Dhall interpreter will fail the entire program.

Most often, imports are used to read library modules with known contents that is not expected to change.
Dhall supports that use case with its "frozen imports" feature.
If all imports are frozen, a Dhall program is guaranteed to give the same results each time it is evaluated.

#### Guaranteed termination

In System Fω, all well-typed expressions are guaranteed to evaluate to a unique final result.
Thanks to this property, the Dhall interpreter is able to guarantee that any well-typed Dhall program will be evaluated in finite time to a unique **normal form** expression (that is, to an expression that cannot be simplified any further).

Evaluation of a well-typed Dhall program will never create infinite loops or throw exceptions due to missing or invalid values or wrong types at run time, as it happens in other programming languages.
It is guaranteed that the correct normal form will be computed, given enough time and computer memory.

Invalid Dhall programs will be rejected at the typechecking phase.
The typechecking itself is also guaranteed to complete within finite time.

The price for those termination and safety guarantees is that the Dhall language is _not_ Turing-complete.
A Turing-complete language must support programs that do not terminate as well as programs for which it is not known whether they terminate.
Dhall supports neither general recursion nor `while`-loops nor any other iterative constructions whose termination is not assured.
Iterative computations are possible in Dhall only if the program specifies the maximum number of iterations in advance.

The lack of Turing-completeness is not a significant limitation for a wide scope of Dhall usage, as this book will show.
Dhall can still perform iterative or recursive processing of numerical data, lists, trees, or other user-defined recursive data structures.

The termination guarantee does _not_ mean that Dhall programs could never exhaust the memory or could never take too long to evaluate.
As Dhall supports arbitrary-precision integers, it is possible to write a Dhall program that runs a loop with an extremely large number of iterations.
It is also possible to come up with short Dhall expressions creating data structures that consume terabytes or petabytes of memory.
We will see some examples of such "rogue expressions" later in this book.

However, it is improbable that a programmer creates a rogue expression by mistake while implementing ordinary tasks in Dhall.
A malicious adversary could try to inject such expressions into Dhall programs.
To mitigate that possibility, Dhall implements strict guardrails on external imports.

### Overview of the standard library ("prelude")

Dhall's [standard library](https://prelude.dhall-lang.org) has a number of modules containing utility functions.

The library functions are mostly organized in subdirectories whose names indicate the main type with which the functions work.
So, functions in the `Bool` subdirectory are for working with the `Bool` type,
functions in the `List` subdirectory work with lists, and so on.

All built-in Dhall functions have the corresponding standard library functions (just for the purpose of clean re-export).
For example, the built-in function `Date/show` has the corresponding standard library code exported at `https://prelude.dhall-lang.org/Date/show` (which just calls the built-in function).

In addition, there are subdirectories such as `Function` and `Operator` that do not correspond to a specific type but are collections of general-purpose utilities.

Library functions for working with `Natural` and `Integer` numbers include functions such as `Natural/lessThan`, `Integer/lessThan`, and so on.
Functions for working with lists include `List/map`, `List/filter`, and other utility functions.

All those functions are implemented through Dhall built-ins.
As Dhall intentionally includes only a small number of built-in functions, implementing functionality such as `List/take` and `List/drop` results in slower and/or larger code than one might expect.

TODO:describe DirectoryFile, JSON, Function, Operator

## Other features of Dhall's type system

### Working with records polymorphically

"Polymorphic records" is a feature of some programming languages where, say, a record of type `{ x : Natural, y : Bool }` is considered to be a subtype of the record type `{ y : Bool }`.
A function that requires its argument to have type `{ y : Bool }` will then also accept an argument of type `{ x : Natural, y : Bool }`.
(The value `x` will be simply ignored.)
In effect, languages with polymorphic records will interpret the record type `{ y : Bool }` as the type of any record having a Boolean field `y` and possibly other fields that the code (in this scope) does not need to know about.

Dhall does not supports subtyping or polymorphic records, but it does include some facilities to work with records polymorphically.

A typical use case for polymorphic records is when a function requires an argument of a record type `{ a : A, b : B }`, but we would like that function to accept records with more fields, for example, of type `{ a : A, b : B, c : C, d : D }`.
The function only needs the fields `a` and `b` and should ignore any other fields the record may have.

To implement this behavior in Dhall, use a field selection operation: any unexpected fields will be automatically removed from the record.

```dhall
let MyTuple = { _1 : Bool, _2 : Natural}
let f = λ(tuple : MyTuple) → tuple._2
let r1 = { _1 = True, _2 = 123, _3 = "abc", other = [ 1, 2, 3 ] }
in f r1.(MyTuple)  -- This is a complete program that returns 123.
```

The field selection operation `r1.(MyTuple)` removes all fields other than those defined in the type `MyTuple`.
We cannot write `f r1` because `r1` does not have the type `MyTuple`.
Instead, we write `f r1.(MyTuple)`.
We would need to use the field selection each time we call the function `f`.

Another often needed feature is providing default values for missing fields.
This is implemented with Dhall's record update operation (`//`):

```dhall
let MyTuple = { _1 : Bool, _2 : Natural}
let myTupleDefault = { _1 = False, _2 = 0 }
let f = λ(tuple : MyTuple) → tuple._2
let r2 = { _2 = 123, _3 = "abc", other = [ 1, 2, 3 ] }
in f (myTupleDefault // r2).(MyTuple)  -- This is a complete program that returns 123.
```

We cannot write `f r2.(MyTuple)` because `r2` does not have the required field `_1`.
The default record `myTupleDefault` provides that value.

The expression `(myTupleDefault // r).(MyTuple)` will accept record values `r` of any record type whatsoever.
If `r` contains fields named `_1` and/or `_2`, the expression `myTupleDefault // r` will preserve those fields while filling in the default values for any missing fields.
The field selection `.(MyTuple)` will remove any other fields.

The built-in Dhall operations `//` and `.()` can be viewed as functions that accept polymorphic record types.
For instance, `r.(MyTuple)` will accept records `r` having the fields `_1 : Bool` , `_2 : Natural` and possibly any other fields.
Similarly, `myTupleDefault // r` will accept records `r` of any record type and return a record that is guaranteed to have the fields `_1 : Bool` and `_2 : NAtural`.

But Dhall cannot directly describe the type of records with unknown fields.
(There is no type that means "any record".)
So, one cannot write a Dhall function taking an arbitrary record `r` and returning `r.(MyTuple)` or `myTupleDefault // r`.

Dhall programs must write expressions such as `myTupleDefault // r` or `r.(MyTuple)` at each place (at call site) where record polymorphism is required.


### Types and values

In Dhall, as in every programming language, types are different from values.
Each value has an assigned type, but it is not true that each type has only one assigned value.

Dhall will check that each value in a program has the correct type and that all types match whenever functions are applied to arguments, or when explicit type annotations are given.

Other than that, Dhall treats types and values in a largely similar way.
Types may be assigned to new named values, stored in records, and passed as function parameters using the same syntax as when working with values.

For instance, we may write `let x : Bool = True` to define a variable of type `Bool`.
Here, we used the type `Bool` as a type annotation for the variable `x`.
But we may also write `let y = Bool` to define a variable `y` whose value is the type symbol `Bool` itself.
Then we will be able to use `y` in type annotations, such as `x : y`.
The type of `y` itself will be `Type`.

To find out the type of an expression, one can write `:type` in the Dhall interpreter:

```dhall
$ dhall repl
Welcome to the Dhall v1.42.2 REPL! Type :help for more information.
⊢ :type True

Bool

⊢ :type Bool

Type
```

Dhall defines functions with the `λ` syntax:

```dhall
let inc = λ(t : Natural) → t + 1
```

The same syntax works if `t` were a type parameter (having type `Type`):

```dhall
let f = λ(t : Type) → λ(x : t) → { first = x, second = x }
```

Records and unions may use types or values as their data, as in these (artificial) examples:


```dhall
⊢ :type { a = 1, b = Bool, c = "xyz" }

{ a : Natural, b : Type, c : Text }

⊢ :type < A : Type | B : Text >.A Natural

< A : Type | B : Text >
```

The built-in type constructors `List` and `Optional` are limited to _values_; one cannot create a `List` of types in the same way as one creates a list of integers, and one cannot use `Optional` with types.

```dhall
⊢ :let a = [ 1, 2, 3 ]

a : List Natural

⊢ :let b = [ Bool, Natural, Text ]

Error: Invalid type for ❰List❱

⊢ :let c = Some Natural

Error: ❰Some❱ argument has the wrong type
```

Nevertheless, Dhall can implement a data structure similar to an `Optional` containing a type.

```dhall
let OptionalT = < None | Some : Type >
```

Later chapters in this book will show how to define type-level data structures such as a list of type symbols.



### Dependent types in Dhall

Dependent types are types that depend on _values_.

Dhall supports **dependent functions**: those are functions whose output type depends on the input value.
More generally, Dhall allows an argument type to depend on any previously given curried arguments.

A simple instance of this dependence is the type of the polymorphic identity function:

```dhall
let example1 : Type = ∀(A : Type) → ∀(x : A) → A
```

Here the second curried argument (`x : A`) is designated as having the type (`A`) given by the first curried argument (`A : Type`).
So, the type of the second argument depends on the first argument.

As another example, consider the following function type:

```dhall
let example2 : Type = ∀(F : Type → Type) → ∀(A : Type) → ∀(x : A) → F A
```

In the type `example2`, the argument `x` has type `A`, which is given by a previous argument.
The output type `F A` depends on the first two arguments.


In Dhall, one can   define functions from types to types or from values to types via the same syntax as for defining ordinary functions.
As an example, look at this function that transforms a value into a type:

```dhall
let f : ∀(x : Bool) → Type  -- From value to type.
  = λ(x : Bool) → if x then Natural else Text
```

The result of evaluating `f False` is the _type_ `Text` itself.
This is an example of a **dependent type**, that is, a type that depends on a value (`x`).
This `f` can be used within the type signature for another function as a type annotation, like this:

```dhall
let some_func_type = ∀(x : Bool) → ∀(y : f x) → Text
```
A value of type `some_func_type` is a curried function that takes a natural number `x` and a second argument `y`.
The type of `y` must be either `Natural` or `Text` depending on the _value_ of the argument `x`.
```dhall
let some_func : some_func_type = λ(x : Bool) → λ(y : f x) → "abc" -- Whatever.
```

If we imagine uncurrying that function, we would get a function of type that we could write symbolically as `{ x : Bool, y : f x } → Text`.
This type is not valid in Dhall, because a record's field types must be fixed and cannot depend on the _value_ of another field.
Such "dependent records" or "dependent pairs" are directly supported in more advanced languages that are intended for working with dependent types.
We will show later in this book how Dhall can encode dependent pairs despite that limitation.

The type `∀(x : Bool) → f x` is also a form of a dependent type, known as a "dependent function".

Dhall's implementation of dependent types is limited to the simplest use cases.
The main limitation is that Dhall cannot correctly infer types that depend on values in `if/then/else` expressions or in pattern-matching expressions.

The following example (using the function `f` defined above) shows that Dhall does not track correctly the dependent types inside an `if` branch:

```dhall
⊢ :let g : ∀(x : Bool) → f x → Text = λ(x : Bool) → λ(y : f x) → if x then "" else y

Error: ❰if❱ branches must have matching types
```
The `if/then/else` construction fails to typecheck even though we expect both `if` branches to return `Text` values.
If we are in the `if/then` branch, we return a `Text` value (an empty string).
If we are in the `if/else` branch, we return a value of type `f x`.
That type depends on the value `x`.
But we know that `x` equals `False` in that branch.
So, the `else` branch should have the type `f False`, which is the same as the type `Text`.
However, Dhall does not implement this logic and cannot see that both branches have the same type (`Text`).

Because of this and other limitations, Dhall can work productively with dependent types only in certain simple cases, such as validations of properties for function arguments.

Below in the chapter "Numerical algorithms" we will see an example of using dependent types for implementing a safe division operation.

### The "assert" keyword and equality types

For values other than `Bool` and `Natural` numbers, equality testing is not available as a function.
However, values of any type may be tested for equality at typechecking time via Dhall's `assert` feature.
That feature is mainly intended for implementing sanity checks and unit tests:

```dhall
let x : Text = "123"
let _ = assert : x ≡ "123"
in x ++ "1"
 -- This is a complete program that returns "1231".
```

The Dhall expression `a ≡ b` is a special _type_ that depends on the values `a` and `b`.
The type `a ≡ b` is different for each pair `a`, `b`.
It is an example of a **dependent type**.

Types of the form `a ≡ b` are known as **equality types** because they have the following special properties:

The type `a ≡ b` has _no values_ (is void) if `a` and `b` have different types, or the same types but different normal forms (as Dhall expressions).
For example, the types `1 ≡ True` and `λ(x : Text) → λ(y : Text) → x ≡ λ(x : Text) → λ(y : Text) → y` are void.
(We will never be able to create any values of those types.)

If `a` and `b` evaluate to the same normal form then the type `a ≡ b` is not void.

For example, the types `False ≡ False` and `123 ≡ 123` are not void.

Actually, types of the form `a ≡ a` are defined to be a unit type.
That is, there always exists a single value of the type `a ≡ a`.

If we want to write that value explicitly, we use the `assert` keyword with the syntax `assert : a ≡ b`.
This expression is valid only if the two sides are equal after reducing them to their normal forms.
If the two sides are not equal after reduction to normal forms, the expression `assert : a ≡ b` will _fail to typecheck_, meaning that the entire program will fail to compile.



When an `assert` value is valid, we may assign that value to a variable:

```dhall
let test1 = assert : 1 + 2 ≡ 0 + 3
```

In this example, the two sides of the type `1 + 2 ≡ 0 + 3` are equal after reducing them to normal forms.
The resulting type `3 ≡ 3` is non-void and has a value.
We assigned that value to `test1`.

It is not actually possible to print the value `test1` of type `3 ≡ 3` or to examine that value in any other way.
That value _exists_ (because the `assert` expression was accepted by Dhall), but that's all we know.


Some examples:

```dhall
let x = 1
let y = 2
let _ = assert : x + 1 ≡ y     -- OK.
let print = λ(n : Natural) → λ(prefix : Text) → prefix ++ Natural/show n
let _ = assert : print (x + 1) ≡ print y    -- OK
```

In the last line, the `assert` expression was used to compare two partially evaluated functions, `print (x + 1)` and `print y`.
The normal form of `print (x + 1)` is the Dhall expression `λ(prefix : Text) → prefix ++ "2"`.
The normal form of `print y` is the same Dhall expression.
So, the assertion is valid.

The fact that `assert` expressions are validated at typechecking time (before evaluating other expressions) has implications for using the `assert` feature in Dhall programs.
For instance, one cannot use `assert` expressions for implementing a function for comparing two arbitrary values given as arguments.

To see why, try writing this code:

```dhall
let compareTextValues : Text → Text → Bool
  = λ(a : Text) → λ(b : Text) →
    let _ = assert : a ≡ b    -- Type error: the two sides are not equal.
    in True
```

This code will fail to typecheck because, within the code of `compareTextValues`, the normal forms of the parameters `a` and `b` are just the _symbols_ `a` and `b`, and those two symbols are not equal.
Because this code fails to typecheck, we cannot use it to implement a function returning `False` when two text strings are not equal.


Another example: we cannot write a Dhall function that checks whether a string is empty, when that string is given as the function's parameter.
```dhall
let isStringEmpty = λ(t : Text) →
  assert : t ≡ "" -- Type error: assertion failed.
```
This fails at typechecking time because the normal form of `t` is just the symbol `t` at that time, and that symbol is never equal to the empty string.
An `assert` expression such as `assert : t ≡ ""` can be validated only when the value `t` is "statically known" in the scope of the expression.
(We will discuss "statically known" values in more detail in the next subsection.)

These examples show that it rarely makes sense to use `assert` inside function bodies.
The `assert` feature is intended for implementing unit tests or other static sanity checks.
In those cases, we do not need to keep the value of each equality type.
We just need to verify that all the equality types are not void.
So, we will usually write unit tests like this:

```dhall
let f = λ(a : Text) → "(" ++ a ++ ")" -- Define a function.
let _ = assert : f "x" ≡ "(x)"  -- OK.
let _ = assert : f "" ≡ "()"    -- OK.
-- Continue writing code.
```

### Asserting that a value is statically known

We say that a value in a Dhall program is a **statically known value** if it is built up from literal constants, imported values, and/or other statically known values.
To get some intuition, consider a typical Dhall program:

```dhall
let Integer/add = https://prelude.dhall-lang.org/Integer/add
let Natural/equal = ...         -- Other library imports.

let k : Natural = ./size.dhall  -- Some value is imported.
let n : Natural = k + 123       -- Compute some more values.
let f = λ(x : Natural) →        -- Some custom function.
    let y = x * k
    in ...
let g = λ(a : Type) → ???       -- More custom functions.
let _ = assert : f n ≡ True     -- Perform some validations.
in ???                          -- Now compute the final result.
```
The values `f`, `g`, `k`, and `n` are statically known in this code.
The value `k` is a direct import.
The value`n` is computed from a literal number (`123`) and from `k`.
The values `f` and `g` are literal function expressions defined at the top level.
The bodies of the functions `f` and `g` may use `k`, `n`, and library imports.
So, those functions are built up from statically  known values. 

Usually, values at the top level of a standalone Dhall program are statically known.
Examples of values that are _not_ statically known are the parameter `x` and the local variable `y` inside the body of the function `f` above.
Generally, parameters of functions are not statically known within the scope of those functions.

The concept of "statically known values" is useful for understanding the usage of `assert` expressions.
Consider an assertion of the form `assert : x ≡ y`.
If `x` and `y` are statically known then the Dhall typechecker will validate that assertion by reducing both `x` and `y` to literal values.
Typically, that's what the programmer expects when writing an `assert` expression.
For this reason, `assert` expressions are most often used with statically known values.

Reducing an expression to a literal value at typechecking time is possible only if that expression is a statically known value.

When `x` or `y` is not statically known, the typechecker will only be able to reduce `x ≡ y` to a normal form containing symbolic variables.
In most cases, this is not useful because it cannot be validated.

For example, here is an attempt to create a function `f` enforcing the condition that `f x` may be called only with nonzero `x`:
```dhall
let f : Natural → Bool = λ(x : Natural) →
  let _ = assert : Natural/isZero x ≡ False
  in True -- Type error: assertion failed.
```
Within the body of this function, `x` is not a statically known value.
The assertion of `Natural/isZero x ≡ False` will be examined at type-checking time, before the function `f` is ever applied to an argument.
Dhall will try to validate the assertion of `Natural/isZero x ≡ False` by reducing both sides to the normal form.
But the normal form of `Natural/isZero x` is just the symbolic expression `Natural/isZero x` itself.
That symbolic expression is not equal to `False`, and so the assertion fails.
We see that Dhall cannot validate this assertion in a meaningful way; this is almost surely not what the programmer expected.

A function may return the _type_ `Natural/isZero x ≡ False` itself; that type is well-defined as a function of `x`.
Then one can use that type to write assertions for statically known values: 
```dhall
let NonzeroNat : Natural → Type
  = λ(x : Natural) → Natural/isZero x ≡ False
let _ = assert : NonzeroNat 123   -- OK.
-- let _ = assert : NonzeroNat 0    -- This fails!
```


A curious consequence of the limitations of Dhall's evaluator and typechecker is that a function can require evidence that one of its parameters is a statically known value.

The implementation is based on the fact that Dhall cannot perform symbolic reasoning with `Natural` values other than in simplest cases.
So, one can implement a constant function of type `Natural → Natural` that appears to perform nontrivial computations but actually always returns `0`.
The function body can be chosen such that Dhall will be unable to detect statically that the function always returns `0`.

We can then apply that function to a `Natural`-valued parameter and assert that the result is indeed `0`.
Dhall will accept the assertion only if the parameter can be reduced to a literal value.
And that will be possible only when the parameter is statically known.

A suitable function is the following:

```dhall
let zeroNatural : Natural → Natural = λ(x : Natural) → Natural/subtract 1 (Natural/subtract x 1)
```
The function `zeroNatural` is a constant function that always returns `0`, but Dhall cannot recognize that property unless `zeroNatural` is applied to a statically known `Natural` argument:
```dhall
let _ = assert : zeroNatural 0 ≡ 0
let _ = assert : zeroNatural 1 ≡ 0
let _ = assert : zeroNatural 2 ≡ 0
-- This fails with a type error:
-- λ(x : Natural) → assert : zeroNatural x ≡ 0
```

With this function, we can create a dependent type that always represents a valid assertion, but Dhall will be able to validate that assertion only for statically known values.
```dhall
let StaticNatural : Natural → Type = λ(x : Natural) → zeroNatural x ≡ 0
let _ = assert : StaticNatural 123      -- OK.
-- This fails with a type error because `x` is not statically known:
-- λ(x : Natural) → assert : StaticNatural x
```

Now write a function taking a `Natural` argument `n` together with an evidence argument of type `StaticNatural n`:
```dhall
let functionStaticOnly = λ(n : Natural) → λ(_ : StaticNatural n) → n + 1
```
We can call this function at the top level of a Dhall program, where all `Natural` values are statically known and an `assert : StaticNatural n` will always succeed.
```dhall
let x = 123
let xIsStatic = assert : StaticNatural x
let y = functionStaticOnly x xIsStatic
let _ = assert : y ≡ 124
```
But it will be impossible to call `functionStaticOnly` within another function, without having an evidence value of type `StaticNatural x`:
```dhall
-- Type error: assertion will fail.
let _ = λ(x : Natural) →
  let xIsStatic = assert : StaticNatural x  -- This fails.
  in functionStaticOnly x xIsStatic
```

A suitable equality type for `Integer` numbers is defined by:
```dhall
let zeroInteger : Integer → Integer = λ(x : Integer) → Natural/toInteger (zeroNatural (Integer/clamp x))
let StaticInteger : Integer → Type = λ(x : Integer) → zeroInteger x ≡ +0
```

For `Bool` arguments, the code could look like this:

```dhall
let zeroBool : Bool → Natural = λ(x : Bool) → zeroNatural (if x then 1 else 0)
let StaticBool : Bool → Type = λ(x : Bool) → zeroBool x ≡ 0
let _ = assert : StaticBool False  -- OK.
```

For `Text` arguments, we create a sequence of operations that will always return an empty string, and we assert on that property.
But the Dhall interpreter is unable to validate that property symbolically.
So, the assertion on `StaticText x` will fail unless `x` is a statically known value:
```dhall
let emptyText : Text → Text = λ(x : Text) → Text/replace x "" (x ++ x)
let StaticText : Text → Type = λ(x : Text) → emptyText x ≡ ""
-- This fails with a type error because `x` is not statically known:
-- λ(x : Text) → assert : StaticText x
```


### The universal type quantifier (∀) and the function symbol (λ)

Dhall uses the symbol `λ` (or equivalently the backslash `\`) to denote functions and the symbol `∀` (or equivalently the keyword `forall`) to denote _types_ of functions.

- An expression of the form `λ(x : sometype1) → something2` is a function that can be applied to an argument in order to compute a result. Note that `something2` could be either a value or a type.
- An expression of the form `∀(x : sometype1) → sometype2` is always a _function type_: in other words, an expression that can be used as a type annotation for a function. In particular, `sometype2` must be a type.

Expressions of the form `∀(x : sometype1) → sometype2` may be used as type annotations for functions of the form `λ(x : sometype1) → something2`.
(However, it is not important that the name `x` be the same in the function and in its type.)

For example, the function that appends `"..."` to a string argument is written like this:

```dhall
let f = λ(x : Text) → x ++ "..."
```

The type of `f` can be written as `∀(x : Text) → Text` if we like.
Let us write the definition of `f` together with a type annotation, to show how the type expression corresponds to the code expression:

```dhall
let f : ∀(x : Text) → Text
  = λ(x : Text) → x ++ "..."
```

To summarize: `λ(x : a) → ...` is a function and can be applied to an argument.
But `∀(x : a) → ...` is a type; it is not a function and cannot be applied to an argument.


The type expression `∀(x : Text) → Text` does not actually need the name `x` and can be also written in a shorter syntax as just `Text → Text`.
But Dhall will internally rewrite that to the normal form `∀(_ : Text) → Text`.

A function type of the form `A → B` can be always rewritten in an equivalent but longer syntax as `∀(a : A) → B`.
So, for instance, type expressions `∀(A : Type) → A → A` and `∀(A : Type) → ∀(x : A) → A` are equivalent.
The additional name `x` could be chosen to help the programmer remember the intent behind that argument.


#### Example: Optional

To illustrate the difference between `∀` and `λ` compare the Dhall expressions `λ(r : Type) → Optional r` and `∀(r : Type) → Optional r`.

The expression `λ(r : Type) → Optional r` is equivalent to just the type constructor `Optional`.
It is a function operating on types: it needs to be applied to a particular type in order to produce a result type.
The expression `λ(r : Type) → Optional r` itself has type `Type → Type`.

The expression `∀(r : Type) → Optional r` has type `Type`.
It is the type of functions having a type parameter `r` and returning a value of type `Optional r`.
A value of type `∀(r : Type) → Optional r` must be a function written in the form `λ(r : Type) → ...` in some way.
The code of such a function must work for all types `r` and must produce some value of type `Optional r`, no matter what the type `r` might be.


#### Example: polymorphic identity function

As another example to help remember the difference between `∀` and `λ`, look at the polymorphic identity function.
That function takes a value `x` of an arbitrary type and again returns the same value `x`:

```dhall
let identity
 : ∀(X : Type) → ∀(x : X) → X
  = λ(X : Type) → λ(x : X) → x
```
Here we denoted the type parameter by the capital `X`.
(Dhall does not require that types be capitalized.)

Defined like this, `identity` is a function of type `∀(X : Type) → X → X`.
The function itself is the expression `λ(X : Type) → λ(x : X) → x`.

The corresponding Haskell code is:

```haskell
identity :: a -> a      -- Haskell.
identity = \x -> x
```

The corresponding Scala code is:

```scala
def identity[X]: X => X  = { x => x }     // Scala
```

In Dhall, the type parameters must be specified explicitly, both when defining a function and when calling it:

```dhall
let identity = λ(X : Type) → λ(x : X) → x
let x = identity Natural 123  -- Writing just `identity 123` is a type error.
```

This makes Dhall code more verbose but also helps remove "magic" from the syntax, helping functional programmers to learn about more complicated types.


#### Example: "Invalid function output"

An expression of the form `λ(x : sometype1) → something2` is a function that can be applied to any `x` of type `sometype1` and will compute a result, `something2`.
(That result could itself be a value or a type.)
The _type_ of the expression `λ(x : sometype1) → something2` is `∀(x : sometype1) → sometype2` where `sometype2` is the type of `something2`.

Another way to see that `∀` always denotes types is to write `∀(x : Text) → 123`.
Dhall will reject that expression with the error message "Invalid function output".
The expression `∀(x : Text) → something` is a _type_ of a function, and `something` must be the output type of that function.
So, `something` must be a type and cannot be a value.
But in the example `∀(x : Text) → 123`, the output type of the function is specified as the number `123`, which is not a type.

In Dhall, this requirement is expressed by saying that `something` should have type `Type`, `Kind`, or `Sort`.

As another example of the error "Invalid function output", consider code like `∀(x : Type) → λ(y : Type) → x`.
This code has the form `∀(x : Type) → something` where `something` is a function expression `λ(y : Type) → x`, which is not a type.
So, a `λ` cannot be used in a curried argument after a `∀`.

Here are some valid examples using both `λ` and `∀` in curried arguments:

```dhall
let _ = ∀(x : Type) → ∀(y : Type) → x
let _ = λ(x : Type) → ∀(y : Type) → x
let _ = λ(x : Type) → λ(y : Type) → x
```


### Kinds and sorts

We have seen that in many cases Dhall treats types (such as `Natural` or `Text`) similarly to values.
For instance, we could write `let N = Natural in ...` and then use `N` interchangeably with the built-in symbol `Natural`.
The value `N` itself has a type that is denoted by the symbol `Type`.
We may write it with a type annotation as `N : Type`.
```dhall
⊢ :let N = Natural

N : Type
```

The symbol `Type` is itself treated as a special constant whose type is `Kind`:

```dhall
⊢ :let p = Type

p : Kind
```

Other possible values of type `Kind` are "type constructor" kinds, such as `Type → Type`, as well as other type expressions involving the symbol `Type`.

```dhall
⊢ :type (Type → Type) → Type

Kind

⊢ :type { a : Type }

Kind
```

As we have just seen, the type of `{ a = 1, b = Bool }` is the record type written in Dhall as `{ a : Natural, b : Type }`.
The type of _that_ is `Kind`:

```dhall
⊢ :type { a : Natural, b : Type }

Kind
```

Any function that returns something containing `Type` will itself have the output type `Kind`:

```dhall
⊢ :type λ(t : Bool) → if t then Type else Type → Type

∀(t : Bool) → Kind
```

Functions with parameters of type `Kind` can be used for creating complicated higher-order types.
For example, here is a function that creates higher-order types of the form `k → k`, where `k` could be `Type`, `Type → Type`, or any other   expression  of type `Kind`:

```dhall
⊢ :let f = λ(k : Kind) → k → k

f : ∀(k : Kind) → Kind

⊢ f Type

Type → Type

⊢ f (Type → Type → Type)

(Type → Type → Type) → Type → Type → Type
```

In turn, the symbol `Kind` is treated as a special value of type `Sort`.
Other type expressions involving `Kind` are also of type `Sort`:

```dhall
⊢ :type Kind

Sort

⊢ :type Kind → Kind → Type

Sort

⊢ :type λ(a : Kind) → a → a

∀(a : Kind) → Kind

⊢ :type ∀(a : Kind) → Kind

Sort
```

The symbol `Sort` is even more special: it _does not_ itself have a type.
Because of that, nearly any explicit usage of `Sort` will be a type error:

```dhall
⊢ :let a = Sort

Error: ❰Sort❱ has no type, kind, or sort

⊢ λ(_ : Sort) → 0

Error: ❰Sort❱ has no type, kind, or sort
```

This feature of Dhall avoids the need for an infinite hierarchy of "**type universes**".
That hierarchy is found in programming languages with full support for dependent types, such as Agda and Idris.
In those languages, the type of `Type` is denoted by `Type 1`, the type of `Type 1` is `Type 2`, and so on to infinity.
Dhall denotes `Type 1` by the symbol `Kind` and `Type 2` by the symbol `Sort`.

Dhall's type system has enough abstraction to support powerful types and to treat types and values in a uniform manner, while avoiding the complications with infinitely many type universes.

Because of this design, Dhall has very limited support for working with the symbol `Kind` itself.
Little can be done with Dhall expressions such as `Kind` or `Kind → Kind`.
One can assign such expressions to variables, one can use them for type annotations, and that's about it.

For instance, it is a type error to write a function that returns the symbol `Kind` as its output value:

```dhall
⊢ :let a = Kind

a : Sort

⊢ :let f = λ(_: Bool) → a

Error: ❰Sort❱ has no type, kind, or sort
```

This error occurs because Dhall requires a function's type _itself_ to have a type.
The symbol `Kind` has type `Sort`,
so the type of the function `f = λ(_: Bool) → Kind` is `Bool → Sort`.
But the symbol `Sort` does not have a type, and neither does the expression `Bool → Sort`.
Dhall raises a type error because the function `f`'s type (which is `Bool → Sort`) does not itself have a type.

For the same reason, Dhall will not accept the following function parameterized by a `Kind` value:
```dhall
⊢ :let f = λ(k : Kind) → ∀(b : Kind) → k → b

Error: ❰Sort❱ has no type, kind, or sort
```
This prevents Dhall from defining recursive kind-polymorphic type constructors. For example, Dhall cannot implement an analog of `List` that works with types of arbitrary kinds.

There was at one time an effort to change Dhall and to make `Kind` values more similar to `Type` values, so that one could have more freedom with functions with `Kind` parameters.
But that effort was abandoned after it was discovered that it would [break the consistency of Dhall's type system](https://github.com/dhall-lang/dhall-haskell/pull/563#issuecomment-426474106).

Nevertheless, it is perfectly possible to define a list of types of a specific kind.
For instance, one can define a list of types of kind `Type`, such as `Natural`, `Bool`, and `Text`.
Separately, one can define a list of types of kind `Type → Type`, such as `Optional` and `List`.
Those definitions will use similar code, but it will not be possible to refactor them into special cases of a more general kind-polymorphic code.
(This limitation does not appear to be particularly important for the practical use cases of Dhall.)

## Numerical algorithms

Dhall's `Natural` numbers have arbitrary precision and support a limited number of built-in operations.
The standard prelude includes functions that can add, subtract, multiply, compare, and test `Natural` numbers for being even or odd.

We will now show how to implement other numerical operations such as division or logarithm.
In an ordinary programming language, we would use loops to implement those operations.
But Dhall needs to guarantee termination and will accept loops only if the number of iterations is given in advance.
This requires us to determine explicit upper bounds on the number of iterations in every algorithm.

### Using `Natural/fold` to implement loops

The function `Natural/fold` can be used for creating loops with a fixed number of iterations.
The type of `Natural/fold` is:

```dhall
let _ = Natural/fold
   : ∀(n : Natural) → ∀(A : Type) → ∀(update : A → A) → ∀(init : A) → A
```
Evaluating `Natural/fold n A f x` will compute `f(f(...f(x)...))`, where `f` is applied `n` times.

For example, if the function `f` appends `" world"` to a string argument, we can use `Natural/fold` to apply that function 4 times:
```dhall
⊢ let f = λ(a : Text) → a ++ " world" in Natural/fold 4 Text f "Hello,"

"Hello, world world world world"
```

In this way, Dhall can perform many operations that are usually implemented via loops.
However, `Natural/fold` is not a `while`-loop: it cannot iterate as many times as needed until some condition holds.
The total number of iterations must be given as the first argument of `Natural/fold`.

When the total number of iterations is not known precisely, one must give an upper estimate and design the algorithm so that further iterations will not change the result once the final iteration is reached.
The Haskell and Scala implementations of Dhall will stop iterations in `Natural/fold` when the result stops changing.

For example, consider this (artificial) example:

```dhall
let f : Natural → Natural = λ(x : Natural) → if Natural/isZero x then 1 else x
let result : Natural = Natural/fold 100000000 Natural f 0
let _ = assert : result ≡ 1
```

Theoretically, `Natural/fold 100000000` needs to apply a given function `100000000` times.
But in this example, the result of applying the function `f` will no longer change after the second iteration, and the loop can be stopped early.
The current Haskell and Scala implementations of Dhall will detect that and complete running this code quite quickly.

The next subsections will show some examples of iterative algorithms implemented via `Natural/fold`.

### Factorial

A simple way of implementing the factorial function in a language that directly supports recursion is to write code like `fact (n) = n * fact (n - 1)`.
Since Dhall does not directly support recursion, we need to reformulate this computation through repeated application of a certain function.
The factorial function must be expressed through a computation of the form `s(s(...(s(z))...))` with some initial value `z : A` and some function `s : A → A`, where `s` is applied `n` times.
We need to find `A`, `s`, and `z` that permit implementing the factorial function in that way.

We expect to iterate over `1, 2, ..., n` while computing the factorial.
It is clear that the type `A` must hold both the current partial result and the iteration count.
So, let us define the accumulator type `A` as a pair `{ current : Natural, iteration : Natural }`.
```dhall
let Accum = { current : Natural, iteration : Natural }
```
Each iteration will multiply the current result by the iteration count and increment that count.
We define the function `s` accordingly.
The complete code is:

```dhall
let factorial = λ(n : Natural) →
  let init : Accum = { current = 1, iteration = 1 }
  let s : Accum → Accum = λ(acc : Accum) → {
    current = acc.current * acc.iteration,
    iteration = acc.iteration + 1,
  }
  let result : Accum = Natural/fold n Accum s init
  in result.current
```

Let us test this code:

```dhall
let _ = assert : factorial 10 ≡ 3628800
```


### Integer division
As another example, we implement division for natural numbers.

A simple iterative algorithm that uses only subtraction runs like this.
Given `x : Natural` and `y : Natural`, we subtract `y` from `x` as many times as needed until the result becomes negative.
The value `x div y` is the number of times we subtracted.

This algorithm can be directly implemented in Dhall, but we need to specify in advance the maximum required number of iterations.
A safe upper bound for the number of subtractions is the value `x` itself (because `y` is at least 1).
So, our code will use the function call `Natural/fold x ...`.

In most cases, the actual required number of iterations will be smaller than `x`.
For clarity, we will maintain a Boolean flag (`done`) and set it to `True` once we reach the final result.
The code will use that flag to ensure that any further iterations do not modify the final result.
(The same functionality could be implemented also without using such a flag.)

```dhall
let Natural/lessThan = https://prelude.dhall-lang.org/Natural/lessThan

-- unsafeDiv x y means x / y, but it will return wrong results when y = 0.
let unsafeDiv : Natural → Natural → Natural =
  let Accum = { result : Natural, sub : Natural, done : Bool }
  in λ(x : Natural) → λ(y : Natural) →
         let init : Accum = { result = 0, sub = x, done = False}
         let update : Accum → Accum = λ(acc : Accum) →
             if acc.done then acc    -- Stop modifying the result.
             else if Natural/lessThan acc.sub y then acc // { done = True }
             else acc // { result = acc.result + 1, sub = Natural/subtract y acc.sub }
         let r : Accum = Natural/fold x Accum update init
         in r.result

let test = assert : unsafeDiv 3 2 ≡ 1
```

### Safe division via dependently-typed evidence

The function `unsafeDiv` works but produces wrong results when dividing by zero.
For instance, `unsafeDiv 2 0` returns `2`.
We would like to prevent using that function when the second argument is zero.

To ensure that we never divide by zero, we may use a technique based on dependently-typed "evidence values".

The first step is to define a dependent type that will be void (with no values) if a given natural number is zero:

```dhall
let Nonzero : Natural → Type
  = λ(y : Natural) → if Natural/isZero y then <> else {}
```
This `Nonzero` is a type function that returns one or another type given a `Natural` value.
For example, `Nonzero 0` returns the void type `<>`, but `Nonzero 10` returns the unit type `{}`.
This definition is straightforward because types and values are treated similarly in Dhall, so it is easy to define a function that returns a type.

We will use that function to implement safe division (`safeDiv`):

```dhall
let safeDiv = λ(x: Natural) → λ(y: Natural) → λ(_: Nonzero y) → unsafeDiv x y
```

The required value of type `Nonzero y` is an "evidence" that the first argument (`y`) is nonzero.

When we use `safeDiv` for dividing by a nonzero value, we specify a third argument of type `{}`.
That argument can have only one value, namely, the empty record, denoted in Dhall by `{=}`.
So, instead of `unsafeDiv 5 2` we now write `safeDiv 5 2 {=}`.

If we try dividing by zero, we will be obliged to pass a third argument of type `<>`, but there are no such values.
Passing an argument of any other type will raise a type error.

```dhall
safeDiv 4 2 {=}  -- Returns 2.

safeDiv 4 0 {=}  -- Type error: wrong type of {=}.
```

In this way, dependently-typed evidence values can enforce constraints at typechecking time.

#### Better error messages for failed assertions

If we write `safeDiv 4 0 {=}`, we get a type error that says "the value `{=}` has type `{}`, but we expected type `<>`".
This message is not particularly helpful.
We can define the dependent type `Nonzero` in a different way, so that the error message shows more clearly why the assertion failed.
For that, we replace the void type `<>` by the equivalent void type of the form `"a" ≡ "b"` where `"a"` and `"b"` are strings that are guaranteed to be different.
Those strings will be printed by Dhall as part of the error message.

To implement this idea, let us replace the definition of `Nonzero` by this code:

```dhall
let Nonzero = λ(y : Natural) →
  if Natural/isZero y
  then "error" ≡ "attempt to divide by zero"
  else {}

let safeDiv = λ(x: Natural) → λ(y: Natural) → λ(_: Nonzero y) → unsafeDiv x y
```

When we evaluate `safeDiv 4 0 {=}`, we now get a good error message:

```dhall
⊢ safeDiv 4 0 {=}

Error: Wrong type of function argument

- "error" ≡ "attempt to divide by zero"
```

Another example is an assertion that a natural number should be less than a given limit.
We implement that assertion via a type constructor `AssertLessThan`.
The error message will be computed as a function of the given arguments:

```dhall
let AssertLessThan = λ(x : Natural) → λ(limit : Natural) →
  if Natural/lessThan x limit
  then {}
  else "error" ≡ "the argument x = ${Natural/show x} must be less than ${Natural/show limit}"
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

- "error" ≡ "the argument x = 200 must be less than 100"
```

This error message clearly describes the problem.

#### Limitations

The main limitation of this technique is that it can work only with statically known values.

For instance, any usage of `safeDiv x y` will require us somehow to obtain an evidence value of type `Nonzero y` that passes typechecking before evaluation begins.
That value serves as evidence that the number `y` is not zero.
Values of type `Nonzero y` can be typechecked only if `y` is a statically known value of type `Natural`.
This is so because the check `Natural/isZero y` is done at typechecking time, before `saveDiv` is evaluated.

What if we need to use `safeDiv` inside a function that takes an argument `y : Natural` and then calls `safeDiv x y`?
That function cannot call `safeDiv x y {=}` because the evidence value `{=}` needs to be typechecked before evaluating any code.
We also cannot test whether `y` is zero at run time and then call `safeDiv` only when `y` is nonzero.
This code:

```dhall
λ(y : Natural) → if Natural/isZero y then 0 else safeDiv 10 y {=} -- ???
```
will give a type error because Dhall cannot verify that `{=}` is of type `Nonzero y`.

Neither can we use the `Optional` type to create a value of type `Optional (Nonzero y)` that will be `None` when `y` equals zero.
Dhall will not accept code like this:

```dhall
λ(y : Natural) → if Natural/isZero y then None (Nonzero y) else (Some {=} : Optional (Nonzero y)) -- Type error: types do not match.
```

Here, Dhall does not recognize that `Nonzero y` is the unit type (`{}`) within the `else` clause.
To recognize that, the interpreter would need to deduce that the condition under `if` is the same as the condition defined in `Nonzero`.
But Dhall's typechecking is insufficiently powerful to handle dependent types in such generality.

So, any function that uses `saveDiv` for dividing by an unknown value `y` will also require an additional evidence argument of type `Nonzero y`.
That argument can be easily provided as `{=}` when calling that function, as long as `y` is a statically  known value.

The advantage of using this technique is that we will guarantee, at typechecking time, that programs will never divide by zero.

### Integer square root

The integer-valued square root of a natural number `n` is the largest natural number `r` such that `r * r <= n`.

A simple algorithm for determining `r` is to start from `1` and increment repeatedly, until the result `r` satisfies `r * r > n`.

As before, Dhall requires is to specify an upper bound on the number of iterations up front.
A safe upper bound is the number  `n` itself.

We will begin with `n` and iterate applying a function `stepUp`.
That function will increment its argument `r` by `1` while checking the condition `r * r <= n`.

The code is:

```dhall
let lessThanEqual = https://prelude.dhall-lang.org/Natural/lessThanEqual
let sqrt = λ(n: Natural) →
  let stepUp = λ(r : Natural) → if (lessThanEqual (r * r) n) then r + 1 else r
  in Natural/subtract 1 (Natural/fold (n + 1) Natural stepUp 1)
let _ = assert : sqrt 15 ≡ 3
let _ = assert : sqrt 16 ≡ 4
```

There are faster algorithms of computing the square root, but those algorithms require division.
Our implementation of division already requires a slow iteration.
So, we will not attempt to optimize the performance of this proof-of-concept code.

### Integer logarithm

The "bit width" (`bitWidth`) of a natural number `n` is the smallest number of binary bits needed to represent `n`.
For example, `bitWidth 3` is `2` because `3` is represented as two binary bits: `0b11`, while `bitWidth 4` is `3` because `4` is `0b100` and requires 3 bits.

To compute this function, we find the smallest natural number `b` such that `2` to the power `b` is larger than `n`. We start with `b = 1` and multiply `b` by `2` as many times as needed until we get a value larger than `n`.

As before, we need to supply an upper bound on the iteration count.
We supply `n` as that bound and make sure that the final result remains constant once we reach it, even if we perform further iterations.

The code is:

```dhall
let bitWidth : Natural → Natural = λ(n : Natural) →
  let Accum = { b : Natural, bitWidth : Natural }
  let init = { b = 1, bitWidth = 0 } -- At all times, b ≡ pow(2, bitWidth).
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
  let Accum = { b : Natural, log : Natural }
  let init = { b = 1, log = 0 } -- At all times, b ≡ pow(base, log).
  let update = λ(acc : Accum) →
     if lessThanEqual acc.b n
     then { b = acc.b * base, log = acc.log + 1 }
     else acc
  let result : Accum = Natural/fold n Accum update init
  in Natural/subtract 1 result.log

let _ = assert : log 10 100 ≡ 2
```

### Greatest common divisor

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

### Floating-point operations

The built-in Dhall type `Double` does not support any numerical operations.
However, one can use values of type `Natural` to implement floating-point arithmetic.
The `scall` repository contains [proof-of-concept code](https://github.com/winitzki/scall/blob/master/tutorial/Float/) implementing some floating-point operations: `Float/create`, `Float/show`, `Float/compare`, `Float/add`, `Float/subtract`, `Float/multiply`, `Float/divide` and so on.
Floating-point numbers are represented by a decimal mantissa and a decimal exponent, and support arbitrary precision (in both mantissa and exponent).
```dhall
let Float/create = (./Float/Type.dhall).Float/create
let Float/divide = ./Float/divide.dhall
let Float/show = ./Float/show.dhall
let x = Float/create +1234 +10000   -- This is 1234 * 10^10000.
let _ = assert : Float/show x ≡ "+1.234e+10003"
let y = Float/create -3 -1          -- This is (-3) * 10^(-1).
let _ = assert : Float/show y ≡ "-0.3"
let z = Float/divide x y 10      -- Compute x / y to 10 digits.
let _ = assert : Float/show z ≡ "-4.113333333e+10003"
```

To illustrate how Dhall can implement an arbitrary-precision numerical algorithm, consider the computation of a floating-point square root.

We will use the following algorithm that computes successive approximations for $x = \sqrt p$, where $p$ is a given non-negative number:

1. Compute the initial approximation $x0$ that is close to $\sqrt p$.

2. Estimate the total number of iterations $n$, where $n \ge 1$.

3. Apply $n$ times the function `update` to $x0$.

The result is the Dhall code `Natural/fold n update x0`.

The initial approximation is defined as follows:


1. Find the largest integer number $k$ such that $p = 10^{2k} q$ and $q \ge 1$. Then we will have $1 \le q < 100$.

2.  The initial approximation to $\sqrt p$ will be some number $10^{k} r$ where $1 \le r < 10$. To find $r$, we use a table lookup based on $q$.

The initial value is chosen such that the number of correct decimal digits doubles after each update.
The first iteration gives 2 correct digits, the second 4 digits, the third 8 digits, etc.
The total number of iterations is set to $n = 1 + \log N$ (the logarithm is in base 2).
```dhall
let Float/sqrt = λ(p : Float) → λ(prec : Natural) →
  let iterations = 1 + (./numerics.dhall).log 2 prec
  let init : Float = ??? -- Code omitted for brevity.
  let update = λ(x : Float) → Float/multiply (Float/add x (Float/divide p x prec) prec) (T.Float/create +5 -1) prec
  in Natural/fold iterations Float update init
```
This code is shown for illustration only! A fully tested version of `Float/sqrt.dhall` is found [in the source code repository](https://github.com/winitzki/scall/blob/master/tutorial/Float/sqrt.dhall).

## Programming with functions

### Identity functions

We have already seen the code for the polymorphic identity function:

```dhall
let identity : ∀(a : Type) → a → a
  = λ(a : Type) → λ(x : a) → x
```

This function simply returns its argument:

```dhall
⊢ identity Natural 123

123
```

We may apply `identity` to a value of any type, say, to a function value:

```dhall
⊢ identity (Natural → Natural) (λ(x : Natural) → x + 1)

λ(x : Natural) → x + 1
```

This works even if the argument has type parameters.
For instance, we can apply `identity` to itself and get the same function as a result:

```dhall
⊢ identity (∀(a : Type) → a → a) identity

λ(a : Type) → λ(x : a) → x
```

#### Identity functions for types and kinds

What if we wanted the identity function to be able to work on _types_ themselves?
We expect some code like `identityT Bool ≡ Bool`.

Note that the type of `Bool` is `Type`.
So, a simple implementation of `identityT` is:

```dhall
let identityT = λ(t : Type) → t
```

This function will work on simple types (such as `Bool`) but not on type constructors such as `List`, because the type of `List` is not `Type` but `Type → Type`.
We would like to make `identityT` sufficiently polymorphic so that it could accept arbitrary type constructors as arguments.
For instance, it should accept arguments of type `Type`, or `Type → Type`, or `Type → Type → Type`, or `(Type → Type) → Type`, and so on.

The type of all those type expressions is `Kind`.
So, we add an argument of type `Kind` to describe the "kinds" of all possible arguments.

The Dhall code is:

```dhall
let identityK = λ(k : Kind) → λ(t : k) → t
```
Here, `t` is anything that has type `k`, while `k` is a kind that could be `Type`, or `Type → Type`, etc., because the only constraint is `k : Kind`.

Now we can test this function on various inputs:

```dhall
⊢ identityK Type Bool

Bool

⊢ identityK (Type → Type) List

List
```

#### No support for kind-polymorphic functions

We implemented different functions (`identity`, `identityT`, `identityK`) that accept arguments of specific kinds.
What if we wanted to implement an identity function that supports arguments of arbitrary kinds?

Dhall does not support function arguments whose type is of unknown kind.
To see why this does not work, consider this attempt to define a "fully general" function `identityX`:

```dhall
 -- Type error: invalid function input.
let identityX = λ(k : Kind) → λ(t : k) → λ(x : t) → x
```
We would like `identityX` to accept any `Kind`, `Type`, and value.
But Dhall rejects this code with an error message: "Invalid function input, `λ(x : t) → x`".

With the option `--explain`, Dhall gives more details about what is wrong:

```bash
$ echo "λ(k : Kind) → λ(t : k) → λ(x : t) → x" | dhall --explain

...
You annotated a function input with the following expression:

↳ t

... which is neither a type nor a kind

────────────────────────────────────────────────────────────────────────────────

1│                                λ(x : t) → x
```
This error message still needs some explanation.

We must keep in mind that any function's input type and output type must be types that can be used to annotate values.

Examples of types that can be used to annotate values are: `Natural`, `List Bool`, `Type`, `Type → Type`, and `Kind`.
Indeed, we can use each of those types to annotate some values, for instance:

`0 : Natural`

`[ True ] : List Bool`

`Text : Type`

`List : Type → Type`

`Type → Type : Kind`

So, it is valid to write `λ(x : List Bool) → ...` or `λ(x : Type) → ...`.

An example of a type that _cannot_ be used to annotate values is `List`.
It is a type _constructor_, that is, a function from `Type` to `Type`. (The type of `List` is `Type → Type`.)
There are no Dhall expressions `x` such that `x : List` is a valid type annotation.
So, the function code `λ(x : List) → ...` is invalid.
The input type of that function (`List`) is not a type that can ever annotate anything.

In the example shown above, the expression causing a type error is the inner function `λ(x : t) → x`.
The input type of that function is designated as `t`.
So, Dhall requires `t` to be a type that can annotate values.
But all we know in our case is that `t` has type `k`, and that `k` is something of type `Kind`.
It is not guaranteed that `k` is `Type`.
For example, `k` could be `Type → Type`, while `t` could be `List`.
This would be the case if we applied `identityX` like this:

```dhall
identityX (Type → Type) List  -- ???
```
Then the type annotations `k : Kind` and `t : k` would both match.

However, it will not be valid in that case to write `x : t`, that is, `x : List`,
because `List` is a type _constructor_.

So, the error is that `t` is not a type that can be used to annotate a value.
Dhall indicates such situations by the error message "Invalid function input".

A more formal condition is that a type `X` "can be used for annotating a value" if we have `X : Type`, or `X : Kind`, or `X : Sort`.
So, Dhall functions of the form `λ(X : Kind) → λ(Y : X) → body` may not use `Y` in any type annotations within `body`.
The argument `Y` may only be used as part of the function's return value.

Dhall functions of the form `λ(X : Type) → body` or `λ(X : Kind) → body` _may_ use `X` in type annotations within `body`.

### Function combinators

The standard combinators for functions are forward and backward composition, currying / uncurrying, argument flipping, and constant functions.

Implementing these combinators in Dhall is straightforward.
Instead of pairs, we use the record type `{ _1 : a, _2 : b }`.

```dhall
let compose_forward
 : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (a → b) → (b → c) → (a → c)
  = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : a → b) → λ(g : b → c) → λ(x : a) →
    g (f (x))

let compose_backward
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

let const
  : ∀(a : Type) → ∀(b : Type) → b → a → b
  = λ(a : Type) → λ(b : Type) → λ(x : b) → λ(_ : a) → x
```

The function `const` creates constant functions and is used like this:

```dhall
⊢ :let f = const Natural Text "abc"

f : Natural → Text

⊢ f 0

"abc"

⊢ f 123

"abc"
```
Here, we used `const` to define a constant function `f` that always returns the string `"abc"` and ignores its argument (of type `Natural`).

Similar combinators can be defined for types instead of values.
Because Dhall does not support polymorphism by kinds, that code needs to be written separately for each kind of types.

For example, suppose we need a constant function that takes any _type constructor_ as argument (whose type is `Type → Type`) and returns a fixed type `Natural`, ignoring the argument.
This function can be created as `ConstKT (Type → Type) Natural`, where `ConstKT` is defined by:

```dhall
let ConstKT
  : ∀(a : Kind) → ∀(b : Type) → a → Type
  = λ(a : Kind) → λ(b : Type) → λ(_ : a) → b
let f = ConstKT (Type → Type) Natural
```
The type of `f` is `(Type → Type) → Type`.

### Verifying laws symbolically with `assert`

The function combinators from the previous subsection obey a number of algebraic laws.
In most programming languages, such laws may be verified only through testing.
Dhall's `assert` feature may be used to verify certain laws rigorously.

A simple example of a law is the basic property of any constant function: the function's output is independent of its input.
We can formulate that law by saying that a constant function `f` should satisfy the equation `f x ≡ f y` for all `x` and `y` of a suitable type.

```dhall
let f : Natural → Text = λ(_ : Natural) → "abc"
let f_const_law = λ(x : Natural) → λ(y : Natural) → assert : f x ≡ f y
```

Dhall can determine that `f x ≡ f y` even though `x` and `y` are unknown, because it is able to evaluate `f x` and `f y` _symbolically_ within the body of `f_const_law`.
Dhall's interpreter can simplify expressions inside function bodies.
So, an `assert` within a function body will verify that the equation holds for all possible function arguments.

Let us verify that the same law holds for any functions created via `const`:

```dhall
let const_law = λ(a : Type) → λ(b : Type) → λ(c : b) → λ(x : a) → λ(y : a) →
  assert : const a b c x ≡ const a b c y
```

The Dhall interpreter will substitute the definition of `const` into that code and perform some evaluation, even though that code is under several layers of λ and arguments `a`, `b`, `c`, `x`, `y` have not yet been given.
In this way, Dhall will be able to verify that the `assert` expression is valid.

To see how this works in detail, begin by evaluating `const a b c x` within the body of the function `const_law`.

Using the definition of `const`, Dhall will reduce `const a b c x` to just `c`.
Also, `const a b c y` will be reduced to just `c`.
There cannot be any further evaluation: the value `c` is unknown within the body of the function since `c` is a parameter.
In other words, the normal form of `c` in that scope is just the symbol `c` itself.
So, Dhall obtains an `assert` expression of the form `assert : c ≡ c`.
Because the normal forms are equal, the `assert` expression is accepted as valid.
In this way, the function `const_law` is typechecked as valid.
This verifies the law.

Another example of a law is the identity law of `flip`: If we "flip" a curried function's arguments twice, we will recover the original function.

The Dhall code for verifying the law is:

```dhall
let verify_flip_flip_law = λ(a : Type) → λ(b : Type) → λ(c : Type) →
  λ(k : a → b → c) →
    assert : flip b a c (flip a b c k) ≡ k
```

Let us see what happens when this code is typechecked.
Dhall will compute the normal forms for both sides of the given assertion and check whether those normal forms are equal.
The normal forms will be computed _inside_ the function's body (that is, under several layers of λ).

To validate the assertion, Dhall first computes the normal form of `flip a b c k`.
In that scope, the parameters `a`, `b`, `c`, `k` are not yet assigned, so their normal forms are just those parameters themselves, as free variables.
So, the normal form of `flip a b c k` is the expression `λ(x : b) → λ(y : a) → k y x`.
There is no further simplification that can be applied at that stage.

Then Dhall computes the normal form of the left-hand side of the assertion:

```dhall
flip b a c (flip a b c k)  -- Symbolic derivation. This is what Dhall does internally.
  ≡ flip b a c (λ(x : b) → λ(y : a) → k y x)
  ≡ λ(xx : a) → λ(yy : b) → (λ(x : b) → λ(y : a) → k y x) yy xx
  ≡ λ(xx : a) → λ(yy : b) → k xx yy
```

The right-hand side of the assertion is the function `k`.
The expression `λ(xx : a) → λ(yy : b) → k xx yy` is just an expanded form of the same function `k`.
So, both sides of the assertion are equal.

Here, Dhall verifies the equivalence of symbolic expression terms such as `λ(xx : a) → λ(yy : b) → k xx yy`.
This code does not substitute any specific values of `xx` or `yy`, nor does it select a specific function `k` for the `assert` test.
The `assert` verifies that both sides are equal "symbolically" (that is, equal as unevaluated code expressions with free variables).
This check is equivalent to a rigorous mathematical proof that the law holds.

To see more examples, let us verify the laws of function composition:

```dhall
let compose_backward
  : ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (b → c) → (a → b) → a → c
  = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : b → c) → λ(g : a → b) → λ(x : a) →
    f (g (x))

  -- The identity laws.
let left_id_law = λ(a : Type) → λ(b : Type) → λ(f : a → b) →
  assert : compose_backward a a b f (identity a) ≡ f
let right_id_law = λ(a : Type) → λ(b : Type) → λ(f : a → b) →
  assert : compose_backward a b b (identity b) f ≡ f

  -- The constant function composition law.
let const_law = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(x : c) → λ(f : a → b) →
  compose_backward a b c (const b c x) f ≡ const a c x

  -- The associativity law.
let assoc_law = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(d : Type) → λ(f : a → b) → λ(g : b → c) → λ(h : c → d) →
  assert : compose_backward a b d (compose_backward b c d h g) f
         ≡ compose_backward a c d h (compose_backward a b c g f)
```

In the Haskell syntax, these laws look like this:

```haskell
-- Symbolic derivation.
f . id == f                  -- Left identity law
id . f == f                  -- Right identity law.
(const x) . f == const x     -- Constant function law.
(h . g) . f == h . (g . f)   -- Associativity law.
```

Using `assert` and functions with type parameters, one can verify a wide range of algebraic laws.
We will show some more examples later in this book.

### Pair products and pair co-products of functions

The **pair product** operation takes two functions `f : a → b` and `g : c → d` and returns a new function of type `Pair a c → Pair b d`.

We define the type constructor `Pair` and the pair product operation `fProduct` as:

```dhall
let Pair = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }

let fProduct : ∀(a : Type) → ∀(b : Type) → (a → b) → ∀(c : Type) → ∀(d : Type) → (c → d) → Pair a c → Pair b d
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(c : Type) → λ(d : Type) → λ(g : c → d) → λ(arg : Pair a c) →
    { _1 = f arg._1, _2 = g arg._2 }
```

The **pair co-product** operation takes two functions `f : a → b` and `g : c → d` and returns a new function of type `Either a c → Either b d`.

```dhall
let Either = λ(a : Type) → λ(b : Type) → < Left : a | Right : b >

let fCoProduct : ∀(a : Type) → ∀(b : Type) → (a → b) → ∀(c : Type) → ∀(d : Type) → (c → d) → Either a c → Either b d
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(c : Type) → λ(d : Type) → λ(g : c → d) → λ(arg : Either a c) →
    merge {
           Left = λ(x : a) → (Either b d).Left (f x),
           Right = λ(y : c) → (Either b d).Right (g y),
          } arg
```


## Typeclasses

Typeclasses can be implemented in Dhall via evidence values.
Those values are passed as additional arguments to functions that require a typeclass constraint.

With that technique, one can define and use different typeclass evidence values for the same type, if that is necessary.
This is similar to the way Scala implements typeclasses (except Scala makes evidence values into "implicit" arguments that the compiler inserts automatically).

### Instances and evidence values

Let us first clarify the terminology used with typeclasses, beginning with a definition that is sufficient for the purposes of this book:

###### Definition

A **typeclass** is a type constructor `P : Type → Type` together with some equations ("typeclass laws") that values of type `P t` must satisfy (for all `t : Type`).
A **typeclass instance** is a type `t` together with a value of type `P t` that satisfies those laws.
That value is called an **evidence value** for the typeclass membership of `t`.
We say that a type `t` "has an instance" of the typeclass (or "is an instance" of the typeclass) if we are able to compute an evidence value of type `P t`.
We say that we have "computed a typeclass instance for `t`" if we have computed an evidence value.
We say that a function having an argument of type `t` "imposes a **typeclass constraint** on `t`" if that function has another argument of type `P t` (the evidence value) and assumes that the evidence value will satisfy the typeclass laws. $\square$

To illustrate this definition, let us give an example using the `Semigroup` typeclass.
Below we will look at other typeclasses more systematically.

###### Example: The `Semigroup` typeclass

A set is called a "semigroup" if there is a binary associative operation for it.
In the language of types, a type `t` is a semigroup if there is a function `append: t → t → t` satisfying the associativity law: for all `x`, `y`, `z` of type `t`, we must have:

`append x (append y z) = append (append x y) z`

To express this property as a typeclass, we introduce a type constructor called `Semigroup`:

```dhall
let Semigroup = λ(t : Type) → { append : t → t → t }
```
For example, `Natural` is a type that can be an instance of `Semigroup`.
A `Semigroup` typeclass evidence value for the type `Natural` is a value of type `Semigroup Natural` that satisfies the associativity law.
Here are some examples of `Semigroup` evidence values:
```dhall
let semigroupNatural : Semigroup Natural = { append = λ(x : Natural) → λ(y : Natural) → x * y }
let semigroupBoolToBool : Semigroup (Bool → Bool) = { append = compose_forward Bool Bool Bool }
```

The type `Natural`  is an instance of `Semigroup` because the associativity law holds for the evidence value `semigroupNatural`.

The associativity law can be expressed using Dhall's equality types like this:
```dhall
let semigroup_law = λ(t : Type) → λ(ev : Semigroup t) →
  λ(x : t) → λ(y : t) → λ(z : t) →
    ev.append x (ev.append y z) ≡ ev.append (ev.append x y) z
```

Dhall's typechecker is able to validate the associativity law for the evidence value `semigroupBoolToBool` (but not for `semigroupNatural`):

```dhall
let _ = λ(x : Bool → Bool) → λ(y : Bool → Bool) → λ(z : Bool → Bool) →
  assert : semigroup_law (Bool → Bool) semigroupBoolToBool x y z
```
A value of type `semigroup_law (Bool → Bool) semigroupBoolToBool` can be seen as a symbolic proof that the `Semigroup` typeclass instance for `Bool → Bool` satisfies the associativity law.

The typeclass laws are an essential part of the definition of a typeclass, because program correctness often implicitly depends on those laws.
So, a more rigorous definition of "typeclass instance for `t`" is a _pair_ consisting of an evidence value `ev : P t` together with a value of the equality type `semigroup_law t ev`.

This is not a simple pair of values, because the _type_ of the second value depends on the first _value_.
This sort of type is called a **dependent pair**, and we will study it in the chapter "Church encodings for more complicated types".

Most programming languages do not support dependent pairs.
Dhall can encode their type (see the chapter "Church encodings for more complicated types" below) but provides only quite limited ways of working with dependent pairs.

For this reason, one usually makes the pragmatic decision to represent typeclass instances only via evidence values (of type `P t`) and omit the equality type evidence altogether, relying on the programmer to validate the laws (e.g., by writing a proof by hand).

Below we will construct evidence values and only occasionally try a symbolic validation of the laws.

As Dhall does not support implicit arguments, all typeclass evidence values must be defined and passed to functions explicitly.

Dhall's `assert` feature may be sometimes used to verify typeclass laws.

To see how this works, let us implement some well-known typeclasses in Dhall.

### The "Show" typeclass

The `Show` typeclass is usually defined in Haskell as:

```haskell
class Show t where     -- Haskell.
  show :: t -> String
```

In Scala, a corresponding definition is:

```scala
trait Show[T] {           // Scala.
 def show(m: T) => String
}
```

A type `T` belongs to the `Show` typeclass if we can compute a printable representation of any given value of type `T`.

To implement this typeclass in Dhall, we first define a type that holds suitable evidence values.
In the case of the `Show` typeclass, an evidence value for a type `t` is just a function of type `t → Text`.

```dhall
let Show = λ(t : Type) → { show : t → Text }
```

As an example of a function with a type parameter and a `Show` typeclass constraint, consider a function that prints a list of values together with some other message.
For that, we would write the following Haskell code:
```haskell
import Data.List (intercalate)           -- Haskell.
printWithPrefix :: Show a => String -> [a] -> String
printWithPrefix message xs = message ++ intercalate ", " (fmap show xs)
```
In Scala, we would write:

```scala
def printWithPrefix[A](message: String, xs: Seq[A])(implicit showA: Show[A]): String =
  message + xs.map(showA.show).mkString(", ")
```
The corresponding Dhall code is:
```dhall
let Text/concatMapSep = https://prelude.dhall-lang.org/Text/concatMapSep
let printWithPrefix : ∀(a : Type) → Show a → Text → List a → Text
  = λ(a : Type) → λ(showA : Show a) → λ(message : Text) → λ(xs : List a) →
    message ++ Text/concatMapSep ", " a showA.show xs
```
To test this code, let us print a list containing values of a record type `{ user : Text, id : Natural }`.
First, we define a `Show` evidence value for that type:
```dhall
let UserWithId = { user : Text, id : Natural }
let showUserWithId : Show UserWithId = { show = λ(r : UserWithId) → "user ${r.user} with id ${Natural/show r.id}" }
```
Then we can  use `printWithPrefix` to print a list of values of that type:
```dhall
let users : List UserWithId = [ { user = "a", id = 1 }, { user = "b", id = 2 } ]
let printed = printWithPrefix UserWithId showUserWithId "users: " users
let _ = assert : printed ≡ "users: user a with id 1, user b with id 2"
```

Using Dhall's built-in functions `Natural/show`, `Double/show`, etc., we could define `Show` typeclass evidence values for the built-in types.
The function `printWithPrefix` could then be used with lists of types `List Natural`, `List Double`, etc.

###  The "Eq" typeclass

The `Eq` typeclass provides a method for checking if two values of a type `t` are equal.

We say that a type `t` belongs to the `Eq` typeclass if there is a function `equal : t → t → Bool` that returns `True` if and only if the two arguments of type `t` are equal.
The function `equal` must satisfy the usual laws of a equality relation: reflexivity, symmetry, transitivity, and extensional identity.
- Reflexivity: for all `x : t` we must have `equal x x ≡ True`. It says that any value should be equal to itself.
- Symmetry: for all `x : t` and `y : t` we must have `equal x y ≡ equal y x`. It says that equality is symmetric.
- Transitivity: for all `x : t`, `y : t`, and `z : t`, if `equal x y ≡ True` and `equal y z ≡ True` then we must have `equal x z ≡ True`.
- Extensional identity: for all `t : Type` and `u : Type`, and for all `x : t`, `y : t`, `f : t → u`, if both `t` and `u` are instances of `Eq`, and if `equal x y ≡ True`, then we must have `equal (f x) (f y) ≡ True`. It says that for any `x` and `y` that are equal, any function `f` will also compute equal results `f x` and `f y`.

The type constructor for the `Eq` typeclass can be defined like this:
```dhall
let Eq = λ(t : Type) → { equal : t → t → Bool}
```

In Dhall, only values of type `Bool`, `Integer`, and `Natural` can be compared for equality.
Let us write the corresponding evidence values:

```dhall
let Bool/equal = https://prelude.dhall-lang.org/Bool/equal
let eqBool: Eq Bool = { equal = Bool/equal }
let Integer/equal = https://prelude.dhall-lang.org/Integer/equal
let eqInteger: Eq Integer = { equal = Integer/equal }
let Natural/equal = https://prelude.dhall-lang.org/Natural/equal
let eqNatural: Eq Natural = { equal = Natural/equal }
```
These evidence values satisfy all the required laws.

We can then implement `Eq` instances for record types or union types that are constructed from `Bool`, `Integer`, `Natural`, and other types for which `Eq` instances are given.
To illustrate how that works in general, let us implement combinators that compute `Eq` instances for `Pair a b` and for `Either a b` from given `Eq` instances for arbitrary types `a` and `b`.
```dhall
let eqPair : ∀(a : Type) → Eq a → ∀(b : Type) → Eq b → Eq (Pair a b)
  = λ(a : Type) → λ(eq_a : Eq a) → λ(b : Type) → λ(eq_b : Eq b) →
    { equal = λ(x : Pair a b) → λ(y : Pair a b) → eq_a.equal x._1 y._1 && eq_b.equal x._2 y._2 }

let eqEither : ∀(a : Type) → Eq a → ∀(b : Type) → Eq b → Eq (Either a b)
  = λ(a : Type) → λ(eq_a : Eq a) → λ(b : Type) → λ(eq_b : Eq b) →
    { equal = λ(x : Either a b) → λ(y : Either a b) →
      merge { Left = λ(xa : a) →
              merge { Left = λ(ya : a) → eq_a.equal xa ya
                    , Right = λ(_ : b) → False
                    } y
            , Right = λ(xb : b) →
              merge { Left = λ(_ : a) → False
                    , Right = λ(yb : b) → eq_b.equal xb yb
                    } y
            } x
    }
```
These combinators perform **typeclass derivation**: a typeclass instance for a more complicated type is derived from given typeclass instances for simpler types.
When `Eq` instances are derived using `eqPair` and `eqEither`, it is guaranteed that the laws will all hold (assuming that those laws already hold for the simpler types).

Let us also show examples of `Eq` evidence values that violate some of the laws.

The first example uses a record as the type `t`.
The comparison operation will only compare the first field of the record and ignore the second one:
```dhall
let Record2 : Type = { n : Natural, label : Text }
let eqRecord2: Eq Record2 = { equal = λ(x : Record2) → λ(y : Record2) → Natural/equal x.n y.n }
```

This operation violates the extensional identity law: there are some functions `f` that give unequal results for records that are supposedly equal according to this typeclass instance.
That will be the case for any function that uses the `label` field of the record, for instance:
```dhall
let f : Record2 → Text = λ(x : Record2) → x.label ++ Natural/show x.n
```

The second example uses the union type `Either Bool Text`.
The comparison operation compares the two `Bool` values but refuses to compare `Text` values with anything.
It returns `False` whenever one of the arguments is a `Text` value.
```dhall
let Union2 : Type = Either Bool Text
let eqUnion2 : Eq Union2 = { equal = λ(x : Union2) → λ(y : Union2) →
  merge { Right = λ(_ : Text) → False
        , Left = λ(bx : Bool) →
          merge { Right = λ(_ : Text) → False
                , Left = λ(by : Bool) → Bool/equal bx by
          } y
        } x
}
```
This operation violates the reflexivity law: values such as `Union2.Right "abc"` are not equal to themselves.

### Monoids and semigroups

The `Monoid` typeclass is usually defined in Haskell as:

```haskell
-- Haskell.
class Monoid m where
  mempty :: m
  mappend :: m -> m -> m
```
The values `mempty` and `mappend` are the **typeclass methods** of the `Monoid` typeclass.

In Scala, a corresponding definition is:

```scala
// Scala
trait Monoid[M] {
 def empty: M
 def combine: (M, M) => M
}
```
Here, the `Monoid` typeclass methods are called `empty` and `combine`.

We see that an evidence value of type `Monoid m` needs to contain a value of type `m` and a function of type `m → m → m`.
A Dhall record type describing those values could be written as `{ empty : m, append : m → m → m }`.
A value of that type provides evidence that the type `m` belongs to the `Monoid` typeclass.

To use the typeclass more easily, we define a type constructor `Monoid` such that the above record type is obtained as `Monoid m`:
```dhall
let Monoid = λ(m : Type) → { empty : m, append : m → m → m }
```
With this definition, `Monoid Bool` is the type `{ mempty : Bool, append : Bool → Bool → Bool }`.
Values of that type are evidence values showing that the type `Bool` belongs to the `Monoid` typeclass.
We also say that values of that type "provide `Monoid` typeclass instances" to the `Bool` type.

Let us write some specific `Monoid` instances for the types `Bool`, `Natural`, `Text`, and `List`:
```dhall
let monoidBool : Monoid Bool
  = { empty = True, append = λ(x : Bool) → λ(y : Bool) → x && y }
let monoidNatural : Monoid Natural
  = { empty = 0, append = λ(x : Natural) → λ(y : Natural) → x + y }
let monoidText : Monoid Text
  = { empty = "", append = λ(x : Text) → λ(y : Text) → x ++ y }
let monoidList : ∀(a : Type) → Monoid (List a)
  = λ(a : Type) → { empty = [] : List a, append = λ(x : List a) → λ(y : List a) → x # y }
```

The `Monoid` instances shown above are not the only ones possible.
For example, one could implement a `Monoid` instance for `Bool` using the "or" operation (`||`) instead of the "and" operation (`&&`).
A `Monoid` instance for `Natural` could use the multiplication (`*`) instead of the addition (`+`).
The specific implementation of `Monoid` should be chosen according to the needs of a specific application.

A **semigroup** is a weaker typeclass that has the `append` method like a monoid, but without the `empty` method.

```dhall
let Semigroup = λ(m : Type) → { append : m → m → m }
```

Any monoid is a semigroup, but not all semigroups are monoids, because the `empty` method cannot be defined for certain types.

### Functions with typeclass constraints

The main use of typeclasses is for implementing functions with a type parameter constrained to belong to a given typeclass.
To implement such functions, we add an argument that requires a typeclass evidence value.

Let us implement some functions with a type parameter required to belong to the `Monoid` typeclass.
Examples are the standard functions `reduce` and `foldMap` for `List`, written in the Haskell syntax as:

```haskell
-- Haskell.
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

Typeclass constraints for other typeclasses are implemented in Dhall via code similar to this.
Functions with typeclass constraints will have type signatures of the form `∀(t : Type) → SomeTypeclass t → ...`.
When calling those functions, the programmer will have to pass evidence values proving that the type parameters are set to types belonging to the specified typeclass.

### Verifying the laws of monoids

In some cases, Dhall's `assert` feature is able to verify typeclass laws symbolically.

The `Monoid` typeclass has three laws: two identity laws and one associativity law.
We can write `assert` expressions that verify those laws for any given evidence value of type `Monoid a`.

First, we implement a function that creates the required equality types:

```dhall
let monoidLaws = λ(m : Type) → λ(monoid_m : Monoid m) → λ(x : m) → λ(y : m) → λ(z : m) →
  let plus = monoid_m.append
  let e = monoid_m.empty
  in {
        monoid_left_id_law = plus e x ≡ x,
        monoid_right_id_law = plus x e ≡ x,
        monoid_assoc_law = plus x (plus y z) ≡ plus (plus x y) z,
       }
```
Note that we did not add `assert` expressions here.
If we did, the assertions would have always failed because the body of `monoidLaws` cannot yet substitute a specific implementation of `monoid_m` to check whether the laws hold.
For instance, the expressions `plus e x` and `x` are always going to be different _while typechecking the body of that function_, which happens before that function is ever applied.
Those expressions will become the same only after we apply `monoidLaws` to a type `m` and to a lawful instance of a `Monoid` typeclass for `m` that provides code for `append` and `empty`.

To check the laws, we will write `assert` values corresponding to each law.

As an example, here is how to check the monoid laws for the evidence value `monoidBool` defined above:

```dhall
let check_monoidBool_left_id_law = λ(x : Bool) → λ(y : Bool) → λ(z : Bool) →
  assert : (monoidLaws Bool monoidBool x y z).monoid_left_id_law
```

The symbolic law checking works only in sufficiently simple cases. For instance, the current version of Dhall cannot establish and equivalence between expressions `x + (y + z)` and `(x + y) + z` when `x`, `y`, `z` are free variables of type `Natural`.
So, it is not possible to check the associativity law for `monoidNatural`.


### The `Functor` typeclass

In the jargon of the functional programming community, a **functor** is a type constructor `F` with an `fmap` method having the standard type signature and obeying the functor laws.

Those type constructors are also called **covariant** functors.
For type constructors, "covariant" means "has a lawful `fmap` method".

Note that this definition of "covariant" does not depend on the concept of subtyping.
One can decide whether a type constructor is covariant just by looking at its type expression.

The intuition behind "covariant functors" is that they represent data structures or "data containers" that can store (zero or more) data items of any given type.

We will call covariant functors just "functors" for short.

A simple example of a functor is a record with two values of type `A` and a value of a fixed type `Bool`.
The `fmap` method transforms the data items of type `A` into data items of another type but keeps the `Bool` value unchanged.

In Haskell, that type constructor and its `fmap` method are defined by:
```haskell
data F a = F a a Bool                -- Haskell.
fmap :: (a → b) → F a → F b
fmap f (F x y t) = F (f x) (F y) t
```
In Scala, the equivalent code is:
```scala
case class F[A](x: A, y: A, t: Boolean)         // Scala.

def fmap[A, B](f: A => B)(fa: F[A]): F[B] = F(f(fa.x), f(fa.y), fa.t)
```
The corresponding Dhall code is:
```dhall
let F : Type → Type
  = λ(a : Type) → { x : a, y : a, t : Bool }
let fmap_F
 : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fa : F a) →
    { x = f fa.x, y = f fa.y, t = fa.t }
```
To test:
```dhall
let example : F Natural = { x = 1, y = 2, t = True }
let after_fmap : F Text = fmap_F Natural Text (λ(x : Natural) → if Natural/even x then "even" else "odd") example
let test = assert : after_fmap ≡ { x = "odd", y = "even", t = True }
```

For convenience, let us define the standard type signature of `fmap` as a type constructor:

```dhall
let FmapT = λ(F : Type → Type) → ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b
```
Then we can write code more concisely as `let fmap_F : FmapT F = ???`.

As another example of defining `fmap`, consider a type constructor that involves a union type:
```dhall
let G : Type → Type = λ(a : Type) → < Left : Text | Right : a >
```
The `fmap` method for `G` is implemented as:
```dhall
let fmap_G : FmapT G
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(ga : G a) →
    merge { Left = λ(t : Text) → (G b).Left t
          , Right = λ(x : a) → (G b).Right (f x)
          } ga
```

The `Functor` typeclass is a constraint for a _type constructor_.
We say that a type constructor `F` is a functor if we can compute an evidence value of type `Functor F` that satisfies the functor laws.
So, the type parameter of `Functor` must be of the kind `Type → Type`.

The required data for an evidence value is a `fmap` method for that type constructor.
Let us now package that information into a `Functor` typeclass similarly to how we did with `Monoid`.

Define the type constructor for evidence values:

```dhall
let Functor = λ(F : Type → Type) → { fmap : FmapT F }
```

Here is a `Functor` evidence values for `List` and `Optional`.
The required `fmap` methods are already available in the Dhall prelude:

```dhall
let functorList : Functor List = { fmap = https://prelude.dhall-lang.org/List/map }
let functorOptional : Functor Optional = { fmap = https://prelude.dhall-lang.org/Optional/map }
```

Using the functions `fmap_F` and `fmap_G` shown above, we may write `Functor` evidence values for the type constructors `F` and `G` as:

```dhall
let functorF : Functor F = { fmap = fmap_F }
let functorG : Functor G = { fmap = fmap_G }
```

It turns out that the code for `fmap` can be derived mechanically from the type definition of a functor.
The Haskell compiler will do that if the programmer just writes `deriving Functor` after the definition.
But Dhall does not support any such metaprogramming facilities.
The code of `fmap` must be written by hand in Dhall programs.

###### Example: a function with a typeclass constraint

Implement a function `inject1` that (for any types `a` and `b` and for any functor `F`) converts a value of type `Pair a (F b)` into a value of type `F (Pair a b)`.
Tthe type constructor `Pair` has been defined above as `Pair a b = { _1 : a, _2 : b }`.

####### Solution

The type signature of the function `inject1` must include the given type constructor `F` and a functor evidence value for it:

```dhall
let inject1_t = ∀(F : Type → Type) → ∀(functorF : Functor F) → ∀(a : Type) → ∀(b : Type) → Pair a (F b) → F (Pair a b)
```

The implementation is based on the idea that, given a fixed value `x : a`, we can write a function of type `b → Pair a b`.
Then can we use the functor property of `F` and lift that function to the type `F b → F (Pair a b)`.
This is almost all we need. The complete code is:
```dhall
let inject1 : inject1_t
  = λ(F : Type → Type) → λ(functorF : Functor F) →
    λ(a : Type) → λ(b : Type) → λ(p : Pair a (F b)) →
      let x : a = p._1
      let fb : F b = p._2
      in functorF.fmap b (Pair a b) (λ(y : b) → { _1 = x, _2 = y }) fb
```

###### Exercises

The following functions need to work in the same way for any types `a` and `b` and for any functor `F`.

1. Implement a function `narrow1` that converts a value of type `Pair a (F < Left : a | Right : b >)` into a value of type `F a`.

2. Implement a function `unzip` that converts a value of type `F (Pair a b)` into a value of type `Pair (F a) (F b)`.

3. Implement a function `widen1` that converts a value of type `F a` into a value of type  `F < Left : a | Right : b >`.

4. Implement a function `expand` that converts a value of type `< Left : F a | Right : F b >` into a value of type  `F < Left : a | Right : b >`.

### Verifying the laws of functors

A functor's `fmap` method must satisfy the identity and the composition laws.
In the Haskell syntax, these laws are (informally) written as:

```haskell
 fmap id == id    -- Identity law
 fmap (f . g) == (fmap f) . (fmap g)   -- Composition law.
```

Given a specific type constructor `F` and its `Functor` typeclass evidence, the following function will set up the equality types for `F`'s functor laws:

```dhall
let functorLaws = λ(F : Type → Type) → λ(functor_F : Functor F) →
  λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : a → b) → λ(g : b → c) →
    let fmap = functor_F.fmap
    in { functor_id_law = fmap a a (identity a) ≡ identity (F a)
       , functor_comp_law =
           let fg = compose_forward a b c f g
           let fmap_f = fmap a b f
           let fmap_g = fmap b c g
           let fmapf_fmapg = compose_forward (F a) (F b) (F c) fmap_f fmap_g
           in fmap a c fg ≡ fmapf_fmapg
       }
```

To verify the functor laws for a specific type, we need to write `assert` expressions for each of the laws separately.

As an example, consider the type constructor `F` from the previous section:

```dhall
let F : Type → Type
  = λ(a : Type) → { x : a, y : a, t : Bool }
let functorF : Functor F = { fmap = λ(A : Type) → λ(B : Type) → λ(f : A → B) → λ(fa : F A) →
    { x = f fa.x, y = f fa.y, t = fa.t }
  }
let check_functor_laws = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : a → b) → λ(g : b → c) →
  let composition_law = assert : (functorLaws F functorF a b c f g).functor_comp_law
  let identity_law = assert : (functorLaws F functorF a b c f g).functor_id_law
  in True -- Type error: assertion failed.
```

The composition law is verified successfully.
However, the `identity_law` fails:

```dhall

You tried to assert that this expression:

↳ λ(fa : { t : Bool, x : a, y : a }) → { t = fa.t, x = fa.x, y = fa.y }

... is the same as this other expression:

↳ λ(x : { t : Bool, x : a, y : a }) → x

... but they differ
```

Dhall's algorithm for normal forms does not recognize that the record `{ t = fa.t, x = fa.x, y = fa.y }` is the same as `fa`.

To get around this limitation, write the identity law separately like this:

```dhall
let identity_law_of_F = λ(a : Type) →
    let id_F = λ(fa : { t : Bool, x : a, y : a }) → { t = fa.t, x = fa.x, y = fa.y }
    in assert : functorF.fmap a a (identity a) ≡ id_F
```

Let us also try verifying the functor laws for the type constructor `G` from the previous section:

```dhall
let functor_laws_of_G = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : a → b) → λ(g : b → c) →
  let identity_law = assert : (functorLaws G functorG a b c f g).functor_id_law
  let composition_law = assert : (functorLaws G functorG a b c f g).functor_comp_law
  in True  -- Type error: assertion failed.
```

This time, the laws cannot be verified.

Trying to verify the identity law, we get this error message:

```dhall
You tried to assert that this expression:

↳ λ(ga : < Left : Text | Right : a >) →
    merge
      { Left = λ(t : Text) → < Left : Text | Right : a >.Left t
      , Right = λ(x : a) → < Left : Text | Right : a >.Right x
      }
      ga

... is the same as this other expression:

↳ λ(x : < Left : Text | Right : a >) → x

... but they differ
```

Trying to verify the composition law, we get:

```dhall
You tried to assert that this expression:

↳ λ(ga : < Left : Text | Right : a >) →
    merge
      { Left = λ(t : Text) → < Left : Text | Right : c >.Left t
      , Right = λ(x : a) → < Left : Text | Right : c >.Right (g (f x))
      }
      ga

... is the same as this other expression:

↳ λ(x : < Left : Text | Right : a >) →
    merge
      { Left = λ(t : Text) → < Left : Text | Right : c >.Left t
      , Right = λ(x : b) → < Left : Text | Right : c >.Right (g x)
      }
      ( merge
          { Left = λ(t : Text) → < Left : Text | Right : b >.Left t
          , Right = λ(x : a) → < Left : Text | Right : b >.Right (f x)
          }
          x
      )

... but they differ
```

Dhall does not simplify `merge` expressions when they are applied to a symbolic variable (in these examples, the symbolic variables are `ga` and `x`).
As soon as we substitute a specific value, say, `x = (G Bool).Left "abc"`, Dhall will be able to verify that the functor laws hold for `G`.

Keeping such limitations in mind, we will try verifying typeclass laws as much as it can be done with Dhall's currently available functionality.

### Contrafunctors (contravariant functors)

Type constructors complementary to covariant ones are **contravariant functors**.
They cannot have a lawful `fmap` method;
instead, they have a `cmap` method with a type signature that flips one of the function arrows: `∀(a : Type) → ∀(b : Type) → (a → b) → F b → F a`.

We will call contravariant type constructors **contrafunctors** for short.

The intuition behind contrafunctors is that they are data structures containing functions that _consume_ data items of a given type.
The `cmap` method transforms data items into data items of another type _before_ they are consumed.

A simple example of a contrafunctor is:

```dhall
let C = λ(a : Type) → a → Text
```
The corresponding `cmap` method is written as:

```dhall
let cmap_C : ∀(a : Type) → ∀(b : Type) → (a → b) → (b → Text) → a → Text
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fb : b → Text) →
    λ(x : a) → fb (f x)
```

The typeclass for contrafunctors is defined by:

```dhall
let Contrafunctor = λ(F : Type → Type) → { cmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F b → F a }
```

As an example, consider this simple contrafunctor `C`:


```dhall
let C = λ(a : Type) → a → Text
```
The corresponding evidence value is written as:

```dhall
let contrafunctor_C : Contrafunctor C
  = { cmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fb : b → Text) →
        λ(x : a) → fb (f x)
  }
```

The laws of contrafunctors are similar to those of functors:


```dhall
let contrafunctorLaws = λ(F : Type → Type) → λ(contrafunctor_F : Contrafunctor F) →
  λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : a → b) → λ(g : b → c) →
    let cmap = contrafunctor_F.cmap
    in { contrafunctor_id_law = cmap a a (identity a) ≡ identity (F a)
       , contrafunctor_comp_law =
          let gf = compose_backward a b c g f
          let cmap_f = cmap a b f
          let cmap_g = cmap b c g
          let cmapf_cmapg = compose_backward (F c) (F b) (F a) cmap_f cmap_g
          in cmap a c gf ≡ cmapf_cmapg
       }
```

We can verify those laws symbolically for the contrafunctor `C` shown above:

```dhall
let contrafunctor_laws_of_C = λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(f : a → b) → λ(g : b → c) →
  { identity_law = assert : (contrafunctorLaws C contrafunctor_C a b c f g).contrafunctor_id_law
  , composition_law = assert: (contrafunctorLaws C contrafunctor_C a b c f g).contrafunctor_comp_law
  }
```

### Bifunctors and profunctors


If a type constructor has several type parameters, it can be covariant with respect to some of those type parameters and contravariant with respect to others.
For example, the type constructor `F` defined by:

```dhall
let F = λ(a : Type) → λ(b : Type) → < Left : a | Right : b → Text >
```
is covariant in `a` and contravariant in `b`.

In this book, we will need **bifunctors** (type constructors covariant in two type parameters) and **profunctors** (type constructors contravariant in the first type parameter and covariant in the second).

An example of a bifunctor in Haskell is  `type P a b = (a, a, b, Int)`.

Dhall encodes bifunctors as functions with two curried arguments of kind `Type`:

```dhall
let P : Type → Type → Type  -- In Haskell and Scala, this is (a, a, b, Int).
  = λ(a : Type) → λ(b : Type) → { x : a, y : a, z : b, t : Integer }
```

Bifunctors have a `bimap` method that changes both type parameters at once:

```dhall
let bimap
 : ∀(a : Type) → ∀(c : Type) → (a → c) → ∀(b : Type) → ∀(d : Type) → (b → d) → P a b → P c d
  = λ(a : Type) → λ(c : Type) → λ(f : a → c) → λ(b : Type) → λ(d : Type) → λ(g : b → d) → λ(pab : P a b) →
    { x = f pab.x, y = f pab.y, z = g pab.z, t = pab.t }
```

The `Bifunctor` typeclass is defined via this method:
```dhall
let Bifunctor : (Type → Type → Type) → Type
  = λ(F : Type → Type → Type) → { bimap : ∀(a : Type) → ∀(c : Type) → (a → c) → ∀(b : Type) → ∀(d : Type) → (b → d) → F a b → F c d }
```

Given `bimap`, one can then define two `fmap` methods that work only on the first or on the second of `P`'s type parameters.

```dhall
let fmap1
  : ∀(a : Type) → ∀(c : Type) → ∀(d : Type) → (a → c) → P a d → P c d
  = λ(a : Type) → λ(c : Type) → λ(d : Type) → λ(f : a → c) → bimap a c f d d (identity d)
```

```dhall
let fmap2
  : ∀(a : Type) → ∀(b : Type) → ∀(d : Type) → (b → d) → P a b → P a d
  = λ(a : Type) → λ(b : Type) → λ(d : Type) → λ(g : b → d) → bimap a a (identity a) b d g
```
Here, we have used the `identity` function defined earlier.

These definitions allow us to compute `Functor` typeclass evidence values for the two functors obtained from a bifunctor `P` by setting one of its type parameters to a fixed type.
The code is:
```dhall
let functor1
  : ∀(P : Type → Type → Type) → Bifunctor P → ∀(a : Type) → Functor (λ(b : Type) → P a b)
  = λ(P : Type → Type → Type) → λ(bifunctorP : Bifunctor P) → λ(a : Type) → { fmap = λ(b : Type) → λ(c : Type) → λ(f : b → c) → bifunctorP.bimap a a (identity a) b c f }
let functor2
  : ∀(P : Type → Type → Type) → Bifunctor P → ∀(b : Type) → Functor (λ(a : Type) → P a b)
  = λ(P : Type → Type → Type) → λ(bifunctorP : Bifunctor P) → λ(b : Type) → { fmap = λ(a : Type) → λ(c : Type) → λ(f : a → c) → bifunctorP.bimap a c f b b (identity b) }
```

Profunctors have an `xmap` method that is similar to `bimap` except for the reversed direction of types.

A Dhall definition of the `Profunctor` typeclass can be written as:

```dhall
let Profunctor : (Type → Type → Type) → Type
  = λ(F : Type → Type → Type) → { xmap : ∀(a : Type) → ∀(c : Type) → (a → c) → ∀(b : Type) → ∀(d : Type) → (b → d) → F c b → F a d }
```


### Pointed functors and contrafunctors

A functor `F` is a **pointed functor** if it has a method called `pure` with the type signature `∀(a : Type) → a → F a`.
This method constructs values of type `F a` from values of type `a`.
In many cases, `pure x` is a data container of type `F a` that stores a single value `x : a`.

Let us define `Pointed` as a typeclass and implement instances for some simple type constructors.

```dhall
let Pointed : (Type → Type) → Type
  = λ(F : Type → Type) → { pure : ∀(a : Type) → a → F a }

let pointedOptional : Pointed Optional = { pure = λ(a : Type) → λ(x : a) → Some x }
let pointedList : Pointed List = { pure = λ(a : Type) → λ(x : a) → [ x ] }
```

So, `Optional` and `List` are pointed functors.

Another example of a pointed functor is `AAInt` defined earlier in this book:

```dhall
let AAInt = λ(a : Type) → { _1 : a, _2 : a, _3 : Integer }

let pointedAAInt : Pointed AAInt = { pure = λ(a : Type) → λ(x : a) → { _1 = x, _2 = x, _3 = +123 } }
```

The `Integer` value `+123` was chosen arbitrarily for this example.

When `F` is a functor, the type `∀(a : Type) → a → F a` can be simplified via one of the **Yoneda identities**, which looks like this:

```dhall
∀(a : Type) → (p → a) → F a  ≅  F p
```
where `p` is a fixed type.
(See the Appendix "Naturality and parametricity" for more details about the Yoneda identities.)

The type signature `∀(a : Type) → a → F a` is a special case of the identity shown above if we set `p` to the unit type (in Dhall, `p = {}`).
Then the type of functions `{} → a` is equivalent to just `a`.
So, the type signature `∀(a : Type) → a → F a` is equivalent to the type `F {}`.

We call a value of type `F {}` a **wrapped unit** value, to indicate that a unit value is being "wrapped" by the type constructor `F`.

Because the type `F {}` is equivalent to the type `∀(a : Type) → a → F a`, we can formulate the `Pointed` typeclass equivalently via the wrapped unit method, which we will denote by `unit`.

```dhall
let PointedU : (Type → Type) → Type
  = λ(F : Type → Type) → { unit : F {} }
```

The type equivalence ("isomorphism") between `∀(a : Type) → a → F a` and `F {}`  means that there is an isomorphism between `Pointed F` and `PointedU F`, given an evidence value of type `Functor F`.
The two directions of that isomorphism can be written as the following Dhall functions:

```dhall
let toPointedU : ∀(F : Type → Type) → Pointed F → PointedU F
  = λ(F : Type → Type) → λ(pointedF : Pointed F) →
    { unit = pointedF.pure {} {=} }
let toPointed : ∀(F : Type → Type) → Functor F → PointedU F → Pointed F
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(pointedUF : PointedU F) →
    { pure = λ(a : Type) → λ(x : a) → functorF.fmap {} a (const {} a x) pointedUF.unit }
```

One advantage of using `PointedU` instead of `Pointed` is that the evidence value has a simpler type and needs no laws.
Another advantage is that `PointedU` can apply to type constructors that are not covariant.

We define a **pointed contrafunctor** as a type constructor `C` for which we have evidence values of type `Contrafunctor C` and `PointedU C`.

For example, consider the contrafunctor `C a = a → Optional r`, where `r` is a fixed type.
We may implement that contrafunctor in Dhall as:

```dhall
let C = λ(r : Type) → λ(a : Type) → a → Optional r
```

This contrafunctor is pointed (with respect to the type parameter `a`) because we can create a value of type `C {}` as a constant function that always returns `None r`:


```dhall
let pointedC : ∀(r : Type) → PointedU (C r)
  = λ(r : Type) → { unit = const {} (Optional r) (None r) }
```

The intuition behind pointed contrafunctors is that they are able to consume an empty value (of unit type),
and we know what result that would give.

Similarly to the method `pure` for pointed functors, pointed contrafunctors have the method we call `cpure`.
Its type is `∀(a : Type) → C a`.
This describes a "consumer" that ignores its input data (of an arbitrary type `a`).

We can define `cpure` for an arbitrary pointed contrafunctor like this:

```dhall
let cpure : ∀(C : Type → Type) → Contrafunctor C → PointedU C → ∀(a : Type) → C a
  = λ(C : Type → Type) → λ(contrafunctorC : Contrafunctor C) → λ(pointedC : PointedU C) → λ(a : Type) →
    contrafunctorC.cmap a {} (const a {} {=}) pointedC.unit
```

### Monads

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
  let pure = λ(a : Type) → λ(x : a) → [ x ]
  let bind = λ(a : Type) → λ(fa : List a) → λ(b : Type) → λ(f : a → List b) →
      List/concatMap a b f fa
  in { pure, bind }
```

The `Reader` monad has an additional type parameter `E` describing the type of the fixed environment value:

```dhall
let Reader = λ(E : Type) → λ(A : Type) → E → A
let monadReader : ∀(E : Type) → Monad (Reader E)
  = λ(E : Type) →
    let pure = λ(A : Type) → λ(x : A) → λ(_ : E) → x
    let bind = λ(A : Type) → λ(oldReader : Reader E A) → λ(B : Type) → λ(f : A → Reader E B) →
         λ(e : E) →
           let a : A = oldReader e
           let b : B = f a e
           in b
    in { pure, bind }
```

Another well-known monad is `State`, which has an additional type parameter `S` describing the type of the internal state:

```dhall
let State = λ(S : Type) → λ(A : Type) → S → Pair A S
let monadState : ∀(S : Type) → Monad (State S)
  = λ(S : Type) →
    let pure = λ(A : Type) → λ(x : A) → λ(s : S) → { _1 = x, _2 = s }
    let bind = λ(A : Type) → λ(oldState : State S A) → λ(B : Type) → λ(f : A → State S B) →
         λ(s : S) →
           let update1 : Pair A S = oldState s
           let update2 : Pair B S = f update1._1 update1._2
           in update2
    in { pure, bind }
```

To verify a monad's laws, we first write a function that takes an arbitrary monad and asserts that its laws hold.

There are three laws of a monad: two identity laws and an associativity law.
In the syntax of Haskell, these laws may be written like this:

```haskell
bind (pure x) f = f x
bind p pure = p
bind (bind p f) g = bind p (\x -> bind (f x) g)
```

In this presentation of the laws, it is not shown what types are used by all of the functions.
The corresponding code in Dhall makes all types explicit:

```dhall
let monadLaws = λ(F : Type → Type) → λ(monadF : Monad F) → λ(a : Type) → λ(x : a) → λ(p : F a) → λ(b : Type) → λ(f : a → F b) → λ(c : Type) → λ(g : b → F c) →
  let left_id_law = monadF.bind a (monadF.pure a x) b f ≡ f x
  let right_id_law = monadF.bind a p a (monadF.pure a) ≡ p
  let assoc_law = monadF.bind b (monadF.bind a p b f) c g
      ≡ monadF.bind a p c (λ(x : a) → monadF.bind b (f x) c g)
  in { left_id_law, right_id_law, assoc_law }
```

The Dhall interpreter can   verify the laws of the `Reader` monad:

```dhall
let testsForReaderMonad = λ(E : Type) → λ(a : Type) → λ(x : a) → λ(p : Reader E a) → λ(b : Type) → λ(f : a → Reader E b) → λ(c : Type) → λ(g : b → Reader E c) →
  let laws = monadLaws (Reader E) (monadReader E) a x p b f c g
  let test1 = assert : laws.left_id_law
  let test2 = assert : laws.right_id_law
  let test3 = assert : laws.assoc_law
  in True
```

Let us also try verifying the laws of the `State` monad:

```dhall
let testsForStateMonad = λ(S : Type) → λ(a : Type) → λ(x : a) → λ(p : State S a) → λ(b : Type) → λ(f : a → State S b) → λ(c : Type) → λ(g : b → State S c) →
  let laws = monadLaws (State S) (monadState S) a x p b f c g
  let test1 = assert : laws.left_id_law
  -- let test2 = assert : laws.right_id_law -- This will not work.
  let test3 = assert : laws.assoc_law
  in True
```

For the State monad, the Dhall interpreter can verify the left identity law and the associativity law, but not the right identity law.
The missing feature is being able to verify that `{ _1 = x._1, _2 = x._2 } ≡ x` when `x` is an arbitrary unknown record with fields `_1` and `_2`.

#### A monad's `join` method implemented with typeclasses

We have defined the `Monad` typeclass via the `pure` and `bind` methods.
Let us implement a function that provides the `join` method for any member of the `Monad` typeclass.

In Haskell, we would define `join` via `bind` as:

```haskell
-- Haskell.
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
In the Dhall prelude, this function is available as `List/concat`.

### Comonads

The `Comonad` typeclass may be defined via the methods `duplicate` and `extract`.

Define the type constructor for evidence values:

```dhall
let Comonad = λ(F : Type → Type) →
  { duplicate : ∀(a : Type) → F a → F (F a)
  , extract : ∀(a : Type) → F a → a
  }
```

As an example, let us define a `Comonad` evidence value for the type constructor `Reader E` in case `E` is a monoidal type:

```dhall
let comonadReader : ∀(E : Type) → Monoid E → Comonad (Reader E) =
  λ(E : Type) → λ(monoidE : Monoid E) →
    let duplicate = λ(a : Type) → λ(fa : Reader E a) → λ(e1 : E) → λ(e2 : E) → fa (monoidE.append e1 e2)
    let extract = λ(a : Type) → λ(fa : Reader E a) → fa monoidE.empty
    in { duplicate, extract }
```

Another example is the so-called **Store comonad**.
It is defined as a pair `(s, s → a)`, where `s` is a fixed type.

```dhall
let Store = λ(s : Type) → λ(a : Type) → Pair s (s → a)
let comonadStore : ∀(s : Type) → Comonad (Store s)
  = λ(s : Type) →
    let duplicate = λ(a : Type) → λ(store : Store s a) →
      store // { _2 = λ(x : s) → store // { _1 = x } }
    let extract = λ(a : Type) → λ(store : Store s a) → store._2 store._1
    in { duplicate, extract }
```

### Applicative functors and contrafunctors

One may define applicative functors as pointed functors that have a `zip` method (that obeys suitable laws).

The corresponding typeclass looks like this:

```dhall
let ApplicativeFunctor = λ(F : Type → Type ) →
  Functor F //\\ Pointed F //\\
    { zip : ∀(a : Type) → F a → ∀(b : Type) → F b → F (Pair a b) }
```

An example of an applicative functor is the built-in `List` type constructor.
Its evidence value for the `ApplicativeFunctor` typeclass can be written as:

```dhall
let applicativeFunctorList : ApplicativeFunctor List = functorList /\ pointedList /\
  { zip = https://prelude.dhall-lang.org/List/zip }
```

It turns out that a `zip` method can be defined for many contravariant functors, and even for some type constructors that are neither covariant nor contravariant.

As an example, consider the type constructor that defines the `Monoid` typeclass:

```dhall
let Monoid = λ(m : Type) → { empty : m, append : m → m → m }
```
This type constructor is itself neither covariant nor contravariant.
However, it supports a `zip` method with the standard type signature:

```dhall
let monoidZip : ∀(a : Type) → Monoid a → ∀(b : Type) → Monoid b → Monoid (Pair a b)
  = λ(a : Type) → λ(monoidA : Monoid a) → λ(b : Type) → λ(monoidB : Monoid b) →
    let empty = { _1 = monoidA.empty, _2 = monoidB.empty }
    let append = λ(x : Pair a b) → λ(y : Pair a b) →
      { _1 = monoidA.append x._1 y._1, _2 = monoidB.append x._2 y._2 }
    in { empty, append }
```
The function `monoidZip` produces a `Monoid` evidence for the pair type (`Pair a b`) out of two evidence values for arbitrary types `a` and `b`.
This is an example of a "combinator" that derives new `Monoid` types out of previously given ones.

In later chapters, we will explore systematically the possible combinators for `Monoid` and other typeclasses.
For now, let us just remark that the `Monoid` type constructor is pointed and has a `zip` method.
An evidence value for the `PointedU` typeclass is:

```dhall
let pointedMonoid : PointedU Monoid =
  let empty : {} = {=}
  let append : {} → {} → {} = λ(_ : {}) → λ(_ : {}) → {=}
  in { unit = { empty, append } }
```
So, we may say that `Monoid` is applicative (although not a functor).
To express that property, let us define the `Applicative` typeclass independently of `Functor`:

```dhall
let Applicative = λ(F : Type → Type ) → PointedU F //\\
  { zip : ∀(a : Type) → F a → ∀(b : Type) → F b → F (Pair a b) }
```

Now we can implement an `Applicative` typeclass evidence for `Monoid`:

```dhall
let applicativeMonoid : Applicative Monoid = pointedMonoid /\ { zip = monoidZip }
```
This example illustrates that the definition of the `Applicative` typeclass can be used with all type constructors, including contravariant ones ("contrafunctors").

An example of an applicative _contrafunctor_ is the type constructor `C m a = a → m`.
The type `C m a` is viewed as a contrafunctor `C m` applied to the type parameter `a`.
The type `m` is assumed to be a fixed type that belongs to the `Monoid` typeclass.

We can implement an `Applicative` evidence value for `C` like this:

```dhall
let C = λ(m : Type) → λ(a : Type) → a → m
let applicativeC : ∀(m : Type) → Monoid m → Applicative (C m)
  = λ(m : Type) → λ(monoidM : Monoid m) →
     let pointedC : PointedU (C m) = { unit = λ(_ : {}) → monoidM.empty }
     let zip = λ(a : Type) → λ(ca : a → m) → λ(b : Type) → λ(cb : b → m) →
       λ(p : Pair a b) → monoidM.append (ca p._1) (cb p._2)
     in pointedC /\ { zip }
```

### Foldable and traversable functors

A functor `F` is called a **foldable functor** if one can extract all data of type `t` stored in a data structure of type `F t`.
This extraction operation can be implemented as a method `toList` with the type signature `F t → List t`.

We will use this operation to define the `Foldable` typeclass in Dhall:
```dhall
let Foldable
  = λ(F : Type → Type) → { toList : ∀(a : Type) → F a → List a }
```
The extracted values are stored in a list in a chosen, fixed order. (Different orders can be used to create different `Foldable` evidence values.)

Another formulation of the "foldable" property is via the function often called `foldMap`, with type signature `(a → m) → F a → m` that assumes `m` to have a `Monoid` typeclass evidence.
The function `foldMap` can be implemented for any `Foldable` functor:

```dhall
let foldMap
  : ∀(m : Type) → Monoid m → ∀(a : Type) → (a → m) → ∀(F : Type → Type) → Foldable F → F a → m
  = λ(m : Type) → λ(monoidM : Monoid m) → λ(a : Type) → λ(f : a → m) → λ(F : Type → Type) → λ(foldableF : Foldable F) → λ(fa : F a) →
    let listA : List a = foldableF.toList a fa
    in List/fold a listA m (λ(x : a) → λ(y : m) → monoidM.append (f x) y) monoidM.empty
```
Here, we use the built-in function `List/fold` that has the following type:
```dhall
let _ = List/fold : ∀(a : Type) → List a → ∀(r : Type) → ∀(cons : a → r → r) → ∀(nil : r) → r
```

A functor is called a **traversable functor** if it supports a method called `traverse` with the type signature written in Haskell like this:

```haskell
-- Haskell.
traverse :: Applicative L => (a -> L b) -> F a -> L (F b)
```
It is important that this method is parameterized by an _arbitrary_ applicative functor `L`.

Rewriting this type signature in Dhall as a type `TraverseT` and making `F` an explicit type parameter, we get:

```dhall
let TraverseT = λ(F : Type → Type) → ∀(L : Type → Type) → Applicative L → ∀(a : Type) → ∀(b : Type) →
  (a → L b) → F a → F (L b)
```
Note how the Haskell typeclass constraint (`Applicative L => ...`) is translated into an evidence argument in Dhall.

The requirement of having a `traverse` method can be formulated via a `Traversable` typeclass:

```dhall
let Traversable = λ(F : Type → Type) → { traverse : TraverseT F }
```

Defined via the `Applicative` typeclass, the `traverse` method should work in the same way for any applicative functor `L`.

We remark without proof that:

- Any traversable functor is also foldable.
- The formulations of the "foldable" property via `foldMap` and via `toList` are equivalent.
- All polynomial functors are both foldable and traversable.
- All traversable functors are polynomial.


### Inheritance of typeclasses

Sometimes one typeclass includes methods from another.
For example, `Monoid` has the `append` method like `Semigroup` does, with the same associativity law.
We could say that the `Monoid` typeclass inherits `append` from `Semigroup`.
The `Monad` typeclass could inherit   `pure` from the `Pointed` typeclass.

To express this kind of inheritance in Dhall, we can use Dhall's features for manipulating records.
Dhall has the operator `//\\` that combines all fields from two record types into a larger record type.
The corresponding operator `/\` combines fields from two record values.
For example:

```dhall
⊢ { a : Text, b : Bool } //\\ { c : Natural }

{ a : Text, b : Bool, c : Natural }

⊢ { a = 1 } /\ { b = True }

{ a = 1, b = True }
```

In these cases, the field names must be different (otherwise it is a type error).

We can use these operators for implementing typeclass inheritance, making typeclass definitions and evidence values shorter to write.

Consider this Dhall code for the `Semigroup` typeclass:

```dhall
let Semigroup = λ(m : Type) → { append : m → m → m }
let semigroupText : Semigroup Text
  = { append = λ(x : Text) → λ(y : Text) → x ++ y }
```

We can use this definition to rewrite the `Monoid` typeclass using the record type from the `Semigroup` typeclass.
We can also rewrite the `Monoid` evidence value for the type `Text` using the `Semigroup` typeclass instance for `Text` (defined above as `semigroupText`):

```dhall
let Monoid = λ(m : Type) → Semigroup m //\\ { empty : m }
let monoidText : Monoid Text = semigroupText /\ { empty = "" }
```

Similarly, we may rewrite the `Monad` typeclass to make it more clear that any monad is also a covariant and a pointed functor:

```dhall
let MonadFP = λ(F : Type → Type) → Functor F //\\ Pointed F //\\
  { bind : ∀(a : Type) → F a → ∀(b : Type) → (a → F b) → F b }
```

As an example, let us define a `Monad` evidence value for `List` in that way:

```dhall
let monadList : MonadFP List =
  functorList /\ pointedList /\
    { bind = λ(a : Type) → λ(fa : List a) → λ(b : Type) → λ(f : a → List b) → List/concatMap a b f fa }
```

### Typeclass derivation

For many typeclasses, it is possible to compute typeclass evidence values for more complicated types automatically from given typeclass evidence values for simpler types.
This sort of computation is known as "typeclass derivation".

In Haskell and Scala, the compiler can perform sophisticated typeclass derivation _automatically_.
In Dhall, we need to pass typeclass evidence manually to appropriate derivation functions.
We will now look at some examples of how that works.

The first example is the derivation of a `Monoid` typeclass evidence for a product type.
If `Monoid` evidence values are given for types `P` and `Q`, the `Monoid` evidence value can be computed automatically for the product type `{ _1 : P, _2 : Q }`.
(We have denoted this type for brevity by `Pair P Q`.)

To define a `Monoid` evidence value for `Pair P Q`, we need to:
- Provide an "empty" value of type `Pair P Q`.
- Define a function `append` that combines two values of type `Pair P Q` into a new value of the same type.
- Verify that the monoid laws still hold for the new evidence value.

All this needs to be done assuming that `Monoid` evidence values for `P` and `Q` are already given (as values `monoidP` and `monoidQ`) and obey the monoid laws.
So, we need to implement a function of type `Monoid P → Monoid Q → Monoid (Pair P Q)`, where `P`, `Q` are type parameters.

To derive the new `Monoid` evidence value, begin by noting that the "empty" values of types `P` and `Q` are already known (`monoidP.empty` and `monoidQ.empty`).
So, we can construct the pair `{ _1 = monoidP.empty, _2 = monoidQ.empty }` and designate that as the "empty" value for the new monoid.

The `append` function is implemented similarly by combining `monoidP.append` and `monoidQ.append`.

The following code performs the full computation:

```dhall
let monoidPair
  : ∀(P : Type) → Monoid P → ∀(Q : Type) → Monoid Q → Monoid (Pair P Q)
  = λ(P : Type) → λ(monoidP : Monoid P) → λ(Q : Type) → λ(monoidQ : Monoid Q) →
    { empty = { _1 = monoidP.empty, _2 = monoidQ.empty }
    , append = λ(x : Pair P Q) → λ(y : Pair P Q) →
       { _1 = monoidP.append x._1 y._1, _2 = monoidQ.append x._2 y._2 }
    }
```

To show how this code may be used, let us derive a `Monoid` evidence value for the type `Pair Bool Natural` using our previously defined evidence values `monoidBool` and `monoidNatural`.

```dhall
let monoidBoolNatural : Monoid (Pair Bool Natural)
  = monoidPair Bool monoidBool Natural monoidNatural
```

It turns out that the monoid laws will hold automatically for all `Monoid` evidence values obtained via `monoidPair`.

The automatic typeclass derivation for pairs is available for a wide range of typeclasses (`Monoid`, `Semigroup`, `Functor`, `Contrafunctor`, `Filterable`, `Applicative`, `Monad`, and some others).
In all those cases, the typeclass laws will also hold automatically for the newly derived evidence values.
This is proved in ["The Science of Functional Programming"](https://leanpub.com/sofp), Chapters 8 and 13, which develop a theory of typeclasses with laws.

There are many type combinators (other than `Pair`) that allow us to derive a new typeclass evidence automatically.
An example is a "function type" combinator:

- If `P` is a monoid and `R` is any fixed type then the function type `Q = R → P` is again a monoid.
- If `F` is a functor and `R` is any fixed type then the type constructor `H` defined by `H a = R → F a` is again a functor.

It turns out that the typeclass laws will hold automatically for typeclass evidence values computed via the "function type" combinators, for a wide range of typeclasses.
(This is also proved in the book "The Science of Functional Programming".)

In later chapters of this book, we will study more systematically the typeclass derivation for a number of typeclasses and type combinators.


## Leibniz equality types

Dhall's `assert` feature is a static check that some expressions are equal.
The syntax is `assert : a ≡ b`, where the expression `a ≡ b` denotes a _type_ that has a value only if `a` equals `b`.
That feature can be viewed as syntax sugar for a technique known as "Leibniz equality types".

A **Leibniz equality type** is a type that depends on two values, say `a` and `b`, of the same type.
The Leibniz equality type is non-void if `a` and `b` are equal, and void if `a` and `b` are unequal.

This chapter will show how to implement Leibniz equality types in Dhall and how to work with them.

### Definition and first examples

A Leibniz equality type constructor corresponding to `a ≡ b` is written as:

```dhall
let LeibnizEqual
  : ∀(T : Type) → ∀(a : T) → ∀(b : T) → Type
  = λ(T : Type) → λ(a : T) → λ(b : T) → ∀(f : T → Type) → f a → f b
```
This complicated expression contains an arbitrary _dependent type_ `f` (a type that depends on a value of type `T`).
It is not obvious how to work with types of the form `LeibnizEqual`.

To figure that out, we begin by considering an example where `T = Natural`.
Define the type `LeibnizEqNat` by applying `LeibnizEqual` to the `Natural` type:

```dhall
let LeibnizEqNat =
   λ(a : Natural) → λ(b : Natural) → ∀(f : Natural → Type) → f a → f b
```
The crucial property of `LeibnizEqNat` is that a function of type `LeibnizEqNat x y` can be created _only if_ the natural numbers `x` and `y` are equal.

To see that, let us write out the types `LeibnizEqNat 0 0` and `LeibnizEqNat 0 1`:

```dhall
-- Symbolic derivation.
LeibnizEqNat 0 0 ≡ ∀(f : Natural → Type) → f 0 → f 0
LeibnizEqNat 0 1 ≡ ∀(f : Natural → Type) → f 0 → f 1
```
Note that `f 0` and `f 1` are unknown _types_ because `f` is an arbitrary, unknown function of type `Natural → Type`.
However, we can always write an identity function of type `f 0 → f 0` even though we do not know anything about the type `f 0`.
This allows us to implement a function of type `LeibnizEqNat 0 0`:

```dhall
let _ : LeibnizEqNat 0 0 = λ(f : Natural → Type) → λ(p : f 0) → p
```

However, it is impossible to implement a function of type `LeibnizEqNat 0 1`.
A function of that type would need to return a function of type `f 0 → f 1` given an `f : Natural → Type`.
Because `f` is a parameter, nothing is known about the types `f 0` and `f 1`.
These two types will be computed by the function `f` that converts natural numbers into types in an arbitrary and unknown way.
So, a function of type `f 0 → f 1` is a function between two completely arbitrary types.
It is impossible to implement such a function.

To see the problem more concretely, let us choose a function `f` such that `f 0` is the unit type `{}` and `f 1` is the void type `<>`. We call that function `unit_if_zero`:
```dhall
let unit_if_zero : Natural → Type
  = λ(n : Natural) → if Natural/isZero n then {} else <>
-- unit_if_zero 0 evaluates to {}
-- unit_if_zero 1 evaluates to <>
```
If we _could_ have a Dhall value `x : LeibnizEqNat 0 1`, we would then apply `x` to the function `unit_if_zero` and to a unit value `{=}` and obtain a value of the void type.
That would be a contradiction in the type system (a value of a type that, by definition, has no values).
Dhall does not allow us to write such code.

These conclusions can be generalized from `Natural` to an arbitrary type `T`.
For any `t : T`, we can implement a (unique) value of type `LeibnizEqual T t t`.
That value is often called `refl` (meaning **reflexivity**, i.e., the property that any value `t` is always equal to itself):

```dhall
let refl : ∀(T : Type) → ∀(t : T) → LeibnizEqual T t t
  = λ(T : Type) → λ(t : T) → λ(f : T → Type) → λ(p : f t) → p
```

But we cannot implement any values of type `LeibnizEqual T x y` when `x` and `y` are different values.
More precisely, this will happen for any `x` and `y` such that the Dhall typechecker will think that `f x` and `f y` are not the same type.

Keep in mind that the Dhall typechecker will not always detect semantic equality in situations where the expressions are syntactically different but actually equal after evaluation.
For example, the expressions  `y * 2` and `y + y` will always evaluate to the same natural number.
But the Dhall typechecker will not accept that `y * 2 ≡ y + y` when `y` is a parameter whose value is not yet known.
As an example, we will not be able to create values of type `λ(y : Natural) → LeibnizEqual Natural (y * 2) (y + y)`.
This is one of the limitations of the Dhall interpreter with respect to dependent types.

To summarize, Leibniz equality types have the following properties:

- One can implement values `refl T x` of type `LeibnizEqual T x x` for any value `x : T`.
- If values `x : T` and `y : T` have different normal forms in Dhall, one cannot implement values of type `LeibnizEqual T x y`.

### Leibniz equality and "assert" expressions

The `assert` feature in Dhall imposes a constraint that two values should be equal (have the same normal forms) at typechecking time.
The expression `assert : x ≡ y` will be accepted only if `x` and `y` have the same type and the same normal forms.

It turns out that Dhall's `assert` feature is equivalent to a certain expression involving the Leibniz equality.
To explain that, let us show how a Leibniz equality type may be used to write code that typechecks only if given values `x` and `y` are equal.

If `x` and `y` are equal then `f x` and `f y` are the same type for any function `f : T → Type`.
If so, the value `refl T x` of type `LeibnizEqual T x x` will be also accepted by Dhall as having the type `LeibnizEqual T x y`.
We can write that constraint as a type annotation (which will be validated at typechecking time) in the form `refl T x : LeibnizEqual T x y`.
That type annotation will be accepted only when `x` equals `y` at typechecking time.

As an example, here is how to assert that `123` equals `100 + 20 + 3`:
```dhall
let _ = refl Natural 123 : LeibnizEqual Natural 123 (100 + 20 + 3)
```
This code is fully analogous to `let _ = assert : 123 ≡ 100 + 20 + 3`.
However, the code written via `LeibnizEqual` is longer, since we have to repeat the value `123` and the type `Natural` (and it is impossible to avoid that repetition).
It is shorter and more convenient to write  `let _ = assert : 123 ≡ 100 + 20 + 3`.

The similarity between Leibniz equality types and Dhall's built-in equality types goes further:
Given a value of type `LeibnizEqual T x y`, one can compute a value of type `x ≡ y`.
Indeed, `LeibnizEqual T x y` is a function that takes an `f : T → Type` and a value of type `f x`, returning a value of type `f y`.
If we need to get a value of type `x ≡ y`, we need to define `f` such that the type `f y` is `x ≡ y`.
Here is an example of defining such a function when `T = Natural`:

```dhall
let x = 1
let f : Natural → Type = λ(a : Natural) → (x ≡ a)
```
When `f` is defined like that, the type `f y` is `x ≡ y` as required, while the type `f x` is `x ≡ x`.
A value of the latter type is written as `assert : x ≡ x`.

Now we have all necessary data to implement a general function `toAssertType` that converts a Leibniz equality into an `assert` value:

```dhall
let toAssertType
  : ∀(T : Type) → ∀(x : T) → ∀(y : T) → LeibnizEqual T x y → (x ≡ y)
  = λ(T : Type) → λ(x : T) → λ(y : T) → λ(leq : LeibnizEqual T x y) →
    let f : T → Type = λ(a : T) → x ≡ a
    in leq f (assert : x ≡ x)
```
With this definition, `toAssertType Natural 1 1 (refl Natural 1)` is exactly the same Dhall value as `assert : 1 ≡ 1`.

In this way, Leibniz equality types reproduce Dhall's `assert` functionality.

Note that `assert` verifies static (compile-time) equality even on values that Dhall cannot compare at run time.
We can write `assert : "abc" ≡ "abc"` for string values, even though Dhall does not allow us to implement a function for comparing two strings at run time.

Similarly, we can implement a value of the Leibniz equality type `LeibnizEqual Text "abc" "abc"` to verify the equality statically:

```dhall
let exampleString = "ab"
let _ = refl Text "abc" : LeibnizEqual Text "${exampleString}c" "abc"
```

We have seen that the Leibniz equality type can be converted to `assert` values.
However, `assert` values currently cannot be converted back to Leibniz equality values.
Dhall currently implements `a ≡ b` and `assert` as special expression types that cannot be manipulated in any way, other than typechecked.

Because Leibniz equality types are more general and more powerful than Dhall's `assert` feature, one might need sometimes to use the Leibniz equality types in case the built-in Dhall features are insufficient.

### Leibniz inequality types

An "inequality type" is a type that is void when `a` and `b` are equal, and non-void when they are not equal.
How could we encode such a type?
We would need to create a type that is void when `a ≡ b` is not void, and vice versa.
This property (a "logical negation" of `a ≡ b`) can be encoded as the type `(a ≡ b) → <>`.

To see why, consider the function type `T → <>` for any type `T`.

Indeed,  if `T` is non-void then we could not possibly have any functions of type `T → <>`.
If we had such a function, we would apply it to some value of type `T` and obtain a value of the void type, which is impossible.
So, the type `T → <>` must be void.

On the other hand, if `T` is void then we _do_ have a function of type `T → <>`; this is the `absurd` function shown earlier in this book.

So, the type `T → <>` may be interpreted as the "negation" of the type `T` in the sense of being void or non-void.

We now use this technique with the Leibniz equality types and define the "inequality type constructor" (`LeibnizUnequal`) such that `LeibnizUnequal T a b` is the same as `(LeibnizEqual T a b) → <>`:

```dhall
let LeibnizUnequal
  : ∀(T : Type) → ∀(a : T) → ∀(b : T) → Type
  = λ(T : Type) → λ(a : T) → λ(b : T) → (∀(f : T → Type) → f a → f b) → <>
```

Suppose some values `a` and `b` are unequal and such that we can distinguish them at run time.
(For instance, we should be able to write a function `is_a` such that `is_a a ≡ True` but `is_a b ≡ False`.)
Then we will be able to construct a value of type `LeibnizUnequal T a b`.
For that, we choose a function `f : T → Type` such that `f a` is the unit type and `f b` is the void type.

As an example, consider `T = Natural` and `a = 1`, `b = 0`.
The type `LeibnizUnequal Natural 1 0` is `(∀(f : Natural → Type) → f 1 → f 0) → <>`.
To construct a value of that type, we need to write code like this:

```dhall
let oneDoesNotEqualZero : LeibnizUnequal Natural 1 0
  = λ(k : ∀(f : Natural → Type) → f 1 → f 0) → ???
```
Here, we need to write a function that returns a value of the void type.
That appears to be impossible because the void type has no values.
But we will not actually call that function; we just need to write code that typechecks.
That code needs to call `k` with some arguments, such that the output type is void (Dhall's `<>`).
The curried function `k` has arguments of type `Natural → Type` and `f 1`, while the final output value has type `f 0`.
So, let us choose `f` such that `f 0 = <>`.
It remains to choose `f` such that `f 1` is not void, so that we could call `k` with all curried arguments.
For simplicity, let us choose `f 1 = {}` (Dhall's unit type).
This allows us to complete the code:

```dhall
let oneDoesNotEqualZero : LeibnizUnequal Natural 1 0
  = λ(k : ∀(f : Natural → Type) → f 1 → f 0) →
    let f : Natural → Type = λ(x : Natural) → if Natural/isZero x then <> else {}
    in k f {=}
```

Similar code would be written for `LeibnizUnequal T a b` when `T` is a union type and the values `a` and `b` are from different parts of the union.
Then we would apply `k` to a function `f` defined via a suitable `merge` expression instead of `if/then/else`.

We note that this sort of code for `LeibnizUnequal T a b` is possible only if we are able to distinguish values of type `T` at _run time_ via `Bool`-valued functions or via `merge` expressions.
This is a stronger requirement than just being able to find out whether two values of type `T` are equal.
Dhall does not support `Bool`-valued comparisons for types such as `Double` or `Text`.
So, it is impossible to write Dhall code with type `LeibnizUnequal Text "abc" "def"` or `LeibnizUnequal Double 0.1 0.2`.
(However, it is possible to implement values of equality types such as `LeibnizEqual Text "abc" "abc"` and `LeibnizEqual Double 0.1 0.1`, as we have already seen.)

The existence of types whose values cannot be compared at run time is not due to a limitation of Dhall.
Even though comparisons for strings or for `Double` numbers could be implemented in another revision of Dhall,
there are types that cannot be efficiently compared at run time.
A simple example is the function type `T = Natural → Bool`.
Two functions of that type are `x = λ(n : Natural) → Natural/isZero (Natural/subtract 10000 n)` and `y = λ(_ : Natural) → True`.
Could we figure out at run time whether these two functions are equal?
Both `x n` and `y n` evaluate to `True` for all `n` up to `10000`.
We need to set `n = 10001` or larger in order to see the difference between `x n` and `y n`.
In general, we cannot be sure that two functions of type `T` are equal unless we try _all_ possible natural numbers as function arguments; but that would take infinite time.

We conclude that there is no practical way of writing a comparison function of type `T → T → Bool` that would compare two functions of type `T` at run time.
(In most cases, Dhall's `assert` feature will be also unable to determine statically that the functions are equal.)

### Constraining a function argument's value

We can use Leibniz equality types for constraining a function argument to be equal or not equal to some value.
To achieve that, we add an extra argument to the function.
The type of that argument will be a suitable Leibniz equality type.
Then the function can only be called only when an evidence value of the required type is provided.

For example, a value of type `LeibnizEqual T x y` is evidence that `x` and `y` are equal.
So, a function with an extra argument of type `LeibnizEqual T x y` can be called only if `x` and `y` have equal normal forms; otherwise, no argument of type `LeibnizEqual T x y` could be provided by the caller.

A function with an argument of type `LeibnizUnequal T x y` can be called only if `x` and `y` have _unequal_ normal forms, provided that Dhall is able to compare values of type `T` for equality at run time.

Compare this with the way "safe division" was implemented in the chapter "Arithmetic with `Natural` numbers".
In that chapter, we added an extra evidence argument of type `Nonzero y` to the function `unsafeDiv`.
The type `Nonzero y` is equivalent to the type `LeibnizUnequal Natural 0 y`. Both types are void when `y` is zero; both types have a single distinct value when `y` is nonzero.
In this way, we see that Leibniz equality types generalize the types of the form of `Nonzero y` to more complicated values and conditions.

Another way of using Leibniz equality is for imposing the requirement that some `Bool`-valued function returns `True`.
For example, suppose we need to implement a function with two `Natural` arguments, and we need to ensure that `f x y` will be called only when `x + y > 100`.
We write:

```dhall
let f = λ(x : Natural) → λ(y : Natural) →
  λ(constraint : LeibnizEqual Bool True (Natural/greaterThan (x + y) 100)) →
    x + y  -- Whatever the function is supposed to do with x and y.
let _ = assert : f 100 10 (refl Bool True) ≡ 110
```

To call `f`, we supply an argument of type `LeibnizEqual Bool True True`.
There is only one value of that type, and that value is produced by `refl Bool True`.
The Dhall typechecker will accept the function call `f 100 10 (refl Bool True)`.
But trying to call `f 1 1 (refl Bool True)` will be a type error.

This technique works only when evaluating conditions on values that are statically known.
For instance, the expression `λ(n : Natural) → f 200 n (refl Bool True)` will not be accepted by Dhall, even though `200 + n` is always greater than `100`.
This is because Dhall's typechecker is not powerful enough to determine symbolically that `200 + n > 100` for an arbitrary natural `n`.



### Leibniz equality at type level

Dhall's `assert` feature is limited to values; it does not work for types or kinds.
The expression `assert : Natural ≡ Natural` (and even just the type `Natural ≡ Natural`) is rejected by Dhall.

One can define a form of a Leibniz equality type for comparing types instead of values:

```dhall
let LeibnizEqualT =
  λ(T : Kind) → λ(a : T) → λ(b : T) → ∀(f : T → Type) → f a → f b

let reflT = λ(T : Kind) → λ(a : T) → λ(f : T → Type) → λ(p : f a) → p
```

Now, the type `LeibnizEqualT Type Natural Bool` will be void because `Natural` and `Bool` are different.
But the type `LeibnizEqualT Type Bool Bool` will _not_ be void because there will be a value `reflT Type Bool` of that type.

If a value of `LeibnizEqualT Type P Q` is given, we have evidence that the types `P` and `Q` are equal.
This allows us to type-cast values of type `P` into values of type `Q`:

```dhall
let LeibnizTypeCast : ∀(P : Type) → ∀(Q : Type) → LeibnizEqualT Type P Q → P → Q
  = λ(P : Type) → λ(Q : Type) → λ(ev : LeibnizEqualT Type P Q) → ev (λ(A : Type) → A)
```
This function is a **type cast** because it reassigns the types without actually changing the values.


A more general form of such type-cast is to transform values of type `F P` into type `F Q` (for any type constructor `F`):
```dhall
let LeibnizTypeCastF : ∀(P : Type) → ∀(Q : Type) → LeibnizEqualT Type P Q → ∀(F : Type → Type) → F P → F Q
  = λ(P : Type) → λ(Q : Type) → λ(ev : LeibnizEqualT Type P Q) → ev
```
An evidence value of type `LeibnizEqualT Type P Q` is a generator of type-casting functions of type `F P → F Q` that work for arbitrary type constructors `F`.

Another use for `LeibnizEqualT` is when implementing an `assert`-like functionality for types:

```dhall
-- This is analogous to `assert : Bool ≡ Bool`, which is not valid in Dhall.
let _ = reflT Type Bool : LeibnizEqualT Type Bool Bool
```

As an example of this usage, let us verify the type equivalence of `LeibnizEqNat 0 1` and `∀(f : Natural → Type) → f 0 → f 1`.
For that, we create an evidence value for their equality:

```dhall
let t1 = LeibnizEqNat 0 1
let t2 = ∀(f : Natural → Type) → f 0 → f 1
let _ = reflT Type t1 : LeibnizEqualT Type t1 t2
```
The last line would be equivalent to `assert : t1 ≡ t2` if Dhall supported assertions on types.
This line validates statically (at typechecking time) that the types are equal.

Because of Dhall's limitations on polymorphism, we cannot implement a single function `LeibnizEqual` that would work both for values and for types.
We need to use `LeibnizEqual` with `refl` when comparing values and `LeibnizEqualT` with `reflT` when comparing types.

We also cannot define a Leibniz equality type for comparing arbitrary _kinds_.
That would require writing something like `λ(T : Sort) → λ(a : T) → ...`, but Dhall rejects this code because `Sort` does not have a type,
while all function types are required to have a type themselves.

How can we verify that, say, `Type` is equal to `Type` but not to `Type → Type`?
We note that the type of `Type` and of `Type → Type` is the symbol `Kind` and not just an arbitrary `Sort`.
So, we may compare specific kinds of that type by defining a restricted version of Leibniz equality that we will call `LeibnizEqualK`:

```dhall
let LeibnizEqualK =
  λ(a : Kind) → λ(b : Kind) → ∀(f : Kind → Type) → f a → f b

let reflK = λ(a : Kind) → λ(f : Kind → Type) → λ(p : f a) → p
```

Now we can provide evidence for kind equality like this:

```dhall
let k1 = Type
let k2 = Type
let _ = reflK k1 : LeibnizEqualK k1 k2 -- Implements `assert : k1 ≡ k2`.
```

What about _inequalities_ at type level?
Recall that an inequality type requires us to have a function that compares values at run time.
However, Dhall cannot compare type symbols at run time.
(It is not possible to write a function `equalT : Type → Type → Bool` such that `equalT Text Text ≡ True` but `equalT Text Double ≡ False`.)
So, we cannot define a type-level inequality type analogous to `LeibnizUnequal`.

### Symbolic reasoning with Leibniz equality

One can implement "equality combinators" that manipulate Leibniz equality types and enable certain patterns of symbolic reasoning about equality of values.
There are five basic "equality combinators" corresponding to the five properties of the equality relation: reflexivity, symmetry, transitivity, value identity, and function extensionality.

The next subsections will show how to translate those properties into Dhall code manipulating the Leibniz equality types.
We will focus on Leibniz equality between values, as Leibniz equality between types or kinds will have similar properties.

#### Reflexivity

The reflexivity property is that any value `x` equals itself.
Translated into the language of equality types, it means that for any `x : T` there must exist a value of type `LeibnizEqual T x x`.
Indeed, we have seen that such a value is created as `refl T x`.

So, we view `refl` as the "reflexivity combinator".

#### Symmetry

The symmetry property is that if `x` equals `y` then also `y` equals `x`.
Translated into the language of equality types, it means that for any value of type `LeibnizEqual T x y` we should be able to construct a value of type `LeibnizEqual T y x`.

So, the symmetry combinator is a function with the type signature `LeibnizEqual T x y → LeibnizEqual T y x`, for all `T : Type`, `x : T`, and `y : T`.
How can we implement that function?
```dhall
let symmetryLeibnizEqual
  : ∀(T : Type) → ∀(x : T) → ∀(y : T) → LeibnizEqual T x y → LeibnizEqual T y x
  = λ(T : Type) → λ(x : T) → λ(y : T) → λ(x_eq_y : LeibnizEqual T x y) → ??? : LeibnizEqual T y x
```
We need to return a value of type `LeibnizEqual T y x`.
The only way to obtain that value is by applying the given evidence value `x_eq_y` to some arguments.
According to the type of `x_eq_y`, we may apply it as `x_eq_y g h`, where the type constructor `g : T → Type` and the value `h : g x` can be chosen as we wish.
The result of evaluating `x_eq_y g h` will then be a value of type `g y`.
But we are required to compute a value of type `LeibnizEqual T y x`.
Evaluating `x_eq_y g h` will give the type `LeibnizEqual T y x` only if the output type `g y` is the same as the type `LeibnizEqual T y x`.
To achieve that, we define `g t = LeibnizEqual T t x`.
Then we notice that the type `g x` is just `LeibnizEqual T x x`.
So, a suitable value `h : g x` is found as `refl T x`.

Putting the code together, we get:

```dhall
let symmetryLeibnizEqual
  : ∀(T : Type) → ∀(x : T) → ∀(y : T) → LeibnizEqual T x y → LeibnizEqual T y x
  = λ(T : Type) → λ(x : T) → λ(y : T) → λ(x_eq_y : LeibnizEqual T x y) →
    let g = λ(t : T) → LeibnizEqual T t x
    let h : g x = refl T x
    in x_eq_y g h
```

#### Transitivity

The transitivity property is that if `x` equals `y` and `y` equals `z` then also `x` equals `z`.
In the language of equality types, it means we should expect to have a combinator with the type signature `LeibnizEqual T x y → LeibnizEqual T y z → LeibnizEqual T x z`, for all `T : Type`, `x : T`, `y : T`, and `z : T`.
How can we implement that?
```dhall
let transitivityLeibnizEqual
  : ∀(T : Type) → ∀(x : T) → ∀(y : T) → ∀(z : T) →
    LeibnizEqual T x y → LeibnizEqual T y z → LeibnizEqual T x z
  = λ(T : Type) → λ(x : T) → λ(y : T) → λ(z : T) → λ(x_eq_y : LeibnizEqual T x y) → λ(y_eq_z : LeibnizEqual T y z) → ??? : LeibnizEqual T x z
```
We need to return a value of type `LeibnizEqual T x z`.
The only way for us to get that value is to apply the given evidence values `x_eq_y` and `y_eq_z` to some arguments.
At the top level, we need to apply `y_eq_z`, because we need to get a type involving the value `z`.
```dhall
??? : LeibnizEqual T x z = y_eq_z g h
```
The required output type `LeibnizEqual T x z` must be the same as the type `g z`.
This will be achieved if we define the type constructor `g : T → Type` by `g t = LeibnizEqual T x t`.
Then the value `h` must have type `g y`, which is `LeibnizEqual T x y`.
The given argument `x_eq_y` has precisely that type.
So, we obtain the complete code of `transitivityLeibnizEqual`:
```dhall
let transitivityLeibnizEqual
  : ∀(T : Type) → ∀(x : T) → ∀(y : T) → ∀(z : T) →
    LeibnizEqual T x y → LeibnizEqual T y z → LeibnizEqual T x z
  = λ(T : Type) → λ(x : T) → λ(y : T) → λ(z : T) → λ(x_eq_y : LeibnizEqual T x y) → λ(y_eq_z : LeibnizEqual T y z) →
    let g = λ(t : T) → LeibnizEqual T x t
    let h : g y = x_eq_y
    in y_eq_z g h
```

#### Value identity

If we know that `x` equals `y`, we also know that `f x` equals `f y` for any function `f`.
This is translated into the following combinator:

```dhall
let identityLeibnizEqual
  : ∀(T : Type) → ∀(x : T) → ∀(y : T) → ∀(U : Type) → ∀(f : T → U) → LeibnizEqual T x y → LeibnizEqual U (f x) (f y)
  = λ(T : Type) → λ(x : T) → λ(y : T) → λ(U : Type) → λ(f : T → U) →
    λ(x_eq_y : LeibnizEqual T x y) → ??? : LeibnizEqual U (f x) (f y)
```

We derive the code for this combinator using the same technique: find a suitable type constructor `g : T → Type` and a value `h : g x` such that evaluating `x_eq_y g h` will give a value of the required output type `LeibnizEqual U (f x) (f y)`.
That last type should be the same as `g y`.
We achieve that by defining `g t = LeibnizEqual U (f x) (f t)`.
Then a suitable value `h : g x` is obtained with `refl`.
The code becomes:
```dhall
let identityLeibnizEqual
  : ∀(T : Type) → ∀(x : T) → ∀(y : T) → ∀(U : Type) → ∀(f : T → U) → LeibnizEqual T x y → LeibnizEqual U (f x) (f y)
  = λ(T : Type) → λ(x : T) → λ(y : T) → λ(U : Type) → λ(f : T → U) → λ(x_eq_y : LeibnizEqual T x y) →
    let g = λ(t : T) → LeibnizEqual U (f x) (f t)
    let h : g x = refl U (f x)
    in x_eq_y g h
```

The analogous property for Leibniz _type_ equality is implemented via an arbitrary type constructor `F` instead of an arbitrary function `f`.

```dhall
let identityLeibnizEqualT
  : ∀(T : Kind) → ∀(x : T) → ∀(y : T) → ∀(U : Kind) → ∀(F : T → U) → LeibnizEqualT T x y → LeibnizEqualT U (F x) (F y)
  = λ(T : Kind) → λ(x : T) → λ(y : T) → λ(U : Kind) → λ(F : T → U) → λ(x_eq_y : LeibnizEqualT T x y) →
    let g = λ(t : T) → LeibnizEqualT U (F x) (F t)
    let h : g x = reflT U (F x)
    in x_eq_y g h
```


#### Function extensionality

The property of **function extensionality** means that two functions are equal when they always give equal results for equal arguments.
In other words, `f ≡ g` if and only if we have `f x ≡ g x` for all `x`.

In terms of equality types, we can implement one direction of this equivalence as a combinator of type `LeibnizEqual (T → U) f g → LeibnizEqual U (f x) (g x)`, for all `T : Type`, `U : Type`, `x : T`, `f : T → U`, and `g : T → U`. This combinator transforms evidence that `f ≡ g` into evidence that `f x ≡ g x` for all `x`.

```dhall
let extensionalityLeibnizEqual
  : ∀(T : Type) → ∀(x : T) → ∀(U : Type) → ∀(f : T → U) → ∀(g : T → U) → LeibnizEqual (T → U) f g → LeibnizEqual U (f x) (g x)
  = λ(T : Type) → λ(x : T) → λ(U : Type) → λ(f : T → U) → λ(g : T → U) → λ(f_eq_g : LeibnizEqual (T → U) f g) → ??? : LeibnizEqual U (f x) (g x)
```
To derive the code, we look for a suitable type constructor `k : (T → U) → Type` and a value `h : k f` such that evaluating `f_eq_g k f` will give a value of the required output type `LeibnizEqual U (f x) (g x)`.
That last type should be the same as `k g`.
We achieve that by defining `k t = LeibnizEqual U (f x) (t x)`.
Then a suitable value `h : k f` is obtained with `refl`.
The code becomes:
```dhall
let extensionalityLeibnizEqual
  : ∀(T : Type) → ∀(x : T) → ∀(U : Type) → ∀(f : T → U) → ∀(g : T → U) → LeibnizEqual (T → U) f g → LeibnizEqual U (f x) (g x)
  = λ(T : Type) → λ(x : T) → λ(U : Type) → λ(f : T → U) → λ(g : T → U) → λ(f_eq_g : LeibnizEqual (T → U) f g) →
    let k = λ(t : T → U) → LeibnizEqual U (f x) (t x)
    let h : k f = refl U (f x)
    in f_eq_g k h
```

#### Examples of symbolic reasoning

To illustrate what we mean by "symbolic reasoning", consider a situation where we have an evidence value of type `x ≡ y` where `x : T`, `y : T`, and an evidence value of type `f ≡ g` where `f : T → U`, `g : T → U`.
It is clear that `f x ≡ g y` in that case.
Can we produce an evidence value for that?

Dhall cannot manipulate values of its built-in equality types (values of the form `assert : x ≡ y`).
There are currently no functions in Dhall that can derive any information out of such values.

But Leibniz equality types and their standard combinators do allow us to perform some computations involving equality.
In this example, we can obtain a value of type `f x ≡ g x` by using the "function extensionality" combinator, and we can obtain a value of type `g x ≡ g y` via the "value identity" combinator.
It remains to use the "transitivity" combinator to establish that `f x ≡ g y`.

The code that computes evidence of type `f x ≡ g y` is:
```dhall
let extensional_equality
  = λ(T : Type) → λ(x : T) → λ(y : T) → λ(U : Type) → λ(f : T → U) → λ(g : T → U) → λ(x_eq_y : LeibnizEqual T x y) → λ(f_eq_g : LeibnizEqual (T → U) f g) →
    let f_x_eq_g_x : LeibnizEqual U (f x) (g x) = extensionalityLeibnizEqual T x U f g f_eq_g
    let g_x_eq_g_y : LeibnizEqual U (g x) (g y) = identityLeibnizEqual T x y U g x_eq_y
    let result : LeibnizEqual U (f x) (g y) = transitivityLeibnizEqual U (f x) (g x) (g y) f_x_eq_g_x g_x_eq_g_y
    in result
```

We conclude this section with a few more illustrations of symbolic reasoning with Leibniz equality types.

###### Example
Given a function `f : T → U → V` and evidence values of types `a ≡ b` and `c ≡ d`, where `a : T`, `b : T`, `c : U`, `d : U`, derive an evidence value for `f a c ≡ f b d`.

####### Solution

The derivation uses the combinator we denoted by `identityLeibnizEqual`.
First, we apply that combinator to `f`, `a`, and `b`; the result is evidence that `f a ≡ f b` as functions of type `U → V`.
It remains to apply `extensional_equality` to those functions and to the values `c`, `d`.
The complete code is:

```dhall
let identityLeibnizEqual2 = λ(T : Type) → λ(U : Type) → λ(V : Type) → λ(a : T) → λ(b : T) → λ(c : U) → λ(d : U) → λ(f : T → U → V) → λ(a_eq_b : LeibnizEqual T a b) → λ(c_eq_d : LeibnizEqual U c d) →
  let fa_eq_fb : LeibnizEqual (U → V) (f a) (f b) = identityLeibnizEqual T a b (U → V) f a_eq_b
  in extensional_equality U c d V (f a) (f b) c_eq_d fa_eq_fb : LeibnizEqual V (f a c) (f b d)
```

The value `identityLeibnizEqual2` is evidence that `f a c` equals `f b d`.

###### Exercise

Given functions `f : T → U → V` and `g : T → U → V` and evidence for `f ≡ g`, `a ≡ b` and `c ≡ d`, where `a : T`, `b : T`, `c : U`, `d : U`, derive an evidence value for `f a c ≡ g b d`.

## Church encodings for simple types

### Fixpoints and Church encoding

Dhall does not directly support defining recursive types or recursive functions.
The only supported recursive types are the built-in `Natural` and `List` types.
However, the Church encoding technique provides a wide range of user-defined recursive types and recursive functions in Dhall.

Dhall's documentation contains a [beginner's tutorial on Church encoding](https://docs.dhall-lang.org/howtos/How-to-translate-recursive-code-to-Dhall.html).
Here, we summarize that technique more briefly.
(What the Dhall documentation calls a "recursion scheme" is more appropriately called a "pattern functor" in this book.)

In languages that directly support recursive types, one defines types such as lists or trees via "type equations".
That is, one writes definitions of the form `T = F T` where `F` is some type constructor and `T` is the type being defined.

For example, suppose `T` is the type of lists with integer values.
A recursive definition of `T` in Haskell could look like this:

```haskell
data T = Nil | Cons Int T     -- Haskell.
```

This definition of `T` has the form of a "recursive type equation", `T = F T`, where `F` is a (non-recursive) type constructor defined by:

```haskell
type F a = Nil | Cons Int a     -- Haskell.
```

The type constructor `F` is called the **pattern functor** for the definition of `T`.
The type `T` is called a **fixpoint** of `F`.

Each recursive type definition can be rewritten in this form, using a suitable pattern functor.

Dhall does not accept recursive type equations, but it will accept the definition of `F` because it is non-recursive.
The definition of `F` is written in Dhall as:

```dhall
let F : Type → Type = λ(a : Type) → < Nil |  Cons : { head : Integer, tail : a } >
```

The **Church encoding** of `T` is the following type expression:

```dhall
let C : Type = ∀(r : Type) → (F r → r) → r
```

This definition of the type `C` is _non-recursive_, and so Dhall will accept it.

Note that we are using `∀(r : Type)` and not `λ(r : Type)` when we define `C`.
This `C` is not a type constructor; it is a type of a function with a type parameter.

When we define `F` as above, it turns out that the type `C` is _equivalent_ to the type of (finite) lists with integer values.

The Church encoding construction works in the same way for any pattern functor `F`.
Given `F`, one defines the corresponding Church-encoded type `C` by:

```dhall
let C = ∀(r : Type) → (F r → r) → r
```
As it turns out, the type `C` is equivalent to the type `T` that one would have defined by `T = F T` in a language that supports recursively defined types.

It is _far from obvious_ that the type `C = ∀(r : Type) → (F r → r) → r` is equivalent to a type `T` defined recursively by `T = F T`.
More precisely, the type `C` is the "least fixpoint" of the type equation `T = F T`.
A mathematical proof of that property is given in the paper ["Recursive types for free"](https://homepages.inf.ed.ac.uk/wadler/papers/free-rectypes/free-rectypes.txt) by P. Wadler, and also in the Appendix of this book.
Here we will focus on the practical uses of Church encoding.

### First examples of recursive types

Here are some examples of Church encoding for recursively defined types.

TODO: insert Haskell recursive definitions for each of these types, and indicate how F is to be defined in each case.

The type `Nat`, equivalent to Dhall's `Natural`, can be defined by:
```dhall
let F = Optional
let Nat = ∀(r : Type) → (F r → r) → r
```

The type `ListInt` (a list with integer values):

```dhall
let F = λ(r : Type) → < Nil | Cons : { head : Integer, tail : r } >
let ListInt = ∀(r : Type) → (F r → r) → r
```

The type `TreeText` (a binary tree with `Text` strings in leaves):

```dhall
let FT = λ(r : Type) → < Leaf : Text | Branch : { left : r, right : r } >
let TreeText = ∀(r : Type) → (FT r → r) → r
```

### Church encoding of non-recursive types

If a pattern functor does _not_ depend on its type parameter, the Church encoding construction will leave the type unchanged.

For example, consider this pattern functor:

```dhall
let K = λ(t : Type) → { x : Text, y : Bool }
```
The type `K t` does not actually depend on `t`.
Nevertheless, `K` is a valid type constructor that is covariant (has a `Functor` evidence value):

```dhall
let functorK : Functor K = { fmap =
  λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(x : K a) → x
}
```
In this code, we just return `x` because the type judgments `x : K a` and `x : K b` both hold.

The type constructor `K` is an example of a **constant functor**.

Because `K` is a covariant functor, we may use the Church encoding to obtain the least fixpoint of `K`.
The corresponding Church encoding gives the type:

```dhall
let C = ∀(r : Type) → ({ x : Text, y : Bool } → r) → r
```

The general properties of the Church encoding always enforce that `C` is a solution of the type equation `C = K C`.
This remains true even when `K` does not depend on its type parameter.
So, now we have `K C = { x : Text, y : Bool }` independently of `C`.
The type equation `C = K C` is non-recursive and simply says that `C = { x : Text, y : Bool }`.

More generally, the type `∀(r : Type) → (p → r) → r` is equivalent to just `p`, because it is the Church encoding of the type equation `T = p`.
Church encodings of that form do not produce new types.
Still, these encodings are useful for showing how certain types can be represented equivalently via higher-order functions.

In this book, we will write type equivalences using the symbol `≅` (which is not a valid Dhall symbol) like this:

```dhall
∀(r : Type) → (p → r) → r  ≅  p
```
This type equivalence is a special case of one of the **Yoneda identities**:

```dhall
∀(r : Type) → (p → r) → G r  ≅  G p
```
Here `G` must be a covariant type constructor and `p` must a fixed type (not depending on `r`).

The Yoneda identities can be proved by assuming suitable naturality laws.
See   Appendix "Naturality and parametricity" for more details.

For now, we will use the type equivalence `(p → r) → r ≅ p` to derive Church-like encodings for unit types, for the void type, as well as for product and co-product types.

- Unit type. Set `p = {}` in the type equivalence and get:

`∀(r : Type) → ({} → r) → r  ≅  {}`

The type `{} → r` is equivalent to just `r`, because there is only one value of type `{}`.
So, we have derived the encoding of the unit type via the polymorphic identity function:

`∀(r : Type) → r → r  ≅  {}`

- Void type. Set `p = <>` in the type equivalence and get:

`∀(r : Type) → (<> → r) → r  ≅  <>`

The type `<> → r` is equivalent to the unit type, because there is only one distinct function of the type `<> → r`.
(That function was called `absurd` in this book.)
So, the type `(<> → r) → r` can be simplified to `{} → r` and finally to just `r`.
We have derived the encoding of the void type as `∀(r : Type) → r`.

- Product types. The simplest product type is `Pair`, which is sufficient to define all other product types. Set `p = Pair a b` in the type equivalence and get:

`∀(r : Type) → (Pair a b → r) → r  ≅  Pair a b`

The type `Pair a b → r` is equivalent to the function type `a → b → r`.
So, we obtain the following encoding of the pair type:

`Pair a b  ≅  ∀(r : Type) → (a → b → r) → r`

- Co-product types. The simplest co-product type is `Either`, which is sufficient to define all other co-product types. Set `p = Either a b` in the type equivalence and get:

`∀(r : Type) → (Either a b → r) → r  ≅  Either a b`

The type `Either a b → r` is equivalent to the type `Pair (a → r) (b → r)`.
We can use the Church encoding of the pair type derived just previously.
As a result, we obtain the following encoding of the `Either` type:

`Either a b  ≅  ∀(r : Type) → (a → r) → (b → r) → r`

These derivations show that the curried function types and the universal type quantifier are sufficient to Church-encode all other types that Dhall can have.

Let us also summarize the standard type equivalence identities we have used in these derivations:

- Function from the unit type: `{} → r  ≅  r`
- Function from the void type: `<> → r  ≅  {}`
- Function from a record type: `Pair a b → r  ≅  a → b → r`
- Function from a union type: `Either a b → r  ≅  Pair (a → r) (b → r)`

### Church encoding in the curried form

Using the standard type equivalence identities shown in the previous section, we can rewrite the type `ListInt` in a form more convenient for practical applications.

The first type equivalence is that a function from a union type is equivalent to a product of functions.
So, the type `F r → r`, written in full as:

`< Nil | Cons : { head : Integer, tail : r } > → r`

is equivalent to a pair of functions of types `{ head : Integer, tail : r } → r` and  `< Nil > → r`.

The type `< Nil >` is a named unit type, so `< Nil > → r` is equivalent to just `r`.

The second type equivalence is that a function from a record type is equivalent to a curried function.
For instance, the type
`{ head : Integer, tail : r } → r`
is equivalent to `Integer → r → r`.

Using these type equivalences, we may rewrite the type `ListInt` in the **curried form** as:

```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r
```
This type is isomorphic to the previous definition of `ListInt`, but now the code does not involve union types or record types any more.
Working with curried functions often gives shorter code than working with union types and record types.
Writing the Church-encoded types in the form `∀(r : Type) → (F r → r) → r` is more convenient for proving general properties of Church encodings than for practical programming.

As an example, let us rewrite the type `TreeText` defined above in the curried form.
Begin with the definition already shown:

```dhall
let FT = λ(r : Type) → < Leaf : Text | Branch : { left : r, right : r } >
let TreeText = ∀(r : Type) → (FT r → r) → r
```

Since `FT r` is a union type with two parts, the type of functions `FT r → r` can be replaced by a pair of functions.

We can also replace functions from a record type by curried functions.

Then we obtain an equivalent definition of `TreeText` that is easier to work with:

```dhall
let TreeText = ∀(r : Type) → (Text → r) → (r → r → r) → r
```

These examples show how any type constructor `F` defined via products (records) and co-products (union types) gives rise to a Church encoding that can be rewritten purely via curried functions, without using any records or union types.
This is what we call the **curried form** of the Church encoding.

Another example of a curried form is the Church encoding of natural numbers.
The non-curried form is `∀(r : Type) → (Optional r → r) → r`. The function type `Optional r → r` is equivalent to the pair of `r` and `r → r`.
So, the curried form is `∀(r : Type) → r → (r → r) → r`.

The curried form is often convenient for practical programming with Church-encoded values.
However, the type `∀(r : Type) → (F r → r) → r` is more suitable for studying and proving the general properties of Church encodings.

Historical note: The curried form of the Church encoding is also known as the **Boehm-Berarducci encoding**.
See [this discussion by O. Kiselyov](https://okmij.org/ftp/tagless-final/course/Boehm-Berarducci.html) for more details.


## Working with Church-encoded data

A Church-encoded data type is always of the form `∀(r : Type) → ... → r`, that is, a curried higher-order function with a type parameter.
A value `x` of that type is a function whose code may be written like this:

```dhall
let x
 : ∀(r : Type) → (F r → r) → r
  = λ(r : Type) → λ(frr : F r → r) →
     let y : r = ??? -- Need to insert some code here.
     in y
```

Working with data encoded in this way is not straightforward.
It takes some work to figure out convenient ways of creating values of those types and of working with them.

Our next steps is to figure out how to implement constructors for Church-encoded data, how to perform aggregations (or "folds"), and how to do pattern matching on that data.

For simplicity, we now consider a Church-encoded type `C = ∀(r : Type) → (F r → r) → r` defined via a pattern functor `F`.
Later we will see that the same techniques work for Church-encoded type constructors and other more complicated types.

An important requirement is that the pattern functor `F` should be a _covariant_ type constructor.
If this is not so, the Church encoding will not work as expected.

We will assume that `F` has a known and lawful `fmap` method that we denote by `fmapF`.
So, all Dhall code below assumes a given set of definitions of this form:

```dhall
let F : Type → Type = ???
let fmapF : FmapT F = ???
```

The required code for the text-valued trees would be:

```dhall
let FT = λ(r : Type) → < Leaf : Text | Branch : { left : r, right : r } >
let fmapF : FmapT FT
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fa : FT a) →
    merge {
      Leaf = λ(t : Text) → (FT b).Leaf t,
      Branch = λ(br : { left : a, right : a }) → (FT b).Branch { left = f br.left, right = f br.right },
    } fa
```


### Generic forms of Church encoding

Dhall's type system is powerful enough to be able to express the Church encoding's type generically, as a function of an arbitrary pattern functor.
We will denote that function by `LFix`, following P. Wadler's paper "Recursive types for free".

For simple types:

```dhall
let LFix : (Type → Type) → Type
  = λ(F : Type → Type) → ∀(r : Type) → (F r → r) → r
```

Instead of repeating the definition `C = ∀(r : Type) → (F r → r) → r`, we will write more concisely: `C = LFix F`.

Later in this book, we will work in Church encoding generically whenever possible.
We will assume that `F` and `fmap_F` are given, and we will implement various functions in terms of `F` and `fmap_F` once and for all.


### Isomorphism `C = F C` via the functions `fix` and `unfix`

The Church-encoded type `C = LFix F` is a fixpoint of the type equation `C = F C`.
A fixpoint means there exist two functions, `fix : F C → C` and `unfix : C → F C`, that are inverses of each other.
Those two functions implement an isomorphism between `C` and `F C`.
The isomorphism shows that the types `C` and `F C` are equivalent (carry the same data), which is one way of understanding why `C` is a fixpoint of the type equation `C = F C`.

Because this isomorphism is a general property of all Church encodings, we can write the code for `fix` and `unfix` once for all pattern functors `F` and the corresponding types `C = LFix F`.

The basic technique of working with a Church-encoded value `c : C` is to use `c` as a curried higher-order function.
That function has two arguments: a type parameter `r` and a function of type `F r → r`.
Given a value `c : C`, we can compute a value of another type `D` if we specify `D` as the type parameter to `c` and if we manage to provide a function of type `F D → D` as the second argument of `c`:
```dhall
let d : D =
  let fdd : F D → D = ???
  in c D fdd
```

We will use this technique to implement `fix` and `unfix`.
The code will be parameterized by an arbitrary functor `F`.
For clarity, we split the code into smaller chunks annotated by their types:

```dhall
let fix : ∀(F : Type → Type) → Functor F → F (LFix F) → LFix F
  = λ(F : Type → Type) → λ(functorF : Functor F) →
    let C = LFix F
    in
      λ(fc : F C) → λ(r : Type) → λ(frr : F r → r) →
        let c2r : C → r = λ(c : C) → c r frr
        let fmap_c2r : F C → F r = functorF.fmap C r c2r
        let fr : F r = fmap_c2r fc
        in frr fr

let unfix : ∀(F : Type → Type) → Functor F → LFix F → F (LFix F)
  = λ(F : Type → Type) → λ(functorF : Functor F) →
    let C = LFix F
    let fmap_fix : F (F C) → F C = functorF.fmap (F C) C (fix F functorF)
    in λ(c : C) → c (F C) fmap_fix
```

The definitions of `fix` and `unfix` are non-recursive and are accepted by Dhall.

It turns out that `fix` and `unfix` are inverses of each other, as long as `F` is a lawful covariant functor and the parametricity assumptions hold (which is always the case in Dhall).

A mathematical proof of that property is given in the paper ["Recursive types for free"](https://homepages.inf.ed.ac.uk/wadler/papers/free-rectypes/free-rectypes.txt) (where those functions are called "roll" and "unroll").
A proof is also shown in "Statement 2" in the section "Church encoding of least fixpoints: Proofs" of Appendix "Naturality and parametricity" in this book.

### Data constructors

The function `fix : F C → C` provides a general way of creating new values of type `C` out of previously known values or from scratch.
Sometimes this function is also called `build`, as in Dhall's `Natural/build` and `List/build`.
We will call it `fix` to remind us of "fixpoints".

To see how this works, note that the type `F C` is almost always a union type.

A function from a union type is equivalent (as a type) to a tuple of simpler functions.

For example, 
a function of type `Either a b → r` is equivalent to a pair of functions of types `a → r` and `b → r`.
We can write this equivalence as:

`Either a b → r  ≅  Pair (a → r) (b → r)`

The type `Option a` is equivalent to `Either {} a`.
So, a function of type `Option a → r` is equivalent to a pair of functions of types `{} → r` and `a → r`.
Because the unit type has only a single value, the type `{} → r` is equivalent to just `r`.
This allows us to rewrite `Option a → r` equivalently as a pair of `r` and `a → r`.

These examples show that we may always rewrite the function type `F C → C` as a tuple of simpler functions whose arguments are not union types.
Each of the simpler functions (in Dhall, we will denote them by `F1 C → C`, `F2 C → C`, etc.) is a specific constructor to which we may assign a name for convenience.
In this way, we will replace a single function `fix` by a number of named constructors that help create values of type `C` more easily.

The code for the constructors can be derived mechanically from the general code of `fix`.
But in some cases it is easier to write the constructors manually, guided by the curried form of the Church encoding.

To illustrate this technique, consider two examples: `ListInt` and `TreeText`.

Begin with the uncurried Church encodings of those types:
```dhall
let ListInt = ∀(r : Type) → (< Nil | Cons { head : Integer, tail : r } > → r) → r

let TreeText = ∀(r : Type) → (< Leaf Text | Branch { left : r, right : r } > → r) → r
```

Now pass to the curried Church encodings:

```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r

let TreeText = ∀(r : Type) → (Text → r) → (r → r → r) → r
```

From this, we can simply read off the types of the constructor functions (which we will call `nil`, `cons`, `leaf`, and `branch` according to the often used names of those constructors):

```dhall
let nil : ListInt = ???
let cons : Integer → ListInt → ListInt = ???

let leaf : Text → TreeText = ???
let branch : TreeText → TreeText → TreeText = ???
```

Each of the constructor functions needs to return a value of the Church-encoded type, and we write out its type signature.
Then, each constructor applies the corresponding part of the curried Church-encoded type to suitable arguments.

How can we obtain the code for those functions?
We know that the constructor functions are just parts of the function body of `fix` that we implemented in the previous section (for all Church encodings at once).
In principle, we could derive the code of the constructor functions from the code for `fix`.
In practice, it is easier to start with the curried forms and try implementing the type signatures.

After some guessing, we arrive at this code:

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
-- The list similar to [+123, -456, +789]:
let example1 : ListInt = cons +123 (cons -456 (cons +789 nil))

{-             /\
   The tree   /\ c    :
             a  b
-}
let example2 : TreeText = branch ( branch (leaf "a") (leaf "b") ) (leaf "c")
```

To illustrate the general principle that constructors come from the `fix` function, let us see how the list constructors `cons` and `nil` can be derived from the general `fix` function for `ListInt`.
Begin by implementing the functions necessary for the definition of `fix`:

```dhall
let F = λ(r : Type) → < Nil | Cons : { head : Integer, tail : r } >
let functorF : Functor F = {
    fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fa : F a) → merge {
      Nil = (F b).Nil,
      Cons = λ(pair : { head : Integer, tail : a }) → (F b).Cons (pair // { tail = f pair.tail })
    } fa
  }
let ListInt = LFix F
```

The argument of the function `fix F functorF : F ListInt → ListInt` is a union type `< Nil | Cons : { head : Integer, tail : ListInt } >`.
Since that union type has only two parts, we can apply `fix` either to the value `Nil` or to a value `Cons { head, tail }`.
The results of those two computations are the two constructors for the `ListInt` type.

We can obtain the normal forms of those constructors if we use Dhall's interpreter to print the values `fix F functorF (F ListInt).Nil` and `fix F functorF (F ListInt).Cons { head, tail }`.
The complete code is [in the file ./example-list-fix.dhall](https://github.com/winitzki/scall/blob/master/tutorial/example-list-fix.dhall).
When we run it through the Dhall interpreter, we get this output:

```dhall
$ dhall --file ./example-list-fix.dhall
{ cons =
    λ(h : Integer) →
    λ ( t
      : ∀(r : Type) → (< Cons : { head : Integer, tail : r } | Nil > → r) → r
      ) →
    λ(r : Type) →
    λ(frr : < Cons : { head : Integer, tail : r } | Nil > → r) →
      frr
        ( < Cons : { head : Integer, tail : r } | Nil >.Cons
            { head = h, tail = t r frr }
        )
, nil =
    λ(r : Type) →
    λ(frr : < Cons : { head : Integer, tail : r } | Nil > → r) →
      frr < Cons : { head : Integer, tail : r } | Nil >.Nil
}
```
Rewriting these expressions via the types `F` and `ListInt` for brevity, we get the following definitions:

```dhall
let cons = λ(h : Integer) → λ(t : ListInt) →
  λ(r : Type) → λ(frr : F r → r) → frr ((F r).Cons { head = h, tail = t r frr})
let nil = λ(r : Type) → λ(frr : F r → r) → frr (F r).Nil
```

These are the two basic constructors for the `ListInt` type.
We see that the code is equivalent to the code we wrote earlier by guessing.


### Aggregations ("folds")

The type `C` itself can be viewed as a type of fold-like functions.

To see the similarity, compare the curried form of the Church-encoded `ListInt` type:

```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r
```
with the type signature of the `foldRight` function for the type `List Integer`:

```dhall
let foldRight_list : ∀(r : Type) → (List Integer) → r → (Integer → r → r) → r = ???
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
let flip_foldRight
  : ListInt → ∀(r : Type) → r → (Integer → r → r) → r
  = λ(p : ListInt) → p
```
This is just an identity function of type `ListInt → ListInt`.

In this sense, we may say that Church encodings replace recursive types by the types of their "fold" operations. 

We note that `foldRight` and `flip_foldRight` are  non-recursive functions.
In this way, the Church encoding enables "fold-like" operations to be implemented without recursion.

For an arbitrary Church-encoded data type `C`, the "fold" function is the identity function of type `C → C` with first two arguments flipped.
In practice, it is easier to "inline" that identity function: that is, to use the data type `C` itself as the "fold"-like function.

Recursive data types such as lists and trees support certain useful operations such as `map`, `concat`, `filter`, `zip`, and `traverse`.
In most FP languages, those operations are implemented via recursive code.
To implement those operations in Dhall, we need to reformulate them as "fold-like aggregations".

A **fold-like aggregation** iterates over the data while some sort of accumulator value is updated at each step.
The result value of the aggregation is the last computed value of the accumulator.

Let us look at some examples of how this is done.

### Applying a function many times

Perhaps the simplest nontrivial recursive type is the type of natural numbers (often denoted "`Nat`").
The type `Nat` is the least fixpoint of the type equation `Nat = F Nat` with `F = Optional`.
So, the Church encoding of `Nat` is the type `∀(r : Type) → (Optional r → r) → r`.

It is more convenient to use the curried form of the Church encoding.
The function type `Optional r → r` is isomorphic to the pair `(r, r → r)`.
If we curry the function type `(Optional r → r) → r`, we obtain `r → (r → r) → r`.
In this way, we derive the Church encoding for `Nat` as it is often shown in tutorials:
```dhall
let Nat = ∀(r : Type) → r → (r → r) → r
```

Values of type `Nat` must be functions of the form `λ(r : Type) → λ(x : r) → λ(f : r → r) → ???`.
Because the type `r` is arbitrary, the only way such a function could work is by applying `f` to the value `x`, then applying `f` to the result, etc., repeating this procedure a certain number of times.
An example is:
```dhall
let three : Nat = λ(r : Type) → λ(x : r) → λ(f : r → r) → f (f (f x))
```
This code applies `f` three times.

It can be proved that any value of type `Nat` must be of this form, applying `f` a certain (hard-coded) number of times.
This follows from the fact that the Church encoding is isomorphic to the least fixpoint of the type equation `Nat = Optional Nat`.

Given a value `n : Nat`, a type `A`, a value `a : A`, and a function `f : A → A`, we can write the expression `n A a f`.
That expression will evaluate to `f (f (... (f a) ... ))`, where `f` is applied `n` times.

This computation can be seen as a fold-like aggregation that accumulates a value of type `A` by repeatedly applying the function `f`.
We could implement a function `Nat/fold` with a fold-like type signature:
```dhall
let Nat/fold : Nat → ∀(r : Type) → (r → r) → r → r
  = λ(n : Nat) → λ(r : Type) → λ(update : r → r) → λ(init : r) →
    n r init update
```

In principle, natural numbers and all their standard arithmetical operations (addition, multiplication and so on) can be implemented purely in terms of the Church-encoded type `Nat`.
For efficiency, Dhall implements natural numbers as a built-in type `Natural` with the built-in function `Natural/fold` instead of using the Church encoding.

### Adding values in a list

Suppose we have a value `list` in the curried-form Church encoding of `ListInt`:

```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r
let list : ListInt = ???
```

Suppose our task is to compute the sum of the absolute values of all integers in `list`.
So, we need to implement a function `sumListInt : ListInt → Natural`.
An example test could be:

```dhall
let sumListInt : ListInt → Natural = ???
let example1 : ListInt = cons +123 (cons -456 (cons +789 nil))
let _ = assert : sumListInt example1 ≡ 1368
```

The function `sumListInt` is a "fold-like" aggregation operation.
To run the aggregation, we need to apply the value `list` to some arguments.
What are those arguments?

The type `ListInt` is a curried function with three arguments.

The first argument must be the type `r` of the result value; in our case, we need to set `r = Natural`.

The second argument is of type `r` and the third argument of type `Integer → r → r`.
In our case, these types become `Natural` and `Integer → Natural → Natural`.

So, it remains to supply those arguments that we will call `init : Natural` and `update : Integer → Natural → Natural`.
The code of `sumListInt` will look like this:

```dhall
let init : Natural = ???
let update : Integer → Natural → Natural = ???
let sumListInt : ListInt → Natural = λ(list : ListInt) → list Natural init update
```

The meaning of `init` is the result of `sumListInt` when the list is empty.
The meaning of `update` is the next accumulator value (of type Natural) computed from a current item from the list (of type `Integer`) and a value of type `Natural` that has been accumulated so far (by aggregating the tail of the list).

In our case, it is natural to set `init` is zero.
The `update` function is implemented via the standard library function `Integer/abs`:

```dhall
let Integer/abs = https://prelude.dhall-lang.org/Integer/abs
let update : Integer → Natural → Natural
  = λ(i : Integer) → λ(previous : Natural) → previous + Integer/abs i
```

The complete test code is:

```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r
let nil : ListInt
   = λ(r : Type) → λ(a1 : r) → λ(a2 : Integer → r → r) → a1
let cons : Integer → ListInt → ListInt
   = λ(n : Integer) → λ(c : ListInt) → λ(r : Type) → λ(a1 : r) → λ(a2 : Integer → r → r) →
     a2 n (c r a1 a2)
```

```dhall
let Integer/abs = https://prelude.dhall-lang.org/Integer/abs
let update : Integer → Natural → Natural
  = λ(i : Integer) → λ(previous : Natural) → previous + Integer/abs i
let sumListInt : ListInt → Natural
  = λ(list : ListInt) → list Natural 0 update
```

```dhall
let example1 : ListInt = cons +123 (cons -456 (cons +789 nil))
let _ = assert : sumListInt example1 ≡ 1368
```

### Pretty-printing a binary tree

Consider the curried form of the Church encoding for binary trees with `Text`-valued leaves:

```dhall
let TreeText = ∀(r : Type) → (Text → r) → (r → r → r) → r
```

The task is to print a text representation of the tree where branching is indicated via nested parentheses, such as `"((a b) c)"`.
The result will be a function `printTree` whose code needs to begin like this:

```dhall
let printTree : TreeText → Text = λ(tree: ∀(r : Type) → (Text → r) → (r → r → r) → r) → ???
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

let test = assert : printTree example2 ≡ "((a b) c)"
```

In a similar way, many recursive functions can be reduced to fold-like operations and then implemented for Church-encoded data non-recursively.

### Where did the recursion go?

The technique of Church encoding is not obvious and may be perplexing.
If we are actually implementing recursive types and recursive functions, why do we no longer see any recursion or iteration in the code?
Specifically, in the code of `sumListInt` and `printTree`, where are the parts that iterate over the data?

Indeed, the functions `sumListInt` and `printTree` are _not_ recursive.
These functions do perform a kind of iteration over the data stored in the list or in the tree.
But the iteration is not performed via loops or recursion.
Instead, iteration is _hard-coded_ in the values `list : ListInt` and `tree: TreeText`.

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

The function `example1` includes nested calls to `a1` and `a2`, which correspond to `ListInt`'s constructors (`nil` and `cons`).
The code of `example1` applies the function `a2` three times, which corresponds to having three items in the list.
But there is no loop or iteration in the code of `example1`.
It is just _hard-coded_ in the body of `example1` that `a2` must be applied three times to some arguments.

When we compute an aggregation such as `sumListInt example1`, we apply `example1` to some arguments.
The last of those arguments is a certain function we called `update`.
The code of `example1` will call `update` three times, as we have seen.
This is how the Church encoding performs iterative computations.

The value `example2` shown above works in a similar way.
If we expand the constructors `branch` and `leaf`, we find that `example2` is a higher-order function:

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
A data structure that contains, say, 1000 data values is Church-encoded into a certain higher-order function (whose arguments are again functions).
The body of that higher-order function contains no loops; it is just hard-coded to apply its argument functions 1000 times.

That is why the Church encoding guarantees that all recursive structures are finite and all operations on those structures will terminate.
For this reason, Dhall is able to accept Church encodings of recursive types and perform iterative and recursive operations on Church-encoded data without compromising any safety guarantees.

### Example: Size and depth of binary trees
Let us see how to compute the size of Church-encoded trees.

We consider binary trees with `Natural`-valued leaves.
The corresponding type `TreeNat` is defined by:

```dhall
let TreeNat = ∀(r : Type) → (Natural → r) → (r → r → r) → r
```
Values of this type can store one or more `Natural` numbers.
The present task is to compute various numerical measures characterizing the tree's stored data.

We will implement three computations:

- The sum of all natural numbers stored in the tree. (`treeSum`)
- The total number of data items in the tree. (`treeCount`)
- The maximum depth of leaves. (`treeDepth`)

Each computation is a fold-like function, so we will implement all of them via similar-looking code:

```dhall
let leafSum : Natural → Natural = ???
let branchSum : Natural → Natural → Natural = ???
let treeSum : TreeNat → Natural = λ(tree : TreeNat) → tree Natural leafSum branchSum

let leafCount : Natural → Natural = ???
let branchCount : Natural → Natural → Natural = ???
let treeCount : TreeNat → Natural = λ(tree : TreeNat) → tree Natural leafCount branchCount

let leafDepth : Natural → Natural = ???
let branchDepth : Natural → Natural → Natural = ???
let treeDepth : TreeNat → Natural = λ(tree : TreeNat) → tree Natural leafDepth branchDepth
```

The difference is only in the definitions of the functions `leafSum`, `branchSum`, and so on.

For the calculation of the sum of all numbers in a tree, `leafSum` should return just the number stored in a leaf, and `branchSum` should return the sum of the two numbers in the branches.
(During evaluation, those two numbers will be equal to the recursively computed sums over the two sub-trees.)
So, we write:
```dhall
let leafSum : Natural → Natural = λ(leaf : Natural) → leaf
let branchSum : Natural → Natural → Natural = λ(left : Natural) → λ(right : Natural) → left + right
let treeSum : TreeNat → Natural = λ(tree : TreeNat) → tree Natural leafSum branchSum
```

For the calculation of the total number of data items, we count each leaf as one item.
The number of items in the two branches needs to be added together.

```dhall
let leafCount : Natural → Natural = λ(leaf : Natural) → 1
let branchCount : Natural → Natural → Natural = λ(left : Natural) → λ(right : Natural) → left + right
let treeCount : TreeNat → Natural = λ(tree : TreeNat) → tree Natural leafCount branchCount
```

For the calculation of the depth, each leaf contributes 0 while each branch contributes 1 plus the maximum of the depths of the two subtrees.
```dhall
let Natural/max = https://prelude.dhall-lang.org/Natural/max
let leafDepth : Natural → Natural = λ(leaf : Natural) → 0
let branchDepth : Natural → Natural → Natural = λ(left : Natural) → λ(right : Natural) → 1 + Natural/max left right
let treeDepth : TreeNat → Natural = λ(tree : TreeNat) → tree Natural leafDepth branchDepth
```

To test this code, let us implement the constructors and create some `TreeNat` values.
```dhall
let leafTreeNat : Natural → TreeNat = λ(n : Natural) → λ(r : Type) → λ(leaf : Natural → r) → λ(branch : r → r → r) → leaf n
let branchTreeNat : TreeNat → TreeNat → TreeNat = λ(left : TreeNat) → λ(right : TreeNat) → λ(r : Type) → λ(leaf : Natural → r) → λ(branch : r → r → r) → branch (left r leaf branch) (right r leaf branch)
let tree1 : TreeNat = leafTreeNat 10
let tree123 : TreeNat = branchTreeNat (leafTreeNat 10) (branchTreeNat (leafTreeNat 20) (leafTreeNat 30))
```

Now we can compute the various numerical metrics for the example trees:

```dhall
let _ = assert : treeSum tree1 ≡ 10
let _ = assert : treeSum tree123 ≡ 60
let _ = assert : treeCount tree1 ≡ 1
let _ = assert : treeCount tree123 ≡ 3
let _ = assert : treeDepth tree1 ≡ 0
let _ = assert : treeDepth tree123 ≡ 2
```


### Pattern matching via "unfix"

When working with recursive types in ordinary functional languages, one often uses pattern matching.
For example, here is a simple Haskell function that detects whether a given tree is a single leaf:

```haskell
-- Haskell.
data TreeInt = Leaf Int | Branch TreeInt TreeInt

isSingleLeaf: TreeInt -> Bool
isSingleLeaf t = case t of
    Leaf _ -> true
    Branch _ _ -> false
```

Further examples are Haskell functions that return the head or the tail of a list if the list is non-empty:

```haskell
-- Haskell.
headMaybe :: [a] -> Maybe a
headMaybe []     = Nothing
headMaybe (x:xs) = Just x

tailMaybe :: [a] -> Maybe [a]
tailMaybe []     = Nothing
tailMaybe (x:xs) = Just xs
```

The Dhall translations of `TreeInt` and `ListInt` are Church-encoded types:

```dhall
let F = λ(r : Type) → < Leaf : Integer | Branch : { left : r, right : r } >
let fmapF : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fa : F a) →
    merge {
      Leaf = λ(t : Integer) → (F b).Leaf t,
      Branch = λ(br : { left : a, right : a }) → (F b).Branch { left = f br.left, right = f br.right },
    } fa
let C = ∀(r : Type) → (F r → r) → r

let TreeInt = ∀(r : Type) → (F r → r) → r
```
and
```dhall
let F = λ(r : Type) → < Nil | Cons : { head : Integer, tail : r } >
let ListInt = ∀(r : Type) → (F r → r) → r
```

Values of type `TreeInt` and `ListInt` are functions, and one cannot perform pattern matching on function values.
How can we implement functions like `isSingleLeaf` and `headMaybe` in Dhall?

The general method for translating pattern matching into Church-encoded types `C` consists of two steps.
The first step is to apply the general function `unfix` of type `C → F C`.
The function `unfix` is available for all Church-encoded types; we have shown its implementation above.

Given a value `c : C` of a Church-encoded type, the value `unfix c` will have type `F C`, which is typically a union type.
The second step is to use the ordinary pattern-matching (Dhall's `merge`) on that value.


This technique allows us to translate `isSingleLeaf` and `headMaybe` to Dhall.

For `C = TreeInt`, the type `F C` is the union type `< Leaf: Integer | Branch : { left : TreeInt, right : TreeInt } >`. The function `isSingleLeaf` is
implemented via pattern matching on that type:

```dhall
let F = λ(r : Type) → < Leaf: Integer | Branch : { left : r, right : r } >

let TreeInt = LFix F

let functorF : Functor F = {
    fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fa : F a) → merge {
      Leaf = (F b).Leaf,
      Branch = λ(branch : { left : a, right : a }) → (F b).Branch { left = f branch.left, right = f branch.right }
    } fa
}

-- Assume the definition of `unfix` as shown above.

let isSingleLeaf : TreeInt → Bool = λ(c : TreeInt) →
    merge {
      Leaf = λ(_ : Integer) → True,
      Branch = λ(_ : { left : TreeInt, right : TreeInt }) → False
    } (unfix F functorF c)
```

For `C = ListInt`, the type `F C` is the union type `< Nil | Cons : { head : Integer, tail : ListInt } >`. The function `headOptional` that replaces
Haskell's `headMaybe` and `tailMaybe` are rewritten in Dhall like this:

```dhall
let F = λ(r : Type) → < Nil | Cons : { head : Integer, tail : r } >

let ListInt = LFix F

let functorF : Functor F = {
    fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fa : F a) → merge {
      Nil = (F b).Nil,
      Cons = λ(pair : { head : Integer, tail : a }) → (F b).Cons (pair // { tail = f pair.tail })
    } fa
  }
-- Constructors.
let cons = λ(h : Integer) → λ(t : ListInt) →
  λ(r : Type) → λ(frr : F r → r) → frr ((F r).Cons { head = h, tail = t r frr})
let nil = λ(r : Type) → λ(frr : F r → r) → frr (F r).Nil

-- Assume the definition of `unfix` as shown above.

let headOptional : ListInt → Optional Integer = λ(c : ListInt) →
    merge {
      Cons = λ(list : { head : Integer, tail : ListInt }) → Some (list.head),
      Nil = None Integer
    } (unfix F functorF c)

let tailOptional : ListInt → Optional ListInt = λ(c : ListInt) →
    merge {
      Cons = λ(list : { head : Integer, tail : ListInt }) → Some (list.tail),
      Nil = None ListInt
    } (unfix F functorF c)

-- Run some tests:
let _ = assert : headOptional (cons -456 (cons +123 nil)) ≡ Some -456
let _ = assert : tailOptional (cons -456 (cons +123 nil)) ≡ Some (cons +123 nil)
```

### Performance of "unfix"

Note that `unfix` is implemented by applying the Church-encoded argument to some function.
In practice, this means that `unfix` will need to traverse the entire data structure.
This behavior may be counter-intuitive.
For example, `headOptional` (as shown above) will need to traverse the entire list of type `ListInt` before
it can determine whether the list is not empty.

Church-encoded data are higher-order functions, and it is not possible to pattern match on them directly.
The data traversal is necessary to enable pattern matching for Church-encoded types.

As a result, the performance of programs will be often slow when working with large Church-encoded data structures.
For example, concatenating or reversing lists of type `ListInt` takes time quadratic in the list length.

TODO:validate these statements!

## Church encodings for more complicated types

### Mutually recursive types

If two or more types are defined recursively through each other, one needs a separate pattern functor and a separate the Church encoding for each of the types.

As an example, consider this Haskell definition:

```haskell
-- Haskell.
data Layer = Name String | OneLayer Layer | TwoLayers Layer2 Layer2
data Layer2 = Name2 String | ManyLayers [ Layer ]
```

The type `Layer` is defined via itself and `Layer2`, while `Layer2` is defined via `Layer`.

We need two pattern functors (`F1` and `F2`) to describe this definition. In terms of the pattern functors, the type definitions should look like this:

```haskell
-- Haskell.
data Layer = Layer (F1 Layer Layer2)
data Layer2 = Layer2 (F2 Layer Layer2)
```

We will achieve this formulation if we define `F1` and `F2` by:

```haskell
-- Haskell.
data F1 a b = Name String |  OneLayer a | TwoLayers b b
data F2 a b = Name2 String | ManyLayers [ a ]
```

The pattern functors `F1` and `F2` are non-recursive type constructors with two type parameters each. The Dhall code for this example is:

```dhall
let F1 = λ(a : Type) → λ(b : Type) → < Name : Text | OneLayer : b | TwoLayers: { left : b, right : b } >
let F2 = λ(a : Type) → λ(b : Type) → < Name2 : Text | ManyLayers : List a >
```

Then we define the types `Layer` and `Layer2` in Dhall via the Church encodings:

```dhall
let Layer  = ∀(a : Type) → ∀(b : Type) → (F1 a b → a) → (F2 a b → b) → a
let Layer2 = ∀(a : Type) → ∀(b : Type) → (F1 a b → a) → (F2 a b → b) → b
```

The definitions appear very similar, except for the output types of the functions.
But that difference is crucial.

See the Appendix "Naturality and Parametricity" for a proof that the Church encodings of that form indeed represent mutually recursive types.

### Recursive type constructors

Typically, a recursive definition of a type constructor is not of the form `T = F T` but of the form `T a = F (T a) a`, or `T a b = F (T a b) a b`, etc., with extra type parameters.
To describe such type equations, the pattern functor `F` must have more type parameters.

For example, consider this Haskell definition of a binary tree with leaves of type `a`:

```haskell
-- Haskell.
data Tree a = Leaf a | Branch (Tree a) (Tree a)
```

The corresponding pattern functor `F` is:

```haskell
-- Haskell.
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
But the quantified type `∀(r : Type)` is not a type parameter of `Tree`; it is part of the definition of the type of `Tree`.

For type constructors with one type parameter, we may define a convenience method `LFixT` that computes the type of the corresponding Church encoding:

```dhall
let LFixT : (Type → Type → Type) → Type → Type
  = λ(F : Type → Type → Type) → λ(a : Type) → ∀(r : Type) → (F a r → r) → r
```

Then the last definition of `Tree` is equivalently written as `let Tree = LFixT F`.

To see that this is the same Church encoding as before, we can express `LFixT` through `LFix`:

```dhall
let LFixT : (Type → Type → Type) → Type → Type
  = λ(F : Type → Type → Type) → λ(a : Type) → LFix (F a)
```

The Church encoding works similarly for type constructors with two or more type parameters.
Consider a Haskell definition of a binary tree with two type parameters and two different kinds of leaf:

```haskell
-- Haskell.
data TreeAB a b = LeafA a | LeafB b | Branch (TreeAB a b) (TreeAB a b)
```

The corresponding pattern functor is:

```haskell
-- Haskell.
data F a b r = LeafA a | LeafB b | Branch r r
```

The Dhall code for this example is:

```dhall
let F = λ(a : Type) → λ(b : Type) → λ(r : Type) →
   < LeafA : a | LeafB : b | Branch : { left : r, right : r } >
let TreeAB = λ(a : Type) → λ(b : Type) → ∀(r : Type) → (F a b r → r) → r
```

### Example: Church-encoded list

The `List` functor is a built-in type in Dhall.
As a pedagogical example, let us now implement an equivalent type constructor using Church encoding.

The recursive type equation for `List` has the form (in Haskell-like syntax):

`List a = Nil | Cons a (List a)`

The right-hand side is equivalent to the Dhall type `Optional (Pair a (List a))`.
So, we can represent the type equation for `List` in the form `List a = FList a (List a)` if we define `FList` by:

```dhall
let FList = λ(a : Type) → λ(r : Type) → Optional (Pair a r)
```
Then the recursive type `CList` is expressed as:
```dhall
let CList = λ(a : Type) → LFix (FList a)
```
or equivalently:
```dhall
let CList = LFixT FList
```

To create values of type `CList x` (where `x` is a specific type), we will implement helper functions `nilCList` and `consCList`.
Rather than coding those functions by hand, let us apply a general method for finding the constructors of a Church-encoded fixpoint type.
That method uses the generic `fix` function for the fixpoint type.


We have seen the implementation of `fix : F C → C` for a simple fixpoint type `C` in the chapter "Church encodings for recursive types".
For the type constructor `CList`, the corresponding function `fixCList` has the type signature `FList a (CList a) → CList a` and is implemented like this:
```dhall
let bifunctorFList : Bifunctor FList = { bimap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(c : Type) → λ(d : Type) → λ(g : c → d) → Optional/map (Pair a c) (Pair b d) (λ(xy : Pair a c) → { _1 = f xy._1, _2 = g xy._2 }) }
let fixCList
  : ∀(a : Type) → FList a (CList a) → CList a
  = λ(a : Type) → λ(fc : FList a (CList a)) →
    λ(r : Type) → λ(frr : FList a r → r) →
      let c2r : CList a → r = λ(ca : CList a) → ca r frr
      let fmap_c2r : FList a (CList a) → FList a r = bifunctorFList.bimap a a (identity a) (CList a) r c2r
      let fr : FList a r = fmap_c2r fc
      in frr fr
```
Now we write the constructors for `CList`-typed lists:

```dhall
let nilCList : ∀(a : Type) → CList a = λ(a : Type) → fixCList a (None (Pair a (CList a)))
let consCList : ∀(a : Type) → a → CList a → CList a = λ(a : Type) → λ(head : a) → λ(tail : CList a) → fixCList a (Some { _1 = head, _2 = tail })
```

Another useful function is `CList/show`.
We will implement it in a simple way that leaves a trailing comma in the lists.
```dhall
let CList/show : ∀(a : Type) → Show a → CList a → Text
  = λ(a : Type) → λ(showA : Show a) → λ(clist : CList a) →
    let printFList
      : FList a Text → Text
      = λ(flist : FList a Text) → Optional/default Text "" (Optional/map (Pair a Text) Text (λ(p : Pair a Text) → "${showA.show p._1}, ${p._2}") flist)
    in "[ ${clist Text printFList}]"
```

As an example of using these tools, let us write a `CList` value corresponding to the list `[ 1, 3, 4, 5 ]` and print it:
```dhall
let exampleCList1345 : CList Natural = consCList Natural 1 (consCList Natural 3 (consCList Natural 4 (consCList Natural 5 (nilCList Natural))))
let _ = assert : CList/show Natural { show = Natural/show } exampleCList1345 ≡ "[ 1, 3, 4, 5, ]"
```


### Example: Concatenating and reversing non-empty lists

Dhall's `List` data structure already has concatenation and reversal operations (`List/concat` and `List/reverse`).
To practice implementing those operations for a Church-encoded data type, consider _non-empty_ lists (`NEL: Type → Type`) defined recursively as:

```haskell
-- Haskell.
data NEL a = One a | Cons a (NEL a)
```

The pattern functor corresponding to this definition is:

```haskell
-- Haskell.
data F a r = One a | Cons a r
```

Convert this definition to Dhall and write the corresponding Church encoding:

```dhall
let F = λ(a : Type) → λ(r : Type) → < One : a |  Cons : { head : a, tail: r } >
let NEL_F = λ(a : Type) → LFix (F a)
```

To construct values of types `NEL_F a`, we will write two functions that we will call `last` und `more`.
The result of `last Natural 1` should be be a one-element non-empty list containing the value `1`.
The result of `more Natural nel 1`, where `nel` is a non-empty list of type `NEL_F Natural`, should be the list `nel` with the value `1` prepended at the beginning.
So, we expect the type signatures of the functions `last` and `more` to be:
```dhall
let last : ∀(a : Type) → a → NEL_F a = ???
let more : ∀(a : Type) → NEL_F a → a → NEL_F a = ???
```

To implement these functions, we use the definition of `NEL_F` and figure out how to construct the suitable parts of the union type `F a r`.
The code is:
```dhall
let last : ∀(a : Type) → a → NEL_F a
  = λ(a : Type) → λ(x : a) → λ(r : Type) → λ(farr : F a r → r) → farr ((F a r).One x)
let more : ∀(a : Type) → a → NEL_F a → NEL_F a
  = λ(a : Type) → λ(x : a) → λ(nel : NEL_F a) → λ(r : Type) → λ(farr : F a r → r) → farr ((F a r).Cons { head = x, tail = nel r farr } )
let exampleNEL : NEL_F Natural = more Natural 1 (more Natural 2 (last Natural 3))
```

It is more convenient to work with non-empty lists if we define them without using union types or record types.
This is achieved if we use the _curried form_ of the Church encoding:

```dhall
let NEL = λ(a : Type) → ∀(r : Type) → (a → r) → (a → r → r) → r
```

The constructors for `NEL` are:

- a function (`one`) that creates a list consisting of one element
- a function (`consn`) that prepends a given value of type `a` to a non-empty list of type `NEL a`

Non-empty lists can be now built as `consn Natural 1 (consn Natural 2 (one Natural 3))`, and so on.

```dhall
let one : ∀(a : Type) → a → NEL a =
    λ(a : Type) → λ(x : a) → λ(r : Type) → λ(ar : a → r) → λ(_ : a → r → r) → ar x
let consn : ∀(a : Type) → a → NEL a → NEL a =
    λ(a : Type) → λ(x : a) → λ(prev : NEL a) → λ(r : Type) → λ(ar : a → r) → λ(arr : a → r → r) → arr x (prev r ar arr)
let example1 : NEL Natural = consn Natural 1 (consn Natural 2 (one Natural 3))
let example2 : NEL Natural = consn Natural 3 (consn Natural 2 (one Natural 1))
```

The folding function is just an identity function:

```dhall
let foldNEL : ∀(a : Type) → NEL a → ∀(r : Type) → (a → r) → (a → r → r) → r
  = λ(a : Type) → λ(nel : NEL a) → nel
```

To see that this is a "right fold", apply `foldNEL` to some functions `last : a → r` and `next : a → r → r` and a three-element list such as `example1`. The result
will be `next 1 (next 2 (last 3))`.
So, the first function evaluation is at the right-most element of the list, then the result is used with the next-but-last element, and so on.
This corresponds to the logic of a **right fold**.

Folding with arguments `one` and `consn` gives again the initial list:

```dhall
let test = assert : example1 ≡ foldNEL Natural example1 (NEL Natural) (one Natural) (consn Natural)
```

To concatenate two lists, we right-fold the first list and substitute the second list instead of the right-most element:

```dhall
let concatNEL: ∀(a : Type) → NEL a → NEL a → NEL a
  = λ(a : Type) → λ(nel1 : NEL a) → λ(nel2 : NEL a) →
        foldNEL a nel1 (NEL a) (λ(x : a) → consn a x nel2) (consn a)
let test = assert : concatNEL Natural example1 example2 ≡ consn Natural 1 (consn Natural 2 (consn Natural 3 (consn Natural 3 (consn Natural 2 (one Natural 1)))))
```

To reverse a list, we right-fold over it and accumulate a new list by appending elements to it.

So, we will need a new constructor (`nsnoc`) that appends a given value of type `a` to a list of type `NEL a`, rather than prepending as `consn` does.

```dhall
let nsnoc : ∀(a : Type) → a → NEL a → NEL a
  = λ(a : Type) → λ(x : a) → λ(prev : NEL a) →
    foldNEL a prev (NEL a) (λ(y : a) → consn a y (one a x)) (consn a)
let test = assert : example1 ≡ nsnoc Natural 3 (nsnoc Natural 2 (one Natural 1))
```

Now we can write the reversing function:

```dhall
let reverseNEL : ∀(a : Type) → NEL a → NEL a =
    λ(a : Type) → λ(nel : NEL a) → foldNEL a nel (NEL a) (one a) (nsnoc a)
let test = assert : reverseNEL Natural example1 ≡ example2
let test = assert : reverseNEL Natural example2 ≡ example1
```

The reversing function allows us to program a _left_ fold: just reverse the list and apply the right fold.
However, the resulting code works much slower than the right fold.
To see why, consider that `reverseNEL` uses `foldNEL` with an argument involving `nsnoc`, which itself also uses `foldNEL`.
So, `reverseNEL` involves two nested iterations over the list (a quadratic number of operations).


### Size and depth of generic Church-encoded data

The functions `concatNEL` and `reverseNEL` shown in the previous section are specific to list-like sequences and cannot be straightforwardly generalized to other recursive types, such as trees.

We will now consider functions that _can_ work with any Church-encoded type constructor.
Examples are functions that compute the total size and the recursion depth of a data structure.

In a previous section, we have seen such functions implemented for specific recursive types.
Now we will generalize those computations to arbitrary Church-encoded data types.
For that, it is more convenient to use the Church encoding definition via `LFix`, rather than via curried functions.

Suppose we are given an arbitrary pattern functor `F` with two type parameters.
It defines a type constructor `C` via Church encoding as:

```dhall
let F = λ(a : Type) → λ(r : Type) → ???
let C = λ(a : Type) → LFix (F a)
```

Typically, a value `p : C a` is a data structure that stores zero or more values of type `a`.
The "total size" of `p` is the number of the values of type `a` that it stores.
For example, if `p` is a list of 5 elements then the size of `p` is 5.
The size of a `TreeInt` value `branch (branch (leaf +10) (leaf +20)) (leaf +30)` is 3 because it stores three numbers.

The "depth" of `p` is the maximum depth of nested constructors required to build that value.
For example, if `p` is a `TreeInt` value `branch (branch (leaf +10) (leaf +20)) (leaf +30)` then the depth of `p` is 2.
The depth of a single-leaf tree (such as `leaf +10`) is 0.

The goal of this section is to implement these computations as generic functions `size` and `depth` that work in the same way for any Church-encoded data structure `p : C a`.

The functions `size` and `depth` will need to traverse the entire data structure and to accumulate a `Natural` value.
The only way of doing the traversal is to apply the Church-encoded value (`p : C a`) to some arguments.
In order to obtain a value of type `Natural`, we need to apply `p` to the type `Natural` and to a function of type `F a Natural → Natural`.
So, the code of both `size` and `depth` needs to have the form `size p = p Natural sizeF` and `depth p = p Natural depthF`, where `sizeF` and `depthF` are suitable functions of type `F a Natural → Natural`.

Let us begin with writing the code for `size` in more detail:

```dhall
let size : ∀(a : Type) → ∀(ca : C a) → Natural
  = λ(a : Type) → λ(ca : C a) →
    let sizeF : F a Natural → Natural = ???
    in ca Natural sizeF
```

A data structure of type `F a Natural` will typically store some values of type `a` and some values of type `Natural`.
The function `sizeF` should count the number of data items (of type `a`) stored in a value of type `F a Natural`.
The values of type `Natural` stored inside `F` represent the sizes of recursively nested data structures of type `C a`.
The sizes of those nested data structures have been already computed at the time when the function `sizeF` is being called.
(This is how the Church encoding implements working with recursive structures.)

So, for a given value `fa : f a Natural`, the result of `sizeF fa` needs to be equal to the number of values of type `a` stored in `fa` plus the sum of all natural numbers stored in `fa`.
It is clear that the function `sizeF` will need to be different for each pattern functor `F`.

For example, non-empty lists (`NEL`) are described by the pattern functor `F` such that `F a r = < One : a | Cons : { head : a, tail: r } >`.
The corresponding function `sizeF_NEL` is:

```dhall
let sizeF_NEL : ∀(a : Type) → < One : a | Cons : { head : a, tail: Natural } > → Natural
  = λ(a : Type) → λ(fa : < One : a | Cons : { head : a, tail: Natural } >) →
    merge {
      One = λ(x : a) → 1,
      Cons = λ(x : { head : a, tail: Natural }) → 1 + x.tail,
    } fa
```

Binary trees (with leaf values of an arbitrary type `a`) are described by the pattern functor `FTree a r = < Leaf : a | Branch : { left : r, right: r } >`.
The corresponding `sizeF` function is:

```dhall
let FTree = λ(a : Type) → λ(r : Type) → < Leaf : a | Branch : { left : r, right: r } >
let sizeF_Tree : ∀(a : Type) → FTree a Natural → Natural
  = λ(a : Type) → λ(fa : FTree a Natural) →
    merge {
      Leaf = λ(x : a) → 1,
      Branch = λ(x : { left : Natural, right: Natural }) → x.left + x.right,
    } fa
```

Having realized that `sizeF` needs to be defined separately for each pattern functor `F`, we now implement `size` as a function of `F` and of `sizeF`.
The type `C` will be expressed as `LFix F`.
We can write a generic implementation of `size` as:

```dhall
let size : ∀(F : Type → Type → Type) → ∀(a : Type) → ∀(sizeF : ∀(b : Type) → F b Natural → Natural) → LFix (F a) → Natural
  = λ(F : Type → Type → Type) → λ(a : Type) → λ(sizeF : ∀(b : Type) → F b Natural → Natural) → λ(ca : LFix (F a)) →
    ca Natural (sizeF a)
```

To test this code, let us compute the size of a non-empty list with three values:

```dhall
let exampleNEL3 : NEL_F Natural = more Natural 1 (more Natural 2 (last Natural 3))
let test =
  let F = λ(a : Type) → λ(r : Type) → < One : a | Cons : { head : a, tail: r } >
  in assert : 3 ≡ size F Natural sizeF_NEL exampleNEL3
```

Here is the size calculation for some example binary tree values:
```dhall
let Tree2 = λ(a : Type) → LFix (FTree a)
let leaf = λ(a : Type) → λ(x : a) → λ(r : Type) → λ(frr : FTree a r → r) → frr ((FTree a r).Leaf x)
let branch = λ(a : Type) → λ(x : Tree2 a) → λ(y : Tree2 a) → λ(r : Type) → λ(frr : FTree a r → r) → frr ((FTree a r).Branch { left = x r frr, right = y r frr } )
let exampleTree2 : Tree2 Natural = branch Natural (leaf Natural 1) (leaf Natural 2)
let _ = assert : 2 ≡ size FTree Natural sizeF_Tree exampleTree2
let exampleTree3 : Tree2 Natural = branch Natural (branch Natural (leaf Natural 1) (leaf Natural 2)) (leaf Natural 3)
let _ = assert : 3 ≡ size FTree Natural sizeF_Tree exampleTree3
```

Turning now to the depth calculation, we proceed similarly and find that the only difference is in the `sizeF` function.
Instead of `sizeF` described above, we need a function we may call `depthF` with the same type signature `∀(b : Type) → F b Natural → Natural`.
The depth calculation will use that function instead of `sizeF`:

```dhall
let depth : ∀(F : Type → Type → Type) → ∀(a : Type) → ∀(depthF : ∀(b : Type) → F b Natural → Natural) → LFix (F a) → Natural
  = λ(F : Type → Type → Type) → λ(a : Type) → λ(depthF : ∀(b : Type) → F b Natural → Natural) → λ(ca : LFix (F a)) →
    ca Natural (depthF a)
```

For the correct calculation of depth of a value `p : F a Natural`, the value `depthF p` should equal `1` plus the maximum of all values of type `Natural` that are stored in the data structure `p`.
If no such values are present, `depthF p` returns `0`.

For non-empty lists (and also for empty lists), the `depthF` function is the same as `sizeF` because the recursion depth is equal to the length of the list.

For binary trees, the corresponding `depthF` is defined by:

```dhall
let depthF_Tree : ∀(a : Type) → < Leaf : a | Branch : { left : Natural, right: Natural } > → Natural
  = λ(a : Type) → λ(fa : < Leaf : a | Branch : { left : Natural, right: Natural } >) →
    merge {
      Leaf = λ(x : a) → 0,
      Branch = λ(x : { left : Natural, right: Natural }) → 1 + Natural/max x.left x.right,
    } fa
```

To test:

```dhall
let _ = assert : 1 ≡ depth FTree Natural depthF_Tree exampleTree2
let _ = assert : 2 ≡ depth FTree Natural depthF_Tree exampleTree3
```

One may notice that the implementations of `size` and `depth` are actually the same code.
The only difference is the argument given as either `sizeF` or `depthF`.
Also, the functions `sizeF` are `depthF` are quite similar.

It turns out that we can implement the functions `sizeF` and `depthF` for arbitrary recursive types, as long as the pattern functor `F` has `Foldable` and `Functor` typeclass evidence values with respect to _both_ type parameters.
Let us now see how that works.

The functions `sizeF` and `depthF` have type `F a Natural → Natural` and may need to count the number of values of type `a` or iterate over all values of type `Natural` stored inside the data structure of type `F a Natural`.

If `F x y` is a foldable functor with respect to both `x` and `y`, it means that we can iterate over all values of type `x` and separately over all values of type `y` stored in `F x y`.
For convenience, let us define the types of the `Foldable` instances corresponding to the two functors obtained from the bifunctor `F` by fixing one of its type parameters:

```dhall
let Foldable1 = λ(F : Type → Type → Type) → ∀(b : Type) → Foldable (λ(a : Type) → F a b)
let Foldable2 = λ(F : Type → Type → Type) → ∀(a : Type) → Foldable (λ(b : Type) → F a b)
```

Two `Foldable` instances give us two `toList` functions (having types `F a b → List a` and `F a b → List b`).
Those functions allow us to extract two lists (of types `List a` and `List Natural`) from a value of type `F a Natural`.
With that, it is straightforward to perform the computations required for `sizeF` and `depthF`.
The code is:

```dhall
let Natural/listMax = https://prelude.dhall-lang.org/Natural/listMax
let Natural/sum = https://prelude.dhall-lang.org/Natural/sum
let sizeAndDepthF
  : ∀(F : Type → Type → Type) → Bifunctor F → Foldable1 F → Foldable2 F → ∀(c : Type) → F c Natural → { size : Natural, depth : Natural }
  = λ(F : Type → Type → Type) → λ(bifunctorF : Bifunctor F) → λ(foldableF1 : Foldable1 F) → λ(foldableF2 : Foldable2 F) → λ(c : Type) → λ(p : F c Natural) →
    let listC : List c = (foldableF1 Natural).toList c p
    let listNatural : List Natural = (foldableF2 c).toList Natural p
    let size = List/length c listC + Natural/sum listNatural
    let depth = Optional/default Natural 0 (Natural/listMax listNatural)
    in { size, depth }
```

Now we can implement the fully generic `size` and `depth` functions that work for any Church-encoded type constructor.

```dhall
let size
  : ∀(F : Type → Type → Type) → Bifunctor F → Foldable1 F → Foldable2 F → ∀(a : Type) → LFix (F a) → Natural
  = λ(F : Type → Type → Type) → λ(bifunctorF : Bifunctor F) → λ(foldableF1 : Foldable1 F) → λ(foldableF2 : Foldable2 F) → λ(a : Type) → λ(ca : LFix (F a)) →
   let sizeF = λ(fa : F a Natural) → (sizeAndDepthF F bifunctorF foldableF1 foldableF2 a fa).size
   in ca Natural sizeF
let depth
  : ∀(F : Type → Type → Type) → Bifunctor F → Foldable1 F → Foldable2 F → ∀(a : Type) → LFix (F a) → Natural
  = λ(F : Type → Type → Type) → λ(bifunctorF : Bifunctor F) → λ(foldableF1 : Foldable1 F) → λ(foldableF2 : Foldable2 F) → λ(a : Type) → λ(ca : LFix (F a)) →
    let depthF = λ(fa : F a Natural) → (sizeAndDepthF F bifunctorF foldableF1 foldableF2 a fa).depth
    in ca Natural depthF
```

TODO: test examples for binary tree

### Implementing "fmap" for Church-encoded functors

TODO: reformulate in terms of the Functor typeclass

A type constructor `F` is a **covariant functor** if it admits an `fmap` method with the type signature:

```dhall
let fmap : ∀(a : Type) → ∀(b : Type) → (a → b) → F a → F b = ???
```
satisfying the appropriate laws (the identity and the composition laws).

For convenience, we will use the type constructor `FmapT` defined earlier and write the type signature of `fmap` as
`fmap : FmapT F`.

Church-encoded type constructors such as lists and trees are covariant in their type arguments.

As an example, let us implement the `fmap` method for the type constructor `Tree` in the curried Church encoding:

```dhall
let Tree = λ(a : Type) → ∀(r : Type) → (a → r) → (r → r → r) → r
let fmapTree : FmapT Tree
   = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(treeA : Tree a) →
     λ(r : Type) → λ(leafB : b → r) → λ(branch : r → r → r) →
       let leafA : a → r = λ(x : a) → leafB (f x)
       in treeA r leafA branch
```

This code only needs to convert a function argument of type `b → r` to a function of type `a → r`.
All other arguments are just copied over.

We can generalize this code to the Church encoding of an arbitrary recursive type constructor with a pattern functor `F`.
We need to convert a function argument of type `F b r → r` to one of type `F a r → r`.
This can be done if `F` is a covariant bifunctor with a known `bimap` function (which we call `bimap_F`).

The code is:

```dhall
let F : Type → Type → Type = λ(a : Type) → λ(b : Type) → ??? -- Define the pattern functor.
let bimap_F
  : ∀(a : Type) → ∀(c : Type) → (a → c) → ∀(b : Type) → ∀(d : Type) → (b → d) → F a b → F c d
  = ??? -- Define the bimap function for F.
let C : Type → Type = λ(a : Type) → ∀(r : Type) → (F a r → r) → r

let fmapC
  : ∀(a : Type) → ∀(b : Type) → (a → b) → C a → C b
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(ca : C a) →
    λ(r : Type) → λ(fbrr : F b r → r) →
      let farr : F a r → r = λ(far : F a r) →
        let fbr : F b r = bimap_F a b f r r (identity r) far
        in fbrr fbr
      in ca r farr
```

We can generalize this code to a function that transforms an arbitrary bifunctor `F` into a `Functor` evidence for the type constructor `LFix (F a)`:

```dhall
let bifunctorLFix
  : ∀(F : Type → Type → Type) → Bifunctor F → Functor (λ(a : Type) → LFix (F a))
  = λ(F : Type → Type → Type) → λ(bifunctorF : Bifunctor F) → {
    fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(ca : LFix (F a)) →
          λ(r : Type) → λ(fbrr : F b r → r) →
            let farr : F a r → r = λ(far : F a r) →
              let fbr : F b r = bifunctorF.bimap a b f r r (identity r) far
              in fbrr fbr
            in ca r farr
  }
```

In later chapters of this book, we will go systematically through various typeclasses such as `Functor`, `Applicative`, and so on.
Each time, if possible, we will implement typeclass evidence values for Church-encoded type constructors.

### Data structures at type level

Dhall's built-in type constructors  `List` and `Optional` only work with **ground types** (that is, of type `Type`).
One can create a list of Booleans, such as `[ False, True ]`, or an `Optional` value storing a number, such as `Some 123`.
But it is a type error in Dhall to write `[ Bool, Natural ]` with the intention of creating a list of type symbols `Bool` and `Natural`.
One also cannot write `Some Text` with the intention of creating an `Optional` value storing the `Text` type symbol.

Let us begin by implementing a type-level analog of the `Optional` type.
For that, we will use a union type that stores data of an arbitrary kind.
We first define a general type `OptionalK` that accepts an arbitrary kind as a parameter.
Then we define a more limited `OptionalT` that works with types such as `Bool` or `Natural`, which is perhaps the most useful case.
```dhall
let OptionalK : Kind → Kind = λ(k : Kind) → < None | Some : k >
let OptionalT = OptionalK Type
let example1 = OptionalT.None
let example2 = OptionalT.`Some` Natural
let example3 = (OptionalK (Type → Type)).`Some` List
```
Note that we have to backquote the constructor name `Some` in `OptionalK`.
Using `Some` without backquotes would lead to a parse error in Dhall, because `Some` is a built-in keyword.

We can implement a method similar to `Optional/default` and perform typechecking against types stored in an `OptionalT` structure:

```dhall
let OptionalK/default
  : ∀(k : Kind) → k → OptionalK k → k
  = λ(k : Kind) → λ(default : k) → λ(opt: OptionalK k) →
    merge { None = default
          , Some = λ(t : k) → t
          } opt
let OptionalT/default = OptionalK/default Type
let someType1 = OptionalT/default Bool example1 -- someType1 is Bool
let someType2 = OptionalT/default Bool example2 -- someType2 is Natural
let _ = True : someType1 -- This typechecks because someType1 is Bool.
let _ = 123 : someType2 -- This typechecks because someType1 is Natural.
```

We now turn to the type-level `List` analogs.
This is more difficult because `List` requires a recursive definition.

Recursive "type-level" data structures can be implemented via the Church encoding technique.
We use the same pattern as before, except that stored data items will now have type `Type`.

Begin by looking at the curried Church encoding of the list of integers:


```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r
let exampleListInt = λ(r : Type) → λ(nil : r) → λ(cons : Integer → r → r) → cons +1 (cons +2 (cons +3 nil))
```

To generalize this data structure to the level of types, we replace `Type` by `Kind` and `Integer` by `Type`.
The resulting code is:

```dhall
let ListT = ∀(r : Kind) → ∀(nil : r) → ∀(cons : Type → r → r) → r
let exampleListT = λ(r : Kind) → λ(nil : r) → λ(cons : Type → r → r) → cons Bool (cons Natural (cons Text nil))  -- Similar to [Bool, Natural, Text].
```
The value `exampleListT` is a type-level list: it defines a list of three _type symbols_.
Note that Dhall's built-in `List` constructor does not support type symbols as list elements; the syntax `[Bool, Natural, Text]` is an error.

To illustrate how to work with a type-level list, let us write a function that extracts the head from a list.
The result will be an `OptionalT` value.

```dhall
let headOptionalT : ListT → OptionalT
  = λ(list : ListT) →
    let cons = λ(t : Type) → λ(_ : OptionalT) → OptionalT.`Some` t
    let nil = OptionalT.None
    in list OptionalT nil cons
```
To extract the type out of an `OptionalT`, we need to provide a default type and use `OptionalT/default`.
The head of the list `exampleListT` is the type `Bool`.
To verify that, we write a type annotation for a `Bool` value:
```dhall
let someBool = headOptionalT exampleListT  -- This is equal to OptionalT.`Some` Bool.
let shouldBeBoolType = OptionalT/default {} someBool
let _ = False : shouldBeBoolType
```

The type-level list `ListT` is hard-coded to store symbols of kind `Type`.
We cannot use it to store type constructors.
For that, we need to define another type-level list type; let's call it `ListTT`.

```dhall
let ListTT = ∀(r : Kind) → ∀(nil : r) → ∀(cons : (Type → Type) → r → r) → r
let exampleListTT = λ(r : Kind) → λ(nil : r) → λ(cons : (Type → Type) → r → r) → cons Optional (cons List nil)  -- Similar to [Optional, List].
```

The constructors `ListT` and `ListTT` define lists whose elements have a hard-coded _kind_.
To see how we could generalize to arbitrary kinds, compare the Church encoding of `ListInt` with that of a generic list.
The difference is that the hard-coded `Integer` type is replaced by a type parameter:

```dhall
let ListInt = ∀(r : Type) → r → (Integer → r → r) → r
let ListGeneric = λ(a : Type) → ∀(r : Type) → r → (a → r → r) → r
```

If we try to use the same technique with `ListT`, we would have to replace `Type` by `Kind`:
```dhall
let ListT = ∀(r : Kind) → r → (Type → r → r) → r
let ListTGeneric = λ(k : Kind) → ∀(r : Kind) → r → (k → r → r) → r  -- Type error: `Sort` does not have a type
```

The error says that `ListTGeneric` cannot be typed.
To understand why, let us look at the type of `ListT`.
We can verify that the type constructor `ListT` has itself the type `Sort`:
```dhall
let _ = ListT : Sort
```

The appearance of `Sort` is usually a warning sign in Dhall.
It means that we are writing code so abstract that it approaches the limits of Dhall's type system.

We defined `ListTGeneric` by lifting the element types in `ListT` one level higher (replacing `Type` with `Kind`).
Since the type of `ListT` is `Sort`, we should expect that the type of `ListTGeneric` is one level higher than `Sort`.
But Dhall does not have any type universes higher than `Sort`.
So, typechecking of `ListTGeneric` fails.

The conclusion is that Dhall does not allow us to define a single list type that would work with arbitrary kinds (that is, a "kind-polymorphic" list).


### Perfect trees and other nested recursive types

A "perfect tree" is a tree-like data structure whose branches are constrained to have the same shape until a chosen depth is reached.
For example, a perfect _binary_ tree of depth $n$ must have exactly $2^n$ leaves.

A Haskell definition of a perfect binary tree can be written like this:

```haskell
data PBTree a = Leaf a | Branch (PBTree (a, a))   -- Haskell.
```
Example values of type `PBTree Int` are:

`Leaf 10`

`Branch (Leaf (10, 20))`

`Branch (Branch (Leaf ((10, 20), (30, 40))))`

The code will typecheck only when there are as many `Branch` levels as the nesting levels in the tuples.

The definition of `PBTree` is recursive because it uses `PBTree` itself.
To define this data structure in Dhall, we need to use Church encoding.
But it turns out that we cannot use the Church encoding techniques shown so far.
The reason is that the recursive use of `PBTree` in `Branch (PBTree (a, a))` substitutes the pair type `(a, a)` as the type parameter of `PBTree`.

Recursive definitions of this form are called **nested types**, indicating that a nontrivial type expression is "nested" in the type constructor being defined.

Compare the definition of `PBTree` with this Haskell definition of the `List` type constructor:
```haskell
data List a = Nil | Cons a (List a)   -- Haskell.
```
This is a type equation of the form `List a = F (List a)`, where the pattern functor `F` is defined by `F r = Nil | Cons a r` if we temporarily pretend that `a` is a fixed type.
We can then denote `List a = T` and rewrite the recursive definition of `List a` as a simple type equation `T = F T`.
The Church encoding technique shown in previous sections will give us an equivalent type implemented in Dhall.

Coming back to the perfect binary tree, we find that we _cannot_ temporarily fix `a`, set `T = PBTree a`, and rewrite the recursive definition of `PBTree` as `T = F T` with some `F`.
Thee reason is that the type equation `PBTree a = Leaf a | Branch (PBTree (a, a))` requires us to change the nested type parameter from `a` to `(a, a)` in the recursive use of `PBTree`.
Because of that change, the type equation for `PBTree` is not of the form `PBTree a = F (PBTree a)` with any functor `F`.

The pattern functor `F` needs to be able to apply `PBTree` to different type parameters.
So, `F` must have the type constructor `PBTree` as its parameter.
The result of applying `F PBTree` must be a new type constructor that takes a type parameter `a` and applies `PBTree` to the pair `(a, a)`.
Then we will be able to rewrite the definition of `PBTree` in the form `PBTree = F PBtree`.
For that, we need to define `F` by:

```haskell
type F r a = Leaf a | Branch (r (a, a))   -- Haskell.
```
The corresponding Dhall definition is:
```dhall
let F = λ(r : Type → Type) → λ(a : Type) → < Leaf : a | Branch : r (Pair a a) >
```
The type of `F` is `(Type → Type) → (Type → Type)`.
It is a pattern functor at the level of _type constructors_.

The Church encoding formula `∀(r : Type) → (F r → r) → r` needs some changes in order to make it work at the level of type constructors:
- Replace `r : Type` by `r : Type → Type`.
- As `F r` is now a type constructor, replace `F r → r` by `∀(s : Type) → F r s → r s`.
- Add a type parameter to the final output `r` at the end of the formula.

The resulting type formula is:

```dhall
let LFixT = λ(F : (Type → Type) → Type → Type) → λ(a : Type) → ∀(r : Type → Type) → (∀(s : Type) → F r s → r s) → r a
```
This is the general Church encoding at the level of type constructors.

For instance, with the pattern functor `F` defined above, we obtain the type constructor for perfect binary trees:

```dhall
let PBTree : Type → Type = LFixT F
```

Let us look at some examples of working with the nested type `PBTree`.

To create values of type `PBTree a`, we implement the constructor functions:

```dhall
let leafPB : ∀(a : Type) → a → PBTree a
  = λ(a : Type) → λ(x : a) → λ(r : Type → Type) → λ(f : ∀(s : Type) → F r s → r s) →
    f a ((F r a).Leaf x)
let branchPB : ∀(a : Type) → PBTree (Pair a a) → PBTree a
  = λ(a : Type) → λ(subtree : PBTree (Pair a a)) → λ(r : Type → Type) → λ(f : ∀(s : Type) → F r s → r s) →
    let raa : r (Pair a a) = subtree r f
    in f a ((F r a).Branch raa)
let examplePB1 : PBTree Natural = leafPB Natural 10
let examplePB2 : PBTree Natural = branchPB Natural (branchPB (Pair Natural Natural) (leafPB (Pair (Pair Natural Natural) (Pair Natural Natural)) { _1 = { _1 = 20, _2 = 30 }, _2 = { _1 = 40, _2 = 50 } } ))
```

When defining `examplePB2`, we used the nested record syntax, and we had to write out the nested `Pair` types explicitly.
The type definition of `PBTree` guarantees that we will not forget by mistake to define all necessary values in those nested records and types.

Let us now write a function that extracts all values stored in a perfect tree to a list,
and another function that computes the depth of a perfect tree.

Extracting values from a perfect tree will be done by code like this:
```dhall
let pbTreeToList : ∀(a : Type) → PBTree a → List a = ???
```

As always with Church encodings, a recursive data structure is encapsulated in a higher-order function.
The only way of traversing the data structure is by applying that higher-order function to suitable arguments.

A value such as `examplePB1 : PBTree Natural` is a higher-order function whose type parameter `r` is a type constructor.
That type constructor will determine the output type when we apply `examplePB1`.
Since we need to produce a `List a` out of `PBTree a`, we supply `List` as that type constructor.
```dhall
let pbTreeToList : ∀(a : Type) → PBTree a → List a
  = λ(a : Type) → λ(tree : PBTree a) → tree List ???
let _ = assert : pbTreeToList Natural examplePB1 ≡ [ 10 ]
let _ = assert : pbTreeToList Natural examplePB2 ≡ [ 20, 30, 40, 50 ]
```
The next argument is a function of type `∀(s : Type) → F r s → r s`, where `F` is the pattern functor for the perfect binary tree, and `r` is already set to `List`.
So, it remains to implement a function of type `∀(s : Type) → < Leaf : s | Branch : List (Pair s s) > → List s`.
This function should just extract all values of type `s` into a list of type `List s`.
Implementing that function is straightforward, and we get the final code of `pbTreeToList`:
```dhall
let pbTreeToList : ∀(a : Type) → PBTree a → List a
  = λ(a : Type) → λ(tree : PBTree a) →
    let toList : ∀(s : Type) → < Leaf : s | Branch : List (Pair s s) > → List s
      = λ(s : Type) → λ(p : < Leaf : s | Branch : List (Pair s s) >) →
        merge { Leaf = λ(leaf : s) → [ leaf ]
              , Branch = λ(branch : List (Pair s s)) → List/concatMap (Pair s s) s (λ(pair : Pair s s) → [ pair._1, pair._2 ] ) branch
              } p
    in tree List toList
let _ = assert : pbTreeToList Natural examplePB1 ≡ [ 10 ]
let _ = assert : pbTreeToList Natural examplePB2 ≡ [ 20, 30, 40, 50 ]
```

The ability to implement `pbTreeToList` means that `PBTree` is a `Foldable` functor.
```dhall
let foldablePBTree : Foldable PBTree = { toList = pbTreeToList }
```

To compute the depth of a perfect binary tree, we will implement a function of type `PBTree a → Natural`.
The code will look like this:
```dhall
let pbTreeDepth : ∀(a : Type) → PBTree a → Natural
  = λ(a : Type) → λ(tree : PBTree a) →
    let P : Type → Type = ???
    let q : ∀(s : Type) → F P s → P s = ???
    in tree P q
```
The type parameter `P` in the function call `tree P q` must be a type constructor.
How should we choose it? Note that the result type of `tree P q` will be `P a`.
So, we have to choose `P` as a constant functor such that `P a = Natural` for all `a`.

The parameter `q` will then have type `∀(s : Type) → < Leaf : s | Branch : Natural > → Natural`.
This function should compute the depth of the tree, assuming that the depth for branches has been already computed.

The complete code with tests is:
```dhall
let pbTreeDepth : ∀(a : Type) → PBTree a → Natural
  = λ(a : Type) → λ(tree : PBTree a) →
    let P : Type → Type = λ(_ : Type) → Natural
    let q : ∀(s : Type) → F P s → P s = λ(s : Type) → λ(p : < Leaf : s | Branch : Natural >) →
      merge { Leaf = λ(_ : s) → 0
            , Branch = λ(depth : Natural) → depth + 1
            } p
    in tree P q
let _ = assert : pbTreeDepth Natural examplePB1 ≡ 0
let _ = assert : pbTreeDepth Natural examplePB2 ≡ 2
```

In this section, we studied only one example of a nested type (the "perfect binary tree").
This and other advanced examples of designing and using nested recursive types
are explained in the paper by Ralph Hinze, ["Manufacturing datatypes"](http://dx.doi.org/10.1017/S095679680100404X) (2001).

### Generalized algebraic data types (GADTs)

To motivate GADTs, consider that ordinary type constructors (such as `List`, `Optional`, or user-defined types like `PBTree` in the previous section) work in the same way for all type arguments.
Indeed, `List Natural`, `List Bool`, etc., are always of the same shape -- they are lists of values of a given type.
A data structure of type `PBTree a` is a perfect binary tree whose data shape and available operations work the same way regardless of the choice of the type `a`.
This property is known as **parametric polymorphism**.

Types known as "GADTs" are type constructors whose definitions are _not_ parametrically polymorphic in the type parameter.
Those types do not work in the same way for all choices of type parameters.

Here is an example of a GADT in Haskell:
```haskell
data LExp t where                      -- Haskell.
  LInt :: Int -> LExp Int
  LBool :: Bool -> LExp Bool
  LAdd :: LExp Int -> LExp Int -> LExp Int
  LNot :: LExp Bool -> LExp Bool
  LIsZero :: Lexp Int -> LExp Bool
```
This example represents the abstract syntax tree for a toy language whose expressions can have Boolean or integer type.
The language guarantees statically that operations are applied to arguments of the correct type.
For instance, `LNot x` will typecheck only when `x` is a Boolean expression such as `LBool True`.
The compiler will report a type error if the programmer writes by mistake something like `LNot (LInt 123)`.


The definition of `LExp t` is not parametrically polymorphic in `t`.
To see that it is _not_ a data structure that works in the same way for all `t`,
notice that values of type `LExp t` can be created only when `t = Int` or `t = Bool`, and not with arbitrary other types `t`.
Also, specific constructors (`LAdd`, `LNot`, etc.) require arguments of specific types (such as `LExp Int`) and will not work with an `LExp t` with an arbitrary `t`.

GADTs are usually defined to work in special ways when their parameters are set to specific types.
For that reason, GADTs are normally neither covariant nor contravariant in their type parameters.
This is not a problem, because in practice it is not necessary to have a `fmap` function for GADTs.

The definition of a GADT can use its type parameters in a precise way required by the task at hand.
Because of this flexibility, GADTs are a powerful feature of languages such as OCaml, Haskell, and Scala.

For illustration, let us implement an interpreter for the toy language in Haskell.
The interpreter will be a function of type `LExp t -> t`, which will guarantee _at type-checking time_ that expressions are evaluated to correct types.

```haskell
eval :: LExp t -> t             -- Haskell.
eval (LInt i) = i
eval (LBool b) = b
eval (LAdd x y) = eval x + eval y
eval (LNot b) = not b
eval (LIsZero x) = x == 0
```

GADTs are usually recursively defined because the constructor arguments need to use the GADT itself.
So, we will encode GADTs with the technique similar to the Church encoding of nested types.

Let us first look at how those types are defined in Haskell and Scala, in order to motivate the corresponding definition in Dhall.

We begin by reviewing the syntax for defining an ordinary **algebraic data type** (ADT) that is not a GADT.
For ADTs, both Haskell and Scala have a "short syntax" and a "long syntax".

The short syntax looks like this:

```haskell
data Tree a = Leaf a | Branch (Tree a) (Tree a)   -- Haskell.
```

```scala
enum Tree[A]:                                            // Scala 3.
  case Leaf[A](a: A);  case Branch[A](left: Tree[A], right: Tree[A])
```

The type `Tree` has two constructors, which may be viewed as functions whose output type is `Tree a`.
In Haskell, `Leaf` is a function of type `a → Tree a`, and `Branch` is a function of type `Tree a → Tree a → Tree a`.
In Scala, `Leaf` and `Branch` behave as functions of types `A => Tree[A]` and `(Tree[A], Tree[A]) => Tree[A]`.

The short syntax does not show those functions explicitly.
The long syntax writes out each constructor separately, specifying the output type as well as inputs.

In Haskell, the long syntax for defining `Tree` looks like this:
```haskell
data Tree a where   -- Haskell.
  Leaf :: a -> Tree a
  Branch :: Tree a -> Tree a -> Tree a
```

Scala 3's long syntax uses the keyword `extends`:

```scala
enum Tree[A]:                                       // Scala 3.
  case Leaf[A](a: A) extends Tree[A]
  case Branch[A](left: Tree[A], right: Tree[A]) extends Tree[A]
```

Denote temporarily `Tree a` by just `T` and consider `a` as a fixed type.
If we consider the product type of the entire set of constructors in the "long syntax", we will obtain the type that we can write in Haskell as `(a → T, T → T → T)`.
That type can be rewritten isomorphically in the form `P T → T`, where `P` is a functor defined by `P t = Leaf a | Branch t t`.

Notice that `P` is exactly the pattern functor of the recursive definition `T = P T`, which stands for `Tree a = P (Tree a)` and defines the `Tree` data type.
The Church encoding for that type is `LFix P = ∀(r : Type) → (P r → r) → r`.
The curried Church encoding is closer to the long syntax if we write it using Dhall's long type annotations:

```dhall
let Tree_ = λ(a : Type) → ∀(r : Type) → ∀(leaf : a → r) → ∀(branch : r → r → r) → r
```
In this form, the curried arguments `leaf` and `branch` correspond to the two  constructors (`Leaf` and `Branch`).
The occurrences of the type `r` correspond to the recursive usages of `Tree` in the constructors.

The "long syntax" has no advantage in simple situations where all constructors create values of the same type.
In this example, all three constructors have the same output type `Tree a`.

However, note that the output type parameter `a` in `Tree a` is written out explicitly in the long syntax.
So, Haskell and Scala allow the programmer to use a _different_ type expression (instead of `a`) in that place.
For example, the output type could be `Tree Int` instead of `Tree a`, or even a more complicated type with other type parameters.
The resulting type would then be a GADT.


Defining a GADT in Haskell or Scala always requires the "long syntax".
Now we will show how to translate such "long syntax" definitions into Dhall.

We use the technique of curried Church encoding at the level of type constructors, similarly to what we did for nested recursive types.

Suppose we have the following Haskell definition of a GADT:
```haskell
data P a where  -- Haskell.
  TInt :: Text -> P Int
  BText :: Bool -> P Int -> P Text
```

The first step is to consider the product type of all constructors:

`(Text -> P Int, Bool -> P Int -> P Text)`

It will be useful to uncurry all constructor arguments:

`(Text -> P Int, (Bool, P Int) -> P Text)`


The next step is to rewrite this type in the form of a mapping `F P -> P`.
In this way, we will derive the pattern functor `F` for this recursive type, showing us how to define the Church encoding.

Note that `P` is a type _constructor_ (`P : Type → Type`), so `F` must have the kind `(Type → Type) → Type → Type`.
Because the mapping between `F P` and `P` is a mapping between _type constructors_, it must be written in Dhall as the type `∀(t : Type) → F P t → P t` (with an extra type parameter `t`).
Here `F P t` is the application of `F` to `P`, giving a type constructor, which is then applied to the type `t`.
The Church encoding must be applied at the level of type constructors via the same type formula as we used in the previous section:

```dhall
let LFixK = λ(F : (Type → Type) → Type → Type) → λ(a : Type) →
   ∀(r : Type → Type) → (∀(t : Type) → F r t → r t) → r a
```
This is the general Church encoding at the level of type constructors.
The type constructor `P` will be defined via `LFixK F` with some `F`.
It remains to figure out how to write a suitable `F`.

As the the definition of `P` is non-parametric, the type `∀(t : Type) → F P t → P t` means just the product of all function types `F P t → P t` for all `t`.
In the example at hand, `P t` can have values only when `t = Int` or `t = Text`.
So, the infinite product type `∀(t : Type) → F P t → P t` reduces to the product of just two function types: `F P Int → P Int` and `F P Text → P Text`.

The pattern functor `F` must be defined in such a way that `∀(t : Type) → F P t → P t`  equals the product type of all type constructors.
Let us write them side by side:

- Product of type constructors is: `(Text -> P Int, (Bool, P Int) -> P Text)`
- Mapping type `F P -> P` is: `(F P Int → P Int, F P Text → P Text)`.

These types will be equal if we define `F P Int = Text` and `F P Text = (Bool, P Int)`.
The type constructor `F P` will be void unless applied to `Int` and `Text` type parameters.

Write the Church encoding for the type constructor `P` as `LFixK F`, which after currying looks like this:

```dhall
let C = λ(a : Type) →
  ∀(r : Type → Type) → (F r Integer → r Integer) → (F r Text → r Text) → r a
```
Substituting the definition of `F` (namely, the two cases `F r Integer = Text` and `F r Text = (Bool, r Integer)`) and currying some more, we obtain:
```dhall
let C = λ(a : Type) →
  ∀(r : Type → Type) → (Text → r Integer) → (Bool → r Integer → r Text) → r a
```
Let us add names to the type signature, making the correspondence with the long-syntax Haskell definition more apparent:

```dhall
let C = λ(a : Type) → ∀(r : Type → Type) →
  ∀(tInt : Text → r Integer) →
  ∀(bText : Bool → r Integer → r Text)
    → r a
```
Compare with the Haskell definition:
```haskell
data P a where                    -- Haskell.
  TInt :: Text -> P Int
  BText :: Bool -> P Int -> P Text
```
We see that the type parameter `r` replaces all recursive usages of `P` in the long-syntax definition.
This is the recipe of writing GADTs in the Church encoding.

Here is the Church encoding for the toy language `LExp` shown above:

```dhall
let LExp = λ(t : Type) → ∀(r : Type → Type) →
   ∀(lBool : Bool → r Bool) →
   ∀(lInt : Integer → r Integer) →
   ∀(lAdd : r Integer → r Integer → r Integer) →
   ∀(lNot : r Bool → r Bool) →
   ∀(lIsZero : r Integer → r Bool) →
     r t
```

To create values of type `LExp`, we use constructor functions corresponding to each of the constructors in the Haskell code.
The code for those constructor functions is quite repetitive, so let us only show two of them:

```dhall
let LInt : Integer → LExp Integer
  = λ(i : Integer) → λ(r : Type → Type) →
    λ(lBool : Bool → r Bool) →
    λ(lInt : Integer → r Integer) →
    λ(lAdd : r Integer → r Integer → r Integer) →
    λ(lNot : r Bool → r Bool) →
    λ(lIsZero : r Integer → r Bool) →
      lInt i
let LAdd : LExp Integer → LExp Integer → LExp Integer
  = λ(x : LExp Integer) → λ(y : LExp Integer) → λ(r: Type → Type) →
    λ(lBool : Bool → r Bool) →
    λ(lInt : Integer → r Integer) →
    λ(lAdd : r Integer → r Integer → r Integer) →
    λ(lNot : r Bool → r Bool) →
    λ(lIsZero : r Integer → r Bool) →
      lAdd (x r lBool lInt lAdd lNot lIsZero) (y r lBool lInt lAdd lNot lIsZero)
```

We can now  create an  example  expression in the toy language:

```dhall
let exampleLExp : LExp Integer = LAdd (LInt +10) (LInt +20)
```

Let us now implement the interpreter for this  language.

Note that the Haskell code for the interpreter is recursive.
In the Church encoding, recursion is replaced by applications of the higher-order functions.
The Dhall code for the interpreter is non-recursive and consists of applying the value of type `LExp t` to suitable arguments.
The first of those arguments is the type constructor parameter `r : Type → Type`.
To choose this parameter, we consider the final output type `r t` of the function `LExp t`.
We need to obtain a value of type `t`.
So, we must choose `r` as the identity functor.
We use the function `identityK` to create it.
After that, all other arguments are easy to compute:
```dhall
let evalLExp : ∀(t : Type) → LExp t → t
  = λ(t : Type) → λ(expr : LExp t) →
    let r : Type → Type                           = λ(a : Type) → a
    let lBool : Bool → r Bool                     = identity Bool
    let lInt : Integer → r Integer                = identity Integer
    let lAdd : r Integer → r Integer → r Integer  = Integer/add
    let lNot : r Bool → r Bool                    = identity Bool
    let lIsZero : r Integer → r Bool              = Integer/equal +0
    in expr r lBool lInt lAdd lNot lIsZero
```

To test this code, let us evaluate the example expression:

```dhall
let _ = assert : evalLExp Integer exampleLExp ≡ +30
```


The toy language `LExp` did not need to use all the possible features of GADTs.
To see how the Church encoding technique can implement the full power of GADTs, look at an artificial example of a GADT where we introduced and set various type parameters for illustration:

```haskell
data WeirdBox a where   -- Haskell.
  Box1 :: a -> WeirdBox Int
  Box2 :: Bool -> WeirdBox String
  Box3 :: Int -> a -> WeirdBox b -> WeirdBox (a, b)
```

```scala
enum WeirdBox[A]:                                            // Scala 3.
  case Box1[A](x: A) extends WeirdBox[Int]
  case Box2(b: Bool) extends WeirdBox[String]
  case Box3[A, B](i: Int, x: A, b: WeirdBox[B]) extends WeirdBox[(A, B)]
```

The type constructor `WeirdBox` substitutes specific types (`Int` and `String`) and the type expression `(a, b)` as output type parameters, instead of just keeping the output type parameter `a` everywhere.
This makes `WeirdBox` a GADT.

The Church encoding of `WeirdBox` looks like this:

```dhall
let WeirdBox = λ(t : Type) → ∀(r : Type → Type) →
   ∀(box1 : ∀(a : Type) → a → r Integer) →
   ∀(box2 : Bool → r Text) →
   ∀(box3 : ∀(a : Type) → ∀(b : Type) → Integer → a → r b → r (Pair a b)) →
     r t
```
Note that the extra type parameters in the constructors `Box1` and `Box3` need to be written explicitly, similarly to the syntax in Scala.
Haskell's more concise syntax declares those type parameters implicitly.

### Existentially quantified types

By definition, a value `x` has an **existentially quantified** type if `x` is a pair `(u, y)` where `u` is some specific type and `y` is a value of type `P u` but the type `u` is not known outside of the scope of `x`.
Here `P` is a type constructor that describes how the type of `y` depends on the type parameter `u`.
The existential type is denoted mathematically by $\exists t.~P~t$.

An example is the following type definition in Haskell:

```haskell
-- Haskell.
data F a = forall t. Example (t -> Bool, t -> a)
```
The type parameter `t` is visible within the scope of the constructor `Example` but is not visible outside:
The data type is defined as `data F a`, which shows only the type parameter `a`. 


The corresponding code in Scala is:

```scala
// Scala
sealed trait F[_]
case class Example[A, T](init: T => Boolean, transform: T => A) extends F[A]
```
The type parameter `T` is visible only within the scope of the case class `Example` but is not visible outside:
The class is defined via `extends F[A]`, which shows only the type parameter `A`.

From the point of view of the program code, an existentially quantified type parameter is one that is present within the scope of a specific data constructor but absent from the overall data type.
In the Haskell code, it is the type parameter `t`, and in the Scala code, it is `T`.

The mathematical notation for `F` is `F a = ∃ t. (t → Bool) × (t → a)`.

To understand how the existential types work, let us look at the way the type parameter `t` is used in the Haskell code just shown.

The type parameter `t` is bound by the quantifier and is visible only inside the type expression `∃ t. (t → Bool) × (t → a)`.
To create a value `x` of type `F a`, we will need to supply two functions, of types `t → Bool` and `t → a`, with a specific (somehow chosen) type `t`.
At that point, we are free to choose `t` arbitrarily.
But when working with a value `x : F a`, we will not directly see the type `t` anymore.
The type of `x` is `F a`; that type does not show what `t` is.
(The type `t` is not a free type parameter in the expression `F a`.)
However, we may imagine that the type parameter `t` still somehow "exists" inside the value `x`.
This motivation helps us remember the meaning of the name "existential".

Existential type quantifiers are not directly supported by Dhall.
Nevertheless, they can be encoded in a special way, as we will now show.

We begin with the type expression `∀(r : Type) → (F a → r) → r`.
Because `F a` does not depend on `r`, this type is simply equivalent to `F a` due to the covariant Yoneda identity.
(We discussed that above in the section "Church encoding of non-recursive types".)

But this is just a first step towards a useful encoding.
Now we look at the function type `F a → r` more closely.

A value `x : F a` must be created as a pair of type `{ _1 : t → Bool, _2 : t → a }` with a chosen type `t`.
A function `f : F a → r` must produce a result value of type `r` from any value `x`, for any type `t`.

In fact, `f` may not inspect the type `t` or make choices based on `t` because the type `t` is existentially quantified and is hidden inside `x`.
So, the function `f` must work for all types `t` in the same way.

It means that the function `f` must have `t` as a _universally quantified_ type parameter.
So, we write the type signature of `f` as `f : ∀(t : Type) → { _1 : t → Bool, _2 : t → a } → r`.

The final code for the Church encoding of `F` becomes:

```dhall
let F = λ(a : Type) → ∀(r : Type) → (∀(t : Type) → { _1 : t → Bool, _2 : t → a } → r) → r
```

It is important that the universal quantifier `∀(t : Type)` is _inside the type_ of an argument of `F`.
Otherwise, the encoding would not work.

To see an example of how to construct a value of type `F a`, let us set `a = Natural`.
The type `F Natural` then becomes `∀(r : Type) → (∀(t : Type) → { _1 : t → Bool, _2 : t → Natural } → r) → r`.
An example value `x : F Natural` is constructed like this:

```dhall
let Integer/greaterThan = https://prelude.dhall-lang.org/Integer/greaterThan
let x
 : ∀(r : Type) → (∀(t : Type) → { _1 : t → Bool, _2 : t → Natural } → r) → r
  = λ(r : Type) → λ(pack : ∀(t : Type) → { _1 : t → Bool, _2 : t → Natural } → r) →
    pack Integer { _1 = λ(x : Integer) → Integer/greaterThan x +10, _2 = λ(x : Integer) → Integer/clamp x }
```

In this code, we apply the given argument `pack` of type `∀(t : Type) → { _1 : t → Bool, _2 : t → Natural } → r` to some arguments.

It is clear that we may produce a value `x : F Natural` given any specific type `t` and any value of type `{ _1 : t → Bool, _2 : t → Natural }`.
This exactly corresponds to the information contained within a value of an existentially quantified type `∃ t. (t → Bool) × (t → Natural)`.

To generalize this example to arbitrary existentially quantified types, we replace the type `{ _1 : t → Bool, _2 : t → a }` by an arbitrary type constructor `P t`.
Here `a` needs to be viewed as a fixed type; for instance, if `a = Natural` we will get:

```dhall
let P = λ(t : Type) → { _1 : t → Bool, _2 : t → Natural }
```

It follows that the Church encoding of $\exists t.~P~t$ is the following type (denoted by `Exists P`):

```dhall
let Exists = λ(P : Type → Type) → ∀(r : Type) → (∀(t : Type) → P t → r) → r
```

To create a value of type `Exists P`, we just need to supply a specific type `t` together with a value of type `P t`.

```dhall
let our_type_t : Type = ???   -- Can be any specific type here.
let our_value : P t = ???   -- Any specific value here.
let e : Exists P = λ(r : Type) → λ(pack : ∀(t : Type) → P t → r) → pack our_type_t our_value
```

Heuristically, the function application `pack X y` will "pack" a given type `X` under the "existentially quantified wrapper" together with a value `y`.
We will now study the constructor functions `Exists` and `pack` in more detail.

#### Working with existential types

To work with existential types more conveniently, let us implement generic functions for creating existentially quantified types and for producing and consuming values of those types.
The three functions are called `Exists`, `pack`, and `unpack`.

The function call `Exists P` creates the type corresponding to the Church encoding of the type $\exists t.~P~t$.
The argument of `Exists` is a _type constructor_ such as `P`.

```dhall
let Exists : (Type → Type) → Type
  = λ(P : Type → Type) → ∀(r : Type) → (∀(t : Type) → P t → r) → r
```

The function `Exists` replaces the mathematical notation $\exists t.~P~t$ by a similar formula: `Exists (λ(t : Type) → P t)`.

The function `pack` creates a value of type `Exists P` from a type `t`, a type constructor `P`, and a value of type `P t`.

```dhall
let pack : ∀(P : Type → Type) → ∀(t : Type) → P t → Exists P
  = λ(P : Type → Type) → λ(t : Type) → λ(pt : P t) →
      λ(r : Type) → λ(pack_ : ∀(t_ : Type) → P t_ → r) → pack_ t pt
```

The function `unpack` implements functions of type `Exists P → r`, where `r` is some arbitrary result type.
So, `unpack` needs to be able to convert a value of type `P t` into a value of type `r` regardless of the actual type `t` encapsulated inside `Exists P`.
To achieve that, one of the arguments of `unpack` will be a function of type `∀(t : Type) → P t → r`.
The other arguments of `unpack` are the type constructor `P`, a value of type `Exists P`, and the result type `r`.

```dhall
let unpack : ∀(P : Type → Type) → ∀(r : Type) → (∀(t : Type) → P t → r) → Exists P → r
  = λ(P : Type → Type) → λ(r : Type) → λ(unpack_ : ∀(t : Type) → P t → r) → λ(ep : Exists P) →
      ep r unpack_
```

We notice that `unpack` does nothing more than rearrange the curried arguments and substitute them into the function `ep`.
To illustrate that, let us simplify the code of `unpack` by rearranging some arguments and omitting the function arguments that are immediately substituted:

```dhall
let unpack1 : ∀(P : Type → Type) → Exists P → ∀(r : Type) → (∀(t : Type) → P t → r) → r
  = λ(P : Type → Type) → λ(ep : Exists P) → ep
```
We find that `unpack1 P` is just the identity function of type `Exists P → Exists P`.
So, we could apply values of type `Exists P` directly as functions, instead of using `unpack`.
Sometimes, it will be more illuminating to use `unpack`.

#### Functions of existential types: the function extension rule

The fact that `unpack` is equivalent to an identity function allows us to simplify the function type `Exists P → q`, where `q` is some fixed type expression.

To see how, let us consider `P` as fixed and specialize the type of `unpack` to `P`.
We will denote the resulting function by `unpackP`:

```dhall
let unpackP : ∀(r : Type) → (∀(t : Type) → P t → r) → (Exists P → r)
  = λ(r : Type) → λ(unpack_ : ∀(t : Type) → P t → r) → λ(ep : Exists P) →
    ep r unpack_
```

This type signature suggests that the function type `Exists P → r` (written in full as `(∀(a : Type) → (∀(t : Type) → P t → a) → a) → r`) is equivalent to a simpler type `∀(t : Type) → P t → r`.

One can prove rigorously that there is an isomorphism between the types `Exists P → r` and `∀(t : Type) → P t → r` (where it is assumed that `r` does _not_ depend on `t`).
We call this isomorphism the **function extension rule** for existential types.

The function `unpackP` shown above gives one direction of the isomorphism.
The other direction is the function we call `outE`:

```dhall
let outE : ∀(r : Type) → (Exists P → r) → ∀(t : Type) → P t → r
  = λ(r : Type) → λ(consume : Exists P → r) → λ(t : Type) → λ(pt : P t) →
    let ep : Exists P = pack P t pt
    in consume ep
```
We will prove in the appendix "Naturality and parametricity" that the functions `unpackP r` and `outE r` are indeed inverses of each other.

Because of this isomorphism, we may always use the simpler type `∀(t : Type) → P t → r` instead of the more complicated type `Exists P → r`.

#### Differences between existential and universal quantifiers

We have introduced the type constructor `Exists` that helps us create existential types.

```dhall
let Exists : (Type → Type) → Type
  = λ(P : Type → Type) → ∀(r : Type) → (∀(t : Type) → P t → r) → r
```

We could define a type constructor `Forall` similarly, to create universally quantified types:

```dhall
let Forall : (Type → Type) → Type
  = λ(P : Type → Type) → ∀(r : Type) → P r
```

These definitions allow us to write types more concisely, e.g., `Exists P` and `Forall P`.

Despite this superficial similarity, existentially quantified types have a significantly different behavior from universally quantified ones.

We can work with values of existentially quantified types, such as `ep : Exists P`, by using the functions `pack` and `unpack`.

To create a value `ep`, we call `pack P t pt` with a specific type `t` and a specific value `pt : P t`.
The type `t` is set when we create the value `ep` and may be different for different such values.

But the specific type `t` used while constructing `ep` will no longer be exposed to the code outside of `ep`.
One could say that the type `t` "exists only inside" the scope of `ep` and is hidden (or "encapsulated") within that scope.

This behavior is quite different from that of values of universally quantified types.
For example, the polymorphic `identity` function has type `identity : ∀(t : Type) → t → t`.
When we apply `identity` to a specific type, we get a value such as:

```dhall
let idText : Text → Text = identity Text
```

When constructing `idText`, we chose to use the type `Text` as the type parameter.
After that, the type `Text` is exposed and visible to the outside code, because that type is part of the type signature of `idText`.
The outside code needs to adapt to that type so that the types match.

When constructing a value `ep : Exists P`, we also need to use a specific type as `t` (say, `t = Text` or another type).
But the chosen type is then hidden inside `ep`, because the externally visible type of `ep` is `Exists P` and does not contain `t` anymore.

It is actually not hidden that `ep` _has_ a type parameter `t` inside.
The hidden information is the actual type `t` used while constructing `ep`.
Let us clarify how that works.

Code that uses `ep` must use `unpack` with an argument function `unpack_ : ∀(t : Type) → P t → r`.
We must implement `unpack_` ourselves.
Most often, `P t` will be a data type that stores some values of type `t`.
Because the code of `unpack_` receives `t` and `P t` as arguments, we will be able to extract some values of type `t` and to pass those values around (while computing the result value of some type `r`).
For instance, a value `x` of type `t` can be further substituted into a function of type `∀(t : Type) → ∀(x : t) → ...` because that function can accept an argument `x` of any type.
But all such functions are constrained to work _in the same way_ for all types `t`.
Such functions will not be able to identify specific types `t` or make decisions based on specific values `x : t`.
In this sense, type quantifiers ensure encapsulation of the type `t` inside the value `ep`.

#### Covariance with respect to the type constructor

Both types `Exists P` and `Forall P` depend covariantly on the _type constructor_ parameter `P`.
Let us first clarify what it means for something to be covariant with respect to such a type parameter.

Covariance of `F x` with respect to a type parameter `x` means that, for any two types `x` and `y` and for any function `f : x → y`, we can compute a function of type `F x → F y`.
The new function needs to satisfy certain laws (the "functor laws").

Similarly, covariance of `Exists` and `Forall` with respect to the type constructor parameter `P` means that, for any two type constructors `P` and `Q` and a mapping from `P` to `Q`, we can compute functions of types `Exists P → Exists Q` and `Forall P → Forall Q`.
Because `P` and `Q` are type constructors, a mapping from `P` to `Q` means a function of type `∀(a : Type) → P a → Q a`.
Let us implement the corresponding transformations:
```dhall
let mapExists
  : ∀(P : Type → Type) → ∀(Q : Type → Type) → (∀(a : Type) → P a → Q a) → Exists P → Exists Q
  = λ(P : Type → Type) → λ(Q : Type → Type) → λ(f : ∀(a : Type) → P a → Q a) → λ(eP : Exists P) →
    λ(r : Type) → λ(pack_q : ∀(t : Type) → Q t → r) →
      let pack_p : ∀(t : Type) → P t → r = λ(t : Type) → λ(pt : P t) → pack_q t (f t pt)
      in eP r pack_p
```

```dhall
let mapForall
  : ∀(P : Type → Type) → ∀(Q : Type → Type) → (∀(a : Type) → P a → Q a) → Forall P → Forall Q
  = λ(P : Type → Type) → λ(Q : Type → Type) → λ(f : ∀(a : Type) → P a → Q a) → λ(aP : Forall P) →
    λ(a : Type) → f a (aP a)
```

We will reuse these mapping functions below to make some code shorter.

### Dependent pairs

A **dependent pair**  is a type that describes pairs of values of a special form: the first value has a given type `X` (say, it is `x : X`), and the second value has type `P x`, where `P : X → Type` is a given function.
So, the _type_ of the second value in the pair depends on the first _value_.
A dependency of a type on a value is the hallmark of a **dependent type**.

Dependent pairs cannot be expressed directly by Dhall records, because each field of a record must have a fixed type that cannot depend on other fields of the same record.
Instead, we will use the Church encoding technique.
That technique is based on the fact that Dhall can already express a function _from_ a dependent pair to some other result type (say, `R`).
That function's type is written as `∀(x : X) → P x → R`.
The only difficulty is that we cannot uncurry this function type into a function from a record to `R`.
Instead, we will apply the technique similar to that used for the Church encoding of pair types:
```dhall
Pair A B  ≅  ∀(R : Type) → (A → B → R) → R
```
The analogous encoding for the dependent pair is:
```dhall
DependentPair X P  ≅  ∀(R : Type) → (∀(x : X) → P x → R) → R
```

So, `DependentPair` is encoded as a type-level function parameterized by an arbitrary type `X` and an arbitrary dependent function of type `X → Type`:

```dhall
let DependentPair
  : ∀(X : Type) → (X → Type) → Type
  = λ(X : Type) → λ(P : X → Type) →
    ∀(R : Type) → (∀(x : X) → P x → R) → R
```

Creating a value of type `DependentPair X P` requires us to provide a value `x : X` and a value of type `P x`.
So, a constructor can be implemented as:
```dhall
let makeDependentPair
  : ∀(X : Type) → ∀(x : X) → ∀(P : X → Type) → P x → DependentPair X P
  = λ(X : Type) → λ(x : X) → λ(P : X → Type) → λ(px : P x) →
    λ(R : Type) → λ(k : ∀(x : X) → P x → R) → k x px
```


Dependent pairs can be used to encode values that satisfy some equations.
Examples are shown in the next section ("Refinement types and singleton types").

As preparation, we will now study some general properties of dependent pairs.

#### Functions from dependent pairs

Dependent pair types (`DependentPair X P`) are Church-encoded as higher-order functions of type `∀(R : Type) → (∀(x : X) → P x → R) → R`.
If we need to implement a function from a dependent pair to some other type (`Q`), the function's type can be simplified like this:

```dhall
DependentPair X P → Q  ≅  ∀(x : X) → P x → Q
```
The type expression `∀(x : X) → P x → Q` is equivalent but shorter.

To implement this type isomorphism, we may define a pair of functions:

```dhall
let simplifyDependentPair
  : ∀(X : Type) → ∀(P : X → Type) → ∀(Q : Type) → (DependentPair X P → Q) → ∀(x : X) → P x → Q
  = λ(X : Type) → λ(P : X → Type) → λ(Q : Type) → λ(long : DependentPair X P → Q) → λ(x : X) → λ(px : P x) →
    long (makeDependentPair X x P px)
```

```dhall
let unsimplifyDependentPair
  : ∀(X : Type) → ∀(P : X → Type) → ∀(Q : Type) → (∀(x : X) → P x → Q) → DependentPair X P → Q
  = λ(X : Type) → λ(P : X → Type) → λ(Q : Type) → λ(short : ∀(x : X) → P x → Q) → λ(dp : DependentPair X P) →
    dp Q short
```

These functions are mutual inverses.
One direction of the isomorphism can be verified using Dhall's `assert` feature, because the proof goes by a straightforward substitution of the terms:

```dhall
let _ = λ(X : Type) → λ(P : X → Type) → λ(Q : Type) → λ(short : ∀(x : X) → P x → Q) →
  assert : short ≡ simplifyDependentPair X P Q (unsimplifyDependentPair X P Q short)
```

To prove the other direction of the isomorphism:
```dhall
let ??? = λ(X : Type) → λ(P : X → Type) → λ(Q : Type) → λ(long : DependentPair X P → Q) →
  assert : long ≡ unsimplifyDependentPair X P Q (simplifyDependentPair X P Q long)
```
does not work in Dhall.
The proof requires a symbolic reasoning with dependently-typed parametricity that is beyond the scope of this book.

#### Extracting the first part of a dependent pair

Given a value of type `DependentPair X P`, we can extract the first value `x : X` stored in it.
We expect that to be done via a function of type `DependentPair X P → X`.
An implementation is:

```dhall
let dependentPairFirstValue
  : ∀(X : Type) → ∀(P : X → Type) → DependentPair X P → X
  = λ(X : Type) → λ(P : X → Type) → λ(dp : DependentPair X P) →
    dp X (λ(x : X) → λ(_ : P x) → x)
```

Notice that the type `DependentPair X P → X` can be simplified to `∀(x : X) → P x → X`.
It is clear that we need to return just the argument `x` and ignore the argument of type `P x`.
This is done via the function `λ(x : X) → λ(_ : P x) → x`.
Now we can use `unsimplifyDependentPair` to convert this function to a function of type `DependentPair X P → X`:

```dhall
let dependentPairFirstValueSimple = λ(X : Type) → λ(P : X → Type) →
  unsimplifyDependentPair X P X (λ(x : X) → λ(_ : P x) → x)
```
We can verify that the simplified code is equivalent to the original code:
```dhall
let _ = assert : dependentPairFirstValueSimple ≡ dependentPairFirstValue
```

However, we cannot extract the second value (of type `P x`) via a simple function of type `DependentPair X P → something`.
The type of the second value depends on the first value (`x`) and cannot be defined separately from that `x`.
Without knowing `x`, we cannot correctly assign a type to a function that extracts just the value of type `P x`.

Extracting the second value from a dependent pair requires advanced support of dependent types that Dhall does not provide.

### Refinement types and singleton types

#### Refinement types

The intent of a **refinement type** is to create a new type that ensures at typechecking time that all values satisfy a given condition.
Dependent pairs provide a general encoding of refinement types in Dhall.

An simple example is a type describing a subset of `Natural` numbers that may not be greater than `10`.
To encode that type via dependent pairs, we need to create a function of type `Natural → Type`.
When that function is applied to a value `x : Natural`, the result must be a type whose values give evidence that `x` is not greater than `10`.
That function should return a void type if `x` is above `10`.

How could we implement such a function? One possibility is to use Dhall's  built-in method `Natural/subtract`.
In Dhall, the expression `Natural/subtract 10 x` will evaluate to zero when `x` is less or equal `10`.
Then we can use Dhall's equality type `Natural/subtract 10 x ≡ 0` as the type of evidence values.
The type `Natural/subtract 10 x ≡ 0` is not void precisely when `x` is not greater than `10`.

This leads us to the definitions:
```dhall
let NaturalLessEqual10Predicate : Natural → Type
  = λ(x : Natural) → Natural/subtract 10 x ≡ 0
let NaturalLessEqual10 = DependentPair Natural NaturalLessEqual10Predicate
```

It is convenient to specialize the constructor (`makeDependentPair`) and the extractor (`dependentPairFirstValue`) to this type:
```dhall
let makeNaturalLessEqual10
  : ∀(x : Natural) → NaturalLessEqual10Predicate x → NaturalLessEqual10
  = λ(x : Natural) → λ(px : NaturalLessEqual10Predicate x) →
    makeDependentPair Natural x NaturalLessEqual10Predicate px
let extractNaturalLessEqual10 : NaturalLessEqual10 → Natural
  = dependentPairFirstValue Natural NaturalLessEqual10Predicate
```
Now we can create values of type `NaturalLessEqual10` like this:
```dhall
let x : NaturalLessEqual10 = makeNaturalLessEqual10 8 (assert : NaturalLessEqual10Predicate 8)
```

This usage is repetitive: we need to write the number `8` twice.
Could we avoid this repetition?

It is not possible to move the `assert` code into the function `makeNaturalLessEqual10`, because `assert` expressions are validated at typechecking time, before the function `makeNaturalLessEqual10` is applied to any arguments.

One way of reducing the code duplication is to notice that `NaturalLessEqual10Predicate 8` actually returns the equality type `0 ≡ 0`.
The same type (`0 ≡ 0`) is returned by `NaturalLessEqual10Predicate x` whenever $x \le 10$.
So, we could define the value `assert : 0 ≡ 0` in advance and use it like this:

```dhall
let NaturalLessEqualAssert = assert : 0 ≡ 0
let x : NaturalLessEqual10 = makeNaturalLessEqual10 8 NaturalLessEqualAssert
```


To make this code shorter still, we could change the definition of `NaturalLessEqual10Predicate` by simplifying the returned type.
Instead of returning an equality type, it is sufficient if `NaturalLessEqual10Predicate x` returns a unit type (in Dhall, `{}`) for $x \le 10$ and a void type (in Dhall, `<>`) for $x > 10$.
We can use an `if/then/else` expression that returns the unit or the void types according to the value of `x`.
Then the definition and the usage become simpler while the functionality remains the same.
Let us use more descriptive names for the new code:
```dhall
let `If N <= 10` : Natural → Type
  = λ(x : Natural) → if Natural/lessThanEqual x 10 then {} else <>
let `N <= 10` = DependentPair Natural `If N <= 10`
```

The code of `makeNaturalLessEqual10` and `extractNaturalLessEqual10` remains the same.
Let us rename those functions too:
```dhall
let `make N <= 10` = λ(x : Natural) → makeDependentPair Natural x `If N <= 10`
let `get N <= 10` = dependentPairFirstValue Natural `If N <= 10`
```

Creating and using a value of the type `N <= 10` looks like this:

```dhall
let x : `N <= 10` = `make N <= 10` 8 {=}
let _ = assert : `get N <= 10` x ≡ 8
```

We can generalize this code to a refinement type that imposes an arbitrary condition on a given type `T`, as long as that condition can be expressed as a function of type `T → Bool`:

```dhall
let toPredicate = λ(T : Type) → λ(cond : T → Bool) →
  λ(t : T) → if cond t then {} else <>
let Refined : ∀(T : Type) → ∀(cond : T → Bool) → Type
  = λ(T : Type) → λ(cond : T → Bool) →
    DependentPair T (toPredicate T cond)
let makeRefined = λ(T : Type) → λ(cond : T → Bool) →
  let Predicate = toPredicate T cond
  in λ(t : T) → λ(evidence : Predicate t) →
    makeDependentPair T t Predicate evidence
let getUnrefined = λ(T : Type) → λ(cond : T → Bool) →
  dependentPairFirstValue T (toPredicate T cond)
```

This method works whenever the refinement condition can be expressed via `Bool` values.
This is not always the case in Dhall; for instance, we cannot write a function of type `Text → Bool` that checks whether the input string is empty.

Nevertheless, a wide range of refinement types on `Text` can be implemented using functions from the library [dhall-text-utils](https://github.com/kukimik/dhall-text-utils).
The main technique in that library is to create functions returning special `Text` values (such as the empty string, or the string `"x"`) instead of functions returning `Bool`.
Then one can use `assert` expressions to verify that a `Text` value satisfies various conditions.

As an example, suppose we need to assert that a given string is _non-empty_.
This is not as straightforward as asserting that a string is empty (for that, we just write `assert : s ≡ ""`)
To implement a non-empty assertion, we use the built-in Dhall function `Text/replace` in a special way.
The trick is to replace the entire input string by the fixed string `"x"`.
The built-in function `Text/replace x y z` is defined such that if `x` is empty then no replacement will be done.
We can see how that works in this example:
```dhall
⊢ Text/replace "abcde" "x" "abcde"

"x"

⊢ Text/replace "" "x" ""

""
```
So, `Text/replace x y x` will return `y` if `x` is non-empty.
But when `x` is an empty string then `Text/replace x y x` will return `x` unchanged; that is, it will return an empty string.

Using this corner case in the definition of `Text/replace`, one can implement an assertion for non-empty strings:

```dhall
let StringIsNotEmpty : Text → Type
  = λ(string : Text) → "x" ≡ Text/replace string "x" string
let _ = assert : StringIsNotEmpty "abc"  -- OK
-- let _ = assert : StringIsNotEmpty "" -- This will not compile.
```

Now we can define a refinement type of non-empty strings:

```dhall
let NonEmptyString = DependentPair Text StringIsNotEmpty
let makeNonEmptyString = λ(string : Text) → λ(ev : StringIsNotEmpty string) →
  makeDependentPair Text string StringIsNotEmpty ev
let getNonEmptyString : NonEmptyString → Text
  = dependentPairFirstValue Text StringIsNotEmpty
```

To create values of the type `NonEmptyString`, it is convenient to define a standard evidence value:

```dhall
let ThisStringIsNotEmpty = assert : "x" ≡ "x"
let x : NonEmptyString = makeNonEmptyString "abc" ThisStringIsNotEmpty
let _ = assert : "abc" ≡ getNonEmptyString x -- Test that we still have that string.
```

The library `dhall-text-utils` uses this and other tricks to implement various assertions on text values.
Here is an example of using `dhall-text-utils` to define a refinement type on `Text` that accepts only strings beginning with either "a" or "z" and consisting of alphanumeric characters.

We begin by implementing a `StringIsValid` predicate:
```dhall
let TU = https://raw.githubusercontent.com/kukimik/dhall-text-utils/refs/heads/master/src/package.dhall sha256:60495097a77d11c500d872a59c2bf96ee7edcd3f0a498261f62ba4cdc11bef15
let alphanum = TU.CharacterClasses.ASCIIalnum
let prefix = TU.Predicates.hasPrefix
let StringIsValid : Text → Type = λ(string : Text) →
  let startsWithAOrZ = TU.Logic.or [ prefix "a" string, prefix "z" string ]
  let isAlphanum = TU.Predicates.consistsOf alphanum string
  in TU.Logic.isTrue (TU.Logic.and [ startsWithAOrZ, isAlphanum ])
```

An example of a "valid string" is `"abcd1234"`.
```dhall
let example = "abcd1234"
let _ = assert : StringIsValid example
```

It is now straightforward to define a refinement type using `StringIsValid`:
```dhall
let ValidString = DependentPair Text StringIsValid
let makeValidString = λ(string : Text) → λ(ev : StringIsValid string) →
  makeDependentPair Text string StringIsValid ev
let getValidString : ValidString → Text
  = dependentPairFirstValue Text StringIsValid
```

To test this code:

```dhall
let x = makeValidString example TU.Logic.QED
let _ = assert : "abcd1234" ≡ getValidString x
```

#### Singleton types

As another example, we show how to encode a **singleton type**: a type that has only one value chosen from a given type.
For instance, a singleton type `Text` with value `"abc"` is a type that contains a single value `"abc"`.
This code defines a type `Text_abc` and a value `x` of that type:
```dhall
let Text_equals_abc = λ(text : Text) → (text ≡ "abc")
let Text_abc : Type = DependentPair Text Text_equals_abc
let x : Text_abc = makeDependentPair Text "abc" Text_equals_abc (assert : "abc" ≡ "abc")
```
We can then extract the `Text`-valued part of `x` and verify that it is equal to the string `"abc"`:

```dhall
let _ = assert : dependentPairFirstValue Text Text_equals_abc x ≡ "abc"
```

We can generalize this code to define a (dependent) type constructor for singleton types that are limited to a given `Text` value:
```dhall
let TextSingletonPredicate = λ(fixed : Text) → λ(text : Text) → (text ≡ fixed)
let TextSingleton : Text → Type
  = λ(fixed : Text) → DependentPair Text (TextSingletonPredicate fixed)
let makeTextSingleton : ∀(fixed : Text) → TextSingleton fixed
  = λ(fixed : Text) → makeDependentPair Text fixed (TextSingletonPredicate fixed) (assert : fixed ≡ fixed)
let x : TextSingleton "abc" = makeTextSingleton "abc"
-- let x : TextSingleton "abc" = makeTextSingleton "def"  -- This will fail!
```

This technique works with any Dhall type (even with fully opaque types such as `Double` or `Bytes`).


#### Typeclass instances with laws


Dependent pairs can be used to encode typeclass instances that are guaranteed to satisfy the typeclass laws.
In this sense, a typeclass instance with evidence of laws is a refinement type.

A simple example is the `Semigroup` typeclass with the associativity law.
Recall the definitions shown in the chapter "Typeclasses":

```dhall
let Semigroup = λ(t : Type) → { append : t → t → t }
let semigroup_law = λ(t : Type) → λ(ev : Semigroup t) →
  λ(x : t) → λ(y : t) → λ(z : t) →
    ev.append x (ev.append y z) ≡ ev.append (ev.append x y) z
```

It will be convenient to define the _type constructor_ for `semigroup_law` separately:
```dhall
let semigroup_law_t : ∀(t : Type) → Semigroup t → Type
  = λ(t : Type) → λ(ev : Semigroup t) →
    ∀(x : t) → ∀(y : t) → ∀(z : t) → semigroup_law t ev x y z
```
This definition allows us to write an evidence for the semigroup law as a value of type `semigroup_law_t t ev`.

Now we can implement the type `SemigroupLawful` via a dependent pair that contains a `Semigroup` typeclass evidence and an evidence that the law holds:

```dhall
let SemigroupLawful = λ(t : Type) → DependentPair (Semigroup t) (λ(ev : Semigroup t) → semigroup_law_t t ev)
```
A value of type `SemigroupLawful` can be produced like this:
```dhall
let T = Bool → Bool
let associativityLawForT = λ(x : T) → λ(y : T) → λ(z : T) → assert : semigroup_law T semigroupBoolToBool x y z
let instanceBoolToBool : SemigroupLawful T = makeDependentPair (Semigroup T) semigroupBoolToBool (semigroup_law_t T) associativityLawForT
```
This value stores at once an implementation of the semigroup function (`append`) and an evidence value proving that `append` satisfies the associativity law.

This technique works only when Dhall's typechecker is powerful enough to validate laws symbolically.
We have chosen the semigroup type `Bool → Bool` in this example because Dhall is able to validate the associativity law for that type.

## Co-inductive types

### Greatest fixpoints: Motivation

Recursive types are usually specified via type equations of the form `T = F T`.
So far, we have used the Church encoding technique for representing such recursive types in Dhall.
But Church encodings always give the **least fixpoints** of type equations.
The least fixpoints give types that are also known as "inductive types".
Another useful kind of fixpoints are **greatest fixpoints**, also known as "co-inductive" types.

In this book, we will denote by `LFix F` the least fixpoint and by `GFix F` the greatest fixpoint of the type equation `T = F T`.

Intuitively, the least fixpoint is the smallest data type `T` that satisfies `T = F T`.
The greatest fixpoint is the largest possible data type that satisfies the same equation.

Least fixpoints are always _finite_ structures.
One can always traverse all data stored in such a structure within a finite number of operations.

Greatest fixpoints are, as a rule, lazily evaluated data structures that correspond to infinite iteration.
A traversal of all data items stored in those data structures is not expected to terminate.
Those data structures are used only in ways that do not involve a full traversal of all data.
It is useful to imagine that those data structures are "infinite", even though the amount of data stored in memory is of course always finite.

As an example of the contrast between the least fixpoints and the greatest fixpoints, consider the pattern functor `F` for the data type `List Text`.
The mathematical notation for `F` is `F r = 1 + Text × r`, and a Dhall definition is:

```dhall
let F = ∀(r : Type) → < Nil | Cons : { head : Text, tail : r } >
```

The type `List Text` is the least fixpoint of `T = F T`.
(So, we may write `LFix F = List Text`.)
A data structure of type `List Text` always stores a finite number of `Text` strings (although the list's length is not bounded in advance).

The greatest fixpoint `GFix F` is a (potentially infinite) stream of `Text` values.
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

So, the mathematical notation for the greatest fixpoint of `T = F T` is `GFix F = ∃ r. r × (r → F r)`.

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

To see that `GFix` is a higher-order function, we let Dhall's REPL expand the definition of `GFix`:

```dhall
⊢ GFix

λ(F : Type → Type) →
  ∀(r : Type) → (∀(t : Type) → { seed : t, step : t → F t } → r) → r
```

A rigorous proof that `GFix F` is indeed the greatest fixpoint of `T = F T` is shown in the appendix "Naturality and parametricity".
Here we will focus on the practical use of the greatest fixpoints.

### Greatest fixpoints for mutually recursive types

Consider two mutually recursive types (this example was shown in the section "Mutually recursive types" of chapter "Church encodings for more complicated types"):

```haskell
-- Haskell.
data Layer = Layer (F1 Layer Layer2)
data Layer2 = Layer2 (F2 Layer Layer2)
```

Define two pattern functors `F1` and `F2` by:


```dhall
let F1 = λ(a : Type) → λ(b : Type) → < Name : Text | OneLayer : b | TwoLayers: { left : b, right : b } >
let F2 = λ(a : Type) → λ(b : Type) → < Name2 : Text | ManyLayers : List a >
```

Then Dhall types `Layer` and `Layer2` can be defined like this:

```dhall
let Layer = Exists (λ(a : Type) → Exists (λ(b : Type) → { seed : a, step1 : a → F1 a b, step2 : b → F2 a b}))
let Layer2 = Exists (λ(a : Type) → Exists (λ(b : Type) → { seed : b, step1 : a → F1 a b, step2 : b → F2 a b}))
```

We will prove in the Appendix "Naturality and parametricity" that these type formulas actually encode the greatest fixpoints.

For the rest of this chapter, we will focus on the simpler case of a single recursively defined type.

### The fixpoint isomorphism

Because `GFix F` is a fixpoint of `T = F T`, the types `T` and `F T` are isomorphic.
It means there exist two functions, here called `fixG : F T → T` and `unfixG : T → F T`, which are inverses of each other.

To implement these functions, we need to assume that `F` belongs to the `Functor` typeclass and has an `fmap` method.

We begin by implementing `unfixG : GFix F → F (GFix F)` (that function is called `out` in the paper "Recursive types for free").

To see how `unfixG g` could work, let us write the type of `g : GFix F` in detail:

```dhall
let g : ∀(r : Type) → (∀(t : Type) → { seed : t, step : t → F t } → r) → r = ???
```
One way of consuming `g` is by applying the function `g` to some arguments.
What arguments should we choose?

The final result of `unfixG g` must be a value of type `F (GFix F)`.
The return type of `g` is an arbitrary type `r` (which is the first argument of `g`).
Because we need to return a value of type `F (GFix F)`, we set `r = F (GFix F)`.

The second argument of `g` is a function of type `∀(t : Type) → { seed : t, step : t → F t } → F (GFix F)`.
If we could produce such a function `f`, we would complete the code of `unfixG`:

```dhall
let unfixG : GFix F → F (GFix F)
  = λ(g : ∀(r : Type) → (∀(t : Type) → { seed : t, step : t → F t } → r) → r) →
    let f : ∀(t : Type) → { seed : t, step : t → F t } → F (GFix F)
      = λ(t : Type) → λ(p : { seed : t, step : t → F t }) → ???
    in g (F (GFix F)) f
```

Within the body of `f`, we have a type `t` and two values `p.seed : t` and `p.step : t → F t`.
So, we could create a value of type `GFix F` as `pack (GF_T F) t p`.
(The function "pack" was defined in the section "Working with existential types".)

However, `f` is required to return not a value of type `GFix F` but a value of type `F (GFix F)`.
To achieve that, we use a trick: we first create a function of type `t → GFix F`.
That function will pack a given value `x : t` together with the "step" function `p.step` into a value of type `GFix F`.

```dhall
-- Given a type t = ??? and p : { seed : t, step : t → F t }.
let k : t → GFix F = λ(x : t) → pack (GF_T F) t { seed = x, step = p.step }
```

Then we will apply `fmap_F` to that function, which will give us a function of type `F t → F (GFix F)`.

```dhall
-- Here t = ??? is a fixed type.
let fk : F t → F (GFix F) = fmap_F t (GFix F) k
```

Finally, we apply the function `fk` to `p.step p.seed`, which is a value of type `F t`.
The result is a value of type `F (GFix F)` as required.

Note that the function `f` depends only on the pattern functor `F` and not on the specific value `g`.
So, it will be convenient to implement `f` separately; we will call it `packF`.

The complete Dhall code is:

```dhall
let packF : ∀(F : Type → Type) → Functor F → ∀(t : Type) → { seed : t, step : t → F t } → F (GFix F)
      = λ(F : Type → Type) → λ(functorF : Functor F) → λ(t : Type) → λ(p : { seed : t, step : t → F t }) →
        let k : t → GFix F = λ(x : t) → pack (GF_T F) t { seed = x, step = p.step }
        let fk : F t → F (GFix F) = functorF.fmap t (GFix F) k
        in fk (p.step p.seed)
let unfixG : ∀(F : Type → Type) → Functor F → GFix F → F (GFix F)
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(g : GFix F) →
    g (F (GFix F)) (packF F functorF)
```

Implementing the function `fixG : F (GFix F) → GFix F` is simpler, once we have `unfixG`.
We first compute `fmap_F unfixG : F (GFix F) → F (F (GFix F))`.
Then we create a value of type `GFix F` by using `pack` with `t = F (GFix F)`:

```dhall
let fixG : ∀(F : Type → Type) → Functor F → F (GFix F) → GFix F
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(fg : F (GFix F)) →
    let fmap_unfixG : F (GFix F) → F (F (GFix F)) = functorF.fmap (GFix F) (F (GFix F)) (unfixG F functorF)
    in pack (GF_T F) (F (GFix F)) { seed = fg, step = fmap_unfixG }
```

### Data constructors and pattern matching

To create values of type `GFix F` more conveniently, we will now implement a function called `makeGFix`.
The code of that function uses the generic `pack` function (defined in the section "Working with existential types") to create values of type `∃ r. r × (r → F r)`.

```dhall
let makeGFix = λ(F : Type → Type) → λ(r : Type) → λ(x : r) → λ(rfr : r → F r) →
  pack (GF_T F) r { seed = x, step = rfr }
```

Creating a value of type `GFix F` requires an initial "seed" value and a "step" function.
We imagine that the code will run the "step" function as many times as needed, in order to retrieve more values from the data structure.

The required reasoning is quite different from that of creating values of the least fixpoint types.
The main difference is that the `seed` value needs to carry enough information for the `step` function to decide which new data to create at any place in the data structure.

Because the type `T = GFix F` is a fixpoint of `T = F T`, we always have the function `fixG : F T → T`.
Similarly to the case of Church encodings, the function `fix` provides a set of constructors for `GFix F`.
Those constructors are "finite": they cannot create an infinite data structure.
For that, we need the general constructor `makeGFix`.

We can also apply `unfixG` to a value of type `GFix F` and obtain a value of type `F (GFix F)`.
We can then perform pattern-matching directly on that value, since `F` is typically a union type.

So, similarly to the case of Church encodings, `fixG` provides constructors and `unfixG` provides pattern-matching for co-inductive types.

### Example of a co-inductive type: "Stream"

To build more intuition for working with co-inductive types, we will now implement a number of functions for a specific example.

Consider the greatest fixpoint of the pattern functor for `List`:

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
let makeStream = λ(a : Type) → makeGFix (F a)
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
let _ = makeStream : ∀(a : Type) → ∀(r : Type) →
  ∀(x : r) → ∀(rfr : r → < Cons : { head : a, tail : r } | Nil > )
    → Stream a
```

We see that `makeStream` constructs a value of type `Stream a` out of an arbitrary type `a`, a type `r` (the internal state of the stream), an initial "seed" value of type `r`, and a "step" function of type `r → < Cons : { head : a, tail : r } | Nil >`.

The type `Stream a` is heuristically understood as a potentially infinite stream of data items (values of type `a`).
Of course, we cannot store infinitely many values in memory.
Values are retrieved one by one, by running the "step" function as many times as needed, or until "step" returns `Nil` (indicating the end of the stream).

Given a value `s : Stream a`, how can we run the "step" function?
We need to apply `s` (which is a function) to an argument `x` of the following type:

```dhall
let x : ∀(t : Type) → { seed : t, step : t → < Cons : { head : a, tail : t } | Nil > } → r = ???
```

So, we need to provide a function of that type.
That function's code will be of the form:

```dhall
λ(t : Type) → λ(stream : { seed : t, step : t → < Cons : { head : a, tail : t } | Nil > }) → ???
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


#### Truncated streams

We can stop a given stream after a given number `n` of items, creating a new value of type `Stream a` that has at most `n` data items.

```dhall
let Stream/truncate : ∀(a : Type) → Stream a → Natural → Stream a
 = λ(a : Type) → λ(stream : Stream a) → λ(n : Natural) →
   let State = { remaining : Natural, stream : Stream a }    -- Internal state of the new stream.
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


Just like `streamToList`, the function `Stream/truncate` requires an explicit bound on the size of the
output list. It is impossible to implement a function that determines whether a given stream terminates.
Also, we cannot terminate a stream at the data item that satisfies some condition (say, at the first
`Natural` number that is equal to zero).
Streams represent conceptually "infinite" structures, and
working with those structures in System Fω often requires an explicit upper bound on the number of
possible iterations.

#### The `cons` constructor for streams. Performance issues

The `cons` operation for lists will prepend a single value to a list.
The analogous operation for streams can be implemented as a special case of concatenating streams:

```dhall
let Stream/cons : ∀(a : Type) → a → Stream a → Stream a
 = λ(a : Type) → λ(x : a) → λ(stream : Stream a) → Stream/concat a (listToStream a [ x ]) stream
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

### Running aggregations ("scan" and "scanMap")

A typical task for streams is to perform **running aggregations**.
A running aggregation extracts each new value from a source stream and updates an aggregated value in some way.
This results in a new stream of aggregated values computed after consuming each value from the source stream.
So, running aggregations may be viewed as transformations of type `Stream a → Stream b`.
Each value in the result stream may depend in some way on the previously seen values in the source stream.

Examples of running aggregations are running sums, running averages, sliding-window averages, and histogram sampling.

For example, the running sum transforms the stream `[1, 2, 3, 4, 5, ...]` into `[1, 3, 6, 10, 15, ...]`.

A general function for running aggregations is called `scan`.
Its type signature is quite similar to that of `fold`:

`scan : ∀(a : Type) → Stream a → ∀(b : Type) → b → (a → b → b) → Stream b`

Here `b` is the type of the aggregated value.
The argument of type `a → b → b` takes the next value of type `a`, the previous aggregated value of type `b`, and computes the next aggregated value of type `b`.
The argument of type `b` is the initial aggregated value.

Unlike `fold` that consumes the entire collection, `scan` computes the new stream one value at a time.


To implement the `scan` function for the `Stream` type, we create a new stream whose values will be of type `b` and whose internal state will contain a source stream (of type `Stream a`) and the current aggregated value.
The code is:

```dhall
let Stream/scan = λ(a : Type) → λ(sa : Stream a) → λ(b : Type) → λ(init : b) → λ(update : a → b → b) →
  let State = { source : Stream a, current : b }
  let initState : State = { source = sa, current = init }
  let ResultT = < Cons : { head : b, tail : State } | Nil >
  let step : State → ResultT = λ(s : State) → merge {
      None = ResultT.Nil,
      Some = λ(headTail : { head : a, tail : Stream a }) →
       let newCurrent = update headTail.head s.current
       in ResultT.Cons { head = newCurrent, tail = { source = headTail.tail, current = newCurrent } },
    } (headTailOption a s.source)
  in makeStream b State initState step
```

As an example, we implement a running sum computation via `scan`:

```dhall
let runningSum : Stream Natural → Stream Natural
  = λ(sn : Stream Natural) → Stream/scan Natural sn Natural 0 (λ(x : Natural) → λ(sum : Natural) → x + sum)

let _ = assert : streamToList Natural (runningSum (repeatForever Natural [ 1, 2, 3 ])) 7
        ≡ [ 1, 3, 6, 7, 9, 12, 13 ]
```

A running aggregation could accumulate _all_ previously seen values into a list.
The result is a function we may call `runningList`:

```dhall
let runningList : ∀(a : Type) → Stream a → Stream (List a)
  = λ(a : Type) → λ(sa : Stream a) → Stream/scan a sa (List a) ([] : List a) (λ(x : a) → λ(current : List a) → current # [ x ] )
let _ = assert : streamToList (List Natural) (runningList Natural (repeatForever Natural [ 1, 2, 3 ])) 5
        ≡ [ [1], [1, 2], [1, 2, 3], [1, 2, 3, 1], [1, 2, 3, 1, 2] ]
```

This is different from the function `streamToList`.
When we apply `streamToList`, we have to give an explicit bound on the size of the output list.
When we apply `runningList`, we obtain a _stream_ of lists of growing size.
We can decide later how many values to take from that stream.

Another version of `scan` is a function `scanMap` that uses a `Monoid` type constraint.
Instead of the initial aggregated value, it uses the "empty" value of the monoid.
The updating function is the monoid's "append" operation.

The function `scanMap` is analogous to `foldMap` and can be implemented via `scan` as:

```dhall
 let Stream/scanMap : ∀(m : Type) → Monoid m → ∀(a : Type) → (a → m) → Stream a → Stream m
  = λ(m : Type) → λ(monoidM : Monoid m) → λ(a : Type) → λ(map : a → m) → λ(sa : Stream a) →
    Stream/scan a sa m monoidM.empty (λ(x : a) → λ(y : m) → monoidM.append (map x) y)
```

We can implement `runningSum` and `runningList` via `scanMap` like this:

```dhall

```

The `Stream` type constructor is a functor.
The corresponding `fmap` function, called `Stream/map`, can be implemented like this:

```dhall
-- Define some typeclass instances to make code more concise.
let HT = λ(h : Type) → λ(t : Type) → < Cons : { head : h, tail : t } | Nil >
let bifunctorHT : Bifunctor HT = { bimap = λ(a : Type) → λ(c : Type) → λ(f : a → c) → λ(b : Type) → λ(d : Type) → λ(g : b → d) → λ(pab : HT a b) → merge {
  Cons = λ(ht : { head : a, tail : b }) → (HT c d).Cons { head = f ht.head, tail = g ht.tail },
  Nil = (HT c d).Nil,
} pab }
let Pack_t = λ(r : Type) → λ(h : Type) → ∀(t : Type) → { seed : t, step : t → HT h t } → r
let contrafunctor_Pack_t : ∀(r : Type) → Contrafunctor (Pack_t r) = λ(r : Type) → {
   cmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(pb : Pack_t r b) →
   -- Compute a value of type Pack_t r a:
     λ(t : Type) → λ(state : { seed : t, step : t → HT a t }) → pb t {
       seed = state.seed,
       step = λ(x : t) → bifunctorHT.bimap a b f t t (identity t) (state.step x),
     }
}
let Stream/map : ∀(a : Type) → ∀(b : Type) → (a → b) → Stream a → Stream b
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(sa : Stream a) →
  -- Compute a value of type Stream b:
    λ(r : Type) → λ(pack_b : ∀(t : Type) → { seed : t, step : t → HT b t } → r) →
      let pack_a : Pack_t r a = (contrafunctor_Pack_t r).cmap a b f pack_b
      in sa r pack_a
let functorStream : Functor Stream = { fmap = Stream/map }

let _ = assert : streamToList Natural (Stream/map Natural Natural (λ(x : Natural) → x * 10) (listToStream Natural [ 1, 2, 3 ]) ) 5 ≡ [ 10, 20, 30 ]
```

Note that the type signatures of `Stream/map` and `Stream/scanMap` are somewhat similar.
The main difference between `Stream/map` and `Stream/scanMap` is that `Stream/scanMap` can accumulate information about previously transformed data items in the stream, while `Stream/map` can only transform one data item at a time.

It turns out that `scanMap` is equivalent to `scan` at the level of types, as long as the parametricity assumptions hold.
The equivalence "at the level of types" (that is, a **type isomorphism**) means that _all possible_ implementations of `scan` (satisfying appropriate laws) are in a one-to-one correspondence to all possible implementations of `scanMap`.
So, it is not an accident that `scanMap` can be expressed via `scan` and vice versa.

The isomorphism between the types of `scan` and `scanMap` is analogous to the isomorphism between `foldLeft` and `reduce` proved in Chapter 12 of ["The Science of Functional Programming"](https://leanpub.com/sofp).
In this book, we will not show the full proof, as the focus is on practical applications.


### Converting from the least fixpoint to the greatest fixpoint

A value of a greatest fixpoint type can be created from a given value of the corresponding least fixpoint type.

Creating a value of the type `GFix F` requires a value of some type `t` and a function of type `t → F t`.
The least fixpoint type `LFix F` already has that function (`unfix`).
So, we can implement a conversion function:

```dhall
let toGFix : ∀(F : Type → Type) → Functor F → LFix F → GFix F
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(x : LFix F) →
    makeGFix F (LFix F) x (unfix F functorF)
```

Because of the use of `unfix`, the resulting fixpoint value will have poor performance: it will traverse the entire initial data structure (`x`) when fetching every new element.


## Translating recursive code into Dhall

In any Dhall definition, such as `let x = ...`, the right-hand side of `let x` may _not_ recursively refer to the same `x` being defined.
The lack of support for recursion applies both to types and to values.

Nevertheless, we have seen that Dhall can work with recursive types if one uses a trick known as the Church encoding of fixpoints.

In this chapter, we will see that Dhall can also accept a wide range of recursive _code_, including code that does not use any recursive types.
This is achieved by a procedure we call the Hu-Iwasaki-Takeichi ("HIT") algorithm.
The HIT algorithm defines some auxiliary types and then converts a given recursive code into a special form, known as a "hylomorphism".
To adapt the resulting hylomorphism to Dhall's constraints and to provide a termination guarantee, the programmer must supply an explicit upper bound on the recursion depth and a "stop-gap" value to be used if the recursion bound turns out to be too low.
In many cases, those modifications are straightforward.

We will begin by explaining the notion of a "hylomorphism" and giving some examples.

### Motivation for hylomorphisms

We have seen the function `streamToList` that extracts at most a given number of values from the stream.
This function can be seen as an example of a **size-limited aggregation**: a function that aggregates data from the stream in some way but reads no more than a given number of data items from the stream.
(The size limit guarantees termination.)

We will now generalize size-limited aggregations from lists to arbitrary greatest fixpoint types.
The result will be a `fold`-like function whose recursion depth is limited in advance.
That limitation will ensure that all computations terminate, as Dhall requires.

The type signature of ordinary `fold` is a generalization of `List/fold` to arbitrary pattern functors.
We have seen `fold`'s type signature when we considered fold-like aggregations for Church-encoded data:

`fold : LFix F → ∀(r : Type) → (F r → r) → r`

By a **fold-like aggregation** we mean any function applied to some data type `P` that iterates over the values stored in `P` in some way.
The general type signature of a fold-like aggregation is `P → ∀(r : Type) → (F r → r) → r`.

The implementation of `fold` will be different for each data structure `P`.
If `P` is the Church encoding of the least fixpoint of `F` then `P`'s `fold` is an identity function because the type `LFix F` is the same as `∀(r : Type) → (F r → r) → r`.
If `P` is the greatest fixpoint (`GFix F`), the analogous type signature of `P`'s `fold` would be:

`GFix F → ∀(r : Type) → (F r → r) → r`

Note that this type is a function from an existential type, which is used to define `GFix F`.
Function types of that kind are equivalent to simpler function types (see the section "Functions of existential types"):

```dhall
GFix F → Q      -- Symbolic derivation.
  ≡  Exists (GF_T F) → Q
  ≡  ∀(t : Type) → GF_T F t → Q
```

We use this equivalence with `Q = ∀(r : Type) → (F r → r) → r` and `GF_T F t = { seed : t, step : t → F t }` as appropriate for streams.
Then we obtain the type signature:

`∀(t : Type) → { seed : t, step : t → F t } → ∀(r : Type) → (F r → r) → r`

Rewrite that type by replacing the record by two curried arguments:

`∀(t : Type) → t → (t → F t) → ∀(r : Type) → (F r → r) → r`

Functions with this type signature are called **hylomorphisms**.
See also [this tutorial](https://blog.sumtypeofway.com/posts/recursion-schemes-part-5.html).


So far, we have motivated hylomorphisms as fold-like functions adapted to greatest fixpoint types (instead of least fixpoints).
Because of the universal quantifiers `∀(t : Type)` and `∀(r : Type)` in their type signature, hylomorphisms are in fact more general: they can be used to transform values of an arbitrary type `t` into values of another type `r`, as long as we can supply a suitable functor `F` and some functions of types `t → F t` and `F r → r`.
An intuitive picture of that sort of computation is that the given function of type `t → F t` will be used repeatedly to "unfold" a given value of type `t` into a tree-like data structure of type `F (F (... (F t)...))`, while the function of type `F r → r` will be used repeatedly to extract the required output values (of type `r`) from that tree-like structure.
The types `t` and `r` do not need to be fixpoint types.

Another way of understanding hylomorphisms is to rewrite their type signature as:

`GFix F → ∀(r : Type) → (F r → r) → r  ≅  GFix F → LFix F`

This can be now seen as a conversion from the greatest fixpoint to the least fixpoint of the same pattern functor.
The converse transformation (from the least fixpoint to the greatest fixpoint) can be implemented in Dhall as shown in the previous chapter.

Now we turn to the question of implementing hylomorphisms in Dhall.
An immediate problem for Dhall is that termination of hylomorphisms is not (and _cannot_ be) guaranteed.
To see why, note that a hylomorphism converts `GFix F` to `LFix F` in a way that is natural in `F` (i.e., it works in the same way for all pattern functors `F`).
This sort of conversion can be done only by copying all values from one data structure to another, completely preserving the recursive structure.
However, a value of a greatest fixpoint type (for example, an unbounded list or an unbounded tree) could allow us to extract an unbounded number of data items, while values of least fixpoint types are always bounded (that is, the data size must be known in advance).
A hylomorphism's code will try to extract all data from an unbounded list, which cannot terminate.

So, Dhall cannot directly support hylomorphisms as they are usually defined.
We will now examine that problem in more detail and show some solutions.

### Why hylomorphisms terminate: a Haskell example

For the purposes of this book, a hylomorphism is just the `fold` function operating on the greatest fixpoint of a given pattern functor `F`.
We would like to implement a hylomorphism with that type signature that works in a uniform way for all `F`.
This is possible if we use explicit recursion (which Dhall does not support).
Here is Haskell code adapted from [B. Milewski's blog post](https://bartoszmilewski.com/2018/12/20/open-season-on-hylomorphisms/):

```haskell
-- Haskell.
hylo :: Functor f => (t -> f t) -> (f r -> r) -> t -> r
hylo coalg alg = alg . fmap (hylo coalg alg) . coalg
```

The code of `hylo` calls `hylo` recursively under `fmap`, and there seems to be no explicit termination for the recursion.
Nevertheless, this code will terminate for some input values `t`.
Specifically, hylomorphisms will terminate when `t` produces a finite data structure when `coalg` is being repeatedly applied to it.

Let us consider an example
where both `t` and `r` are the type of (finite) binary trees with string-valued leaves.
We have denoted that type by `TreeText` before.
The type constructor `f` will be the pattern functor for `TreeText`.

Our Haskell definitions for `TreeText`, its pattern functor `F`, and the `fmap` method for `F` are:

```haskell
-- Haskell.
data TreeText = Leaf String | Branch TreeText TreeText

data F r = FLeaf String | FBranch r r

fmap :: (a -> b) -> F a -> F b
fmap f (FLeaf t) = FLeaf t
fmap f (FBranch x y) = FBranch (f x) (f y)
```

The type `TreeText` is the least fixpoint of `F` and has the standard methods `fix : F TreeText → TreeText` and `unfix : TreeText → F TreeText`.
Haskell implementations of `fix` and `unfix` are little more than identity functions that reassign types:

```haskell
-- Haskell.
fix :: F TreeText -> TreeText
fix FLeaf t -> Leaf t
fix FBranch x y -> Branch x y

unfix :: TreeText -> F TreeText
unfix Leaf t -> FLeaf t
unfix Branch x y -> FBranch x y
```

We may substitute `fix` and `unfix` as the `alg` and `coalg` arguments of `hylo` as shown above, because their types match.
The result (`hylo unfix fix`) will be a function of type `TreeText → TreeText`.
Because `fix` and `unfix` are isomorphisms, the function `hylo unfix fix` will be just an identity function of type `TreeText → TreeText`.
In this example of applying `hylo`, we expect that the input tree should remain unchanged because we just unpack the tree's recursive type (`TreeText → F TreeText`, `F TreeText → F (F TreeText)`, and so on) and then pack it back (applying `F TreeText → TreeText`) with no changes.
We are using this artificial example only for understanding how the recursion can terminate in the Haskell code of `hylo`.

Choose some input value `t0` of type `TreeText`:

```haskell
-- Haskell.
t0 :: TreeText
t0 = Branch (Leaf "a") (Leaf "b")
```

Denote `hylo unfix fix` by just `h` for brevity.
The recursive code of `h` is just `h = fix . fmap h . unfix`.
Now we expand the recursive definition of `h` three times, starting with the expression `h t0`:

```haskell
h t0
  == (fix . fmap h . unfix) t0
  == fix (fmap h (unfix t0))
  == fix (fmap (fix . fmap h . unfix) (unfix t0))
  == fix ( (fmap fix . fmap (fmap h) . fmap unfix) (unfix t0))
  == fix (fmap fix (fmap (fmap h) (fmap unfix (unfix t0))))
```

The argument of `fmap (fmap h)` in the last line is `fmap unfix (unfix t0)`.
This is a value of type `F (F TreeText)` that we temporarily denote by `c0`.
Then `h t0` is given by:

```haskell
h t0 == fix (fmap fix (fmap (fmap h) c0))
```

Let us compute `c0`:

```haskell
unfix t0 == FBranch (Leaf "a") (Leaf "b")

c0 == fmap unfix (unfix t0) == FBranch (FLeaf "a") (FLeaf "b")
```

We note that each application of `unfix` replaces one layer of `TreeText`'s constructors by one layer of `F`'s constructors.
All constructors of `TreeText` will be eliminated after applying `unfix`, `fmap unfix`, etc., as many times as the recursion depth of `t0` requires (in this example, just two times).

After that, the value `c0` no longer contains any constructors of `TreeText`; it is built only with `F`'s constructors.
For that reason, `c0` will _remain unchanged_ under any further application of `fmap (fmap f)` with _any_ function `f : TreeText → TreeText`.
In other words:

```haskell
fmap (fmap f) c0 == c0  -- For any f.
```

It follows that the computation `fmap (fmap f) c0` _does not use_ the value `f`.

Our code for `h t0` needs to compute `fmap (fmap h) c0`.
Because that computation does not use the value `h`, and because Haskell's evaluation is lazy, Haskell will not perform any more recursive calls to `h`.
This is why the recursion terminates at the step when we reach the computation `h t0`.

If the value `t0` had been a more deeply nested tree, we would need to expand the recursive definition of `h` more times.
The required number of recursive calls is equal to the "recursion depth" of the value `t0`.
(The subsection "Sizing up a Church-encoded type constructor" showed how to compute that depth for Church-encoded data types.)

We can now generalize this example to an arbitrary application of a hylomorphism.
For brevity, we denote `h = hylo coalg alg`.
The function `h : t → r` is then defined by `h = alg . fmap h . coalg`.
When we apply `h` to some value `t0 : t`, we get: `h t0 = alg (fmap h (coalg t0))`.

The recursion will terminate if, at some recursion depth, the expression `fmap (fmap (... (fmap f)...)) c` does not actually need to use the function `f`.
We will then have `fmap (fmap (... (fmap f)...)) c == c`.
At this point, the result `c` will be returned and no more recursive calls to `h` will be made.

The type of `c` will be `f (f (... (f t)))`.
It is a data structure generated by repeated applications of `coalg`, `fmap coalg`, `fmap (fmap coalg)`, etc., to the initial value `t0`.
These repeated applications create a data structure of a deeply nested type: `f (f (... (f t)))`.
At a certain recursion depth, that data structure will no longer contain any values of type `t`.
So, applying a nested `fmap (fmap (... (fmap f)...)` to that data structure will not need to transform any data
and will be an identity transformation.

We find that the hylomorphism's recursive code will terminate
only if the data structure of type `f (f (... (f t)))`
generated out of the initial "seed" value (`t0`) will no longer contain values of type `t`
after a finite number of recursion steps.

The hylomorphism's argument has type `GFix F`.
However, it is impossible to assure up front that a given data structure of type `GFix F`
in fact has only a finite recursion depth.
So, the termination of the hylomorphism is in general not guaranteed.
As a result, no function with the type signature of `hylo` can be implemented in Dhall.

### Depth-bounded hylomorphisms

What we _can_ do is to implelment functions with type signatures similar to those of a hylomorphism,
but with extra arguments that explicitly ensure termination.

One possibility, [shown as an example in an anonymous blog post](https://sassa-nf.dreamwidth.org/90732.html), is to add a `Natural`-valued bound on the depth of recursion, together with a "stop-gap" value.
The stop-gap value will be used when the recursion bound is smaller than the actual recursion depth of the input data.
If the recursion bound is large enough, the hylomorphism's output value will be independent of the stop-gap value.

To show how that works, we begin with Haskell code for the depth-bounded hylomorphism.
Then we will translate that code to Dhall.

The idea of depth-bounded hylomorphisms is to expand the recursive definition (`h = alg . fmap h . coalg`, where we denoted `h = hylo coalg alg`) only a given number of times.
To be able to do that, we begin by choosing some initial value for `h`.
Then we expand the recursive definition repeatedly, up to the given depth bound.
We denote the initial value of `h` by `stopgap` and the intermediate results by `h_1`, `h_2`, `h_3`, ...:

```haskell
h_0 = stopgap
h_1 = alg . fmap h_0 . coalg
h_2 = alg . fmap h_1 . coalg
h_3 = alg . fmap h_2 . coalg
...
```

All the intermediate values `h_1`, `h_2`, `h_3`, ..., are still of type `t → r`.
After repeating this procedure `n` times (where `n` is a given natural number), we will obtain a function `h_n : t → r`.
The example in the previous subsection shows that applying `h_n` to a value `t` will give a result (of type `r`) that does not depend on the `stopgap` value, as long as the recursion depth `n` is large enough.

Let us now implement this logic in Dhall:

```dhall
let hylo_Nat : ∀(F : Type → Type) → Functor F →
    Natural → ∀(t : Type) → t → (t → F t) → ∀(r : Type) → (F r → r) → (t → r) → r
  = λ(F : Type → Type) → λ(functorF : Functor F) →
    λ(limit : Natural) → λ(t : Type) → λ(seed : t) → λ(coalg : t → F t) → λ(r : Type) → λ(alg : F r → r) → λ(stopgap : t → r) →
      let update : (t → r) → t → r = λ(f : t → r) → λ(y : t) → alg (functorF.fmap t r f (coalg y))
      let transform : t → r = Natural/fold limit (t → r) update stopgap
      in transform seed
```

The function `hylo_Nat` is a general fold-like aggregation function that can be used with arbitrary pattern functors `F`.
Termination is assured because we specify a limit for the recursion depth in advance.
This function will be used later in this book when implementing the `zip` method for Church-encoded type constructors.

For now, let us see some examples of using `hylo_Nat` with an explicit bound on the recursion depth.

### Determining the recursion depth

The function `hylo_Nat` expresses a hylomorphism via `Natural/fold`, which requires the user to specify up front the maximum recursion depth (the total number of iterations for `Natural/fold`).

The Dhall interpreter has an optimization for `Natural/fold`:
the iterations will stop before reaching the maximum number if the current intermediate result stops changing.
This mechanism works well for calculations with numbers but does not work for `hylo_Nat` because the intermediate result is a function (of type `t → r`).
Each iteration changes that function, adding a layer of `fmap` and a composition with other functions (`coalg` and `alg`).
It is true that the result of applying that function to a particular argument may no longer change after a certain number of iterations.
But the function itself is different at each iteration, and so `Natural/fold` will not be able to terminate the loop early.

Keeping this in mind, we will proceed in two steps:

- Implement a separate function (`hylo_max_depth`) for computing the required recursion depth. We will call that function before running `hylo_Nat`.
- Modify `hylo_Nat` so that the iterations stop automatically once the maximum required recursion depth is reached.

We begin by implementing a function for computing the maximum required recursion depth for a hylomorphism.

Recall that a hylomorphism `hylo coalg alg x` stops its iterations when the repeated application
of `coalg` to `x` produces a value `p : F (F (... (F t)...))` such that
`fmap_F (fmap_F (... (fmap_F f)...)) p` leaves `p` unchanged and does not actually call `f`.
This happens when some constructors of the union type `F t` do not store any values of type `t`.
To detect that condition, we need to be able to check whether any values of type `t` are actually
stored in a given data structure `p : F (F (... (F t)...))`.

We begin by implementing a function `contains_t` for checking whether a value of type `F t` contains any values of type `t`.
For that, we need to be able to extract all values of type `t` out of a given data structure of type `F t`.
This functionality will be available if the functor `F` is _foldable_.
We will require `Functor` and `Foldable` typeclass evidence for `F`
(such evidence can be created for any polynomial functor `F`).

TODO: replace this logic by simply checking that the result of toList is a non-empty list. In that case, the data structure is non-empty and contains some values of type t.

Suppose `p : F t` is a given value.
As `F` is a functor, we first use `F`'s `fmap` method to replace all values of type `t` by the Boolean value `True`.
(The Haskell code would be `fmap (\_ -> True) p`.)
The result is a value of type `F Bool`.
Then we use `F`'s `toList` method for performing the Boolean "or" operation over all Boolean values contained in that data structure.
For that, we will use the Dhall library function `Bool/or` that performs the "or" operation over all Boolean values in a list.
The resulting value will be `True` if and only if the data structure contains a `True` value.
The Dhall code is:
```dhall
let Bool/or = https://prelude.dhall-lang.org/Bool/or
let contains_t
  : ∀(F : Type → Type) → Functor F → Foldable F → ∀(t : Type) → F t → Bool
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(foldableF : Foldable F) → λ(t : Type) → λ(p : F t) →
    let replacedByTrue : F Bool = functorF.fmap t Bool (λ(_ : t) → True) p
    in Bool/or (foldableF.toList Bool replacedByTrue)
```

To test this code, we define the functor `FT`, which is the pattern functor of a binary tree with `Natural` leaf values:

```dhall
let FT = λ(t : Type) → < Leaf : Natural | Branch : { left : t, right : t } >
let functorFT = { fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(f1a : FT a) → merge { Leaf = (FT b).Leaf, Branch = λ(branch : { left : a, right : a }) → (FT b).Branch { left = f branch.left, right = f branch.right } } f1a }
let foldableFT = { toList = λ(a : Type) → λ(fa : FT a) → merge { Leaf = λ(_ : Natural) → [] : List a, Branch = λ(branch : { left : a, right : a }) → [ branch.left, branch.right ] } fa }
```

To see that the function `contains_t` works as expected, let us test it on some values of type `FT t`:
```dhall
let _ =
  let t = Text
  let check : FT t → Bool = contains_t FT functorFT foldableFT t
  let test1 : FT t = (FT t).Leaf 123   -- Does not contain values of type t.
  let test2 : FT t = (FT t).Branch { left = "a", right = "b" } -- Contains values of type t.
in { _1 = assert : check test1 ≡ False, _2 = assert : check test2 ≡ True }
```

The next step is to implement a check for the presence of values of type `t` in a data structure of type `F (F (... (F t)...))` having $n$ nested layers of type constructors `F`.
To achieve that, we first need to apply `fmap_F` $n$ times to the function `λ(_ : t) → True`.
This gives a function that replaces values of type `t` by `True` at the deepest nesting level in the data structure:

`fmap_F (fmap_F (... (fmap_F (λ(_ : t) → True)))...)) {- n times -} : F (F (... (F t)...)) {- n times -} → F (F (... (F Bool)...)) {- n times -}`

Then we need to apply `fmap_F` $n-1$ times to the function `findTrueValues` shown above:

`fmap_F (fmap_F (... (fmap_F (λ(_ : t) → True)))...)) {- n times -} : F (F (... (F Bool)...)){- n times -} → F (F (... (F Bool)...)) {- n-1 times -}`

Applying that function will reduce by $1$ the number of nested layers of `F`.
We need to keep doing this until we remove all layers of `F` and obtain a `Bool` value.
We may describe the procedure symbolically like this:

```haskell
findTrue : F Bool → Bool = Bool/or . foldableF.toList Bool
replace = λ(_ : t) → True

h0 : t → Bool = replace
h1 : F t → Bool = findTrue . fmap_F h0
h2 : F (F t) → Bool = findTrue . fmap_F h1
h3 : F (F (F t)) → Bool = findTrue . fmap_F h2
hN : F (F (... (F t)...)) {- n times -} → Bool = findTrue . fmap_F hN-1
```

The function `hN` will be applied to a value obtained by repeatedly applying `coalg : t → F t` to some initial value `p : t`.
We can describe that by:

```haskell
c1 : t → F t = coalg
c2 : t → F (F t) = fmap_F c1
c3 : t → F (F (F t)) = fmap_F c2
...
```

Now we notice that the composition `hN . cN` is equivalent to the code of a depth-bounded hylomorphism with a stop-gap value, that is, `hylo_Nat`, with depth limit `N` and the stop-gap function equal to `replace`.
After `N` iterations, we will have transformed an initial value `p : t` via `cN` into a value of type `F (F (... (F t)...)) {- n times -}` and then back into a `Bool` value via `hN`.

The final step is to write code for finding the smallest `N` for which the resulting `Bool` value becomes `False`.
For that, we will of course need an absolute upper bound on possible `N`.
Given that bound, we write a loop using `Natural/fold` such that the current accumulated value stops changing when the value `hN (cN p)` first becomes `False`.
The loop accumulates a hylomorphism of type `t → Bool` that we use to detect the presence of values of type `t`.
When no values of type `t` are present, we stop changing the accumulated value.
The code is:
```dhall
let hylo_max_depth
  : ∀(F : Type → Type) → Functor F → Foldable F → Natural → ∀(t : Type) → (t → F t) → t → Natural
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(foldableF : Foldable F) → λ(limit : Natural) → λ(t : Type) → λ(coalg : t → F t) → λ(p : t) →
    let replace : t → Bool = λ(_ : t) → True
    let findTrue : F Bool → Bool = λ(p : F Bool) → Bool/or (foldableF.toList Bool p)
    let Acc = { depth : Natural, hylo : t → Bool }
    let update : Acc → Acc = λ(acc : Acc) →
      let newHylo : t → Bool = λ(x : t) → findTrue (functorF.fmap t Bool acc.hylo (coalg x))
      let hasValuesT = acc.hylo p
      in if hasValuesT then { depth = acc.depth + 1, hylo = newHylo } else acc
    let init : Acc = { depth = 0, hylo = replace }
    let result = Natural/fold limit Acc update init
    in result.depth
```

To test this code, we use the functor `FT` defined above and implement a function `coalg : Natural → FT Natural` such that iterating `coalg n` beyond depth `n` will no longer put values of type `t` into trees of type `FT (FT (... (FT t)...))`.
```dhall
-- Note that FT t is just < Leaf : Natural | Branch : { left : t, right : t } >
let FNat = FT Natural
let coalg : Natural → FT Natural = λ(n : Natural) →
  let n-1 = Natural/subtract 1 n
  in if Natural/isZero n then FNat.Leaf 0
     else FNat.Branch { left = n-1, right = n-1 }
let tests =
  let FFNat = FT (FT Natural)
  let FFFNat = FT (FT (FT Natural))
  let fmapCoalg : FNat → FFNat = functorFT.fmap Natural FNat coalg
  let fmapFmapCoalg : FFNat → FFFNat = functorFT.fmap FNat FFNat fmapCoalg
  let _ = assert : coalg 0 ≡ FNat.Leaf 0
  let _ = assert : coalg 1 ≡ FNat.Branch { left = 0, right = 0 }
  let _ = assert : fmapCoalg (coalg 1) ≡ FFNat.Branch { left = FNat.Leaf 0, right = FNat.Leaf 0 }
  let _ = assert : fmapFmapCoalg (fmapCoalg (coalg 1)) ≡ FFFNat.Branch { left = FFNat.Leaf 0, right = FFNat.Leaf 0 }
  in "tests pass"
```
The tests show that repeated application of `coalg` to `1` produces a data structure that stops changing after the second `fmap`.
So, we expect `hylo_max_depth` to return `2` when applied to that `coalg`:
```dhall
let _ = assert : hylo_max_depth FT functorFT foldableFT 10 Natural coalg 1 ≡ 2
```

Now, instead of calling `hylo_Nat F functorF limit t x coalg r alg stopgap`, we can write `hylo_Nat F functorF (max_depth F functorF foldableF limit coalg t) t x coalg r alg stopgap`.

To make the usage of hylomorphisms simpler, let us modify `hylo_Nat` so that the maximum recursion depth is calculated and applied automatically.
We will accumulate two depth-bounded hylomorphisms: one for detecting the recursion depth and another for computing the actual result value.
We will keep the accumulated value unchanged once the maximum recursion depth is reached.
The shortcut detection mechanism in `Natural/fold` will then automatically stop the iterations.

The resulting function is `hylo_N`:
```dhall
let hylo_N : ∀(F : Type → Type) → Functor F → Foldable F →
    Natural → ∀(t : Type) → t → (t → F t) → ∀(r : Type) → (F r → r) → (t → r) → r
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(foldableF : Foldable F) →
    λ(limit : Natural) → λ(t : Type) → λ(seed : t) → λ(coalg : t → F t) → λ(r : Type) → λ(alg : F r → r) → λ(stopgap : t → r) →
      let replace : t → Bool = λ(_ : t) → True
      let findTrue : F Bool → Bool = λ(p : F Bool) → Bool/or (foldableF.toList Bool p)
      let Acc = { depthHylo : t → Bool, resultHylo : t → r }
      let update : Acc → Acc = λ(acc : Acc) →
        let newDepthHylo : t → Bool = λ(x : t) → findTrue (functorF.fmap t Bool acc.depthHylo (coalg x))
        let newResultHylo : t → r = λ(y : t) → alg (functorF.fmap t r acc.resultHylo (coalg y))
        let hasValuesT = acc.depthHylo seed
        in if hasValuesT then { depthHylo = newDepthHylo, resultHylo = newResultHylo } else acc
      let init : Acc = { depthHylo = replace, resultHylo = stopgap }
      let result = Natural/fold limit Acc update init
      in result.resultHylo seed
```

Speed tests show that `hylo_N` is somewhat faster than computing the maximum depth separately.

TODO:try revising this logic, accumulating both the h function and the result of applying h to seed? No. We can't just stop when the result of applying to seed stops changing. We need to accumulate a second hylomorphism that detects the max depth.

### Example: the Egyptian division algorithm

The [Egyptian algorithm for integer division](https://isocpp.org/blog/2016/08/turning-egyptian-division-into-logarithms) can be written via recursive code like this:

```haskell
egyptian_div_mod :: Int -> Int -> (Int, Int)   -- Haskell.
egyptian_div_mod a b =  -- Divide a / b assuming that b > 0.
  if a < b then (0, a) else if a - b < b then (1, a - b)
      else
        let (quotient, remainder) = egyptian_div_mod a (2 * b) -- Recursive call.
        in
          if remainder < b then (2 * quotient, remainder)
          else (2 * quotient + 1, remainder - b)
```

The function `egyptian_div_mod` is recursive and cannot be directly translated to Dhall.
We have two options:
- By trial and error, we can perhaps guess how to convert `egyptian_div_mod` into some calls to `Natural/fold` that Dhall accepts.
- Use a general procedure for rewriting the recursive code of `f` into a hylomorphism, then implement that in Dhall using the depth-bounded function `hylo_N`.

The general procedure for converting recursive code to hylomorphisms is explained in the paper by Hu, Iwasaki, and Takeichi (HIT), ["Deriving structural hylomorphisms"](https://www.researchgate.net/publication/2813507).
The HIT derivation procedure applies to a wide range of recursive functions including the egyptian division algorithm.
We will now follow that procedure for the code of `egyptian_div_mod` shown above.

A first problem is that
the HIT derivation procedure works with functions of a single argument, while `egyptian_div_mod` has two (curried) arguments.
So, let us first refactor `egyptian_div_mod` into a recursive function of a single argument.
Notice that recursive calls to `egyptian_div_mod` only change the value of the argument `b`, while the value of `a` remains the same for all recursive calls.
We will define `egyptian_div_mod a b = e_div_mod b` where `e_div_mod` is defined within the scope of `egyptian_div_mod` so that it captures the value of `a`.
We will also introduce helper functions `postprocess1` and `postprocess2` to make the structure of the code more transparent:

```haskell
egyptian_div_mod :: Int -> Int -> (Int, Int)   -- Haskell.
egyptian_div_mod a b =
  let
    postprocess1 :: Int -> (Int, Int)
    postprocess1 b = if a < b then (0, a) else (1, a - b)
    postprocess2 :: ((Int, Int), Int) -> (Int, Int)
    postprocess2 ((quotient, remainder), b) =
      if remainder < b then (2 * quotient, remainder)
      else (2 * quotient + 1, remainder - b)

    e_div_mod :: Int -> (Int, Int)
    e_div_mod b =
      if a - b < b then postprocess1 b
      else postprocess2 ((e_div_mod (2 * b)), b) -- Recursive call.
  in e_div_mod b
```

The function `e_div_mod` can be rewritten as a hylomorphism if we find a functor `P` such that the code of `e_div_mod` is expressed as a composition of three functions:

- A function `coalg` of type `Int → P Int`.
- A recursive call to `fmap_P e_div_mod`, where `fmap_P` is the functor `P`'s `fmap` method. This gives a function of type `P Int → P (Int, Int)`.
- A function `alg` of type `P (Int, Int) → (Int, Int)`.

Then we will be able to write: `e_div_mod == alg . fmap_P e_div_mod . coalg`, which means that `e_div_mod` is a hylomorphism.

To find a suitable functor `P`, we note that the code of `e_div_mod` contains an `if/then/else` construction for deciding whether a recursive call to `e_div_mod` is needed.
To reproduce an `if/then/else` via the hylomorphism formula `alg . fmap_P e_div_mod . coalg`, we need to choose `P` such that `fmap_P` skips calling `e_div_mod` in one case but does call it in another case.
This can be achieved if `P x` is a union type with two constructors, the first one not containing any values of type `x`, and the second one containing a single value of type `x`.
For example, if we define `P` and the corresponding `fmap_P` by this Haskell code:
```haskell
type P x = P1 Int | P2 x         -- Haskell.
fmap_P :: (a -> b) -> P a -> P b
fmap_P f (P1 i) = P1 i
fmap_P f (P2 a) = P2 (f a)
```
then the application `fmap_P e_div_mod (P1 123)` will not call `e_div_mod`, while the application `fmap_P e_div_mod (P2 123)` will.

So, the functor `P` must be chosen as a union type whose structure describes the presence or the absence of recursive calls in the various choice branches of the function `e_div_mod`.
In our case, it is sufficient if the union type `P x` has _two_ parts because the code of `e_div_mod` only has two choice branches (one branch without recursive calls and one branch with one recursive call):
```haskell
    ...
    e_div_mod b =
      if a - b < b then postprocess1 b      -- No recursive calls.
      else postprocess2 (e_div_mod (2 * b)) -- Recursive call.
    ...
```

The next question is what data should the constructors `P1` and `P2` carry.
Do we need to define `P` as `type P x = P1 (Int, Int, Int) | P2 (x, Int, Int)` or as something else like that?
To answer that question, we look at the data `e_div_mod` requires in the two branches when computing the final result:

- In the first branch, a single integer value (`b`) is used to compute the result as `postprocess1 b`.
So, it suffices to define `P1` storing a single integer (the value of `b`).

- In the second branch, we apply `postprocess2` to a pair of integers returned by the recursive call `e_div_mod (2 * b)`.
The recursive call will be taken care of by `fmap_P e_div_mod`.
The result of that call will be a value of type `P (Int, Int)`.
So, the type parameter `x` in `P x` will be actually set to `(Int, Int)` when `fmap_P` is applied.
In addition, `postprocess2` needs to have the value of `b`.

We conclude that it is sufficient to use `P2 (x, Int)` in the definition of the union type.

This consideration shows that `P` can be defined as the Haskell code `data P x = P1 Int | P2 (x, Int)` or the Dhall equivalent:

```dhall
let P = λ(X : Type) → < P1 : Natural | P2 : { p : X, b : Natural } >
let fmap_P : FmapT P
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(pa : P a) →
    merge {
      P1 = (P b).P1,
      P2 = λ(x : { p : a, b : Natural }) → (P b).P2 { p = f x.p, b = x.b },
    } pa
let functorP : Functor P = { fmap = fmap_P }
let toList_P : ∀(a : Type) → P a → List a
  = λ(a : Type) → λ(pa : P a) → merge {
      P1 = λ(_ : Natural) → [] : List a,
      P2 = λ(x : { p : a, b : Natural }) → [x.p],
  } pa
let foldableP : Foldable P = { toList = toList_P }
```

The "postprocessing" steps in the code of `e_div_mod` are translated into a function `alg : P (Int, Int) → (Int, Int)` implemented in Haskell as:
```haskell
alg :: P (Int, Int) -> (Int, Int)  -- Haskell.
alg (P1 b) = postprocess1 b
alg (P2 ((quotient, remainder), b)) = postprocess2 ((quotient, remainder), b)
```

This code can be rewritten in Dhall straightforwardly.
For convenience, we define a record type `Result` that holds the quotient and the remainder.
Also, we add an extra argument to some functions for supplying a value of `a`:

```dhall
let Result = { div : Natural, rem : Natural }
let postprocess1 = λ(a : Natural) → λ(b : Natural) →
  -- if a < b then (0, a) else (1, a - b)
  if Natural/lessThan a b then { div = 0, rem = a }
  else { div = 1, rem = Natural/subtract b a }
let postprocess2 = λ(p2 : { p : Result, b : Natural }) →
    -- if remainder < b then (2 * quotient, remainder) else (2 * quotient + 1, remainder - b)
  let quotient = p2.p.div
  let remainder = p2.p.rem
  in if Natural/lessThan remainder p2.b then { div = 2 * quotient, rem = remainder }
     else { div = 2 * quotient + 1 , rem = Natural/subtract p2.b remainder }

let alg : Natural → P Result → Result
  = λ(a : Natural) → λ(pp : P Result) → merge {
    P1 = postprocess1 a,
    P2 = postprocess2,
  } pp
```

It remains to implement the function `coalg` whose Haskell type signature is `Int → P Int`.
That function must do two things: first, it must decide which of the parts (`P1` or `P2`) of the union type `P Int` will be created.
Second, in case the calculation must use a recursive call, `coalg` must prepare the data for the recursive invocation as well as for the post-processing.

A Haskell implementation of `coalg` is:
```haskell
coalg :: Int -> P Int   -- Haskell.
coalg b = if a - b < b then P1 b else P2 (2 * b, b)
```
Here, the value `P2 (2 * b, b)` contains at once the argument `2 * b` for the recursive call of `e_div_mod` and the extra value `b` needed for `postprocess2`.

The code of `coalg` can be translated to Dhall as:

```dhall
let coalg : Natural → Natural → P Natural
  = λ(a : Natural) → λ(b : Natural) →
    if Natural/lessThan (Natural/subtract b a) b then (P Natural).P1 b
    else (P Natural).P2 { p = 2 * b, b = b }
```

This completes the rewriting of `e_div_mod` as a hylomorphism, whose Haskell code would be:

```haskell
e_div_mod = hylo coalg alg  -- Haskell.
```

Now we can implement `e_div_mod` in Dhall using `hylo_Nat` or `hylo_N` with appropriate extra arguments:

```dhall
let egyptian_div_mod : Natural → Natural → Result
  = λ(a : Natural) → λ(b : Natural) →
    let stopgap : Natural → Result = λ(b : Natural) →
       { div = 0, rem = b }   -- An obviously wrong result: remainder cannot be b.
    let limit = a    -- A very imprecise upper bound on the number of iterations.
    in hylo_Nat P functorP limit Natural b (coalg a) Result (alg a) stopgap
-- Test:
let _ = assert : egyptian_div_mod 11 2 ≡ { div = 5, rem = 1 }
```

We may also use `hylo_N` instead of `hylo_Nat`, with an automatic detection of recursion depth and early termination:

```dhall
let egyptian_div_mod : Natural → Natural → Result
  = λ(a : Natural) → λ(b : Natural) →
    let stopgap : Natural → Result = λ(b : Natural) →
       { div = 0, rem = b }   -- An obviously wrong result: remainder cannot be b.
    let limit = a
    in hylo_N P functorP foldableP limit Natural b (coalg a) Result (alg a) stopgap
-- Test:
let _ = assert : egyptian_div_mod 11 2 ≡ { div = 5, rem = 1 }
```

This function is fast enough to divide even very large numbers.
The following test takes just a few seconds (when using Dhall version 1.42.2 or later):

```dhall
⊢ (./tutorial/EgyptianDivision.dhall).egyptian_div_mod_N 1000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000 3

{ div =
    333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333
, rem = 1
}
```

The HIT procedure can convert a wide class of recursive functions into hylomorphisms.
In most cases, we will be able to implement those hylomorphisms in Dhall by choosing appropriate upper limits and stopgap values so that the hylomorphism may be replaced by `hylo_Nat`, which guarantees termination.
This is another practical motivation for studying hylomorphisms.

### Converting recursive code into hylomorphisms: the HIT algorithm

The [HIT paper](https://www.researchgate.net/publication/2813507) gives an algorithm for converting a recursive function into a hylomorphism.
[Another paper](https://www.researchgate.net/publication/2649019) describes an extension of the HIT algorithm for mutually recursive functions.
In this book, we will limit our consideration to the simple HIT algorithm for a single recursive function.

The HIT algorithm works only for recursive code of a certain restricted form:

- The code must have a single top-level pattern-matching expression that decides whether (and how many) recursive calls are needed.
- Each pattern-matching branch may have zero or more recursive calls. The number of recursive calls must be known _statically_ within each pattern-matching branch.
- Recursive calls are not nested: the arguments of recursive calls are computed without any further recursive calls.

Code of that form can be described by this Haskell skeleton:

```haskell
-- Haskell. A recursive function `f` of type X → Y is defined by:
f :: X -> Y
f x = case do_choice x of
  C0 x0 -> post_0 x0
  C1 x1 -> post_1 x1 (f (arg_1_1 x1)) (f (arg_1_2 x1)) ... (f (arg_1_n1 x1))
  C2 x2 -> post_2 x2 (f (arg_2_1 x2)) (f (arg_2_2 x2)) ... (f (arg_2_n2 x1))
  ...
  where
    do_choice = ...
    post_0 = ...
    post_1 = ...
    arg_1_1 = ...
    arg_1_2 = ...
    ...
```
Here, the function `do_choice` has type `X → C`, where `C` is a known union type with branches `C0`, `C1`, `C2`, etc.
We assume that values `x0`, `x1`, etc., have types `A0`, `A1`, etc., so that the union type `C` may be defined in Dhall as:
```dhall
let C = < C0 : A0 | C1 : A1 | C2 : A2 | ??? and so on >
```
The types `A0`, `A1`, etc., must be chosen such that they can carry all the information needed for the remaining computations in each of the choice branches.

The functions `arg_1_n` (with $n=1,2,...$) have type `A1 → X`, the functions `arg_2_n` (with $n=1,2,...$) have type `A2 → X`, etc.

The functions `post_n` (with $n=1,2,...$) have types `An → Y → Y → ... → Y` with as many arguments of type `Y` as recursive calls of the function `f` in the corresponding branch.

In the first branch (the Haskell code line `C0 x0 -> ...`), there are no recursive calls, so we have `post_0 : A0 → Y`.
The first branch does not use any recursive calls of `f` and computes the result immediately as `post_0 x0`.
If the code of `f` contains several such branches with no recursive calls, we may redefine the union type `C` so that all those branches are combined into a single one, described by the constructor `C0`.

Other branches (`C1`, `C2`, etc.) _do_ require one or more recursive calls to `f`.
The arguments for those recursive calls are computed from the available data (`x1`, `x2`, etc.) using functions that we denoted by `arg_1_1`, `arg_1_2`, `arg_2_1`, etc.
Once the recursive calls are completed, the post-processing functions (`post_1`, `post_2`, etc.) are used to compute the final results.

Starting from recursive Haskell code for `f` in the skeleton form shown above, the HIT algorithm derives an equivalent code for `f` as a hylomorphism.

We begin by defining the union type `C` and the function `do_choice: X → C` by following the Haskell code of `f` as indicated above.

The next step is to determine a suitable functor `P` that will be used for defining the hylomorphism.
To figure that out, notice that a hylomorphism's Haskell code contains recursion at _only one_ place:
```haskell
hylo coalg alg = alg . (fmap (hylo coalg alg)) . coalg  -- Haskell.
```
The function `hylo` calls itself only via `fmap hylo`.
So, the recursive calls correspond to places where the data structure of type `P t` stores values of type `t`.
Those stored values are used as _arguments_ of the recursive calls (because that's how `fmap` works).

It follows that we need to choose `P` such that `P t` stores a separate value of type `t` for each recursive call.
The data type `P t` will be a union type with as many constructors (`P0`, `P1`, etc.) as the branches `C0`, `C1`, etc.
For the code skeleton shown above, we would need to define `P` as:

```dhall
let P = λ(T : Type) →
< | P0 : A0
  | P1 : { a1 : A1, call_1 : T, call_2 : T, ..., call_n1 : T }
  | P2 : { a2 : A2, call_1 : T, call_2 : T, ..., call_n2 : T }
  | ???  -- And so on.
>
```
We will also need to define `Functor` and `Foldable` typeclass instances for the chosen functor `P`.
(Later chapters in this book show general procedures for defining such typeclass instances for all functors `P` of the required form.)

The next step is to define suitable functions `coalg : X → P X` and `alg : P Y → Y` such that the code of `f` is equivalent to `hylo coalg alg`.
The function `coalg` prepares the arguments for the recursive calls, and the function `alg` performs the post-processing after the recursive calls are done:
```dhall
let coalg : X → P X = λ(x : X) →
  let choice : C = do_choice x  -- As in the code of `f`.
  merge { -- Prepare the function arguments for recursive calls.
    C0 = λ(x0 : A0) → (P X).P0 x0,
    C1 = λ(x1 : A1) → (P X).P1 { a1 = x1, call_1 = arg_1_1 x1, ..., call_n1 = arg_1_n1 x1 },
    C2 = λ(x2 : A2) → (P X).P2 { a2 = x2, call_1 = arg_2_1 x2, ..., call_n2 = arg_2_n2 x2 },
    ???  -- And so on.
  } choice

let alg : P Y → Y = λ(fy : P Y) →
  merge {
    P0 = λ(r0 : A0) → post_0 r0,
    P1 = λ(r1 : { a1 : A1, call_1 : Y, call_2 : Y, ..., call_n1 : Y }) → post_1 r1.a1 r1.call_1 r1.call_2 ... r1.call_n1,
    P2 = λ(r2 : { a2 : A2, call_1 : Y, call_2 : Y, ..., call_n2 : Y }) → post_2 r2.a2 r2.call_1 r2.call_2 ... r2.call_n2,
    ???  -- And so on.
  } fy
```

In this way, the HIT algorithm rewrites the code of `f` in terms of a hylomorphism.
It remains to estimate an upper bound for the number of iterations and apply `hylo_Nat` or `hylo_N` with a suitable stop-gap argument.
That will guarantee termination, and the resulting code will be accepted by Dhall.

### Example: Fibonacci numbers

As an artificial but instructive example of a recursive function that does not use any recursive types, consider a straightforward (but quite slow) implementation of a function that computes the $n$-th Fibonacci number:

```haskell
fibonacci :: Int -> Int  -- Haskell.
fibonacci n = if n < 3 then 1 else fibonacci (n - 1) + fibonacci (n - 2)
```

This code is not acceptable in Dhall because `fibonacci` is defined recursively.
Let us now apply the HIT algorithm to the Haskell code shown above.

We set the types `X = Y = Natural`.
The first step is to define the type `C` and the function `do_choice : X → C`.
The type `C` should be a union type that describes the possible choices in making the recursive calls.
The code of `fibonacci` has a top-level choice with two branches: no recursive calls and 2 recursive calls.
So, the type `C` should be a union type with two parts, such as `< BaseCase : A0 | RecCase : A1 >`.

What are the suitable types `A0` and `A1`?
The information in those types must be sufficient to compute the result values in each branch.
Looking at the code, we find that each branch just needs the input value (`n`).
So, we could define the type `C` as a union type with two parts carrying no information:
```dhall
let C = < BaseCase | RecCase >
```
The types `A0` and `A1` are just unit types, and we may omit the values `x0 : A0` and `x1 : A1`.
To simplify the code further, we could use the `Bool` type as `C`:
```dhall
let C = Bool
let do_choice : Natural → Bool = λ(n : Natural) → Natural/lessThan n 3
```

The next step is to define the functor `P`.
The type `P A` must be a union type with two cases:
```dhall
let P : Type → Type = λ(A : Type) → < P0 : ??? | P1 : ??? >
```

The first case (`P0`) corresponds to the clause without recursive calls.
The output value in that clause is always just `1`.
So, we do not need `P0` to carry any values.
Let us make `P0` an empty constructor.

The second part of the union type (`P1`) needs to carry the values needed for the two arguments ($n-1$ and $n-2$) of the recursive calls.
So, we define `P1` as a record type with two values of type `A`.
(The type `A` will be set to `Natural` when the functor `P` is used.)
The complete code for `P` becomes:
```dhall
let P : Type → Type = λ(A : Type) → < P0 | P1 : { call_1 : A, call_2 : A } >
let functorP : Functor P = {
  fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(pa : P a) →
    merge {
      P0 = (P b).P0,
      P1 = λ(r1 : { call_1 : a, call_2 : a }) → (P b).P1 { call_1 = f r1.call_1, call_2 = f r1.call_2 }
    } pa
}
```
The functions `arg_1_1`, `arg_1_2`, and `post_1` are defined as:
```dhall
let arg_1_1 = λ(n : Natural) → Natural/subtract 1 n
let arg_1_2 = λ(n : Natural) → Natural/subtract 2 n
let post_1 = λ(r1 : Natural) → λ(r2 : Natural) → r1 + r2
```

Now we can follow the skeleton code shown above and write code for the `alg` and `coalg` functions (that we will call `algFib` and `coalgFib`):
```dhall
let coalgFib : Natural → P Natural = λ(n : Natural) →
  let choice : Bool = do_choice n
  -- Use if/then/else instead of merge on Bool.
  in if choice then (P Natural).P0
  else (P Natural).P1 { call_1 = arg_1_1 n, call_2 = arg_1_2 n }
```

```dhall
let algFib : P Natural → Natural = λ(p : P Natural) →
  merge {
    P0 = 1,
    P1 = λ(r1 : { call_1 : Natural, call_2 : Natural }) →
      post_1 r1.call_1 r1.call_2
  } p
```

It remains to find a stop-gap value and an upper bound on the number of iterations.

The stop-gap value can be any function of type `Natural → Natural`.
So, we can just use a constant function that always returns `0`.
In Dhall, this is written as `const Natural Natural 0`.
(The stop-gap function will never be used, as long as we perform enough iterations.)

A safe upper bound on the number of iterations is the number `n` itself.

The Dhall code for the `fibonacci` function becomes:
```dhall
let fibonacci : Natural → Natural
  = λ(n : Natural) → hylo_Nat P functorP n Natural n coalgFib Natural algFib (const Natural Natural 0)
let _ = assert : fibonacci 8 ≡ 21
```

What is the time complexity of the hylomorphism-based `fibonacci` function?
At first sight, it may appear that the complexity is linear because the hylomorphism runs `Natural/fold n`, which iterates a function `n` times.
But actually the complexity is still exponential in `n`, just like the initial recursive code.
The reason is that the $n$-th iteration works with a data structure of type `P (P (... (P Natural) ...))` nested $n$ times.
With our definition of $P$, that data structure is a binary tree of depth $n$, which stores $2^n$ values of type `Natural`.
Processing that data structure takes exponential time ($O(2^n)$).

The HIT algorithm does not change the asymptotic performance of recursive code.
It only converts the code into the form of a hylomorphism.
To improve the asymptotic complexity of the resulting code, it would be best to start with a faster recursive algorithm, such as the ["doubling algorithm"](https://www.nayuki.io/page/fast-fibonacci-algorithms) for the Fibonacci sequence.

Alternatively, one could use techniques such as ["shortcut fusion"](https://ora.ox.ac.uk/objects/uuid:0b493c43-3b85-4e3a-a844-01ac4a45c11b) that works directly with hylomorphisms.
Such techniques are beyond the scope of this book.
We use hylomorphisms only as a vehicle for converting recursive code to a form that Dhall will accept.

### Hylomorphisms driven by a Church-encoded template

In the code for `hylo_Nat`, the total number of iterations was limited by a given natural number.
To drive the iterations, we used the standard `fold` method (`Natural/fold`) for natural numbers.

Note that `Natural` is a built-in recursive type in Dhall, and `Natural/fold` is also a built-in function.
Could we drive iterations via the `fold` method for a different recursive type?

Suppose we already have a value of the Church-encoded least fixpoint type (`LFix F`).
That value can serve as a "recursion template" that at the same time provides depth limits and all necessary default values.

We will denote the template-driven hylomorphism by `hylo_T`.
The type signature is `LFix F → GFix F → LFix F`.
We will again expand the type signature and unpack the existential types into a curried argument.
The Dhall code is:

```dhall
let hylo_T
 : LFix F → ∀(t : Type) → t → (t → F t) → ∀(r : Type) → (F r → r) → r
  = λ(template : LFix F) → λ(t : Type) → λ(seed : t) → λ(coalg : t → F t) → λ(r : Type) → λ(alg : F r → r) →
    let F/ap : ∀(a : Type) → ∀(b : Type) → F (a → b) → F a → F b = ??? -- Implement this function for F.
    let reduce : F (t → r) → t → r
      = λ(ftr : F (t → r)) → λ(arg : t) → alg (F/ap t r ftr (coalg t))
    let transform : t → r = template (t → r) reduce
    in transform seed
```

For this code, we need to have a function `F/ap` with type `F (a → b) → F a → F b`.
In many cases, such a function exists.
This function is typical of "applicative functors", which we will study later in this book.

As long as the pattern functor `F` is applicative, we will be able to implement `hylo_T` for `F`.
(We will see below that all polynomial functors are applicative.)

## Combinators for monoids

A type is a monoid if there are methods called `empty` and `append` that satisfy appropriate laws.
As we have seen in the "Typeclasses" chapter,
Dhall defines enough operations for `Bool` values, `Natural` numbers, `Text` strings, and `List` values to support those methods and to have a `Monoid` typeclass evidence.
It turns out that there are general combinators that produce `Monoid` evidence for larger types built from smaller ones.
We will now explore those combinators systematically and show the corresponding `Monoid` typeclass evidence.
The proofs that the laws hold are given in ["The Science of Functional Programming"](https://leanpub.com/sofp), Chapter 8.

### "Optional" monoid

For any type `T`, the type `Optional T` is a monoid.
The `empty` value is `None T`.
Appending `Some x` to `Some y` will just keep one of the values and discard the other.
This gives two ways of implementing the `append x y` operation.

We first implement a helper method that selects between two `Optional` values:
```dhall
let Optional/orElse
  : ∀(a : Type) → Optional a → Optional a → Optional a
  = λ(a : Type) → λ(x : Optional a) → λ(y : Optional a) →
    merge { None = y, Some = λ(_ : a) → x } x
```

Now we can write the two possible `Monoid` instances for `Optional T`:

```dhall
let monoidOptionalKeepX : ∀(T : Type) → Monoid (Optional T)
  = λ(T : Type) → { empty = None T
                  , append = Optional/orElse T
                  }
```

```dhall
let monoidOptionalKeepY : ∀(T : Type) → Monoid (Optional T)
  = λ(T : Type) → { empty = None T
                  , append = flip (Optional T) (Optional T) (Optional T) (Optional/orElse T)
                  }
```

### Function monoid

For any type `T`, the function type `T → T` is a monoid.
Its `empty` value is an identity function.
The `append` operation is a function composition.
It may be defined in one of the two ways, depending on the choice of forward or backward composition:

```dhall
let monoidFuncBackward : ∀(T : Type) → Monoid (T → T)
  = λ(T : Type) → { empty = identity T, append = compose_backward T T T }
let monoidFuncForward : ∀(T : Type) → Monoid (T → T)
  = λ(T : Type) → { empty = identity T, append = compose_forward T T T }
```

### Unit type

The unit type (`{}`) is a monoid whose operations always return the value `{=}`.

```dhall
let monoidUnit : Monoid {} = { empty = {=}, append = λ(_ : {}) → λ(_ : {}) → {=} }
```

### Product of monoids

If `P` and `Q` are monoidal types then so is the product type `Pair P Q`.
We implement this property as a combinator function that requires `Monoid` typeclass evidence for both `P` and `Q`:
```dhall
let monoidPair
  : ∀(P : Type) → Monoid P → ∀(Q : Type) → Monoid Q → Monoid (Pair P Q)
  = λ(P : Type) → λ(monoidP : Monoid P) → λ(Q : Type) → λ(monoidQ : Monoid Q) →
    { empty = { _1 = monoidP.empty, _2 = monoidQ.empty }
    , append = λ(x : Pair P Q) → λ(y : Pair P Q) →
       { _1 = monoidP.append x._1 y._1, _2 = monoidQ.append x._2 y._2 }
    }
```

### Co-product of monoids

If `P` and `Q` are monoidal types then so is the co-product type `Either P Q`.
There are two ways of defining the monoid operations for `Either P Q`, depending on the choice of the `empty` method (which can be chosen to be either in the left or in the right part of the `Either` union type).

If we choose the `empty` method to return `Left monoidP.empty` then the `append` operation must return a `Right` value whenever the two values are of different types:
```dhall
let monoidEitherLeft
  : ∀(P : Type) → Monoid P → ∀(Q : Type) → Monoid Q → Monoid (Either P Q)
  = λ(P : Type) → λ(monoidP : Monoid P) → λ(Q : Type) → λ(monoidQ : Monoid Q) →
    { empty = (Either P Q).Left monoidP.empty
    , append = λ(x : Either P Q) → λ(y : Either P Q) →
      merge { Left = λ(py : P) →
        merge { Left = λ(px : P) → (Either P Q).Left (monoidP.append px py)
              , Right = λ(qx : Q) → (Either P Q).Right qx
          } x
            , Right = λ(qy : Q) →
        merge { Left = λ(px : P) → (Either P Q).Right qy
              , Right = λ(qx : Q) → (Either P Q).Right (monoidQ.append qx qy)
          } x
            } y
    }
```

The other choice is when the `empty` method returns `Right monoidQ.empty`.
```dhall
let monoidEitherLeft
  : ∀(P : Type) → Monoid P → ∀(Q : Type) → Monoid Q → Monoid (Either P Q)
  = λ(P : Type) → λ(monoidP : Monoid P) → λ(Q : Type) → λ(monoidQ : Monoid Q) →
    { empty = (Either P Q).Right monoidQ.empty
    , append = λ(x : Either P Q) → λ(y : Either P Q) →
      merge { Left = λ(py : P) →
        merge { Left = λ(px : P) → (Either P Q).Left (monoidP.append px py)
              , Right = λ(qx : Q) → (Either P Q).Left py
          } x
            , Right = λ(qy : Q) →
        merge { Left = λ(px : P) → (Either P Q).Left px
              , Right = λ(qx : Q) → (Either P Q).Right (monoidQ.append qx qy)
          } x
            } y
    }
```


### Functions with monoidal output types

If `P` is _any_ type and `Q` is a monoidal type then the function type `P → Q` is a monoid.
We implement this property as a combinator function:
```dhall
let monoidFunc
  : ∀(P : Type) → ∀(Q : Type) → Monoid Q → Monoid (P → Q)
  = λ(P : Type) → λ(Q : Type) → λ(monoidQ : Monoid Q) →
    { empty = λ(_ : P) → monoidQ.empty
    , append = λ(x : P → Q) → λ(y : P → Q) → λ(p : P) → monoidQ.append (x p) (y p)
    }
```

### Reverse monoid

If a type `P` is a monoid, we may define another implementation of the `append` method by reversing the order of appending the values.
In other words, we may define the new operation `append_reverse x y` as `append y x`.
(The `empty` value remains unchanged.)
The laws will still hold for the new monoid instance.

```dhall
let monoidReverse
  : ∀(P : Type) → Monoid P → Monoid P
  = λ(P : Type) → λ(monoidP : Monoid P) →
    monoidP // { append = λ(x : P) → λ(y : P) → monoidP.append y x }
```

For some monoids `P`, reversing does not change their `append` operation.
Such `P` are called **commutative monoids**.
An example is `P = Natural` with the commutative `append` operation such as `append x y = x + y`.

An example of a non-commutative monoid is the `Optional` monoid shown earlier in this chapter.


## Combinators for functors and contrafunctors

Functors and contrafunctors may be built only in a fixed number of ways, because there is a fixed number of ways one may define type constructors in Dhall (without using dependent types).
We will now enumerate all those ways.
The result is a set of standard combinators that create larger (contra)functors from smaller ones.

All the combinators preserve functor laws; the created new functor instances are automatically lawful.
This is proved in ["The Science of Functional Programming"](https://leanpub.com/sofp), Chapter 6.
We will only give the Dhall code that creates the typeclass instance values for all the combinators.

### Constant (contra)functors

The simplest combinator is a **constant functor**: it is a type constructor that does not depend on its type parameter.
Examples of such type constructors are `F a = Integer` or `G a = List Bool`.
To define them in Dhall, we could write:

```dhall
let F = λ(a : Type) → Integer
let G = λ(a : Type) → List Bool
```

We can generate all such type constructors via the `Const` combinator:

```dhall
let Const : Type → Type → Type
   = λ(c : Type) → λ(_ : Type) → c
```
Using `Const`, we would define `F = Const Integer`, `G = Const (List Bool)` and so on.

The type constructor `Const c` is a functor for any fixed type `c`.
The `Functor` evidence value for `Const c` can be implemented by:

```dhall
let functorConst : ∀(c : Type) → Functor (Const c)
  = λ(c : Type) → { fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → identity (Const c a) }
```

Because the implementation of `fmap f` is just an identity function, a value of a constant functor type does not change under `fmap`.
So, a constant functor is at the same time a contrafunctor.
An evidence value for `Contrafunctor (Const c)` can be written as:

```dhall
let contrafunctorConst : ∀(c : Type) → Contrafunctor (Const c)
  = λ(c : Type) → { cmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → identity (Const c a) }
```

### Identity functor

The **identity functor** is the type constructor `Id` such that `Id a = a`.

A `Functor` evidence value for `Id` can be implemented as:

```dhall
let Id = λ(a : Type) → a

let functor_Id : Functor Id  = { fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → f }
```

### Functor and contrafunctor composition

If `F` and `G` are two functors then the functor composition `H a = F (G a)` is also one.
We compute the type via the combinator called `Compose`, which is analogous to the function combinator `compose` defined earlier in this book.

```dhall
let Compose : (Type → Type) → (Type → Type) → (Type → Type)
  = λ(F : Type → Type) → λ(G : Type → Type) → λ(a : Type) → F (G a)
```

The `Functor` evidence for `Compose F G` can be constructed automatically if the evidence values for `F` and `G` are known:

```dhall
let functorFunctorCompose
  : ∀(F : Type → Type) → Functor F → ∀(G : Type → Type) → Functor G → Functor (Compose F G)
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(G : Type → Type) → λ(functorG : Functor G) →
    { fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) →
        let ga2gb : G a → G b = functorG.fmap a b f
        in functorF.fmap (G a) (G b) ga2gb
    }
```

If `F` is covariant but `G` is contravariant (or vice versa), the composition of `F` and `G` becomes contravariant.
We can also automatically construct the evidence values for those cases:

```dhall
let functorContrafunctorCompose
  : ∀(F : Type → Type) → Functor F → ∀(G : Type → Type) → Contrafunctor G → Contrafunctor (Compose F G)
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(G : Type → Type) → λ(contrafunctorG : Contrafunctor G) →
    { cmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) →
        let gb2ga : G b → G a = contrafunctorG.cmap a b f
        in functorF.fmap (G b) (G a) gb2ga
    }
```

```dhall
let contrafunctorFunctorCompose
  : ∀(F : Type → Type) → Contrafunctor F → ∀(G : Type → Type) → Functor G → Contrafunctor (Compose F G)
  = λ(F : Type → Type) → λ(contrafunctorF : Contrafunctor F) → λ(G : Type → Type) → λ(functorG : Functor G) →
    { cmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) →
        let ga2gb : G a → G b = functorG.fmap a b f
        in contrafunctorF.cmap (G a) (G b) ga2gb
    }
```

Finally, the composition of two contrafunctors is again a covariant functor:

```dhall
let contrafunctorContrafunctorCompose
  : ∀(F : Type → Type) → Contrafunctor F → ∀(G : Type → Type) → Contrafunctor G → Functor (Compose F G)
  = λ(F : Type → Type) → λ(contrafunctorF : Contrafunctor F) → λ(G : Type → Type) → λ(contrafunctorG : Contrafunctor G) →
    { fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) →
        let gb2ga : G b → G a = contrafunctorG.cmap a b f
        in contrafunctorF.cmap (G b) (G a) gb2ga
    }
```

### Products and co-products

To implement the product of two type constructors, we use Dhall records:


```dhall
let Pair = λ(a : Type) → λ(b : Type) → { _1 : a, _2 : b }
let Product : (Type → Type) → (Type → Type) → (Type → Type)
  = λ(F : Type → Type) → λ(G : Type → Type) → λ(a : Type) → Pair (F a) (G a)
```
This creates a new type constructor `Product F G` out of two given type constructors `F` and `G`.

The product of two functors is again a functor, and an evidence value can be constructed automatically.
For that, it is convenient to use the function pair product operation `fProduct` defined in the chapter "Programming with functions".

```dhall
let fProduct : ∀(a : Type) → ∀(b : Type) → (a → b) → ∀(c : Type) → ∀(d : Type) → (c → d) → Pair a c → Pair b d
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(c : Type) → λ(d : Type) → λ(g : c → d) → λ(arg : Pair a c) →
    { _1 = f arg._1, _2 = g arg._2 }

let functorProduct
  : ∀(F : Type → Type) → Functor F → ∀(G : Type → Type) → Functor G → Functor (Product F G)
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(G : Type → Type) → λ(functorG : Functor G) →
    { fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) →
        -- Return a function of type Pair (F a) (G a) → Pair (F b) (G b).
        fProduct (F a) (F b) (functorF.fmap a b f) (G a) (G b) (functorG.fmap a b f)
    }
```

Similar code works for contrafunctors:

```dhall
let contrafunctorProduct
  : ∀(F : Type → Type) → Contrafunctor F → ∀(G : Type → Type) → Contrafunctor G → Contrafunctor (Product F G)
  = λ(F : Type → Type) → λ(contrafunctorF : Contrafunctor F) → λ(G : Type → Type) → λ(contrafunctorG : Contrafunctor G) →
    { cmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) →
        -- Return a function of type Pair (F b) (G b) → Pair (F a) (G a).
        fProduct (F b) (F a) (contrafunctorF.cmap a b f) (G b) (G a) (contrafunctorG.cmap a b f)
    }
```

To implement the co-product of functors and contrafunctors, we use the type `Either` defined before.

```dhall
let Either = λ(a : Type) → λ(b : Type) → < Left : a | Right : b >

let CoProduct : (Type → Type) → (Type → Type) → (Type → Type)
  = λ(F : Type → Type) → λ(G : Type → Type) → λ(a : Type) → Either (F a) (G a)
```

This creates a new type constructor `CoProduct F G` out of two given type constructors `F` and `G`.

The co-product of two functors is again a functor, and the co-product of two contrafunctors is again a contrafunctor.
Evidence values can be constructed automatically.
For that, it is convenient to use the function pair co-product operation `fCoProduct` defined in the chapter "Programming with functions".

```dhall
let fCoProduct : ∀(a : Type) → ∀(b : Type) → (a → b) → ∀(c : Type) → ∀(d : Type) → (c → d) → Either a c → Either b d
  = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(c : Type) → λ(d : Type) → λ(g : c → d) → λ(arg : Either a c) →
    merge {
           Left = λ(x : a) → (Either b d).Left (f x),
           Right = λ(y : c) → (Either b d).Right (g y),
          } arg
```

```dhall
let functorCoProduct
  : ∀(F : Type → Type) → Functor F → ∀(G : Type → Type) → Functor G → Functor (CoProduct F G)
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(G : Type → Type) → λ(functorG : Functor G) →
    { fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) →
        -- Return a function of type Either (F a) (G a) → Either (F b) (G b).
        fCoProduct (F a) (F b) (functorF.fmap a b f) (G a) (G b) (functorG.fmap a b f)
    }
```

```dhall
let contrafunctorCoProduct
  : ∀(F : Type → Type) → Contrafunctor F → ∀(G : Type → Type) → Contrafunctor G → Contrafunctor (CoProduct F G)
  = λ(F : Type → Type) → λ(contrafunctorF : Contrafunctor F) → λ(G : Type → Type) → λ(contrafunctorG : Contrafunctor G) →
    { cmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) →
        -- Return a function of type Either (F b) (G b) → Either (F a) (G a).
        fCoProduct (F b) (F a) (contrafunctorF.cmap a b f) (G b) (G a) (contrafunctorG.cmap a b f)
    }
```


### Function types with functors and contrafunctors

The function-type functor `H a = F a → G a` is covariant if `F` is contravariant and `G` is covariant.
Similarly, `H` is contravariant if `F` is covariant and `G` is contravariant.

For convenience, we define the `Arrow` combinator:
```dhall
let Arrow : (Type → Type) → (Type → Type) → (Type → Type)
  = λ(F : Type → Type) → λ(G : Type → Type) → λ(a : Type) → F a → G a
```

We can automatically construct the evidence values for the cases we just described:

```dhall
let contrafunctorFunctorArrow
  : ∀(F : Type → Type) → Contrafunctor F → ∀(G : Type → Type) → Functor G → Functor (Arrow F G)
  = λ(F : Type → Type) → λ(contrafunctorF : Contrafunctor F) → λ(G : Type → Type) → λ(functorG : Functor G) →
    { fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(arrowA : F a → G a) → λ(fb : F b) →
        functorG.fmap a b f (arrowA (contrafunctorF.cmap a b f fb))
    }
```

```dhall
let functorContrafunctorArrow
  : ∀(F : Type → Type) → Functor F → ∀(G : Type → Type) → Contrafunctor G → Contrafunctor (Arrow F G)
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(G : Type → Type) → λ(contrafunctorG : Contrafunctor G) →
    { cmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(arrowB : F b → G b) → λ(fa : F a) →
        contrafunctorG.cmap a b f (arrowB (functorF.fmap a b f fa))
    }
```

### Universal and existential type quantifiers

Given a type constructor with multiple type parameters, we may impose a type quantifier on some of the parameters and obtain a type constructor with fewer type parameters.
Imposing type quantifiers will not change the covariance properties of the type constructor.
In this way, we may produce new functors or contrafunctors.

Without loss of generality, we consider a type constructor `F` that has two type parameters and define a new type constructor `G` by imposing a universal type quantifier on the second type parameter of `F`.
(The first type parameter remains free.)
In a mathematical notation, the definition of `G` is $G ~a = \forall b.~ F~ a~ b$.
The corresponding Dhall code is:

```dhall
let F : Type → Type → Type = λ(a : Type) → λ(b : Type) → ???
let G : Type → Type = λ(a : Type) → ∀(b : Type) → F a b
```

If `F a b` is covariant with respect to `a` then so is `G a`; if `F a b` is contravariant with respect to `a` then so is `G a`.

Note that `F a b` could be covariant, contravariant, or neither with respect to the second type parameter (`b`).
To derive the (co/contra)variant functor property of `G`, we only need properties of `F` with respect to the first type parameter.

To express the requirement that `F a b` is covariant with respect to `a` (while `F` could be anything with respect to `b`), we write a `Functor` evidence value for the type constructor `λ(a : Type) → F a b` while keeping `b` fixed:

```dhall
let functorF1
  : ∀(b : Type) → Functor (λ(a : Type) → F a b)
  = λ(b : Type) → { fmap = ??? }
```
Then we can express the functor property of `∀b. F a b` as a function that transforms the functor evidence of `F` to that of `G`.
Note that we only need the functor property of `F a b` with respect to `a`, while `b` is kept fixed.
To implement a functor evidence for `G`, we use the `mapForall` function defined before.
```dhall
let functorForall1
  : ∀(F : Type → Type → Type) → (∀(b : Type) → Functor (λ(a : Type) → F a b)) → Functor (λ(a : Type) → ∀(b : Type) → F a b)
  = λ(F : Type → Type → Type) → λ(functorF1 : ∀(b : Type) → Functor (λ(a : Type) → F a b)) →
    { fmap = λ(c : Type) → λ(d : Type) → λ(f : c → d) →
-- Need a function of type G c → G d. Use mapForall P Q for that,
-- where P and Q are defined such that Forall P = G c and Forall Q = G d.
      let P = F c
      let Q = F d
      let fPQ : ∀(a : Type) → P a → Q a = λ(a : Type) → (functorF1 a).fmap c d f
      in mapForall P Q fPQ
    }
```

Similar code can be written for the type `∀a. F a b` (where the _second_ type parameter remains free).
We omit that code.

In case `F a b` is contravariant in `a`, we can implement a _contrafunctor_ evidence for `G`:

```dhall
let contrafunctorForall1
  : ∀(F : Type → Type → Type) → (∀(b : Type) → Contrafunctor (λ(a : Type) → F a b)) → Contrafunctor (λ(a : Type) → ∀(b : Type) → F a b)
  = λ(F : Type → Type → Type) → λ(contrafunctorF1 : ∀(b : Type) → Contrafunctor (λ(a : Type) → F a b)) →
    { cmap = λ(c : Type) → λ(d : Type) → λ(f : c → d) →
-- Need a function of type G d → G c. Use mapForall for that.
      let P = F c
      let Q = F d
      let fQP : ∀(a : Type) → Q a → P a = λ(a : Type) → (contrafunctorF1 a).cmap c d f
      in mapForall Q P fQP
    }
```

Existential quantifiers have similar properties.
If we define `G` by $G ~a = \exists b.~ F~ a~ b$ then `G` will be covariant if `F a b` is covariant with respect to `a`; and `G` will be contravariant if `F a b` is contravariant with respect to `a`.
It does not matter whether `F a b` is covariant, contravariant, or neither with respect to `b`.

The following code defines the functor or the contrafunctor instances (as appropriate) for the type constructor `G`:
```dhall
let functorExists1
  : ∀(F : Type → Type → Type) → (∀(b : Type) → Functor (λ(a : Type) → F a b)) → Functor (λ(a : Type) → Exists (λ(b : Type) → F a b))
  = λ(F : Type → Type → Type) → λ(functorF1 : ∀(b : Type) → Functor (λ(a : Type) → F a b)) →
    { fmap = λ(c : Type) → λ(d : Type) → λ(f : c → d) →
-- Need a function of type G c → G d. Use mapExists for that.
      let P = F c
      let Q = F d
      let fPQ : ∀(a : Type) → P a → Q a = λ(a : Type) → (functorF1 a).fmap c d f
      in mapExists P Q fPQ
    }
```

```dhall
let contrafunctorExists1
  : ∀(F : Type → Type → Type) → (∀(b : Type) → Contrafunctor (λ(a : Type) → F a b)) → Contrafunctor (λ(a : Type) → Exists (λ(b : Type) → F a b))
  = λ(F : Type → Type → Type) → λ(contrafunctorF1 : ∀(b : Type) → Contrafunctor (λ(a : Type) → F a b)) →
    { cmap = λ(c : Type) → λ(d : Type) → λ(f : c → d) →
-- Need a function of type G d → G c. Use mapExists for that.
      let P = F c
      let Q = F d
      let fQP : ∀(a : Type) → Q a → P a = λ(a : Type) → (contrafunctorF1 a).cmap c d f
      in mapExists Q P fQP
    }
```


### Recursive functors and contrafunctors

Although Dhall does not support recursive types directly, we have seen in previous chapters that least fixpoints and greatest fixpoints can be encoded in Dhall.
Those encodings are built using the record types, the function types, the universal quantifier, and the existential quantifier.
The constructions in the previous subsections show how to build functor and contrafunctor instances for those types.
So, in principle we already know enough to build functor or contrafunctor instances (as appropriate) for arbitrary recursive type constructors.

However, for illustration we will show the Dhall code for those instances.

A least-fixpoint type constructor is defined via a pattern functor that must be a type constructor with two type parameters.
The first type parameter remains free in the resulting recursive type constructor, while the second type parameter is used for recursion.
(See the section "Recursive type constructors" in the chapter "Church encoding for recursive.)

Suppose `F` is a given pattern functor with two type parameters.
Then we can define the recursive type constructor `C` as the least fixpoint of the recursive type equation `C a = F a (C a)`,
and the type constructor `D` as the greatest fixpoint of the same type equation: `D a = F a (D a)`.

Using the notation `LFix` and `GFix` for the least and the greatest fixpoints, we may also write `C a = LFix (F a)` and `D a = GFix (F a)`.

If `F a b` is covariant in `a` then `C` and `D` will be also covariant and will have `Functor` instances.
If `F a b` is contravariant in `a` then `C` and `D` will be also contravariant and will have `Contrafunctor` instances.
For those properties to hold, it does not matter whether `F a b` is covariant or contravariant in `b` (or neither).

We will now show code that takes a `Functor` instance for `F a b` with respect to `a` and produces `Functor` instances for `C` and `D`.
To simplify the code, we will begin by noting that both `LFix P` and `GFix P` are covariant in the type constructor `P`.
For any two type constructors `P` and `Q`, we can transform `LFix P → LFix Q` and `GFix P → GFix Q` given a function of type `∀(a : Type) → P a → Q a`.
Let us implement these transformations for later use:
```dhall
let mapLFix
  : ∀(P : Type → Type) → ∀(Q : Type → Type) → (∀(a : Type) → P a → Q a) → LFix P → LFix Q
  = λ(P : Type → Type) → λ(Q : Type → Type) → λ(f : ∀(a : Type) → P a → Q a) → λ(c : LFix P) →
    λ(r : Type) → λ(qrr : Q r → r) → c r (λ(pr : P r) → qrr (f r pr))
```

```dhall
let mapGFix
  : ∀(P : Type → Type) → ∀(Q : Type → Type) → (∀(a : Type) → P a → Q a) → GFix P → GFix Q
  = λ(P : Type → Type) → λ(Q : Type → Type) → λ(f : ∀(a : Type) → P a → Q a) →
-- GFix P = Exists (λ(r : Type) → { seed : r, step : r → P r })
-- Need a function of type GFix P → GFix Q. Use mapExists for that.
    let GP = λ(a : Type) → { seed : a, step : a → P a }
    let GQ = λ(a : Type) → { seed : a, step : a → Q a }
    let mapGPGQ : ∀(a : Type) → GP a → GQ a = λ(a : Type) → λ(gpa : GP a) → gpa // { step = λ(x : a) → f a (gpa.step x) }
    in mapExists GP GQ mapGPGQ
```

We can now implement the `Functor` evidence for `LFix (F a)` and `GFix (F a)`:
```dhall
let functorLFix
  : ∀(F : Type → Type → Type) → (∀(b : Type) → Functor (λ(a : Type) → F a b)) → Functor (λ(a : Type) → LFix (F a))
  = λ(F : Type → Type → Type) → λ(functorF1 : ∀(b : Type) → Functor (λ(a : Type) → F a b)) →
    let C : Type → Type = λ(a : Type) → LFix (F a)
    in { fmap = λ(c : Type) → λ(d : Type) → λ(f : c → d) →
-- Need a function of type C c → C d. Use mapLFix for that.
-- Define P and Q such that LFix P = C c and LFix Q = C d.
          let P = F c
          let Q = F d
          let mapPQ : ∀(a : Type) → P a → Q a = λ(a : Type) → (functorF1 a).fmap c d f
          in mapLFix P Q mapPQ
       }
```

```dhall
let functorGFix
  : ∀(F : Type → Type → Type) → (∀(b : Type) → Functor (λ(a : Type) → F a b)) → Functor (λ(a : Type) → GFix (F a))
  = λ(F : Type → Type → Type) → λ(functorF1 : ∀(b : Type) → Functor (λ(a : Type) → F a b)) →
    let D : Type → Type = λ(a : Type) → GFix (F a)
    in { fmap = λ(c : Type) → λ(d : Type) → λ(f : c → d) →
-- Need a function of type D c → D d. Use mapGFix for that.
-- Define P and Q such that GFix P = D c and GFix Q = D d.
          let P = F c
          let Q = F d
          let mapPQ : ∀(a : Type) → P a → Q a = λ(a : Type) → (functorF1 a).fmap c d f
          in mapGFix P Q mapPQ
       }
```

Contrafunctor instances for recursive types can be computed by similar code.
Note that the pattern functor `F` must be still covariant in the type parameter on which we impose the fixpoint:
If `F a b` is contravariant in `a` and covariant in `b` then both `LFix (F a)` and `GFix (F a)` are contravariant in `a`.

```dhall
let contrafunctorLFix
  : ∀(F : Type → Type → Type) → (∀(b : Type) → Contrafunctor (λ(a : Type) → F a b)) → Contrafunctor (λ(a : Type) → LFix (F a))
  = λ(F : Type → Type → Type) → λ(contrafunctorF1 : ∀(b : Type) → Contrafunctor (λ(a : Type) → F a b)) →
    let C : Type → Type = λ(a : Type) → LFix (F a)
    in { cmap = λ(c : Type) → λ(d : Type) → λ(f : c → d) →
-- Need a function of type C d → C c. Use mapLFix for that.
-- Define P and Q such that LFix P = C c and LFix Q = C d.
          let P = F c
          let Q = F d
          let mapQP : ∀(a : Type) → Q a → P a = λ(a : Type) → (contrafunctorF1 a).cmap c d f
          in mapLFix Q P mapQP
       }
```

```dhall
let contrafunctorGFix
  : ∀(F : Type → Type → Type) → (∀(b : Type) → Contrafunctor (λ(a : Type) → F a b)) → Contrafunctor (λ(a : Type) → GFix (F a))
  = λ(F : Type → Type → Type) → λ(contrafunctorF1 : ∀(b : Type) → Contrafunctor (λ(a : Type) → F a b)) →
    let D : Type → Type = λ(a : Type) → GFix (F a)
    in { cmap = λ(c : Type) → λ(d : Type) → λ(f : c → d) →
-- Need a function of type D d → D c. Use mapGFix for that.
-- Define P and Q such that GFix P = D c and GFix Q = D d.
          let P = F c
          let Q = F d
          let mapQP : ∀(a : Type) → Q a → P a = λ(a : Type) → (contrafunctorF1 a).cmap c d f
          in mapGFix Q P mapQP
       }
```

### Monads are automatically functors

The `Monad` typeclass was defined in chapter "Typeclasses".
A combinator can convert a `Monad` evidence value into a `Functor` evidence:

```dhall
let monadFunctor
  : ∀(F : Type → Type) → Monad F → Functor F
  = λ(F : Type → Type) → λ(monadF : Monad F) →
    { fmap = λ(a : Type) → λ(b : Type) → λ(f : a → b) → λ(fa : F a) →
        monadF.bind a fa b (λ(x : a) → monadF.pure b (f x))
    }
```

This combinator works because of the assumed monad laws.

## Filterable (contra)functors and their combinators

Dhall's standard prelude has the function `List/filter` that removes values from a list whenever the value does not satisfy a condition:
```dhall
let List/filter = https://prelude.dhall-lang.org/List/filter
let _ = assert : List/filter Natural (Natural/lessThan 4) [ 1, 2, 3, 4, 5, 6, 7, 8 ] ≡ [ 5, 6, 7, 8 ]
```

The notion of a "filterable functor" comes from generalizing this `filter` function to type constructors other than `List`.

It turns out to be more convenient to define `filter` through another function called `deflate`.

We define a **filterable functor** `F` as a (covariant) functor with and additional method called `deflate`, which has the following type signature:

`deflate : F (Optional a) → F a`

A **filterable contrafunctor** `C` has a method called `inflate`, with the following type signature:

`inflate : C a → C (Optional a)`

We can now define the corresponding typeclasses:
```dhall
let Filterable = λ(F : Type → Type) → Functor F //\\ { deflate : ∀(a : Type) → F (Optional a) → F a }
let ContraFilterable = λ(F : Type → Type) → Contrafunctor F //\\ { inflate : ∀(a : Type) → F a → F (Optional a) }
```

Using the `deflate` method from a `Filterable` instance, we may implement a `filter` function like this:
```dhall
let filter
  : ∀(F : Type → Type) → Filterable F → ∀(a : Type) → (a → Bool) → F a → F a
  = λ(F : Type → Type) → λ(filterableF : Filterable F) → λ(a : Type) → λ(cond : a → Bool) → λ(fa : F a) →
    let a2opt : a → Optional a = λ(x : a) → if cond x then Some x else None a
    let foa : F (Optional a) = filterableF.fmap a (Optional a) a2opt fa
    in filterableF.deflate a foa
```

Similarly, we may implement a `cfilter` function for a filterable contrafunctor like this:
```dhall
let cfilter
  : ∀(F : Type → Type) → ContraFilterable F → ∀(a : Type) → (a → Bool) → F a → F a
  = λ(F : Type → Type) → λ(contrafilterableF : ContraFilterable F) → λ(a : Type) → λ(cond : a → Bool) → λ(fa : F a) →
    let a2opt : a → Optional a = λ(x : a) → if cond x then Some x else None a
    let foa : F (Optional a) = contrafilterableF.inflate a fa
    in contrafilterableF.cmap a (Optional a) a2opt foa
```

The functions `deflate` and `inflate` must satisfy certain laws that are detailed in ["The Science of Functional Programming"](https://leanpub.com/sofp), Chapter 9.
That book also shows various combinators that create new `Filterable` and `Contrafilterable` instances out of previous ones, and proves that the resulting instances always obey the laws.
Here, we will focus on implementing those combinators in Dhall.

### Constant filterable (contra)functors

A constant functor is at the same time a contrafunctor and is always filterable.
The `deflate` function and the `inflate` function are just identity functions.
The typeclass instances are:
```dhall
let filterableConst : ∀(c : Type) → Filterable (Const c)
  = λ(c : Type) → functorConst c /\ { deflate = λ(a : Type) → identity c }
let contrafilterableConst : ∀(c : Type) → ContraFilterable (Const c)
  = λ(c : Type) → contrafunctorConst c /\ { inflate = λ(a : Type) → identity c }
```

### Filterable (contra)functor composition

If `F` is a filterable functor and `G` is any functor (_not necessarily_ filterable) then the composition functor `Compose G F` is filterable.
We may implement a `Filterable` instance like this:

```dhall
let filterableFunctorFunctorCompose
  : ∀(F : Type → Type) → Filterable F → ∀(G : Type → Type) → Functor G → Filterable (Compose G F)
  = λ(F : Type → Type) → λ(filterableF : Filterable F) → λ(G : Type → Type) → λ(functorG : Functor G) →
    functorFunctorCompose G functorG F filterableF.{fmap} /\ { deflate = λ(a : Type) → functorG.fmap (F (Optional a)) (F a) (filterableF.deflate a) }
```
In this code, we have reused the function `functorFunctorCompose` from the previous chapter, in order to create a `Functor` typeclass evidence for `Compose G F`.
Then we use the record concatenation operator (`/\`) to add a `deflate` field to the record.

Similar constructions work for filterable contrafunctors.
In general, `Compose G F` is filterable as long as `F` is filterable, regardless of `G`.
There are four possible cases, depending on covariance or contravariance of `F` and `G`:

- If `F` is a filterable functor and `G` is any functor then `Compose G F` is a filterable functor.
- If `F` is a filterable functor and `G` is any contrafunctor then `Compose G F` is a filterable contrafunctor.
- If `F` is a filterable contrafunctor and `G` is any functor then `Compose G F` is a filterable contrafunctor.
- If `F` is a filterable contrafunctor and `G` is any contrafunctor then `Compose G F` is a filterable functor.

For the first case, we just saw the code for a `Filterable` instance.
Here is the corresponding code for the remaining three cases:

```dhall
let filterableContrafunctorFunctorCompose
  : ∀(F : Type → Type) → Filterable F → ∀(G : Type → Type) → Contrafunctor G → ContraFilterable (Compose G F)
  = λ(F : Type → Type) → λ(filterableF : Filterable F) → λ(G : Type → Type) → λ(contrafunctorG : Contrafunctor G) →
    contrafunctorFunctorCompose G contrafunctorG F filterableF.{fmap} /\ { inflate = λ(a : Type) → contrafunctorG.cmap (F (Optional a)) (F a) (filterableF.deflate a) }
```

```dhall
let filterableFunctorContrafunctorCompose
  : ∀(F : Type → Type) → ContraFilterable F → ∀(G : Type → Type) → Functor G → ContraFilterable (Compose G F)
  = λ(F : Type → Type) → λ(contrafilterableF : ContraFilterable F) → λ(G : Type → Type) → λ(functorG : Functor G) →
    functorContrafunctorCompose G functorG F contrafilterableF.{cmap} /\ { inflate = λ(a : Type) → functorG.fmap (F a) (F (Optional a)) (contrafilterableF.inflate a) }
```

```dhall
let filterableContrafunctorContrafunctorCompose
  : ∀(F : Type → Type) → ContraFilterable F → ∀(G : Type → Type) → Contrafunctor G → Filterable (Compose G F)
  = λ(F : Type → Type) → λ(contrafilterableF : ContraFilterable F) → λ(G : Type → Type) → λ(contrafunctorG : Contrafunctor G) →
    contrafunctorContrafunctorCompose G contrafunctorG F contrafilterableF.{cmap} /\ { deflate = λ(a : Type) → contrafunctorG.cmap (F a) (F (Optional a)) (contrafilterableF.inflate a) }
```

In addition to these general combinators that work with any filterable functors or contrafunctors, there are two special combinators that compose `Optional` with other functors:

1) If `F` is _any functor_ then `Compose F Optional` is a filterable functor.
For clarity, we may define that new functor as `G a = F (Optional a)`.
The functor `G` is known as the "free filterable functor on `F`".
Let us implement a `Filterable` evidence for `G`:
```dhall
let Optional/concat = https://prelude.dhall-lang.org/Optional/concat
let freeFilterable
  : ∀(F : Type → Type) → Functor F → Filterable (Compose F Optional)
  = λ(F : Type → Type) → λ(functorF : Functor F) →
    functorFunctorCompose F functorF Optional functorOptional /\ { deflate = λ(a : Type) →
-- Need a function of type F (Optional (Optional a)) → F (Optional a).
      functorF.fmap (Optional (Optional a)) (Optional a) (Optional/concat a) }
```

2) If `F` is any polynomial functor (not necessarily filterable) then `Compose Optional F` is a filterable functor.
We may define the new functor as `G a = Optional (F a)`.
To implement a `Filterable` evidence for `G`, we need a special `swap` function with type signature `F (Optional a) → Optional (F a)` and obeying suitable laws.
Such a function can be always implemented for any polynomial functor `F`.
Details and proofs are in ["The Science of Functional Programming"](https://leanpub.com/sofp), Chapter 13.

```dhall
let swapFilterable
  : ∀(F : Type → Type) → Functor F → (∀(a : Type) → F (Optional a) → Optional (F a)) → Filterable (Compose Optional F)
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(swap : ∀(a : Type) → F (Optional a) → Optional (F a)) →
     functorFunctorCompose Optional functorOptional F functorF /\  { deflate = λ(a : Type) →
-- Need a function of type Optional (F (Optional a)) → Optional (F a).
      λ(ofoa : Optional (F (Optional a))) → Optional/concat (F a) (Optional/map (F (Optional a)) (Optional (F a)) (swap a) ofoa) }
```

### Filterable (contra)functor (co-)products

When a new type constructor is created via a product or a co-product, the filterable property is preserved. There are four cases:

1) The product of two filterable functors is again a filterable functor:
```dhall
let filterableFunctorProduct
  : ∀(F : Type → Type) → Filterable F → ∀(G : Type → Type) → Filterable G → Filterable (Product F G)
  = λ(F : Type → Type) → λ(filterableF : Filterable F) → λ(G : Type → Type) → λ(filterableG : Filterable G) →
    functorProduct F filterableF.{fmap} G filterableG.{fmap} /\ { deflate = λ(a : Type) → fProduct (F (Optional a)) (F a) (filterableF.deflate a) (G (Optional a)) (G a) (filterableG.deflate a) }
```

2) The product of two filterable contrafunctors is again a filterable contrafunctor:
```dhall
let filterableContrafunctorProduct
  : ∀(F : Type → Type) → ContraFilterable F → ∀(G : Type → Type) → ContraFilterable G → ContraFilterable (Product F G)
  = λ(F : Type → Type) → λ(contrafilterableF : ContraFilterable F) → λ(G : Type → Type) → λ(contrafilterableG : ContraFilterable G) →
    contrafunctorProduct F contrafilterableF.{cmap} G contrafilterableG.{cmap} /\ { inflate = λ(a : Type) → fProduct (F a) (F (Optional a)) (contrafilterableF.inflate a) (G a) (G (Optional a)) (contrafilterableG.inflate a) }
```

3) The co-product of two filterable functors is again a filterable functor:
```dhall
let filterableFunctorCoProduct
  : ∀(F : Type → Type) → Filterable F → ∀(G : Type → Type) → Filterable G → Filterable (CoProduct F G)
  = λ(F : Type → Type) → λ(filterableF : Filterable F) → λ(G : Type → Type) → λ(filterableG : Filterable G) →
    functorCoProduct F filterableF.{fmap} G filterableG.{fmap} /\ { deflate = λ(a : Type) → fCoProduct (F (Optional a)) (F a) (filterableF.deflate a) (G (Optional a)) (G a) (filterableG.deflate a) }
```

4) The co-product of two filterable contrafunctors is a filterable contrafunctor:
```dhall
let filterableContrafunctorCoProduct
  : ∀(F : Type → Type) → ContraFilterable F → ∀(G : Type → Type) → ContraFilterable G → ContraFilterable (CoProduct F G)
  = λ(F : Type → Type) → λ(contrafilterableF : ContraFilterable F) → λ(G : Type → Type) → λ(contrafilterableG : ContraFilterable G) →
    contrafunctorCoProduct F contrafilterableF.{cmap} G contrafilterableG.{cmap} /\ { inflate = λ(a : Type) → fCoProduct (F a) (F (Optional a)) (contrafilterableF.inflate a) (G a) (G (Optional a)) (contrafilterableG.inflate a) }
```

### Function types with filterable (contra)functors

When a new type constructor has a function type, the filterable property is preserved.
To define the new functor or contrafunctor, we use the `Arrow` combinator shown in the previous chapter.

Suppose `F` is a filterable functor and `G` is a filterable contrafunctor.
Then we can consider two new type constructors: `Arrow F G` and `Arrow G F`.
It turns out that `Arrow F G` is a filterable contrafunctor, while `Arrow G F` is a filterable functor.
The typeclass evidence values can be constructed automatically. For that, we reuse the functor combinators defined in the previous chapter:
```dhall
let filterableFunctorContrafunctorArrow
  : ∀(F : Type → Type) → Filterable F → ∀(G : Type → Type) → ContraFilterable G → ContraFilterable (Arrow F G)
  = λ(F : Type → Type) → λ(filterableF : Filterable F) → λ(G : Type → Type) → λ(contrafilterableG : ContraFilterable G) →
    functorContrafunctorArrow F filterableF.{fmap} G contrafilterableG.{cmap} /\ { inflate = λ(a : Type) → λ(x : F a → G a) → λ(foa : F (Optional a)) → contrafilterableG.inflate a (x (filterableF.deflate a foa)) }
```

```dhall
let filterableContrafunctorFunctorArrow
  : ∀(F : Type → Type) → ContraFilterable F → ∀(G : Type → Type) → Filterable G → Filterable (Arrow F G)
  = λ(F : Type → Type) → λ(contrafilterableF : ContraFilterable F) → λ(G : Type → Type) → λ(filterableG : Filterable G) →
    contrafunctorFunctorArrow F contrafilterableF.{cmap} G filterableG.{fmap} /\ { deflate = λ(a : Type) → λ(x : F (Optional a) → G (Optional a)) → λ(fa : F a) → filterableG.deflate a (x (contrafilterableF.inflate a fa)) }
```

In addition to the `Arrow` combinator that works with any filterable functors or contrafunctors, there exists a special construction with a function type:
If `F` is any polynomial functor (not necessarily filterable) and `G` is any filterable contrafunctor then `Arrow F (Compose Optional G)` is a filterable contrafunctor.
This construction requires a special `swap` function with type signature `F (Optional a) → Optional (F a)` and obeying suitable laws.
Such a function can be always implemented for any polynomial functor `F`.
Details and proofs are in ["The Science of Functional Programming"](https://leanpub.com/sofp), Chapter 13.

```dhall
let filterableContrafunctorSwap
  : ∀(F : Type → Type) → Functor F → (∀(a : Type) → F (Optional a) → Optional (F a)) → ∀(G : Type → Type) → ContraFilterable G → ContraFilterable (Arrow F (Compose Optional G))
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(swap : ∀(a : Type) → F (Optional a) → Optional (F a)) → λ(G : Type → Type) → λ(contrafilterableG : ContraFilterable G) →
    functorContrafunctorArrow F functorF (Compose Optional G) (functorContrafunctorCompose Optional functorOptional G contrafilterableG.{cmap}) /\ { inflate = λ(a : Type) →
-- We need a function of type (F a → Optional (G a)) → F (Optional a) → Optional (G (Optional a)).
     λ(x : F a → Optional (G a)) → λ(foa : F (Optional a)) → Optional/map (G a) (G (Optional a)) (contrafilterableG.inflate a) (Optional/concat (G a) (Optional/map (F a) (Optional (G a)) x (swap a foa))) }
```

### Universal and existential type quantifiers

If `F` is a type constructor with two type parameters, we may impose a universal or an existential quantifier on one of the type parameters and obtain a new type constructor with just one type parameter.
This gives us new type constructors defined as: $$G ~ x = \forall y. ~ F ~ x ~ y$$  $$H ~ x = \exists y. ~ F ~ x ~ y$$

Imposing a quantifier on `y` will preserve the filterable properties of `F x y` with respect to `x`.
It does not matter whether `F x y` is covariant, contravariant, or neither with respect to `y`.

So, we have four cases:

1) If $F ~ x ~ y$ is a filterable functor with respect to $x$ then $G$ is a filterable functor.
```dhall
let filterableForall1
  : ∀(F : Type → Type → Type) → (∀(b : Type) → Filterable (λ(a : Type) → F a b)) → Filterable (λ(a : Type) → ∀(b : Type) → F a b)
  = λ(F : Type → Type → Type) → λ(filterableF1 : ∀(b : Type) → Filterable (λ(a : Type) → F a b)) →
    let G : Type → Type = λ(a : Type) → Forall (F a)
    in (functorForall1 F (λ(b : Type) → (filterableF1 b).{fmap})) /\ { deflate = λ(a : Type) →
-- Need a function of type G (Optional a) → G a. Use mapForall for that.
-- Define P and Q such that Forall P = G (Optional a) and Forall Q = G a.
      let P = F (Optional a)
      let Q = F a
      let mapPQ : ∀(x : Type) → P x → Q x = λ(x : Type) → (filterableF1 x).deflate a
      in mapForall P Q mapPQ
     }
```

2) If $F ~ x ~ y$ is a filterable contrafunctor with respect to $x$ then $G$ is a filterable contrafunctor.
```dhall
let contrafilterableForall1
  : ∀(F : Type → Type → Type) → (∀(b : Type) → ContraFilterable (λ(a : Type) → F a b)) → ContraFilterable (λ(a : Type) → ∀(b : Type) → F a b)
  = λ(F : Type → Type → Type) → λ(contrafilterableF1 : ∀(b : Type) → ContraFilterable (λ(a : Type) → F a b)) →
    let G : Type → Type = λ(a : Type) → Forall (F a)
    in (contrafunctorForall1 F (λ(b : Type) → (contrafilterableF1 b).{cmap})) /\ { inflate = λ(a : Type) →
-- Need a function of type G a → G (Optional a). Use mapForall for that.
      let P = F (Optional a)
      let Q = F a
      let mapQP : ∀(x : Type) → Q x → P x = λ(x : Type) → (contrafilterableF1 x).inflate a
      in mapForall Q P mapQP
     }
```

3) If $F ~ x ~ y$ is a filterable functor with respect to $x$ then $H$ is a filterable functor.
```dhall
let filterableExists1
  : ∀(F : Type → Type → Type) → (∀(b : Type) → Filterable (λ(a : Type) → F a b)) → Filterable (λ(a : Type) → Exists (λ(b : Type) → F a b))
  = λ(F : Type → Type → Type) → λ(filterableF1 : ∀(b : Type) → Filterable (λ(a : Type) → F a b)) →
    let H : Type → Type = λ(a : Type) → Exists (F a)
    in (functorExists1 F (λ(b : Type) → (filterableF1 b).{fmap})) /\ { deflate = λ(a : Type) →
-- Need a function of type H (Optional a) → H a. Use mapExists for that.
-- Define P and Q such that Exists P = H (Optional a) and Exists Q = H a.
      let P = F (Optional a)
      let Q = F a
      let mapPQ : ∀(x : Type) → P x → Q x = λ(x : Type) → (filterableF1 x).deflate a
      in mapExists P Q mapPQ
     }
```

4) If $F ~ x ~ y$ is a filterable contrafunctor with respect to $x$ then $H$ is a filterable contrafunctor.
```dhall
let contrafilterableExists1
  : ∀(F : Type → Type → Type) → (∀(b : Type) → ContraFilterable (λ(a : Type) → F a b)) → ContraFilterable (λ(a : Type) → Exists (λ(b : Type) → F a b))
  = λ(F : Type → Type → Type) → λ(contrafilterableF1 : ∀(b : Type) → ContraFilterable (λ(a : Type) → F a b)) →
    let H : Type → Type = λ(a : Type) → Exists (λ(b : Type) → F a b)
    in (contrafunctorExists1 F (λ(b : Type) → (contrafilterableF1 b).{cmap})) /\ { inflate = λ(a : Type) →
-- Need a function of type H a → H (Optional a). Use mapExists for that.
      let P = F (Optional a)
      let Q = F a
      let mapQP : ∀(x : Type) → Q x → P x = λ(x : Type) → (contrafilterableF1 x).inflate a
      in mapExists Q P mapQP
     }
```

### Recursive filterable type constructors

Recursive type constructors are defined via `LFix` or `GFix` from pattern functors, which are type constructors `F` with two type parameters (so that `F a b` is a type).

Imposing a fixpoint on one type parameter will preserve the filterable property with respect to the other type parameter.
Define the type constructors `C` and `D` as `C a = LFix (F a)` and `D a = GFix (F a)`.
Then we need to consider four cases:

1) If `F a b` is covariant and filterable with respect to `a` then so is `C a`.

```dhall
let filterableLFix
  : ∀(F : Type → Type → Type) → (∀(b : Type) → Filterable (λ(a : Type) → F a b)) → Filterable (λ(a : Type) → LFix (F a))
  = λ(F : Type → Type → Type) → λ(filterableF1 : ∀(b : Type) → Filterable (λ(a : Type) → F a b)) →
    functorLFix F (λ(b : Type) → (filterableF1 b).{fmap}) /\ { deflate = λ(a : Type) →
-- Need a function of type C (Optional a) → C a. Use mapLFix for that.
-- Define P and Q such that LFix P = C (Optional a) and LFix Q = C a.
          let P = F (Optional a)
          let Q = F a
          let mapPQ : ∀(x : Type) → P x → Q x = λ(x : Type) → (filterableF1 x).deflate a
          in mapLFix P Q mapPQ
       }
```

2) If `F a b` is covariant and filterable with respect to `a` then so is `D a`.

```dhall
let filterableGFix
  : ∀(F : Type → Type → Type) → (∀(b : Type) → Filterable (λ(a : Type) → F a b)) → Filterable (λ(a : Type) → GFix (F a))
  = λ(F : Type → Type → Type) → λ(filterableF1 : ∀(b : Type) → Filterable (λ(a : Type) → F a b)) →
    functorGFix F (λ(b : Type) → (filterableF1 b).{fmap}) /\ { deflate = λ(a : Type) →
-- Need a function of type D (Optional a) → D a. Use mapGFix for that.
          let P = F (Optional a)
          let Q = F a
          let mapPQ : ∀(x : Type) → P x → Q x = λ(x : Type) → (filterableF1 x).deflate a
          in mapGFix P Q mapPQ
       }
```

3) If `F a b` is contravariant and filterable with respect to `a` then so is `C a`.

```dhall
let contrafilterableLFix
  : ∀(F : Type → Type → Type) → (∀(b : Type) → ContraFilterable (λ(a : Type) → F a b)) → ContraFilterable (λ(a : Type) → LFix (F a))
  = λ(F : Type → Type → Type) → λ(contrafilterableF1 : ∀(b : Type) → ContraFilterable (λ(a : Type) → F a b)) →
    contrafunctorLFix F (λ(b : Type) → (contrafilterableF1 b).{cmap}) /\ { inflate = λ(a : Type) →
-- Need a function of type C a → C (Optional a). Use mapLFix for that.
          let P = F (Optional a)
          let Q = F a
          let mapQP : ∀(x : Type) → Q x → P x = λ(x : Type) → (contrafilterableF1 x).inflate a
          in mapLFix Q P mapQP
       }
```

4) If `F a b` is contravariant and filterable with respect to `a` then so is `D a`.

```dhall
let contrafilterableGFix
  : ∀(F : Type → Type → Type) → (∀(b : Type) → ContraFilterable (λ(a : Type) → F a b)) → ContraFilterable (λ(a : Type) → GFix (F a))
  = λ(F : Type → Type → Type) → λ(contrafilterableF1 : ∀(b : Type) → ContraFilterable (λ(a : Type) → F a b)) →
    contrafunctorGFix F (λ(b : Type) → (contrafilterableF1 b).{cmap}) /\ { inflate = λ(a : Type) →
-- Need a function of type D a → D (Optional a). Use mapGFix for that.
          let P = F (Optional a)
          let Q = F a
          let mapQP : ∀(x : Type) → Q x → P x = λ(x : Type) → (contrafilterableF1 x).inflate a
          in mapGFix Q P mapQP
       }
```

While these four constructions do automatically produce some evidence values for the filterable typeclass, the results might not be what we expect.
To see what kind of filtering logic comes out of those definitions, consider the Church-encoded `CList` functor defined as the least fixpoint `LFix (FList a)`, where `FList a b = Optional (Pair a b)` as we defined earlier.

A `Filterable` evidence for `CList` requires a value of type `∀(b : Type) → Filterable (λ(a : Type) → FList a b)`; that is, a `Filterable` evidence for `FList a b` with respect to `a` with fixed `b`.
This is equivalent to a `deflate` method of type `Optional (Pair (Optional a) b) → Optional (Pair a b)`.
We can certainly implement `Filterable` for `FList` using that `deflate` method and the general combinator `filterableLFix` as shown above.
But, as it turns out, this combinator produces a filtering operation that truncates a list after the first value that does not pass the given predicate.
For example, filtering the list `[ 1, 3, 4, 5 ]` with the predicate `Natural/odd` will result in the list `[ 1, 3 ]` rather than `[ 1, 3, 5 ]` as one might expect.
Nevertheless, this operation (analogous to `takeWhile` in Haskell and Scala) is a law-abiding filtering operation.

We will now verify that this logic is indeed what `filterableLFix` produces.
Then we will find a different combinator that does not truncate data structures unnecessarily.

We begin by implementing the function `deflateFList`.
```dhall
let deflateFList
  : ∀(a : Type) → ∀(b : Type) → FList (Optional a) b → FList a b
  = ???
```
How would we write code for that?
The output value must be either `None (Pair a b)` or `Some { _1 = ..., _2 = ... }`.
If the input is `Some { _1 = None a, _2 = y : b }`, the function must return `None` as it cannot compute a pair of values of types `a` and `b` (only a value of type `b` is given).
```dhall
let expandPairOptional : ∀(a : Type) → ∀(b : Type) → Pair (Optional a) b → Optional (Pair a b)
  = λ(a : Type) → λ(b : Type) → λ(p : Pair (Optional a) b) → merge {
    None = None (Pair a b)
  , Some = λ(x : a) → Some { _1 = x, _2 = p._2 }
  } p._1
let Optional/concatMap = https://prelude.dhall-lang.org/Optional/concatMap.dhall
let deflateFList
  : ∀(a : Type) → ∀(b : Type) → FList (Optional a) b → FList a b
  = λ(a : Type) → λ(b : Type) → Optional/concatMap (Pair (Optional a) b) (Pair a b) (expandPairOptional a b)
```
Adding a `Functor` evidence, we may write the `Filterable` typeclass evidence for the type constructor `F a b` with `b` fixed:
```dhall
let filterableFList1
  : ∀(b : Type) → Filterable (λ(a : Type) → FList a b)
  = λ(b : Type) → {
      deflate = λ(a : Type) → deflateFList a b
    , fmap = λ(x : Type) → λ(y : Type) → λ(f : x → y) → Optional/map (Pair x b) (Pair y b) (λ(xb : Pair x b) → xb // { _1 = f xb._1 })
    }
```
Now we can implement a `Filterable` evidence for `CList` using `filterableLFix`:
```dhall
let filterableCList: Filterable CList = filterableLFix FList filterableFList1
```

Then we apply the generic `filter` function with the predicate `Natural/odd` to the list `exampleCList1345` and obtain the result corresponding to the list `[ 1, 3 ]`.
```dhall
let result : CList Natural = filter CList filterableCList Natural Natural/odd exampleCList1345
let _ = assert : CList/show Natural { show = Natural/show } result ≡ "[ 1, 3, ]"
```
So, this filtering operation indeed truncates the data after the first item that fails the predicate.

To figure out how to create a different filtering operation, let us reconsider the type signature of `deflateFList`, which is `∀(a : Type) → ∀(b : Type) → FList (Optional a) b → FList a b`.
When this function is used to produce the filtering operation for `CList`, the type parameter `b` is set to `CList a`.
We see that the requirement of having a function with the type signature `FList (Optional a) b → FList a b` (for _all_ `b`) is actually too strong; we only need that function with `b = CList a`.

The filtering shown above truncates lists after the first failing item because `deflateFList` must return an empty `Optional` value (that is, `None (Pair a b)`) in case the argument of type `Optional a` equals `None a`.
An empty `Optional` value corresponds to an empty list in this recursive type.
Instead, we need to continue filtering with the tail of the list.
The tail of the list is described by the value of type `b` in `FList a b` (and we will be setting `b = CList a`).
So, we would like the function `deflateFList` to return the tail of the list (a value of type `CList a`) when an item fails the predicate.

The type `CList a` is equivalent to `FList a (CList a)` by definition of the least fixpoint.
We conclude that the type signature of `deflateFList` should be modified so that the function could return not only values of type `FList a b` but sometimes also values of type `b`.
To that end, we rewrite the function's type signature as:
```dhall
let deflateFListEither
  : ∀(a : Type) → ∀(b : Type) → FList (Optional a) b → Either (FList a b) b
  = ???
```
The new implementation will return a `Right` part of the `Either` in cases where the old code returned a `None`:
```dhall
let deflateFListEither
: ∀(a : Type) → ∀(b : Type) → FList (Optional a) b → Either (FList a b) b
= λ(a : Type) → λ(b : Type) → λ(flist : FList (Optional a) b) → merge {
    None = (Either (FList a b) b).Left (None (Pair a b))
  , Some = λ(p : Pair (Optional a) b) → merge {
      None = (Either (FList a b) b).Right p._2
    , Some = λ(x : a) → (Either (FList a b) b).Left (Some { _1 = x, _2 = p._2 })
    } p._1
  } flist
```

We now generalize the type signature of `deflateFListEither` from `FList` an arbitrary bifunctor `F`.
For convenience, let us define that type signature separately:
```dhall
let DeflateEitherT = λ(F : Type → Type → Type) → ∀(a : Type) → ∀(b : Type) → F (Optional a) b → Either (F a b) b
```
Then we can implement a new combinator, named `filterableLFixEither`:
```dhall
let filterableLFixEither
  : ∀(F : Type → Type → Type) → Bifunctor F → DeflateEitherT F → Filterable (λ(a : Type) → LFix (F a))
  = λ(F : Type → Type → Type) → λ(bifunctorF : Bifunctor F) → λ(deflateEither : DeflateEitherT F) →
    bifunctorLFix F bifunctorF /\ { deflate = λ(a : Type) →
          let C = λ(a : Type) → LFix (F a)
-- Need a function of type C (Optional a) → C a.
-- Define P such that LFix P = C (Optional a).
          let P = F (Optional a)
          let functorFa : Functor (F a) = { fmap = λ(x : Type) → λ(y : Type) → λ(f : x → y) → bifunctorF.bimap a a (identity a) x y f }
          in λ(c : LFix P) → c (C a) (λ(q : P (C a)) → merge {
            Left = λ(fa : F a (C a)) → fix (F a) functorFa fa
          , Right = λ(ca : C a) → ca
          } (deflateEither a (C a) q))
       }
```

We now define a new `Filterable` instance for `CList` and test that it does not truncate lists too soon:

```dhall
let filterableCListEither : Filterable CList
  = filterableLFixEither FList bifunctorFList deflateFListEither
let result : CList Natural = filter CList filterableCListEither Natural Natural/odd exampleCList1345
let _ = assert : CList/show Natural { show = Natural/show } result ≡ "[ 1, 3, 5, ]"
```

There are often many ways of implementing a `Filterable` typeclass for a given functor.
The combinators `filterableLFix` and `filterableLFixEither` are two possibilities among many.

## Applicative type constructors and their combinators

The familiar `zip` method for lists works by transforming a pair of lists into a list of pairs.
It turns out that the `zip` method, together with its mathematical properties, can be generalized from `List` to a wide range of type constructors, such as polynomial functors, tree-like recursive types, and even non-covariant type constructors.
In the functional programming community, pointed functors with a suitable `zip` method are known as **applicative functors**.

We defined the `Applicative` typeclass in chapter "Typeclasses":

```dhall
let Applicative = λ(F : Type → Type) →
  { unit : F {}
  , zip : ∀(a : Type) → F a → ∀(b : Type) → F b → F (Pair a b)
  }
```
This typeclass does not assume that `F` is a covariant or contravariant functor, and may be used together with `Functor` or `Contrafunctor` typeclass as required.

If `F` is a functor, we may define other often used methods of applicative functors, such as `pure`, `ap`, and `map2`.
Those methods can be implemented generically through `unit` and `zip`:

```dhall
let pureForApplicativeFunctor
  : ∀(F : Type → Type) → Functor F → Applicative F → ∀(a : Type) → a → F a
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(applicativeF : Applicative F) → λ(a : Type) → λ(x : a) →
      functorF.fmap {} a (λ(_ : {}) → x) applicativeF.unit
```

```dhall
let apForApplicativeFunctor
  : ∀(F : Type → Type) → Functor F → Applicative F → ∀(a : Type) → ∀(b : Type) → F (a → b) → F a → F b
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(applicativeF : Applicative F) → λ(a : Type) → λ(b : Type) → λ(fab : F (a → b)) → λ(fa : F a) →
      let pairs : F (Pair (a → b) a) = applicativeF.zip (a → b) fab a fa
      in functorF.fmap (Pair (a → b) a) b (λ(p : Pair (a → b) a) → p._1 p._2) pairs
```

```dhall
let map2ForApplicativeFunctor
  : ∀(F : Type → Type) → Functor F → Applicative F → ∀(a : Type) → ∀(b : Type) → ∀(c : Type) → (a → b → c) → F a → F b → F c
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(applicativeF : Applicative F) → λ(a : Type) → λ(b : Type) → λ(c : Type) → λ(abc : a → b → c) → λ(fa : F a) → λ(fb : F b) →
      let pairs : F (Pair a b) = applicativeF.zip a fa b fb
      in functorF.fmap (Pair a b) c (λ(p : Pair a b) → abc p._1 p._2) pairs
```

As an example, let us implement an `Applicative` typeclass evidence for the polynomial type constructor `C` defined by:
```dhall
let C = λ(a : Type) → { id : Natural, x : a, y : a }
let applicativeC : Applicative C = {
  unit = { id = 0, x = {=}, y = {=} }
, zip = λ(a : Type) → λ(fa : C a) → λ(b : Type) → λ(fb : C b) →
  { id = fa.id + fb.id
  , x = { _1 = fa.x, _2 = fb.x }
  , y = { _1 = fa.y, _2 = fb.y }
  }
}
```
We will see later in this chapter that all polynomial functors (with monoidal constant types) are applicative.

If `F` is a contrafunctor, we cannot define `ap` or `map2`, but we can still define `pure` as well as a function called `cpure` with a simpler type:
```dhall
let cpureForApplicativeContrafunctor
  : ∀(F : Type → Type) → Contrafunctor F → Applicative F → ∀(a : Type) → F a
  = λ(F : Type → Type) → λ(contrafunctorF : Contrafunctor F) → λ(applicativeF : Applicative F) → λ(a : Type) →
      contrafunctorF.cmap a {} (λ(_ : a) → {=}) applicativeF.unit
let pureForApplicativeContrafunctor
  : ∀(F : Type → Type) → Contrafunctor F → Applicative F → ∀(a : Type) → a → F a
  = λ(F : Type → Type) → λ(contrafunctorF : Contrafunctor F) → λ(applicativeF : Applicative F) → λ(a : Type) → λ(_ : a) →
      contrafunctorF.cmap a {} (λ(_ : a) → {=}) applicativeF.unit
```

An example of an applicative type constructor that is neither covariant nor contravariant is `Monoid`.
(The type constructor `Monoid` describes typeclass evidence values for monoidal types.)
Chapter "Typeclasses" has shown that the `Monoid` type constructor admits an `Applicative` typeclass evidence.

Let us now find out what combinators exist for creating new applicative type constructors out of previously given ones.

### Constant (contra)functors and the identity functor

A constant type constructor (`Const T`) is at once a functor and a contrafunctor.
It is applicative as long as `T` is a monoidal type.

```dhall
let applicativeConst
  : ∀(T : Type) → Monoid T → Applicative (Const T)
  = λ(T : Type) → λ(monoidT : Monoid T) →
    { unit = monoidT.empty
    , zip = λ(a : Type) → λ(x : Const T a) → λ(b : Type) → λ(y : Const T b) →
      monoidT.append x y
    }
```

The identity functor (`Id`) is applicative:

```dhall
let applicativeId : Applicative Id
  = { unit = {=}
    , zip = λ(a : Type) → λ(x : Id a) → λ(b : Type) → λ(y : Id b) →
      { _1 = x, _2 = y }
    }
```

### Products and co-products

If `P` and `Q` are applicative then so is their product (`Product P Q`).

```dhall
let applicativeProduct
  : ∀(P : Type → Type) → Applicative P → ∀(Q : Type → Type) → Applicative Q → Applicative (Product P Q)
  = λ(P : Type → Type) → λ(applicativeP : Applicative P) → λ(Q : Type → Type) → λ(applicativeQ : Applicative Q) →
    let R = λ(a : Type) → { _1 : P a, _2 : Q a } -- Same as Product P Q a.
    in { unit = { _1 = applicativeP.unit, _2 = applicativeQ.unit }
       , zip = λ(a : Type) → λ(x : R a) → λ(b : Type) → λ(y : R b) →
         { _1 = applicativeP.zip a x._1 b y._1, _2 = applicativeQ.zip a x._2 b y._2 }
       }
```
This works equally well for any type constructors (not necessarily covariant).

Co-products of applicative functors are _not_ always applicative.

There are three cases when co-products are applicative:

1) A co-product with a constant applicative functor: `CoProduct P (Const T)` where `P` is applicative and `T` is a monoidal type.
The result is an applicative type constructor `R`, of the same variance as `P`, such that `R a = Either (P a) T`.

```dhall
let applicativeCoProductConst
  : ∀(P : Type → Type) → Applicative P → ∀(T : Type) → Monoid T → Applicative (CoProduct P (Const T))
  = λ(P : Type → Type) → λ(applicativeP : Applicative P) → λ(T : Type) → λ(monoidT : Monoid T) →
    let R = λ(a : Type) → Either (P a) T -- Same as CoProduct P (Const T) a.
    in { unit = (R {}).Left applicativeP.unit
       , zip = λ(a : Type) → λ(x : R a) → λ(b : Type) → λ(y : R b) →
           merge {
             Left = λ(pa : P a) → merge {
                 Left = λ(pb : P b) → (R (Pair a b)).Left (applicativeP.zip a pa b pb)
               , Right = λ(t : T) → (R (Pair a b)).Right t
             } y
           , Right = λ(tx : T) → merge {
               Left = λ(pb : P b) → (R (Pair a b)).Right tx
             , Right = λ(ty : T) → (R (Pair a b)).Right (monoidT.append tx ty)
             } y
           } x
       }
```

2) A co-product of the form `CoProduct P (Product Id Q)`, where `P` and `Q` are applicative and `P` is a functor.
The result is an applicative functor `R` such that `R a = Either (P a) { _1 : a, _2 : Q a }`.

```dhall
let applicativeCoProductWithId
  : ∀(P : Type → Type) → Applicative P → Functor P → ∀(Q : Type → Type) → Applicative Q → Applicative (CoProduct P (Product Id Q))
  = λ(P : Type → Type) → λ(applicativeP : Applicative P) → λ(functorP : Functor P) → λ(Q : Type → Type) → λ(applicativeQ : Applicative Q) →
      let R = λ(a : Type) → Either (P a) { _1 : a, _2 : Q a } -- Same as CoProduct P (Product Id Q) a.
      let pure_P = pureForApplicativeFunctor P functorP applicativeP
      in { unit = (R {}).Right { _1 = {=}, _2 = applicativeQ.unit }
         , zip = λ(a : Type) → λ(x : R a) → λ(b : Type) → λ(y : R b) →
             merge {
               Left = λ(pa : P a) → merge {
                   Left = λ(pb : P b) → (R (Pair a b)).Left (applicativeP.zip a pa b pb)
                 , Right = λ(pair : { _1 : b, _2 : Q b }) → (R (Pair a b)).Left (applicativeP.zip a pa b (pure_P b pair._1))
               } y
             , Right = λ(pair_x : { _1 : a, _2 : Q a }) → merge {
                 Left = λ(pb : P b) → (R (Pair a b)).Left (applicativeP.zip a (pure_P a pair_x._1) b pb)
               , Right = λ(pair_y : { _1 : b, _2 : Q b }) → (R (Pair a b)).Right { _1 = { _1 = pair_x._1, _2 = pair_y._1 }, _2 = applicativeQ.zip a pair_x._2 b pair_y._2 }
               } y
             } x
         }
```

3) A co-product of applicative _contrafunctors_ `P` and `Q`.
The result is an applicative contrafunctor `R` such that `R a = Either (P a) (Q a)`.

```dhall
let applicativeContrafunctorCoProduct
  : ∀(P : Type → Type) → Applicative P → ∀(Q : Type → Type) → Applicative Q → Contrafunctor Q → Applicative (CoProduct P Q)
  = λ(P : Type → Type) → λ(applicativeP : Applicative P) → λ(Q : Type → Type) → λ(applicativeQ : Applicative Q) → λ(contrafunctorQ : Contrafunctor Q) →
      let R = λ(a : Type) → Either (P a) (Q a) -- Same as CoProduct P Q a.
      in { unit = (R {}).Left applicativeP.unit
       , zip = λ(a : Type) → λ(x : R a) → λ(b : Type) → λ(y : R b) →
           merge {
             Left = λ(pa : P a) → merge {
                 Left = λ(pb : P b) → (R (Pair a b)).Left (applicativeP.zip a pa b pb)
               , Right = λ(qb : Q b) → (R (Pair a b)).Right (contrafunctorQ.cmap (Pair a b) b (take_2 a b) qb)
             } y
           , Right = λ(qa : Q a) → merge {
               Left = λ(pb : P b) → (R (Pair a b)).Right (contrafunctorQ.cmap (Pair a b) a (take_1 a b) qa)
             , Right = λ(qb : Q b) → (R (Pair a b)).Right (applicativeQ.zip a qa b qb)
             } y
           } x
       }
```


### Functor and contrafunctor composition

Composition of type constructors preserves the applicative property if the _first_ type constructor is a functor.
For example, if `P` and `Q` are both applicative functors then so is `Compose P Q`.
If `P` is an applicative functor and `Q` is an applicative contrafunctor then `Compose P Q` is an applicative contrafunctor.
```
let applicativeFunctorCompose
  : ∀(P : Type → Type) → Applicative P → Functor P → ∀(Q : Type → Type) → Applicative Q → Applicative (Compose P Q)
  = λ(P : Type → Type) → λ(applicativeP : Applicative P) → λ(functorP : Functor P) → λ(Q : Type → Type) → λ(applicativeQ : Applicative Q) →
    let R = λ(a : Type) → P (Q a)
    let pure_P = pureForApplicativeFunctor P functorP applicativeP
    let map2_P = map2ForApplicativeFunctor P functorP applicativeP
    in { unit = pure_P (Q {}) (applicativeQ.unit)
       , zip = λ(a : Type) → λ(x : R a) → λ(b : Type) → λ(y : R b) →
           map2_P (Q a) (Q b) (Q (Pair a b)) (λ(qa : Q a) → λ(qb : Q b) → applicativeQ.zip a qa b qb) x y
       }
```

In other cases (such as the composition of two applicative contrafunctors), the result is not necessarily applicative.
A counterexample is `P a = a → p` and `Q a = a → q`, where `p` and `q` are fixed monoidal types that are assumed to be different and unrelated.
Both `P` and `Q` are applicative contrafunctors, but their composition `R = Compose P Q`, which can be written out as `R a = (a → q) → p`, is a functor that is not applicative [as far as the author knows](https://stackoverflow.com/questions/79148048/).

When `p` and `q` were the same type, `p = q`, the functor `R a = (a → p) → p` _is_ applicative.
It is known as the "continuation monad", and every monad in Dhall is also an applicative functor.

### Reverse applicative functors and contrafunctors

For any applicative (contra)functor `P` with a given `zip` operation, one can redefine the `zip` operation to combine the effects in the reverse order (while keeping the order of values).
The result is another lawful implementation of the applicative property of the (contra)functor.

We can define the "reversing" operation via general combinators that transform a given `Applicative` evidence into the reversed one:
```dhall
let reverseApplicativeFunctor
  : ∀(P : Type → Type) → Applicative P → Functor P → Applicative P
  = λ(P : Type → Type) → λ(applicativeP : Applicative P) → λ(functorP : Functor P) →
    applicativeP // { zip = λ(a : Type) → λ(x : P a) → λ(b : Type) → λ(y : P b) →
      functorP.fmap (Pair b a) (Pair a b) (swap b a) (applicativeP.zip b y a x) }
```

```dhall
let reverseApplicativeContrafunctor
  : ∀(P : Type → Type) → Applicative P → Contrafunctor P → Applicative P
  = λ(P : Type → Type) → λ(applicativeP : Applicative P) → λ(contrafunctorP : Contrafunctor P) →
    applicativeP // { zip = λ(a : Type) → λ(x : P a) → λ(b : Type) → λ(y : P b) →
      contrafunctorP.cmap (Pair a b) (Pair b a) (swap a b) (applicativeP.zip b y a x) }
```

For some applicative (contra)functors `P`, reversing does not change their `zip` operation.
Such `P` are called _commutative_ applicative.
Examples are the functor `R a = p → a`, where `p` is any fixed type, and the contrafunctor `P a = a → m`, where `m` is a commutative monoid type.

### Monads are automatically applicative

If it is known that a functor `P` is a monad, we can define an `Applicative` evidence for that functor.

```dhall
let monadApplicative
  : ∀(P : Type → Type) → Monad P → Applicative P
  = λ(P : Type → Type) → λ(monadP : Monad P) →
    let functorP : Functor P = monadFunctor P monadP
    in { unit = monadP.pure {} {=}
       , zip = λ(a : Type) → λ(pa : P a) → λ(b : Type) → λ(pb : P b) →
          let aPab : a → P (Pair a b) = λ(x : a) → functorP.fmap b (Pair a b) (λ(y : b) → { _1 = x, _2 = y }) pb 
          in monadP.bind a pa (Pair a b) aPab
       }
```

In this code, we begin with the value `pa : P a` and use `bind` to transform `pa` into a value of type `P (Pair a b)`.
We could instead begin with `pb : P b` and proceed similarly.
So, there are two ways of creating an `Applicative` instance out of a `Monad` instance, and they are reversals of each other (in the sense of the previous subsection).
These two `Applicative` instances will be the same only if the monad instance is commutative.

### Function types

Here, we consider creating a new type constructor `R` via the `Arrow` combinator, `R = Arrow P Q`
(which is equivalent to `R a = P a → Q a`).
Generally `R` will not be applicative.
It is known that `R` is applicative in certain cases:

1) If `P` is an applicative functor and `Q` is an applicative type constructor (of any variance) then `R` is applicative.

```dhall
let arrowFunctorApplicative
  : ∀(P : Type → Type) → Applicative P → Functor P → ∀(Q : Type → Type) → Applicative Q → Applicative (Arrow P Q)
  = λ(P : Type → Type) → λ(applicativeP : Applicative P) → λ(functorP : Functor P) → λ(Q : Type → Type) → λ(applicativeQ : Applicative Q) →
    let R = λ(a : Type) → P a → Q a -- Same as R = Arrow P Q.
    in { unit = λ(_ : P {}) → applicativeQ.unit
       , zip = λ(a : Type) → λ(ra : R a) → λ(b : Type) → λ(rb : R b) →
           λ(pab : P (Pair a b)) →
             let pa : P a = functorP.fmap (Pair a b) a (take_1 a b) pab
             let pb : P b = functorP.fmap (Pair a b) b (take_2 a b) pab
             in applicativeQ.zip a (ra pa) b (rb pb)
       }
```

2) If `P` is _any_ contrafunctor (not necessarily applicative) then `Arrow P Id` is an applicative functor.
The new type constructor `R` has the form `R a = P a → a`.

```dhall
let arrowContrafunctorIdApplicative
  : ∀(P : Type → Type) → Contrafunctor P → Applicative (Arrow P Id)
  = λ(P : Type → Type) → λ(contrafunctorP : Contrafunctor P) →
    let R = λ(a : Type) → P a → a -- Same as R = Arrow P Id.
    in { unit = λ(_ : P {}) → {=}
       , zip = λ(a : Type) → λ(ra : R a) → λ(b : Type) → λ(rb : R b) →
           λ(pab : P (Pair a b)) →
             let g : a → R (Pair a b) = λ(x : a) → λ(pab : P (Pair a b)) → { _1 = x, _2 = rb (contrafunctorP.cmap b (Pair a b) (λ(y : b) → { _1 = x, _2 = y }) pab) }
             let aab : a → Pair a b = λ(x : a) → g x pab
             let pa : P a = contrafunctorP.cmap a (Pair a b) aab pab
             in g (ra pa) pab
       }
```

### Universal type quantifiers


If `F` is a type constructor with two type parameters, we may impose a universal quantifier on one of the type parameters and obtain a new type constructor with just one type parameter: $$G ~ x = \forall y. ~ F ~ x ~ y$$

In Dhall, `G` is defined as `λ(x : Type) → Forall (F x)` using the `Forall` combinator that we have defined earlier.

It does not matter whether `F x y` is covariant, contravariant, or neither with respect to `x` or `y`.
Imposing a quantifier on `y` will preserve the applicative property of `F x y` with respect to `x`.
That property means that, if the type `y` is fixed, there is an `Applicative` typeclass evidence for `F x y` with respect to `x`.
This evidence contains values of types `F {} y` and `F a y → F b y → F (Pair a b) y`. 
From those values, we need to compute values of types `G y` and `G a → G b → G (Pair a b)`.

```dhall
let applicativeForall1
  : ∀(F : Type → Type → Type) → (∀(b : Type) → Applicative (λ(a : Type) → F a b)) → Applicative (λ(a : Type) → Forall (F a))
  = λ(F : Type → Type → Type) → λ(applicativeF1 : ∀(b : Type) → Applicative (λ(a : Type) → F a b)) →
    let G : Type → Type = λ(a : Type) → Forall (F a)
    in { unit = λ(c : Type) → (applicativeF1 c).unit
       , zip = λ(a : Type) → λ(ra : G a) → λ(b : Type) → λ(rb : G b) →
           λ(c : Type) → (applicativeF1 c).zip a (ra c) b (rb c)
       }
```

### Least fixpoint types

Implementing a `zip` method for recursive type constructors turns out to require quite a bit of work.
In this section, we will show how a `zip` method can be written for type constructors defined via `LFix`, such as lists and trees.

Given a pattern bifunctor `F`, we define the functor `C` such that `C a = LFix (F a)`.
```dhall
let F = λ(a : Type) → λ(b : Type) → ???
let C = λ(a : Type) → LFix (F a)
```
The type signature of `zip` for `C` must be:
```dhall
let zip_C : ∀(a : Type) → C a → ∀(b : Type) → C b → C (Pair a b) = ???
```

It turns out that we can implement `zip_C` for the functor `C` if the bifunctor `F` supports two functions that we will call `bizip_F1` and  `bizip_FC`.
Those functions express a certain applicative-like property for `F`.

The function `bizip_F1` must have the type signature:
```dhall
let bizip_F1 : ∀(r : Type) → ∀(a : Type) → F a r → ∀(b : Type) → F b r → F (Pair a b) r = ???
```
This type is similar to the `zip` function except it works only with the first type parameter of `F`, keeping the second type parameter (`r`) fixed.
The function `bizip_F1` can be implemented for any polynomial bifunctor `F`.
For the purposes of defining `zip_C`, we do not need to require any laws for `bizip_F1`.

The function `bizip_FC` must have the type signature:
```dhall
let bizip_FC : ∀(a : Type) → F a (C a) → ∀(b : Type) → F b (C b) → F (Pair a b) (Pair (C a) (C b)) = ???
```
The type signature of `bizip_FC` is of the form `F a p → F b q → F (Pair a b) (Pair p q)` if we set `p = C a` and `q = C b`.
This type signature is similar to a `zip` function that works at the same time with both type parameters of `F`.
However, when all type parameters are unconstrained, we find that functions of type `F a p → F b q → F (Pair a b) (Pair p q)` do not exist for many pattern functors `F`, such as that for non-empty lists (`F a r = Either a (Pair a r)`) and for non-empty binary trees (`F a r = Either a (Pair r r)`).
In contrast, `bizip_FC` can be implemented for all polynomial bifunctors `F`.

In addition to `bizip_F1` and `bizip_FC`, we require a function for computing the recursion depth of a value of type `C a`.
That function (`depth : ∀(a : Type) → C a → Natural`) can be implemented if we have a function `maxF2 : F {} Natural → Natural` that finds the maximum among all `Natural` numbers stored in a given value of type `F {} Natural`.
The function `maxF2` is available for any given polynomial bifunctor `F` and can be implemented via a `Traversable` instance for `F` with respect to its second type parameter.

TODO:implement maxF2

The code of `depth` is then written as shown in section "Size and depth of generic Church-encoded data".

TODO:implement depth here or there

TODO:write the general code for `zip`

For illustration, let us implement `zip` for `F a r = Either a (Pair r r)`.
This `F` describes binary trees with data held in leaves.

We are allowed to imlpement the function `bizip_F1` in any way whatsoever, as it does not need to satisfy any laws.
For instance, we may discard arguments whenever one of the values of type `F a r` is a `Right`.
```dhall
let F = λ(a : Type) → λ(r : Type) → Either a (Pair r r)
let bizip_F1
  : ∀(r : Type) → ∀(a : Type) → F a r → ∀(b : Type) → F b r → F (Pair a b) r
  = λ(r : Type) → λ(a : Type) → λ(far: Either a (Pair r r)) → λ(b : Type) → λ(fbr: Either b (Pair r r)) →
     merge {
       Left = λ(x : a) → merge {
         Left = λ(y : b) → (F (Pair a b) r).Left { _1 = x, _2 = y }
       , Right = λ(p : Pair r r) → (F (Pair a b) r).Right p
       } fbr
     , Right = λ(p : Pair r r) → (F (Pair a b) r).Right p
     } far
```

The function `bizip_FC` must be implemented similarly to a lawful `zip` method; for instance, arguments should never be discarded.
When one argument is a `Left x` and the other is a `Right y` then we use `C`'s `Functor` instance to produce required values of type `Pair (C a) (C b)`.
A `Functor` typeclass evidence for `C` is derived via `bifunctorLFix` from a `Bifunctor` evidence for `F`.
```dhall
let C = λ(a : Type) → LFix (F a)
let bifunctorF : Bifunctor F = { bimap = λ(a : Type) → λ(c : Type) → λ(ac: a → c) → λ(b : Type) → λ(d : Type) → λ(bd: b → d) → λ(fab: F a b) →
  merge {
    Left = λ(x : a) → (F c d).Left (ac x)
  , Right = λ(p : Pair b b) → (F c d).Right { _1 = bd p._1, _2 = bd p._2 }
  } fab
}
let functorC : Functor C = bifunctorLFix F bifunctorF
let bizip_FC
  : ∀(a : Type) → F a (C a) → ∀(b : Type) → F b (C b) → F (Pair a b) (Pair (C a) (C b))
  = λ(a : Type) → λ(faca : F a (C a)) → λ(b : Type) → λ(fbcb: F b (C b)) →
      let ResultT = F (Pair a b) (Pair (C a) (C b))
      let ca2cb : b → C a → C b = λ(y : b) → λ(ca : C a) → functorC.fmap a b (λ(_ : a) → y) ca
      let cb2ca : a → C b → C a = λ(x : a) → λ(cb : C b) → functorC.fmap b a (λ(_ : b) → x) cb
      in merge {
       Left = λ(x : a) → merge {
         Left = λ(y : b) → ResultT.Left { _1 = x, _2 = y }
       , Right = λ(p : Pair (C b) (C b)) → ResultT.Right { _1 = { _1 = cb2ca x p._1, _2 = p._1 }, _2 = { _1 = cb2ca x p._2, _2 = p._2 } }
       } fbcb
     , Right = λ(p : Pair (C a) (C a)) → merge {
         Left = λ(y : b) → ResultT.Right { _1 = { _1 = p._1, _2 = ca2cb y p._1 }, _2 = { _1 = p._2, _2 = ca2cb y p._2 } }
       , Right = λ(q : Pair (C b) (C b)) → ResultT.Right { _1 = { _1 = p._1, _2 = q._1 }, _2 = { _1 = p._2, _2 = q._2 } }
       } fbcb
     } faca
```

TODO:run this on some examples

TODO:Show that `bizip_FC` can be implemented in 2 different ways for `FList`, corresponding to the ordinary zip and to the padding zip.

## Foldable and traversable functors

The standard library function `List/fold` corresponds to the "right fold" known in other functional languages.
This folding operation implements a general **fold-like aggregation** on lists.
It turns out that this operation can be generalized to many other data types.
Type constructors that support a `fold` operation are called "foldable" functors.

The type signature of `List/fold` is:

```dhall
⊢ :type List/fold

∀(a : Type) → List a → ∀(list : Type) → ∀(cons : a → list → list) → ∀(nil : list) → list
```

Compare this with the standard type signature of `foldr` in Haskell:

```haskell
foldr :: Foldable t => (a -> b -> b) -> b -> t a -> b
```
The type parameters `a`, `b` do not need to be introduced explicitly in Haskell.
In Dhall, we need to write `∀(a : Type) → ...` and `∀(b : Type) → ...` at the appropriate places.
The Dhall and Haskell type signatures are equivalent if we set `t = List`, `b = list`, and move the curried argument `t a` to the front of the type signature.

This comparison shows us how to generalize the type signature of `List/fold` to other functors `F`:

```dhall
let FoldT = ∀(F : Type → Type) → ∀(a : Type) → F a → ∀(b : Type) → (a → b → b) → b → b
```
So, we could have defined the `Foldable` typeclass by the requirement that a function of type `FoldT F` exists.

It turns out to be equivalent (but simpler) if we require a method called `toList`, with type signature `F a → List a`.
This method should extract all data stored in a functor and put that data into a list (in some fixed order).
Once the data is extracted, we can apply the standard `List/fold` method to that list.

So, let us define the typeclass `Foldable` through the `toList` method:

```dhall
let Foldable = ∀(F : Type → Type) → ∀(a : Type) → F a → List a
```

Only functors are foldable in a non-trivial way.
To be more precise, we might add a `Functor F` typeclass constraint to the definition of `Foldable`.

TODO: formulate Foldable via toList, and explain the generalization to Traversable 

## Monads and their combinators

## Monad transformers

## Free typeclass instances

Certain typeclasses support "free instances", which means a type construction that automatically creates a typeclass instance out of another type that does not necessarily belong to that typeclass.

For example, a "free monoid on `T`" is the type `List T`.
The type `List T` is always a monoid, even if `T` is not a monoid.
So, the "free monoid on `T`" is a construction that creates a monoidal type out of any given type `T`.
It works by wrapping the type `T` by the `List` functor.

Other "free typeclass" constructions work similarly: they take a given type and wrap it in a suitable type constructor such that the result always belongs to the required typeclass.

To qualify as a free typeclass, the wrapping must satisfy certain laws that we will not discuss here.
See ["The Science of Functional Programming"](https://leanpub.com/sofp), Chapter 13 for full details.

As a counterexample, consider the "`Optional` monoid" construction (see the chapter "Combinators for monoids").
This construction takes an arbitrary type `T` and produces the type `Optional T`, which is always a monoid.
So, `Optional T` can be also described as a "wrapping" that is guaranteed to produce a monoid out of any type `T`.
But `Optional T` is not the free monoid on `T` because it does not satisfy some of the required laws.

Another example of a free typeclass is the "free monad on a functor `F`", which wraps any given functor `F` into a suitable type constructor and creates a new functor that is always a monad.

This chapter will show how to implement free instances for many of the frequently used typeclasses.

Keep in mind that not all typeclasses can have free instances.
Examples of typeclasses that cannot have free instances are `Show`, `Comonad`, and `Traversable`.

### Free semigroup and free monoid

### Free monad

### Free functor

### Free contrafunctor

### Free pointed functor

### Free filterable

### Free applicative

## Dhall as a scripting DSL

# Appendixes

## Appendix: Deriving a functional language from first principles

One can logically derive a functional programming language similar to Dhall by following certain principles of language design motivated by mathematical experience.

#### Some principles of functional programming

- A program is a (large) expression built up by combining smaller expressions.
- Any changeable part of a program can be replaced by another expression, giving us another valid program.
- In particular, any expression can be replaced by a named constant or by a parameter of a function.
- Every expression has a certain type. Expressions with mismatched types are rejected.

Let us see where these principles lead.

### Step 1: Literal values, operations, variables

#### Literal numbers
We expect the language to handle natural numbers (`0`, `1`, ...) and operations with them (`+` and `*`).

#### Operations on numbers

We expect this to get evaluated to `610`:

```haskell
10 + 20 * 30
```

- We expect this to be a complete Dhall program, because programs are expressions.

- We expect `10` and `20 * 30` also to be expressions.

#### Literal lists

Dhall writes lists like this:

```haskell
[1, 2, 3]
```

The operation `#` concatenates lists. For example, `[1, 2, 3] # [10, 20]` evaluates to the list `[1, 2, 3, 10, 20]`.

#### Variable assignments

- Any part of a program should replaceable by a named constant.

We need some syntax for that.
The syntax must be able to specify that, for example, "in the program that follows, the name `x` will stand for the expression `10 + 20 * 30`".

Dhall has the following syntax:

```haskell
let x = 10 + 20 * 30  in  <rest_of_program>
```

- This is a Dhall program (as long as `rest_of_program` is also a Dhall program).
- This is an expression (as long as `rest_of_program` is also a valid expression).

A simple example:

```haskell
let x = 10 + 20 * 30   in   x * x * x
```

This program evaluates to `226981000`:

```haskell
⊢ let x = 10 + 20 * 30   in   x * x * x

226981000
```

We usually say that we have defined a "variable" `x`, although `x` is actually a constant and can never change within the program that follows.

#### Alternative syntax

The syntax `let a = b in c` is certainly not the only one possible. Another syntax could be just `a = b; c` say.

That sort of syntax is similar to what is used in Scala. However, Scala requires a `val` keyword and braces as delimiters to make the syntax unambiguous:

```scala
{
  val x = 10 + 20 * 30
  x * x * x
}
```

Haskell supports another syntax where variables are defined after the expressions:

```haskell
x * x * x  where x = 10 + 20 * 30
```

It is not very important what syntax we use. It is important that the entire code is a single expression that is evaluated to a result value (or a result expression). The syntax "`let a = b in c`" emphasizes this better than the syntax "`a = b; c`".

#### Multiple variables

We will usually separate variable assignments vertically in Dhall:

```haskell
let x = 10 + 20 * 30
in x * x * x
```

- Because the above is a valid program, we should be able to add more "let" definitions to it:

```haskell
let y = 2
let x = 10 + 20 * 30
in x + y
```

In Dhall, the syntax `let a = b let c = d in e` is the same as the longer `let a = b in let c = d in e`.

It follows that Dhall programs must have this form: `let` ... `let` ... `in` ...

There is nothing else we can write, because the entire program must be a single expression.

#### Nested variable definitions

- Any part of an expression can be replaced by another expression.

For example, we should be able to replace `20` in the expression `10 + 20 * 30` by an expression that defines and uses a new variable.

Suppose we want to write `let p = 10 in p + p` instead of `20`. We should get the same results. But how to write that kind of programs? We need a syntax for that.
We cannot just write `10 + let p = 10 in p + p * 30`: we need to separate the end of the sub-expression from `* 30`.

In Dhall, we use parentheses to separate sub-expressions, like in mathematics. We can write `10 + (20) * 30`, and it's the same as `10 + 20 * 30`.

So, we write:

```haskell
10 + (let p = 10 in p + p) * 30
```

In this way, we can write `let` inside expressions at any place.

#### Types

It is invalid to add a number to a list with a `+`. Let us write an artificial example where we use types incorrectly:

```haskell
let x = 10
let y = [20, 30]
in x + y 

Error: ❰+❱ only works on ❰Natural❱s
```

This is a **type error**: the type of both arguments of `+` must be `Natural`, but the value `[20, 30]` does not have type `Natural`.

- Every value must have a type.

We can use the Dhall command `:type` to figure out the type of any value. Then we find that `[20, 30]` has type `List Natural`:

```dhall
⊢ :type 10

Natural

⊢ :type [20, 30]

List Natural
```

All types must match when we apply functions to arguments. The expression `10 + [ 20, 30 ]` is invalid, because both sides of `+` must have type `Natural`.

To make sure our values have correct types and to detect errors earlier, we may add type annotations to Dhall programs. A natural place to add a type annotation is with the syntax `let x : A = b`:

```haskell
let x : Natural = 10
let y : Natural = [20, 30]
in x + y 

Error: Expression doesn't match annotation
```

Now we got the error about `y` at the point where we annotated its type.

In this artificial example, it might be obvious that `y = [20, 30]` does not have type `Natural`.
But when `y` is computed in a different part of the program, we might forget about its type more easily. It is helpful to get the type errors early.

The Dhall syntax for type annotations is `x : <type>`. This is also the syntax convention in several other languages. (Haskell uses `::` instead of `:`.)

We may write type annotations after any expression:

```haskell
(10 + (20 : Natural) * 30) : Natural
```

Some programming languages do not use a special separator for type annotations: for example, C++ and Java write `int x` to denote that `x` has integer type. Because of this syntax, C++ and Java cannot support type annotations within expressions.

Type annotations are not mandated by the principles of functional programming; in many cases, programs can be written without any type annotations.
For example, the compiler of OCaml has a powerful algorithm that can figure out all types even without a single type annotation.

However, when programming entirely without type annotations, one finds that error messages often become harder to understand.
When code uses wrong types by mistake, the compiler assumes that the code is correct and infers the corresponding types, which are not what the programmer expected at that place.
Then the compiler proceeds with type-checking the code and reports an error that types do not match somewhere else in the code, which is harder to understand.

Such errors would be reported at the right place if the code had type annotations.
In practice, the error is more often in the code, not in the type annotations.

### Step 2: Functions

#### Built-in functions

At this point we are able to compute simple arithmetic expressions.
It is certainly useful to add more built-in functions to the language.
For example, Dhall has a built-in function `Natural/subtract`:

```haskell
Natural/subtract 5 10
```

In the Dhall syntax, functions are applied to arguments without parentheses.
If `f` is a function, we can just write `f 123` instead of `f(123)`.

- Because expressions can be used anywhere, `(123)` and `123` is always the same.

So, `f(123)` and `f 123` is actually the same thing in Dhall.

This kind of syntax is not often used in mathematics; usually, mathematical functions are written with parentheses, such as `f(x)`.

Some programming languages allow functions that return other functions; then the syntax becomes something like `f(x)(y)(z)`. In this expression, `f` is a function such that `f(x)` is again a function, which can be applied to `y` and then gives another function that can be applied to `z`.

In Dhall, parentheses are written only when needed to separate sub-expressions. One can write `f(x)(y)(z)` in Dhall, but it will be easier to write `f x y z`.

In Dhall, the function `Natural/subtract x y` takes one argument `x` and returns another function, which takes another argument `y` and then returns `y - x` if both `x` and `y` are natural numbers.

#### User-defined functions

- For any sub-expression, we should be able to write a function that substitutes that sub-expression.

Applying this principle to the example expression `10 + 20 * 30`, we may say that we should be able to write a function that substitutes the value `20` in this function by some other value.

The language should allow us to specify that we have a function that takes an argument, say, `n` (which must be a natural value) and puts that `n` instead of  `20` in the expression `10 + 20 * 30`.

The Dhall syntax for that function looks like this:

```haskell
λ(n : Natural) → 10 + n * 30
```

We may read that as "here's a function that takes a value of type Natural substitutes that value instead of `n` into the expression `10 + n * 30`".

This function is a value; this text is a Dhall program that evaluates to that function. There is nothing more to simplify in that expression:

```dhall
⊢ λ(n : Natural) → 10 + n * 30

λ(n : Natural) → 10 + n * 30
```

Other programming languages use syntax such as `function(n) { return 10 + n * 30; }` (JavaScript) or `{ (n : Int) => 10 + n * 30 }` (Scala) or something else.
The syntax just needs to put separators between the name of the function's symbolic parameter, the type of that parameter, and the body of the function.

- Everything must be a value.

So, functions are also values and may be assigned to variables.

```haskell
let f = λ(n : Natural) → 10 + n * 30
in f 2
```

This program defines `f` as a function and then applies the function `f` to the value `2`. The resulting program is evaluated to `70`.

- We should be able to write a function that substitutes any part of any expression.

For example, in the program shown above, we should be able to substitute some other number instead of `10`. How can we do that?

We should be able to do that in the same way: We just begin a new function having a new symbolic parameter. Then we put that parameter into the expression at the right place:

```haskell
λ(p : Natural) →
  let f = λ(n : Natural) → p + n * 30
  in f 2   -- Line indentations are _not_ significant in Dhall.
```
This is a function that can be applied to `10` and then evaluates to`70`.

How can we write code that applies such a function to an argument `10`? There are two ways now:

- Define a named constant for this function.
- Put that function into parentheses and apply directly.

First program:

```haskell
let q = λ(p : Natural) →
  let f = λ(n : Natural) → p + n * 30
  in f 2
in q 10
```

Second program:

```haskell
(
λ(p : Natural) →
  let f = λ(n : Natural) → p + n * 30
  in f 2
) 10
```

Both programs evaluate to `70`.

#### Equivalence of functions and "let" expressions

We can see that the language has two ways of doing the same thing.
Indeed, "let" expressions can be rewritten via functions:

`let x : Natural = a in y`

is the same as:

`(λ(x : Natural) → y) a`

If a language has functions but does not have "let" expressions, one could just write programs using functions.
It looks more visual to write with "let" expressions but it's equivalent.

(Below we will see one exception where it is _not_ equivalent in Dhall to replace a "let" expression with a function, but it is a rare corner case involving type aliases.)

#### Curried functions

- A function should be able to substitute values in any expression.

So, a function could substitute values in another function.
This means we have a function that _returns another function_.

Begin by writing just a simple function:

```haskell
λ(p : Natural) → p * 2 + 123
```
We would like to replace `123` in this function by an arbitrary value `n`.
So, we write a function whose parameter is `n` and whose return value is the above function with `n` instead of `123`.

```haskell
λ(n : Natural) → 
    λ(p : Natural) → p * 2 + n
```

How can we use this function? We need to apply it to an argument. Let us denote this function by `f`, and apply to an argument as `f 123`:

```dhall
⊢ let f = λ(n : Natural) → λ(p : Natural) → p * 2 + n  in  f 123

λ(p : Natural) → p * 2 + 123
```
The result of evaluating `f 123` is a function. So, we can apply that function to another argument.
We can write that as `(f 123) 456`, or equivalently as `f 123 456` without parentheses:
```dhall
⊢ let f = λ(n : Natural) → λ(p : Natural) → p * 2 + n  in  f 123 456

1035
```
The syntax `f 123 456` requires some time to get used to.

Functions `f` that can be used in this way are known as **curried functions**.

Curried functions are often used by applying to all possible arguments at once, as `f 123 456` in this example.
Typically one views `f` as a function of _two_ curried arguments `234` and `456`, although technically `f` has only one argument and returns a function with one argument.

We see that the existence of curried functions is not a special feature of the language, but a necessary consequence of the principle that we should be able to refactor any expression into a function that will substitute a given part of that expression.

#### Value capture in functions

A curried function may return a function whose body includes a "captured" parameter. We have seen this in the code above:

```dhall
⊢ let f = λ(n : Natural) → λ(p : Natural) → p * 2 + n  in  f 123

λ(p : Natural) → p * 2 + 123
```
The parameter `n` was set to `123`, which is "captured" in the new function body.

If we are working inside a function where some more parameters are defined and then `f` is called on a parameter then the resulting function will "capture" that parameter:

```haskell
-- Previous code defines some parameters:
λ(a : Natural) → λ(b : Natural) → λ(c : Natural) →
  let w = f b   -- So, w = λ(p : Natural) → p * 2 + b
  -- Further code that uses w...
  let y = λ(b : Natural) → b + w c  -- This `b` is not the same as the `b` above.
  -- Further code...
```
The function `w` "captures" the parameter `b` that was visible in the scope of the definition of `w`.
In the further code that uses `w`, there might be other variables called `b` but `w` will not use them.
The expression `λ(b : Natural) → b + w c` shown above will _not_ be translated into `λ(b : Natural) → b + p * 2 + b` because the `b` captured inside `w` stays the same and cannot be changed.

This behavior follows from our expectation that `w` must be a fixed, immutable value.
Even if `w` is a function whose body refers to the name `b`, the value under that name is fixed and will not change even if some other variable named `b` is defined in a local scope.

#### Higher-order functions

The language should allow us to write a function that replaces a given sub-expression by an argument value.
That sub-expression could be itself a function expression.
In that case, we need to be able to write a function whose argument is itself a function.

Consider the function `f` defined above. It contains `λ(p : Natural) → p * 2 + n` as a sub-expression.
Suppose we need to replace the computation `p * 2` by another function of `p`.

To express our intent more clearly, let us rewrite `f 123`, making this computation explicit:

```haskell
let f = λ(n : Natural) →
  let g = λ(p : Natural) → p * 2
  in λ(p : Natural) → g p + n
in f 123
```

Now we replace `g` by a new parameter; this gives us a new function:

```dhall
let r = λ(g : Natural → Natural) → 
          let f = λ(n : Natural) →
            λ(p : Natural) → g p + n
          in f 123
```

The function we assigned to `r` has a function `g` as its argument and returns another function: `λ(p : Natural) → g p + 123`.
Such functions `r` are called "higher-order" functions.

Generally, functions that return other functions as their result values, and/or take other functions as arguments, are called **higher-order functions**.

So, all curried functions are higher-order functions.

### Step 3: Function types

- Every expression must have a type.

So, functions must have types, and the language must allow us to write those types.

The simplest syntax for function types looks like this: `Natural → Natural`.
It means a function that takes an argument of type `Natural` and returns a result also of type `Natural`.

We can check that a function has the type we expect:

```haskell
( λ(p : Natural) → p * 2 + 123 ) : Natural → Natural
```
A type annotation can be also written when defining a variable:

```dhall
let g : Natural → Natural = λ(p : Natural) → p * 2 + 123
```

#### Types of higher-order functions

A curried function returns a function, so its type has the form `something → (something → something)`.
An example is the function `f` defined above: its type is written as `Natural → (Natural → Natural)`.

```dhall
let f : Natural → Natural → Natural
  = λ(n : Natural) → λ(p : Natural) → p * 2 + n
```

Because curried functions are used often, most functional languages adopt the convention that the syntax for `→` associates to the right.
Then parentheses can be omitted, and one writes just `Natural → Natural → Natural`.
This syntax requires some getting used to.

If a function of type `Natural → Natural` is an argument of another function, parentheses are _required_ for the type of that argument.
An example is the function type `(Natural → Natural) → Natural`.

Here are some higher-order functions annotated with their types:
```haskell
let q : (Natural → Natural) → Natural = λ(f : Natural → Natural) → f 123
let r : (Natural → Natural) → Natural → Natural = λ(g : Natural → Natural) → λ(p : Natural) → g p + 123
```

All built-in functions have fixed, known types.
For example, the function `Natural/subtract` has type `Natural → Natural → Natural`.

### Step 4: Type parameters

- Any changeable part of a program may be replaced by a parameter of a function.

Let us apply this principle to _type annotations_.
For example, we may want to replace the type `Natural` in the expression `λ(p : Natural) → p` by a parameter.
Such parameters are called **type parameters**.

Type parameters stand for unknown types. So, they are  different from parameters such as `p` `λ(p : Natural) → p` that stand for an unknown natural number value.
We may call `p` a "value parameter" if we want to be more pedantic.

A programming language must choose a syntax for type parameters. In some languages, type parameters must be capitalized, or they must be put in special brackets, and so on.

In Dhall, the syntax for type parameters is quite similar to the syntax for value parameters: one writes `λ(t : Type) → ...`.
Here, `Type` is a special built-in symbol, similar to the type symbol `Natural`.
The syntax `t : Type` means that `t` is a type parameter that stands for some (as yet) unknown type.

As an example of replacing types by type parameters in expressions, let us replace `Natural` in `λ(p : Natural) → p` by the type parameter `t`.
The result is a function whose argument is `t`:

```haskell
λ(t : Type) → λ(p : t) → p
```

This function is similar to a curried function. It can be used with two curried arguments:

```dhall
⊢ let id = λ(t : Type) → λ(p : t) → p   in   id Natural 123

123
```

Functions with type parameters are known in some programming languages as **generic functions** or **polymorphic functions**.

For instance, the function `id` shown above may be called  the "polymorphic identity function".

Polymorphic functions can work with values of any type, as long as that type is given as the type argument.

Dhall has no features for determining what specific type `t` is; all we know is that `t` is a type (the annotation `t : Type`).
When we are writing the code for the function body of a polymorphic function, we have to treat `t` as a completely unknown, arbitrary type.
The function body cannot implement one special logic for `t = Natural`, another logic for `t = Natural → Natural`, and yet another logic for other `t`.
A polymorphic function must work in the same way for all types.

This property of functions is known as **parametric polymorphism** or "parametricity".

For example, the function `id` shown above can work with type `Natural → Natural` just as well:

```dhall
⊢ let id = λ(t : Type) → λ(p : t) → p   in   id (Natural → Natural) (λ(p : Natural) → p * 2)

λ(p : Natural) → p * 2
```

Another example of a polymorphic function is:

```haskell
λ(t : Type) → λ(p : t) → λ(q : t) → p
```

#### Types of functions with type parameters

- Any expression must have a certain type.

So, we need a syntax for writing the types of polymorphic functions.

Types of ordinary functions are written simply as `Natural → Natural` and so on.
But this syntax is not sufficient for polymorphic functions because the type parameter must be used when describing the types of their arguments and return values.

For example, the function `id` shown above:
```haskell
λ(t : Type) → λ(p : t) → p
```
has the first curried argument `t : Type`, the second curried argument `p : t`, and the final returned result is `p` (which has type `t`).
We cannot write the type of this function as `Type → t → t` because the symbol `t` in that expression is undefined.

Dhall uses special syntax to describe such types:

```haskell
∀(t : Type) → t → t
```
This reads as: "For all types `t` return a function of type `t → t`".

Dhall also supports a more verbose syntax:
```haskell
∀(t : Type) → ∀(p : t) → t
```
In this syntax, the name `p` is written out, even though it is not used later in the type expression.

We can see this syntax in a Dhall interactive session if we define the `id` function separately (using Dhall's `:let` directive in the REPL):

```dhall
⊢ :let id = λ(t : Type) → λ(p : t) → p

id : ∀(t : Type) → ∀(p : t) → t

⊢ id (Natural → Natural) (λ(p : Natural) → p * 2)

λ(p : Natural) → p * 2
```

This form of the function type syntax shows how Dhall treats types and values in a largely similar way.

#### Functions from types to types

Functions with type parameters follow from the principle that we should be able to a function argument by a type.
The result is that we obtain a function whose first argument is a type parameter.

We may apply the same principle also to the returned value of a function.
So, now we would like to replace the returned value of a function by a type.

The result is a function that _returns a type_.

The argument of such a function could be either a value parameter or a type parameter.
If it is a type parameter, we obtain a function from types to types.

An example of such a function is the Dhall built-in symbol `List`.
This symbol can be used as `List Natural`, `List Bool`, or `List (Natural → Natural)` to denote lists whose elements have a given type.
For example, we may write:
```haskell
[ True, False, True ] : List Bool
```
and:
```haskell
[ 1, 2, 3, 4, 5 ] : List Natural
```
and:
```haskell
[ λ(p : Natural) → p * 2, λ(p : Natural) → p * p, λ(p : Natural) → 123 ] : List (Natural → Natural)
```

Note that `List` itself is not a type that has values. It is not valid to write `[ 1, 2, 3 ] : List`.
We must apply `List` to a type parameter in order to get a type that has values.

It is inconvenient to say "the type of a type". So, types of types are called **kinds**.
The symbol `Type` itself is a "kind".

Types that has values, such as `Natural` or `List Bool`, have kind `Type`.

The symbol `List` itself has kind `Type → Type`, indicating that `List` is a function that maps types to types.

Dhall has another built-in symbol of the same kind: `Optional`.

Note that `List` and `Optional` are not just any functions that map types to types.
These functions always create a new type as a result.
For instance, `List Bool` is not the same type as `Bool`, and not the same type as any other type that's not computed as `List Bool`.

Type-to-type functions that always create a new type are usually called **type constructors**.

### Step 5: Values, types, and kinds

- Any expression must have a certain type.

If types are expressions then each type must itself have a type.

In most programming languages, types are not expressions.
Even in languages that support type parameters, types themselves often cannot have type annotations.

But if we persist in applying this principle further, we arrive at a language where each type _needs_ to have a type.

In Dhall, each value indeed has a type, and each type also has a type (it is called "kind").
For example, the value `123` has type `Natural`, and the function `λ(p : Natural) → p * 2` has type `Natural → Natural`.
These types themselves have kind `Type`.

This is consistent with the syntax for type parameters: the type parameter is annotated as `λ(t : Type) → ...`.
When we apply a polymorphic function in Dhall, we need to write the type parameter.

In other languages, type parameters may be omitted when the compiler is able to figure out what they must be; but Dhall requires explicit type parameters.

The type parameter `t` could be assigned to `Natural` and `Natural → Natural` when a polymorphic function is applied.
So, we see that `Natural → Natural` has kind `Type`.

The syntax for type annotations is the same for values (`123 : Natural`) and for types (`Natural : Type`).

What is the type of `Type` itself? In Dhall, that type is denoted by the built-in symbol `Kind`, and it is called a **sort**.

Other types of sort `Kind` are `Type → Type` or `Type → Type → Type` and other type-level functions.

What is the type of `Kind`? In Dhall, it is called `Sort`.
Another example of a type of sort `Sort` is `Kind → Kind`.

#### Type universes

The principle that everything should have a type means that we have an infinite sequence of type symbols.
This is an inconvenient feature of programming languages that take the typing principle to its limit.
Such languages (Agda, Idris, Lean, etc.) need to support an infinite sequence of type symbols, each denoting the type of the previous one: `Type0` has type `Type1`; `Type1` has type `Type2`; `Type2` has type `Type3` and so on.
Those type symbols are called **type universes**.

The Dhall language makes a decision to cut the infinite sequence of type universes after `Sort`.
So, in Dhall the symbol `Sort` does _not_ itself has a type.
If `Sort` used in situations where its type were needed (for example, as the type of a function argument or a function return value) then Dhall indicates a type error and does not accept the program.

As a result, Dhall has only three type universes: `Type`, `Kind`, `Sort` (corresponding to `Type0`, `Type1`, `Type2` in other languages).

In practice, the restriction to three type universes is not a significant limitation.
One consequence of that limitation is Dhall's inability to implement a kind-parametric list as a user-defined data type.
Dhall programs can implement user-defined data structures of the following shapes:

- Lists of values such as `[ 1, 2, 3]`; that is, lists of values of a specific type.
- lists of simple types such as `[ Natural, Natural → Natural, Bool ]` (a "type-level" list). Each type in that list has kind `Type`.
- Lists of types that all belong to a given specific kind. For example, the list `[ Option, List, Option ]` where each type has kind `Type → Type`.

But Dhall cannot implement a kind-polymorphic list parameterized by an arbitrary kind.

In practice, the need for such data structures appears to be rare.

In a language that supports infinitely many type universes, one can implement a fully "universe-polymorphic" list; that is, a list of types belonging to an arbitrary type universe.

The problem with type universes is that there isn't a reasonable practical need for infinitely many different type universes; and yet the language forces the programmer to consider them and to write code supporting them.


## Appendix: Naturality and parametricity

The properties known as "naturality" and "parametricity" are rigorous mathematical expressions of a programmer's intuition about functions with type parameters.

This appendix will describe some results of the theory that studies those properties, applied to Dhall programs.

To make the presentation easier to follow, we will denote all types by capital letters and all values by lowercase letters (although Dhall does not require that convention and does not use it in its own libraries).

### Natural transformations

Type signatures of the form `∀(A : Type) → F A → G A`, where `F` and `G` are some type constructors, are often seen in practice.
Examples are functions like `List/head`, `Optional/concat`, and many others.

```dhall
⊢ :type List/head

∀(a : Type) → List a → Optional a

⊢ :type https://prelude.dhall-lang.org/Optional/concat

∀(a : Type) → ∀(x : Optional (Optional a)) → Optional a
```
In the last example, the type signature of `Optional/concat` is of the form `∀(A : Type) → F A → G A` if we define the type constructor `F` as `F A = Optional (Optional A)` and set `G = Optional`.

Functions of type `∀(A : Type) → F A → G A` are called **natural transformations** when both `F` and `G` are covariant functors, or when both are contravariant (and when the function satisfies the naturality law).


If a function has several type parameters, it may be a natural transformation separately with respect to some (or all) of the type parameters.

To see how it works, consider the method `List/map` that has the following type signature:

```dhall
let List/map : ∀(A : Type) → ∀(B : Type) → (A → B) → List A → List B
  = https://prelude.dhall-lang.org/List/map
```

To see that `List/map` is a natural transformation, we first fix the type parameter `B`.
That is, we remove `∀(B : Type)` from the type signature and assume that the type `B` is defined and fixed.
Then we rewrite the type signature of `List/map` as `∀(A : Type) → F A → G A`, where the type constructors `F` and `G` are defined by `F X = X → B` and `G X = List X → List B`.
Both types `F X` and `G X` are _contravariant_ with respect to `X`.
So, in this way we express `List/map` as a (contravariant) natural transformation with respect to the type parameter `A`.

Considering now the type parameter `B` as varying, we fix `A` and rewrite the type signature of `List/map` as `∀(B : Type) → K B → L B`, where `K` and `L` are defined by `K X = A → X` and `L X = List A → List X`.
Both `K` and `L` are covariant.
So, `List/map` is a (covariant) natural transformation with respect to the type parameter `B`.

### Naturality laws

Suppose both `F` and `G` are covariant functors, and consider a natural transformation `t : ∀(A : Type) → F A → G A`.

Often, a covariant functor represents a generic data structure such that `F A` is a type that can store data of an arbitrary type `A`.
One expects that a natural transformation `t` takes some of the data of type `A` stored in `F A` and somehow arranges for (some of) the same data to be stored in `G A`.

The function `t` must implement an algorithm that works in the same way for all types `A` and for all values of those types.
The code of `t` cannot make any algorithmic decisions either based on specific values `x : A` stored in `F A`, or based on the type `A` itself.
The function `t` may omit, duplicate, or reorder some data items, but no data may be changed, and no new data may be added.
This is because the function `t` does not know anything about the type `A` and so cannot compute any new values of type `A`.
All values of type `A` to be stored in the new data structure `G A` must be some of those values that were already stored in `F A`.

The mathematical formulation of that property is called the **naturality law** of `t`.
It is an equation written like this: For any types `A` and `B`, and for any function `f : A → B`:

```haskell
t . fmap_F f == fmap_G f . t
```

To represent this concise formula in Dhall, we write the following definitions:

```dhall
-- Define the type constructor F and its fmap method:
let F : Type → Type = ???
let fmap_F : ∀(A : Type) → ∀(B : Type) → (A → B) → F A → F B = ???
-- Define the type constructor G and its fmap method:
let G : Type → Type = ???
let fmap_G : ∀(A : Type) → ∀(B : Type) → (A → B) → G A → G B = ???
-- Define the natural transformation t:
let t : ∀(A : Type) → F A → G A = ???
let naturality_law =
  λ(A : Type) → λ(B : Type) → λ(f : A → B) → λ(p : F A) →
    assert : fmap_G A B f (t A p) ≡ t B (fmap_F A B f p)
```

A naturality law of `t` describes what happens when we apply the transformation `t` to a data container.
We can apply `t` to transform `F A → G A`, followed by an `fmap_G f`-based transformation (`G A → G B`).

(A lawful `fmap` method does not change the container's shape or data ordering but only replaces each data item of type `A` separately by another data item of type `B`.)

We can also first apply `fmap_F f` to transform `F A → F B` and then apply `t` to transform `F B → G B`.
The final results of type `G B` will be the same.

So, naturality laws describe program refactorings where the programmer decides to change the order of function applications.
(For example, first apply `List/map A B f` and then `List/head B`; or first apply `List/head A` and then `Optional/map A B f`.)
Because the naturality law holds, the results of the program are guaranteed to remain the same after refactoring.

If a natural transformation has several type parameters, there will be a separate naturality law with respect to each of the type parameters.
To write that kind of naturality law, we need to fix all type parameters except one.

As an example, consider the function `List/map` whose type signature may be written as: `∀(A : Type) → ∀(B : Type) → (A → B) → List A → List B`.

We fix the type parameter `B` and view `List/map` as a natural transformation with respect to the type parameter `A`.
To write the corresponding naturality law, we introduce arbitrary types `X`, `Y` and an arbitrary functions `f : X → A` and `g : A → B`.
Then, for any value `p : List X` we must have:

```dhall
let fThenG : X → B = compose_forward X A B f g
 in      -- Symbolic derivation.
   assert : List/map X B fThenG p ≡ List/map A B g (List/map X A f p)
```


### Parametricity theorem. Relational naturality laws

As a motivation for the parametricity theorem, consider a simple function with a type parameter:

```dhall
let f : ∀(A : Type) → A → A → A
  = λ(A : Type) → λ(x : A) → λ(y : A) → x
```

Because the type `A` is unknown, the function `f` cannot examine the values `x` and `y` and perform any nontrivial computation with them (it cannot even check whether `x == y`).

Neither can the code of `f` examine the type `A` itself and make decisions based on that.
The code of `f` cannot check whether the type parameter `A` is equal to `Natural`, say.
This is so because Dhall does not support comparing types or pattern-matching on type parameters.

The function `f` must work in the same way for all types `A`.
It is not possible to create a value of an unknown type `A` from scratch.
So, the code of `f` can return one of the given values (`x` or `y`), but it can do nothing else.

Here is an imaginary example of a function that does not work in the same way for all types:

```dhall
let f_strange
  : ∀(A : Type) → A → A → A
  = λ(A : Type) → λ(x : A) → λ(y : A) →
      -- Type error: Dhall cannot compare types.
    if A == Natural then x else y
```
This function implements a different logic for `A == Natural` as opposed to other types.
This sort of code could be written in a language where types may be compared at run time.
But Dhall does not support such functionality.

For this reason, any Dhall function with a type parameter `A` must work in the same way for all `A`.
This property is known as the "full polymorphic parametricity" of the function's code.
We will call such code **fully parametric code** for short.

The **parametricity theorem** says that any fully parametric function will automatically satisfy a certain mathematical law.
The form of that law is determined by the type signature of the function and does not depend on its implementation.
(So, all functions of that type will satisfy the same law.
That law was called a **free theorem** in the paper ["Theorems for free" by P. Wadler](https://people.mpi-sws.org/~dreyer/tor/papers/wadler.pdf).)

The general formulation and proof of the parametricity theorem are beyond the scope of this book.
For more details, see ["The Science of Functional Programming"](https://leanpub.com/sofp) by the same author.
In Appendix C of that book, the parametricity theorem is proved for fully parametric programs written in a subset of Dhall.
(The relevant subset of Dhall excludes dependent type constructors or quantifiers for type-valued functions.
For example, Leibniz equality types are not supported by the parametricity theorem, as they allow us to implement functions that do not work in the same way for all choices of type parameters.)

For natural transformations (functions of type `∀(A : Type) → F A → G A`), the automatic law will be the naturality law.

So, the parametricity theorem guarantees that all Dhall functions of type `∀(A : Type) → F A → G A` are natural transformations obeying the naturality law, as long as the type constructors `F` and `G` are both covariant or both contravariant.

For functions of more complicated type signatures, naturality laws do not apply.
The parametricity theorem gives a law of a more complicated form than a naturality law.

An example of such a law is for functions with type signatures `∀(A : Type) → (F A → G A) → H A`, where `F`, `G`, and `H` are arbitrary covariant type constructors.
This is not a type signature of a natural transformation because it _cannot_ be rewritten in the form `∀(A : Type) → K A → L A` where `K` and `L` are either both covariant or both contravariant.

For functions `t : ∀(A : Type) → (F A → G A) → H A`, the parametricity theorem gives the law formulated like this:

For any types `A` and `B`, and for any functions `f : A → B`, `p : F A → G A`, and `q : F B → G B`, first define the property we call "`f`-relatedness". We say that `p` and `q` are "`f`-related" if for all `x : F A` we have:

```dhall
fmap_G A B f (p x) ≡ q (fmap_F A B f x)  -- Symbolic derivation.
```
This equation is similar to a naturality law except for using two different functions, `p` and `q`.
(If we set `p = q`, we would obtain the naturality law of `p`. However, that naturality law is not what is being required here.)

Having defined the property of `f`-relatedness, we can finally formulate the law of `t` that follows from the parametricity theorem: For any `f`-related values `p` and `q`, the following equation must hold:

`fmap_H A B f (t A p) ≡ t B q`

It is important to note that the property of being `f`-related is defined as a _many-to-many relation_ between the functions `f`, `p`, and `q`.
Because of this complication, the law of `t` does not have the form of a single equation.
The law says that the equation `fmap_H A B f (t A p) ≡ t B q` holds for all those `p` and `q` that are in a certain relation to each other and to `f`.

That law of `t` is known as a **strong dinaturality law**.
That law makes it easier to use the relational naturality law adapted to the type signature of `t`.

The strong dinaturality law can be written in Dhall syntax as:

```dhall
-- Symbolic derivation. The strong dinaturality law of `p`:
∀(t : ∀(R : Type) → (F R → G R) → H R) → ∀(A : Type) → ∀(B : Type) → ∀(f : A → B) → ∀(p : F A → G A) → ∀(q : F B → G B) →
-- If p and q are f-related then fmap f (t p) ≡ t q
   ∀(_ : ∀(x : F A) → functorG.fmap A B f (p x) ≡ q (functorF.fmap A B f x)) →
     functorH.fmap A B f (t A p) ≡ t B q
```
By the parametricity theorem, any Dhall function with type signature `∀(A : Type) → (F A → G A) → H A` will obey the strong dinaturality law.



To summarize: the parametricity theorem applies to any Dhall value implemented via fully parametric code.
For any Dhall type signature that involves type parameters, the parametricity theorem gives a law automatically satisfied by all Dhall values of that type signature.

That law is determined by the type signature alone and can be written in advance, without knowing the code of the Dhall function.

That law is the naturality law if the function has a type signature of the form `∀(A : Type) → K A → L A`, where `K` and `L` are either both covariant or both contravariant.

For functions with type signatures of the form `∀(A : Type) → (F A → G A) → H A`, where `F`, `G`, and `H` are arbitrary covariant type constructors, parametricity theorem gives a more complicated relational naturality law, which can be reduced to the strong dinaturality law shown above.

In this book's derivations, we will prove various properties of Dhall programs by assuming that the parametricity theorem and the various relational naturality laws always hold.
The parametricity theorem shows how such laws are formulated for arbitrarily complicated type signatures.
For the purposes of this book, it will be sufficient to use the strong dinaturality law shown above for type signatures of the form `∀(A : Type) → (F A → G A) → H A`.

### The four Yoneda identities for types

One of the important applications of the parametricity theorem is the type equivalences known as the **Yoneda identities**.

There are four different Yoneda identities.
An example of a Yoneda identity is the following type equivalence:

```dhall
F A  ≅  ∀(B : Type) → (A → B) → F B
```
This type equivalence holds under two assumptions:

- `F` is a covariant functor with a lawful `fmap` method
- all functions of the type `∀(B : Type) → (A → B) → F B` are natural transformations that satisfy the appropriate naturality law

Because of automatic parametricity, the second assumption is always satisfied as long as we are considering functions implemented in Dhall.

The Yoneda identity shown above requires `F` to be a covariant functor.
There is a corresponding Yoneda identity for contravariant functors ("contrafunctors") `C`:

```dhall
C A  ≅  ∀(B : Type) → (B → A) → C B
```

The two Yoneda identities just shown will apply to universally quantified function types of a certain form.
Similar type identities exist for certain _existentially_ quantified types:

```dhall
-- Mathematical notation: F A ≅ ∃ B. (F B) × (B → A)
F A  ≅  Exists (λ(B : Type) → { seed : F B, step : B → A })

-- Mathematical notation: C A ≅ ∃ B. (C B) × (A → B)
C A  ≅  Exists (λ(B : Type) → { seed : C B, step : A → B })
```
Here it is required that `F` be a covariant functor and `C` a contrafunctor.
These type equivalences are sometimes called **co-Yoneda identities**.

In the next subsections, we show proofs of the covariant versions of the Yoneda identities.
Proofs for the contravariant versions are similar.

#### Proof of the covariant Yoneda identity

We prove that, for any covariant functor `F` and for any type `A`, the type `F A` is equivalent to the type of natural transformations `∀(B : Type) → (A → B) → F B`.

For brevity, let us view `A` and `F` as fixed and denote by `Y` the type:

```dhall
let F : Type → Type = ???
let Y = ∀(B : Type) → (A → B) → F B
```

It is assumed that the naturality laws hold for all natural transformations of type `Y`, and that the functor laws hold for `F`'s `fmap_F` method.

To demonstrate the type equivalence (an isomorphism), we implement two functions `inY` and `outY` that map between the two types:

```dhall
let fmap_F = ???
let inY : F A → Y
  = λ(fa : F A) → λ(B : Type) → λ(f : A → B) → fmap_F A B f fa

let outY : Y → F A
  = λ(y : Y) → y A (identity A)
```

We have imposed the requirement that any value of type `Y` must be a natural transformation.
So, we need to begin by showing that, for any `fa : F A`, the value `inY fa` is automatically a natural transformation of type `Y`.

The naturality law corresponding to the type `Y = ∀(B : Type) → (A → B) → F B` says that, for any `y : Y` and any types `B`, `C`, and for any functions `f : A → B`, `g : B → C`, the following equation must hold:

```dhall
-- Symbolic derivation.
y C (compose_forward A B C f g) ≡ fmap_F B C g (y B f)
```

We substitute `y = inY fa` into the left-hand side of this naturality law:

```dhall
-- Symbolic derivation.
y C (compose_forward A B C f g)   -- Expand the definition of y:
  ≡ inY fa C (compose_forward A B C f g)  -- Expand the definition of inY:
  ≡ fmap_F A C (compose_forward A B C f g) fa  -- Use fmap_F's composition law:
  ≡ fmap_F B C g (fmap_F A B f fa)
```

Now we write the right-hand side of the naturality law:

```dhall
-- Symbolic derivation.
fmap_F B C g (y B f)  -- Expand the definition of y:
  ≡ fmap_F B C g (inY fa B f)  -- Expand the definition of inY:
  ≡ fmap_F B C g (fmap_F A B f fa)
```
We obtain the same expression as from the left-hand side.
So, the naturality law will hold automatically for values `y` obtained via `inY`.

Now we will prove that the compositions of `inY` with `outY` in both directions are identity functions.

The first direction: for any given `fa : F A`, we compute `y : Y = inY fa` and `faNew : F A = outY y`.
Then we need to prove that `faNew ≡ fa`:

```dhall
-- Symbolic derivation.
faNew ≡ outY y  -- Expand the definition of outY:
  ≡ y A (identity A)   -- Expand the definition of y:
  ≡ inY fa A (identity A)  -- Expand the definition of inY:
  ≡ fmap_F A A (identity A) fa  -- Use the identity law of fmap_F:
  ≡ identity (F A) fa    -- Apply the identity function:
  ≡ fa
```
This depends on the identity law of `fmap_F`, which holds by assumption.

The second direction: for any given `y : Y` that satisfies the naturality law, we compute `fa : F A = outY y` and `yNew : Y = inY fa`.
Then we need to prove that `yNew ≡ y`.
Both `y` and `yNew` are functions, so we need to show that those functions give the same results when applied to arbitrary arguments.
Take any type `B` and any `f : A → B`.
Then we need to show that `yNew B f ≡ y B f`.
This will require using the naturality law of `y`:

```dhall
-- Symbolic derivation.
yNew B f ≡ inY fa B f  -- Expand the definition of inY:
  ≡ fmap_F A B f fa  -- Expand the definition of fa:
  ≡ fmap_F A B f (outY y)  -- Expand the definition of outY:
  ≡ fmap_F A B f (y A (identity A))  -- Use the naturality law of y:
  ≡ y B (compose_forward A A B (identity A) f)  -- Compute composition:
  ≡ y B f
```

This completes the proof of the isomorphism between `F A` and `Y`. $\square$

Let us remark on the use of `y`'s naturality law in this proof.

At a certain step in the last part of the proof, we needed to show that `fmap_F A B f (y A (identity A))` equals `y B f`.
Because `y` is an arbitrary function, we cannot substitute any specific code for `y`.
If we knew nothing else about `y` other than it has type `Y`, we would not be able to proceed with the proof any further after that step.

But we do know that `y` satisfies a naturality law, and that law relates different expressions involving `y`.
So, the proof of the Yoneda identity works only due to the assumed naturality law of `y`.
All Dhall functions of type `Y` will automatically satisfy that law.
The Yoneda identities do not hold in programming languages where one can implement functions that violate naturality.

#### Proof of the covariant co-Yoneda identity


We prove that, for any covariant functor `F` and for any type `A`:

```dhall
-- Mathematical notation: F A ≅ ∃ B. (F B) × (B → A)
F A  ≅  Exists (λ(B : Type) → { seed : F B, step : B → A })
```

For brevity, let us view `F` and `A` as fixed and denote:

```dhall
let F = ???
let P = λ(B : Type) → { seed : F B, step : B → A }
```

Then the covariant co-Yoneda identity says: `F A ≅ Exists P`.

To make the required assumptions precise, let us write out the type `Exists P`:

```dhall
Exists P  ≅  (∀(R : Type) → (∀(B : Type) → P B → R) → R)
```

Both universal quantifiers (`∀(R : Type)` and `∀(B : Type)`) are used with function types of the form of natural transformations.
So, we need to assume that all functions with type signatures `∀(B : Type) → P B → R` are natural transformations with respect to `B`, and all functions with type signatures `∀(R : Type) → (∀(B : Type) → P B → R) → R` are natural transformations with respect to `R`.
These assumptions are satisfied automatically if we are working with functions implemented in Dhall.

Begin by considering the type `∀(B : Type) → P B → R`.
We can rewrite that type equivalently in a curried form, replacing the record type `{ seed : F B, step : B → A }` by two curried arguments of types `F B` and `B → A`:

```dhall
∀(B : Type) → P B → R
  ≅  ∀(B : Type) → { seed : F B, step : B → A } → R
  ≅  ∀(B : Type) → (B → A) → F B → R
```

Now we note that the last type is of the form to which the contravariant Yoneda identity applies.
Namely, it is of the form `∀(B : Type) → (B → A) → C B` with the contrafunctor `C` defined by `C B = F B → R`.
By the contravariant Yoneda identity, that type is equivalent to just `C A`, which means `F A → R`.

So, we may write the type equivalence:

```dhall
∀(B : Type) → (B → A) → F B → R  ≅  F A → R
```

Using that equivalence, the type `Exists P` is rewritten as:

```dhall
Exists P ≡ ∀(R : Type) → (∀(B : Type) → P B → R) → R
  ≅ ∀(R : Type) → (F A → R) → R
```

This type is in the form to which the covariant Yoneda identity applies.
We find:

```dhall
∀(R : Type) → (F A → R) → R  ≅  F A
```

This proves the equivalence `Exists P ≅ F A`.

To derive the code that transforms values of type `F A` into values of type `Exists P` and back,
we turn to the two Yoneda identities used in the proof.

The first type equivalence (`∀(B : Type) → (B → A) → F B → R  ≅  F A → R`) corresponds to using the contravariant Yoneda identity.
The corresponding code consists of two functions:

TODO

### Church encoding of least fixpoints: Proofs

Here we show proofs of some technical properties of Church-encoded types.
(Those properties are explained in the paper "Recursive types for free" by P. Wadler.
This section gives fully detailed proofs.)

Throughout this section, we assume that `F` is a lawful covariant functor for which an evidence value `functorF : Functor F` is available.
We define the type `C` by `C = LFix F`, or in explicit form: `C = ∀(R : Type) → (F R → R) → R`.

We will assume that (due to automatic parametricity) all values of type `C` obey the **strong dinaturality law** shown earlier, adapted to the type signature of `C`.

###### Statement 1

For any type `R` and any function `frr : F R → R`, define the function `c2r : C → R` by:

`let c2r : C → R = λ(c : C) → c R frr`

Then the function `c2r` satisfies the law: for any value `fc : F C`,

`c2r (fix F functorF fc) ≡ frr (functorF.fmap C R c2r fc)`

In category theory, that law is known as the "$F$-algebra morphism law".
Functions that satisfy that law are called **$F$-algebra morphisms**.

So, it is claimed that `c2r` is always an $F$-algebra morphism.
(Note that the notion of an $F$-algebra morphism of type `C → R` depends on having the designated functions `fix : F C → C` and `frr : F R → R`.)

####### Proof

Expand `c2r (fix F functorF fc)` using the definitions of `c2r` and `fix`:

```dhall
-- Symbolic derivation.
c2r (fix F functorF fc)
  ≡ (λ(c : C) → c R frr) (fix F functorF fc)
  ≡ fix F functorF fc R frr
  ≡ frr (functorF.fmap C R (λ(c : C) → c R frr) fc)
  ≡ frr (functorF.fmap C R c2r fc)
```
This is now equal to the right-hand side of the equation we needed to prove.

###### Statement 2

The functions `fix F functorF: F C → C` and `unfix F functorF : C → F C` defined in the chapter "Working with Church-encoded data" are inverses of each other.

####### Proof

We need to prove the two directions of the isomorphism:

(1) For an arbitrary value `c : C`, show that:

`fix F functorF (unfix F functorF c) ≡ c`

(2) For an arbitrary value `p : F C`, show that:

`unfix F functorF (fix F functorF p) ≡ p`

To prove item (1), we note that both sides are functions of type `C`.
Apply both sides to arbitrary arguments `R : Type` and `frr : F R → R` and substitute the definitions of `fix` and `unfix`:

```dhall
-- Expect the following expression to equal just `c R frr`:
fix F functorF (unfix F functorF c) R frr ≡ ???
```

We define temporary symbols `fmap_fix` and `c2r` for brevity, and rewrite the definitions of `fix` and `unfix` as:

```dhall
-- Symbolic derivation. Define for brevity:
let fmap_fix : F (F C) → F C = functorF.fmap (F C) C (fix F functorF)
let c2r : C → R = λ(c : C) → c R frr
-- The applications of `fix` and `unfix` to arbitrary arguments are then rewritten as:
fix F functorF fc R frr = frr (functorF.fmap C R c2r fc)
unfix F functorF c = c (F C) fmap_fix
```

The equation we are trying to prove then becomes:

`frr (functorF.fmap C R c2r (c (F C) fmap_fix)) ≡ c R frr`

By assumption, the value `c : C` satisfies the strong dinaturality law:


```dhall
-- Symbolic derivation. The strong dinaturality law of `c`:
∀(c : C) → ∀(a : Type) → ∀(b : Type) → ∀(f : a → b) → ∀(p : F a → a) → ∀(q : F b → b) →
-- If p and q are f-related then f (c a p) ≡ c b q
   ∀(_ : ∀(x : F a) → f (p x) ≡ q (functorF.fmap a b f x)) →
     f (c a p) ≡ c b q
```
The last equation needs to match the equation we need to prove:

```dhall
-- Symbolic derivation. We need to match this equation:
  frr (functorF.fmap C R c2r (c (F C) fmap_fix)) ≡ c R frr
-- with this one:
  f (c a p) ≡ c b q
-- These equations will be the same if we define:
  a = F C
  b = R
  f = λ(fc : F C) → frr (functorF.fmap C R c2r fc)
  p = fmap_fix
  q = frr
```
This will finish the proof of item (1) as long as we verify the assumption of the strong dinaturality law: namely, that `p` and `q` are `f`-related.
That will be true if, for any `x : F a`, we had:

`f (p x) ≡ q (functorF.fmap a b f x)`

Substitute the parameters as shown above:

```dhall
-- Symbolic derivation. We need to show that this holds:
∀(x : F (F C)) →
  frr (functorF.fmap C R c2r (fmap_fix x))
    ≡ frr (functorF.fmap (F C) R f x)
-- Omit the call to `frr` in both sides:
functorF.fmap C R c2r (fmap_fix x) ≡ functorF.fmap (F C) R f x
```
In the last equation, the left-hand side contains a composition of two functions under `fmap`.
We use `fmap`'s composition law to transform that:

```dhall
-- Symbolic derivation.
functorF.fmap C R c2r (fmap_fix x)
  ≡ functorF.fmap C R c2r (functorF.fmap (F C) C (fix F functorF) x)
-- Use functorF's composition law:
  ≡ functorF.fmap (F C) R (λ(fc : F C) → c2r (fix F functorF fc)) x
```

Now the remaining equation is rewritten to:

```dhall
-- Symbolic derivation. We need to show that this holds:
functorF.fmap (F C) R (λ(fc : F C) → c2r (fix F functorF fc)) x
  ≡ functorF.fmap (F C) R f x
```
Both sides are now of the form `functorF.fmap (F C) R (...) x`. It remains to prove:

`λ(fc : F C) → c2r (fix F functorF fc) ≡ f`

Substitute the definition of `f`:

`f ≡ λ(fc : F C) → frr (functorF.fmap C R c2r fc)`

Omit the common code `λ(fc : F C) → ...`, and it remains to prove that:

`c2r (fix F functorF fc) ≡ frr (functorF.fmap C R c2r fc)`

This holds by Statement 1. This concludes the proof of item (1).

To prove item (2), we substitute the definitions of `fix` and `unfix`:

```dhall
-- Symbolic derivation. For any p : F C, expect this to equal just p:
unfix F functorF (fix F functorF p)  -- Substitute the definition of unfix:
  ≡ fix F functorF p (F C) fmap_fix  -- Substitute the definition of fix:
  ≡ fmap_fix (functorF.fmap C (F C) (λ(c : C) → c (F C) fmap_fix) p)
-- Substitute the definition of `unfix` again:
  ≡ fmap_fix (functorF.fmap C (F C) (unfix F functorF) p)
-- Use the composition law of fmap:
  ≡ functorF.fmap C C (λ(c : C) → fix F functorF (unfix F functorF c)) p
```
Now we use item (1) that we already proved, and find:

`fix F functorF (unfix F functorF c) ≡ c`

So, the argument of `functorF.fmap C C ` is actually an identity function of type `C → C`.
This allows us to complete the final step of the proof:

```dhall
-- Symbolic derivation.
functorF.fmap C C (λ(c : C) → fix F functorF (unfix F functorF c)) p
  ≡ functorF.fmap C C (λ(c : C) → c) p
-- Use the identity law of fmap:
  ≡ p
```

###### Statement 3

Applying any value of a Church-encoded type (`c : C`) to its own standard function `fix` gives again the same value `c`.
More precisely:

`c C (fix F functorF) ≡ c`

####### Proof

We need to prove an equation between functions of type `C`.
Apply both sides of that equation to arbitrary arguments `R : Type` and `frr : F R → R`.
So, we need to prove that:

`c C (fix F functorF) R frr ≡ c R frr`

Values `c : C` satisfy the strong dinaturality law:


```dhall
-- Symbolic derivation. The strong dinaturality law of `c`:
∀(c : C) → ∀(a : Type) → ∀(b : Type) → ∀(f : a → b) → ∀(p : F a → a) → ∀(q : F b → b) →
-- If p and q are f-related then f (c a p) ≡ c b q
   ∀(_ : ∀(x : F a) → f (p x) ≡ q (functorF.fmap a b f x)) →
     f (c a p) ≡ c b q
```
The last equation needs to match the equation we need to prove:

```dhall
-- Symbolic derivation. We need to match this equation:
  c C (fix F functorF) R frr ≡ c R frr
-- with this one:
  f (c a p) ≡ c b q
-- These equations will be the same if we define:
  a = C
  b = R
  f = λ(c : C) → c R frr
  p = fix F functorF
  q = frr
```
This will finish the proof of as long as we verify the assumption of the strong dinaturality law: namely, that `p` and `q` are `f`-related.
That will be true if, for any `x : F a`, we had:

`f (p x) ≡ q (functorF.fmap a b f x)`

Substitute the parameters as shown above:

```dhall
-- Symbolic derivation. We need to show that, for any `x : F C`:
f (fix F functorF x) ≡ frr (functorF.fmap C R f x)
```

This holds by Statement 1 if we rename `fc = x` and `c2r = f`.

###### Statement 4

Given a type `R` and a function `frr : F R → R`, suppose there exists a function `f : C → R` that
satisfies the $F$-algebra morphism law:

`∀(fc : F C) → f (fix F functorF fc) ≡ frr (functorF.fmap C R f fc)`

Then the function `f` is equal to the function `c2r` defined by `c2r = λ(c : C) → c R frr`.
(By Statement 1, that function satisfies the $F$-algebra morphism law.)

####### Proof

Suppose a function `f : C → R` is given and satisfies the $F$-algebra morphism law.
We need to prove that, for any `c : C`, the following holds:

`f c ≡ c2r c ≡ c R frr`.

Values `c : C` satisfy the strong dinaturality law:


```dhall
-- Symbolic derivation. The strong dinaturality law of `c`:
∀(c : C) → ∀(a : Type) → ∀(b : Type) → ∀(f : a → b) → ∀(p : F a → a) → ∀(q : F b → b) →
-- If p and q are f-related then f (c a p) ≡ c b q
   ∀(_ : ∀(x : F a) → f (p x) ≡ q (functorF.fmap a b f x)) →
     f (c a p) ≡ c b q
```
The last equation needs to match the equation we need to prove:

```dhall
-- Symbolic derivation. We need to match this equation:
  f c ≡ c R frr
-- with this one:
  f (c a p) ≡ c b q
-- These equations will be the same if we define:
  a = C
  b = R
  p = fix F functorF
  q = frr
```

Note that the strong dinaturality law gives `f (c C p) ≡ c R frr` and not `f c = c R frr`.
However, Statement 3 says that `c C p ≡ c` with our definition of `p`.
That is why we are justified in replacing `f c` by `f (c C p)`.

So, the proof will be finished as long as we verify the assumption of the strong dinaturality law: namely, that `p` and `q` are `f`-related.
That will be true if, for any `x : F a`, we had:

`f (p x) ≡ q (functorF.fmap a b f x)`

Substitute the parameters as shown above, and rename `x` to `fc`:

```dhall
-- Symbolic derivation. We need to show that, for any `fc : F C`:
f (fix F functorF fc) ≡ frr (functorF.fmap C R f fc)
```

This is exactly the same as the $F$-algebra morphism law for `f`, which holds by assumption.

###### Statement 5

The Church encoding type `C` has the following so-called "universal property":
For any fixpoint `R` of the type equation `R = F R`, there exists a unique function `c2r : C → R` that preserves the fixpoint isomorphisms.

To explain the property of "preserving the fixpoint isomorphisms" in detail, consider that:
- The type isomorphism `C ≅ F C` is given by two functions: `fix_C : F C → C` and `unfix_C : C → F C`. Each value `c : C` corresponds to a value `fc : F C` computed as `fc = unfix_C c`, and each value `fc` corresponds to a value `c` computed as `c = fix_C fc`.
- The type isomorphism `R ≅ F R` is given by two functions: `fix_R : F R → R` and `unfix_R : R → F R`. Each value `r : R` corresponds to a value `fr : F R` computed as `fr = unfix_R r`, and each value `fr` corresponds to a value `r` computed as `r = fix_R fr`.
- Any `c : C` is mapped by the function `c2r` into some `r : R`.
- Any `fc : F C` is mapped by the function `fmap_F c2r` into some `fr : F R`.
- The property of "preserving the fixpoint isomorphisms" means that `c2r` should map `c` into `r` and the corresponding `fc` into the corresponding `fr`. In other words, `fr ≡ unfix_R r` if and only if `fc ≡ unfix_C c`.

It means that the following equations must hold:

(1) For any `fc c : F C`: `fix_R (fmap_F c2r fc) ≡ c2r (fix_C fc)`.

(2) For any `c : C`: `unfix_R (c2r c) ≡ fmap_F c2r (unfix_C c)`.

We claim that these equations will hold for the function `c2r` defined by `c2r = λ(c : C) → c R fix_R`, and that there is only one such function.

####### Proof

By Statement 1 (where we use `frr = fix_R`), there is only one function of type `C → R` that satisfies equation (1) above, and that function is `c2r` defined by `c2r = λ(c : C) → c R fix_R`.

To show that `c2r` also satisfies equation (2) above, we choose any value `c : C` and compute the corresponding `fc = unfix_C c`.
Then we substitute that `fc` into equation (1):

```dhall
-- Symbolic derivation.
fix_R (fmap_F c2r fc) ≡ c2r (fix_C fc)
  -- Substitute fc = unfix_C c:
fix_R (fmap_F c2r (unfix_C c)) ≡ c2r (fix_C (unfix_C c))
  -- Use the isomorphism law: fix_C (unfix_C  c) ≡ c
fix_R (fmap_F c2r (unfix_C c)) ≡ c2r c
  -- Apply unfix_R to both sides of the equation:
unfix_R (fix_R (fmap_F c2r (unfix_C c))) ≡ unfix_R (c2r c)
  -- Use the isomorphism law: unfix_R (fix_R fr) ≡ fr
fmap_F c2r (unfix_C c) ≡ unfix_R (c2r c)
```
We obtain equation (2). $\square$

The next statement gives a necessary and sufficient condition for the existence of the least fixpoint of a given functor.

###### Statement 6

For any (covariant) functor `F`, the least fixpoint type `LFix F` is non-void if and only if the type `F <>` is non-void (here, `<>` is Dhall's representation of the void type).

The intuitive picture is that the type `F <>` describes the base case(s) of induction on which the definition of the least fixpoint is based.

####### Proof

The proof goes in two parts:

1) To show that `LFix F` is non-void if `F <>` is non-void.

2) To show that `LFix F` is void if `F <>` is void.

To prove part (1), we show that one may compute a value of type `LFix F` out of a given value of type `F <>`.
In other words, one may implement a function with the type signature `F <> → LFix F`:

```dhall
let fVoidToLFix : ∀(F : Type → Type) → Functor F → F <> → LFix F
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(fv : F <>) → λ(r : Type) → λ(frr : F r → r) →
    frr (functorF.fmap <> r (λ(void : <>) → absurd void r) fv)
```

To prove part (2), we need to assume that the type `F <>` is void.
A type `X` is void if and only if we can implement a function of type `X → <>`.
So, we will prove part (2) if we implement a function with type signature `(F <> → <>) → LFix F → <>`.
The code is straightforward; we simply apply a value of type `LFix F` to the void type.
```dhall
let fVoidVoidToLFixVoid : ∀(F : Type → Type) → (F <> → <>) → LFix F → <>
  = λ(F : Type → Type) → λ(p : F <> → <>) → λ(c : LFix F) →
    c <> p
```
For this part of the proof, we do not need to use the functor property of `F`.

### Mendler encodings

An alternative encoding of a recursive type is known as the **Mendler encoding**.
We show it only for reference, as there is no particular advantage in using the Mendler encodings in Dhall.
The lazy evaluation in Dhall already gives all possible shortcuts and performance improvements when working with recursive types.

```dhall
let MFix : (Type → Type) → Type
  = λ(F : Type → Type) → ∀(r : Type) → (∀(s : Type) → (s → r) → F s → r) → r
```
When `F` is a covariant functor, the type `∀(s : Type) → (s → r) → F s → r` is isomorphic to just `F r → r` (by the contravariant Yoneda identity).
Then the Mendler encoding is isomorphic to the Church encoding of the least fixpoint of `F`.

When `F` is not a functor, the Mendler encoding gives the least fixpoint of the "free functor on `F`".
We will show the "free functor" construction later in this book.

As an example, let us implement the `List` functor using the Mendler encoding.
```dhall
let ListF = λ(a : Type) → λ(r : Type) → Optional (Pair a r)
let ListM = λ(a : Type) → MFix (ListF a)
let nilM = λ(a : Type) → λ(r : Type) → λ(f : ∀(s : Type) → (s → r) → Optional (Pair a s) → r) → f r (identity r) (None (Pair a r))
let consM = λ(a : Type) → λ(head : a) → λ(tail : ListM a) → λ(r : Type) → λ(f : ∀(s : Type) → (s → r) → Optional (Pair a s) → r) → f r (identity r) (Some { _1 = head, _2 = tail r f } )
```

Here are some example values of type `ListM Natural`:

```dhall
let exampleListM0 : ListM Natural = nilM Natural
let exampleListM3 : ListM Natural = consM Natural 10 (consM Natural 20 (consM Natural 30 (nilM Natural)))
```

TODO:implement headOptional for ListM and discuss performance

Let us implement the `fix` and `unfix` methods for the Mendler encoding.
Note that `fix` no longer needs a `Functor` typeclass evidence for `F`.
(But `unfix` still does!)
```dhall
let fix_M : ∀(F : Type → Type) → F (MFix F) → MFix F
  = λ(F : Type → Type) →
    let C = MFix F
    in λ(fc : F C) → λ(r : Type) → λ(y : ∀(s : Type) → (s → r) → F s → r) →
      let c2r : C → r = λ(c : C) → c r y
      in y C c2r fc

let unfix_M : ∀(F : Type → Type) → Functor F → MFix F → F (MFix F)
  = λ(F : Type → Type) → λ(functorF : Functor F) →
    let C = MFix F
    let fmap_fix_M : F (F C) → F C = functorF.fmap (F C) C (fix_M F)
    let y : ∀(s : Type) → (s → F C) → F s → F C = λ(s : Type) → λ(f : s → F C) → λ(fs : F s) → fmap_fix_M (functorF.fmap s (F C) f fs)
    in λ(c : C) → c (F C) y
```


### The Church-Yoneda identity

The Church encoding formula (`∀(r : Type) → (F r → r) → r`) is not of the same form as the Yoneda identity because the function argument `F r` depends on `r`.
The standard Yoneda identities do not apply to types of that form.

There is a generalized identity (without a widely accepted name) that combines both forms of types:

```dhall
∀(R : Type) → (F R → R) → G R  ≅  G (LFix F)
```
Here `F` and `G` are arbitrary covariant functors, and `LFix F = ∀(R : Type) → (F R → R) → R` is the Church-encoded least fixpoint of `F`.

It is also assumed that all functions with type signature `∀(R : Type) → (F R → R) → G R` will satisfy the **strong dinaturality law**.

This book calls this the **Church-Yoneda identity** because of the similarity to both the Church encodings and the types of functions used in the Yoneda identities.

The Church-Yoneda identity is mentioned as "proposition 1" in the proceedings of the conference ["Fixed Points in Computer Science 2010"](https://hal.science/hal-00512377/document) on page 78 of the paper by T. Uustalu.

In the next subsection, we will use that identity to prove the Church encoding formula for mutually recursive types.

Here is a proof of the Church-Yoneda identity that assumes that the parametricity theorem holds for all values.

To make the proof shorter, let us define the "Church-Yoneda" type constructor, which we will denote by `CY`:
```dhall
let CY = λ(F : Type → Type) → λ(G : Type → Type) → ∀(R : Type) → (F R → R) → G R
```
Then the Church-Yoneda identity may be written as `CY F G  ≅  G (LFix F)`.

First, we implement a pair of functions (`fromCY` and `toCY`) that map between the types `CY F G` and `G (LFix F)`.
Then we will show that those functions are inverses of each other, which will prove the type isomorphism.

```dhall
let fromCY : ∀(F : Type → Type) → Functor F → ∀(G : Type → Type) → CY F G → G (LFix F)
  = λ(F : Type → Type) → λ(functorF : Functor F) → λ(G : Type → Type) → λ(cy : CY F G) →
    let C = LFix F
    in cy C (fix F functorF)  -- Use the standard `fix` function.
let toCY : ∀(F : Type → Type) → ∀(G : Type → Type) → Functor G → G (LFix F) → CY F G
  = λ(F : Type → Type) → λ(G : Type → Type) → λ(functorG : Functor G) →
    let C = LFix F
    in λ(gc : G C) →
        λ(R : Type) → λ(frr: F R → R) →
          let c2r : C → R = λ(c : C) → c R frr
          in functorG.fmap C R c2r gc
```
For brevity, we will write `C` instead of `LFix F` to denote that Church-encoded recursive type.

It remains to prove the two directions of the isomorphism roundtrip (applying `fromCY` after `toCY`, or applying `toCY` after `fromCY`):

(1) For any `gc : G C`, we need to show that:

`fromCY F functorF G (toCY F G functorG gc) ≡ gc`

(2) For any `cy : CY F G`, we need to show that:

`toCY F G functorG (fromCY F functorF G cy) ≡ cy`

To prove item (1), we begin by substituting the definitions of `fromCY` and `toCY` into the left-hand side:

```dhall
-- Symbolic derivation. We expect this to equal `gc`.
fromCY F functorF G (toCY F G functorG gc)
  ≡ fromCY F functorF G (λ(R : Type) → λ(frr: F R → R) →
    functorG.fmap C R (λ(c : C) → c R frr) gc
) ≡ functorG.fmap C C (λ(c : C) → c C (fix F functorF)) gc
```

The last application of `fmap` is to a function of type `C → C` defined by `λ(c : C) → c C (fix F functorF)`.
Applying any value of a Church-encoded type (`c : C`) to its own standard function `fix` gives again the same value `c`.
(That property is proved in the paper "Recursive types for free", and also in this Appendix as "Statement 3" in the section "Church encodings of least fixpoints: Proofs".)

So, the function `λ(c : C) → c C (fix F functorF)` is actually an _identity function_ of type `C → C`.
Applying `fmap` to an identity function gives again an identity function.
Then we get:
```dhall
-- Symbolic derivation.
functorG.fmap C C (λ(c : C) → c C (fix F functorF)) gc
  ≡ functorG.fmap C C (identity C) gc
  ≡ identity (G C) gc
  ≡ gc
```
This is exactly what we needed to show.

To prove item (2), we note that both sides are functions of type `CY F G = ∀(R : Type) → (F R → R) → G R`.
To establish that those two functions are equal, we apply both sides to an arbitrary type `R` and an arbitrary function `frr : F R → R`.
Then we substitute the definitions of `fromCY` and `toCY` into the left-hand side:

```dhall
-- Symbolic derivation. We expect this to equal `cy R frr`.
toCY F G functorG (fromCY F functorF G cy) R frr
  ≡ toCY F G functorG (cy C (fix F functorF)) R frr
  ≡ functorG.fmap C R (λ(c : C) → c R frr) (cy C (fix F functorF))
```

We need to show that the last expression is equal to `cy R frr`.
So, we need to prove an equation that looks like `fmap f (cy p) ≡ cy q`.
This is similar to the form of the strong dinaturality law of `cy`.
Let us write the general form of that law and then find specific parameters that will move the proof forward:

```dhall
-- Symbolic derivation. The strong dinaturality law of `cy`:
∀(cy : CY F G) → ∀(a : Type) → ∀(b : Type) → ∀(f : a → b) → ∀(p : F a → a) → ∀(q : F b → b) →
-- If p and q are f-related then fmap f (cy p) ≡ cy q
   ∀(_ : ∀(x : F a) → f (p x) ≡ q (functorF.fmap a b f x)) →
     functorG.fmap a b f (cy a p) ≡ cy b q
```

Compare the last expression in our derivation with this law and read off the required parameters:

```dhall
-- Symbolic derivation. We need to match this equation:
  functorG.fmap C R (λ(c : C) → c R frr) (cy C (fix F functorF)) ≡ cy R frr
-- with this one:
  functorG.fmap a b f (cy a p) ≡ cy b q
-- These equations will be the same if we define:
  a = C
  b = R
  f = λ(c : C) → c R frr
  p = fix F functorF
  q = frr
```
This will finish the proof of item (2) as long as we verify the assumption of the strong dinaturality law: namely, that `p` and `q` are `f`-related.
That will be true if, for any `x : F a`, we had:

`f (p x) ≡ q (functorF.fmap a b f x)`

Substitute the parameters as shown above:

```dhall
-- Symbolic derivation. We need to show that:
∀(x : F C) →
  f (fix F functorF x) ≡ frr (functorF.fmap C R f x)
```
This holds by Statement 1 in the previous section if we set `fc = x` and `c2r = f`.

### Existential types: Identity law of "pack"

In this subsection, we fix an arbitrary type constructor `P : Type → Type` and study values of type `ExistsP` defined by:

```dhall
let ExistsP = ∀(R : Type) → (∀(T : Type) → P T → R) → R
```
When `P` is fixed, it is convenient to specialize the definitions of `pack` and `unpack` to `P`.
Call the resulting functions `packP` and `unpackP`:

```dhall
let packP : ∀(T : Type) → P T → ExistsP
  = λ(T : Type) → λ(pt : P T) →
      λ(R : Type) → λ(pack_ : ∀(T_ : Type) → P T_ → R) → pack_ T pt
```
and
```dhall
let unpackP : ∀(R : Type) → (∀(T : Type) → P T → R) → ExistsP → R
  = λ(R : Type) → λ(unpack_ : ∀(T : Type) → P T → R) → λ(ep : ExistsP) →
      ep R unpack_
```

With these definitions, we may build values of type `ExistsP` via `packP` and consume those values via `unpackP`.

In this section, we prove an important property of `packP`, which we call the "identity law".
That law is found when we assign the type `R = ExistsP` as the first type parameter of `unpackP`.
With that assignment, we obtain the function `unpackP ExistsP` of the following type:

```dhall
let _ = (unpackP ExistsP) : (∀(T : Type) → P T → ExistsP) → ExistsP → ExistsP
```

The type of the first argument, `∀(T : Type) → P T → ExistsP`, is exactly the same as the type of `packP`.
This suggests considering the following function:

```dhall
let _ = (unpackP ExistsP packP) : ExistsP → ExistsP
```

The identity law says that this function is the _identity function_ of type `ExistsP → ExistsP`.

Heuristically, for any `ep : ExistsP`, the expression `unpackP ExistsP packP ep` first "unpacks" the value `ep` and then immediately "packs" it back.
We might expect that `ep` remains unchanged under those operations.
The identity law of `pack` makes that intuition precise:

```dhall
let ep : ExistsP = ???  -- Create any value of type ExistsP. Then:

unpackP ExistsP packP ep  ≡  ep
```

Because `unpackP` is little more than an identity function of type `ExistsP → ExistsP`, it turns out to be helpful if we inline the code of `unpackP` and simplify the last equation to:

```dhall
let ep : ExistsP = ???  -- Create any value of type ExistsP. Then:

ep ExistsP packP  ≡  ep
```
The last equation is a concise formulation of the identity law of `pack`.
Both sides of the law have type `ExistsP`.
We will now prove that law for arbitrary `ep`, assuming that the naturality law of `ep` holds.
([The author is grateful to Dan Doel for assistance with this proof.](https://cstheory.stackexchange.com/questions/54124))

Note that `ExistsP` is the function type of a natural transformation with respect to the type parameter `R`.
By parametricity, all Dhall values `ep : ExistsP` will satisfy the corresponding naturality law.
That law says that, for any types `R` and `S` and for any functions `f : R → S` and `g : ∀(T : Type) → P T → R`, we will have:

```dhall
-- Symbolic derivation. The naturality law of `ep`:
f (ep R g)  ≡  ep S (λ(T : Type) → λ(pt : P T) → f (g T pt))
```

Both sides of the naturality law apply `ep` to some arguments.
In order to be able to use the naturality law, we need to adapt the equation `ep ExistsP packP ≡ ep` somehow.
To make progress, we apply both sides of that equation to arbitrary arguments `U : Type` and `u : ∀(T : Type) → P T → U`.
If `ep ExistsP packP` is the same function as `ep` then `ep packP U u` will be the same value as `ep U u`.
Write the corresponding equation:

```dhall
-- Symbolic derivation.
ep ExistsP packP U u  ≡  ep U u
```

Our goal is to derive this equation as a consequence of the naturality law of `ep`.
For that, we just need to assign suitable parameters `R`, `S`, `f`, and `g` in that law.
We set `R = ExistsP`, `S = U`, `f ep = ep U u`, and `g = packP`.
Then the left-hand side of the naturality law becomes:

```dhall
-- Symbolic derivation.
f (ep R g)  ≡  ep R g U u  ≡  ep ExistsP packP U u
```
This is exactly the left-hand side of the law we need to prove.

The right-hand side of the naturality law becomes:

```dhall
-- Symbolic derivation.
ep S (λ(T : Type) → λ(pt : P T) → f (g T pt))
  ≡  ep U (λ(T : Type) → λ(pt : P T) → (g T pt) U u)
  ≡  ep U (λ(T : Type) → λ(pt : P T) → packP T pt U u)
```

This will be equal to `ep U u` (the right-hand side of the equation we need to prove) if we could show that:

```dhall
-- Symbolic derivation.
λ(T : Type) → λ(pt : P T) → packP T pt U u  ≡  u
```

Substitute the definition of `packP` and get:

```dhall
-- Symbolic derivation.
λ(T : Type) → λ(pt : P T) → packP T pt U u
  ≡  λ(T : Type) → λ(pt : P T) → u T pt
```

Because `u` is a function of type `∀(T : Type) → P T → U`, the code of `u` has the form `λ(T : Type) → λ(pt : P T) → ...`.

So, the function `λ(T : Type) → λ(pt : P T) → u T pt` is the same as just `u`.

(This is a special case of the general fact that the expression `λ(x : A) → f x` is the same function as `f`.)

Finally, we found what we needed:

```dhall
-- Symbolic derivation.
ep U (λ(T : Type) → λ(pt : P T) → packP T pt U u)
  ≡  ep U (λ(T : Type) → λ(pt : P T) → u T pt)
  ≡  ep U u
```

This completes the proof that `ep ExistsP packP U u ≡ ep U u`.

### Existential types: Function extension rule

To simplify the code, we still keep `P` fixed in this section and use the definitions `ExistsP`, `packP`, and `unpackP` shown before.

We will now show that the functions `unpackP R` and `outE R` defined in section "Functions of existential types" are inverses of each other (when the type `R` is kept fixed).
This will prove the **function extension rule** for existential types,
which claims an isomorphism between types `ExistsP → R` and `∀(T : Type) → P T → R`.
The functions `unpackP R` and `outE R` are the two directions of the isomorphism.

Begin the proof by recalling the definitions of `unpackP` and `outE`:

```dhall
let unpackP : ∀(R : Type) → (∀(T : Type) → P T → R) → Exists P → R
  = λ(R : Type) → λ(unpack_ : ∀(T : Type) → P T → R) → λ(ep : Exists P) →
    ep R unpack_

let outE : ∀(R : Type) → (Exists P → R) → ∀(T : Type) → P T → R
  = λ(R : Type) → λ(consume : Exists P → R) → λ(T : Type) → λ(pt : P T) →
    let ep : Exists P = pack P T pt
    in consume ep
```

To verify that the functions `unpackP R` and `outE R` are inverses of each other, we need to show that the compositions of these functions in both directions are identity functions.

The first direction is when we apply `unpackP R` and then `outE R`.
Take an arbitrary `k : ∀(T : Type) → P T → R` and first apply `unpackP R` to it, then `outE R`:

```dhall
-- Symbolic derivation.
outE R (unpackP R k)                 -- Use the definition of unpackP:
  ≡  outE R (λ(ep : ExistsP) → ep R k)   -- Use the definition of outE:
  ≡  λ(T : Type) → λ(pt : P T) → (λ(ep : ExistsP) → ep R k) (packP T)
```

The result is a function of type `λ(T : Type) → λ(pt : P T) → R`.
We need to show that this function is equal to `k`.
To do that, apply that function to arbitrary values `T : Type` and `pt : P T`.
The result should be equal to `k T pt`:

```dhall
-- Symbolic derivation.
outE R (unpackP R k) T pt
  ≡  (λ(ep : ExistsP) → ep R k) (packP T)
  ≡  (packP T) R k  -- Use the definition of packP:
  ≡  (λ(R : Type) → λ(pack_ : ∀(T_ : Type) → P T_ → R) → pack_ T pt) R k
  ≡  k T pt
```

This proves the first direction of the isomorphism.

The other direction is when we first apply `outE` and then `unpackP`.
It will be more convenient to denote the fixed type by `S` in this derivation (because we will need to apply the naturality law of `ep`, and the type parameter denoted by `R` is already used in that law).

Take an arbitrary value `consume : ExistsP → S` and first apply `outE S` to it, then `unpackP S`:

```dhall
-- Symbolic derivation.
unpackP S (outE S consume)
  ≡  unpackP S (λ(T : Type) → λ(pt : P T) → consume (packP T))
  ≡  λ(ep : ExistsP) → ep S (λ(T : Type) → λ(pt : P T) → consume (packP T))
```

The result is a function of type `ExistsP → S`.
We need to show that this function is equal to `consume`.

Apply that function to an arbitrary value `ep : ExistsP`:

```dhall
-- Symbolic derivation.
unpackP S (outE S consume) ep
  ≡  ep S (λ(T : Type) → λ(pt : P T) → consume (packP T))
```

We need to show that the last line is equal to just `consume ep`.
We will do that in two steps.

The first step is apply the naturality law of `ep` shown in the previous subsection:

```dhall
-- Symbolic derivation.
f (ep R g) ≡ ep S (λ(T : Type) → λ(pt : P T) → f (g T pt))
```
We assign `f = consume`, `R = ExistsP`, and `g = packP` in that law and get:

```dhall
-- Symbolic derivation.
consume (ep ExistsP packP)
  ≡  ep S (λ(T : Type) → λ(pt : P T) → consume (packP T pt))
```

We wanted to show that the last line equals just `consume ep`, but instead we got the expression `consume (ep ExistsP packP)`.

The second step is to use the identity law of `pack`.
In the previous section, we derived the following form of that identity law:

```dhall
-- Symbolic derivation.
ep ExistsP packP  ≡  ep
```

It follows that `consume (ep ExistsP packP) ≡ consume ep`.

Then we get:

`consume ep  ≡  ep S (λ(T : Type) → λ(pt : P T) → consume (packP T pt))`

This concludes the proof.

### Existential types: Wadler's "surjective pairing rule"

The paper "Recursive types for free" mentions a "surjective pairing rule" that we will now formulate and prove for existential types of the form `ExistsP`:

For any value `ep : ExistsP`, any type `S`, and any function `h : ExistsP → S`, the following equation holds:

`h ep = ep S (λ(T : Type) → λ(pt : P T) → h (packP T pt))`

Proof: After setting `h = consume`, this equation is the same as the last line in the proof in the previous section.

This property allows us to express a function application `h ep` through an application of `h` to a value explicitly constructed via `packP`.

This does _not_ mean that `packP` constructs all possible values of type `ExistsP` (i.e., that `packP` is surjective as a function from `T : Type` and `pt : P T` to `ExistsP`).
We cannot prove _that_ property.

The meaning of the "surjective pairing rule" is a weaker statement: if a function `h : ExistsP → S` describes some property (call it an "h-property") then the h-property of arbitrary `ep : ExistsP` can be expressed through the h-property of values constructed via `packP`.

#### Encoding Wadler's notation in Dhall

Wadler's paper "Recursive types for free" uses a special notation for existential types.
That notation can be encoded in Dhall as follows:

```dhall
-- Symbolic derivation.
ExistsP      -- Wadler's existential type ∃X. P X
packP X y    -- Wadler's constructor: (X, y)
t W (λ(T : Type) → λ(pt : P T) → w)  -- Wadler's eliminator: (case t of {(X, y) → w}) : W
```

With this notation, consider Wadler's "surjective pairing rule", which he writes as:

`h t == case t of {(X, y) → h(X, y)}`

This is translated into Dhall as:

`h t ≡ t S (λ(X : Type) → λ(y : P X) → h (packP X y))`

After renaming `t = ep`, this is the same equation we proved above.

### Church encodings of greatest fixpoints: Proofs

In this section, we will prove some general properties of co-inductive types, such as `GFix F` defined in the chapter "Co-inductive types".
In particular, we will prove that `GFix F` is indeed the greatest fixpoint of the type equation `C = F C`.

For simplicity, we will assume that `F` is a covariant type constructor with one argument and a given `Functor` evidence value.
An example of such an `F` could be:

```dhall
let F = Optional
let functorF : Functor Optional = { fmap = https://prelude.dhall-lang.org/Optional/map }
```

To make the derivations shorter, we will consider `F` as a fixed functor and denote `fixf = fixG F functorF` and `unfixf = unfixG F functorF`.
(The functions `fixG` and `unfixG` were defined in the section "The fixpoint isomorphism", chapter "Co-inductive types".)
We can then simplify the code of those functions, assuming that `F` and `functorF` are given and fixed.
We will also denote the type `GFix F` simply by `G`.
We will then transform the type signatures to use curried arguments, eliminating the record type `{ seed : t, step : t → F t }`.

Here is a summary of the resulting definitions:

```dhall
let PackTo = λ(r : Type) → ∀(t : Type) → (t → F t) → t → r  -- Define PackTo for brevity.
let G = ∀(r : Type) → PackTo r → r
let unfold : PackTo G
  = λ(t : Type) → λ(c : t → F t) → λ(y : t) →
    λ(r : Type) → λ(pack_ : PackTo r) → pack_ t c y
let unfoldF : PackTo (F G)
  = λ(t : Type) → λ(c : t → F t) → λ(y : t) →
    functorF.fmap t G (unfold t c) (c y)
let unfixf : G → F G = λ(g : G) → g (F G) unfoldF
let fmap_unfixf = functorF.fmap G (F G) unfixf
let fixf : F G → G
  = unfold (F G) fmap_unfixf
```

Below we will need to use the relational naturality law of `unfold`.
That law will be applied in the following form:

For any types `R`, `S`, for any functions `f : R → S`, `cR : R → F R`, `cS : S → F S`:
if `cS (f x) ≡ functorF.fmap R S f (cR x)` for all `x : R` then `unfold R cR y ≡ unfold S cS (f y)` for all `y : R`.

###### Statement 1 (extensional surjectivity of `unfold`)

For any value `g : G`, for any type `S`, for any function `h : G → S`, we will have:

`h g = g S (λ(R : Type) → λ(cR : R → F R) → λ(x : R) → h (unfold R cR x))`

The name "extensional surjectivity" is used here to make it clear that we are not proving the ordinary surjectivity for `unfold`.
The ordinary meaning of surjectivity for `unfold` would hold if any value `g : G` can be expressed as `g = unfold R cR x` with a suitable type `R` and suitable values `cR : R → F R` and `x : R`.
We are not able to prove that here.

Instead, this statement claims a weaker property: for any function `h : G → S`, the function application `h g` can be computed if we know how to compute the function application `h (unfold R cR x)` for arbitrary types `R` and arbitrary values `cR : R → F R` and `x : R`.


####### Proof

Apply Wadler's "surjective pairing rule" to the type `GFix F` and obtain the following property:

For any `t : GFix F`, for any type `S`, for any `h : GFix F → S`, we have:

`h t ≡ t S (λ(X : Type) → λ(y : { step : X → F X, seed : X }) → h (pack (GF_T F) X y))`

Now we pass from `GFix F` to the equivalent type `G` and from `pack` to the equivalent function `unfold` by currying the arguments.
Then we will obtain directly the equation we need for the extensional surjectivity of `unfold`.

###### Statement 2

Given any type `R` and any function `rfr : R → F R`, define the function `r2g` by:

`let r2g : R → G = λ(x : R) → unfold R rfr x`

or more concisely:

`let r2g : R → G = unfold R rfr`

Then the function `r2g` satisfies the following law: for any `r : R`,

`unfixf (r2g r) ≡ functorF.fmap R G r2g (rfr r)`

or equivalently:

`unfixf (unfold R rfr r) ≡ functorF.fmap R G (unfold R rfr) (rfr r)`


In category theory, that law is known as the "$F$-coalgebra morphism law".
Functions that satisfy that law are called **$F$-coalgebra morphisms**.

So, we claim that `r2g` is always an $F$-coalgebra morphism.

####### Proof

Begin with the expression `unfixf (unfold R rfr r)`:

```dhall
-- Symbolic derivation.
unfixf (unfold R rfr r)              -- Use definition of unfixf:
 ≡ unfold R rfr r (F G) unfoldF    -- Use definition of unfold:
 ≡ unfoldF R rfr r                 -- Use definition of unfoldF:
 ≡ functorF.fmap R G (λ(x : R) → unfold R rfr x) (rfr r)
```
Rewrite the right-hand side of the equation we needed to prove:
```dhall
-- Symbolic derivation.
functorF.fmap R G (unfold R rfr) (rfr r)
 ≡ functorF.fmap R G (λ(x : R) → unfold R rfr x) (rfr r)
```

The two sides are now equal.

###### Statement 3

The construction in Statement 2 may be used with `R = G` and `rfr = unfixf`.
Then the corresponding function `r2g` will be an identity function of type `G → G`,
as long as `unfold` satisfies its relational naturality law.
We can write that property as:

`unfold G unfixf ≡ identity G`

In other words, for any value `g : G` we will have:

`g ≡ unfold G unfixf g`


####### Proof

For brevity, denote `v = unfold G unfixf`. Then our goal is to prove that `v g ≡ g`.

First, we use the relational naturality law of `unfold` with `S = G`, `f = unfold R cR`, `cS = unfixf` and get:

If `unfixf (unfold R cR x) ≡ functorF.fmap R G (unfold R cR) (cR x)` for all `x : R` then `unfold R cR y ≡ unfold G unfixf (unfold R cR y)` for all `y : R`.

The precondition holds by Statement 2.
So, we have for all `y : R`:

`unfold R cR y ≡ unfold G unfixf (unfold R cR y) ≡ v (unfold R cR y)`

This is close to what we need: this equation says `k == v k` for `k = unfold R cR y`.
But we need to show `g ≡ v g` for arbitrary `g : G`.

To get around this difficulty, we use Statement 1 with `h = identity G` and get:

`g ≡ g G (λ(R : Type) → λ(cR : R → F R) → λ(y : R) → unfold R cR y)`

Now substitute what we already derived:

`unfold R cR y ≡ v (unfold R cR y)`

and get:

`g ≡ g G (λ(R : Type) → λ(cR : R → F R) → λ(y : R) → v (unfold R cR y))`

 Again use Statement 1, this time with `h = v` and `g = unfold R cR y`, to get:

```dhall
-- Symbolic derivation.
g G (λ(R : Type) → λ(cR : R → F R) → λ(y : R) → v (unfold R cR y))
  ≡ v g
```

So, we obtain `g ≡ v g` as required.


###### Statement 4

For a fixed functor `F`, the functions `fixf : F G → G` and `unfixf : G → F G` are inverses of each other.

####### Proof

We need to prove two directions of the isomorphism round-trip:

(1) For any `g : G` we will have `fixf (unfixf g) ≡ g`

(2) For any `fg : F G` we will have `unfixf (fixf fg) ≡ fg`

To prove item (1), we write out the left-hand side of its equation:

```dhall
-- Symbolic derivation. Expect this to equal just `g`.
fixf (unfixf g)
  ≡ unfold (F G) fmap_unfixf (unfixf g)
```

Then we use the relational naturality law of `unfold` with `R = G`, `S = F G`, `f = unfixf`, `cR = unfixf`, and `cS = fmap_unfixf`.
The precondition of the relational naturality law becomes:

if `cS (f x) ≡ functorF.fmap R S f (cR x)` for all `x : R` then `unfold R cR y ≡ unfold S cS (f y)` for all `y : R`.

Substituting the definitions of `cR` and `cS`, we get:

`fmap_unfixf (unfixf x) ≡ functorF.fmap G (F G) unfixf (unfixf x)`

This condition holds by definition of `fmap_unfixf`.
So, the conclusion of the law also holds: for all `g : G`,

`unfold G unfixf g ≡ unfold (F G) fmap_unfixf (unfixf g)`

The right-hand side is the same as the expression we got after expanding `fixf` in `fixf (unfixf g)`.
So, we continue our derivation:

```dhall
-- Symbolic derivation. Expect this to equal just `g`.
fixf (unfixf g)  -- Use the definition of fixf:
  ≡ unfold (F G) fmap_unfixf (unfixf g)  -- Use the relational naturality law of `unfold`:
  ≡ unfold G unfixf g  -- Use Statement 3:
  ≡ g
```

Item (1) is proved.

To prove item (2), write out the left-hand side of its equation:

```dhall
-- Symbolic derivation. Expect this to equal just `fg`.
unfixf (fixf fg)   -- Use the definition of unfixf:
  ≡ fixf fg (F G) unfoldF  -- Use the definition of fixf:
  ≡ unfold (F G) fmap_unfixf fg (F G) unfoldF  -- Use the definition of unfold:
  ≡ unfoldF (F G) fmap_unfixf fg  -- Use the definition of unfoldF:
  ≡ functorF.fmap (F G) G (unfold (F G) fmap_unfixf) (fmap_unfixf fg)
```

At this point, recognize that `unfold (F G) fmap_unfixf` is just `fixf` and simplify the last line to:

`functorF.fmap (F G) G fixf (fmap_unfixf fg)`

The last expression is the same as `fmap fixf` applied to `fmap unfixf fg`.
By `fmap`'s composition law, we have `fmap fixf . fmap unfixf ≡ fmap (fixf . unfixf)`.
We already proved in item (1) that the composition `fixf . unfixf` is an identity function (`fixf (unfixf g) == g`).
Applying `functorF.fmap` to an identity function of type `G → G` gives an identity function of type `F G → F G`.
So, the last expression is an identity function applied to `fg`, and the result is just `fg`:

`functorF.fmap (F G) G fixf (fmap_unfixf fg) ≡ fg`

This is what remained to be proved for item (2). $\square$

###### Statement 5

Given any type `R` and any function `rfr : R → F R`,
there exists only one $F$-coalgebra morphism of type `R → G`, namely the function `r2g` defined in Statement 2 as `r2g = unfold R rfr`.

####### Proof

Let `f : R → G` be any function that satisfies the $F$-coalgebra morphism law.
We need to show that `f` is then equal to `r2g` (which is defined as `unfold R rfr`).

The $F$-coalgebra morphism law of `f` says that, for any `x : R`,

`unfixf (f x) ≡ functorF.fmap R G f (rfr x)`

This equation is the same as the precondition of the relational naturality law of `unfold` with `S = G`, `cR = rfr`, and `cS = unfixf`.
So, the conclusion of that law holds: for any `x : R`,

`unfold R rfr x ≡ unfold G unfixf (f x)`

By Statement 3, we have `g ≡ unfold G unfixf g` for any `g : G`.
Use that property for `g = f x` and obtain:

`unfold R rfr x ≡ unfold G unfixf g ≡ g ≡ f x`

The left-hand side is exactly the function `r2g` from Statement 2.
So, we have proved that `r2g x ≡ f x`. The function `f` is the same as `r2g`.


### The Church-co-Yoneda identity

The following identity holds for all covariant functors `F` and `K`:

```dhall
-- Mathematical notation:  K (GFix F) ≅ ∃ A. (K A) × (A → F A)
K (GFix F)  ≅  Exists (λ(A : Type) → { seed : K A, step : A → F A })
```

This type identity (without a widely accepted name) is analogous to the Church-Yoneda identity, except for using existentially quantified types and the greatest fixpoints instead of universally quantified types and the least fixpoints.
We call this identity the **Church-co-Yoneda identity**.

For that identity to hold, we need the following requirements:

- both `F` and `K` must be lawful covariant functors (with `Functor` typeclass evidence values satisfying the functor laws)
- parametricity assumptions (equivalently, the relational naturality laws) must hold for all functions

Denote for brevity:

```dhall
let G = GFix F
let CCoY = Exists (λ(A : Type) → { seed : K A, step : A → F A })
let fmap_K = functorK.fmap
let fmap_F = functorF.fmap
```

For convenience, we redefine the types `G` and `CCoY` using curried arguments:

```dhall
let G = ∀(R : Type) → (∀(T : Type) → (T → F T) → T → R) → R
let CCoY = ∀(R : Type) → (∀(T : Type) → (T → F T) → K T → R) → R
```

To prove the Church-co-Yoneda identity, we begin by implementing the two directions of the isomorphism:

`fromCCoY : CCoY → K G`

and

`toCCoy : K G → CCoY`

The function type `CCoY → K G` can be simplified using the function extension rule for existential types:

`CCoY → K G  ≅  ∀(T : Type) → (T → F T) → K T → K G`

Then we notice the similarity between the last type and the type of `unfold`:

`unfold : ∀(T : Type) → (T → F T) → T → G`

The difference is only in a replacement of `T → G` by `K T → K G`.
We can implement that replacement via `fmap_K`.
Now we can write the code for the function `fromCCoY` as:

```dhall
let fromCCoY : CCoY → K G
  = λ(c : CCoY) →
    c (K G) (λ(T : Type) → λ(cT : T → F T) →
      fmap_K T G (unfold T cT)
    )
```

Let us define a type alias (`PackKTo`) for a type expression that we will be using often:

```dhall
let PackKTo = λ(A : Type) → ∀(T : Type) → (T → F T) → K T → A
```
Then the type of `fromCCoY` is equivalent to just `PackKTo (K G)`.

To implement `toCCoY`, we write:

```dhall
let toCCoY : K G → ∀(R : Type) → (PackKTo R) → R
  = λ(kg : K G) → λ(R : Type) → λ(p : PackKTo R) →
    p G unfixf kg
```

It remains to show that `fromCCoY` and `toCCoY` are inverses to each other.
We need to prove the two directions of the isomorphism round-trip:

(1) For any `kg : K G` we have `kg ≡ fromCCoY (toCCoY kg)`

(2) For any `c : CCoY` we have `c ≡ toCCoY (fromCCoY c)`

To prove item (1):

```dhall
-- Symbolic derivation. Expect this to equal `kg`:
fromCCoY (toCCoY kg)   -- Expand the definition of fromCCoY:
  ≡ toCCoY kg (K G) (λ(T : Type) → λ(cT : T → F T) →
      fmap_K T G (unfold T cT)
    )                  -- Expand the definition of toCCoY:
  ≡ (λ(T : Type) → λ(cT : T → F T) →
      fmap_K T G (unfold T cT)
    ) G unfixf kg      -- Apply function to arguments:
  ≡ fmap_K G G (unfold G unfixf) kg
```

Statement 3 in section "Properties of co-inductive type encodings" shows that `unfold G unfixf` is an identity function of type `G → G` (denoted by `identity G`).
So, we have:

```dhall
-- Symbolic derivation. Expect this to equal `kg`:
fromCCoY (toCCoY kg)
  ≡ fmap_K G G (unfold G unfixf) kg   -- Use Statement 3:
  ≡ fmap_K G G (identity G) kg     -- Use functor K's identity law:
  ≡ identity (K G) kg              -- Apply identity function:
  ≡ kg
```

This proves item (1).

To prove item (2), write:

```dhall
-- Symbolic derivation. Expect this to equal `c`:
toCCoY (fromCCoY c)   -- Expand the definition of fromCCoY:
  ≡ toCCoY (c (K G) (λ(T : Type) → λ(cT : T → F T) →
      fmap_K T G (unfold T cT)
    ))                -- Expand the definition of toCCoY:
  ≡ λ(R : Type) → λ(p : PackKTo R) →
    p G unfixf (c (K G) (λ(T : Type) → λ(cT : T → F T) →
      fmap_K T G (unfold T cT)
    ))
```

At this point, we need to use some laws that hold due to parametricity assumptions.

The first required law is the naturality law for values `c : CCoY`.
That law says that for any types `Q`, `S`, for any function `f : Q → S`, for any value `q : ∀(T : Type) → (T → F T) → K T → Q`:

`f (c Q q) = c S (λ(T : Type) → λ(cT : T → F T) → λ(kt : K T) → f (q T cT kt))`

Apply that law to the last expression, setting `Q = K G`, `S = R`, `f = p G unfixf`, and
`q = λ(T : Type) → λ(cT : T → F T) → fmap_K T G (unfold T cT)`.
Then we get:

```dhall
-- Symbolic derivation.
p G unfixf (c (K G) (λ(T : Type) → λ(cT : T → F T) → fmap_K T G (unfold T cT)
  ≡ f (c Q q)
  ≡ c S (λ(T : Type) → λ(cT : T → F T) → λ(kt : K T) → f (q T cT kt))
  ≡ c R (λ(T : Type) → λ(cT : T → F T) → λ(kt : K T) → p G unfixf (fmap_K T G (unfold T cT) kt))
```

The next step is to simplify the sub-expression `p G unfixf (fmap_K T G (unfold T cT) kt)`.
For that, we use the strong dinaturality law for values `p : PackKTo R`.
That law says: for any types `T`, `U`, for any values `h : T → U`, `kt : K T`, `cT : T → F T`, and `cU : U → F U`, if the precondition holds:

`fmap_F T U h (cT x) ≡ cU (h x)` for all `x : T`,

then the conclusion holds:

`p T cT kt ≡ p U cU (fmap_K T U h kt)`

We need to set the parameters in the law to match the right-hand side of the last equation:


```dhall
-- Symbolic derivation. We will match:
p U cU (fmap_K T U h kt)
-- with:
p G unfixf (fmap_K T G (unfold T cT) kt)
-- if we set U = G, cU = unfixf, and h = unfold T cT.
```
With these parameters, we get:

```dhall
-- Symbolic derivation.
p G unfixf (fmap_K T G (unfold T cT) kt)
  ≡ p U cU (fmap_K T U h kt)
  ≡ p T cT kt
```
as long as the precondition of the law holds:

`fmap_F T G (unfold T cT) (cT x) ≡ unfixf (unfold T cT x)`

This equation (after setting `R = T` and `rfr = cT`) was derived in Statement 2 in the section "Properties of co-inductive types".

This allows us to complete the proof of item 2:

```dhall
-- Symbolic derivation. Expect this to equal `c`.
toCCoY (fromCCoY c)  -- Expand definitions of toCCoY and fromCCoY:
  ≡ λ(R : Type) → λ(p : PackKTo R) →
    p G unfixf (c (K G) (λ(T : Type) → λ(cT : T → F T) →
      fmap_K T G (unfold T cT)
    ))    -- Use the naturality law of `c`:
  ≡ λ(R : Type) → λ(p : PackKTo R) →
    c R (λ(T : Type) → λ(cT : T → F T) → λ(kt : K T) →
      p G unfixf (fmap_K T G (unfold T cT) kt))
        -- Use the relational naturality law of `p`:
  ≡ λ(R : Type) → λ(p : PackKTo R) →
    c R (λ(T : Type) → λ(cT : T → F T) → λ(kt : K T) → p T cT kt)
       -- Unexpand function: λ T → λ cT → λ kt → p T cT kt ≡ p
  ≡ λ(R : Type) → λ(p : PackKTo R) → c R p
       -- Unexpand function: λ R → λ p → c R p ≡ c
  ≡ c
```

### Church encodings for mutually recursive fixpoints: Proofs

Suppose two types `T`, `U` are defined via **mutually recursive** definitions: each type's definition uses that type itself and also the other type.

```dhall
-- Type error: Dhall does not support recursive definitions.
let T = F T U
let U = G T U
```
Here `F` and `G` are some (covariant) bifunctors.

An example definition of `F` and `G` is:

```dhall
let F : Type → Type → Type = λ(a : Type) → λ(b : Type) → < One | Two : a | Three : b >
let G : Type → Type → Type = λ(a : Type) → λ(b : Type) →  { first : a, second : b, third : Bool }
```


Types `T` and `U` are defined as solutions of two simultaneous equations.
Solutions may be defined as least fixpoints or as greatest fixpoints of those equations, depending on what is necessary for the application code.

In this section, we will prove the Church encoding formulas for the least and the greatest fixpoints.
The least fixpoints are given by the following encodings:

```dhall
let T = ∀(a : Type) → ∀(b : Type) → (F a b → a) → (G a b → b) → a
let U = ∀(a : Type) → ∀(b : Type) → (F a b → a) → (G a b → b) → b
```

The greatest fixpoints are given by the following encodings:

```dhall
let T = Exists (λ(a : Type) → Exists (λ(b : Type) → { seed : a, stepA : a → F a b, stepB : b → G a b }))
let U = Exists (λ(a : Type) → Exists (λ(b : Type) → { seed : b, stepA : a → F a b, stepB : b → G a b }))
```

From the shape of those types, it is clear how to generalize these Church encodings to any number of mutually recursive types.

To prove these type formulas, we will need the property we call the "nested fixpoint lemma":

###### Statement 1 (nested fixpoint lemma).
Suppose `J` is any bifunctor. Then the nested fixpoint of `J x y` with respect to both `x` and `y` is equivalent to a simple fixpoint of `J x x` with respect to `x`.
That property holds both for the least fixpoints and for the greatest fixpoints:

```dhall
LFix (λ(x : Type) → LFix (λ(y : Type) → J x y))
  ≅  LFix (λ(x : Type) → J x x)
GFix (λ(x : Type) → GFix (λ(y : Type) → J x y))
  ≅  GFix (λ(x : Type) → J x x)
```


####### Proof

We will use the Church-Yoneda identity for the least fixpoints and the Church-co-Yoneda identity for the greatest fixpoints.

Begin by considering the left-hand side of the identity for the least fixpoints.
Denote for brevity by `N x` the least fixpoint of `J x y` with respect to `y`:

`N x = LFix (λ(y : Type) → J x y)`

Then the nested fixpoint can be written as:

```dhall
LFix (λ(x : Type) → LFix (λ(y : Type) → J x y))
  ≅  LFix (λ(x : Type) → N x)  =  LFix N
```

Use the Church encoding for the last expression:

```dhall
LFix N  ≅  ∀(x : Type) → (N x → x) → x
```

If we view `x` as a fixed type, we can say that the type expression `(N x → x) → x` is of the form `P (N x)` where `P` is a covariant functor defined by `P a = (a → x) → x`.
Then we may apply the Church-Yoneda identity, which gives:

```dhall
(N x → x) → x  =  P (N x)  =  P (LFix (λ(y : Type) → J x y))
  ≅  ∀(y : Type) → (J x y → y) → P y    -- Used Church-Yoneda identity.
  ≅  ∀(y : Type) → (J x y → y) → (y → x) → x
```

It remains to add the universal quantifier (`∀x`) to the last type expression.
After some rewriting, the last type expression will fit the form of the Yoneda identity:

```dhall
∀(x : Type) → ∀(y : Type) → (J x y → y) → (y → x) → x -- Swap arguments:
  ≅  ∀(x : Type) → ∀(y : Type) → (y → x) → (J x y → y) → x  -- Define R:
  ≅  ∀(x : Type) → ∀(y : Type) → (y → x) → R x        -- Swap ∀x and ∀y:
  ≅  ∀(y : Type) → ∀(x : Type) → (y → x) → R x
```
Here we defined `R a = (J a y → y) → a`.
Since `R` is a covariant functor, we may use the covariant Yoneda identity:


```dhall
∀(x : Type) → (y → x) → R x  ≅  R y
  = (J y y → y) → y
```
We can now complete the derivation for the least fixpoints:

```dhall
LFix (λ(x : Type) → N x)
  ≅  ∀(y : Type) → (J y y → y) → y    -- This is a Church encoding.
  ≅  LFix (λ(y : Type) → J y y)
```

For the greatest fixpoints, the proof is similar.
We denote for brevity by `N x` the greatest fixpoint of `J x y` with respect to `y`:

`N x = GFix (λ(y : Type) → J x y) = GFix (J x)`

Then the nested fixpoint can be written as:

```dhall
GFix (λ(x : Type) → GFix (λ(y : Type) → J x y))
  ≅  GFix (λ(x : Type) → N x)  =  GFix N
```

Use the Church encoding for the last expression:

```dhall
GFix N  ≅  Exists (λ(x : Type) → { seed : x, step : x → N x })
```

If we view `x` as a fixed type within the scope of the record, we can say that the type expression `{ seed : x, step : x → N x }` is of the form `P (N x)` where `P` is a covariant functor defined by `P a = { seed : x, step : x → a }`.
The type `P (N x)` contains a fixpoint inside the functor `P`:

```dhall
P (N x)  ≅  P (GFix (J x))
```
Here we may apply the Church-co-Yoneda identity, which gives:

```dhall
P (GFix J x)  ≅  Exists (λ(y : Type) → { seed : P y, step : y → J x y })
   ≅  Exists (λ(y : Type) → { seed : { seed : x, step : x → y }, step : y → J x y })
```

It remains to add the existential quantifier in `x` to the last type expression.
After some rewriting, the last type expression will fit the form of the co-Yoneda identity:

```dhall
Exists (λ(x : Type) → Exists (λ(y : Type) → { seed : { seed : x, step : x → y }, step : y → J x y }))    -- Rearrange record:
  ≅  Exists (λ(x : Type) → Exists (λ(y : Type) → { seed : { seed : x, step : y → J x y }, step : x → y }))
  ≅  Exists (λ(x : Type) → Exists (λ(y : Type) → { seed : R x, step : x → y }))     -- Define R.
  ≅  Exists (λ(y : Type) → Exists (λ(x : Type) → { seed : R x, step : x → y }))     -- Swap quantifiers.
```
Here we defined `R a = { seed : a, step : y → J a y }`.
Since `R` is a covariant functor, we may use the covariant co-Yoneda identity:


```dhall
Exists (λ(x : Type) → { seed : R x, step : x → y }))  ≅  R y
  = { seed : y, step : y → J y y }
```
We can now complete the derivation for the greatest fixpoints:

```dhall
GFix (λ(x : Type) → N x)
  ≅  Exists (λ(y : Type) →  { seed : y, step : y → J y y })   -- This is a Church encoding.
  ≅  GFix (λ(y : Type) → J y y)
```
$\square$

Now we begin the proof of the Church encodings for mutually recursive types.

The proofs in both cases (least and greatest fixpoints) have many similar steps.
The first step is to express `U` via `T` and to derive a fixpoint equation for `T` alone.
We already know how to encode fixpoints defining a single recursive type, and we will use those encodings to express `T`.
Then we will use the Church-Yoneda identity (for least fixpoints) and the Church-co-Yoneda identity (for greatest fixpoints) to show that the Church encodings of `T` are equivalent to the type formulas given above.
The derivation for `U` will be exactly similar and will be omitted.


Let us first consider the greatest fixpoints.
Rewrite the equations `T = F T U` and `U = G T U` as:

```dhall
-- Type error: Dhall does not support recursive definitions.
T = GFix (λ(x : Type) → F x U)
U = GFix (λ(y : Type) → G T y)
```

We would like to derive a fixpoint equation for `T` alone, instead of having two mutually dependent equations.
We notice that the last equation expresses `U` via `T`.
It will be convenient to write that expression as `U = H T` where the functor `H` is defined by:

```dhall
let H = λ(x : Type) → GFix (λ(y : Type) → G x y)
```

The type constructor `λ(y : Type) → G a y` is an expanded form of the curried type constructor `G a`.
So, we may define `H` more concisely as:
```dhall
let H = λ(x : Type) → GFix (G x)
```

As `U = H T`, we can derive a fixpoint equation that contains just `T` and no `U`:

```dhall
-- Symbolic derivation.
T ≡ GFix (λ(x : Type) → F x U)
  ≡ GFix (λ(x : Type) → F x (H T))
```
This is a fixpoint equation for `T` alone. The solution can be written as:

```dhall
let T = GFix (λ(t : Type) → GFix (λ(x : Type) → F x (H t)))
```
To see the structure of the last equation more clearly, let us define the type constructor `J` by `J x y = F x (H y)`, or in Dhall:

```dhall
let J = λ(x : Type) → λ(y : Type) → F x (H y)
```
Then `T` is expressed as:

```dhall
let T = GFix (λ(t : Type) → GFix (λ(x : Type) → J x t))
```

The bifunctor `J x y = F x (H y)` is covariant in both `x` and `y`.
So, we may use the nested fixpoint lemma to conclude that `T` is the greatest fixpoint of `J x x` with respect to `x` alone:

```dhall
let T = GFix (λ(x : Type) → J x x)
  -- Or equivalently:
let H = λ(x : Type) → GFix (G x)
in let T = GFix (λ(x : Type) → F x (H x))
```

For the case of least fixpoints, the argument is similar. The resulting definitions for `H` and `T` are:

```dhall
let H = λ(x : Type) → LFix (G x)
in let T = LFix (λ(x : Type) → F x (H x))
```

We have eliminated `U` and obtained a fixpoint equation for `T` alone.
Now we use the known Church encodings:

```dhall
-- For the greatest fixpoints:
let T = Exists (λ(x : Type) → { seed : x, step : x → F x (GFix (G x)) })
-- Written out in full:
let T = Exists (λ(x : Type) → { seed : x, step : x → F x (Exists (λ(y : Type) → { seed : y , step : y → G x y })) })
-- For the least fixpoints:
let T = ∀(x : Type) → (F x (LFix (G x)) → x) → x
-- Written out in full:
let T = ∀(x : Type) → (F x (∀(y : Type) → (G x y → y) → y) → x) → x
```

Note that both type formulas involve type quantifiers _inside_ functors:
For the greatest fixpoints, `T` has the form `T = Exists ( ... F x (Exists ...))`.
For the least fixpoints, `T` has the form `T = ∀x( ... F x (∀y ...))`.

Our goal is to derive the type formulas we started with:

```dhall
-- For the greatest fixpoints:
let T = Exists (λ(a : Type) → Exists (λ(b : Type) → { seed : a, stepA : a → F a b, stepB : b → G a b }))
-- For the least fixpoints:
let T = ∀(a : Type) → ∀(b : Type) → (F a b → a) → (G a b → b) → a
```
These formulas are simpler because all type quantifiers are outside.
To achieve that simplification, we will need to use the Church-Yoneda and the Church-co-Yoneda identities.
Those identities say that a type with a fixpoint inside a functor is equivalent to a type whose quantifier is outside.
For the greatest fixpoints, we will apply the Church-co-Yoneda identity, and for the least fixpoints, we will apply the Church-Yoneda identity.
It remains to bring the type expressions for `T` into the form suitable for applying those identities.

First consider the case of the greatest fixpoints.
Write the type expression for `T` that we last obtained:

```dhall
let T = Exists (λ(x : Type) → { seed : x, step : x → F x (GFix (G x)) })
```

The Church-co-Yoneda identity says that, for any functors `P` and `Q`,

`P (GFix Q)  ≅  Exists (λ(A : Type) → { seed : P A, step : A → Q A })`

The left-hand side of this formula will match the type expression for `T` if we consider `x` to be a fixed type and set `P a = { seed: x, step: x → F x a }` and `Q a = G x a`.
With these definitions, `P` and `Q` are covariant functors.
Then we may use the Church-co-Yoneda identity to obtain:

```dhall
-- Symbolic derivation.
T = Exists (λ(x : Type) → { seed : x, step : x → F x (GFix (G x)) })
  ≡ Exists (λ(x : Type) → P (GFix Q))
  ≡ Exists (λ(x : Type) → Exists (λ(A : Type) → { seed : P A, step : A → Q A }))
-- Rename A to y and expand the definitions of P and Q:
  ≡ Exists (λ(x : Type) → Exists (λ(y : Type) → { seed : { seed: x, step: x → F x y }, step : y → G x y }))
```

It remains to replace the record type `{ seed : { seed: x, step: x → F x y }, step : y → G x y }` by an equivalent record type `{ seed : x, stepA : x → F x y, stepB : y → G x y }`.
Then we get the required type formula for `T`:

```dhall
-- Symbolic derivation.
T ≡ Exists (λ(x : Type) → Exists (λ(y : Type) → { seed : x, stepA : x → F x y, stepB : y → G x y }))
```

This concludes the proof for the greatest fixpoints.

For the least fixpoints, we recall the last obtained type expression for `T`:

```dhall
let T = ∀(x : Type) → (F x (LFix (G x)) → x) → x
```

The Church-Yoneda identity says that, for any functors `P` and `Q`:

`P (LFix Q)  ≅  ∀(y : Type) → (Q y → y) → P y`

The left-hand side of this formula will match the type expression for `T` if we consider `x` to be a fixed type and set `P a = (F x a → x) → x` and `Q a = G x a`.
Defined in that way, both `P` and `Q` are covariant functors.
Then we may apply the Church-Yoneda identity to obtain:

```dhall
-- Symbolic derivation.
let T = ∀(x : Type) → (F x (LFix (G x)) → x) → x
  ≡ ∀(x : Type) → P (Lfix Q)
  ≡ ∀(x : Type) → ∀(y : Type) → (Q y → y) → P y
  ≡ ∀(x : Type) → ∀(y : Type) → (G x y → y) → (F x y → x) → x
```

This is equivalent to the type expression we wanted to derive:

```dhall
let T = ∀(a : Type) → ∀(b : Type) → (F a b → a) → (G a b → b) → a
```

This concludes the proof for the least fixpoints.

### Summary of type equivalence identities

Here are some of the type identities we have proved in this Appendix.

All those identities hold under assumptions of parametricity.

We show the identities both in the Dhall syntax and in a standard mathematical notation.

Function extension rule (for any type constructor `P`):

```dhall
Exists P → R  ≅  ∀(T : Type) → P T → R
```
$$ (\exists A.~P~A) \to R \cong \forall T.~P~T\to R $$

The "nested fixpoint lemma" (for any covariant bifunctor `J`):

```dhall
LFix(λ(x : Type) → LFix(λ(y : Type) → J x y))  ≅  LFix(λ(x : Type) → J x x)
GFix(λ(x : Type) → GFix(λ(y : Type) → J x y))  ≅  GFix(λ(x : Type) → J x x)
```
$$ \mu x.~\mu y.~J~x~y \cong \mu x.~J~x~x $$
$$ \nu x.~\nu y.~J~x~y \cong \nu x.~J~x~x $$

Yoneda identity (for a covariant functor `Q`):

```dhall
∀(x : Type) → (a → x) → Q x  ≅  Q a
```
$$ \forall x.~(a \to x)\to Q~x \cong Q~a $$

Yoneda identity (for a contravariant functor `Q`):

```dhall
∀(x : Type) → (x → a) → Q x  ≅  Q a
```
$$ \forall x.~(x \to a)\to Q~x \cong Q~a $$

Church encoding of least fixpoints (for a covariant functor `P`):

```dhall
∀(x : Type) → (P x → x) → x  ≅  LFix P
```
$$ \forall x.~(P~x \to x)\to x \cong \mu x.~P~x $$

Existence of least fixpoints:

```dhall
LFix P  ≅  <>  {- if and only if: -} P <>  ≅  <>
```
$$ \forall x.~(P~x \to x)\to x \cong 0  ~~\Leftrightarrow~~  P ~0 \cong 0 $$

Church encoding for mutually recursive least fixpoints (for covariant bifunctors `F`, `G`):


```dhall
-- Two fixpoint equations: U = F U V, V = G U V
U  ≅  ∀(x : Type) → ∀(y : Type) → (F x y → x) → (G x y → y) → x
V  ≅  ∀(x : Type) → ∀(y : Type) → (F x y → x) → (G x y → y) → y
```
$$ U \cong \forall x.~\forall y.~(F~x~y \to x)\to (G~x~y \to y)\to x  $$
$$ V \cong \forall x.~\forall y.~(F~x~y \to x)\to (G~x~y \to y)\to y  $$

Church-Yoneda identity (for covariant functors `P` and `Q`):

```dhall
∀(x : Type) → (P x → x) → Q x  ≅  Q (LFix P)
```
$$ \forall x.~(P~x \to x)\to Q~x \cong Q(\mu x.~P~x) $$

Church encoding of greatest fixpoints (for a covariant functor `P`):

```dhall
Exists (λ(a : Type) → { seed : a, step : a → P a })  ≅  GFix P
```
$$ \exists a.~a \times (a\to P~a) \cong \nu x.~P~x $$

Church encoding of mutually recursive greatest fixpoints (for covariant bifunctors `F`, `G`):

```dhall
-- Two fixpoint equations: U = F U V, V = G U V
U  ≅  Exists (λ(a : Type) → Exists (λ(b : Type) → { seed : a, stepA : a → F a b, stepB: b → G a b }))
V  ≅  Exists (λ(a : Type) → Exists (λ(b : Type) → { seed : b, stepA : a → F a b, stepB: b → G a b }))
```
$$ U \cong \exists a.~\exists b.~a \times (a\to F~a~b)\times (b\to G~a~b)  $$
$$ V \cong \exists a.~\exists b.~b \times (a\to F~a~b)\times (b\to G~a~b)  $$

Co-Yoneda identity (for a covariant functor `Q`):

```dhall
Exists (λ(a : Type) → { seed : Q a, step : a → r })  ≅  Q a
```
$$ \exists a.~Q~a \times (a\to r) \cong Q~a $$

Co-Yoneda identity (for a contravariant functor `Q`):

```dhall
Exists (λ(a : Type) → { seed : Q a, step : r → a })  ≅  Q a
```
$$ \exists a.~Q~a \times (r\to a) \cong Q~a $$

Church-co-Yoneda identity (for covariant functors `P` and `Q`):

```dhall
Exists (λ(a : Type) → { seed : Q a, step : a → P a })  ≅  Q (GFix P)
```
$$ \exists a.~Q~a \times (a\to P~a) \cong Q (\nu x.~P~x) $$
