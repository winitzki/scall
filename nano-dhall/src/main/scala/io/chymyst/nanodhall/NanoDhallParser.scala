package io.chymyst.nanodhall

import fastparse.Parsed
import io.chymyst.fastparse.Memoize

final case class NanoDhallParser[R <: NanoExpr[R]](create: NanoExpr[R]) {

  def parse(input: String): R = parseToResult(input) match {
    case Parsed.Success(value: R, index) => value
    case failure: Parsed.Failure         =>
      Memoize.clearAll() // Parser will be re-run on trace(). So, the parser cache needs to be cleared.
      throw new Exception(s"Dhall parser error: ${failure.extra.trace().longMsg}")
  }

  def parseToResult(source: String): Parsed[R] = Memoize.parse(source, NanoDhallGrammar(create).complete_expression(_))

}

object NanoDhallParser {
  lazy val adtParser = NanoDhallParser(NanoExprADT.CreateADT)
}
