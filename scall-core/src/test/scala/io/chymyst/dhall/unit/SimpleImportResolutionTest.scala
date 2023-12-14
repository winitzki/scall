package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.Expression
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.SyntaxConstants.{FilePrefix, ImportType}
import io.chymyst.dhall.{Semantics, SyntaxConstants}
import io.chymyst.test.ResourceFiles.{enumerateResourceFiles, resourceAsFile}

import java.nio.file.Paths
import scala.util.Try

class SimpleImportResolutionTest extends DhallTest {

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
    expect(chained.importType == ImportType.ImportPath(FilePrefix.Absolute, SyntaxConstants.FilePath(Seq("tmp", "file2.dhall"))))

    val import3   = "../tmp2/file3.dhall".dhall.scheme.asInstanceOf[Import[Expression]]
    val chained13 = Import.chainWith(import1, import3)
    expect(chained13.canonicalize.importType == ImportType.ImportPath(FilePrefix.Absolute, SyntaxConstants.FilePath(Seq("tmp2", "file3.dhall"))))
  }

  test("no loops in importing") {
    val file = resourceAsFile("dhall-lang/Prelude/Map/map.dhall").get.toPath
    expect(file.toString.dhall.resolveImports(file.getParent.resolve("package.dhall")).isInstanceOf[Expression])
  }

  test("alpha-normalize and beta-normalize imported file") {
    val file     = resourceAsFile("dhall-lang/tests/semantic-hash/success/prelude/Natural/enumerate/0A.dhall").get.toPath
    val resolved = TestUtils.readToString(file).dhall.resolveImports(file)
    println(s"Resolving imports gives:\n${resolved.print}")
    val alpha    = resolved.alphaNormalized
    println(s"Alpha-normalized:\n${alpha.print}")
    val beta     = alpha.betaNormalized
    println(s"Beta-normalized:\n${beta.print}\nCBOR model:\n${beta.toCBORmodel}")
  }

  test("exponential blowup in normal form") {
    val input   = """let drop = https://prelude.dhall-lang.org/List/drop
                  |let generate = https://prelude.dhall-lang.org/List/generate
                  |let f = \(g : Natural) -> generate g Natural (\(x : Natural) -> x)
                  |in \(g : Natural) -> \(n : Natural) -> drop n Natural (f g)
                  |""".stripMargin.dhall.resolveImports().typeCheckAndBetaNormalize().unsafeGet
    val results = (1 to 10).map { i => input(NaturalLiteral(i)).betaNormalized.print.length }
    expect(results.forall(_ < 2000))
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
