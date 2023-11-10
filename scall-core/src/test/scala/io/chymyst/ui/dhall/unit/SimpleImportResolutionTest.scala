package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.test.ResourceFiles.enumerateResourceFiles
import io.chymyst.ui.dhall.Parser.InlineDhall
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.Syntax.{DhallFile, Expression}
import io.chymyst.ui.dhall.SyntaxConstants.{Builtin, ConstructorName, FieldName, FilePrefix, ImportType, VarName}
import io.chymyst.ui.dhall.TypeCheck._Type
import io.chymyst.ui.dhall.TypeCheckResult.Valid
import io.chymyst.ui.dhall.{Parser, SyntaxConstants, TypeCheckResult}
import munit.FunSuite

import java.io.FileInputStream

class SimpleImportResolutionTest extends FunSuite {

  test("environment presets are parsed correctly for testing") {
    enumerateResourceFiles("dhall-lang/tests/import/success", Some("originHeadersENV.dhall"))
      .foreach { file =>
        val envs = DhallImportResolutionSuite.readHeadersFromEnv(file)
        expect(envs == Seq(("DHALL_HEADERS",
          """toMap {
            |  `httpbin.org:443` = toMap {
            |    `User-Agent` = "Dhall"
            |  }
            |}
            |""".stripMargin)))
      }
  }

  test("import chaining must compute correct paths") {
    val import1 = "/tmp/file1.dhall".dhall.scheme.asInstanceOf[Import[Expression]]
    val import2 = "/tmp/file2.dhall".dhall.scheme.asInstanceOf[Import[Expression]]
    val chained = import1 chainWith import2
    expect(chained.importType == ImportType.Path(FilePrefix.Absolute, SyntaxConstants.File(Seq("tmp", "file2.dhall"))))

    val import3 = "../tmp2/file3.dhall".dhall.scheme.asInstanceOf[Import[Expression]]
    val chained13 = import1 chainWith import3
    expect(chained13.canonicalize.importType == ImportType.Path(FilePrefix.Absolute, SyntaxConstants.File(Seq("tmp2", "file3.dhall"))))
  }

}
