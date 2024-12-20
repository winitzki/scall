package io.chymyst.dhall

import fastparse.Parsed
import io.chymyst.dhall.Main.OutputMode.Decode
import io.chymyst.dhall.Syntax.{DhallFile, Expression, ExpressionScheme}
import io.chymyst.dhall.Yaml.YamlOptions
import mainargs.{Flag, Leftover, ParserForMethods, arg, main}

import java.io.{FileInputStream, FileOutputStream, InputStream, OutputStream}
import java.nio.file.{Path, Paths}
import java.time.LocalDateTime
import sourcecode.{File => SourceFile, Line => SourceLine}

object Main {

  sealed trait OutputMode

  object OutputMode {
    case object Dhall extends OutputMode

    case object Text extends OutputMode

    case object Yaml extends OutputMode

    case object Json extends OutputMode

    case object Toml extends OutputMode

    case object Decode extends OutputMode

    case object Encode extends OutputMode

    case object GetType extends OutputMode

    case object GetHash extends OutputMode
  }

  def process(path: Path, input: InputStream, output: OutputStream, outputMode: OutputMode, options: YamlOptions): Unit = {
    outputMode match {
      case OutputMode.Decode =>
        // TODO streamline those APIs
        output.write((Expression(CBORmodel.decodeCbor2(CBOR.java8ReadInputStreamToByteArray(input)).toScheme).print + "\n").getBytes("UTF-8"))

      case _ => // In all other modes, we need to evaluate the Dhall file to a normal form.
        val outputBytes = Parser.parseDhallStream(input) match {
          case Parsed.Success(dhallFile: DhallFile, _) =>
            val resolved            = dhallFile.value.resolveImports(path)
            val valueType           = resolved.inferType.map { t =>
              val normalForm = resolved.betaNormalized
              (t, normalForm)
            }
            val result: Array[Byte] = valueType match {
              case TypecheckResult.Valid((tpe: Expression, expr: Expression)) =>
                outputMode match {
                  case OutputMode.Dhall                  => (expr.print + "\n").getBytes("UTF-8")
                  case OutputMode.Text                   =>
                    (expr.scheme match {
                      case ExpressionScheme.TextLiteral(List(), trailing) => trailing + "\n"
                      case s                                              => s"Error: Dhall expression should have type Text but is instead: $s\n"
                    }).getBytes("UTF-8")
                  case OutputMode.Yaml | OutputMode.Json =>
                    (Yaml.toYaml(dhallFile.copy(value = expr), options) match {
                      case Left(value)  => value + "\n"
                      case Right(value) => value
                    }).getBytes("UTF-8")
                  case OutputMode.Encode                 =>
                    expr.toCBORmodel.encodeCbor2
                  case OutputMode.GetType                =>
                    (tpe.print + "\n").getBytes("UTF-8")
                  case OutputMode.GetHash                =>
                    ("sha256:" + Semantics.semanticHash(expr, Paths.get(".")) + "\n").getBytes("UTF-8")
                  case OutputMode.Toml                   =>
                    (Toml.toToml(expr) match {
                      case Left(value)  => value + "\n"
                      case Right(value) => value
                    }).getBytes("UTF-8")
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
    case Some("toml")   => OutputMode.Toml
    case Some("json")   => OutputMode.Json
    case Some("decode") => OutputMode.Decode
    case Some("encode") => OutputMode.Encode
    case Some("type")   => OutputMode.GetType
    case Some("hash")   => OutputMode.GetHash
    case _              => OutputMode.Dhall
  }

  val defaultIndent = 2

  // $COVERAGE-OFF$
  @main                                         // This method will be called by `ParserForMethods.runOrExist()` automatically.
  def `java -jar dhall.jar --flags... command`( // The name `dhall.jar` will be printed at the top of the help text.
    @arg(short = 'f', doc = "Path to the input Dhall file (default: stdin)")
    file: Option[String],
    @arg(short = 'o', doc = "Path to the output file (default: stdout)")
    output: Option[String],
    @arg(short = 'q', doc = "Quote all strings (for YAML output only; default is false)")
    quoted: Flag,
    @arg(short = 'd', doc = "Create a YAML file with document separators (for YAML output only; default is false)")
    documents: Flag,
    @arg(short = 'i', doc = "Indentation depth for JSON and YAML (default: 2)")
    indent: Option[Int],
    @arg(doc = "Optional command: decode, encode, hash, text, type, json, yaml, toml")
    command: Leftover[String],
  ): Unit = {
    val (inputPath, inputStream) = file match {
      case Some(inputFile) =>
        val path = Paths.get(inputFile)
        (path, new FileInputStream(path.toFile))
      case None            => (Paths.get("."), System.in)
    }
    val outputStream             = output match {
      case Some(outputFile) => new FileOutputStream(outputFile)
      case None             => System.out
    }
    val outputMode               = parseArgs(command.value.toArray)
    process(
      inputPath,
      inputStream,
      outputStream,
      outputMode,
      YamlOptions(
        quoteAllStrings = quoted.value,
        createDocuments = documents.value,
        indent = indent.getOrElse(defaultIndent),
        jsonFormat = outputMode == OutputMode.Json,
      ),
    )
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
  // $COVERAGE-ON$
}
