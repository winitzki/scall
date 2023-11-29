[![Project stage: Experimental][project-stage-badge: Experimental]][project-stage-page]
[![Build](https://github.com/winitzki/scall/workflows/scall_build_and_test/badge.svg)](https://github.com/winitzki/scall/actions/workflows/build-and-test.yml)


[project-stage-page]: https://blog.pother.ca/project-stages/
[project-stage-badge: Concept]: https://img.shields.io/badge/Project%20Stage-Concept-red.svg
[project-stage-badge: Research]: https://img.shields.io/badge/Project%20Stage-Research-orange.svg
[project-stage-badge: Experimental]: https://img.shields.io/badge/Project%20Stage-Experimental-yellow.svg
[project-stage-badge: Development]: https://img.shields.io/badge/Project%20Stage-Development-yellowgreen.svg
[project-stage-badge: Production Ready]: https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg
[project-stage-badge: DEPRECATED]: https://img.shields.io/badge/Project%20Stage-%20!%20DEPRECATED%20%20%20!-ff0000.svg

# scall - A reference implementation of Dhall in Scala

This project is a Scala implementation of the [Dhall language](https://dhall-lang.org), a purely functional programming language designed for programmable configuration with strong guarantees of consistency and security.

# Example usage

Read a Dhall expression into a Dhall syntax tree, perform type checking and beta-normalization, and convert into a Scala value.

```scala
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.codec.FromDhall.DhallExpressionAsScala

val a: Boolean = "Natural/odd 123".dhall.typeCheckAndBetaNormalize().unsafeGet.asScala[Boolean]

assert(a == true)

val b: BigInt = "1 + 2".dhall.typeCheckAndBetaNormalize().unsafeGet.asScala[BigInt]

assert(b == 3)
```

Define a Dhall factorial function as a Dhall expression, and apply it to another a Dhall expression.

```scala
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.Expression
import io.chymyst.dhall.codec.FromDhall.DhallExpressionAsScala

val factorial: Expression =
  """
    |\(x: Natural) ->
    |  let t = {acc: Natural, count: Natural}
    |  let result = Natural/fold x t (\(x: t) -> {acc = x.acc * x.count, count = x.count + 1} ) {acc = 1, count = 1}
    |    in result.acc
        """.stripMargin.dhall.betaNormalized

assert(factorial.toDhall ==
  """
    |λ(x : Natural) → (Natural/fold x { acc : Natural, count : Natural } (λ(x : { acc : Natural, count : Natural }) → { acc = x.acc * x.count, count = x.count + 1 }) { acc = 1, count = 1 }).acc
    |""".stripMargin.trim)

val ten: Expression = "10".dhall

// Manipulate Dhall expressions.
val tenFactorial: Expression = factorial(ten)

assert(tenFactorial.betaNormalized.asScala[BigInt] == BigInt(3628800))
```

In this example, we skipped type-checking since we know that our expression is well-typed.
However, Dhall only guarantees correct evaluation for well-typed expressions.
An ill-typed expression may fail to evaluate or even cause an infinite loop:

```scala
import io.chymyst.dhall.Parser.StringAsDhallExpression

// Curry's Y combinator. We set the `Bool` type arbitrarily; the types do not match.
val illTyped = """\(f : Bool) -> let p = (\(x : Bool) -> f x x) in p p""".dhall

val argument = """\(x: Bool) -> x""".dhall
val bad = illTyped(argument)
// These expressions fail type-checking.
assert(illTyped.typeCheckAndBetaNormalize().isValid == false)
assert(bad.typeCheckAndBetaNormalize().isValid == false)
// If we try evaluating `bad` without type-checking, we will get an infinite loop.
try {
  bad.betaNormalized
} catch {
  case _: StackOverflowError =>
}
```

## Goals of the project

1. Fully implement the syntax and semantics of Dhall. All standard tests from the [dhall-lang repository](https://github.com/dhall-lang/dhall-lang) must pass. (This is done.)
2. Implement JSON and YAML export.
2. Implement tools for working with Dhall values in Scala conveniently. Convert between ordinary Scala types and Dhall types (both at run time and at compile time if possible). Most Dhall integrations only support a small subset of Dhall, but Scala has a rich type system. We would like to support Scala function types, Scala type constructors, higher-kinded types, and other Scala features as much as possible.
3. Implement tools for converting Dhall values into compiled Scala code (JAR format). JAR dependencies should be a transparent replacement of the standard Dhall imports, as far as Scala is concerned.

## Current status

- [x] The [Dhall language standard version v23.0.0](https://github.com/dhall-lang/dhall-lang/blob/master/CHANGELOG.md) is fully implemented.

- [x] A parser from Dhall to Scala case classes is implemented using [fastparse](https://github.com/com-lihaoyi/fastparse).  All the parser tests pass.

- [x] A serializer and deserializer for CBOR format is implemented using one of the two libraries: [cbor-java](https://github.com/c-rack/cbor-java) and [CBOR-Java](https://github.com/peteroupc/CBOR-Java). 

Two of the CBOR tests fail due to a bug in `CBOR-Java`. The bug was fixed [in this PR](https://github.com/peteroupc/CBOR-Java/pull/25) but the fix is not yet published to Sonatype / Maven.org.

- [x] There are no CBOR failures with the library `cbor-java`. All CBOR encoding and decoding tests pass.

- [x] Alpha-normalization is implemented according to [the Dhall specification](https://github.com/dhall-lang/dhall-lang/blob/master/standard/alpha-normalization.md). All alpha-normalization tests pass.

- [x] Beta-normalization is implemented according to [the Dhall specification](https://github.com/dhall-lang/dhall-lang/blob/master/standard/beta-normalization.md). All 285 beta-normalization tests pass.

- [x] Typechecking is implemented according to [the Dhall specification](https://github.com/dhall-lang/dhall-lang/blob/master/standard/type-inference.md), including the "function check". All typechecking tests pass.

- [x] GitHub Actions are used to test with JDK 8, 11, 17 and Scala 2.13.11.

- [x] Import resolution code is fully implemented, all tests pass.

- [ ] Converting Dhall values to Scala values: in progress.

## Special features in the Scala implementation of Dhall

- [x] All alpha-normalization, beta-normalization, and type-checking results are cached in LRU caches of configurable size.

- [x] A [non-standard "do-notation"](./do-notation.md) is implemented.

- [ ] Dhall values of function types are converted to Scala functions. For example, `λ(x : Natural) -> x + 1` is converted into the Scala function equivalent to `{ x : BigInt => x + 1 }`, which has type `Function1[BigInt, BigInt]`.

- [ ] Dhall values of type `Type` (for example, `Text`, `Bool`, or `Natural -> Natural`) are converted to Scala type tags such as `Tag[String]`, `Tag[Boolean]`, or `Tag[BigInt => BigInt]`.

## Roadmap for future developments

1. Possibly, implement automatic type inference for certain solvable cases. Omit type annotations from lambdas and omit parentheses: `\x -> x + 1` should be sufficient for simple cases. Omit the type argument from curried functions if other arguments can be used to infer the type. List/map [ 1, 2, 3 ] (\x -> x + 1) should be sufficient. Similarly with the do-notation, `as bind with x in p then q` should be sufficient. (This probably requires introducing a new syntax form for do-notation rather than immediate desugaring, but perhaps not.)
2. More caching and more native implementation for literals, to improve performance. (Without caching, List/sort would hang on a list of 6 natural numbers.)
5. Convert between Dhall values and Scala values automatically (as much as possible given the Scala type system). Support both Scala 2 and Scala 3.
6. Create Scala-based Dhall values at compile time from Dhall files or from literal Dhall strings (compile-time constants).
7. Compile Dhall values into a library JAR. Enable importing JAR dependencies instead of Dhall imports (import `as Scala`?). Publish the Dhall standard library and other libraries as JARs.
8. Extend Dhall on the Scala side (with no changes to the Dhall language definition) so that certain Dhall types or values may be interpreted via custom Scala code.
9. Detect Dhall functions that operate efficiently on literal arguments, and implement those functions in efficient JVM code.
10. Detect Dhall functions that will ignore some (curried) arguments when given certain values of literal arguments, and implement laziness to make code more efficient.
11. Implement some elementary functions for Natural more efficiently (probably no need to change Dhall language), such as gcd or sqrt.
12. Implement numerical functions for rational numbers (instead of floating-point).
13. Implement higher-kinded types, heterogeneous lists, dependently-typed lists, etc., if possible.

### Parsing with `fastparse`

The ABNF grammar of Dhall is translated into rules of `fastparse`.

The "cut" is used sparingly as the `~/` operator, usually after a keyword or after a required whitespace.

However, in some cases adding this "cut" operator made the parsing results incorrect and had to be removed.

Another feature is that some parses need to fail for others to succeed. For example, `missingfoo` should be parsed as an identifier. However, `missing` is a keyword and is matched first. To ensure correct parsing, negative lookahead is used for keywords.

#### Limitations

So far, there are some issues with the Unicode characters:

- If the input contains non-UTF8 sequences, the `fastparse` library will replace those sequences by the "replacement" character (Unicode decimal `65533`). However, the Dhall standard specifies that non-UTF8 input should be rejected by the parser. As a workaround, at the moment, Unicode character `65533` is not allowed in Dhall files and will be rejected at parsing time.
