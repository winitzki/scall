package io.chymyst.dhall

import io.chymyst.dhall.Syntax.{Expression, ExpressionScheme}
import io.chymyst.dhall.SyntaxConstants.FieldName

object Yaml {
  def yamlIndent(indent: Int) = " " * indent

  private def toYamlLines(expr: Expression, indent: Int): Either[String, (String, Seq[String])] = expr.scheme match {
    case ExpressionScheme.RecordLiteral(defs) =>
      val content = defs.map { case (FieldName(name), e: Expression) =>
        toYamlLines(e, indent).map(lines => (name, lines))
      }
      val errors  = content.collect { case Left(e) => e }
      if (errors.nonEmpty) Left(errors.mkString("; "))
      else {
        val valids = content.map { case Right(x) => x }
        val output = valids
          .map {
            case (name, (firstLine, Seq()))     => (name + ": " + firstLine, Seq())
            case (name, (firstLine, moreLines)) => (escapeYamlName(name) + ":", (firstLine +: moreLines).map(l => yamlIndent(indent) + l))
          }.flatMap { case (head, tail) => head +: tail }
        Right((output.head, output.tail))
      }
    case ExpressionScheme.EmptyList(_)        => Right(("[]", Seq()))

    case ExpressionScheme.NonEmptyList(exprs) =>
      val content = exprs.map(e => toYamlLines(e, indent))
      val errors  = content.collect { case Left(e) => e }
      if (errors.nonEmpty) Left(errors.mkString("; "))
      else {
        val valids = content.map { case Right(x) => x }
        val output = valids
          .map {
            case (firstLine, Seq())     => ("- " + firstLine, Seq())
            case (firstLine, moreLines) => ("- " + firstLine, moreLines.map(l => yamlIndent(indent) + l))
          }.flatMap { case (head, tail) => head +: tail }
        Right((output.head, output.tail))
      }

    case ExpressionScheme.NaturalLiteral(_) | ExpressionScheme.DoubleLiteral(_) | ExpressionScheme.TextLiteral(List(), _)             =>
      Right((expr.print, Seq()))
    case ExpressionScheme.ExprConstant(SyntaxConstants.Constant.True) | ExpressionScheme.ExprConstant(SyntaxConstants.Constant.False) =>
      Right((expr.print.toLowerCase, Seq()))

    case s => Left(s"Error: Unsupported expression type for Yaml export: ${expr.print}")
  }

  private def escapeYamlName(name: String): String = name match {
    case "y" | "n" | "no" | "off" | "on" | "yes" => s"'$name'"
    case _                                       => name
  }

  def toYaml(value: Expression, indent: Int = 2): Either[String, String] =
    toYamlLines(value, indent).map { case (first, next) => (first +: next).mkString("", "\n", "\n") }

}
