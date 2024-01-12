package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.internal.{Instrument, Msgs}
import io.chymyst.dhall.Grammar

import scala.collection.mutable

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

  test("simple grammar with exponential slowness and manual memoization") {
    import fastparse.NoWhitespace._
    import fastparse._

    type R = Int // BigInt

    final case class PRunData(
      terminalMsgs: Msgs,
      aggregateMsgs: Msgs,
      shortMsg: Msgs,
      lastFailureMsg: Msgs,
      failureStack: List[(String, Int)],
      isSuccess: Boolean,
      logDepth: Int,
      index: Int,
      cut: Boolean,
      successValue: Any,
      verboseFailures: Boolean,
      noDropBuffer: Boolean,
      misc: collection.mutable.Map[Any, Any],
    ) {
      override def toString: String                               = {
        s"ParsingRun(index=$index, isSuccess = $isSuccess, successValue = $successValue)"
      }
      def assignToParsingRun[T](pr: ParsingRun[T]): ParsingRun[T] = { // Assign the mutable data to a given ParsingRun value.
        pr.terminalMsgs = terminalMsgs
        pr.aggregateMsgs = aggregateMsgs
        pr.shortMsg = shortMsg
        pr.lastFailureMsg = lastFailureMsg
        pr.failureStack = failureStack
        pr.isSuccess = isSuccess
        pr.logDepth = logDepth
        pr.index = index
        pr.cut = cut
        pr.successValue = successValue
        pr.verboseFailures = verboseFailures
        pr.noDropBuffer = noDropBuffer
        misc.foreach { case (k, v) => pr.misc.put(k, v) }
        pr
      }
    }

    object PRunData {
      def ofParsingRun[T](pr: ParsingRun[T]): PRunData = PRunData(
        pr.terminalMsgs,
        pr.aggregateMsgs,
        pr.shortMsg,
        pr.lastFailureMsg,
        pr.failureStack,
        pr.isSuccess,
        pr.logDepth,
        pr.index,
        pr.cut,
        pr.successValue,
        pr.verboseFailures,
        pr.noDropBuffer,
        mutable.Map.from(pr.misc),
      )
    }

    def cacheGrammar[R](cache: mutable.Map[Int, PRunData], parser: => P[_])(implicit p: P[_]): P[R] = {
      // The `parser` has not yet been run! And it is mutable. Do not run it twice!
      val cachedData: PRunData = cache.getOrElseUpdate(p.index, PRunData.ofParsingRun(parser))
      // After the `parser` has been run on `p`, the value of `p` changes and becomes equal to the result of running the parser.
      // If the result was cached, we need to assign it to the current value of `p`. This will imitate the side effect of running the parser again.
      cachedData.assignToParsingRun(p).asInstanceOf[P[R]]
    }

    def cacheGrammar0[R](cache: mutable.Map[Int, PRunData], parser: => P[_])(implicit p: P[_]): P[R] = {
      // The `parser` has not yet been run! And it is mutable. Do not run it twice!
      // After the `parser` has been run on `p`, the value of `p` changes and becomes equal to the result of running the parser.
      val initIndex = p.index
      (cache.get(initIndex) match {
        case Some(cachedPRun) =>
          cachedPRun.assignToParsingRun(p)
        case None             =>
          val result = parser // Evaluate this only once!
          cache.put(initIndex, PRunData.ofParsingRun(result))
          result
      }).asInstanceOf[P[R]]
    }

    val cache_minus = mutable.Map[Int, P[R]]()
    val cache_plus  = mutable.Map[Int, P[R]]()
    val cache_other = mutable.Map[Int, PRunData]()

    def clearCaches() = {
      cache_minus.clear()
      cache_plus.clear()
      cache_other.clear()
    }

    def program[$: P]: P[R]                       = P(expr ~ End)
    def expr[$: P]: P[R]                          = P(x_minus | x_plus)
    def x_minus[$: P]: P[R]                       = P(x_times ~ "-" ~ expr).map { case (x, y) => x - y }
    def x_minus_cached[$](implicit p: P[$]): P[R] = cache_minus.getOrElseUpdate(p.index, x_minus[$])
    def x_plus[$: P]: P[R]                        = P(x_times ~ ("+" ~ expr).rep).map { case (i, is) => i + is.sum }
    def x_plus_cached[$](implicit p: P[$]): P[R]  = cache_plus.getOrElseUpdate(p.index, x_plus[$])
    def x_times[$: P]: P[R]                       = P(x_other_cached ~ ("*" ~ x_other_cached).rep).map { case (i, is) => i * is.product }
    def x_other[$: P]: P[R]                       = P(number | ("(" ~ expr ~ ")"))
    def x_other_cached[$](implicit p: P[$]): P[R] = cacheGrammar(cache_other, x_other)
    def number[$: P]: P[R]                        = P(CharIn("0-9").rep(1)).!.map(_.toInt) // .map(x => BigInt(x))

    clearCaches()
    println(parse("123*(1+1)", program(_)))
    clearCaches()
    expect(parse("123*(1+1)", program(_)).get.value == 246)
    clearCaches()

    expect(parse("123", program(_)).get.value == 123)
    clearCaches()
    expect(parse("123+1", program(_)).get.value == 124)
    clearCaches()
    expect(parse("123*2", program(_)).get.value == 246)
    clearCaches()
    expect(parse("123*1+1", program(_)).get.value == 124)
    clearCaches()
    expect(parse("123*1-1", program(_)).get.value == 122)
    clearCaches()
    expect(parse("123*(1-1)", program(_)).get.value == 0)
    clearCaches()
    expect(parse("1-1-1", program(_)).get.value == 1) // Incorrect value due to wrong precedence in this toy grammar.
    clearCaches()
    expect(parse("1+2*3-(4-5)*6", program(_)).get.value == 1 + 2 * 3 - (4 - 5) * 6)
    clearCaches()

    (1 to 500).foreach { n => // Without memoization, this gets very slow around 20.
      val (_, elapsed) = elapsedNanos {
        clearCaches()
        expect(parse("(" * (n - 1) + "1" + ")" * (n - 1), program(_)).get.value == 1)
        clearCaches()
        expect(parse("1+" + "(1+" * (n - 1) + "1" + ")" * (n - 1), program(_)).get.value == n + 1)
        clearCaches()
      }
      println(f"Expression length = $n, elapsed = ${elapsed / 1000.0 / 1000000}%.2f")
    }
  }

}
