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

  test("simple grammar with exponential slowness") {
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

    def plus[$: P]: P[Int] = P(not_plus ~ ("+" ~ not_plus).rep).map { case (i, is) => i + is.sum }

    def not_plus[$: P]: P[Int] = P(number | ("(" ~ plus ~ ")"))

    def number[$: P]: P[Int] = P(CharIn("0-9").rep(1).!.map(_.toInt))

    expect(parse("123", program(_)).get.value == 123)
    expect(parse("(123)", program(_)).get.value == 123)
    expect(parse("1+1+1", program(_)).get.value == 3)
    expect(parse("1+(1+1)", program(_)).get.value == 3)
    val n = 500
    expect(parse("(" * (n - 1) + "1" + ")" * (n - 1), program(_)).get.value == 1)
    expect(parse("1+" + "(1+" * (n - 1) + "1" + ")" * (n - 1), program(_)).get.value == n + 1)
  }
}
