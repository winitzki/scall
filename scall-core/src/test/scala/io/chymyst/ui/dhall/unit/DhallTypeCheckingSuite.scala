package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.test.ResourceFiles.enumerateResourceFiles
import io.chymyst.ui.dhall.Parser
import io.chymyst.ui.dhall.Syntax.DhallFile
import io.chymyst.ui.dhall.TypecheckResult.Valid
import munit.FunSuite

import java.io.{File, FileInputStream}
import scala.collection.immutable.Seq
import scala.util.Try

class DhallTypeCheckingSuite extends FunSuite {

  test("type inference success") {
    val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/type-inference/success", Some("A.dhall")).map { file =>
      val validationFile = new File(file.getAbsolutePath.replace("A.dhall", "B.dhall"))

      val result = Try {
        val Parsed.Success(DhallFile(_, ourResult), _)        = Parser.parseDhallStream(new FileInputStream(file))
        val Parsed.Success(DhallFile(_, validationResult), _) = Parser.parseDhallStream(new FileInputStream(validationFile))
        println(s"DEBUG: ${file.getName} starting type inference")
        val x                                                 = ourResult.resolveImports(file.toPath).inferType match {
          case Valid(a) => a
        }
        val y                                                 = validationResult
        expect(x.toDhall == y.toDhall && x == y)
        file.getName
      }
      if (result.isFailure) println(s"${file.getPath}: ${result.failed.get.getMessage}") // \n${printFailure(result.failed.get)}")
      result
    }
    TestUtils.requireSuccessAtLeast(364, results)
  }

  test("type inference failure") {
    val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/type-inference/failure", Some(".dhall")).map { file =>
      val result = Try {
        val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
        expect(!ourResult.resolveImports(file.toPath).inferType.isValid)
        file.getName
      }
      if (result.isFailure) println(s"${file.getName}: ${result.failed.get.getMessage}")
      result
    }
    TestUtils.requireSuccessAtLeast(121, results)
  }

}