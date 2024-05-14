package io.chymyst.dhall

import fastparse.Parsed

object Main {
  def main(args: Array[String]) = {
    Parser.parseDhallStream(System.in) match {
      case Parsed.Success(value, index) => System.out.println(value.value.typeCheckAndBetaNormalize().unsafeGet.print)
      case failure: Parsed.Failure      => System.err.println(s"Error parsing Dhall input: ${failure}\n${failure.extra}")
    }
  }
}
