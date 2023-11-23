# Do-notation for Dhall

Following a proposal in https://discourse.dhall-lang.org/t/proposal-do-notation-syntax/99 the Scala implementation contains a version of the "do-notation".

This implementation reuses existing keywords and does not introduce any new reserved symbols.

The implemented Dhall syntax is:

```dhall
as M T in bind_function
    with x : A in p
    with y : B in q
    with z : C in r
    then s
```

This syntax is analogous to Haskell's "do notation":

```haskell
-- Haskell
result :: M T
result = do 
            x <- p
            y <- q
            z <- r
            s
```

and to Scala's "for/yield" syntax:

```scala
// Scala
val result: M[T] = for {
  x <- p
  y <- q
  z <- r
  t <- s
} yield t
```

In the Dhall example, `M : Type → Type` is any type constructor for which a "bind function" is available.

The type of `bind_function` must be `∀(a : Type) → ∀(b : Type) → M a → (a → M b) → M b`.

Similarly to the do-notation in Haskell, the expressions `p`, `q`, `r`, etc., may use variables `x : A`, `y : B`, etc., defined at any line _above_ the line where it is used.

The types of those expressions must be `p : M A`, `q : M B`, `r : M C`, and `s : M T`.

The type of the entire expression is `M T`.

Example:

```dhall
λx -> as Optional Natural in bindOptional
    with y : Natural in subtract1Optional x
    with z : Natural in subtract1Optional y
    then subtract1Optional z
```

This function has type `Natural -> Optional Natural`.

A full working test is shown [here](https://github.com/winitzki/scall/blame/master/scall-core/src/test/scala/io/chymyst/dhall/unit/SimpleSemanticsTest.scala#L77).

## Parsing the do-notation syntax

The do-notation contains the first block: `as M T in b`, then zero or more `with ... in ...` blocks, and finally a closing block `then ...`.

The type application `M T` may be in parentheses but does not need to be, as it is surrounded by keywords.

Type annotations (`x : A`) are mandatory in the `with x : A in p` blocks.

There is one more sub-rule (`expression-as-in`) in the top `expression` rule:

```abnf
expression =
            ...; "forall (x : a) -> b"
            ; do notation: "as M T in bind_function with x : A in p with y : B in q then r"
            / expression-as-in
            / ... ; a -> b
            / ...

; "as (M T) in bind_function *(with x : A in p) then r"
expression-as-in = "as" whsp1 application-expression whsp "in" whsp1 expression whsp *with-binding "then" whsp1 expression

; "with x : A in p"
with-binding = "with" whsp1 nonreserved-label whsp ":" whsp1 expression whsp "in" whsp1 expression whsp
```

The parsed sub-expressions are desugared at the parsing stage into a nested application of `bind_function`.

## Desugaring the do-notation

The do-notation is desugared at parsing stage into nested applications of `bind_function`.
Type-checking is performed at a later stage.

The desugaring algorithm is defined by:

     desugar(as M T in bind_function with x : A in p <rest of the code>)
       = bind_function A T p (λ(x : A) -> desugar(as M T in bind_function <rest of the code>))`
     
     desugar(as M T in bind_function then x) = x

After desugaring, type annotations are added to aid error detection.

     M : Type → Type
     T : Type
     bind_function : ∀(a : Type) → ∀(b : Type) → M a → (a → M b) → M b
     p : M A
     desugar(as M T in bind_function ...): M T
