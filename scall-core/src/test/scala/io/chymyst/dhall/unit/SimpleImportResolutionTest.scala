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
import io.chymyst.dhall.{Parser, Semantics, SyntaxConstants, TypecheckResult}
import munit.FunSuite

import java.io.FileInputStream
import java.nio.file.Paths
import scala.util.Try

class SimpleImportResolutionTest extends FunSuite {

  test("environment presets are parsed correctly for testing") {
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
    val file = resourceAsFile("dhall-lang/Prelude/Map/map.dhall").get.toPath.toString
    expect(file.dhall.resolveImports(Paths.get(file).getParent.resolve("package.dhall")).isInstanceOf[Expression])
  }

  test("import alternatives inside expressions") {
    val file   = resourceAsFile("dhall-lang/Prelude/Bool/and.dhall").get.toPath.toString
    val parent = Paths.get(file).getParent.resolve("package.dhall")
    expect(Try("{a = missing}".dhall.resolveImports(parent)).isFailure)
    expect(s"{ a = missing } ? { a = $file } ? { a = missing }".dhall.resolveImports(parent).isInstanceOf[Expression])
  }

  test("import . or .. or other invalid imports should fail") {
    Seq("{a = missing}", ".", "./", "./.", "./..", "./././..", "./../.././..", "/tmp").foreach { string =>
      expect(Try(string.dhall.resolveImports(Paths.get("."))).isFailure)
    }
  }

  test("do not recover from sha mismatch") {
    val file  = resourceAsFile("dhall-lang/Prelude/Bool/and.dhall").get.toPath
    val expr1 = "./and.dhall sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ? 0".dhall
    expect(Try(expr1.resolveImports(file)).isFailure)
    val expr2 = "./and.dhall sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".dhall
    expect(Try(expr2.resolveImports(file)).isFailure)
  }

  test("correct sha256 check") {
    val expr1 = "./some/import.dhall as Location".dhall
    expect(Semantics.semanticHash(expr1.resolveImports(), Paths.get(".")) == "de93ce8633ee0cbc9c4d4351cafcd82965c5a7c221d28ad194900ead38887617")
    val expr2 = "./some/import.dhall sha256:efc43103e49b56c5bf089db8e0365bbfc455b8a2f0dc6ee5727a3586f85969fd as Location".dhall
    expect(Semantics.semanticHash(expr2.resolveImports(), Paths.get(".")) == "de93ce8633ee0cbc9c4d4351cafcd82965c5a7c221d28ad194900ead38887617")
  }

}
