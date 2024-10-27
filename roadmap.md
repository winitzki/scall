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


## Parsing enhancements

The following enhancements could be implemented without any functional changes:

| Will parse this syntax:         | Into this standard Dhall expression:                                 |
|---------------------------------|----------------------------------------------------------------------|
| `x : {=}`                       | `x : T` (when type `T` can be inferred, or else fails to type-check) |
| `∀(x : a)(y : b)(z : c) → expr` | ∀(x : a) → ∀(y : b) → ∀(z : c) → expr                                |
| `λ(x : a)(y : b)(z : c) → expr` | λ(x : a) → λ(y : b) → λ(z : c) → expr                                |
| `λ x (y : b) z → expr`          | `λ(x : {=}) → λ(y : b) → λ(z : {=}) → expr`  (with inferred types)   |
| `λ { x : a, y : b } → expr`     | `λ(p : { x : a, y : b }) → let x = p.x in let y = p.y in expr`       |
| `x ``p`` y`  at low precedence  | `p x y`  where `p` may itself need to be single-back-quoted          |
| `x ▷ f a b`  at low precedence  | `f a b x`  (also support non-unicode version of the triangle)        |
| `x.[a]`                         | `List.index {=} a x`    (with inferred type)                         |

## Typechecker and beta-reducer with a "value context"

A "value context" `D` is a set of ordered pairs of terms.

`D = { x = y, a = b, ... }`

If `D` contains `x = y` then it is assumed also that `D` contains `y = x`. (Do we need this feature?)

If `D` contains `x = y` and we are type-checking or beta-reducing an expression that has `x` free, we may substitute `y` instead of `x` in that expression.

- If we have `λ(x : a === b) → expr` then we add the relation `a = b` to `D` while type-checking `expr`.
- In an expression `merge r (x : X)`, suppose a handler clause has the form `ConstructorName = λ(p : P) → expr`. While type-checking `expr`, we add the relation `x = X.ConstructorName p` to `D`.
- In an expression `if c then x else y`, we add `c = True` while typechecking `x` and `c = False` while typechecking `y`.

## Mini-Dhall

Implement a "core Dhall" language that has only the core System F-omega features. No records, no unions, only natural numbers, only 2 built-in operations with natural numbers.

Experiment with different implementations and decide how to improve performance.
