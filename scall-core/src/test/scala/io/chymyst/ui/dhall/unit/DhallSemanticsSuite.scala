package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.ui.dhall.Parser
import io.chymyst.ui.dhall.Semantics.alphaNormalize
import io.chymyst.ui.dhall.Syntax.DhallFile
import io.chymyst.ui.dhall.unit.TestUtils.enumerateResourceFiles
import munit.FunSuite

import java.io.{File, FileInputStream}
import scala.util.Try

class DhallSemanticsSuite extends FunSuite {

  test("alpha normalization success") {
    val results: Seq[Try[String]] = enumerateResourceFiles("tests/alpha-normalization/success", Some("A.dhall"))
      .map { file =>
        val validationFile = new File(file.getAbsolutePath.replace("A.dhall", "B.dhall"))

        val result = Try {
          val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhall(new FileInputStream(file))
          val Parsed.Success(DhallFile(_, validationResult), _) = Parser.parseDhall(new FileInputStream(validationFile))
          val x = ourResult.alphaNormalized
          val y = validationResult.alphaNormalized
          //  println(s"DEBUG: ${file.getName}: our parser gives ${ourResult.toDhall}, after alpha-normalization ${x.toDhall}")
          expect(x.toDhall == y.toDhall && x == y)
          file.getName
        }
        if (result.isFailure) println(s"${file.getName}: ${result.failed.get.getMessage}")
        result
      }
    println(s"Success count: ${results.count(_.isSuccess)}\nFailure count: ${results.count(_.isFailure)}")
    expect(results.count(_.isFailure) == 0)
  }

  test("beta normalization success") {
    val results: Seq[Try[String]] = enumerateResourceFiles("tests/normalization/success", Some("A.dhall"))
      .map { file =>
        val validationFile = new File(file.getAbsolutePath.replace("A.dhall", "B.dhall"))

        val result = Try {
          val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhall(new FileInputStream(file))
          val Parsed.Success(DhallFile(_, validationResult), _) = Parser.parseDhall(new FileInputStream(validationFile))
          val x = ourResult.betaNormalized
          val y = validationResult
          if (!(x.toDhall == y.toDhall && x == y)) println(s"DEBUG: ${file.getName}: our parser gives ${ourResult.toDhall}, after beta-normalization ${x.toDhall}")
          expect(x.toDhall == y.toDhall && x == y)
          file.getName
        }
        if (result.isFailure) println(s"${file.getName}: ${result.failed.get.getMessage}")
        result
      }
    println(s"Success count: ${results.count(_.isSuccess)}\nFailure count: ${results.count(_.isFailure)}")
    expect(results.count(_.isFailure) == 0)
  }
}
