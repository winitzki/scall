package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.Syntax.{DhallFile, Expression}
import io.chymyst.dhall.unit.SimpleCBORtest.cborRoundtrip
import io.chymyst.dhall.{CBORmodel, Parser}
import io.chymyst.test.ResourceFiles

import java.io.FileInputStream
import java.nio.file.{Files, Paths}
import scala.util.{Failure, Try}

class MiscBugsTest extends DhallTest with ResourceFiles {

  test("time literals with nanos") {
    val results: Seq[Try[_]] = ("12:30:00.1111111" +: TestFixtures.timeLiterals).flatMap { t =>
      val x = t.dhall
      Seq(
        Try(expect(x.print == t)),
        Try(cborRoundtrip(x)),
        Try(expect(Expression(CBORmodel.decodeCbor1(x.toCBORmodel.encodeCbor1).toScheme).print == x.print)),
        Try(expect(Expression(CBORmodel.decodeCbor1(x.toCBORmodel.encodeCbor1).toScheme) == x)),
        Try(expect(Expression(CBORmodel.decodeCbor2(x.toCBORmodel.encodeCbor2).toScheme).print == x.print)),
        Try(expect(Expression(CBORmodel.decodeCbor2(x.toCBORmodel.encodeCbor2).toScheme) == x)),
      )
    }
    println(results.filter(_.isFailure).map(_.failed.get).take(10).map(_.getMessage).mkString("\n"))
    TestUtils.requireSuccessAtLeast(results.length, results)
  }

  test("time literals with truncated nanos") {
    val results: Seq[Try[_]] = TestFixtures.timeLiteralsTruncated.flatMap { case (input, _) =>
      val output = input // We no longer truncate seconds fractions.
      val x      = input.dhall
      Seq(
        Try(expect(x.print == output)),
        Try(cborRoundtrip(x)),
        Try(expect(Expression(CBORmodel.decodeCbor1(x.toCBORmodel.encodeCbor1).toScheme).print == x.print)),
        Try(expect(Expression(CBORmodel.decodeCbor1(x.toCBORmodel.encodeCbor1).toScheme) == x)),
        Try(expect(Expression(CBORmodel.decodeCbor2(x.toCBORmodel.encodeCbor2).toScheme).print == x.print)),
        Try(expect(Expression(CBORmodel.decodeCbor2(x.toCBORmodel.encodeCbor2).toScheme) == x)),
      )
    }
    println(results.filter(_.isFailure).map(_.failed.get).take(10).map(_.getMessage).mkString("\n"))
    TestUtils.requireSuccessAtLeast(results.length, results)
  }

  // The file `time_literal/time_literal_test.cbor` was prepared with the Haskell version of dhall that truncates seconds fractions to 12 after-comma digits.
  test("cbor encoding for time literals with long fraction using cbor1") {
    val (input, expected)     = "09:00:00.0123456789012345678901234567890000000000" -> "09:00:00.0123456789010000000000000000000000000000"
    val fromCbor1: Expression = CBORmodel.decodeCbor1(Files.readAllBytes(resourceAsFile("time_literal/time_literal_test.cbor").get.toPath)).toScheme
    expect(input.dhall.print == input)
    expect(expected.dhall == fromCbor1)
  }

  test("cbor encoding for time literals with long fraction using cbor2") {
    val (input, expected)     = "09:00:00.0123456789012345678901234567890000000000" -> "09:00:00.0123456789010000000000000000000000000000"
    val fromCbor2: Expression = CBORmodel.decodeCbor2(Files.readAllBytes(resourceAsFile("time_literal/time_literal_test.cbor").get.toPath)).toScheme
    expect(input.dhall.print == input) // We no longer truncate seconds fractions.
    expect(expected.dhall == fromCbor2)
  }

  def runTestWithNanos(iterations: Int, initialSeconds: Int, createFiles: Boolean) = {
    // Encode "00:00:00.000000000000" with various numbers of zeros after comma.
    val results = (1 to iterations).map { i =>
      val inputString = f"00:00:$initialSeconds%02d." + "0" * i
      val input       = inputString.dhall
      val fileName    = s"time_literal_$initialSeconds.$i.cbor"
      if (createFiles) {
        import scala.sys.process._
        Try {
          Files.write(Paths.get("1.sh"), s"echo '$inputString' | dhall encode > $fileName".getBytes)
          "bash 1.sh".!
        }
      } else {
        val filePath        = resourceAsFile("time_literal/" + fileName).get.toPath
        val validationBytes = Files.readAllBytes(filePath)
        val fromCbor1       = CBORmodel.decodeCbor1(validationBytes)
        val fromCbor2       = CBORmodel.decodeCbor2(validationBytes)
        Try {
          expect(input.print == fromCbor1.toScheme.print)
          expect(input.scheme == fromCbor1.toScheme)
          expect(fromCbor1 == fromCbor2)
          expect(input.toCBORmodel == fromCbor1)
          expect(input.toCBORmodel.encodeCbor1 sameElements validationBytes)
          expect(input.toCBORmodel.encodeCbor2 sameElements validationBytes)
        } recoverWith { case t: Throwable =>
          println(s"Failure with file $fileName (valid CBOR model $fromCbor1): $t")
          Failure(t)
        }
      }
    }
    TestUtils.requireSuccessAtLeast(results.length, results)
  }

  test("cbor encoding for time literals of varying precision for 00:00:00.000000000000") {
    runTestWithNanos(30, 0, false)
  }

  test("cbor encoding for time literals of varying precision for 00:00:01.000000000000") {
    runTestWithNanos(30, 1, false)
  }

  test("type inference must use correct de Bruijn index, no imports") {
    val input =
      """
        |let Optional/null = λ(a : Type) →
        |      λ(xs : Optional a) →
        |        merge { Some = λ(_ : a) → False, None = True } xs
        |let Optional/fold = λ(a : Type) →
        |      λ(o : Optional a) →
        |      λ(optional : Type) →
        |      λ(some : a → optional) →
        |      λ(none : optional) →
        |        merge { Some = some, None = none } o
        |let equal : (∀(a: Type) -> ∀(_: ∀(_: a) -> ∀(_: a) -> Bool) -> ∀(_: Optional a) -> ∀(_: Optional a) -> Bool)  = λ(a: Type) -> λ(compare: ∀(_: a) -> ∀(_: a) -> Bool) -> λ(ox: Optional a) -> λ(oy: Optional a) -> Optional/fold a ox Bool (λ(x: a) -> Optional/fold a oy Bool (compare x) False) (Optional/null a oy)
        |in equal """.stripMargin.dhall
    input.inferType
  }

  test("type inference must use correct de Bruijn index, use imports") {
    val input =
      """
        |let Optional/null = ./null
        |let Optional/fold = ./fold
        |let equal : (∀(a: Type) -> ∀(_: ∀(_: a) -> ∀(_: a) -> Bool) -> ∀(_: Optional a) -> ∀(_: Optional a) -> Bool)  = λ(a: Type) -> λ(compare: ∀(_: a) -> ∀(_: a) -> Bool) -> λ(ox: Optional a) -> λ(oy: Optional a) -> Optional/fold a ox Bool (λ(x: a) -> Optional/fold a oy Bool (compare x) False) (Optional/null a oy)
        |in equal """.stripMargin.dhall
    input.resolveImports(resourceAsFile("dhall-lang/Prelude/Optional/all").get.toPath).inferType
  }

  test("failure in Mode/equal.dhall") {
    val fileName = "equal.dhall"
    enumerateResourceFiles("dhall-lang/Prelude/DirectoryTree/Mode", Some(fileName)) foreach { file =>
      println(s"Reading ${file.toPath}")
      val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
      ourResult.resolveImports(file.toPath).inferType
    }
  }
}
