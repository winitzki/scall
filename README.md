[![Project stage: Experimental][project-stage-badge: Experimental]][project-stage-page]
[![Build](https://github.com/winitzki/scall/workflows/scall_build_and_test/badge.svg)](https://github.com/winitzki/scall/actions/workflows/build-and-test.yml)
[![codecov](https://codecov.io/gh/winitzki/scall/graph/badge.svg?token=7DB1MQJMTW)](https://codecov.io/gh/winitzki/scall)

[project-stage-page]: https://blog.pother.ca/project-stages/

[project-stage-badge: Concept]: https://img.shields.io/badge/Project%20Stage-Concept-red.svg

[project-stage-badge: Research]: https://img.shields.io/badge/Project%20Stage-Research-orange.svg

[project-stage-badge: Experimental]: https://img.shields.io/badge/Project%20Stage-Experimental-yellow.svg

[project-stage-badge: Development]: https://img.shields.io/badge/Project%20Stage-Development-yellowgreen.svg

[project-stage-badge: Production Ready]: https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg

[project-stage-badge: DEPRECATED]: https://img.shields.io/badge/Project%20Stage-%20!%20DEPRECATED%20%20%20!-ff0000.svg

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Github Tag](https://img.shields.io/github/tag/winitzki/scall.svg?label=release&colorB=blue)](https://github.com/winitzki/scall/tags)
[![Maven Central](https://img.shields.io/maven-central/v/io.chymyst/dhall-scala-cli_2.13.svg)](http://search.maven.org/#search%7Cga%7C1%7Cio.chymyst)

# scall - A reference implementation of Dhall in Scala

This project is a Scala implementation of the [Dhall language](https://dhall-lang.org), a purely functional programming
language designed for programmable configuration with strong guarantees of consistency and security.

# Example usage

Read a Dhall expression into a Dhall syntax tree, perform type checking and beta-normalization, and convert into a Scala
value.

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

assert(factorial.print ==
  """
    |λ(x : Natural) → (Natural/fold x { acc : Natural, count : Natural } (λ(x : { acc : Natural, count : Natural }) → { acc = x.acc * x.count, count = x.count + 1 }) { acc = 1, count = 1 }).acc
    |""".stripMargin.trim)

val ten: Expression = "10".dhall

// Manipulate Dhall expressions.
val tenFactorial: Expression = factorial(ten)

assert(tenFactorial.betaNormalized.asScala[BigInt] == BigInt(3628800))
```

In this example, we skipped type-checking since we know that the Dhall factorial expression is well-typed.
However, Dhall only guarantees correct evaluation for well-typed expressions.
An ill-typed expression may fail to evaluate or even cause an infinite loop:

```scala
import io.chymyst.dhall.Parser.StringAsDhallExpression

// Curry's Y combinator. We set the `Bool` type arbitrarily; the types cannot match in any case.
val illTyped = """\(f : Bool) -> let p = (\(x : Bool) -> f x x) in p p""".dhall
val argument = """\(x: Bool) -> x""".dhall
val bad = illTyped(argument)

// These expressions fail type-checking.
assert(illTyped.inferType.isValid == false)
assert(bad.inferType.isValid == false)

// If we try evaluating `bad` without type-checking, we will get an infinite loop.
bad.betaNormalized // java.lang.StackOverflowError
```

The Dhall factorial function can be also converted directly to a Scala function:

```scala
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.codec.FromDhall.DhallExpressionAsScala

val factorial: BigInt => BigInt = """
                                    |\(x: Natural) ->
                                    |  let t = {acc: Natural, count: Natural}
                                    |  let result = Natural/fold x t (\(x: t) -> {acc = x.acc * x.count, count = x.count + 1} ) {acc = 1, count = 1}
                                    |    in result.acc
        """.stripMargin.dhall.betaNormalized.asScala[BigInt => BigInt]

assert(factorial(BigInt(10)) == BigInt(3628800))
```

## Goals of this project

1. Fully implement the syntax and semantics of Dhall. All standard tests from
   the [dhall-lang repository](https://github.com/dhall-lang/dhall-lang) must pass. (This is done.)
2. Implement JSON and YAML export. (In progress.)
2. Implement tools for working with Dhall values in Scala conveniently. Convert between ordinary Scala types and Dhall
   types (both at run time and at compile time if possible). Most Dhall integrations only support a small subset of
   Dhall, but Scala has a rich type system. We would like to support Scala function types, Scala type constructors,
   higher-kinded types, and other Scala features as much as possible.
3. Implement tools for converting Dhall values into compiled Scala code (JAR format). JAR dependencies should be a
   transparent replacement of the standard Dhall imports, as far as Scala is concerned.
4. Optimize Dhall execution further. At the moment, all intermediate results of typechecking and beta-normalization are cached. For more optimization, perhaps rewrite the interpreter use HOAS, PHOAS, or Normalization-By-Evaluation. Compute different parts of a record in parallel. Perform typechecking and beta normalization of different subexpressions in parallel. Make sure caching is thread-safe.

## Current status

- [x] The [Dhall language standard version v23.0.0](https://github.com/dhall-lang/dhall-lang/blob/master/CHANGELOG.md)
  is fully implemented:

    - [x] A parser from Dhall to Scala case classes is implemented according
      to [the Dhall ABNF grammar](https://github.com/dhall-lang/dhall-lang/blob/master/standard/dhall.abnf)
      using [fastparse](https://github.com/com-lihaoyi/fastparse), closely
      following [the syntax guidelines](https://github.com/dhall-lang/dhall-lang/blob/master/standard/syntax.md).

    - [x] A serializer and deserializer for CBOR format is implemented. User may choose one of the two CBOR
      libraries: [cbor-java](https://github.com/c-rack/cbor-java)
      and [CBOR-Java](https://github.com/peteroupc/CBOR-Java). The latter library is 2x faster. Both libraries work correctly.

    - [x] Alpha-normalization is implemented according
      to [the Dhall specification](https://github.com/dhall-lang/dhall-lang/blob/master/standard/alpha-normalization.md).

    - [x] Beta-normalization is implemented according
      to [the Dhall specification](https://github.com/dhall-lang/dhall-lang/blob/master/standard/beta-normalization.md).

    - [x] Typechecking is implemented according
      to [the Dhall specification for type inference](https://github.com/dhall-lang/dhall-lang/blob/master/standard/type-inference.md)
      including [the "function check"](https://github.com/dhall-lang/dhall-lang/blob/master/standard/function-check.md).

    - [x] Import resolution code is implemented according
      to [the Dhall specification for imports](https://github.com/dhall-lang/dhall-lang/blob/master/standard/imports.md).

    - [x] All the [Dhall acceptance tests](https://github.com/dhall-lang/dhall-lang/tree/master/tests) pass: parsing,
      CBOR encoding and decoding, alpha-normalization, beta-normalization, type-checking, and imports.

- [x] Supporting Scala 2.13 only, for now.

- [x] GitHub Actions are used to test with JDK 8, 17, 22.

- [x] Converting Dhall values to Scala values: basic support is complete.

### Experimental features and optimizations

- [x] All alpha-normalization, beta-normalization, and type-checking results are cached in LRU caches of configurable
  size.

- [x] A [non-standard "do-notation"](./do-notation.md) is implemented.

- [x] Optimization: `Natural/fold` will short-cut the loop when the current result stops changing. (Backward compatible, no change to normal forms!) I also [contributed this optimization to the Haskell backend](https://github.com/dhall-lang/dhall-haskell/pull/2596).

- [x] Experimental feature: `assert : a === b` will perform alpha, beta, and eta-reduction on `a` and `b` before comparing their CBOR serializations. (Breaking change to normal forms!)

- [x] Experimental optimization: `Natural/fold` will not expand under lambda if intermediate expressions keep growing beyond about 500 sub-terms. (Breaking change to normal forms! Standard tests still pass because they do not include terms with such large normal forms.)


## Other features in the Scala implementation of Dhall

- [x] Dhall values of function types are converted to Scala functions. For example, `λ(x : Natural) -> x + 1` is
  converted into a Scala function equivalent to `{ x : BigInt => x + 1 }`, which has type `Function1[BigInt, BigInt]`.

- [ ] Dhall values of type `Type` (for example, `Text`, `Bool`, or `Natural -> Natural`) are converted to Scala type
  tags such as `Tag[String]`, `Tag[Boolean]`, or `Tag[BigInt => BigInt]`.

- [x] Print Dhall values using the standard Dhall syntax.

- [x] Export Dhall values to Yaml for most of the relevant types (numbers, strings, Booleans, lists, records).

## Ideas for future developments

1. Implement automatic type inference for certain solvable cases. Omit type annotations from lambdas and omit
   parentheses: `\x -> x + 1` should be sufficient for simple cases. Omit the type argument from curried functions if
   other arguments can be used to infer the type. List/map [ 1, 2, 3 ] (\x -> x + 1) should be sufficient. Just a `None`
   without a type should be sufficient in most cases. Similarly, with the do-notation, `as bind with x in p then q`
   should be sufficient. (This probably requires introducing a new syntax form for do-notation rather than immediate
   desugaring, but perhaps not.)
2. Try HOAS and PHOAS to make the implementation faster.
3. Make sure the parser and the interpreter are stack-safe or at least do not introduce stack-overflow bottlenecks
   beyond what is expected.
5. Convert between Dhall values and Scala values automatically (as much as possible given the Scala type system).
   Support both Scala 2 and Scala 3.
6. Create Scala-based Dhall values at compile time from Dhall files or from literal Dhall strings (compile-time
   constants).
7. Compile Dhall values into a library JAR. Enable importing JAR dependencies instead of Dhall imports (
   import `as Scala`?). Publish the Dhall standard library and other libraries as JARs.
8. Extend Dhall on the Scala side (with no changes to the Dhall language definition) so that certain Dhall types or
   values may be interpreted via custom Scala code.
9. Avoid beta-normalizing under lambda when that would increase the size of a Dhall function body. This is needed to
   operate efficiently on literal arguments (function body should not be fully beta-normalized until applied to a
   literal argument).
10. Detect Dhall functions that will ignore some (curried) arguments when given certain values of literal arguments, and
    implement laziness to make code more efficient. With that, detect fixpoints of Dhall functions under `List/fold` and stop the
    iteration early.
11. Implement some elementary functions for Natural more efficiently (probably no need to change Dhall language), such
    as gcd, div_mod, int_sqrt.
12. Implement numerical functions for rational numbers and for floating-point numbers.
13. Implement higher-kinded types, heterogeneous lists, dependently-typed lists, etc., if possible.
14. `assert` should be more powerful. Enable comparing types, enable associative simplification, product and co-product rewriting.

### Parsing with `fastparse`

The ABNF grammar of Dhall is translated into rules of `fastparse`.

The "cut" is used sparingly as the `~/` operator, usually after a keyword or after a required whitespace.

However, in some cases adding this "cut" operator made the parsing results incorrect and had to be removed.

Another feature is that some parses need to fail for others to succeed. For example, `missingfoo` should be parsed as an
identifier. However, `missing` is a keyword and is matched first. To ensure correct parsing, negative lookahead is used
for keywords.

To improve parsing performance, the parsing results for some sub-expressions are memoized.
This is implemented via an add-on library `fastparse-memoize`. 

#### Limitations

So far, there are some issues with the Unicode characters:

- If the input contains non-UTF8 sequences, the `fastparse` library will replace those sequences by the "replacement"
  character (Unicode decimal `65533`). However, the Dhall standard specifies that non-UTF8 input should be rejected by
  the parser. As a workaround, at the moment, Unicode character `65533` is not allowed in Dhall files and will be
  rejected at parsing time.

- Deeply nested expressions will cause a stack overflow error.

# Release version history


## 0.2.1

- Implemented `fastparse-memoize` to speed up parsing (by 10x and more in some cases).
- Upgrade to fastparse 3.1.x

## 0.2.0

- First version published on Sonatype
- Fixed the regression described in https://github.com/dhall-lang/dhall-haskell/issues/2597
- Support for Yaml and JSON export
- Standalone JAR executable `dhall.jar` with command-line options similar to `dhall-haskell`
- Published to Maven (Scala 2.13 only)

## 0.1 (Not published)

- Full support for Dhall language standard version 23.0.0, submitted corrections to the Dhall standard: https://github.com/dhall-lang/dhall-lang/pull/1362
- Fixing the bug in CBOR library: https://github.com/peteroupc/CBOR-Java/pull/25 and uptaking CBOR-Java 4.5.3 with the fix.
- Some extensions (all backward-compatible, with no changes in normal forms)

# Building a command-line utility

To build a standalone `dhall` executable: `bash make_jar.sh`

This creates the file `./dhall.jar`.

To test the executable: `bash test_jar.sh`

The test script should print "Tests successful." at the end. If it does not print that, some tests failed.

## Using the command-line utility

```bash
$ java -jar ./dhall.jar --help
java -jar dhall.jar --flags... command
  -f --file <str>    Path to the input Dhall file (default: stdin)
  -o --output <str>  Path to the output file (default: stdout)
  -q --quoted        Quote all strings (for Yaml output only; default is false)
  -d --documents     Create a Yaml file with document separators (for Yaml output only; default is
                     false)
  -i --indent <int>  Indentation depth for JSON and Yaml (default: 2)
  command <str>...   Optional command: decode, encode, hash, text, type, yaml, json
```

Examples: 

Compute the Dhall normal form of a Dhall expression from a given file.
```bash
$ java -jar ./dhall.jar --file ./scall-cli/src/test/resources/jar-tests/3.dhall
{ True = [1.23, 4.56], a = 2, b = None Bool, c = Some "abc", y = True }
```

Print the inferred type of a Dhall expression.
```bash
$ java -jar ./dhall.jar --file ./scall-cli/src/test/resources/jar-tests/3.dhall type
{ True : List Double, a : Natural, b : Optional Bool, c : Optional Text, y : Bool }
```

Compute the SHA256 hash of a Dhall expression.
```bash
$ java -jar ./dhall.jar --file ./scall-cli/src/test/resources/jar-tests/3.dhall hash
sha256:e06ccdb4df3721dba87291eb49754c87955462d73df626e5e4c77de3af06e87f
```

Export a Dhall expression to Yaml format. Default indentation is 2 spaces.
```bash
$ java -jar ./dhall.jar --file ./scall-cli/src/test/resources/jar-tests/3.dhall yaml
'True':
  - 1.23
  - 4.56
a: 2
c: abc
'y': true
```


Export a Dhall expression to JSON format, with 4 spaces of indentation.
```bash
$ java -jar ./dhall.jar --file ./scall-cli/src/test/resources/jar-tests/3.dhall --indent 4 json
{
    "True":   [
        1.23,
        4.56
    ],
    "a":   2,
    "c":   "abc",
    "y":   true
}
```

