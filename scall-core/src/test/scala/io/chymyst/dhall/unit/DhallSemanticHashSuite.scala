package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.{Parser, Semantics}
import io.chymyst.test.ResourceFiles.enumerateResourceFiles

import scala.util.Try

class DhallSemanticHashSuite extends DhallTest {
  test("dhall standard acceptance tests for semantic hash") {
    val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/semantic-hash/success", Some("A.dhall")).map { file =>
      val result = Try {
        val diagnosticString = TestUtils.readToString(file.getAbsolutePath.replace("A.dhall", "B.hash"))
        val expr             = Parser.parseDhall(TestUtils.readToString(file.getAbsolutePath)).get.value.value
        val ourHash          = "sha256:" + Semantics.semanticHash(expr, file.toPath)
        if (ourHash != diagnosticString) {
          println(s"Failure in file ${file.getAbsolutePath}")
          val resolved = expr.resolveImports(file.toPath)
          val alpha    = resolved.alphaNormalized
          val beta     = alpha.betaNormalized
          println(s"Resolving imports: $resolved\nAlpha-normalized:$alpha\nBeta-normalized:$beta\nCBOR model: ${beta.toCBORmodel}")
        }
        expect(ourHash == diagnosticString)
        file.getName
      }
      if (result.isFailure) println(s"${file.getAbsolutePath}: ${result.failed.get}")
      result
    }
    TestUtils.requireSuccessAtLeast(151, results)
  }
}
