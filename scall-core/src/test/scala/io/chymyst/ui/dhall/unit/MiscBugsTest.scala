package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import com.upokecenter.cbor.CBORObject
import io.chymyst.test.Throwables
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
    val results: Seq[Try[_]] = ("12:30:00.1111111" +: TestFixtures.timeLiterals).flatMap { t =>
      val x = t.dhall
      Seq(
        Try(expect(x.toDhall == t)),
        Try(cborRoundtrip(x)),
        Try(expect(Expression(CBORmodel.decodeCbor1(x.toCBORmodel.encodeCbor1).toScheme).toDhall == x.toDhall)),
        Try(expect(Expression(CBORmodel.decodeCbor1(x.toCBORmodel.encodeCbor1).toScheme) == x)),
        Try(expect(Expression(CBORmodel.decodeCbor2(x.toCBORmodel.encodeCbor2).toScheme).toDhall == x.toDhall)),
        Try(expect(Expression(CBORmodel.decodeCbor2(x.toCBORmodel.encodeCbor2).toScheme) == x)),
      )
    }
    println(results.filter(_.isFailure).map(_.failed.get).take(10).map(_.getMessage).mkString("\n"))
    TestUtils.requireSuccessAtLeast(TestFixtures.timeLiterals.length, results)
  }

}
