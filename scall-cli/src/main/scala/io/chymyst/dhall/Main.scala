package io.chymyst.dhall

import fastparse.Parsed
import io.chymyst.dhall.Syntax.{DhallFile, Expression, ExpressionScheme}
import io.chymyst.dhall.SyntaxConstants.FieldName

import java.io.{InputStream, OutputStream}

object Main {

  sealed trait OutputMode
  object OutputMode {
    case object Dhall extends OutputMode
    case object Text  extends OutputMode
    case object Yaml  extends OutputMode
  }

  def process(input: InputStream, output: OutputStream, outputMode: OutputMode): Unit = {
    val outputString = Parser.parseDhallStream(input) match {
      case Parsed.Success(value: DhallFile, _) =>
        val result: String = value.value.typeCheckAndBetaNormalize() match {
          case TypecheckResult.Valid(expr: Expression) =>
            outputMode match {
              case OutputMode.Dhall => expr.print
              case OutputMode.Text  =>
                expr.scheme match {
                  case ExpressionScheme.TextLiteral(List(), trailing) => trailing
                  case s                                              => s"Error: Dhall expression should have type Text but is instead: $s"
                }
              case OutputMode.Yaml  => toYaml(expr.scheme).merge
            }

          case TypecheckResult.Invalid(errors) =>
            errors.toString
        }
        result

      case failure: Parsed.Failure => s"Error parsing Dhall input: $failure\n${failure.extra}"
    }
    output.write(outputString.getBytes("UTF-8"))
  }

  val yamlIndent = " " * 2

  def toYaml(value: Syntax.ExpressionScheme[Syntax.Expression], depth: Int = 0): Either[String, String] = value.scheme match {
    case ExpressionScheme.RecordLiteral(defs)                                                                             =>
      val content = defs.map(e => toYaml(e._2.scheme, depth + 1).map(e._1.name + ": " + _))
      val errors  = content.collect { case Left(e) => e }
      if (errors.nonEmpty) Left(errors.mkString("; "))
      else {
        val valids = content.collect { case Right(x) => x }
        val output = valids.mkString("\n" + yamlIndent * depth)
        Right(output)
      }
    case ExpressionScheme.NonEmptyList(exprs)                                                                             =>
      val content = exprs.map(e => toYaml(e.scheme, depth + 1))
      val errors  = content.collect { case Left(e) => e }
      if (errors.nonEmpty) Left(errors.mkString("; "))
      else {
        val valids = content.collect { case Right(x) => x }
        val output = yamlIndent * depth + "- " + valids.mkString("\n" + yamlIndent * depth)
        Right(output)
      }
    case ExpressionScheme.NaturalLiteral(_) | ExpressionScheme.DoubleLiteral(_) | ExpressionScheme.TextLiteral(List(), _) => Right(value.print)
    case s                                                                                                                => Left(s"Error: Unsupported expression type for Yaml export: $s")
  }

  def parseArgs(args: Array[String]): OutputMode =
    if (args contains "text") OutputMode.Text else if (args contains "yaml") OutputMode.Yaml else OutputMode.Dhall

  // $COVERAGE-OFF$
  def main(args: Array[String]) = {
    process(System.in, System.out, parseArgs(args))
  }
  // $COVERAGE-ON$
}
