package io.chymyst.dhall

import io.chymyst.dhall.CBORmodel.CString
import io.chymyst.dhall.Syntax.{DhallFile, Expression, ExpressionScheme}
import io.chymyst.dhall.SyntaxConstants.FieldName

import scala.annotation.tailrec

object Toml {
  final case class TomlOptions(indent: Int = 2)

  private def errorMessage(expression: Expression): String =
    s"Error: Unsupported expression type for TOML export: ${expression.print}, must be a record literal."

  def toToml(dhallFile: DhallFile, tomlOptions: TomlOptions): Either[String, String] = toToml(dhallFile.value, tomlOptions)

  @tailrec
  def toToml(expression: Expression, tomlOptions: TomlOptions = TomlOptions()): Either[String, String] = {
    expression.scheme match {
      case ExpressionScheme.Annotation(data: Expression, tipe)                => toToml(data, tomlOptions)
      case ExpressionScheme.RecordLiteral(defs: Seq[(FieldName, Expression)]) => topLevelRecordToToml(defs)
      case ExpressionScheme.KeywordSome(data)                                 => toToml(data, tomlOptions)
      case _                                                                  => Left(errorMessage(expression))
    }
  }

  private def isRecord(expression: Expression): Boolean = expression.scheme match {
    case ExpressionScheme.RecordLiteral(_) => true
    case _                                 => false
  }

  private def exprToToml(expression: Expression, atTopLevel: Boolean): Either[String, String] = expression.scheme match {

    case ExpressionScheme.EmptyList(_)           => Right("[]")
    case ExpressionScheme.NonEmptyList(exprs)    =>
      val converted = exprs.map(exprToToml(_, atTopLevel = false))
      val errors    = converted.collect { case Left(x) => x }
      if (errors.nonEmpty) Left(errors.mkString("; "))
      else {
        Right(converted.collect { case Right(x) => x }.mkString("[ ", ", ", " ]"))

      }
    case ExpressionScheme.RecordLiteral(defs)    =>
      val converted = defs.map { case (fieldName, expr) => tomlKeyValue(fieldName, expr) }
      val errors    = converted.collect { case Left(x) => x }
      if (errors.nonEmpty) Left(errors.mkString("; "))
      else {
        val convertedValues  = converted.collect { case Right(x) => x }
        val (prefix, suffix) = if (atTopLevel) ("", "") else ("{\n", "\n}\n")
        Right(convertedValues.mkString(prefix, "\n", suffix))
      }
    case ExpressionScheme.Annotation(data, tipe) => exprToToml(data, atTopLevel)
    case ExpressionScheme.DoubleLiteral(_) | ExpressionScheme.NaturalLiteral(_) | ExpressionScheme.IntegerLiteral(_) | ExpressionScheme.DateLiteral(_, _, _) |
        ExpressionScheme.TimeLiteral(_, _, _, _) | ExpressionScheme.TimeZoneLiteral(_) =>
      Right(expression.print)
    case ExpressionScheme.TextLiteral(Nil, str)  => Right(CString(str).toString)

    case ExpressionScheme.KeywordSome(data)                                                                                           => exprToToml(data, atTopLevel)
    case ExpressionScheme.ExprConstant(SyntaxConstants.Constant.False) | ExpressionScheme.ExprConstant(SyntaxConstants.Constant.True) =>
      Right(expression.print.toLowerCase)
    case _                                                                                                                            => Left(errorMessage(expression))
  }

  private def keyNameToToml(str: String): Either[String, String] = {
    // TODO: string escaping and/or errors
    Right(str)
  }

  private def tomlKeyValue(name: FieldName, expression: Expression): Either[String, String] = {
    val converted = Seq(keyNameToToml(name.name), exprToToml(expression, atTopLevel = false))

    val errors = converted.collect { case Left(x) => x }
    if (errors.nonEmpty) Left(errors.mkString("; "))
    else {
      val Seq(tomlKey, tomlValue) = converted.collect { case Right(x) => x }
      Right(s"$tomlKey = $tomlValue")
    }
  }

  def tomlTable(name: FieldName, expression: Expression): Either[String, String] = {
    val converted = Seq(keyNameToToml(name.name), exprToToml(expression, atTopLevel = true))

    val errors = converted.collect { case Left(x) => x }
    if (errors.nonEmpty) Left(errors.mkString("; "))
    else {
      val Seq(tomlKey, tomlValue) = converted.collect { case Right(x) => x }
      Right(s"[$tomlKey]\n$tomlValue")
    }
  }

  private def topLevelRecordToToml(defs: Seq[(FieldName, Expression)]): Either[String, String] = {
    // If a record value is not itself a nested record, it is printed as key = value.
    // We are at top level. Replace each nested record by a TOML "table".
    val converted: Seq[Either[String, String]] = defs.map { case (fieldName, expr) =>
      if (isRecord(expr)) {
        tomlTable(fieldName, expr)
      } else {
        tomlKeyValue(fieldName, expr)
      }
    }
    val errors                                 = converted.collect { case Left(x) => x }
    if (errors.nonEmpty) Left(errors.mkString("; ")) else Right(converted.collect { case Right(x) => x }.mkString("", "\n", "\n"))
  }
}
