# Roadmap for Dhall-related projects

- Finish the tutorial book and publish it (depends on finishing SOFP first).
- Rewrite all algorithms to be stack-safe.
- Rewrite the type checking and normal form algorithms using λ-spines and ∀-spines instead of nested forms, without functional changes.
- Implement parsing enhancements, without changing normal forms. (See below.)
- Implement a type-checker that can modify the expression being type checked. The new expression may have some type parameters inserted and some type annotations inserted.
- Implement "one-step" type inference for λ-spines using that feature. Modify the do-notation by using type inference.
- Automatically insert values of unit type `{}` when needed.
- Each expression has a built-in "type annotation" field that may be initially empty. Implement the beta-reducer that can use the type information. If type information is not present, certain "type-sensitive" beta-reductions will not be performed (but others will be).
- Implement more features for dependent type checking. Add a "value context" to the typechecker. (See below.)
- Enable row and column polymorphism.
- Figure out if my definition of the freeVar judgment is correct (do we allow only zero de Bruijn index values?).
- Document the import system in the standard. Or, add an "implementation note" document. Currently, the import system is less clearly documented than other parts.
- Document the JSON, the YAML, and the TOML export.
- Implement "lightweight bindings" for Python, Rust, Java?
- Implement an IntelliJ plugin for fully-featured Dhall IDE.
- Enhance the Dhall grammar for better error reporting.
- Use `SymbolicGraph` to implement a shim for the `fastparse` parsing framework so that parsers are stack-safe.
- Export to Scala source: the exported value must be a Scala expression that evaluates to the normal form of the Dhall value, in a Scala representation.
- Export to JVM code and run to compute the normal form? (JIT compiler; perhaps only for literal values of ground types.)

## Parsing enhancements

The following enhancements could be implemented without any functional changes:

| Will parse this syntax:         | Into this standard Dhall expression:                                                                   |
|---------------------------------|--------------------------------------------------------------------------------------------------------|
| `123_456`                       | `123456` (underscores permitted and ignored within any numerical values including double or hex bytes) |
| `x : {=}`                       | `x : T` (when type `T` can be inferred, or else fails to type-check)                                   |
| `∀(x : X)(y : Y)(z : Z) → expr` | ∀(x : X) → ∀(y : Y) → ∀(z : Z) → expr                                                                  |
| `λ(x : X)(y : Y)(z : Z) → expr` | λ(x : X) → λ(y : Y) → λ(z : Z) → expr                                                                  |
| `λ x (y : Y) z → expr`          | `λ(x : {=}) → λ(y : Y) → λ(z : {=}) → expr`  (with inferred types)                                     |
| `λ { x : X, y : Y } → expr`     | `λ(p : { x : X, y : Y }) → let x = p.x in let y = p.y in expr`                                         |
| `x ``p`` y`  at low precedence  | `p x y`  where `p` itself may need to be single-back-quoted                                            |
| `x ▷ f a b`  at low precedence  | `f a b x`  (also support non-unicode version of the triangle)                                          |
| `x.[a]`                         | `List.index {=} a x`    (with inferred type)                                                           |

## Typechecker and beta-reducer with a "value context"

A "value context" `D` is a set of ordered pairs of terms.

`D = { x = y, a = b, ... }`

If `D` contains `x = y` then it is assumed also that `D` contains `y = x`. (Do we need this feature?)

If `D` contains `x = y` and we are type-checking or beta-reducing an expression that has `x` free, we may substitute `y` instead of `x` in that expression.

- If we have `λ(x : a === b) → expr` then we add the relation `a = b` to `D` while type-checking `expr`.
- In an expression `merge r (x : X)`, suppose a handler clause has the form `ConstructorName = λ(p : P) → expr`. While type-checking `expr`, we add the relation `x = X.ConstructorName p` to `D`.
- In an expression `if c then x else y`, we add `c = True` while typechecking `x` and `c = False` while typechecking `y`.

## µDhall

Implement a "core Dhall" language that has only the core System F-omega features. No records, no unions, only natural numbers, only 2 built-in operations with natural numbers.

The point of µDhall is to provide a very small language for experimenting with different implementation techniques, in order to decide how to improve performance.
It should be relatively quick to implement Micro-Dhall completely, with a standard test suite.

Proposed features of µDhall:

- No Unicode chars from higher Unicode pages can be used in identifiers.
- No `Sort`, only `Type` and `Kind`, while `Kind` is not typeable.
- `(λ(x : X) → expr) : (∀(x : X) → expr)`
- `f a b c` and `x : X` expressions.
- `Natural`, `Natural/fold`, `Natural/isZero`, `+`, `Natural/subtract`
- No `Bool` but implement `Bool` as `∀(a : Type) → a → a → a` and implement utility methods for `Bool`.
- `let a = b in e` and `let a : A = b in c` like in Dhall; but no shortcut of `let x = a let y = b`.
- Imports of files only (no http, no env vars). No sha256, no alternative imports, no `missing`, no `as Text` etc. Imports are cached though (for referential transparency).
- No text strings. This just complicates the implementation with escapes, multiline strings, interpolation, etc.
- No products or co-products, encode them
- No built-in `Option` or `List` type constructors
- No `assert` or `a === b`
- No CBOR support

Implement µDhall and verify that:
- There are no stack overflows, even with very deeply nested data
- There are no performance bottlenecks, even with large normal forms
- There is no problem with highly repetitive data or highly repetitive normal forms
- Export to Scala expressions

Try implementing "gas" in order to limit the run time of evaluating the beta-normal form and to reject space-leaks and time-leaks (preferably at compile time).

## Implement type refinement

- Text strings that are non-empty or not containing a given string
- Numbers or booleans with prescribed properties
- How to implement a type that includes a proposition?

## Implement "late binding as in OOP" for records

Define a record `let x = { a = 1, b = this.a + 1 }`, and we should get something equivalent to `{ a = 1, b = 2 }`.
Then `let y = x // { a = 2 }` should give `{ a = 2, b = 3 }`.

- How to encode this in System F-omega? What types correspond to such records?
