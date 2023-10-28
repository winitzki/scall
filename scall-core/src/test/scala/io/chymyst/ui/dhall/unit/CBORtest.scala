package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import com.upokecenter.cbor.CBORObject
import io.chymyst.ui.dhall.CBORmodel.{CDouble, CMap, CString, CTagged}
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.Syntax.{Expression, ExpressionScheme}
import io.chymyst.ui.dhall.SyntaxConstants.VarName
import io.chymyst.ui.dhall.unit.CBORtest.{bytesToCBORmodel, cborRoundtrip}
import io.chymyst.ui.dhall.{CBOR, CBORmodel, Grammar, SyntaxConstants}
import munit.FunSuite

import java.time.LocalTime

object CBORtest {
  def bytesToCBORmodel(bytes: Array[Byte]): CBORmodel = CBORmodel.fromCbor(CBORObject.DecodeFromBytes(bytes))

  def cborRoundtrip(expr: Expression) = {
    val aModel = CBOR.toCborModel(expr)

    val aBytes: Array[Byte] = aModel.toCBOR.EncodeToBytes
    val bModel: CBORmodel = bytesToCBORmodel(aBytes)

    val aModelString = aModel.toString
    val bModelString = bModel.toString
    expect(aModelString == bModelString)
  }
}

class CBORtest extends FunSuite {


  test("CBOR roundtrips 1") {
    cborRoundtrip(ExpressionScheme.ExprBuiltin(SyntaxConstants.Builtin.True))
    cborRoundtrip(ExpressionScheme.ExprBuiltin(SyntaxConstants.Builtin.List))
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

  test("CBOR roundtrips for half-precision float") {
    expect(CBORObject.FromObject(1.0).EncodeToBytes().length == 3)
    expect(CBORObject.FromObject(0.0f).EncodeToBytes().length == 5)
    expect(CBORObject.FromObject(-0.0f).EncodeToBytes().length == 5)
    expect(CBORObject.FromObject(0.0).EncodeToBytes().length == 5)
    expect(CBORObject.FromObject(-0.0).EncodeToBytes().length == 5)
  }

  test("CBOR roundtrips for half-precision double") {
    //expect(CBORObject.FromObject(-1.0).EncodeToBytes.length == 3)
    val mantissa = 0L
    val exponent = 1007L // Between 999 and 1008.
    val sign = 1L
    val obj = CBORObject.FromFloatingPointBits(((sign & 0x1) << 63) | ((exponent & 0x7ff) << 52) | (mantissa & 0xfffffffffffffL), 8)
    //    expect(obj.AsDoubleValue == -1.0)
    val bytes = obj.EncodeToBytes
    expect(bytes.length == 3)
    expect(bytesToCBORmodel(bytes).asInstanceOf[CDouble].data == -1.52587890625E-5)
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
    cborRoundtrip(TimeLiteral(LocalTime.of(12, 0)))
  }

  test("CBOR for dictionaries") {
    val dict = CMap(Map("a" -> CString("b")))
    val bytes = dict.toCBOR.EncodeToBytes
    val dictAfterBytes = bytesToCBORmodel(bytes)
    expect(dict == dictAfterBytes)
  }

  test("CBOR for tagged array") {
    val taggedDict = CTagged(4, CMap(Map("a" -> CString("b"))))
    val bytes = taggedDict.toCBOR.EncodeToBytes
    val dictAfterBytes = bytesToCBORmodel(bytes)
    expect(taggedDict == dictAfterBytes)
  }

  test("CBOR for strings containing newlines") {
    val s = CString("\n")
    val bytes = s.toCBOR.EncodeToBytes
    val sAfterBytes = bytesToCBORmodel(bytes)
    expect(s == sAfterBytes)
  }
}
