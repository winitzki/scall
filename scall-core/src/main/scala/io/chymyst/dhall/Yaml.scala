package io.chymyst.dhall

import io.chymyst.dhall.CBORmodel.CString
import io.chymyst.dhall.Syntax.ExpressionScheme.{ExprBuiltin, RecordLiteral, TextLiteral}
import io.chymyst.dhall.Syntax.{DhallFile, Expression, ExpressionScheme}
import io.chymyst.dhall.SyntaxConstants.{Builtin, FieldName}

object Yaml {
  def yamlIndent(indent: Int) = " " * indent

  final case class YamlOptions(quoteAllStrings: Boolean = false, indent: Int = 2)

  sealed trait LineType

  case object YRecord    extends LineType
  case object YArray     extends LineType
  case object YPrimitive extends LineType

  final case class YamlLines(ltype: LineType, lines: Seq[String])

  private def toYamlLines(expr: Expression, options: YamlOptions): Either[String, YamlLines] =
    expr.inferType match {
      case TypecheckResult.Invalid(errors) => Left(errors.toString)
      case TypecheckResult.Valid(tpe)      =>
        // Check if it has type List { mapKey : Text, mapValue : _ }.
        lazy val isRecordMap = tpe.scheme match {
          case ExpressionScheme.Application(Expression(ExprBuiltin(Builtin.List)), Expression(ExpressionScheme.RecordType(defs))) =>
            val fieldMap: Map[FieldName, Expression] = defs.toMap
            fieldMap.keySet == Set(FieldName("mapKey"), FieldName("mapValue")) && fieldMap(FieldName("mapKey")) == Expression(ExprBuiltin(Builtin.Text))

          case _ => false
        }

        expr.scheme match {
          case ExpressionScheme.RecordLiteral(Seq()) => Right(YamlLines(YRecord, Seq("{}")))

          case ExpressionScheme.RecordLiteral(defs) =>
            val content: Seq[Either[String, (String, YamlLines)]] = defs.map { case (FieldName(name), e: Expression) =>
              toYamlLines(e, options).map(lines => (name, lines))
            }
            val errors                                            = content.collect { case Left(e) => e }
            if (errors.nonEmpty) Left(errors.mkString("; "))
            else {
              val valids                 = content.map { case Right(x) => x }
              val output: Seq[YamlLines] = valids.map {
                case (_, YamlLines(t, Seq()))                      => YamlLines(t, Seq[String]())
                case (name, YamlLines(YPrimitive, Seq(firstLine))) => YamlLines(YRecord, Seq(escapeYamlName(name, options) + ": " + firstLine))
                // If the value of the record field is a multiline YAML, we will skip a line unless the first line is empty.
                case (name, YamlLines(_, lines))                   =>
                  if (lines.head.isEmpty)
                    YamlLines(YRecord, (escapeYamlName(name, options) + ": " + lines.tail.head) +: lines.tail.tail.map(l => yamlIndent(options.indent) + l))
                  else
                    YamlLines(YRecord, (escapeYamlName(name, options) + ":") +: lines.map(l => yamlIndent(options.indent) + l))
              }
              Right(YamlLines(YRecord, output.flatMap(_.lines)))
            }
          case ExpressionScheme.EmptyList(_)        =>
            val emptyListOrRecord = if (isRecordMap) "{}" else "[]"
            Right(YamlLines(if (isRecordMap) YArray else YRecord, Seq(emptyListOrRecord)))

          case ExpressionScheme.NonEmptyList(exprs) =>
            if (isRecordMap) { // Each expression in the list is a record { mapKey = x, mapValue = y }.
              // Generate the same yaml as for a record {x = y, ...}
              toYamlLines(
                Expression(RecordLiteral(exprs.map { case Expression(RecordLiteral(defs)) =>
                  val fieldMap: Map[FieldName, Expression] = defs.toMap
                  (FieldName(fieldMap(FieldName("mapKey")).scheme.asInstanceOf[TextLiteral[Expression]].trailing), fieldMap(FieldName("mapValue")))
                })),
                options,
              )

            } else {
              val content = exprs.map(e => toYamlLines(e, options))
              val errors  = content.collect { case Left(e) => e }
              if (errors.nonEmpty) Left(errors.mkString("; "))
              else {
                val valids                 = content.map { case Right(x) => x }
                val output: Seq[YamlLines] = valids.map {
                  case YamlLines(t, Seq())          => YamlLines(t, Seq())
                  case YamlLines(_, Seq(firstLine)) => YamlLines(YArray, Seq("- " + firstLine))
                  // If the value of the list item is a multiline YAML, we will skip the first line if it is empty.
                  case YamlLines(_, lines)          =>
                    val content = if (lines.head.isEmpty) lines.tail else lines
                    YamlLines(YArray, ("- " + content.head) +: content.tail.map(l => yamlIndent(options.indent) + l))
                }
                Right(YamlLines(YArray, output.flatMap(_.lines)))
              }
            }

          case ExpressionScheme.TextLiteral(List(), trailing) =>
            if (trailing.contains("\n")) {
              val lines = trailing.split("\n").toSeq
              Right(YamlLines(YPrimitive, Seq("", "|") ++ lines))
            } else Right(YamlLines(YPrimitive, Seq(stringEscapeForYaml(trailing, options))))

          case ExpressionScheme.NaturalLiteral(_) | ExpressionScheme.DoubleLiteral(_) =>
            Right(YamlLines(YPrimitive, Seq(expr.print)))

          case ExpressionScheme.ExprConstant(SyntaxConstants.Constant.True) | ExpressionScheme.ExprConstant(SyntaxConstants.Constant.False) =>
            Right(YamlLines(YPrimitive, Seq(expr.print.toLowerCase)))

          case ExpressionScheme.KeywordSome(expression: Expression) => toYamlLines(expression, options)

          case ExpressionScheme.Application(Expression(ExprBuiltin(Builtin.None)), _) => Right(YamlLines(YPrimitive, Seq()))

          case _ => Left(s"Error: Unsupported expression type for Yaml export: ${expr.print} of type ${tpe.print}")
        }
    }

  private val yamlSpecialNames = Set("y", "n", "yes", "no", "on", "off", "true", "false", "null", "~")

  private def stringEscapeForYaml(str: String, options: YamlOptions): String = {
    if (yamlSpecialNames contains str.toLowerCase) "'" + str + "'"
    else if (str.matches("^[+-]?([0-9]+|\\.inf|nan|[0-9]*\\.[0-9]*)$")) "'" + str + "'"
    else if (str.matches("^([0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]|[0-9][0-9]:[0-9][0-9]:[0-9][0-9])$")) "'" + str + "'"
    else if (
      options.quoteAllStrings || str.matches("^(.*[\":{}$\\[\\]\\\\*&#?|<>!%@]|[^A-Za-z0-9_]*-).*$")
    )            // Quote the string with "..." if the string contains a bare "-" but not if "-" is preceded by an alphanum symbol.
      CString(
        str
      ).toString // \0, \x01, \x02, \x03, \x04, \x05, \x06, \a, \b, \t, \n, \v, \f, \r, \x0e, \x0f, \x10, \x11, \x12, \x13, \x14, \x15, \x16, \x17, \x18, \x19, \x1a, \e, \x1c, \x1d, \x1e, \x1f, \N, \_, \L, \P
    else str
  }

  private def escapeYamlName(name: String, options: YamlOptions): String = {
    if (options.quoteAllStrings || (yamlSpecialNames contains name.toLowerCase)) s"'$name'" else name
  }

  private def commentsToYaml(comments: String): String = {
    if (comments.isEmpty) "" else comments.split("\n").map(line => line.replaceFirst("^[ \\t]*--", "")).mkString("#", "\n#", "\n")
  }

  def toYaml(expression: Expression, options: YamlOptions): Either[String, String] = {
    toYaml(DhallFile(Seq(), "", expression), options)
  }

  def toYaml(dhallFile: DhallFile, options: YamlOptions): Either[String, String] = {
    toYamlLines(dhallFile.value, options).map(_.lines.mkString("", "\n", "\n")).map(yaml => commentsToYaml(dhallFile.headerComments) + yaml)
  }

}
