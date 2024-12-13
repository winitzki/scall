package io.chymyst.nanodhall

import fastparse.Parsed
import io.chymyst.fastparse.Memoize

final case class NanoDhallParser[R <: NanoExpr[R]](create: NanoExpr[R]) {
  implicit class StringAsDhallExpression(val input: String) {
    def dhall(create: NanoExpr[R]): R = parseToExpression(input)
  }

  def parseToExpression(input: String): R = parseDhall(input) match {
    case Parsed.Success(value: R, index) => value
    case failure: Parsed.Failure =>
      Memoize.clearAll() // Parser will be re-run on trace(). So, the parser cache needs to be cleared.
      throw new Exception(s"Dhall parser error: ${failure.extra.trace().longMsg}")
  }


  def parseDhall(source: String): Parsed[R] = Memoize.parse(source, NanoDhallGrammar(create).complete_expression(_))

}

object NanoDhallParser {
  lazy val adtParser = NanoDhallParser(NanoExprADT.CreateADT)
}
