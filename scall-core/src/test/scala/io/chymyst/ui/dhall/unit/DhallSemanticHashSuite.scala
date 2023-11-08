package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.ui.dhall.Syntax.Expression
import io.chymyst.ui.dhall.Syntax.ExpressionScheme.BytesLiteral
import io.chymyst.ui.dhall.unit.TestUtils.printFailure
import io.chymyst.ui.dhall.{Parser, Semantics}
import munit.FunSuite

import java.nio.file.{Files, Paths}
import scala.util.Try

class DhallSemanticHashSuite extends FunSuite {
  test("dhall standard acceptance tests for semantic hash") {
    val results:Seq[Try[String]] = TestUtils.enumerateResourceFiles("dhall-lang/tests/semantic-hash/success", Some("A.dhall")).map { file =>
      val result = Try{
        val diagnosticString = Files.readString(Paths.get(file.getAbsolutePath.replace("A.dhall", "B.hash"))).trim
        val ourHash = "sha256:" + Semantics.semanticHash(Parser.parseDhall(Files.readString(Paths.get(file.getAbsolutePath))).get.value.value, file.toPath.getParent)
        expect(ourHash == diagnosticString)
        file.getName
      }
      if (result.isFailure) println(s"${file.getName}: ${result.failed.get}")
      result
    }
    val failures = results.count(_.isFailure)
    println(s"Success count: ${results.count(_.isSuccess)}\nFailure count: $failures")
    expect(failures == 0)
  }
}
