package io.chymyst.dhall

import fastparse.Parsed
import io.chymyst.dhall.Syntax.{DhallFile, Expression, ExpressionScheme}
import io.chymyst.dhall.SyntaxConstants.FieldName

import java.io.{InputStream, OutputStream}
import java.nio.file.{Files, Paths}

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

  def process(input: InputStream, output: OutputStream, outputMode: OutputMode = OutputMode.Dhall): Unit = {
    outputMode match {
      case OutputMode.Decode =>
        // TODO streamline those APIs
        output.write(Expression(CBORmodel.decodeCbor2(input.readAllBytes()).toScheme).print.getBytes("UTF-8"))
      case _                 =>
        val outputBytes = Parser.parseDhallStream(input) match {
          case Parsed.Success(value: DhallFile, _) =>
            val valueType           = value.value.inferType.map { t => (t, t.betaNormalized) }
            val result: Array[Byte] = valueType match {
              case TypecheckResult.Valid((tpe: Expression, expr: Expression)) =>
                outputMode match {
                  case OutputMode.Dhall   => expr.print.getBytes("UTF-8")
                  case OutputMode.Text    =>
                    (expr.scheme match {
                      case ExpressionScheme.TextLiteral(List(), trailing) => trailing
                      case s                                              => s"Error: Dhall expression should have type Text but is instead: $s"
                    }).getBytes("UTF-8")
                  case OutputMode.Yaml    => toYaml(expr.scheme).merge.getBytes("UTF-8")
                  case OutputMode.Encode  => expr.toCBORmodel.encodeCbor2
                  case OutputMode.GetType => tpe.print.getBytes("UTF-8")
                  case OutputMode.GetHash => Semantics.semanticHash(expr, Paths.get(".")).getBytes("UTF-8")
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

  val yamlIndent = " " * 2

  def toYaml(value: Syntax.ExpressionScheme[Syntax.Expression], depth: Int = 0): Either[String, String] = value.scheme match {
    case ExpressionScheme.RecordLiteral(defs)                                                                             =>
      val content = defs.map(e => toYaml(e._2.asInstanceOf[Expression].scheme, depth + 1).map(e._1.name + ": " + _))
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
  def main(args: Array[String]) = {
    process(System.in, System.out, parseArgs(args))
  }
  // $COVERAGE-ON$
}
