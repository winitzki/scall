package io.chymyst.dhall

import io.chymyst.dhall.CBORmodel.CString
import io.chymyst.dhall.Syntax.ExpressionScheme.{ExprBuiltin, RecordLiteral, TextLiteral}
import io.chymyst.dhall.Syntax.{DhallFile, Expression, ExpressionScheme}
import io.chymyst.dhall.SyntaxConstants.{Builtin, FieldName}

/** Conversion of Dhall values to Yaml is supported for numbers, strings, date/time values, lists, records, and union types, arbitrarily nested. Other types or
  * kinds are not supported and will give an error when converting to Yaml.
  *
  * As a rule, only normal forms may be converted to Yaml. For example, converting `{ a.b = 1 }` involves desugaring to `{ a = { b = 1 } }`; the desugaring must
  * be performed before converting to Yaml.
  *
  * Conversion of Dhall values to Yaml is performed according to the following rules:
  *
  *   - Each supported Dhall value is converted into a (possibly empty) list of Yaml lines.
  *
  *   - Natural literals (including Bytes literals) are translated into Yaml integers.
  *   - Double literals are translated into Yaml floats.
  *   - Text literals are translated into Yaml unquoted strings, quoted strings, or multiline strings.
  *     - If a text literal contains one of the special characters, it is quoted in double quotes.
  *     - If a text literal contains newlines, it is represented as a multiline string.
  *     - If a text literal is a reserved Yaml word, or matches the format of a number or a date, it is quoted in single quotes.
  *     - Otherwise the text literal is unquoted (unless the "quote all" option is given; in that case, it is quoted in double quotes).
  *   - Date, Time, TimeZone are translated into Yaml quoted strings, except when they are part of a timestamp record as follows:
  *     - A timestamp record of type { date : Date, time : Time } is translated into a quoted string such as "1960-12-25T04:23:34".
  *     - A timestamp record of type { date : Date, time : Time, timeZone: TimeZone } is translated into a quoted string such as "1960-12-25T04:23:34+00:00".
  *   - A value of Optional type that is a `None T` is omitted (translated into empty list of Yaml lines) unless it is at top-level, in which case it is
  *     translated into `null`. If an option is given to preserve null values, `None T` is always translated into `null` (also when not at top level).
  *   - A value of a union type is either a constructor without arguments, or a constructor with a single argument.
  *     - A constructor without argument is converted to a Yaml string corresponding to the constructor's name.
  *     - A constructor with argument is converted to Yaml lines corresponding to the argument (the constructor's name is then ignored).
  *
  *   - Lists of values are translated into Yaml arrays as follows:
  *     - Empty lists are translated into the Yaml line `[]` except if the empty list has a map type.
  *     - Empty lists of map type `{ mapKey : Text, mapValue : ... }` are translated into the Yaml line `{}`.
  *     - For non-empty lists, each list element is translated into zero or more Yaml lines.
  *     - If a list element is translated into an empty Yaml, that list element is omitted.
  *     - If a list element is a single Yaml line, it is formatted after the dash:
  *
  * {{{
  *      - 123
  *      - 456
  * }}}
  *
  *   - If a list element is a multiline Yaml, the first line is formatted after the dash, and all next lines are indented:
  *
  * {{{
  *      - - 1
  *        - 2
  *      - a: 1
  *        b: 2
  * }}}
  *
  *   - Records are translated into Yaml records as follows:
  *     - Empty records are translated into the Yaml line `{}`.
  *     - For each field of a non-empty record, the corresponding value is translated into zero or more Yaml lines.
  *     - If a field's value is an empty Yaml, that field is omitted.
  *     - If a field's value is a single Yaml line, that line is formatted after the field's name:
  *
  * {{{
  *      a: xyz
  * }}}
  *   - If the field's name is a reserved word, or if the "quote all" option is given, the field's name is put in single quotes.
  *
  * Reserved words are "true", "false", "null", "~", "yes", "no", "y", "n", "on", "off".
  *
  *   - If a field's value is a multi-line string (which is a Yaml primitive type), the beginning separator is formatted after the field's name:
  *
  * {{{
  *      a: |
  *         multi-line
  *         string
  * }}}
  *
  *   - If a field's value is multi-line Yaml, and if the field's value is a record or a list (i.e., not a primitive type), it is formatted started from the
  *     next line and all lines are indented. For example:
  *
  * {{{
  *      a:
  *        - 1
  *        - 2
  *      b:
  *        c: 1
  *        d: 2
  * }}}
  */
object Yaml {
  def yamlIndent(indent: Int) = " " * indent

  final case class YamlOptions(quoteAllStrings: Boolean = false, indent: Int = 2, createDocuments: Boolean = false, jsonFormat: Boolean = false)

  sealed trait LineType

  case object YRecord    extends LineType
  case object YArray     extends LineType
  case object YPrimitive extends LineType

  final case class YamlLines(ltype: LineType, lines: Seq[String])

  // This function should ignore options.createDocuments because it is used recursively for sub-document Yaml values.
  private def toYamlLines(expr: Expression, options: YamlOptions): Either[String, YamlLines] = {
    val format = if (options.jsonFormat) "JSON" else "Yaml"
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
          case ExpressionScheme.RecordLiteral(Seq()) => Right(YamlLines(YPrimitive, Seq("{}")))

          case ExpressionScheme.RecordLiteral(defs) =>
            val content: Seq[Either[String, (String, YamlLines)]] = defs.map { case (FieldName(name), e: Expression) =>
              toYamlLines(e, options).map(lines => (name, lines))
            }

            val errors = content.collect { case Left(e) => e }
            if (errors.nonEmpty) Left(errors.mkString("; "))
            else {
              val valids                 = content.map { case Right(x) => x }
              val output: Seq[YamlLines] = valids.map {
                case (_, YamlLines(t, Seq()))             => YamlLines(t, Seq[String]()) // null values are omitted.
                case (name, YamlLines(YPrimitive, lines)) =>
                  YamlLines(
                    YRecord,
                    Seq(escapeSpecialName(name, options) + ":" + yamlIndent(math.max(1, options.indent - 1)) + lines.head) ++ lines.tail.map(l =>
                      yamlIndent(options.indent) + l
                    ),
                  )
                case (name, YamlLines(_, lines))          =>
                  YamlLines(YRecord, (escapeSpecialName(name, options) + ":") +: lines.map(l => yamlIndent(options.indent) + l))
              }
              if (options.jsonFormat) {
                val (jsonBeginRecord, jsonEndRecord) = if (options.jsonFormat) (Seq("{"), Seq("}")) else (Seq(), Seq())
                if (output.isEmpty) Right(YamlLines(YRecord, Seq("{}")))
                else {
                  val init = output.init.flatMap(y => if (y.lines.isEmpty) y.lines else y.lines.init :+ (y.lines.last + ","))
                  val last = output.last.lines
                  Right(YamlLines(YRecord, jsonBeginRecord ++ init ++ last ++ jsonEndRecord))
                }
              } else Right(YamlLines(YRecord, output.flatMap(_.lines)))
            }

          case ExpressionScheme.EmptyList(_) =>
            val emptyListOrRecord = if (isRecordMap) "{}" else "[]"
            Right(YamlLines(YPrimitive, Seq(emptyListOrRecord)))

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
                val delimiter              = if (options.jsonFormat) "" else "-"
                val valids                 = content.map { case Right(x) => x }
                val output: Seq[YamlLines] = valids.map {
                  case YamlLines(t, Seq()) => YamlLines(t, Seq()) // null values are omitted.
                  // If the value of a list item is a multiline YAML, we will skip the first line if it is empty.
                  case YamlLines(_, lines) =>
                    YamlLines(
                      YArray,
                      (delimiter + yamlIndent(math.max(1, options.indent - 1)) + lines.head) +: lines.tail.map(l => yamlIndent(options.indent) + l),
                    )
                }
                if (options.jsonFormat) {
                  val (jsonBeginList, jsonEndList) = if (options.jsonFormat) (Seq("["), Seq("]")) else (Seq(), Seq())
                  if (output.isEmpty) Right(YamlLines(YArray, Seq("[]")))
                  else {
                    val init = output.init.flatMap(y => if (y.lines.isEmpty) y.lines else y.lines.init :+ (y.lines.last + ","))
                    val last = output.last.lines
                    Right(YamlLines(YArray, jsonBeginList ++ init ++ last ++ jsonEndList))
                  }
                } else Right(YamlLines(YArray, output.flatMap(_.lines)))
              }
            }

          case ExpressionScheme.TextLiteral(List(), trailing) =>
            if (!options.jsonFormat && trailing.contains("\n")) {
              val lines = trailing.split("\n").toSeq
              Right(YamlLines(YPrimitive, Seq("|") ++ lines))
            } else Right(YamlLines(YPrimitive, Seq(stringEscapeForYaml(trailing, options))))

          case ExpressionScheme.NaturalLiteral(_) | ExpressionScheme.DoubleLiteral(_) =>
            Right(YamlLines(YPrimitive, Seq(expr.print)))

          case ExpressionScheme.ExprConstant(SyntaxConstants.Constant.True) | ExpressionScheme.ExprConstant(SyntaxConstants.Constant.False) =>
            Right(YamlLines(YPrimitive, Seq(expr.print.toLowerCase)))

          case ExpressionScheme.KeywordSome(expression: Expression) => toYamlLines(expression, options)

          case ExpressionScheme.Application(Expression(ExprBuiltin(Builtin.None)), _) => Right(YamlLines(YPrimitive, Seq()))

          case ExpressionScheme.Field(Expression(ExpressionScheme.UnionType(_)), FieldName(name)) =>
            Right(YamlLines(YPrimitive, Seq(stringEscapeForYaml(name, options))))

          case ExpressionScheme.Application(Expression(ExpressionScheme.Field(Expression(ExpressionScheme.UnionType(_)), FieldName(_))), expr) =>
            toYamlLines(expr, options)

          case _ => Left(s"Error: Unsupported expression type for $format export: ${expr.print} of type ${tpe.print}")
        }
    }
  }

  private val yamlReservedWords = Set("y", "n", "yes", "no", "on", "off", "true", "false", "null", "~")

  private def stringEscapeForYaml(str: String, options: YamlOptions): String = {
    if (options.jsonFormat) CString(str).toString
    else {
      val singleQuote = "'"
      if (yamlReservedWords contains str.toLowerCase) singleQuote + str + singleQuote
      else if (str.matches("^[+-]?([0-9]+|\\.inf|nan|[0-9]*\\.[0-9]*)$")) singleQuote + str + singleQuote
      else if (str.matches("^([0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]|[0-9][0-9]:[0-9][0-9]:[0-9][0-9])$")) singleQuote + str + singleQuote
      else if (
        options.quoteAllStrings || str.matches("^(.*[\":{}$\\[\\]\\\\*&#?|<>!%@]|[^A-Za-z0-9_]*-).*$")
      )            // Quote the string with "..." if the string contains a bare "-" but not if "-" is preceded by an alphanum symbol.
        CString(
          str
        ).toString // \0, \x01, \x02, \x03, \x04, \x05, \x06, \a, \b, \t, \n, \v, \f, \r, \x0e, \x0f, \x10, \x11, \x12, \x13, \x14, \x15, \x16, \x17, \x18, \x19, \x1a, \e, \x1c, \x1d, \x1e, \x1f, \N, \_, \L, \P
      else str
    }
  }

  private def escapeSpecialName(name: String, options: YamlOptions): String = {
    val singleQuote = if (options.jsonFormat) "\"" else "'"
    if (options.quoteAllStrings || options.jsonFormat || (yamlReservedWords contains name.toLowerCase)) singleQuote + name + singleQuote else name
  }

  private def commentsToYaml(comments: String, options: YamlOptions): String = {
    if (comments.isEmpty || options.jsonFormat) "" else comments.split("\n").map(line => line.replaceFirst("^[ \\t]*--", "")).mkString("#", "\n#", "\n")
  }

  // It is assumed that `expression` is reduced to its beta-normal form. Unreduced syntax sugar such as `{ a.b = 1 }` will not be accepted.
  def toYaml(expression: Expression, options: YamlOptions): Either[String, String] = {
    toYaml(DhallFile(Seq(), "", expression), options)
  }

  def toYaml(dhallFile: DhallFile, rawOptions: YamlOptions): Either[String, String] = {
    val options   = if (!rawOptions.jsonFormat) rawOptions.copy(indent = math.max(rawOptions.indent, 1)) else rawOptions
    val yamlLines = (options.createDocuments && !options.jsonFormat, dhallFile.value.scheme) match {
      case (true, ExpressionScheme.NonEmptyList(exprs)) =>
        val results = exprs.map { e => toYamlLines(e, options.copy(createDocuments = false)) }
        val errors  = results.collect { case Left(x) => x }
        if (errors.isEmpty) {
          val valids = results.collect { case Right(x) => x }
          Right(YamlLines(YArray, valids.flatMap { v => "---" +: v.lines }))
        } else Left(errors.mkString("; "))

      case _ =>
        val documentPrefix: Seq[String] = if (options.createDocuments) Seq("---") else Seq()
        toYamlLines(dhallFile.value, options.copy(createDocuments = false)).map(y => y.copy(lines = documentPrefix ++ y.lines))
    }
    yamlLines.map(_.lines.mkString("", "\n", "\n")).map(yaml => commentsToYaml(dhallFile.headerComments, options) + yaml)
  }

}
