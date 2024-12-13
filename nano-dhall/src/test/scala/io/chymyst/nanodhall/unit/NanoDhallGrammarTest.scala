package io.chymyst.nanodhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.nanodhall.NanoExprADT._
import io.chymyst.nanodhall.{NanoConstant, NanoDhallParser, NanoOperator, VarName}
import munit.FunSuite

class NanoDhallGrammarTest extends FunSuite {

  val parser1 = NanoDhallParser.adtParser

  test("parse natural number") {
    expect(parser1.parseToExpression("0") == NaturalLiteral(BigInt(0)))
    expect(parser1.parseToExpression("123") == NaturalLiteral(BigInt(123)))
    expect(parser1.parseToExpression("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890") == NaturalLiteral(BigInt("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890")))
  }

  test("parse variable") {
    expect(parser1.parseToExpression("x") == Variable(VarName("x"), BigInt(0)))
    expect(parser1.parseToExpression("x @ 0") == Variable(VarName("x"), BigInt(0)))
    expect(parser1.parseToExpression("x @ 10") == Variable(VarName("x"), BigInt(10)))
  }

  test("parse addition") {
    expect(parser1.parseToExpression("0 + 1") == Operator(NaturalLiteral(BigInt(0)), NanoOperator.Plus, NaturalLiteral(BigInt(1))))
  }

  test("parse lambda") {
    expect(parser1.parseToExpression("\\(x : Natural) -> x + 1") == Lambda(VarName("x"), Constant(NanoConstant.Natural), Operator(Variable(VarName("x"), 0), NanoOperator.Plus, NaturalLiteral(1))))
  }


  test("parse lambda and applications") {
    expect(parser1.parseToExpression("(\\(x : Natural) -> f x + 1) 2") == Application(Lambda(VarName("x"), Constant(NanoConstant.Natural), Operator(Application(Variable(VarName("f"), 0), Variable(VarName("x"), 0)), NanoOperator.Plus, NaturalLiteral(1))), NaturalLiteral(2)))
  }

  test("parse forall") {
    expect(parser1.parseToExpression("(\\(x : Natural) -> x + 1) : forall (x : Natural) -> Natural") == Annotation(Lambda(VarName("x"), Constant(NanoConstant.Natural), Operator(Variable(VarName("x"), 0), NanoOperator.Plus, NaturalLiteral(1))), Forall(VarName("x"), Constant(NanoConstant.Natural), Constant(NanoConstant.Natural))))
  }
}
