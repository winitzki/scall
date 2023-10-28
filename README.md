# scall - A reference implementation of Dhall in Scala

This project is a Scala implementation of the [Dhall language](https://dhall-lang.org), a purely functional programming language designed for programmable configuration with strong guarantees of consistency and security.

## Goals of the project

1. Fully implement the syntax and semantics of Dhall. All standard tests from the [dhall-lang repository](https://github.com/dhall-lang/dhall-lang) must pass. It should be possible to read arbitrary Dhall code and implement standard functions such as normalization, hashing, and JSON / YAML export.
2. Implement tools for working with Dhall values in Scala conveniently. Convert between ordinary Scala types and Dhall types (both at run time and at compile time if possible). We would like to support Scala function types, Scala type constructors, higher-kinded types, and other Scala features as much as possible.
3. Implement tools for converting Dhall values into compiled Scala code (JAR format). JAR dependencies should be a transparent replacement of Dhall imports.

## Current status

### Completed

A parser from Dhall to Scala case classes, using [fastparse](https://github.com/com-lihaoyi/fastparse).

All the parser tests pass.

A serializer and deserializer for CBOR format, using [CBOR-Java](https://github.com/peteroupc/CBOR-Java).

Two of the CBOR tests fail due to a bug in `CBOR-Java`. The bug was fixed upstream but the fix is not yet published. Other CBOR tests pass.

Alpha-normalization according to [the Dhall specification](https://github.com/dhall-lang/dhall-lang/blob/master/standard/alpha-normalization.md).

### In progress

Beta-normalization.

## Roadmap

1. Parse from Dhall text into Dhall expression structures.
2. Serialize Dhall expressions into CBOR and deserialize back into Dhall.
3. Evaluate and normalize Dhall values according to Dhall semantics.
4. Import Dhall values from the network according to the Dhall security model.
5. Convert between Dhall values and Scala values (as much as possible given the Scala type system).
6. Create Scala-based Dhall values at compile time from Dhall files or from literal Dhall strings (compile-time constants).
7. Compile Dhall values into a library JAR. Publish the standard and taking JAR dependencies.
8. Extend Dhall on the Scala side (no changes to the Dhall standard) so that certain Dhall types or values are interpreted via custom Scala code.

### Parsing with `fastparse`

The ABNF grammar of Dhall is translated into rules of `fastparse`.

The "cut" is used sparingly as the `~/` operator, usually after a keyword or after a required whitespace.

However, in some cases adding this "cut" operator made the parsing results incorrect.

#### Limitations

So far, there are some issues with the Unicode characters:

- If the input contains non-UTF8 sequences, the `fastparse` library appears to skip some of the input and create a valid UTF-8 string. However, the Dhall standard specifies that non-UTF8 input should be rejected by the parser.
- If the input contains Unicode characters greater than 65535 the `fastparse` library seems to truncate those characters.

There is also a failing test with `missing//blah` or `missingas text` and such. The keyword `missing` somehow conflicts with parsing.

### CBOR encoding

The CBOR encoding is implemented using the "CBOR-Java" library (`"com.upokecenter" % "cbor" % "4.5.2"`) because it is easy to use and supports big integers and ordered dictionaries out of the box.

#### Limitations

The "CBOR-Java" library version 4.5.2 has a bug where it fails to detect the low precision of special `Double` values `0.0` and `-0.0` (positive and negative zero). The library converts those values to 32-bit `Float` precision whereas the expected behavior is to convert them to 16-bit (half-precision). This fails two of the Dhall acceptance tests. The bug has been fixed in [this PR](https://github.com/peteroupc/CBOR-Java/pull/25).

