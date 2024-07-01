package io.chymyst.dhall

import io.chymyst.dhall.Syntax.ExpressionScheme.ExprBuiltin
import io.chymyst.dhall.Syntax.{Expression, ExpressionScheme}
import io.chymyst.dhall.SyntaxConstants.{Builtin, FieldName}

object Yaml {
  def yamlIndent(indent: Int) = " " * indent

  private def toYamlLines(expr: Expression, indent: Int): Either[String, Seq[String]] = expr.scheme match {
    case ExpressionScheme.RecordLiteral(Seq()) => Right(Seq("{}"))

    case ExpressionScheme.RecordLiteral(defs) =>
      val content = defs.map { case (FieldName(name), e: Expression) =>
        toYamlLines(e, indent).map(lines => (name, lines))
      }
      val errors  = content.collect { case Left(e) => e }
      if (errors.nonEmpty) Left(errors.mkString("; "))
      else {
        val valids = content.map { case Right(x) => x }
        val output = valids.flatMap {
          case (_, Seq())             => Seq[String]()
          case (name, Seq(firstLine)) => Seq(name + ": " + firstLine)
          case (name, lines)          => (escapeYamlName(name) + ":") +: lines.map(l => yamlIndent(indent) + l)
        }
        Right(output)
      }
    case ExpressionScheme.EmptyList(_)        => Right(Seq("[]")) // TODO: support Dhall toMap structures

    case ExpressionScheme.NonEmptyList(exprs) =>
      val content = exprs.map(e => toYamlLines(e, indent))
      val errors  = content.collect { case Left(e) => e }
      if (errors.nonEmpty) Left(errors.mkString("; "))
      else {
        val valids = content.map { case Right(x) => x }
        val output = valids.flatMap {
          case Seq()          => Seq()
          case Seq(firstLine) => Seq("- " + firstLine)
          case lines          => ("- " + lines.head) +: lines.tail.map(l => yamlIndent(indent) + l)
        }
        Right(output)
      }

    case ExpressionScheme.NaturalLiteral(_) | ExpressionScheme.DoubleLiteral(_) | ExpressionScheme.TextLiteral(List(), _)             =>
      Right(Seq(expr.print))
    case ExpressionScheme.ExprConstant(SyntaxConstants.Constant.True) | ExpressionScheme.ExprConstant(SyntaxConstants.Constant.False) =>
      Right(Seq(expr.print.toLowerCase))

    case ExpressionScheme.KeywordSome(expression: Expression) => toYamlLines(expression, indent)

    case ExpressionScheme.Application(Expression(ExprBuiltin(Builtin.None)), _) => Right(Seq())

    case _ => Left(s"Error: Unsupported expression type for Yaml export: ${expr.print}")
  }

  private val yamlBooleanNames = Set("y", "n", "yes", "no", "on", "off", "true", "false")

  private def escapeYamlName(name: String): String = {
    if (yamlBooleanNames contains name.toLowerCase) s"'$name'" else name
  }

  def toYaml(value: Expression, indent: Int = 2): Either[String, String] =
    toYamlLines(value, indent).map(_.mkString("", "\n", "\n"))

}
