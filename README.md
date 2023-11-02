# scall - A reference implementation of Dhall in Scala

This project is a Scala implementation of the [Dhall language](https://dhall-lang.org), a purely functional programming language designed for programmable configuration with strong guarantees of consistency and security.

## Goals of the project

1. Fully implement the syntax and semantics of Dhall. All standard tests from the [dhall-lang repository](https://github.com/dhall-lang/dhall-lang) must pass. It should be possible to read arbitrary Dhall code and implement standard functions such as normalization, hashing, and JSON / YAML export.
2. Implement tools for working with Dhall values in Scala conveniently. Convert between ordinary Scala types and Dhall types (both at run time and at compile time if possible). We would like to support Scala function types, Scala type constructors, higher-kinded types, and other Scala features as much as possible.
3. Implement tools for converting Dhall values into compiled Scala code (JAR format). JAR dependencies should be a transparent replacement of Dhall imports.

## Current status

### Completed

A parser from Dhall to Scala case classes is implemented using [fastparse](https://github.com/com-lihaoyi/fastparse).

All the parser tests pass.

A serializer and deserializer for CBOR format is implemented using one of the two libraries: [cbor-java](https://github.com/c-rack/cbor-java) and [CBOR-Java](https://github.com/peteroupc/CBOR-Java). 

Two of the CBOR tests fail due to a bug in `CBOR-Java`. The bug was fixed [in this PR](https://github.com/peteroupc/CBOR-Java/pull/25) but the fix is not yet published to Sonatype / Maven.org.

There are no CBOR failures with the library `cbor-java`.

All CBOR encoding and decoding tests pass.

Alpha-normalization is implemented according to [the Dhall specification](https://github.com/dhall-lang/dhall-lang/blob/master/standard/alpha-normalization.md).

All alpha-normalization tests pass.

### In progress

Beta-normalization.

## Roadmap

1. Parse from Dhall text into Dhall expression structures. (Done.)
2. Serialize Dhall expressions into CBOR and deserialize back into Dhall. (Done.)
3. Evaluate and normalize Dhall values according to Dhall semantics.
4. Import Dhall values from files or from the Internet according to the Dhall security model.
5. Convert between Dhall values and Scala values automatically (as much as possible given the Scala type system).
6. Create Scala-based Dhall values at compile time from Dhall files or from literal Dhall strings (compile-time constants).
7. Compile Dhall values into a library JAR. Enable importing JAR dependencies instead of Dhall imports. Publish the Dhall standard library and other libraries as JARs.
8. Extend Dhall on the Scala side (with no changes to the Dhall language definition) so that certain Dhall types or values may be interpreted via custom Scala code.
9. Detect Dhall functions that always give literal values for literal arguments, and implement those functions in efficient JVM code.
10. Detect Dhall functions that will ignore some (curried) arguments when given certain values of literal arguments, and implement laziness to make code more efficient.
11. Implement some elementary functions for Natural more efficiently (probably no need to change Dhall language), such as gcd or sqrt.
12. Implement numerical functions for rational numbers (instead of floating-point).

### Parsing with `fastparse`

The ABNF grammar of Dhall is translated into rules of `fastparse`.

The "cut" is used sparingly as the `~/` operator, usually after a keyword or after a required whitespace.

However, in some cases adding this "cut" operator made the parsing results incorrect and had to be removed.

Another feature is that some parses need to fail for others to succeed. For example, `missingfoo` should be parsed as an identifier. However, `missing` is a keyword and is matched first. To ensure correct parsing, negative lookahead is used for keywords. 

#### Limitations

So far, there are some issues with the Unicode characters:

- If the input contains non-UTF8 sequences, the `fastparse` library will replace those sequences by the "replacement" character (Unicode decimal `65533`). However, the Dhall standard specifies that non-UTF8 input should be rejected by the parser. As a workaround, at the moment, Unicode character `65533` is not allowed in Dhall files and will be rejected at parsing time.
