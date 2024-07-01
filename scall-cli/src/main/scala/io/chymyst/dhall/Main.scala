package io.chymyst.dhall

import fastparse.Parsed
import io.chymyst.dhall.Syntax.{DhallFile, Expression, ExpressionScheme}

import java.io.{FileInputStream, InputStream, OutputStream}
import java.nio.file.{Path, Paths}
import mainargs.{Flag, Leftover, ParserForMethods, arg, main}

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

  def process(path: Path, input: InputStream, output: OutputStream, outputMode: OutputMode): Unit = {
    outputMode match {
      case OutputMode.Decode =>
        // TODO streamline those APIs
        output.write((Expression(CBORmodel.decodeCbor2(input.readAllBytes()).toScheme).print + "\n").getBytes("UTF-8"))
      case _                 =>
        val outputBytes = Parser.parseDhallStream(input) match {
          case Parsed.Success(value: DhallFile, _) =>
            val resolved            = value.value.resolveImports(path)
            val valueType           = resolved.inferType.map { t => (t, resolved.betaNormalized) }
            val result: Array[Byte] = valueType match {
              case TypecheckResult.Valid((tpe: Expression, expr: Expression)) =>
                outputMode match {
                  case OutputMode.Dhall   => (expr.print + "\n").getBytes("UTF-8")
                  case OutputMode.Text    =>
                    (expr.scheme match {
                      case ExpressionScheme.TextLiteral(List(), trailing) => trailing + "\n"
                      case s                                              => s"Error: Dhall expression should have type Text but is instead: $s\n"
                    }).getBytes("UTF-8")
                  case OutputMode.Yaml    =>
                    (Yaml.toYaml(expr) match {
                      case Left(value)  => value + "\n"
                      case Right(value) => value
                    }).getBytes("UTF-8")
                  case OutputMode.Encode  =>
                    expr.toCBORmodel.encodeCbor2
                  case OutputMode.GetType =>
                    (tpe.print + "\n").getBytes("UTF-8")
                  case OutputMode.GetHash =>
                    ("sha256:" + Semantics.semanticHash(expr, Paths.get(".")) + "\n").getBytes("UTF-8")
                }

              case TypecheckResult.Invalid(errors) =>
                (errors.toString + "\n").getBytes("UTF-8")
            }
            result

          case failure: Parsed.Failure => s"Error parsing Dhall input: $failure\n${failure.extra}\n".getBytes("UTF-8")
        }
        output.write(outputBytes)
    }
  }

  def parseArgs(args: Array[String]): OutputMode = args.lastOption match {
    case Some("text")   => OutputMode.Text
    case Some("yaml")   => OutputMode.Yaml
    case Some("decode") => OutputMode.Decode
    case Some("encode") => OutputMode.Encode
    case Some("type")   => OutputMode.GetType
    case Some("hash")   => OutputMode.GetHash
    case _              => OutputMode.Dhall
  }

  // $COVERAGE-OFF$
  @main
  def run(
    @arg(short = 'f', doc = "Path to the input Dhall file")
    file: Option[String],
    command: Leftover[String],
  ): Unit = {
    val (path, inputStream) = file match {
      case Some(value) =>
        val path = Paths.get(value)
        (path, new FileInputStream(path.toFile))
      case None        => (Paths.get("."), System.in)
    }
    process(path, inputStream, System.out, parseArgs(command.value.toArray))
  }
  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
  // $COVERAGE-ON$
}
