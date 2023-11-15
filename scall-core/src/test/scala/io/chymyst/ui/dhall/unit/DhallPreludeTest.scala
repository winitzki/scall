package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.test.ResourceFiles.enumerateResourceFiles
import io.chymyst.test.{ResourceFiles, TestTimeouts, Throwables}
import io.chymyst.ui.dhall.{Parser, Semantics, TypeCheck}
import io.chymyst.ui.dhall.Parser.InlineDhall
import io.chymyst.ui.dhall.Syntax.{DhallFile, Expression}
import munit.FunSuite

import java.io.FileInputStream
import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt
import scala.util.Try

class DhallPreludeTest extends FunSuite with TestTimeouts {

  test("typecheck List/partition.dhall imports") {
    val problematic1 = "../List/partition.dhall"
    val path = ResourceFiles.resourceAsFile("dhall-lang/Prelude/Natural/package.dhall").get.toPath
    expect(problematic1.dhall.resolveImports(path).inferType.isValid)
  }

  test("typecheck Natural/sort.dhall imports") {
    val problematic1 = "let sort = ../Natural/sort.dhall in  assert : sort [ 3, 2, 1 ] ≡ [ 1, 2, 3 ]"
    val problematic2 = "let sort = ../Natural/sort.dhall in  assert : sort [ 3, 2, 1, 3, 2, 1 ] ≡ [ 1, 1, 2, 2, 3, 3 ]"
    val path = ResourceFiles.resourceAsFile("dhall-lang/Prelude/Natural/package.dhall").get.toPath
//    println(problematic2.dhall.resolveImports(path).toDhall)
    expect(problematic1.dhall.resolveImports(path).inferType.isValid)
    expect(problematic2.dhall.resolveImports(path).isInstanceOf[Expression])
    expect(problematic2.dhall.resolveImports(path).inferType.isValid)
  }

  test("import dhall-lang/Prelude/Natural/package.dhall without hanging") {
    enumerateResourceFiles("dhall-lang/Prelude/Natural", Some("package.dhall"))
      .foreach { file =>
        val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
        expect(ourResult.resolveImports(file.toPath).isInstanceOf[Expression])
      }
  }

  test("import dhall-lang/Prelude/package.dhall without hanging") {
    enumerateResourceFiles("dhall-lang/Prelude", Some("package.dhall"))
      .filter(_.getAbsolutePath contains "dhall-lang/Prelude/package.dhall")
      .foreach { file =>
        val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
        expect(ourResult.resolveImports(file.toPath).isInstanceOf[Expression])
        println(s"Beta-normalization cache: ${Semantics.cacheBetaNormalize.statistics}\nType inference cache: ${TypeCheck.cacheTypeCheck.statistics}")
        /*
        Beta-normalization cache: Total requests: 176721, cache hits: 147798, total cache size: 28923
        Type inference cache: Total requests: 143975, cache hits: 53668, total cache size: 90307
         */
      }
  }

  test("resolve imports (but do not typecheck) each file from the standard prelude except two package.dhall files") {
    val results = enumerateResourceFiles("dhall-lang/Prelude", Some(".dhall"))
      .filterNot(_.getAbsolutePath contains "dhall-lang/Prelude/package.dhall")
      .filterNot(_.getAbsolutePath contains "dhall-lang/Prelude/Natural/package.dhall")
      .map { file =>
        val result = Try {
          //println(s"${LocalDateTime.now} Parsing file ${file.getAbsolutePath}")
          val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
//          println(s"${LocalDateTime.now} Resolving imports in file ${file.getAbsolutePath}")
          val (_, elapsed) = elapsedNanos(expect(ourResult.resolveImports(file.toPath).isInstanceOf[Expression]))
          elapsed
        }
        if (result.isFailure) println(s"Failure for file $file: ${Throwables.printThrowable(result.failed.get)}")
//        else println(f"Success for file $file took ${result.get / 1000000.0}%2.2f ms")
        result
      }
    TestUtils.requireSuccessAtLeast(256, results)
  }

}
