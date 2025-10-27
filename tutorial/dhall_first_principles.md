# Functional programming from first principles

We will derive a functional programming language similar to Dhall by following certain principles of language design motivated by mathematical experience.

## The principles 

- A program is a (large) formula. Large formulas are built up by combining smaller formulas.
- Every changeable part of anything is a value.
- Values may be assigned to names, and then we should be able to use those names instead.
- Whenever a value is used, we should be able to write a function that substitutes that value.
- Each value has a certain type, and all types must match. Formulas with mismatched types are rejected.

Let us see where these principles lead.

## Step 1: Literal values, operations, variables 

### Literal numbers
We expect the language to handle natural numbers (`0`, `1`, ...) and operations with them (`+` and `*`).

### Operations on numbers

We expect this to get evaluated to `610`:

```dhall
10 + 20 * 30
```

- We expect this to be a complete Dhall program, because programs are formulas.

- We expect this to be a value. We expect `10` and `20 * 30` also to be values.

### Literal lists

Dhall writes lists like this:

```dhall
[1, 2, 3]
```

The operation `#` concatenates lists. For example, `[1, 2, 3] # [10, 20]` evaluates to the list `[1, 2, 3, 10, 20]`. 

### Variable assignments

- Because all values should be able to get assigned to names, we need syntax for that.

The syntax must be able to specify that, say, "in whatever program that follows, the name `x` will stand for the expression `10 + 20 * 30`".

Dhall has the following syntax for that:

```dhall
let x = 10 + 20 * 30  in  <rest_of_program>
```

- This is a Dhall program (as long as `rest_of_program` is also a Dhall program).
- This is a value (as long as `rest_of_program` is also a valid value).

A simple example:

```dhall
let x = 10 + 20 * 30   in   x * x * x
```

This program evaluates to `226981000`:

```dhall
⊢ let x = 10 + 20 * 30   in   x * x * x

226981000
```

We usually say that we have defined a "variable" `x`, although `x` is actually a constant and can never change within the program that follows.

### Alternative syntax

The syntax `let a = b in c` is certainly not the only one possible. Another syntax could be just `a = b; c` say.

That sort of syntax is similar to what is used in Scala. However, Scala requires a `val` keyword and braces as delimiters to make the syntax unambiguous:

```scala
{
  val x = 10 + 20 * 30
  x * x * x
}
```

It is not very important what syntax we use. It is important that the entire code is a single formula that is evaluated to a single value. The syntax "`let a = b in c`" emphasizes this better than the syntax "`a = b; c`".

### Multiple variables

We will usually separate variable assignments vertically in Dhall:

```dhall
let x = 10 + 20 * 30
in x * x * x
```

- Because the above is a valid program, we should be able to add more variable definitions to it:

```dhall
let y = 2
let x = 10 + 20 * 30
in x + y
```

In Dhall, the syntax `let a = b let c = d in e` is the same as the longer `let a = b in let c = d in e`.

It follows that Dhall programs must have this form: `let` ... `let` ... `in` ...

There is nothing else we can write, because the entire program must be a single formula.

### Nested variable definitions

- Because we should be able to use values anywhere, we need to be able to *define and use* a new variable, say, at the place of `20` in the expression `10 + 20 * 30`.

For example, we want to write `let p = 10 in p + p` instead of `20`. We should get the same results. But how to write that kind of programs? We need a syntax for that.
We cannot just write `10 + let p = 10 in p + p * 30`: we need to separate the end of the sub-expression from `* 30`.

In Dhall, we use parentheses to separate sub-expressions, like in mathematics. We can write `10 + (20) * 30`, and it's the same as `10 + 20 * 30`.

So, we write:

```dhall
10 + (let p = 10 in p + p) * 30
```

In this way, we can write `let` inside expressions at any place.

### Types

It is invalid to add a number to a list with a `+`. Let us write an artificial example where we use types incorrectly:

```dhall
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

```dhall
let x : Natural = 10
let y : Natural = [20, 30]
in x + y 

Error: Expression doesn't match annotation
```

Now we got the error about `y` at the point where we annotated its type.

In this artificial example, it might be obvious that `y = [20, 30]` does not have type `Natural`.
But when `y` is computed in a different part of the program, we might forget about its type more easily. It is helpful to get the type errors early. 

The Dhall syntax for type annotations is `x : <type>`. This is also the syntax convention in several other languages. (Haskell uses `::` instead of `:`.)

Because the separator symbol `:` is not used in any other way, we may write type annotations at any place:

```dhall
(10 + (20 : Natural) * 30) : Natural
```

Some programming languages do not use a special separator for type annotations: for example, C++ and Java write `int x` to denote that `x` has integer type. Because of this syntax, C++ and Java programs cannot support adding a type annotation at any place within an expression.

Type annotations are not mandated by the principles of functional programming; in many cases, programs can be written without any type annotations. The compiler of OCaml has a powerful algorithm that can figure out all types even without a single type annotation. However, when programming entirely without type annotations, one finds that error messages often become harder to understand: the compiler assumes that the code is correct and infers the corresponding types, then prints an error that types do not match. But in practice, most often the error is in the code, not in the types.

## Step 2: Functions

### Built-in functions

At this point we are able to compute simple arithmetic expressions.
It is certainly useful to add more built-in functions to the language.
For example, Dhall has a built-in function `Natural/subtract`:

```dhall
Natural/subtract 5 10
```

In the Dhall syntax, functions are applied to arguments without parentheses. If `f` is a function, we can just write `f 123` instead of `f(123)`.

- Because expressions can be used anywhere, `(123)` and `123` is always the same.

So, `f(123)` and `f 123` is actually the same thing in Dhall.

This kind of syntax is not often used in mathematics; usually, mathematical functions are written with parentheses, such as `f(x)`.

Some programming languages allow functions that return other functions; then the syntax becomes something like `f(x)(y)(z)`. In this expression, `f` is a function such that `f(x)` is again a function, which can be applied to `y` and then gives another function that can be applied to `z`.

In Dhall, parentheses are written only when needed to separate sub-expressions. One can write `f(x)(y)(z)` in Dhall, but it will be easier to write `f x y z`.

In Dhall, the function `Natural/subtract x y` takes one argument `x` and returns another function, which takes another argument `y` and then returns `y - x` if both `x` and `y` are natural numbers.

### User-defined functions

- Whenever we use a value, we should be able to write a function that substitutes that value.

Applying this principle to the example expression `10 + 20 * 30`, we may say that we should be able to write a function that substitutes the value `20` in this function by some other value.

The language should allow us to specify that we have a function that takes an argument, say, `n` (which must be a natural value) and puts that `x` instead of  `20` in the expression `10 + 20 * 30`.

The Dhall syntax for that function looks like this:

```dhall
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

```dhall
let f = λ(n : Natural) → 10 + n * 30
in f 2
```

This program defines `f` as a function and then applies the function `f` to the value `2`. The resulting program is evaluated to `70`.

- We should be able to write a function that substitutes any part of anything.

For example, in the program shown above, we should be able to substitute some other number instead of `10`. How can we do that?

We should be able to do that in the same way as in every case: We write a function, introduce a new symbolic parameter, and put that parameter into the expression at the right place. The language must be able to figure out correctly what to do.

```dhall
λ(p : Natural) →
  let f = λ(n : Natural) → p + n * 30
  in f 2
```

Line indentations are _not_ significant in Dhall.

### Curried functions

### Variable capture in functions

## Function types

### Types of built-in and user-defined functions

### Types of higher-order functions

## Type parameters

### Types and kinds
