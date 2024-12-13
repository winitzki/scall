package io.chymyst.nanodhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.nanodhall.NanoExprADT._
import io.chymyst.nanodhall.{NanoConstant, NanoDhallParser, NanoOperator, VarName}
import munit.FunSuite

class NanoDhallGrammarTest extends FunSuite {

  val parser1 = NanoDhallParser.adtParser

  test("parse natural number") {
    expect(parser1.parse("0") == NaturalLiteral(BigInt(0)))
    expect(parser1.parse("123") == NaturalLiteral(BigInt(123)))
    expect(parser1.parse("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890") == NaturalLiteral(BigInt("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890")))
  }

  test("parse variable") {
    expect(parser1.parse("x") == Variable(VarName("x"), BigInt(0)))
    expect(parser1.parse("x @ 0") == Variable(VarName("x"), BigInt(0)))
    expect(parser1.parse("x @ 10") == Variable(VarName("x"), BigInt(10)))
  }

  test("parse addition") {
    expect(parser1.parse("0 + 1") == Operator(NaturalLiteral(BigInt(0)), NanoOperator.Plus, NaturalLiteral(BigInt(1))))
  }

  test("parse lambda") {
    expect(parser1.parse("\\(x : Natural) -> x + 1") == Lambda(VarName("x"), Constant(NanoConstant.Natural), Operator(Variable(VarName("x"), BigInt(0)), NanoOperator.Plus, NaturalLiteral(BigInt(1)))))
  }


  test("parse lambda and applications") {
    expect(NaturalLiteral(0) == NaturalLiteral(BigInt(0)))
    expect(parser1.parse("(\\(x : Natural) -> f x + 1) 2") == Application(Lambda(VarName("x"), Constant(NanoConstant.Natural), Operator(Application(Variable(VarName("f"), BigInt(0)), Variable(VarName("x"), BigInt(0))), NanoOperator.Plus, NaturalLiteral(BigInt(1)))), NaturalLiteral(BigInt(2))))
  }

  test("parse forall") {
    expect(parser1.parse("(\\(x : Natural) -> x + 1) : forall (x : Natural) -> Natural") == Annotation(Lambda(VarName("x"), Constant(NanoConstant.Natural), Operator(Variable(VarName("x"), BigInt(0)), NanoOperator.Plus, NaturalLiteral(BigInt(1)))), Forall(VarName("x"), Constant(NanoConstant.Natural), Constant(NanoConstant.Natural))))
  }

  test("parse let-bindings") {
    val expected = Let(VarName("x"), NaturalLiteral(BigInt(1)), Let(VarName("y"), Variable(VarName("x"), BigInt(0)), Variable(VarName("y"), BigInt(0))))
    expect(parser1.parse("let x = 1 in let y = x in y") == expected)
    expect(parser1.parse("let x = 1 let y = x in y") == expected)
  }

}
