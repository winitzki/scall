package io.chymyst.dhall

import io.chymyst.dhall.CBORmodel.CString
import io.chymyst.dhall.Syntax.ExpressionScheme.{ExprBuiltin, RecordLiteral, TextLiteral}
import io.chymyst.dhall.Syntax.{DhallFile, Expression, ExpressionScheme}
import io.chymyst.dhall.SyntaxConstants.{Builtin, FieldName}

object Yaml {
  def yamlIndent(indent: Int) = " " * indent

  private def toYamlLines(expr: Expression, indent: Int): Either[String, Seq[String]] =
    expr.inferType match {
      case TypecheckResult.Invalid(errors) => Left(errors.toString)
      case TypecheckResult.Valid(tpe)      =>
        // Check if it has type List { mapKey : Text, mapValue : _ }.
        val isRecordMap = tpe.scheme match {
          case ExpressionScheme.Application(Expression(ExprBuiltin(Builtin.List)), Expression(ExpressionScheme.RecordType(defs))) =>
            val fieldMap: Map[FieldName, Expression] = defs.toMap
            fieldMap.keySet == Set(FieldName("mapKey"), FieldName("mapValue")) && fieldMap(FieldName("mapKey")) == Expression(ExprBuiltin(Builtin.Text))

          case _ => false
        }

        expr.scheme match {
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
                case (name, Seq(firstLine)) => Seq(escapeYamlName(name) + ": " + firstLine)
                // If the value of the record field is a multiline YAML, we will skip a line unless the first line is empty.
                case (name, lines)          =>
                  if (lines.head.isEmpty)
                    (escapeYamlName(name) + ": " + lines.tail.head) +: lines.tail.tail.map(l => yamlIndent(indent) + l)
                  else
                    (escapeYamlName(name) + ":") +: lines.map(l => yamlIndent(indent) + l)
              }
              Right(output)
            }
          case ExpressionScheme.EmptyList(_)        =>
            val emptyListOrRecord = if (isRecordMap) "{}" else "[]"
            Right(Seq(emptyListOrRecord))

          case ExpressionScheme.NonEmptyList(exprs) =>
            if (isRecordMap) { // Each expression in the list is a record { mapKey = x, mapValue = y }.
              // Generate the same yaml as for a record {x = y, ...}
              toYamlLines(
                Expression(RecordLiteral(exprs.map { case Expression(RecordLiteral(defs)) =>
                  val fieldMap: Map[FieldName, Expression] = defs.toMap
                  (FieldName(fieldMap(FieldName("mapKey")).scheme.asInstanceOf[TextLiteral[Expression]].trailing), fieldMap(FieldName("mapValue")))
                })),
                indent,
              )

            } else {
              val content = exprs.map(e => toYamlLines(e, indent))
              val errors  = content.collect { case Left(e) => e }
              if (errors.nonEmpty) Left(errors.mkString("; "))
              else {
                val valids = content.map { case Right(x) => x }
                val output = valids.flatMap {
                  case Seq()          => Seq()
                  case Seq(firstLine) => Seq("- " + firstLine)
                  // If the value of the list item is a multiline YAML, we will skip the first line if it is empty.
                  case lines          =>
                    val content = if (lines.head.isEmpty) lines.tail else lines
                    ("- " + content.head) +: content.tail.map(l => yamlIndent(indent) + l)
                }
                Right(output)
              }
            }

          case ExpressionScheme.TextLiteral(List(), trailing) =>
            if (trailing.contains("\n")) {
              val lines = trailing.split("\n").toSeq
              Right(Seq("", "|") ++ lines)
            } else Right(Seq(stringEscapeForYaml(trailing, expr)))

          case ExpressionScheme.NaturalLiteral(_) | ExpressionScheme.DoubleLiteral(_) =>
            Right(Seq(expr.print))

          case ExpressionScheme.ExprConstant(SyntaxConstants.Constant.True) | ExpressionScheme.ExprConstant(SyntaxConstants.Constant.False) =>
            Right(Seq(expr.print.toLowerCase))

          case ExpressionScheme.KeywordSome(expression: Expression) => toYamlLines(expression, indent)

          case ExpressionScheme.Application(Expression(ExprBuiltin(Builtin.None)), _) => Right(Seq())

          case _ => Left(s"Error: Unsupported expression type for Yaml export: ${expr.print} of type ${tpe.print}")
        }
    }

  private val yamlSpecialNames = Set("y", "n", "yes", "no", "on", "off", "true", "false", "null", "~")

  private def stringEscapeForYaml(str: String, expr: Expression): String = {
    if (yamlSpecialNames contains str.toLowerCase) "'" + str + "'"
    else if (str.matches("^[+-]?([0-9]+|\\.inf|nan|[0-9]*\\.[0-9]*)$")) "'" + str + "'"
    else if (str.matches("^([0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]|[0-9][0-9]:[0-9][0-9]:[0-9][0-9])$")) "'" + str + "'"
    else if (str.matches("^.*[-\":{}$\\[\\]\\\\*&#?|<>!%@].*$"))
      CString(
        str
      ).toString // \0, \x01, \x02, \x03, \x04, \x05, \x06, \a, \b, \t, \n, \v, \f, \r, \x0e, \x0f, \x10, \x11, \x12, \x13, \x14, \x15, \x16, \x17, \x18, \x19, \x1a, \e, \x1c, \x1d, \x1e, \x1f, \N, \_, \L, \P
    else str
  }

  private def escapeYamlName(name: String): String = {
    if (yamlSpecialNames contains name.toLowerCase) s"'$name'" else name
  }

  private def commentsToYaml(comments: String): String = {
    if (comments.isEmpty) "" else comments.split("\n").map(line => line.replaceFirst("^[ \\t]*--", "")).mkString("#", "\n#", "\n")
  }

  def toYaml(expression: Expression, indent: Int): Either[String, String] = {
    toYaml(DhallFile(Seq(), "", expression), indent)
  }

  def toYaml(dhallFile: DhallFile, indent: Int = 2): Either[String, String] = {
    toYamlLines(dhallFile.value, indent).map(_.mkString("", "\n", "\n")).map(yaml => commentsToYaml(dhallFile.headerComments) + yaml)
  }

}
