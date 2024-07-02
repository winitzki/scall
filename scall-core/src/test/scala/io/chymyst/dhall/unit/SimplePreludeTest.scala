package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.dhall.Parser
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.{DhallFile, Expression}
import io.chymyst.dhall.unit.TestUtils.readToString
import io.chymyst.test.ResourceFiles.enumerateResourceFiles
import io.chymyst.test.{ResourceFiles, TestTimings, Throwables}

import java.io.FileInputStream
import scala.util.Try

class SimplePreludeTest extends DhallTest with TestTimings {

  test("read List/partition.dhall and beta-normalize it alone") {
    val expr = readToString(ResourceFiles.resourceAsFile("dhall-lang/Prelude/List/partition.dhall").get.toPath).dhall
    expect(expr.inferType.isValid)
    expr.betaNormalized
  }

  test("typecheck List/partition.dhall imports") {
    val problematic1 = "../List/partition.dhall"
    val path         = ResourceFiles.resourceAsFile("dhall-lang/Prelude/Natural/package.dhall").get.toPath
    expect(problematic1.dhall.resolveImports(path).inferType.isValid)
  }

  test("read Natural/sort.dhall and beta-normalize it alone") {
    val path = ResourceFiles.resourceAsFile("dhall-lang/Prelude/Natural/sort.dhall").get.toPath
    val expr = readToString(path).dhall
    expect(!expr.inferType.isValid) // Type-checking fails without first resolving imports.
    val resolved      = expr.resolveImports(path)
    expect(resolved.isInstanceOf[Expression])
    // println("resolved sort.dhall beta-normalized without typechecking:\n" + resolved.betaNormalized.print)
    val smallListTest = resolved("[3, 2]".dhall).betaNormalized
    println(s"sort [3, 2] = ${smallListTest.print}")
    println(s"sort [4, 3, 2, 1] = ${resolved("[4, 3, 2, 1]".dhall).betaNormalized}")
    resolved.inferType
    println(TestUtils.cacheStatistics())
    /*
    Alpha-normalization cache: Total requests: 936, cache hits: 54.06%, total cache size: 479
    Beta-normalization cache: Total requests: 4837, cache hits: 63.26%, total cache size: 2164
    Type-checking cache: Total requests: 2711, cache hits: 42.27%, total cache size: 1565
     */
  }

  test("typecheck Natural/sort.dhall imports") {
    val problematic0 = "let sort = ../Natural/sort.dhall in  assert : sort [ 3, 2 ] ≡ [ 2, 3 ]"
    val problematic1 = "let sort = ../Natural/sort.dhall in  assert : sort [ 3, 2, 1 ] ≡ [ 1, 2, 3 ]"
    val problematic2 = "let sort = ../Natural/sort.dhall in  assert : sort [ 3, 2, 1, 3, 2, 1 ] ≡ [ 1, 1, 2, 2, 3, 3 ]"
    val path         = ResourceFiles.resourceAsFile("dhall-lang/Prelude/Natural/package.dhall").get.toPath
    expect(problematic0.dhall.resolveImports(path).inferType.isValid)
    println(problematic0.dhall.resolveImports(path).print)
    expect(problematic1.dhall.resolveImports(path).inferType.isValid)
    println(problematic1.dhall.resolveImports(path).print)
    expect(problematic2.dhall.resolveImports(path).isInstanceOf[Expression])
    println(problematic2.dhall.resolveImports(path).print)
    expect(problematic2.dhall.resolveImports(path).inferType.isValid)
  }

  test("import dhall-lang/Prelude/Natural/package.dhall without hanging") {
    enumerateResourceFiles("dhall-lang/Prelude/Natural", Some("package.dhall")).foreach { file =>
      val Parsed.Success(DhallFile(_, _, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
      expect(ourResult.resolveImports(file.toPath).isInstanceOf[Expression])
    }
  }

  test("import dhall-lang/Prelude/package.dhall without hanging") {
    enumerateResourceFiles("dhall-lang/Prelude", Some("package.dhall")).filter(_.getAbsolutePath contains "dhall-lang/Prelude/package.dhall").foreach { file =>
      val Parsed.Success(DhallFile(_, _, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
      expect(ourResult.resolveImports(file.toPath).isInstanceOf[Expression])
      println(TestUtils.cacheStatistics())
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
          val Parsed.Success(DhallFile(_, _, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
//          println(s"${LocalDateTime.now} Resolving imports in file ${file.getAbsolutePath}")
          val (_, elapsed)                                  = elapsedNanos(expect(ourResult.resolveImports(file.toPath).isInstanceOf[Expression]))
          elapsed
        }
        if (result.isFailure) println(s"Failure for file $file: ${Throwables.printThrowable(result.failed.get)}")
//        else println(f"Success for file $file took ${result.get / 1000000.0}%2.2f ms")
        result
      }
    TestUtils.requireSuccessAtLeast(256, results)
  }

}
