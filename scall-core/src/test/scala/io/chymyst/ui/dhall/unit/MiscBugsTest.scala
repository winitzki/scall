package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import com.upokecenter.cbor.CBORObject
import io.chymyst.ui.dhall.CBORmodel.{CDouble, CMap, CString, CTagged}
import io.chymyst.ui.dhall.Parser.InlineDhall
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.Syntax.{Expression, ExpressionScheme}
import io.chymyst.ui.dhall.SyntaxConstants.{Builtin, Constant}
import io.chymyst.ui.dhall.unit.CBORtest.cborRoundtrip
import io.chymyst.ui.dhall.{CBOR, CBORmodel, Grammar}
import munit.FunSuite

import scala.util.Try

class MiscBugsTest extends FunSuite {

  test("time literals with nanos") {
    val results: Seq[Try[_]] = TestFixtures.timeLiterals.map { t =>
      Try {
        val x = t.dhall
        println(s"TimeLiteral $t converted back to ${x.toDhall}")
        expect(x.toDhall == t)
        cborRoundtrip(x)
        expect(Expression(CBORmodel.decodeCbor1(x.toCBORmodel.encodeCbor1).toScheme).toDhall == x.toDhall)
        expect(Expression(CBORmodel.decodeCbor1(x.toCBORmodel.encodeCbor1).toScheme) == x)
      }
    }
    TestUtils.requireSuccessAtLeast(TestFixtures.timeLiterals.length, results)
  }

}
