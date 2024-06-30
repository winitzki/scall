package io.chymyst.dhall

import fastparse.Parsed
import io.chymyst.dhall.Syntax.{DhallFile, Expression, ExpressionScheme}

import java.io.{InputStream, OutputStream}
import java.nio.file.Paths

object Main {

  sealed trait OutputMode
  object OutputMode {
    case object Dhall   extends OutputMode
    case object Text    extends OutputMode
    case object Yaml    extends OutputMode
    case object Decode  extends OutputMode
    case object Encode  extends OutputMode
    case object GetType extends OutputMode
    case object GetHash extends OutputMode
  }

  def process(input: InputStream, output: OutputStream, outputMode: OutputMode): Unit = {
    outputMode match {
      case OutputMode.Decode =>
        // TODO streamline those APIs
        output.write(Expression(CBORmodel.decodeCbor2(input.readAllBytes()).toScheme).print.getBytes("UTF-8"))
      case _                 =>
        val outputBytes = Parser.parseDhallStream(input) match {
          case Parsed.Success(value: DhallFile, _) =>
            val valueType           = value.value.inferType.map { t => (t, value.value.betaNormalized) }
            val result: Array[Byte] = valueType match {
              case TypecheckResult.Valid((tpe: Expression, expr: Expression)) =>
                outputMode match {
                  case OutputMode.Dhall   => expr.print.getBytes("UTF-8")
                  case OutputMode.Text    =>
                    (expr.scheme match {
                      case ExpressionScheme.TextLiteral(List(), trailing) => trailing
                      case s                                              => s"Error: Dhall expression should have type Text but is instead: $s"
                    }).getBytes("UTF-8")
                  case OutputMode.Yaml    =>
                    Yaml.toYaml(expr).merge.getBytes("UTF-8")
                  case OutputMode.Encode  =>
                    expr.toCBORmodel.encodeCbor2
                  case OutputMode.GetType =>
                    tpe.print.getBytes("UTF-8")
                  case OutputMode.GetHash =>
                    ("sha256:" + Semantics.semanticHash(expr, Paths.get("."))).getBytes("UTF-8")
                }

              case TypecheckResult.Invalid(errors) =>
                errors.toString.getBytes("UTF-8")
            }
            result

          case failure: Parsed.Failure => s"Error parsing Dhall input: $failure\n${failure.extra}".getBytes("UTF-8")
        }
        output.write(outputBytes)
    }
  }

  def parseArgs(args: Array[String]): OutputMode = args(0) match {
    case "text"   => OutputMode.Text
    case "yaml"   => OutputMode.Yaml
    case "decode" => OutputMode.Decode
    case "encode" => OutputMode.Encode
    case "type"   => OutputMode.GetType
    case "hash"   => OutputMode.GetHash
    case _        => OutputMode.Dhall
  }

  // $COVERAGE-OFF$
  def main(args: Array[String]): Unit = {
    process(System.in, System.out, parseArgs(args))
  }
  // $COVERAGE-ON$
}
