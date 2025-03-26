# Roadmap for Dhall-related projects

- Finish the tutorial book and publish it (depends on finishing SOFP first).
- Rewrite all algorithms to be stack-safe, including parsing. Add tests for deeply nested values.
- Rewrite the type checking and normal form algorithms using ∀-spines instead of nested forms, without functional changes. This is a pre-requisite for the one-step type inference. (Not sure whether also λ-spines are required for this to work.)
- Implement parsing enhancements, without changing normal forms. (See below.)
- Implement a type-checker that can modify the expression being type checked. The new expression may have some arguments inserted and some type annotations inserted, etc.
- Implement "one-step" type inference for ∀-spines using that feature. (Not sure whether also λ-spines are required for this to work.) Modify the do-notation by using type inference.
- Implement some more beta-reducing rules. For example, `Integer/clamp (Integer/clamp x) = Integer/clamp x` or some more rules for `Natural` number operations. I documented some possible new rules in the scall source code.
- Automatically insert values of the unit type `{}` when needed, similarly to the one-step type inference. Values of the type `{}` will be also inserted when their type was computed depending on previous values. Similarly, insert values of equality types.
- Each expression has a built-in "type annotation" field that may be initially empty. Implement the beta-reducer that can use the type information. If type information is not present, certain "type-sensitive" beta-reductions will not be performed (but others will be).
- Implement more features for dependent type checking. Add a "value context" to the typechecker. (See below.)
- Enable row and column polymorphism according to [this issue](https://github.com/dhall-lang/dhall-lang/issues/1381). See if the standard tests still pass.
- Figure out if my definition of the `freeVar` judgment is correct (do we allow only zero de Bruijn index values?).
- - Implement widening on union values. `(x : U).(V)` is valid if `V` has the type constructor of the same type as used for `x : U`.
- Document the import system in the standard (I had a branch in `winitzki/dhall-lang` about that). Or, add an "implementation note" document. Currently, the import system is less clearly documented than other parts.
- Fully document the JSON, the YAML, and the TOML export.
- Implement "lightweight bindings" for Python, Rust, Java as an exercise?
- Implement an IntelliJ plugin for fully-featured Dhall IDE.
- Enhance the Dhall grammar for better error reporting.
- Implement the Dhall grammar via tree-sitter.
- Implement native code overrides for Dhall expressions, dynamic loading from JAR by SHA256.
- Use `SymbolicGraph` to implement a shim for the `fastparse` parsing framework so that parsers are stack-safe. Alternatively, use `TailCalls` in the output type of the parsers. (Will that work?)
- Export to Scala source: the exported value must be a Scala expression that evaluates to the normal form of the Dhall value, in a Scala representation.
- Export to JVM code and run to compute the normal form? (JIT compiler; perhaps only for literal values of ground types.)
- Prevent explosion of normal forms; implement automatic stopping for normal form expansion under lambda or whenever they grow exponentially beyond a certain limit.

## Parsing enhancements

The following enhancements could be implemented by changing only the parser:

| Will parse this new syntax:             | Into this standard Dhall expression:                                                                |
|-----------------------------------------|-----------------------------------------------------------------------------------------------------|
| `123_456`                               | `123456` (underscores permitted and ignored within numerical values, including double or hex bytes) |
| `match x y`                             | `merge y x`  (or some other syntax: x match y, x as in y? avoid new keywords?)                      |
| `∀(x : X)(y : Y)(z : Z) → expr`         | `∀(x : X) → ∀(y : Y) → ∀(z : Z) → expr`                                                             |
| `λ(x : X)(y : Y)(z : Z) → expr`         | `λ(x : X) → λ(y : Y) → λ(z : Z) → expr`                                                             |
| `let f (x : X) (y : Y) : Z = expr`      | `let f = λ(x : X) → λ(y : Y) → (expr : Z)` where Z is optional                                      |
| `λ { x : X, y : Y } → expr`             | `λ(_ : { x : X, y : Y }) → let x = _.x in let y = _.y in expr`                                      |
| `let f { x : X, y : Y } : Z = expr`     | `let f = λ(_ : { x : X, y : Y }) → let x = _.x in let y = _.y in (expr : Z)` where Z is optional    |
| `let { x = a : A, y = b : B } = c in d` | `let a : A = c.x in let b : B = c.y in d` where A, B are optional                                   |
| `x ``p`` y`  at low precedence          | `p x y`  where `p` itself may need to be single-back-quoted                                         |
| `f a $ g b`  at low precedence          | `f a (g b)`                                                                                         |
| `x ▷ f a b`  at low precedence          | `f a b x`  (also support non-unicode version of the triangle)                                       |
| `x.[a]`                                 | `List.index A a x`    (with inferred type)                                                          |

The precedence of the operator `|>`
is higher than that of `$` but lower than that of all double back-quoted infix operators (which have all the same precedence).

The operators `|>` and all double back-quoted infix operators associate to the left.

The operator `$` associates to the right as in Haskell.

## Typechecker and beta-reducer with a "value context"

A "value context" `D` is a set of ordered pairs of terms.

`D = { x = y, a = b, ... }`

If `D` contains `x = y` then it is assumed also that `D` contains `y = x`. (Do we need this feature?)

If `D` contains `x = y` and we are type-checking or beta-reducing an expression that has `x` free, we may substitute `y` instead of `x` in that expression.

- If we have `λ(x : a === b) → expr` then we add the relation `a = b` to `D` while type-checking `expr`.
- In an expression `merge r (x : X)`, suppose a handler clause has the form `ConstructorName = λ(p : P) → expr`. While type-checking `expr`, we add the relation `x = X.ConstructorName p` to `D`.
- In an expression `if c then x else y`, we add `c = True` while typechecking `x` and `c = False` while typechecking `y`.

## µDhall

Implement a "micro-Dhall" (µDhall) language that has only the core System F-omega features. No records, no unions, only natural numbers, only 4 built-in operations with natural numbers.

The point of µDhall is to provide a very small language for experimenting with different implementation techniques, in order to decide how to improve performance.
It should be relatively quick to implement µDhall completely, with a comprehensive test suite.

Proposed features of µDhall:

- No Unicode chars from higher Unicode pages.
- No `Sort`, only `Type` and `Kind`, while `Kind` is not typeable.
- Syntax: `(λ(x : X) → expr) : (∀(x : X) → expr)`.
- Syntax: `f a b c` and `x : X` expressions.
- Built-in symbols: `Natural`, `Natural/fold`, `Natural/isZero`, `+`, `Natural/subtract`, `Type`, `Kind`.
- Built-in `Natural` number constants (0, 1, 2, ...) with unlimited precision.
- No `Bool`. (Can be Church-encoded.)
- No `Integer` or any other built-in Dhall data types.
- `let a = b in e` but no `let a : A = b in c` like in Dhall; perhaps with shortcut `let x = a let y = b in ...`.
- Imports of files only (no http, no env vars). No sha256, no alternative imports, no `missing`, no `as Text` etc. Imports are cached though (for referential transparency). Relative path only, from current directory (the imported expression must begin with `./`). Or perhaps no imports at all?
- No text strings. This just complicates the implementation with escapes, multiline strings, interpolation, etc.
- No products or co-products, records or unions. (Can be Church-encoded.)
- No built-in `Option` or `List` type constructors. (Can be Church-encoded.)
- No `assert` or `a === b`. (Can be encoded via Leibniz equality.)
- No CBOR support.

Implement µDhall and verify that:
- There are no stack overflows, even with very deeply nested data.
- There are no performance bottlenecks, even with large normal forms.
- There is no problem with highly repetitive data or highly repetitive normal forms.
- Avoid normal-form explosion.

Try implementing "gas" in order to limit the run time of evaluating the beta-normal form and to reject space-leaks and time-leaks (preferably at compile time), reuse memory, share data (given that the source text is already in memory).

Try various implementation ideas: HOAS, PHOAS, CBOR-based processing (?), or various abstract machines.

Try various ideas about how to combine type-checking with evaluation in a single pass.

Try various ideas about term inference / implicit parameters.

## Implement type refinement

- Text strings that are non-empty or not containing a given string
- Literal singleton types, or, more generally, types allowed to have a prescribed set of values
- Numbers or booleans with prescribed properties (given a predicate)
- How to implement a type that includes a proposition with a more ergonomic syntax?

## Implement "late binding as in OOP" for records

Define a record `let x = { a = 1, b = this.a + 1 }`, and we should get something equivalent to `{ a = 1, b = 2 }`.
Then `let y = x // { a = 2 }` should give `{ a = 2, b = 3 }`.

- How to encode this in System F-omega? What types correspond to such records?
