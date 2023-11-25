package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import com.upokecenter.cbor.CBORObject
import fastparse.Parsed
import io.chymyst.dhall.Parser
import io.chymyst.dhall.Syntax.Expression
import io.chymyst.test.ResourceFiles.enumerateResourceFiles
import io.chymyst.test.Throwables.printThrowable
import io.chymyst.dhall.CBORmodel.fromCbor2
import io.chymyst.dhall.Syntax.{DhallFile, Expression}
import io.chymyst.dhall.{CBOR, CBORmodel, Parser, Syntax}
import munit.FunSuite

import java.io.{File, FileInputStream}
import java.nio.file.{Files, Paths}
import scala.util.{Failure, Success, Try}

class DhallParserAndCbor2Suite extends FunSuite {

  def testFilesForSuccess = enumerateResourceFiles("dhall-lang/tests/parser/success", Some(".dhall"))

  def testFilesForFailure = enumerateResourceFiles("dhall-lang/tests/parser/failure", Some(".dhall"))

  test("parse standard examples for successful parsing") {
    val results = testFilesForSuccess.map { file =>
      val result = for {
        result1 <- Try(Parser.parseDhallStream(new FileInputStream(file))).recoverWith { case exception =>
                     Failure(new Exception(s"Parsing file ${file.getName} expecting success. Result: parser crashed with: ${printThrowable(exception)}"))
                   }
        result2 <- result1 match {
                     case Parsed.Success(value, index) => Success(value)
                     case Parsed.Failure(a, b, c)      =>
                       Failure(new Exception(s"Parsing file ${file.getName} expecting success. Result: $result1, diagnostics: ${c.stack}"))
                   }
      } yield result2
      result match {
        case Failure(exception) => println(exception.getMessage)
        case Success(value)     => println(file.getAbsolutePath)
      }
      result
    }
    TestUtils.requireSuccessAtLeast(286, results)
  }

  test("parse standard examples for failed parsing") {
    val results  = testFilesForFailure.map { file =>
      val result = Try {
        val Parsed.Success(result, _) = Parser.parseDhallStream(new FileInputStream(file))
        result
      }

      if (result.isSuccess) println(s"Parsing file ${file.getName} expecting failure. Result: unexpected success:\n\t\t\t${result.get}\n")
      result
    }
    val failures = results.count(_.isSuccess) // We expect that all examples fail to parse here.
    println(s"Success count: ${results.count(_.isFailure)}\nFailure count: $failures")
    expect(failures == 0)
  }

  test("convert standard examples for successful parsing into CBOR") {
    val results = testFilesForSuccess.flatMap { file =>
      val r: Option[Expression] = Try(Parser.parseDhallStream(new FileInputStream(file))).toOption.flatMap {
        case Parsed.Success(DhallFile(_, expr), _) => Some(expr)
        case _                                     => None
      }
      val result                = r.map { expr => Try(expr.toCBORmodel.encodeCbor2) }
      if (result.exists(_.isFailure)) println(s"${file.getName}: failed parsing or converting file to CBOR: ${result.get.failed.get.getMessage}")
      result
    }
    TestUtils.requireSuccessAtLeast(286, results)
  }

  test("validate CBOR writing for standard examples") {
    val outDir        = "./testdhallb"
    Try(Files.createDirectory(Paths.get(outDir)))
    val results       = testFilesForSuccess.flatMap { file =>
      val validationFile      = file.getAbsolutePath.replace("A.dhall", "B.dhallb")
      val cborValidationBytes = Files.readAllBytes(Paths.get(validationFile))
      val diagnosticFile      = file.getAbsolutePath.replace("A.dhall", "B.diag")
      val diagnosticString    = TestUtils.readToString(diagnosticFile)
      val result1             = for {
        cborValidationModel           <- Try(CBORmodel.decodeCbor2(cborValidationBytes).toString)
        Parsed.Success(dhallValue, _) <- Try(Parser.parseDhallStream(new FileInputStream(file)))
        model                         <- Try(dhallValue.value.toCBORmodel)
        bytesGeneratedByUs            <- Try(model.encodeCbor2)
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
    expect(failures <= 2 && modelFailures == 0) // Two failures are due to a bug in CBOR-Java. PR was already merged to fix that bug.
  }

  test("validate CBOR reading for standard examples") {
    val results = testFilesForSuccess.flatMap { file =>
      val validationFile      = file.getAbsolutePath.replace("A.dhall", "B.dhallb")
      val cborValidationBytes = Files.readAllBytes(Paths.get(validationFile))
      val diagnosticFile      = file.getAbsolutePath.replace("A.dhall", "B.diag")
      val diagnosticString    = TestUtils.readToString(diagnosticFile)
      val result1             = for {
        cborValidationModel           <- Try(CBORmodel.decodeCbor2(cborValidationBytes).toString)
        Parsed.Success(dhallValue, _) <- Try(Parser.parseDhallStream(new FileInputStream(file)))
        model                         <- Try(CBOR.toCborModel(dhallValue.value))
        bytesGeneratedByUs            <- Try(model.encodeCbor2)
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
          } else Failure(new Exception(s"CBOR model differs: our CBOR model is:\n$model\nbut the expected CBOR model is:\n$diagnosticString\n"))
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
        val ourExpr: Expression           = CBORmodel.decodeCbor2(cborBytes).toScheme
        val cborModelFromFileA: CBORmodel = fromCbor2(CBORObject.DecodeFromBytes(cborBytes))
        val Parsed.Success(dhallValue, _) = Parser.parseDhallStream(new FileInputStream(validationFile))
        val validationExpr                = dhallValue.value
        // We have read the CBOR file correctly.
        expect((ourExpr.toCBORmodel equals validationExpr.toCBORmodel) && (ourExpr equals validationExpr))
        expect(cborModelFromFileA.dhallDiagnostics == diagnosticString)
        file.getName
      }
      if (result.isFailure) println(s"${file.getName}: ${result.failed.get.getMessage}")
      result
    }
    TestUtils.requireSuccessAtLeast(82, results)
  }

  test("validate binary decoding/failure") {
    val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/binary-decode/failure", Some(".dhallb")).map { file =>
      val diagnosticFile = file.getAbsolutePath.replace(".dhallb", ".diag")
      val cborBytes      = Files.readAllBytes(Paths.get(file.getAbsolutePath))
      val result         = Try {
        val diagnosticString              = TestUtils.readToString(diagnosticFile)
        val cborModelFromFileA: CBORmodel = fromCbor2(CBORObject.DecodeFromBytes(cborBytes))
        // We have read the CBOR file correctly.
        expect(cborModelFromFileA.toString == diagnosticString)
        expect(Try(CBORmodel.decodeCbor2(cborBytes).toScheme).isFailure)
        file.getName
      }
      if (result.isFailure) println(s"${file.getName}: ${result.failed.get.getMessage}")
      result
    }
    TestUtils.requireSuccessAtLeast(9, results)
  }
}
