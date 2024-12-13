package io.chymyst.nanodhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.nanodhall.{NanoDhallParser, NanoExprADT}
import munit.FunSuite

class NanoDhallGrammarTest extends FunSuite {

  val parser1 = NanoDhallParser.adtParser

  test("parse natural number") {
    expect(parser1.parseToExpression("123") == NanoExprADT.NaturalLiteral(BigInt(123)))
  }
}
