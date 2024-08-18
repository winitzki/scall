[![Project stage: Experimental][project-stage-badge: Experimental]][project-stage-page]
[![Build](https://github.com/winitzki/scall/workflows/scall_build_and_test/badge.svg)](https://github.com/winitzki/scall/actions/workflows/build-and-test.yml)

[project-stage-page]: https://blog.pother.ca/project-stages/

[project-stage-badge: Concept]: https://img.shields.io/badge/Project%20Stage-Concept-red.svg

[project-stage-badge: Research]: https://img.shields.io/badge/Project%20Stage-Research-orange.svg

[project-stage-badge: Experimental]: https://img.shields.io/badge/Project%20Stage-Experimental-yellow.svg

[project-stage-badge: Development]: https://img.shields.io/badge/Project%20Stage-Development-yellowgreen.svg

[project-stage-badge: Production Ready]: https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg

[project-stage-badge: DEPRECATED]: https://img.shields.io/badge/Project%20Stage-%20!%20DEPRECATED%20%20%20!-ff0000.svg

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Github Tag](https://img.shields.io/github/tag/winitzki/scall.svg?label=release&colorB=blue)](https://github.com/winitzki/scall/tags)
[![Maven Central](https://img.shields.io/maven-central/v/io.chymyst/fastparse-memoize_2.13.svg)](https://central.sonatype.com/artifact/io.chymyst/fastparse-memoize_2.13)

# `fastparse-memoize` - A speedup for Li Haoyi's `fastparse`

The library `fastparse-memoize` adds the `memoize` method to `fastparse`'s parsers.

When `fastparse` processes grammars that require backtracking, parsing rules may be applied repeatedly at the same place in the input.
In most cases, the parsing results will also be the same. 

Memoizing means that a parser will not be tried again at the same place; instead, the previous parsing result is fetched from the cache and is immediately available.

## Usage

- Import the `Memoize` symbol and use `Memoize.parse` instead of `fastparse.parse`. Use `Memoize.parseInputRaw` instead of `fastparse.parseInputRaw`.
- Import the `Memoize.MemoizeParser` symbol and use `.memoize` on selected parsing rules.

## Example

Consider a simple language consisting of the digit `1`, the `+` operation, and parentheses.
Example strings in that language are `1+1` and `1+(1+1+(1))+(1+1)`.

This language is parsed by the following straightforwardly written grammar:

```scala
import fastparse._

def program1[$: P]: P[String] = P(expr1 ~ End).!
def expr1[$: P]               = P(plus1 | other1)
def plus1[$: P]: P[_]         = P(other1 ~ "+" ~ expr1)
def other1[$: P]: P[_]        = P("1" | ("(" ~ expr1 ~ ")"))

val n = 30
val input = "(" * n + "1" + ")" * n  // The string ((( ... (((1))) ... ))) is the input for the parser.
fastparse.parse(input, program1(_)) // Very slow.

```

This program works but is exponentially slow on certain valid expressions, such as `((((1))))` with many parentheses.

The reason for slowness is that the parser tries `plus1` before `other1`.
For the string `((((1))))`, the `plus1` rule will first try applying `other1` but will eventually fail because it will not find the symbol `+`.
When `plus1` fails, it backtracks one symbol and retries `other1` again.
So, the amount of work for `other1` is doubled on every backtracking attempt.
This leads to $2^n$ attempts to parse with `other1`.
The exponential slowness is already apparent with $n=25$ or so.

JVM warmup does not lead to any speedup of the parsing, because the slowness is algorithmic.

The repeated parsing with the rule `other1` can be avoided if the resuls of the parsing are memoized.
The revised code attaches a `.memoize` call to `other1` but leaves all other parsing rules unchanged:

```scala
import io.chymyst.fastparse.Memoize
import io.chymyst.fastparse.Memoize.MemoizeParser

def program2[$: P]: P[String] = P(expr2 ~ End).!
def expr2[$: P]               = P(plus2 | other2)
def plus2[$: P]: P[_]         = P(other2 ~ "+" ~ expr2)
def other2[$: P]: P[_]        = P("1" | ("(" ~ expr2 ~ ")")).memoize

val n = 30
val input = "(" * n + "1" + ")" * n  // The string ((( ... (((1))) ... ))) is the input for the parser.
Memoize.parse(input, program1(_)) // Very fast.
```

## How it works

The parsing rule such as `other1` is a function of type `P[_] => P[_]`.
When that parsing rule is tried, its argument of type `P[_]` contains the current parsing context, including the current position in the input text.
The result is an updated parsing context, including the information about success or failure.
The entire updated parsing context is cached.
Whenever the same parsing rule is tried again at the same position in the input text, the cached result is returned immediately.

This works for most rules that do not have user-visible side effects.

## Limitations

1. For some `fastparse` grammars, memoization of certain rules will lead to incorrect parsing results (while memoizing other rules is perfectly fine).
To maintain correctness of parsing, memoization should be not be applied indiscriminately to all rules.
After adding some `memoize` calls, the resulting grammar should be tested against a comprehensive set of positive and negative parsing examples.

2. It will not be always obvious which parsing rules should be selected for memoization.
Performance may improve after memoizing some rules but not after memoizing some other rules.
Memoization should be stress-tested on realistic parsing examples to verify that parsing performance is actually improved.

3. Currently, `fastparse` has no protection against stack overflow.
Each memoization call introduces an extra function call between rule invocations.
This will make stack overflow occur earlier when parsing deeply nested input using memoized rules.
For this reason, a `fastparse` grammar with many memoized rules may actually create the stack overflow error on much smaller inputs than the same grammar without memoization.

The conclusion is that memoization should be applied only to a carefully selected, small subset of parsing rules and tested comprehensively (both for parsing correctness and for performance).
The memoized rules should be selected as the smallest set of rules such that the parsing performance improves after memoization while correctness is maintained.
