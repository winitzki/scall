package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.test.ResourceFiles.enumerateResourceFiles
import io.chymyst.test.Throwables.printThrowable
import io.chymyst.ui.dhall.Parser
import io.chymyst.ui.dhall.Syntax.DhallFile
import munit.FunSuite

import java.io.{File, FileInputStream}
import scala.util.Try

class DhallSemanticsSuite extends FunSuite {

  test("alpha normalization success") {
    val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/alpha-normalization/success", Some("A.dhall")).map { file =>
      val validationFile = new File(file.getAbsolutePath.replace("A.dhall", "B.dhall"))

      val result = Try {
        val Parsed.Success(DhallFile(_, ourResult), _)        = Parser.parseDhallStream(new FileInputStream(file))
        val Parsed.Success(DhallFile(_, validationResult), _) = Parser.parseDhallStream(new FileInputStream(validationFile))
        val x                                                 = ourResult.alphaNormalized
        val y                                                 = validationResult.alphaNormalized
        //  println(s"DEBUG: ${file.getName}: our parser gives ${ourResult.toDhall}, after alpha-normalization ${x.toDhall}")
        expect(x.toDhall == y.toDhall && x == y)
        file.getName
      }
      if (result.isFailure) println(s"${file.getName}: ${result.failed.get.getMessage}")
      result
    }
    TestUtils.requireSuccessAtLeast(10, results)
  }

  test("beta normalization success") {
    val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/normalization/success", Some("A.dhall")).map { file =>
      val validationFile = new File(file.getAbsolutePath.replace("A.dhall", "B.dhall"))

      val result = Try {
        val Parsed.Success(DhallFile(_, ourResult), _)        = Parser.parseDhallStream(new FileInputStream(file))
        val Parsed.Success(DhallFile(_, validationResult), _) = Parser.parseDhallStream(new FileInputStream(validationFile))
        val x                                                 = ourResult.resolveImports(file.toPath).betaNormalized
        val y                                                 = validationResult.resolveImports(validationFile.toPath) // Should not normalize the validation result.

        if (x.toDhall != y.toDhall)
          println(
            s"DEBUG: ${file.getName}: The Dhall texts differ. Our parser gives:\n${ourResult.toDhall}\n\t\tafter beta-normalization:\n${x.toDhall}\n\t\texpected correct answer:\n${y.toDhall}\n"
          )
        else if (x != y)
          println(
            s"DEBUG: ${file.getName}: The expressions differ. Our parser gives:\n${ourResult.toDhall}\n\t\tafter beta-normalization:\n${x.toDhall}\n\t\tDhall texts are equal but expressions differ: our normalized expression is:\n$x\n\t\tThe expected correct expression is:\n$y\n"
          )

        expect(x.toDhall == y.toDhall && x == y)
        file.getName
      }
      if (result.isFailure)
        println(
          s"${file.getName}: ${result.failed.get}${printThrowable(result.failed.get).split("\n", -1).filter(_ contains "Semantics.scala").mkString("\n")}"
        )
      result
    }
    TestUtils.requireSuccessAtLeast(285, results)
  }
}
