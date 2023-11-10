package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.test.ResourceFiles.enumerateResourceFiles
import io.chymyst.ui.dhall.Parser
import io.chymyst.ui.dhall.Syntax.{DhallFile, Expression}
import munit.FunSuite

import java.io.FileInputStream
import scala.util.Try

class DhallPreludeTest extends FunSuite {
  test("import with correct relative directory when several imports are done from the same file") {
    enumerateResourceFiles("dhall-lang/tests/type-inference/success", Some("preludeA.dhall"))
      .foreach { file =>
        val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
        expect(ourResult.resolveImports(file.toPath).isInstanceOf[Expression])
      }
  }

  test("can import each file from the standard prelude") {
    val results = enumerateResourceFiles("dhall-lang/Prelude", Some(".dhall"))
      .map { file =>
        val result = Try {
          //println(s"Parsing file ${file.getAbsolutePath}")
          val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
          //println(s"Resolving imports in file ${file.getAbsolutePath}")
          expect(ourResult.resolveImports(file.toPath).isInstanceOf[Expression])
        }
        if (result.isFailure) println(s"Failure for file $file: ${result.failed.get}")
        result
      }
    TestUtils.requireSuccessAtLeast(258, results)
  }

}
