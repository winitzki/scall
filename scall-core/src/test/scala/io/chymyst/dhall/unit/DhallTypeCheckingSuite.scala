package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.dhall.Parser
import io.chymyst.dhall.Syntax.DhallFile
import io.chymyst.dhall.TypecheckResult.Valid
import io.chymyst.dhall.unit.TestUtils.DhallTest

import java.io.{File, FileInputStream}
import scala.collection.immutable.Seq
import scala.util.Try

class DhallTypeCheckingSuite extends DhallTest {

  test("type inference success") {
    setupEnvironment {
      val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/type-inference/success", Some("A.dhall")).map { file =>
        val validationFile = new File(file.getAbsolutePath.replace("A.dhall", "B.dhall"))

        val result = Try {
          val Parsed.Success(DhallFile(_, ourResult), _)        = Parser.parseDhallStream(new FileInputStream(file))
          val Parsed.Success(DhallFile(_, validationResult), _) = Parser.parseDhallStream(new FileInputStream(validationFile))

          val resolved = ourResult.resolveImports(file.toPath)
          // println(s"DEBUG: ${file.getName} starting type inference, ourResult = ${ourResult.toDhall}, after resolving: $resolved")

          val x = resolved.inferType match {
            case Valid(a) => a
          }
          val y = validationResult
          if (x.toDhall != y.toDhall) println(s"ERROR: Results do not match.\nx.toDhall = ${x.toDhall}\ny.toDhall = ${y.toDhall}\n")
          expect(x.toDhall == y.toDhall, x == y)
          file.getName
        }
        if (result.isFailure) println(s"${file.getPath}: ${result.failed.get.getMessage}") // \n${Throwables.printThrowable(result.failed.get)})
        result
      }
      TestUtils.requireSuccessAtLeast(364, results)
    }
  }

  test("type inference failure") {
    setupEnvironment {
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
}
