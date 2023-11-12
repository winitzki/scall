package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.test.ResourceFiles.enumerateResourceFiles
import io.chymyst.test.TestTimeouts
import io.chymyst.ui.dhall.Parser
import io.chymyst.ui.dhall.Syntax.{DhallFile, Expression}
import munit.FunSuite

import java.io.FileInputStream
import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt
import scala.util.Try

class DhallPreludeTest extends FunSuite with TestTimeouts {
  test("can import each file from the standard prelude") {
    val results = enumerateResourceFiles("dhall-lang/Prelude", Some(".dhall"))
      .filterNot(_.getAbsolutePath contains "dhall-lang/Prelude/package.dhall") // This used to hang.
      .map { file =>
        val result = Try {
          println(s"${LocalDateTime.now} Parsing file ${file.getAbsolutePath}")
          val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
          println(s"${LocalDateTime.now} Resolving imports in file ${file.getAbsolutePath}")
          val (_, elapsed) = elapsedNanos(expect(ourResult.resolveImports(file.toPath).isInstanceOf[Expression]))
          elapsed
        }
        if (result.isFailure) println(s"Failure for file $file: ${result.failed.get}")
        else println(f"Success for file $file took ${result.get / 1000000.0}%2.2f ms")
        result
      }
    TestUtils.requireSuccessAtLeast(258, results)
  }

  test("import with correct relative directory when several imports are done from the same file") {
    enumerateResourceFiles("dhall-lang/tests/type-inference/success", Some("preludeA.dhall"))
      .foreach { file =>
        val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
        expect(ourResult.resolveImports(file.toPath).isInstanceOf[Expression])
      }
  }

}
