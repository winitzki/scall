package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import com.upokecenter.cbor.CBORObject
import io.chymyst.dhall.CBORmodel.{CDouble, CMap, CString, CTagged}
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.Syntax.{Expression, ExpressionScheme}
import io.chymyst.dhall.SyntaxConstants.Builtin
import io.chymyst.dhall.SyntaxConstants.Constant.True
import io.chymyst.dhall.unit.SimpleCBORtest.cborRoundtrip
import io.chymyst.dhall.{CBOR, CBORmodel, Grammar}

import java.util.Locale

object SimpleCBORtest {
  def cborRoundtrip(expr: Expression) = {
    val aModel = CBOR.toCborModel(expr)

    val aBytes: Array[Byte] = aModel.toCbor2.EncodeToBytes
    val bModel: CBORmodel   = CBORmodel.decodeCbor2(aBytes)

    val aModelString = aModel.toString
    val bModelString = bModel.toString
    expect(aModelString == bModelString)
  }
}

class SimpleCBORtest extends DhallTest {

  test("CBOR roundtrips 1") {
    cborRoundtrip(ExpressionScheme.ExprConstant(True))
    cborRoundtrip(ExpressionScheme.ExprBuiltin(Builtin.List))
  }

  test("CBOR roundtrips 2") {
    cborRoundtrip(NaturalLiteral(123))
    cborRoundtrip(DoubleLiteral(456.0))
    cborRoundtrip(DoubleLiteral(0.0))
    cborRoundtrip(DoubleLiteral(Double.NaN))
    cborRoundtrip(DoubleLiteral(Double.NegativeInfinity))
    cborRoundtrip(DoubleLiteral(Double.PositiveInfinity))
  }

  test("CBOR roundtrips 2a") {
    intercept[AssertionError] { // This fails because the CBOR library converts all doubles even after specifying the half-precision bits.
      cborRoundtrip(DoubleLiteral(-0.0))
    }
  }

  test("CBOR roundtrips for half-precision floats") {
    expect(CBORObject.FromObject(1.0).EncodeToBytes().length == 3)
    expect(CBORObject.FromObject(0.0f).EncodeToBytes().length == 3)
    expect(CBORObject.FromObject(-0.0f).EncodeToBytes().length == 3)
    expect(CBORObject.FromObject(0.0).EncodeToBytes().length == 3)
    expect(CBORObject.FromObject(-0.0).EncodeToBytes().length == 3)
  }

  test("CBOR roundtrips for half-precision double") {
    // expect(CBORObject.FromObject(-1.0).EncodeToBytes.length == 3)
    val mantissa = 0L
    val exponent = 1007L // Between 999 and 1008.
    val sign     = 1L
    val obj      = CBORObject.FromFloatingPointBits(((sign & 0x1) << 63) | ((exponent & 0x7ff) << 52) | (mantissa & 0xfffffffffffffL), 8)
    //    expect(obj.AsDoubleValue == -1.0)
    val bytes    = obj.EncodeToBytes
    expect(bytes.length == 3)
    expect(CBORmodel.decodeCbor2(bytes).asInstanceOf[CDouble].data == -1.52587890625e-5)
  }

  test("CBOR roundtrips 3") {
    cborRoundtrip(TextLiteral.ofText(Grammar.TextLiteralNoInterp("abcde")))
  }

  test("CBOR roundtrips 4") {
    cborRoundtrip(NonEmptyList[Expression](Seq(1, 2, 3, 4, 5).map(x => NaturalLiteral(x))))
  }

  test("CBOR roundtrips 5") {
    cborRoundtrip(Variable(underscore, BigInt(7)))
  }

  test("CBOR roundtrips 6") {
    cborRoundtrip(TimeLiteral(12, 0, 0, ""))
  }

  test("CBOR for dictionaries") {
    val dict           = CMap(Map("a" -> CString("b")))
    val bytes          = dict.toCbor2.EncodeToBytes
    val dictAfterBytes = CBORmodel.decodeCbor2(bytes)
    expect(dict == dictAfterBytes)
  }

  test("CBOR for tagged array") {
    val taggedDict     = CTagged(4, CMap(Map("a" -> CString("b"))))
    val bytes          = taggedDict.toCbor2.EncodeToBytes
    val dictAfterBytes = CBORmodel.decodeCbor2(bytes)
    expect(taggedDict == dictAfterBytes)
  }

  test("CBOR for strings containing newlines") {
    val s           = CString("\n")
    val bytes       = s.encodeCbor2
    val sAfterBytes = CBORmodel.decodeCbor2(bytes)
    expect(s == sAfterBytes)
  }

  test("CBOR1 for strings containing newlines") {
    val s           = CString("\n")
    val bytes       = s.encodeCbor1
    val sAfterBytes = CBORmodel.decodeCbor1(bytes)
    expect(s == sAfterBytes)
    expect(s.toString == "\"\\n\"")
  }

  test("CBOR3 for Double") {
    val s1 = CDouble(1.0)
    val s2 = CBORmodel.decodeCbor3(s1.encodeCbor1)
    println(s"s1 = $s1, s2 = $s2")
    expect(s1 == s2)
  }

  test("CBOR3 for simple integer encoding of variables") {
    val s1 = "_".dhall.toCBORmodel
    val s2 = CBORmodel.decodeCbor3(s1.encodeCbor1)
    println(s"s1 = $s1, s2 = $s2")
    expect(s1 == s2)
  }

  test("CBOR3 for simple integer encoding of variables") {
    val s1 = "_@3".dhall.toCBORmodel
    val s2 = CBORmodel.decodeCbor3(s1.encodeCbor1)
    println(s"s1 = $s1, s2 = $s2")
    expect(s1 == s2)
  }
}
