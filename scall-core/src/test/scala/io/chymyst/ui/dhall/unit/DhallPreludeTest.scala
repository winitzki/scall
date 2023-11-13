package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.test.ResourceFiles.enumerateResourceFiles
import io.chymyst.test.{ResourceFiles, TestTimeouts}
import io.chymyst.ui.dhall.Parser
import io.chymyst.ui.dhall.Parser.InlineDhall
import io.chymyst.ui.dhall.Syntax.{DhallFile, Expression}
import munit.FunSuite

import java.io.FileInputStream
import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt
import scala.util.Try

class DhallPreludeTest extends FunSuite with TestTimeouts {

  test("typecheck Natural/package.dhall imports") {
    val imports = Seq(
//       "./build.dhall",
//       "./enumerate.dhall",
//       "./even.dhall",
//       "./fold.dhall",
//       "./isZero.dhall",
//       "./odd.dhall",
//       "./product.dhall",
//       "./sum.dhall",
//       "./show.dhall",
//       "./toDouble.dhall",
//       "./toInteger.dhall",
//       "./lessThan.dhall",
//       "./lessThanEqual.dhall",
//       "./equal.dhall",
//       "./greaterThanEqual.dhall",
//       "./greaterThan.dhall",
//       "./min.dhall",
//       "./max.dhall",
//       "./listMin.dhall",
       "../List/partition.dhall",
      "./sort.dhall",
//            "./subtract.dhall",
    )
    val path = ResourceFiles.resourceAsFile("dhall-lang/Prelude/Natural/package.dhall").get.toPath
    imports.foreach {i =>
      println(s"Resolving imports in $i")
      i.dhall.resolveImports(path)
    }

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
      }
  }

  test("import each file from the standard prelude except two package.dhall files") {
    val results = enumerateResourceFiles("dhall-lang/Prelude", Some(".dhall"))
      .filterNot(_.getAbsolutePath contains "dhall-lang/Prelude/package.dhall")
      .filterNot(_.getAbsolutePath contains "dhall-lang/Prelude/Natural/package.dhall")
      .map { file =>
        val result = Try {
          //println(s"${LocalDateTime.now} Parsing file ${file.getAbsolutePath}")
          val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
          println(s"${LocalDateTime.now} Resolving imports in file ${file.getAbsolutePath}")
          val (_, elapsed) = elapsedNanos(expect(ourResult.resolveImports(file.toPath).inferType.isValid))
          elapsed
        }
        if (result.isFailure) println(s"Failure for file $file: ${result.failed.get}")
        else println(f"Success for file $file took ${result.get / 1000000.0}%2.2f ms")
        result
      }
    TestUtils.requireSuccessAtLeast(256, results)
  }

}
