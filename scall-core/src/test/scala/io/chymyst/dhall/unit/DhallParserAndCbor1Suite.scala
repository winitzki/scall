package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.dhall.Syntax.{DhallFile, Expression}
import io.chymyst.dhall.{CBORmodel, Parser, Syntax}
import io.chymyst.test.ResourceFiles.enumerateResourceFiles

import java.io.{File, FileInputStream}
import java.nio.file.{Files, Paths}
import scala.util.{Failure, Success, Try}

class DhallParserAndCbor1Suite extends DhallTest {
  def testFilesForSuccess = enumerateResourceFiles("dhall-lang/tests/parser/success", Some(".dhall"))

  def testFilesForFailure = enumerateResourceFiles("dhall-lang/tests/parser/failure", Some(".dhall"))

  test("convert standard examples for successful parsing into CBOR") {
    val results = testFilesForSuccess.flatMap { file =>
      val r: Option[Syntax.Expression] = Try(Parser.parseDhallStream(new FileInputStream(file))).toOption.flatMap {
        case Parsed.Success(DhallFile(_, expr), _) => Some(expr)
        case _                                     => None
      }
      val result                       = r.map { expr => Try(expr.toCBORmodel.encodeCbor1) }
      if (result.exists(_.isFailure)) println(s"${file.getName}: failed parsing or converting file to CBOR1: ${result.get.failed.get.getMessage}")
      result
    }
    println(s"Success count: ${results.count(_.isSuccess)}\nFailure count: ${results.count(_.isFailure)}")
    TestUtils.requireSuccessAtLeast(284, results)
  }

  test("validate CBOR writing for standard examples") {
    val outDir        = "./testdhallb-cbor1"
    Try(Files.createDirectory(Paths.get(outDir)))
    val results       = testFilesForSuccess.flatMap { file =>
      val validationFile      = file.getAbsolutePath.replace("A.dhall", "B.dhallb")
      val cborValidationBytes = Files.readAllBytes(Paths.get(validationFile))
      val diagnosticFile      = file.getAbsolutePath.replace("A.dhall", "B.diag")
      val diagnosticString    = TestUtils.readToString(diagnosticFile)
      val result1             = for {
        cborValidationModel           <- Try(CBORmodel.decodeCbor1(cborValidationBytes).toString)
        Parsed.Success(dhallValue, _) <- Try(Parser.parseDhallStream(new FileInputStream(file)))
        model                         <- Try(dhallValue.value.toCBORmodel)
        bytesGeneratedByUs            <- Try(model.encodeCbor1)
      } yield (model, bytesGeneratedByUs, dhallValue.value, cborValidationModel)
      val result2             = result1.toOption.map { case (model, bytesGeneratedByUs, expression, cborValidationModel) =>
        Files.write(Paths.get(outDir + "/" + file.getName.replace("A.dhall", "A.dhallb")), bytesGeneratedByUs)
        val modelString = model.toString
        if (bytesGeneratedByUs sameElements cborValidationBytes) Success(file.getName)
        else if (modelString == diagnosticString) {
          val extraMessage =
            if (modelString != cborValidationModel) s"\nwhile our reading of the validation file also differs:\n\t\t$cborValidationModel" else ""
          Failure(
            new Exception(
              s"CBOR encoding differs, our expression is '$expression', but the generated CBOR model agrees with expected:\n\t\t$modelString$extraMessage\n"
            )
          )
        } else Failure(new Exception(s"CBOR model differs: our CBOR model is:\n$modelString\nbut the expected CBOR model is:\n$diagnosticString\n"))
      }
      if (result2.exists(_.isFailure)) println(s"CBOR validation failed for file ${file.getName}: ${result2.get.failed.get.getMessage}")
      result2
    }
    val failures      = results.count(_.isFailure)
    val modelFailures = results.filter(_.isFailure).count(_.failed.get.getMessage.contains("model differs"))
    println(s"Success count: ${results.count(_.isSuccess)}\nFailure count: $failures\nCBOR model mismatch count: $modelFailures")
    expect(failures == 0 && modelFailures == 0)
  }

  test("validate CBOR reading for standard examples") {
    val results = testFilesForSuccess.flatMap { file =>
      val validationFile      = file.getAbsolutePath.replace("A.dhall", "B.dhallb")
      val cborValidationBytes = Files.readAllBytes(Paths.get(validationFile))
      val diagnosticFile      = file.getAbsolutePath.replace("A.dhall", "B.diag")
      val diagnosticString    = TestUtils.readToString(diagnosticFile)
      val result1             = for {
        cborValidationModel             <- Try(CBORmodel.decodeCbor1(cborValidationBytes).toString)
        Parsed.Success(dhallValue, _)   <- Try(Parser.parseDhallStream(new FileInputStream(file)))
        model                           <- Try(dhallValue.value.toCBORmodel)
        bytesGeneratedByUs: Array[Byte] <- Try(model.encodeCbor1)
      } yield (model, bytesGeneratedByUs, dhallValue.value, cborValidationModel)
      val result2             = result1.toOption
        .map { case (model, bytesGeneratedByUs, expression, cborValidationModel) =>
          if (bytesGeneratedByUs sameElements cborValidationBytes) Success((model, expression))
          else if (model.toString == diagnosticString) {
            val extraMessage =
              if (model.toString != cborValidationModel) s"\nwhile our reading of the validation file also differs:\n\t\t$cborValidationModel" else ""
            Failure(
              new Exception(
                s"CBOR encoding differs, our expression is '$expression', but the generated CBOR model agrees with expected:\n\t\t$model$extraMessage\n"
              )
            )
          } else Failure(new Exception(s"CBOR model differs: our CBOR model is:\n${model.toString}\nbut the expected CBOR model is:\n$diagnosticString\n"))
        }.flatMap(_.toOption) // We ignore any failures with CBOR encoding because those failures are detected in the previous test.
      result2.map { case (model, expression) =>
        Try(model.toScheme == expression.scheme) match {
          case Failure(exception) => Failure(new Exception(s"${file.getName}: Parser crashed on model $model: $exception"))
          case Success(true)      => Success(file.getName)
          case Success(false)     =>
            Failure(new Exception(s"${file.getName}: After restoring from bytes, expression differs: expected:\n$expression\n\t\tbut got:\n${model.toScheme}"))
        }
      }

    }

    println(s"Success count: ${results.count(_.isSuccess)}\nFailure count: ${results
        .count(_.isFailure)}\nCBOR expression mismatch count: ${results.filter(_.isFailure).count(_.failed.get.getMessage.contains("expression differs"))}")
    results.filter(_.isFailure).map(_.failed.get.getMessage).foreach(println)
    expect(results.count(_.isFailure) == 0)
  }

  test("validate binary decoding/success") {
    val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/binary-decode/success", Some("A.dhallb")).map { file =>
      val validationFile = new File(file.getAbsolutePath.replace("A.dhallb", "B.dhall"))
      val cborBytes      = Files.readAllBytes(Paths.get(file.getAbsolutePath))
      val result         = Try {
        val diagnosticFile                = file.getAbsolutePath.replace("A.dhallb", "A.diag")
        val diagnosticString              = TestUtils.readToString(diagnosticFile)
        val ourExpr: Expression           = CBORmodel.decodeCbor1(cborBytes).toScheme
        val cborModelFromFileA: CBORmodel = CBORmodel.decodeCbor1(cborBytes)
        val Parsed.Success(dhallValue, _) = Parser.parseDhallStream(new FileInputStream(validationFile))
        val validationExpr                = dhallValue.value
        // We have read the CBOR file correctly.
        expect((ourExpr.toCBORmodel equals validationExpr.toCBORmodel) && (ourExpr equals validationExpr))
        expect(cborModelFromFileA.dhallDiagnostics == diagnosticString)
        file.getName
      }
      if (result.isFailure) println(s"${file.getName}: failure is: ${result.failed.get}")
      result
    }
    println(s"Success count: ${results.count(_.isSuccess)}\nFailure count: ${results.count(_.isFailure)}")
    TestUtils.requireSuccessAtLeast(82, results)
  }

  test("validate binary decoding/failure") {
    val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/binary-decode/failure", Some(".dhallb")).map { file =>
      val diagnosticFile = file.getAbsolutePath.replace(".dhallb", ".diag")
      val cborBytes      = Files.readAllBytes(Paths.get(file.getAbsolutePath))
      val result         = Try {
        val diagnosticString              = TestUtils.readToString(diagnosticFile)
        val cborModelFromFileA: CBORmodel = CBORmodel.decodeCbor1(cborBytes)
        // We have read the CBOR file correctly.
        expect(cborModelFromFileA.toString == diagnosticString)
        expect(Try(cborModelFromFileA.toScheme).isFailure)
        file.getName
      }
      if (result.isFailure) println(s"${file.getName}: ${result.failed.get.getMessage}")
      result
    }
    println(s"Success count: ${results.count(_.isSuccess)}\nFailure count: ${results.count(_.isFailure)}")
    TestUtils.requireSuccessAtLeast(9, results)
  }
}
