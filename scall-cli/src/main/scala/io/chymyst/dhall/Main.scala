package io.chymyst.dhall

import fastparse.Parsed

import java.io.{InputStream, OutputStream}

object Main {

  def process(input: InputStream, output: OutputStream): Unit =
    Parser.parseDhallStream(input) match {
      case Parsed.Success(value, index) =>
        val result = value.value.typeCheckAndBetaNormalize().unsafeGet.print.getBytes("UTF-8")
        output.write(result)

      case failure: Parsed.Failure => System.err.println(s"Error parsing Dhall input: ${failure}\n${failure.extra}")
    }

  // $COVERAGE-OFF$
  def main(args: Array[String]) = {
    process(System.in, System.out)
  }
  // $COVERAGE-ON$
}
