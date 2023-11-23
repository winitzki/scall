package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.{Parser, Semantics}
import io.chymyst.test.ResourceFiles.enumerateResourceFiles
import io.chymyst.dhall.{Parser, Semantics}
import munit.FunSuite

import java.nio.file.{Files, Paths}
import scala.util.Try

class DhallSemanticHashSuite extends FunSuite {
  test("dhall standard acceptance tests for semantic hash") {
    val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/semantic-hash/success", Some("A.dhall")).map { file =>
      val result = Try {
        val diagnosticString = TestUtils.readToString((file.getAbsolutePath.replace("A.dhall", "B.hash")))
        val ourHash          = "sha256:" + Semantics.semanticHash(Parser.parseDhall(TestUtils.readToString((file.getAbsolutePath))).get.value.value, file.toPath)
        expect(ourHash == diagnosticString)
        file.getName
      }
      if (result.isFailure) println(s"${file.getName}: ${result.failed.get}")
      result
    }
    TestUtils.requireSuccessAtLeast(151, results)
  }
}
