package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.dhall.Parser
import io.chymyst.dhall.Syntax.DhallFile
import io.chymyst.dhall.TypecheckResult.Valid
import io.chymyst.test.ResourceFiles.enumerateResourceFiles

import java.io.{File, FileInputStream}
import scala.util.Try

class DhallTypeCheckingSuite extends DhallTest {

  test("type inference success") {
    val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/type-inference/success", Some("A.dhall")).map { file =>
      val validationFile = new File(file.getAbsolutePath.replace("A.dhall", "B.dhall"))

      val result = Try {
        val Parsed.Success(DhallFile(_, _, ourResult), _)        = Parser.parseDhallStream(new FileInputStream(file))
        val Parsed.Success(DhallFile(_, _, validationResult), _) = Parser.parseDhallStream(new FileInputStream(validationFile))
        // Resolve all imports without using the cache.
        val resolved                                             = ourResult.resolveImports(currentFile = file.toPath, enableCache = false)

        // println(s"DEBUG: ${file.getName} starting type inference, ourResult = ${ourResult.print}, after resolving: $resolved")

        val x        = resolved.inferType match {
          case Valid(a) => a
        }
        val y        = validationResult
        val xPrinted = x.print
        val yPrinted = y.print
        if (xPrinted != yPrinted) println(s"DEBUG: strings are not equal:\n$xPrinted\n$yPrinted")
        expect(xPrinted == yPrinted, x == y)

        file.getName
      }
      if (result.isFailure)
        println(
          s"DhallTypeCheckingSuite expected success but got: ${file.getPath}: ${result.failed.get.getMessage}"
        ) // \n${Throwables.printThrowable(result.failed.get)})
      result
    }
    TestUtils.requireSuccessAtLeast(364, results)
  }

  test("type inference failure") {
    val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/type-inference/failure", Some(".dhall")).map { file =>
      val result = Try {
        val Parsed.Success(DhallFile(_, _, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
        expect(!ourResult.resolveImports(file.toPath).inferType.isValid)
        file.getName
      }
      if (result.isFailure) println(s"DhallTypeCheckingSuite expected failure but got inferred type: ${file.getName}: ${result.failed.get.getMessage}")
      result
    }
    TestUtils.requireSuccessAtLeast(121, results)
  }

}
