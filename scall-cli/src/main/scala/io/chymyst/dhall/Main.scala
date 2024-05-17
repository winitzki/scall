package io.chymyst.dhall

import fastparse.Parsed
import io.chymyst.dhall.Syntax.{DhallFile, Expression}

import java.io.{InputStream, OutputStream}

object Main {

  def process(input: InputStream, output: OutputStream): Unit =
    Parser.parseDhallStream(input) match {
      case Parsed.Success(value: DhallFile, _) =>
        val result: String = value.value.typeCheckAndBetaNormalize() match {
          case TypecheckResult.Valid(expr: Expression) =>
            expr.print
          case TypecheckResult.Invalid(errors)         =>
            errors.toString
        }
        output.write(result.getBytes("UTF-8"))

      case failure: Parsed.Failure => output.write(s"Error parsing Dhall input: ${failure}\n${failure.extra}".getBytes("UTF-8"))
    }

  // $COVERAGE-OFF$
  def main(args: Array[String]) = {
    process(System.in, System.out)
  }
  // $COVERAGE-ON$
}
