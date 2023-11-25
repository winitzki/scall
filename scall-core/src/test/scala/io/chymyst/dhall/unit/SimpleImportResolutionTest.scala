package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.test.ResourceFiles
import io.chymyst.test.ResourceFiles.{enumerateResourceFiles, resourceAsFile}
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.Syntax.{DhallFile, Expression}
import io.chymyst.dhall.SyntaxConstants.{Builtin, ConstructorName, FieldName, FilePrefix, ImportType, VarName}
import io.chymyst.dhall.TypeCheck._Type
import io.chymyst.dhall.TypecheckResult.Valid
import io.chymyst.dhall.unit.TestUtils.{DhallTest, UsingCaches}
import io.chymyst.dhall.{Parser, SyntaxConstants, TypecheckResult}
import munit.FunSuite

import java.io.FileInputStream
import java.nio.file.Paths
import scala.util.Try

class SimpleImportResolutionTest extends DhallTest {

  test("environment presets are parsed correctly for testing") {
    setupEnvironment {
      enumerateResourceFiles("dhall-lang/tests/import/success", Some("originHeadersENV.dhall")).foreach { file =>
        val envs = DhallImportResolutionSuite.readHeadersFromEnv(file)
        expect(
          envs == Seq(
            (
              "DHALL_HEADERS",
              """toMap {
                |  `httpbin.org:443` = toMap {
                |    `User-Agent` = "Dhall"
                |  }
                |}
                |""".stripMargin,
            )
          )
        )
      }
    }
  }

  test("import chaining must compute correct paths") {
    val import1 = "/tmp/file1.dhall".dhall.scheme.asInstanceOf[Import[Expression]]
    val import2 = "/tmp/file2.dhall".dhall.scheme.asInstanceOf[Import[Expression]]
    val chained = Import.chainWith(import1, import2)
    expect(chained.importType == ImportType.Path(FilePrefix.Absolute, SyntaxConstants.FilePath(Seq("tmp", "file2.dhall"))))

    val import3   = "../tmp2/file3.dhall".dhall.scheme.asInstanceOf[Import[Expression]]
    val chained13 = Import.chainWith(import1, import3)
    expect(chained13.canonicalize.importType == ImportType.Path(FilePrefix.Absolute, SyntaxConstants.FilePath(Seq("tmp2", "file3.dhall"))))
  }

  test("no loops in importing") {
    setupEnvironment {
      val file = resourceAsFile("dhall-lang/Prelude/Map/map.dhall").get.toPath.toString
      expect(file.dhall.resolveImports(Paths.get(file).getParent.resolve("package.dhall")).isInstanceOf[Expression])
    }
  }

  test("import alternatives inside expressions") {
    setupEnvironment {
      val file   = resourceAsFile("dhall-lang/Prelude/Bool/and.dhall").get.toPath.toString
      val parent = Paths.get(file).getParent.resolve("package.dhall")
      expect(Try("{a = missing}".dhall.resolveImports(parent)).isFailure)
      expect(s"{ a = missing } ? { a = $file } ? { a = missing }".dhall.resolveImports(parent).isInstanceOf[Expression])
    }
  }

  test("import . or .. or other invalid imports should fail") {
    Seq("{a = missing}", ".", "./", "./.", "./..", "./././..", "./../.././..", "/tmp").foreach { string =>
      expect(Try(string.dhall.resolveImports(Paths.get("."))).isFailure)
    }
  }
}
