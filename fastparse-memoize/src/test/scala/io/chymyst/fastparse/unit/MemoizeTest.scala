package io.chymyst.fastparse.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.NoWhitespace._
import fastparse._
import io.chymyst.fastparse.Memoize
import io.chymyst.test.TestTimings
import munit.FunSuite

class MemoizeTest extends FunSuite with TestTimings {

  test("minimal slow grammar") {
    val n = 27

    // This grammar supports expressions of the form: 1+(1+1)+1.
    def program1[$: P]: P[String] = P(expr1 ~ End).!
    def expr1[$: P]: P[_]         = P((other1 ~ "+" ~ expr1) | other1)
    def other1[$: P]              = P("1" | ("(" ~ expr1 ~ ")"))

    val input               = "(" * (n - 1) + "1" + ")" * (n - 1)
    val (result1, elapsed1) = elapsedNanos(fastparse.parse(input, program1(_)))
    assert(result1.get.value == input)
    println(s"Elapsed without memoization: ${elapsed1 / 1e9}")

    // The same grammar with memoization for `other`.
    import io.chymyst.fastparse.Memoize.MemoizeParser
    def program2[$: P]: P[String] = P(expr2 ~ End).!
    def expr2[$: P]: P[_]         = P((other2 ~ "+" ~ expr2) | other2)
    def other2[$: P]              = P("1" | ("(" ~ expr2 ~ ")")).memoize

    val (result2, elapsed2) = elapsedNanos(Memoize.parse(input, program2(_)))
    assert(result2.get.value == input)
    println(s"Elapsed with memoization: ${elapsed2 / 1e9}")
    println(s"Speedup is ${elapsed1 / elapsed2}x")
  }

  test("slow grammar 2") {
    val n = 27

    // This grammar supports expressions of the form: 1+(1+1)+1.
    def program1[$: P]: P[String] = P(expr1 ~ End).!
    def expr1[$: P]               = P(plus1 | other1)
    def plus1[$: P]: P[_]         = P(other1 ~ "+" ~ expr1)
    def other1[$: P]: P[_]        = P("1" | ("(" ~ expr1 ~ ")"))
    val input                     = "(" * (n - 1) + "1" + ")" * (n - 1)
    val (result1, elapsed1)       = elapsedNanos(fastparse.parse(input, program1(_)))
    assert(result1.get.value == input)
    println(s"Elapsed without memoization: ${elapsed1 / 1e9}")

    // The same grammar with memoization for `other`.
    import io.chymyst.fastparse.Memoize.MemoizeParser
    def program2[$: P]: P[String] = P(expr2 ~ End).!
    def expr2[$: P]               = P(plus2 | other2)
    def plus2[$: P]: P[_]         = P(other2 ~ "+" ~ expr2)
    def other2[$: P]: P[_]        = P("1" | ("(" ~ expr2 ~ ")")).memoize

    val (result2, elapsed2) = elapsedNanos(Memoize.parse(input, program2(_)))
    assert(result2.get.value == input)
    println(s"Elapsed with memoization: ${elapsed2 / 1e9}")
    println(s"Speedup is ${elapsed1 / elapsed2}x")
  }

  test("slow grammar becomes faster after memoization") {
    // Integer calculator program: 1+2*3-(4-5)*6 and so on. No spaces, for simplicity.
    def program1[$: P]: P[Int] = P(expr1 ~ End)
    // Important: `... | minus1` does not make the grammar exponentially slow, but `minus1 | ...` does.
    def expr1[$: P]: P[Int]    = P(minus1 | plus1)
    def minus1[$: P]           = P(times1 ~ "-" ~ expr1).map { case (x, y) => x - y }
    def plus1[$: P]            = P(times1 ~ ("+" ~ expr1).rep).map { case (i, is) => i + is.sum }
    def times1[$: P]           = P(other1 ~ ("*" ~ other1).rep).map { case (i, is) => i * is.product }
    def other1[$: P]: P[Int]   = P(number | ("(" ~ expr1 ~ ")"))
    def number[$: P]           = P(CharIn("0-9").rep(1)).!.map(_.toInt)
    // Verify that this works as expected.
    assert(fastparse.parse("123*(1+1)", program1(_)).get.value == 246)
    assert(fastparse.parse("123*1+1", program1(_)).get.value == 124)
    assert(fastparse.parse("123*1-1", program1(_)).get.value == 122)
    assert(fastparse.parse("123*(1-1)", program1(_)).get.value == 0)

    // Parse an expression of the form `(((((...(1)...)))))`.
    val n                   = 23
    val (result1, elapsed1) = elapsedNanos(fastparse.parse("(" * (n - 1) + "1" + ")" * (n - 1), program1(_)))
    assert(result1.get.value == 1)

    // The same parsing after memoization.
    import io.chymyst.fastparse.Memoize.MemoizeParser
    def program2[$: P]: P[Int] = P(expr2 ~ End)
    def expr2[$: P]: P[Int]    = P(minus2 | plus2)
    def minus2[$: P]           = P(times2 ~ "-" ~ expr2).map { case (x, y) => x - y }
    def plus2[$: P]            = P(times2 ~ ("+" ~ expr2).rep).map { case (i, is) => i + is.sum }
    def times2[$: P]           = P(other2 ~ ("*" ~ other2).rep).map { case (i, is) => i * is.product }
    def other2[$: P]: P[Int]   = P(number | ("(" ~ expr2 ~ ")")).memoize

    // Warm up JVM.
    (1 to 10).foreach { _ =>
      Memoize.parse("(" * (n - 1) + "1" + ")" * (n - 1), program2(_))
    }

    val (result2, elapsed2) = elapsedNanos(Memoize.parse("(" * (n - 1) + "1" + ")" * (n - 1), program2(_)))
    assert(result2.get.value == 1)
    // Verify that the memoized parser works as expected.
    assert(Memoize.parse("123*(1+1)", program2(_)).get.value == 246)
    assert(Memoize.parse("123*1+1", program2(_)).get.value == 124)
    assert(Memoize.parse("123*1-1", program2(_)).get.value == 122)
    assert(Memoize.parse("123*(1-1)", program2(_)).get.value == 0)

    println(s"before memoization: ${elapsed1 / 1e9}, after memoization: ${elapsed2 / 1e9}, speedup: ${elapsed1 / elapsed2}x")
    // Memoization should speed up at least 200 times in this example, after JVM warmup.
    expect(elapsed1 > elapsed2 * 200)
  }
}
