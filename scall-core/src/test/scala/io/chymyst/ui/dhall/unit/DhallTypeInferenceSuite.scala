package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.ui.dhall.Parser
import io.chymyst.ui.dhall.Syntax.DhallFile
import io.chymyst.ui.dhall.TypeCheckResult.{Invalid, Valid}
import io.chymyst.ui.dhall.unit.TestUtils.enumerateResourceFiles
import munit.FunSuite

import java.io.{File, FileInputStream}
import scala.collection.immutable.Seq
import scala.util.Try

class DhallTypeInferenceSuite extends FunSuite {

  test("type inference success") {
    val results: Seq[Try[String]] = enumerateResourceFiles("tests/type-inference/success", Some("A.dhall"))
      .map { file =>
        val validationFile = new File(file.getAbsolutePath.replace("A.dhall", "B.dhall"))

        val result = Try {
          val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
          val Parsed.Success(DhallFile(_, validationResult), _) = Parser.parseDhallStream(new FileInputStream(validationFile))
          val x = ourResult.inferType match {
            case Valid(a) => a
          }
          val y = validationResult
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

  test("type inference failure") {
    val results: Seq[Try[String]] = enumerateResourceFiles("tests/type-inference/failure", Some(".dhall"))
      .map { file =>
        val result = Try {
          val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
          expect(!ourResult.inferType.isValid)
          file.getName
        }
        if (result.isFailure) println(s"${file.getName}: ${result.failed.get.getMessage}")
        result
      }
    println(s"Success count: ${results.count(_.isSuccess)}\nFailure count: ${results.count(_.isFailure)}")
    expect(results.count(_.isFailure) == 0)
  }

}
