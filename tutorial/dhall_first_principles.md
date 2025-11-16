# Functional programming from first principles

We will derive a functional programming language similar to Dhall by following certain principles of language design motivated by mathematical experience.

## The principles of functional programming

- A program is a (large) expression built up by combining smaller expressions. Any changeable part of a program is an expression.
- Any expression can be replaced by another expression.
- In particular, any expression can be replaced by a named constant assigned separately to the same expression.
- That named constant can be also made into a parameter of a function. 
- Every expression has a certain type. Expressions with mismatched types are rejected.
- Every expression may be replaced by another expression that evaluates to the same result. The program's result will not change.

Let us see where these principles lead.

## Step 1: Literal values, operations, variables 

### Literal numbers
We expect the language to handle natural numbers (`0`, `1`, ...) and operations with them (`+` and `*`).

### Operations on numbers

We expect this to get evaluated to `610`:

```dhall
10 + 20 * 30
```

- We expect this to be a complete Dhall program, because programs are expressions.

- We expect `10` and `20 * 30` also to be expressions.

### Literal lists

Dhall writes lists like this:

```dhall
[1, 2, 3]
```

The operation `#` concatenates lists. For example, `[1, 2, 3] # [10, 20]` evaluates to the list `[1, 2, 3, 10, 20]`. 

### Variable assignments

- Any part of a program should replaceable by a named constant. We need some syntax for that.

The syntax must be able to specify that, say, "in whatever program that follows, the name `x` will stand for the expression `10 + 20 * 30`".

Dhall has the following syntax:

```dhall
let x = 10 + 20 * 30  in  <rest_of_program>
```

- This is a Dhall program (as long as `rest_of_program` is also a Dhall program).
- This is an expression (as long as `rest_of_program` is also a valid expression).

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

It is not very important what syntax we use. It is important that the entire code is a single expression that is evaluated to a result value (or a result expression). The syntax "`let a = b in c`" emphasizes this better than the syntax "`a = b; c`".

### Multiple variables

We will usually separate variable assignments vertically in Dhall:

```dhall
let x = 10 + 20 * 30
in x * x * x
```

- Because the above is a valid program, we should be able to add more "let" definitions to it:

```dhall
let y = 2
let x = 10 + 20 * 30
in x + y
```

In Dhall, the syntax `let a = b let c = d in e` is the same as the longer `let a = b in let c = d in e`.

It follows that Dhall programs must have this form: `let` ... `let` ... `in` ...

There is nothing else we can write, because the entire program must be a single expression.

### Nested variable definitions

- Any part of an expression can be replaced by another expression.

For example, we should be able to replace `20` in the expression `10 + 20 * 30` by an expression that defines and uses a new variable.

Suppose we want to write `let p = 10 in p + p` instead of `20`. We should get the same results. But how to write that kind of programs? We need a syntax for that.
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

Type annotations are not mandated by the principles of functional programming; in many cases, programs can be written without any type annotations.
For example, the compiler of OCaml has a powerful algorithm that can figure out all types even without a single type annotation.

However, when programming entirely without type annotations, one finds that error messages often become harder to understand.
When code is subtly wrong, the compiler assumes that the code is correct and infers the corresponding types, which are actually incorrect.
Then the compiler reports an error that types do not match somewhere else in the code.

Such errors would be reported at the right place if the code had type annotations.
In practice, the error is more often in the code, not in the type annotations.

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

- For any sub-expression, we should be able to write a function that substitutes that sub-expression.

Applying this principle to the example expression `10 + 20 * 30`, we may say that we should be able to write a function that substitutes the value `20` in this function by some other value.

The language should allow us to specify that we have a function that takes an argument, say, `n` (which must be a natural value) and puts that `n` instead of  `20` in the expression `10 + 20 * 30`.

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

- We should be able to write a function that substitutes any part of any expression.

For example, in the program shown above, we should be able to substitute some other number instead of `10`. How can we do that?

We should be able to do that in the same way: We just begin a new function having a new symbolic parameter. Then we put that parameter into the expression at the right place:

```dhall
λ(p : Natural) →
  let f = λ(n : Natural) → p + n * 30
  in f 2   -- Line indentations are _not_ significant in Dhall.
```
This is a function that can be applied to `10` and then evaluates to`70`.

How can we write code that applies such a function to an argument `10`? There are two ways now:

- Define a named constant for this function.
- Put that function into parentheses and apply directly.

First program:

```dhall
let q = λ(p : Natural) →
  let f = λ(n : Natural) → p + n * 30
  in f 2
in q 10
```

Second program:

```dhall
(
λ(p : Natural) →
  let f = λ(n : Natural) → p + n * 30
  in f 2
) 10
```

Both programs evaluate to `70`.

### Equivalence of functions and "let" expressions

We can see that the language has two ways of doing the same thing.
Indeed, "let" expressions can be rewritten via functions:

`let x : Natural = a in y`

is the same as:

`(λ(x : Natural) → y) a`

If a language has functions but does not have "let" expressions, one could just write programs using functions.
It looks more visual to write with "let" expressions but it's equivalent.

(Below we will see one exception where it is _not_ equivalent in Dhall to replace a "let" expression with a function, but it is a rare corner case involving type aliases.)

### Curried functions

- A function should be able to substitute values in any expression.

So, a function could substitute values in another function.
This means we have a function that _returns another function_.

Begin by writing just a simple function:

```dhall
λ(p : Natural) → p * 2 + 123
```
We would like to replace `123` in this function by an arbitrary value `n`.
So, we write a function whose parameter is `n` and whose return value is the above function with `n` instead of `123`.

```dhall
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
The syntax `f 123 456` requires some time to get used to. Functions `f` that can be used in this way are known as **curried functions**.

We see that the existence of curried functions is not a special feature of the language, but a necessary consequence of the principle that we should be able to refactor any expression into a function that will substitute a given part of that expression.

### Value capture in functions

A curried function may return a function whose body includes a "captured" parameter. We have seen this in the code above:

```dhall
⊢ let f = λ(n : Natural) → λ(p : Natural) → p * 2 + n  in  f 123

λ(p : Natural) → p * 2 + 123
```
The parameter `n` was set to `123`, which is "captured" in the new function body.

If we are working inside a function where some more parameters are defined and then `f` is called on a parameter then the resulting function will "capture" that parameter:

```dhall
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

### Higher-order functions

The language should allow us to write a function that replaces a given sub-expression by an argument value.
That sub-expression could be itself a function expression.
In that case, we need to be able to write a function whose argument is itself a function.

Consider the function `f` defined above. It contains `λ(p : Natural) → p * 2 + n` as a sub-expression.
Suppose we need to replace the computation `p * 2` by another function of `p`.

To express our intention more clearly, let us rewrite `f 123`, making this computation explicit:

```dhall
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

## Function types

- Every expression must have a type.

So, functions must have types, and the language must allow us to write those types.

The simplest syntax for function types looks like this: `Natural → Natural`.
It means a function that takes an argument of type `Natural` and returns a result also of type `Natural`.

We can check that a function has the type we expect:

```dhall
( λ(p : Natural) → p * 2 + 123 ) : Natural → Natural
```
It is convenient when the type annotation can be written next to a defined variable:

```dhall
let g : Natural → Natural = λ(p : Natural) → p * 2 + 123
```

### Types of higher-order functions

A curried function returns a function, so its type has the form `something → (something → something)`.
An example is the function `f` defined above: its type is written as `Natural → (Natural → Natural)`.

```dhall
let f : Natural → Natural → Natural
  = λ(n : Natural) → λ(p : Natural) → p * 2 + n
```

Because curried functions are used often, most functional languages adopt the convention that the syntax for `→` associates to the right.
Then parentheses can be omitted and one writes `Natural → Natural → Natural`.
This syntax requires some getting used to.

If a function of type `Natural → Natural` is an argument of another function, parentheses are _required_ for the type of that argument.
An example is the function type `(Natural → Natural) → Natural`.

Here are some higher-order functions annotated with their types:
```dhall
let q : (Natural → Natural) → Natural = λ(f : Natural → Natural) → f 123
let r : (Natural → Natural) → Natural → Natural = λ(g : Natural → Natural) → λ(p : Natural) → g p + 123
```

All built-in functions have fixed, known types.
For example, the function `Natural/subtract` has type `Natural → Natural → Natural`.

## Type parameters

### Values, types, and kinds
