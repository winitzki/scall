package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.{P, Parsed}
import io.chymyst.fastparse.Memoize.parse
import io.chymyst.dhall.Syntax.Expression
import io.chymyst.dhall.Syntax.ExpressionScheme.Variable
import io.chymyst.dhall.{Semantics, SyntaxConstants, TypeCheck}
import io.chymyst.test.{ManyFixtures, TestTimings}
import io.chymyst.test.Throwables.printThrowable
import munit.FunSuite

import java.nio.file.{Files, Path, Paths}
import scala.util.Try

object TestUtils extends ManyFixtures {

  def readToString(path: String): String = new String(Files.readAllBytes(Paths.get(path))).trim

  def readToString(path: Path): String = new String(Files.readAllBytes(path)).trim

  def v(name: String): Expression = Expression(Variable(SyntaxConstants.VarName(name), BigInt(0)))

  def checkMaybeLastPosition[A](parsed: Parsed[A], input: String, expectedResult: A, lastPosition: Option[Int] = None): Unit = {
    /*parsed match {
      case Parsed.Success(value, index)          =>
        println(s"Parsing input '$input', got Success($value, $index), expecting Success($expectedResult, _)")
      case Parsed.Failure(message, index, extra) =>
        println(s"Error: Parsing input '$input', expected Success($expectedResult, $index) but got Failure('$message', $index, ${Try(extra.stack).toOption})")
    }*/
    lastPosition match {
      case Some(lastIndex) => expect((input != null) && (parsed == Parsed.Success(expectedResult, lastIndex)))
      case None            => expect((input != null) && (parsed.get.value == expectedResult))
    }
  }

  // Do not verify the last parsed position.
  def check[A](grammarRule: P[_] => P[A], input: String, expectedResult: A): Unit = {
    val parsed = parse(input, grammarRule)
    checkMaybeLastPosition(parsed, input, expectedResult)
  }

  def check[A](grammarRule: P[_] => P[A], input: Array[Byte], expectedResult: A): Unit = {
    val parsed = parse(input, grammarRule)
    checkMaybeLastPosition(parsed, new String(input), expectedResult)
  }

  def check[A](grammarRule: P[_] => P[A], input: String, expectedResult: A, lastIndex: Int): Unit = {
    val parsed = parse(input, grammarRule)
    checkMaybeLastPosition(parsed, input, expectedResult, Some(lastIndex))
  }

  def check[A](grammarRule: P[_] => P[A], input: Array[Byte], expectedResult: A, lastIndex: Int): Unit = {
    val parsed = parse(input, grammarRule)
    checkMaybeLastPosition(parsed, new String(input), expectedResult, Some(lastIndex))
  }

  def toFail[A](grammarRule: P[_] => P[A], input: Array[Byte], lastIndex: Int): Unit = {
    val parsed = parse(input, grammarRule)
    parsed match {
      case Parsed.Success(value, index)              =>
        throw new Exception(s"Error: Parsing input '$input', expected Failure but got Success($value, $index)")
      case f @ Parsed.Failure(message, index, extra) =>
        println(
          s"Parsing input '$input', expected index $lastIndex, got Failure('$message', $index, ${Try(extra.stack).toOption}), message '${f.msg}' as expected"
        )
        expect(input != null && f.index == lastIndex)
    }
  }

  def toFail[A](grammarRule: P[_] => P[A], input: String, parsedInput: String, expectedMessage: String, lastIndex: Int): Unit = {
    val parsed = parse(input, grammarRule)
    parsed match {
      case Parsed.Success(value, index)              =>
        throw new Exception(s"Error: Parsing input '$input', expected Failure but got Success($value, $index)")
      case f @ Parsed.Failure(message, index, extra) =>
        println(
          s"Parsing input '$input', expected index $lastIndex, got Failure('$message', $index, ${Try(extra.stack).toOption}), message '${f.msg}' as expected"
        )
        expect(input != null && (f.msg contains expectedMessage), input != null && f.index == lastIndex)
    }
  }

  def check[A](successExamples: Seq[(String, A)], grammarRule: P[_] => P[A]): Unit = {
    val results = successExamples.map { case (s, d) =>
      Try(check(grammarRule(_), s, d, s.length))
    }
    if (results.forall(_.isSuccess))
      println(s"All ${successExamples.size} examples passed.")
    else {
      println(s"Error: ${results.count(_.isFailure)} examples failed:")
      val message = results.filter(_.isFailure).map(_.failed.get).map(printThrowable).mkString("\n\n")
      throw new Exception(message)
    }
  }

  def requireSuccessAtLeast(totalTests: Int, results: Seq[Try[_]], allowFailures: Int = 0) = {
    val (failures, successes) = failureAndSuccessCounts(totalTests, results, allowFailures)
    expect(failures <= allowFailures, successes >= totalTests - allowFailures)
  }

  def cacheStatistics(): String = {
    Seq(Semantics.cacheAlphaNormalize, Semantics.cacheBetaNormalize, TypeCheck.cacheTypeCheck)
      .map(cache => s"${cache.name}: ${cache.statistics}").mkString("\n")
  }

}

trait DhallTest extends FunSuite with TestTimings // Add more options here by mixing other traits.
