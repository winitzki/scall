package io.chymyst.dhall

import fastparse._
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.Syntax.{DhallFile, Expression}
import io.chymyst.dhall.SyntaxConstants.FieldName

import java.io.InputStream

object Parser {
  implicit class StringAsDhallExpression(val input: String) extends AnyVal {
    def dhall: Expression = Parser.parseToExpression(input)
  }

  def parseToExpression(input: String): Expression = parseDhall(input) match {
    case Parsed.Success(value: DhallFile, index) => value.value
    case failure: Parsed.Failure                 => throw new Exception(s"Dhall parser error: ${failure.extra.trace().longMsg}")
  }

  def parseDhallBytes(source: Array[Byte]): Parsed[DhallFile] = parse(source, Grammar.complete_dhall_file(_))

  def parseDhall(source: String): Parsed[DhallFile] = parse(source, Grammar.complete_dhall_file(_))

  def parseDhallStream(source: InputStream): Parsed[DhallFile] = parse(source, Grammar.complete_dhall_file(_))

  private def localDateTimeZone(dateOption: Option[DateLiteral], timeOption: Option[TimeLiteral], zoneOption: Option[Int]): Expression = {
    val dateR = dateOption.map { date => (FieldName("date"), Expression(date)) }
    val dateT = dateOption.map { date => (FieldName("date"), Expression(ExprBuiltin(SyntaxConstants.Builtin.Date))) }
    val timeR = timeOption.map { time => (FieldName("time"), Expression(time)) }
    val timeT = timeOption.map { time => (FieldName("time"), Expression(ExprBuiltin(SyntaxConstants.Builtin.Time))) }
    val zoneR = zoneOption.map { zone => (FieldName("timeZone"), Expression(TimeZoneLiteral(zone))) }
    val zoneT = zoneOption.map { zone => (FieldName("timeZone"), Expression(ExprBuiltin(SyntaxConstants.Builtin.TimeZone))) }

    val record = RecordLiteral[Expression](Seq(dateR, timeR, zoneR).flatten).sorted
    // val recordType = RecordType[Expression](Seq(dateT, timeT, zoneT).flatten).sorted

    // Return { date : Date, time : Time, timeZone : TimeZone } or some subset of that record without the type.
    record
  }

  def localDateTimeWithZone(date: DateLiteral, time: TimeLiteral, zone: Int): Expression = localDateTimeZone(Some(date), Some(time), Some(zone))

  def localTimeWithZone(time: TimeLiteral, zone: Int): Expression = localDateTimeZone(None, Some(time), Some(zone))

  def localDateTime(date: DateLiteral, time: TimeLiteral): Expression = localDateTimeZone(Some(date), Some(time), None)
}
