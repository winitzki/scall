package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.test.ResourceFiles.enumerateResourceFiles
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.Syntax.{DhallFile, Expression}
import io.chymyst.ui.dhall.SyntaxConstants.{Builtin, ConstructorName, FieldName, VarName}
import io.chymyst.ui.dhall.TypeCheck._Type
import io.chymyst.ui.dhall.TypeCheckResult.Valid
import io.chymyst.ui.dhall.{Parser, TypeCheckResult}
import munit.FunSuite

import java.io.FileInputStream

class SimpleImportResolutionTest extends FunSuite {

  test("environment presets are parsed correctly for testing") {
    enumerateResourceFiles("dhall-lang/tests/import/success", Some("originHeadersENV.dhall"))
      .foreach { file =>
        val envs = DhallImportResolutionSuite.readHeadersFromEnv(file)
        expect(envs == Seq(("DHALL_HEADERS", """toMap {
                                               |  `httpbin.org:443` = toMap {
                                               |    `User-Agent` = "Dhall"
                                               |  }
                                               |}
                                               |""".stripMargin)))
      }
  }

}
