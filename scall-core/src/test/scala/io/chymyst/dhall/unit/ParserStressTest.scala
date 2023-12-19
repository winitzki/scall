package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.internal.Instrument
import io.chymyst.dhall.Grammar

class ParserStressTest extends DhallTest {

  test("tracing the parse of deeply nested parentheses") {
    val generate = { n: Int => "(" * n + "1" + ")" * n }
    val input    = generate(1)
    fastparse.parse(
      input,
      Grammar.complete_expression(_),
      instrument = new Instrument {
        override def beforeParse(parser: String, index: Int): Unit = ()
        // if (parser contains "expression") println(s"DEBUG ${"\t" * index} parser=$parser, index = $index, already parsed = '${input.slice(0, index)}'")

        override def afterParse(parser: String, index: Int, success: Boolean): Unit =
          if ((parser contains "e") && index == 3 && !success)
            println(
              s"DEBUG ${"\t" * index} parser=$parser ${if (success) "success" else "failure"}, index = $index, already parsed = '${input.slice(0, index)}'"
            )
      },
    )
  }

  test("simple grammar with no exponential slowness") {
    import fastparse.NoWhitespace._
    import fastparse._

//    def program[$: P]: P[Int] = P(expr ~ End)
//
//    def expr[$: P]: P[Int] = P(plus | ("(" ~ expr ~ ")"))
//
//    def number[$: P]: P[Int] = P(CharIn("0-9").rep(1).!.map(_.toInt))
//
//    def plus[$: P]: P[Int] = P(number | (expr ~ ("+" ~ expr).rep).map { case (i, is) => i + is.sum })

    def program[$: P]: P[Int] = P(plus ~ End)

    def plus[$: P]: P[Int] = P(xprimitive_expression ~ ("+" ~ xprimitive_expression).rep).map { case (i, is) => i + is.sum }

    def xprimitive_expression[$: P]: P[Int] = P(number | ("(" ~ plus ~ ")"))

    def number[$: P]: P[Int] = P(CharIn("0-9").rep(1).!.map(_.toInt))

    expect(parse("123", program(_)).get.value == 123)
    expect(parse("(123)", program(_)).get.value == 123)
    expect(parse("1+1+1", program(_)).get.value == 3)
    expect(parse("1+(1+1)", program(_)).get.value == 3)
    val n = 500
    expect(parse("(" * (n - 1) + "1" + ")" * (n - 1), program(_)).get.value == 1)
    expect(parse("1+" + "(1+" * (n - 1) + "1" + ")" * (n - 1), program(_)).get.value == n + 1)
  }

  test("simple grammar with exponential slowness") {
    import fastparse.NoWhitespace._
    import fastparse._

    def program[$: P]: P[Int] = P(expr ~ End)
    def expr[$: P]: P[Int]    = P(x_minus | x_plus)
    def x_minus[$: P]         = P(x_times ~ "-" ~ expr).map { case (x, y) => x - y }
    def x_plus[$: P]          = P(x_times ~ ("+" ~ expr).rep).map { case (i, is) => i + is.sum }
    def x_times[$: P]         = P(x_other ~ ("*" ~ x_other).rep).map { case (i, is) => i * is.product }
    def x_other[$: P]         = P(number | ("(" ~ expr ~ ")"))
    def number[$: P]          = P(CharIn("0-9").rep(1)).!.map(_.toInt)

    expect(parse("123", program(_)).get.value == 123)
    expect(parse("123+1", program(_)).get.value == 124)
    expect(parse("123*2", program(_)).get.value == 246)
    expect(parse("123*(1+1)", program(_)).get.value == 246)
    expect(parse("123*1+1", program(_)).get.value == 124)
    expect(parse("123*1-1", program(_)).get.value == 122)
    expect(parse("123*(1-1)", program(_)).get.value == 0)
    expect(parse("1-1-1", program(_)).get.value == 1) // Incorrect value due to wrong precedence in this toy grammar.
    expect(parse("1+2*3-(4-5)*6", program(_)).get.value == 1 + 2 * 3 - (4 - 5) * 6)

    (10 to 21).foreach { n =>
      val (_, elapsed) = elapsedNanos {
        expect(parse("(" * (n - 1) + "1" + ")" * (n - 1), program(_)).get.value == 1)
        expect(parse("1+" + "(1+" * (n - 1) + "1" + ")" * (n - 1), program(_)).get.value == n + 1)
      }
      println(f"Expression length = $n, elapsed = ${elapsed / 1000.0 / 1000000}%.2f")
    }

  }
}
