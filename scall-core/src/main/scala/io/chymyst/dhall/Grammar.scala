package io.chymyst.dhall

import fastparse.NoWhitespace._
import fastparse._
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.Syntax.{DhallFile, Expression, PathComponent, RawRecordLiteral}
import io.chymyst.dhall.SyntaxConstants.{ConstructorName, FieldName, ImportType, VarName}
import io.chymyst.dhall.TypeCheck._Type

import java.time.LocalDate
import scala.util.{Failure, Success, Try}
import io.chymyst.fastparse.Memoize.MemoizeParser

object Grammar {

  def ALPHA[$: P] = P(
    CharIn("\u0041-\u005A", "\u0061-\u007A") //  A_Z | a_z
  )

  def BIT[$: P] = P(CharIn("01"))


  def DIGIT[$: P] = P(
    CharIn("0-9")
    //  0_9
  )

  def end_of_line[$: P] = P("\n" | "\r\n")

  def valid_non_ascii[$: P] = P(
    CharIn(
      "\u0080-\uD7FF",
      // %xD800_DFFF = surrogate pairs
      //      "\uE000-\uFFFC",
      "\uE000-\uFFFC", // Workaround: Disallow the "replacement" character ("\uFFFD") because it will be generated for invalid utf-8 encodings.
    )
      // Encode other Unicode ranges into Java's UTF-16 using UTF-16 surrogates.
      // See https://www.cogsci.ed.ac.uk/~richard/utf-8.cgi?input=10000&mode=hex and look for "UTF-16 surrogates".
      // %xFFFE_FFFF = non_characters
      //        | % x10000_1FFFD
      // U+10000 = "\uD800\uDC00"
      // U+103FF = "\uD800\uDFFF"
      // U+10400 = "\uD801\uDC00"
      // U+1FFFD = "\uD83F\uDFFD"
      // format: off
      | (CharIn("\uD800-\uD83E") ~ CharIn("\uDC00-\uDFFF"))
      // format: on
      | (CharIn("\uD83F") ~ CharIn("\uDC00-\uDFFD"))
      //      // %x1FFFE_1FFFF = non_characters
      //      | % x20000_2FFFD   // U+20000 = \uD840\uDC00
      | (CharIn("\uD840-\uD87E") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uD87F") ~ CharIn("\uDC00-\uDFFD"))
      //        // %x2FFFE_2FFFF = non_characters
      //        | % x30000_3FFFD
      | (CharIn("\uD880-\uD8BE") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uD8BF") ~ CharIn("\uDC00-\uDFFD"))
      //      // %x3FFFE_3FFFF = non_characters
      //      | % x40000_4FFFD
      | (CharIn("\uD8C0-\uD8FE") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uD8FF") ~ CharIn("\uDC00-\uDFFD"))
      //        // %x4FFFE_4FFFF = non_characters
      //        | % x50000_5FFFD
      | (CharIn("\uD900-\uD93E") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uD93F") ~ CharIn("\uDC00-\uDFFD"))
      //      // %x5FFFE_5FFFF = non_characters
      //      | % x60000_6FFFD
      | (CharIn("\uD940-\uD97E") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uD97F") ~ CharIn("\uDC00-\uDFFD"))
      //        // %x6FFFE_6FFFF = non_characters
      //        | % x70000_7FFFD
      | (CharIn("\uD980-\uD9BE") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uD9BF") ~ CharIn("\uDC00-\uDFFD"))
      //      // %x7FFFE_7FFFF = non_characters
      //      | % x80000_8FFFD
      | (CharIn("\uD9C0-\uD9FE") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uD9FF") ~ CharIn("\uDC00-\uDFFD"))
      //        // %x8FFFE_8FFFF = non_characters
      //        | % x90000_9FFFD
      | (CharIn("\uDA00-\uDA3E") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uDA3F") ~ CharIn("\uDC00-\uDFFD"))
      //      // %x9FFFE_9FFFF = non_characters
      //      | % xA0000_AFFFD
      | (CharIn("\uDA40-\uDA7E") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uDA7F") ~ CharIn("\uDC00-\uDFFD"))
      //        // %xAFFFE_AFFFF = non_characters
      //        | % xB0000_BFFFD
      | (CharIn("\uDA80-\uDABE") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uDABF") ~ CharIn("\uDC00-\uDFFD"))
      //      // %xBFFFE_BFFFF = non_characters
      //      | % xC0000_CFFFD
      | (CharIn("\uDAC0-\uDAFE") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uDAFF") ~ CharIn("\uDC00-\uDFFD"))
      //        // %xCFFFE_CFFFF = non_characters
      //        | % xD0000_DFFFD
      | (CharIn("\uDB00-\uDB3E") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uDB3F") ~ CharIn("\uDC00-\uDFFD"))
      //      // %xDFFFE_DFFFF = non_characters
      //      | % xE0000_EFFFD
      | (CharIn("\uDB40-\uDB7E") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uDB7F") ~ CharIn("\uDC00-\uDFFD"))
      //        // %xEFFFE_EFFFF = non_characters
      //        | % xF0000_FFFFD
      | (CharIn("\uDB80-\uDBBE") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uDBBF") ~ CharIn("\uDC00-\uDFFD"))
      // U+F0000 = "\uDB80\uDC00"
      // U+FFFFD = "\uDBBF\uDFFD"
      //      // %xFFFFE_FFFFF = non_characters
      //      | % x100000_10FFFD
      | (CharIn("\uDBC0-\uDBFE") ~ CharIn("\uDC00-\uDFFF"))
      | (CharIn("\uDBFF") ~ CharIn("\uDC00-\uDFFD"))
    // U+100000 = "\uDBC0\uDC00"
    // U+10FFFD = "\uDBFF\uDFFD"
    // %x10FFFE_10FFFF = non_characters
  )
    .memoize

  def tab[$: P] = P("\t")

  def block_comment[$: P] = P(
    "{-" ~/ block_comment_continue // Do not use cut here, because then block comment will fail the entire identifier when parsing "x {- -}" without a following @.
  )
    .memoize

  def block_comment_char[$: P] = P(
    CharIn("\u0020-\u007F")
      | valid_non_ascii
      | tab
      | end_of_line
  )
    .memoize

  def block_comment_continue[$: P]: P[Unit] = P(
    "-}"
      | (block_comment ~/ block_comment_continue)
      | (block_comment_char ~ block_comment_continue)
  )
    .memoize

  def not_end_of_line[$: P] = P(
    CharIn("\u0020-\u007F") | valid_non_ascii | tab
  )

  def line_comment_prefix[$: P] = P(
    "--" ~ (not_end_of_line.rep)
  )

  def line_comment[$: P] = P(
    line_comment_prefix ~ end_of_line
  )

  def whitespace_chunk[$: P] = P(
    " "
      | tab
      | end_of_line
      | line_comment
      | block_comment
  )
    .memoize

  def whsp[$: P]: P[Unit] = P(
    NoCut(whitespace_chunk.rep)
  )

  def whsp1[$: P]: P[Unit] = P(
    NoCut(whitespace_chunk.rep(1))
  ).memoize

  def ALPHANUM[$: P] = P(
    ALPHA | DIGIT
  ).memoize

  def hexdigitAnyCase[$: P] = P(
    CharIn("0-9A-Fa-f")
  ).memoize

  def simple_label_first_char[$: P] = P(
    ALPHA | "_"
  ).memoize

  def simple_label_next_char[$: P] = P(
    ALPHANUM | "-" | "/" | "_"
  ).memoize

  /*
  ; A simple label cannot be one of the reserved keywords
  ; listed in the `keyword` rule.
  ; A PEG parser could use negative lookahead to
  ; enforce this, e.g. as follows:
  ; simple-label =
  ;       keyword 1*simple-label-next-char
  ;     / !keyword (simple-label-first-char *simple-label-next-char)
   */
  def simple_label[$: P]: P[String] = P(
    (keyword.map(_ => ()) ~ simple_label_next_char.rep(1)) // Do not insert a cut after keyword.
      | (!keyword ~ simple_label_first_char ~ simple_label_next_char.rep)
  ).!.memoize

  // Any printable character other than the backquote.
  def quoted_label_char[$: P] = P(
    CharIn("\u0020-\u005F", "\u0061-\u007E")
    // %x60 = '`'
  )

  def quoted_label[$: P] = P(
    quoted_label_char.rep
  ).!

  // Note: identifiers in backquotes may contain arbitrary text, including the name of a Dhall keyword.
  // Example: "let `in` = 1 in `in`" evaluates to "1".
  // A successfully parsed `label` is guaranteed to be either quoted or not a keyword.
  def label[$: P]: P[String] = P(
    ("`" ~ quoted_label ~ "`") | simple_label
  )

  // A successfully parsed `nonreserved_label` is guaranteed to be either quoted or not a builtin.
  def nonreserved_label[$: P] = P(
    (builtin ~ simple_label_next_char.rep(1)).! | (!builtin ~ label)
  ).map(VarName)

  def any_label[$: P]: P[String] = P(
    label
  )

  def any_label_or_some[$: P]: P[String] = P(
    any_label | requireKeyword("Some").!
  ).memoize

  def with_component[$: P]: P[String] = P(
    any_label_or_some | "?".!
  ).!.memoize

  final case class TextLiteralNoInterp(value: String) extends AnyVal

  // Either a complete interpolated expression ${...} or a single character.
  def double_quote_chunk[$: P]: P[Either[TextLiteral[Expression], TextLiteralNoInterp]] = P( // text literal with or without interpolations
    interpolation.map(TextLiteral.ofExpression).map(Left.apply)
      // '\'    Beginning of escape sequence
      | ("\\" ~/ double_quote_escaped).map(TextLiteralNoInterp.apply).map(Right.apply)
      | double_quote_char.!.map(TextLiteralNoInterp.apply).map(Right.apply)
  ).memoize

  def double_quote_escaped[$: P]: P[String] = P(
    //    CharIn("\"$\\/bfnrt")
    CharIn("\"").! // '"'    quotation mark  U+0022
      | "$".!.map(_ => "\u0024") // '$'    dollar sign     U+0024
      | "\\".! //| % x5C // '\'    reverse solidus U+005C
      | "/".! // '/'    solidus         U+002F
      | "b".!.map(_ => "\b") // 'b'    backspace       U+0008
      | "f".!.map(_ => "\f") // 'f'    form feed       U+000C
      | "n".!.map(_ => "\n") // 'n'    line feed       U+000A
      | "r".!.map(_ => "\r") // 'r'    carriage return U+000D
      | "t".!.map(_ => "\t") // 't'    tab             U+0009
      | ("u" ~ unicode_escape.map(hex => new String(Character.toChars(Integer.parseInt(hex, 16))))) // 'uXXXX' | 'u{XXXX}'    U+XXXX
    // See https://stackoverflow.com/questions/5585919/creating-unicode-character-from-its-number
  ).memoize

  def unicode_escape[$: P]: P[String] = P(
    unbraced_escape.! | ("{" ~/ braced_escape.! ~ "}")
  )

  def unicode_suffix[$: P] = P(
    (CharIn("0-9A-E") ~ hexdigitAnyCase.rep(exactly = 3))
      | ("F" ~ hexdigitAnyCase.rep(exactly = 2) ~ CharIn("0-9A-D"))
  )

  def unbraced_escape[$: P] = P(
    ((DIGIT | "A" | "B" | "C") ~ hexdigitAnyCase.rep(exactly = 3))
      | ("D" ~ CharIn("0-7") ~ hexdigitAnyCase ~ hexdigitAnyCase)
      // %xD800_DFFF Surrogate pairs
      | ("E" ~ hexdigitAnyCase)
      | ("F" ~ hexdigitAnyCase.rep(exactly = 2) ~ CharIn("0-9A-D"))
    // %xFFFE_FFFF Non_characters
  )


  def braced_codepoint[$: P] = P(
    ((CharIn("1-9A-F") | "10") ~ unicode_suffix)
      //;
      //  (Planes
      //  1_16
      //  )
      | unbraced_escape // (Plane 0)
      | hexdigitAnyCase.rep(min = 1, max = 3) // %x000_FFF
  )

  def braced_escape[$: P] = P(
    "0".rep ~ braced_codepoint
  )

  def double_quote_char[$: P] = P(
    CharIn("\u0020-\u0021", "\u0023-\u005B", "\u005D-\u007F")
      //    %x20_21
      //      // %x22 = '"'
      //      | %x23_5B
      //        // %x5C = "\"
      //        | %x5D_7F
      | valid_non_ascii
  )

  def double_quote_literal[$: P]: P[TextLiteral[Expression]] = P(
    "\"" ~/ double_quote_chunk.rep ~ "\""
  ).map(_.map(literalOrInterp => literalOrInterp.map(TextLiteral.ofText[Expression]).merge).fold(TextLiteral.empty[Expression])(_ ++ _))
    .memoize

  def single_quote_continue[$: P]: P[TextLiteral[Expression]] = P(
    (interpolation ~ single_quote_continue).map { case (head, tail) => TextLiteral.ofExpression(head) ++ tail }
      | (escaped_quote_pair ~ single_quote_continue).map { case (a, b) => a ++ b }
      | (escaped_interpolation ~ single_quote_continue).map { case (a, b) => a ++ b }
      | P("''").map(_ => TextLiteral.empty) // End of text literal.
      | (single_quote_char ~ single_quote_continue).map { case (char, tail) => TextLiteral.ofString[Expression](char) ++ tail }
  ).memoize

  def escaped_quote_pair[$: P]: P[TextLiteral[Expression]] = P(
    "'''".!.map(_ => TextLiteral.ofString(s"''"))
  )

  def escaped_interpolation[$: P]: P[TextLiteral[Expression]] = P(
    "''${".!.map(_ => TextLiteral.ofString("${"))
  )

  def single_quote_char[$: P]: P[String] = P(
    CharIn("\u0020-\u007F")
      | valid_non_ascii
      | tab
      | end_of_line
  ).!.memoize

  def single_quote_literal[$: P]: P[TextLiteral[Expression]] = P(
    "''" ~ end_of_line ~/ single_quote_continue
  ).map(_.align)

  def interpolation[$: P]: P[Expression] = P(
    "${" ~ complete_expression ~/ "}"
  ).memoize

  def text_literal[$: P]: P[TextLiteral[Expression]] = P(
    double_quote_literal
      | single_quote_literal
  ).memoize

  // See https://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java
  def hexStringToByteArray(s: String): Array[Byte] = { // `s` must be a String of even length.
    val len = s.length
    val data = new Array[Byte](len >> 1)
    var i = 0
    while (i < len) {
      data(i >> 1) = ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16)).toByte
      i += 2
    }
    data
  }

  def bytes_literal[$: P]: P[BytesLiteral] = P(
    "0x\"" ~ hexdigitAnyCase.rep(exactly = 2).rep.! ~ "\""
  ).map(BytesLiteral.of)

  val simpleKeywords = Seq(
    "if",
    "then",
    "else",
    "let",
    "in",
    "assert",
    "as",
    "using",
    "merge",
    "missing",
    "Infinity",
    "NaN",
    "Some",
    "toMap",
    "with",
    "forall",
    "showConstructor",
    //    "Text",
    //    "Location",
    //    "Bytes",
  )

  val simpleKeywordsSet = simpleKeywords.toSet

  def opOr[$: P] = P("||")

  def opPlus[$: P] = P("+")

  def opTextAppend[$: P] = P("++")

  def opListAppend[$: P] = P("#")

  def opAnd[$: P] = P("&&")

  def opTimes[$: P] = P("*")

  def opEqual[$: P] = P("==")

  def opNotEqual[$: P] = P("!=")

  def opAlternative[$: P] = P("?")

  def forall_symbol[$: P] = P(
    "\u2200" // Unicode FOR ALL
  )

  def forall[$: P] = P(
    forall_symbol | requireKeyword("forall")
  )

  private def concatKeywords[$: P](keywords: Seq[String]): P[String] = {
    keywords
      .sorted(Ordering[String].reverse) // Reversing the order will disambiguate parsing of keywords that are substrings of another keyword.
      .map {
        k => implicit ctx: P[_] => P(k)
      }.reduce {
        (p1, p2) => implicit ctx: P[_] => P(p1(ctx) | p2(ctx))
      }(implicitly[P[$]]).!
  }
//    .memoize  // Do not memoize: breaks parsing!

  //def keywordOrBuiltin[$: P]: P[String] = concatKeywords(simpleKeywords ++ builtinSymbolNames)

  def keyword[$: P]: P[String] = concatKeywords(simpleKeywords)

  val builtinSymbolNames = SyntaxConstants.Builtin.namesToValuesMap.keys.toSeq
  val builtinSymbolNamesSet = SyntaxConstants.Builtin.namesToValuesMap.keySet

  val constantSymbolNames = SyntaxConstants.Constant.namesToValuesMap.keys.toSeq
  val constantSymbolNamesSet = SyntaxConstants.Constant.namesToValuesMap.keySet

  def builtin[$: P]: P[Expression] = {
    // TODO report issue: possible confusion between Builtin and Constant symbols.
    // Builtins are parsed into Expression Builtin, but should sometimes be parsed into a Constant.
    // TODO figure out whether True and False should be moved to Builtin out of Constants. (Syntax.hs says it's "typechecking constants".)
    // Are there any situations where True and False are parsed as Builtin (e.g. prohibiting other usage) and not as a Constant? In that case there is a confusion in the standard.
    (concatKeywords(builtinSymbolNames).map(SyntaxConstants.Builtin.withName).map(ExprBuiltin)
      | concatKeywords(constantSymbolNames).map(SyntaxConstants.Constant.withName).map(ExprConstant)
      ).map(Expression.apply)
  }

  def combine[$: P] = P(
    "\u2227" | "/\\"
  )

  def combine_types[$: P] = P(
    "\u2A53" | "//\\\\"
  )

  def equivalent[$: P] = P(
    "\u2261" | "==="
  )

  def prefer[$: P] = P(
    "\u2AFD" | "//"
  )

  def lambda[$: P] = P(
    "\u03BB" | "\\"
  )

  def arrow[$: P] = P(
    "\u2192" | "->"
  )

  def complete[$: P] = P(
    "::"
  )

  def exponent[$: P] = P(
    "e" ~ ("+" | "-").? ~ DIGIT.rep(1)
  )

  def numeric_double_literal[$: P]: P[DoubleLiteral] = P(
    // [ "+" | "-" ] 1*DIGIT ( "." 1*DIGIT [ exponent ] | exponent)
    (("+" | "-").? ~ DIGIT.rep(1) ~ ("." ~ DIGIT.rep(1) ~ exponent.? | exponent)).!
  ).flatMap { digits =>
    val number = digits.toDouble
    if (number.isFinite)
      Pass(DoubleLiteral(number)) else Fail(s"Digits $digits do not represent a finite Double value")
  }

  def minus_infinity_literal[$: P]: P[DoubleLiteral] = P(
    "-" ~ requireKeyword("Infinity")
  ).map(_ => DoubleLiteral(Double.NegativeInfinity))

  def plus_infinity_literal[$: P]: P[DoubleLiteral] = P(
    requireKeyword("Infinity")
  ).map(_ => DoubleLiteral(Double.PositiveInfinity))

  def double_literal[$: P]: P[DoubleLiteral] = P(
    // "-Infinity"
    minus_infinity_literal
      // "Infinity"
      | plus_infinity_literal
      // "NaN"
      | requireKeyword("NaN").map(_ => DoubleLiteral(Double.NaN))
      // "2.0"
      | numeric_double_literal
  )

  def natural_literal[$: P]: P[NaturalLiteral] = P(
    // Binary with "0b" prefix
    ("0b" ~ BIT.rep(1).!).map(bindigits => BigInt(bindigits, 2))
      // Hexadecimal with "0x" prefix
      | ("0x" ~ hexdigitAnyCase.rep(1).!).map(hexdigits => BigInt(hexdigits, 16))
      // Decimal; leading 0 digits are not allowed
      | (CharIn("1-9") ~ DIGIT.rep).!.map(digits => BigInt(digits, 10))
      // ... except for 0 itself
      | P("0").map(_ => BigInt(0))
  ).map(NaturalLiteral.apply)

  def integer_literal[$: P]: P[IntegerLiteral] = P(
    ("+" | "-").! ~ natural_literal
  ).map {
    case ("+", nat) => nat.value
    case ("-", nat) => -nat.value
  }.map(IntegerLiteral.apply)

  def temporal_literal[$: P]: P[Expression] = P(
    // "YYYY_MM_DDThh:mm:ss[+-]HH:MM", parsed as a `{ date : Date, time : Time, timeZone : TimeZone }`
    (full_date ~ CharIn("tT") ~ partial_time ~ time_offset)
      .map { case (date, time, zone) => Parser.localDateTimeWithZone(date, time, zone) }
      // "YYYY_MM_DDThh:mm:ss", parsed as a `{ date : Date, time : Time }`
      | (full_date ~ CharIn("tT") ~ partial_time)
      .map { case (date, time) => Parser.localDateTime(date, time) }
      // "hh:mm:ss[+-]HH:MM", parsed as a `{ time : Time, timeZone, TimeZone }`
      | (partial_time ~ time_offset)
      .map { case (time, zone) => Parser.localTimeWithZone(time, zone) }
      // "YYYY_MM_DD", parsed as a `Date`
      | full_date.map(Expression.apply)
      // "hh:mm:ss", parsed as a `Time`
      | partial_time.map(Expression.apply)
      // "[+-]HH:MM", parsed as a `TimeZone`
      // Carefully note that this `time_numoffset` and not `time_offset`, meaning
      // that a standalone `Z` is not a valid Dhall literal for a `TimeZone`
      | time_numoffset.map(i => Expression(TimeZoneLiteral(i)))
  )

  def date_fullyear[$: P]: P[Int] = P(
    DIGIT.rep(exactly = 4)
  ).!.map(_.toInt)

  def date_month[$: P]: P[Int] = P(
    DIGIT.rep(exactly = 2)
    //("0" ~ CharIn("1-9")) | "1" ~ CharIn("0-2") // 01, 02, ..., 11, 12
  ).!.map(_.toInt)

  def date_mday[$: P]: P[Int] = P(
    DIGIT.rep(exactly = 2)
    //    ("0" ~ CharIn("1-9")) | (CharIn("12") ~ DIGIT) | ("3" ~ CharIn("01")) // 01_28, 01_29, 01_30, 01_31 based on
    // month/year
  ).!.map(_.toInt)

  def time_hour[$: P]: P[Int] = P(
    DIGIT.rep(exactly = 2) // 00_23
  ).!.map(_.toInt)

  def time_minute[$: P]: P[Int] = P(
    DIGIT.rep(exactly = 2) // 00_59
  ).!.map(_.toInt)

  def time_second[$: P]: P[Int] = P(
    DIGIT.rep(exactly = 2) // 00_59 (**UNLIKE** RFC 3339, we don't support leap seconds)
  ).!.map(_.toInt)

  def time_secfrac[$: P]: P[String] = P( // Keep the trailing fraction of a second as String with no changes.
    "." ~ (DIGIT.! // Do not add a cut after "."!
      .rep(1) // RFC 3339
      .map(_.mkString)
      )
  )

  // Return the total count of minutes (signed integer).
  def time_numoffset[$: P]: P[Int] = P(
    ("+" | "-").! ~ time_hour ~ ":" ~ time_minute
  ).map {
    case ("+", h, m) => h * 60 + m
    case ("-", h, m) => -(h * 60 + m)
  }

  def time_offset[$: P]: P[Int] = P(
    P("Z").map(_ => 0) // "Z" desugars to "+00:00"
      | time_numoffset
  )

  def partial_time[$: P]: P[TimeLiteral] = P(
    time_hour ~ ":" ~ time_minute ~ ":" ~ time_second
      ~ time_secfrac.?
  ).flatMap { case (h, m, s, secfracOpt) =>
    val secfrac = secfracOpt.getOrElse("")
    Try(TimeLiteral.of(h, m, s, secfrac)) match {
      case Failure(exception) => Fail(s"Invalid local time literal ${TimeLiteral(h, m, s, secfrac)}, error: $exception")
      case Success(value) => Pass(value)
    }
  }

  def full_date[$: P]: P[DateLiteral] = P(
    date_fullyear ~ "-" ~ date_month ~ "-" ~ date_mday
  ).flatMap { case (y, m, d) =>
    Try(LocalDate.of(y, m, d)) match {
      case Failure(exception) => Fail(s"Invalid date literal $y-$m-$d - $exception")
      case Success(_) => Pass(DateLiteral(y, m, d))
    }
  }


  def identifier[$: P]: P[Expression] = P(
    variable | builtin
  )

  /*
    If the identifier matches one of the names in the `builtin` rule, then it is a
    builtin, and should be treated as the corresponding item in the list of
    "Reserved identifiers for builtins" specified in the `standard/README.md` document.
    It is a syntax error to specify a de Bruijn index in this case.
    Otherwise, this is a variable with name and index matching the label and index.

    This is guaranteed because `nonreserved_label` does not match any keyword or builtin, and we match builtins separately without a de Bruijn index.
     */
  def variable[$: P]: P[Expression] = P(
    nonreserved_label ~ (whsp ~ "@" ~/ whsp ~ natural_literal).?
  ).map { case (name, index) => Variable(name, index.map(_.value).getOrElse(BigInt(0))) }

  def path_character[$: P] = P( // Note: character 002D is the hyphen and needs to be escaped when used under CharIn().
    CharIn("\u0021\u0024-\u0027\u002A-\u002B\\-\u002E\u0030-\u003B\u0040-\u005A\u005E-\u007A\u007C\u007E")
  )

  def quoted_path_character[$: P] = P(
    CharIn("\u0020\u0021", "\u0023-\u002E", "\u0030-\u007F") // \u002F is the slash character '/'
      | valid_non_ascii
  )

  def unquoted_path_component[$: P] = P(
    path_character.rep(1)
  )

  def quoted_path_component[$: P] = P(
    quoted_path_character.rep(1)
  )

  def path_component[$: P] = P(
    "/" ~ (unquoted_path_component.! | ("\"" ~ quoted_path_component.! ~ "\""))
  )

  def path[$: P] = P(
    path_component.rep(1)
  )

  def local[$: P] = P(
    parent_path
      | here_path
      | home_path
      // NOTE: Backtrack if parsing this alternative fails.
      // This is because the first character of this alternative will be "/", but
      // if the second character is "/" or "\" then this should have been parsed
      // as an operator instead of a path
      | absolute_path
  )

  def parent_path[$: P] = P(
    ".." ~/ path // Relative path
  ).map(segments => ImportType.ImportPath(SyntaxConstants.FilePrefix.Parent, SyntaxConstants.FilePath.of(segments)))

  def here_path[$: P] = P(
    "." ~ path // Relative path
  ).map(segments => ImportType.ImportPath(SyntaxConstants.FilePrefix.Here, SyntaxConstants.FilePath.of(segments)))

  def home_path[$: P] = P(
    "~" ~/ path // Home_anchored path
  ).map(segments => ImportType.ImportPath(SyntaxConstants.FilePrefix.Home, SyntaxConstants.FilePath.of(segments)))

  def absolute_path[$: P] = P(
    path // Absolute path
  ).map(segments => ImportType.ImportPath(SyntaxConstants.FilePrefix.Absolute, SyntaxConstants.FilePath.of(segments)))


  def scheme[$: P]: P[SyntaxConstants.Scheme] = P(
    "http" ~ "s".?
  ).!.map(s => SyntaxConstants.Scheme.withNameInsensitive(s))

  def http_raw[$: P]: P[SyntaxConstants.ImportURL] = P(
    scheme ~ "://" ~ authority.! ~ path_abempty ~ ("?" ~ query.!).?
  ).map { case (s, a, p, q) => SyntaxConstants.ImportURL(s, a, p, q) }

  def path_abempty[$: P]: P[SyntaxConstants.FilePath] = P(
    ("/" ~ segment.!).rep
  ).map { segments => SyntaxConstants.FilePath.of(segments) }

  def authority[$: P] = P(
    (userinfo ~ "@").? ~ host ~ (":" ~ port).?
  )

  def userinfo[$: P] = P(
    (unreserved | pct_encoded | sub_delims | ":").rep
  )

  def host[$: P] = P(
    IP_literal | IPv4address | domain
  )

  def port[$: P] = P(
    DIGIT.rep
  )

  def IP_literal[$: P] = P(
    "[" ~ (IPv6address | IPvFuture) ~ "]"
  )

  def IPvFuture[$: P] = P(
    "v" ~ hexdigitAnyCase.rep(1) ~ "." ~ (unreserved | sub_delims | ":").rep(1)
  )

  def IPv6address[$: P] = P(
    ((h16 ~ ":").rep(exactly = 6) ~ ls32)
      | ("::" ~ (h16 ~ ":").rep(exactly = 5) ~ ls32)
      | (h16.? ~ "::" ~ (h16 ~ ":").rep(exactly = 4) ~ ls32)
      | ((h16 ~ (":" ~ h16).rep(max = 1)).? ~ "::" ~ (h16 ~ ":").rep(exactly = 3) ~ ls32)
      | ((h16 ~ (":" ~ h16).rep(max = 2)).? ~ "::" ~ (h16 ~ ":").rep(exactly = 2) ~ ls32)
      | ((h16 ~ (":" ~ h16).rep(max = 3)).? ~ "::" ~ (h16 ~ ":").rep(exactly = 1) ~ ls32)
      | ((h16 ~ (":" ~ h16).rep(max = 4)).? ~ "::" ~ ls32)
      | ((h16 ~ (":" ~ h16).rep(max = 5)).? ~ "::" ~ h16)
      | ((h16 ~ (":" ~ h16).rep(max = 6)).? ~ "::")
  )
  /*

                                           6( h16 ":" ) ls32
              |                       "::" 5( h16 ":" ) ls32
              | [ h16               ] "::" 4( h16 ":" ) ls32
              | [ h16 *1( ":" h16 ) ] "::" 3( h16 ":" ) ls32
              | [ h16 *2( ":" h16 ) ] "::" 2( h16 ":" ) ls32
              | [ h16 *3( ":" h16 ) ] "::"    h16 ":"   ls32
              | [ h16 *4( ":" h16 ) ] "::"              ls32
              | [ h16 *5( ":" h16 ) ] "::"              h16
              | [ h16 *6( ":" h16 ) ] "::"
  )
   */

  def h16[$: P] = P(
    hexdigitAnyCase.rep(min = 1, max = 4)
  )

  def ls32[$: P] = P(
    (h16 ~ ":" ~ h16) | IPv4address
  )

  def IPv4address[$: P] = P(
    dec_octet ~ "." ~ dec_octet ~ "." ~ dec_octet ~ "." ~ dec_octet
  )

  def dec_octet[$: P] = P(
    ("25" ~ CharIn("0-5")) //%x30_35       // 250_255
      | ("2" ~ CharIn("0-4") ~ DIGIT) // 200_249
      | ("1" ~ DIGIT.rep(exactly = 2)) // 100_199
      | (CharIn("1-9") ~ DIGIT) // 10_99
      | DIGIT // 0_9
  )

  def domain[$: P] = P(
    domainlabel ~ ("." ~ domainlabel).rep ~ ".".?
  )

  def domainlabel[$: P] = P(
    ALPHANUM.rep(1) ~ ("-".rep(1) ~ ALPHANUM.rep(1)).rep
  )

  def segment[$: P] = P(
    pchar.rep
  )

  def pchar[$: P] = P(
    unreserved | pct_encoded | sub_delims | ":" | "@"
  )

  def query[$: P] = P(
    (pchar | "/" | "?").rep
  )

  def pct_encoded[$: P] = P(
    "%" ~ hexdigitAnyCase ~ hexdigitAnyCase
  )

  def unreserved[$: P] = P(
    ALPHANUM | "-" | "." | "_" | "~"
  )

  def sub_delims[$: P] = P(
    "!" | "$" | "&" | "'" | "*" | "+" | ";" | "="
  )

  // This does not seem to be necessary. The headers field is optional.
  //  val emptyHeaders: Expression = EmptyList(RecordType(Seq(
  //    (FieldName("mapKey"), Builtin(SyntaxConstants.Builtin.Text)),
  //    (FieldName("mapValue"), Builtin(SyntaxConstants.Builtin.Text)),
  //  )))

  def http[$: P]: P[ImportType.Remote[Expression]] = P(
    http_raw ~ (whsp1 ~ requireKeyword("using") ~ whsp1 ~/ import_expression).? // Do not add cut after `http_raw ~ (whsp1 ~`.
  ).map { case (url, headers) => ImportType.Remote(url, headers) }

  def env[$: P]: P[ImportType.Env] = P(
    "env:" ~/ (
      bash_environment_variable.!
        | ("\"" ~ posix_environment_variable ~ "\"")
      )
  ).map(name => ImportType.Env(name))

  def bash_environment_variable[$: P] = P(
    (ALPHA | "_") ~ (ALPHANUM | "_").rep
  )

  def posix_environment_variable[$: P] = P(
    posix_environment_variable_character.rep(1).map(_.mkString)
  )

  def mapPosixEnvCharacter: String => Char = {
    case "\"" => '"'
    case "\\" => '\\'
    case "a" => '\u0007'
    case "b" => '\u0008'
    case "f" => '\u000C'
    case "n" => '\u000A'
    case "r" => '\u000D'
    case "t" => '\u0009'
    case "v" => '\u000B'
    case x => x.last
  }

  def posix_environment_variable_character[$: P]: P[Char] = P(
    ("\\" ~ (CharIn("\"abfnrtv") | "\\").!.map(mapPosixEnvCharacter))
      //    %x5C                 // '\'    Beginning of escape sequence
      //      ( %x22               // '"'    quotation mark  U+0022
      //        | %x5C               // '\'    reverse solidus U+005C
      //          | %x61               // 'a'    alert           U+0007
      //        | %x62               // 'b'    backspace       U+0008
      //          | %x66               // 'f'    form feed       U+000C
      //        | %x6E               // 'n'    line feed       U+000A
      //          | %x72               // 'r'    carriage return U+000D
      //        | %x74               // 't'    tab             U+0009
      //          | %x76               // 'v'    vertical tab    U+000B
      // Printable characters except double quote, backslash and equals
      | CharIn("\u0020-\u0021", "\u0023-\u003C", "\u003E-\u005B", "\u005D-\u007E").!.map(_.head)
    //  %x20_21
    //      // %x22 = '"'
    //      | %x23_3C
    //        // %x3D = '='
    //        | %x3E_5B
    //      // %x5C = "\"
    //      | %x5D_7E
  )

  def import_type[$: P]: P[ImportType[Expression]] = P(
    // Prevent parsing `missingfoo` as `missing` followed by a parse failure.
    (requireKeyword("missing") ~ !simple_label_next_char).map(_ => ImportType.Missing)
      | local
      | http
      | env
  )

  def hash[$: P] = P(
    "sha256:" ~/ hexdigitAnyCase.rep(exactly = 64).!./ // "sha256:XXX...XXX"
  )

  def import_hashed[$: P]: P[(ImportType[Expression], Option[String])] = P(
    import_type ~ (whsp1 ~ hash).?
  )

  def import_only[$: P]: P[Expression] = P(
    import_hashed ~ (whsp1 ~ requireKeyword("as") ~ whsp1 ~/ ("Text" | "Location" | "Bytes").!).?
  ).map { case (importType, digest, mode) =>
    val importMode = mode match {
      case Some("Bytes") => SyntaxConstants.ImportMode.RawBytes
      case Some("Location") => SyntaxConstants.ImportMode.Location
      case Some("Text") => SyntaxConstants.ImportMode.RawText
      case None => SyntaxConstants.ImportMode.Code
    }
    Import(importType, importMode, digest.map(BytesLiteral.of))
  }

  // The ABNF spec does not define those sub-rules. They are created only to help with debugging.

  def expression_lambda[$: P]: P[Expression] = P(lambda ~ whsp ~/ "(" ~ whsp ~/ nonreserved_label ~ whsp ~ ":" ~ whsp1 ~/ expression ~ whsp ~ ")" ~ whsp ~/ arrow ~/
    whsp ~ expression)
    .map { case (name, tipe, body) => Lambda(name, tipe, body) }

  def expression_if_then_else[$: P]: P[Expression] = P(
    requireKeyword("if") ~ whsp1 ~/ expression ~ whsp ~ requireKeyword("then") ~ whsp1 ~/ expression ~ whsp ~ requireKeyword("else") ~ whsp1 ~/ expression
  ).map { case (cond, ifTrue, ifFalse) =>
    If(cond, ifTrue, ifFalse)
  }

  def expression_let_binding[$: P]: P[Expression] = P(let_binding.rep(1) ~ requireKeyword("in") ~ whsp1 ~/ expression)
    .map { case (letBindings, expr) =>
      letBindings.foldRight(expr) { case ((varName, tipe, body), prev) => Let(varName, tipe, body, prev) }
    }

  // Experimental: "do notation"
  //  "as (M A) in bind with x : B in p with y : C in q then z"
  def expression_as_in[$: P]: P[Expression] = P(
    requireKeyword("as") ~ whsp1 ~/ application_expression ~ whsp ~
      requireKeyword("in") ~ whsp1 ~/ expression ~ whsp ~
      with_binding.rep ~ requireKeyword("then") ~ whsp1 ~/ expression
  ).map { case (Expression(Application(typeConstructor, typeArg)), bind, withBindings, thenResult) =>
    // Desugar according to https://discourse.dhall-lang.org/t/proposal-do-notation-syntax/99
    //    as (M A) in bind ... with x : B in q <rest>
    // desugars to
    //    bind B A q (\(x: B) -> desugar <rest> )
    //
    //    as (M A) in bind ...then q  <end of construction>
    // desugars to
    //    ... -> q
    val varA = ~"a"
    val varB = ~"b"

    // The type of `bind` must be ∀(a : Type) → ∀(b : Type) → M a → (a → M b) → M b
    val bindWithTypeAnnotation = bind | ((varA | _Type) ->: (varB | _Type) ->: typeConstructor(varA) ->: ((~"_" | varA) ->: typeConstructor(varB)) ->: typeConstructor(varB))

    withBindings.foldRight(thenResult) { case ((varName, varType, source), b) => bindWithTypeAnnotation(varType)(typeArg)(source)((~(varName.name) | varType) -> b) }
  }

  // A part of the do-notation syntax: "with x : B in p".
  // The type annotation is required because we will then desugar to a Lambda where a type annotation is required, and it's too early for type inference.
  def with_binding[$: P]: P[(VarName, Expression , Expression)] = P(
    requireKeyword("with") ~ whsp1 ~/ nonreserved_label ~ whsp ~ ":" ~ whsp1 ~/ expression ~ whsp ~ requireKeyword("in") ~ whsp1 ~/ expression ~ whsp
  )

  def expression_forall[$: P]: P[Expression] = P(forall ~ whsp ~/ "(" ~ whsp ~ nonreserved_label ~ whsp ~/ ":" ~ whsp1 ~/ expression ~ whsp ~ ")" ~ whsp ~ arrow ~/
    whsp ~ expression)
    .map { case (varName, tipe, body) => Forall(varName, tipe, body) }

  // (`A → B` is short-hand for `∀(_ : A) → B`)
  def expression_arrow[$: P]: P[Expression] = P(operator_expression ~ whsp ~ arrow ~/ whsp ~ expression)
    .map { case (head, body) => Forall(underscore, head, body) }

  def expression_merge[$: P]: P[Expression] = P(requireKeyword("merge") ~ whsp1 ~/ import_expression ~ whsp1 ~/ import_expression ~ whsp ~/ ":" ~ whsp1 ~/
    expression)
    .map { case (e1, e2, t) => Merge(e1, e2, Some(t)) }

  def expression_toMap[$: P]: P[Expression] = P(requireKeyword("toMap") ~ whsp1 ~/ import_expression ~/ whsp ~ ":" ~ whsp1 ~/ expression)
    .map { case (e1, e2) => ToMap(e1, Some(e2)) }

  def expression_assert[$: P]: P[Expression] = P(requireKeyword("assert") ~ whsp ~/ ":" ~ whsp1 ~/ expression)
    .map { expr => Assert(expr) }

  def expression[$: P]: P[Expression] = P(
    //  "\(x : a) -> b"
    expression_lambda./
      //
      //  "if a then b else c"
      |  expression_if_then_else./
      //
      //  "let x : t = e1 in e2"
      //  "let x     = e1 in e2"
      //  We allow dropping the `in` between adjacent let_expressions; the following are equivalent:
      //  "let x = e1 let y = e2 in e3"
      //  "let x = e1 in let y = e2 in e3"
      |  expression_let_binding./
      //
      //  "forall (x : a) -> b"
      |  expression_forall./
      //
      // Experimental: "do notation"
      //  "as (M A) in bind with x : B in p with y : C in q then z"
      |  expression_as_in./
      //
      //  "a -> b"
      //
      //  NOTE: Backtrack if parsing this alternative fails
      | NoCut(expression_arrow)
      //
      //  "a with x = b"
      //
      //  NOTE: Backtrack if parsing this alternative fails
      | NoCut(with_expression)
      //
      //  "merge e1 e2 : t"
      //
      //  NOTE: Backtrack if parsing this alternative fails since we can't tell
      //  from the keyword whether there will be a type annotation or not
      | NoCut(expression_merge)
      //
      //  "[] : t"
      //
      //  NOTE: Backtrack if parsing this alternative fails since we can't tell
      //  from the opening bracket whether or not this will be an empty list or
      //  a non-empty list
      | NoCut(empty_list_literal)
      //
      //  "toMap e : t"
      //
      //  NOTE: Backtrack if parsing this alternative fails since we can't tell
      //  from the keyword whether there will be a type annotation or not
      | NoCut(expression_toMap)
      //
      //  "assert : Natural/even 1 === False"
      | expression_assert./
      //
      //  "x : t"
      | annotated_expression./
  )
    .memoize

  def annotated_expression[$: P]: P[Expression] = P(
    operator_expression ~ (whsp ~ ":" ~ whsp1 ~/ expression).?
  ).map { case (expr, tipe) =>
    tipe match {
      case Some(t) => Annotation(expr, t)
      case None => expr
    }
  }

  def let_binding[$: P] = P(
    requireKeyword("let") ~ whsp1 ~/ nonreserved_label ~ whsp ~ (":" ~ whsp1 ~/ expression ~ whsp).? ~ "=" ~ whsp ~/ expression ~ whsp1./
  )

  def empty_list_literal[$: P]: P[Expression] = P(
    "[" ~ whsp ~ ("," ~ whsp).? ~ "]" ~ whsp ~/ ":" ~ whsp1 ~/ expression
  ).map(expr => EmptyList(expr))

  def with_expression[$: P] = P(
    import_expression ~ (whsp1 ~ "with" ~ whsp1 ~/ with_clause).rep(1)
    // record with x1.y1.z1 = expr1 with x2.y2.z2 = expr2   should be represented by With(  With(record, Seq(x1, y1, z1), expr1), Seq(x2, y2, z2), expr2)
  ).map { case (expr, substs) =>
    def toPathComponent(f: FieldName): PathComponent = if (f.name == "?") PathComponent.DescendOptional else PathComponent.Label(f)

    substs.foldLeft(expr) { case (prev, (varName, fields, target)) => With(prev, (varName +: fields).map(toPathComponent), target)
    }
  }

  def with_clause[$: P] = P(
    with_component.map(FieldName) ~ (whsp ~ "." ~ whsp ~/ with_component.map(FieldName)).rep ~ whsp ~ "=" ~ whsp ~/ operator_expression
  )

  def operator_expression[$: P]: P[Expression] = P(
    equivalent_expression
  )

  private implicit class FoldOpExpression(resultWithExpressionSequence: P[(Expression, Seq[Expression])]) {
    def withOperator(op: SyntaxConstants.Operator): P[Expression] =
      resultWithExpressionSequence.map { case (head, tail) => tail.foldLeft(head)((prev, arg) => ExprOperator(prev, op, arg)) }
  }

  def equivalent_expression[$: P]: P[Expression] = P(
    import_alt_expression ~ (whsp ~ equivalent ~ whsp ~/ import_alt_expression).rep
  ).withOperator(SyntaxConstants.Operator.Equivalent)
    .memoize

  def import_alt_expression[$: P]: P[Expression] = P(
    or_expression ~ (whsp ~ opAlternative ~ whsp1 ~/ or_expression).rep
  ).withOperator(SyntaxConstants.Operator.Alternative)
    .memoize

  def or_expression[$: P]: P[Expression] = P(
    plus_expression ~ (whsp ~ opOr ~ whsp ~/ plus_expression).rep
  ).withOperator(SyntaxConstants.Operator.Or)
    .memoize

  def plus_expression[$: P]: P[Expression] = P(
    text_append_expression ~ (whsp ~ opPlus ~ whsp1 ~/ text_append_expression).rep
  ).withOperator(SyntaxConstants.Operator.Plus)
    .memoize

  def text_append_expression[$: P]: P[Expression] = P(
    list_append_expression ~ (whsp ~ opTextAppend ~ whsp ~/ list_append_expression).rep
  ).withOperator(SyntaxConstants.Operator.TextAppend)
    .memoize

  def list_append_expression[$: P]: P[Expression] = P(
    and_expression ~ (whsp ~ opListAppend ~ whsp ~/ and_expression).rep
  ).withOperator(SyntaxConstants.Operator.ListAppend)
    .memoize

  def and_expression[$: P]: P[Expression] = P(
    combine_expression ~ (whsp ~ opAnd ~ whsp ~/ combine_expression).rep
  ).withOperator(SyntaxConstants.Operator.And)
    .memoize

  def combine_expression[$: P]: P[Expression] = P(
    prefer_expression ~ (whsp ~ combine ~ whsp ~/ prefer_expression).rep
  ).withOperator(SyntaxConstants.Operator.CombineRecordTerms)
    .memoize

  def prefer_expression[$: P]: P[Expression] = P(
    combine_types_expression ~ (whsp ~ prefer ~ whsp ~/ combine_types_expression).rep
  ).withOperator(SyntaxConstants.Operator.Prefer)
    .memoize

  def combine_types_expression[$: P]: P[Expression] = P(
    times_expression ~ (whsp ~ combine_types ~ whsp ~/ times_expression).rep
  ).withOperator(SyntaxConstants.Operator.CombineRecordTypes)
    .memoize

  def times_expression[$: P]: P[Expression] = P(
    equal_expression ~ (whsp ~ opTimes ~ whsp ~/ equal_expression).rep
  ).withOperator(SyntaxConstants.Operator.Times)
    .memoize

  def equal_expression[$: P]: P[Expression] = P(
    not_equal_expression ~ (whsp ~ opEqual ~ whsp ~ not_equal_expression).rep // Should not cut because == can be confused with ===
  ).withOperator(SyntaxConstants.Operator.Equal)
    .memoize

  def not_equal_expression[$: P]: P[Expression] = P(
    application_expression ~ (whsp ~ opNotEqual ~ whsp ~/ application_expression).rep
  ).withOperator(SyntaxConstants.Operator.NotEqual)
    .memoize

  def application_expression[$: P]: P[Expression] = P(
    first_application_expression ~ (whsp1 ~ import_expression).rep // Do not insert a cut after whsp1 here.
  ).map { case (head, tail) => tail.foldLeft(head)((prev, expr) => Application(prev, expr)) }

  def first_application_expression[$: P]: P[Expression] = P(
    //  "merge e1 e2"
    (requireKeyword("merge") ~ whsp1 ~/ import_expression ~ whsp1 ~/ import_expression)
      .map { case (e1, e2) => Expression(Merge(e1, e2, None)) }
      //
      //  "Some e"
      | (requireKeyword("Some") ~ whsp1 ~/ import_expression)
      .map(expr => Expression(KeywordSome(expr)))
      //
      //  "toMap e"
      | (requireKeyword("toMap") ~ whsp1 ~/ import_expression)
      .map(expr => Expression(ToMap(expr, None)))
      //
      //  "showConstructor e"
      | (requireKeyword("showConstructor") ~ whsp1 ~/ import_expression)
      .map(expr => Expression(ShowConstructor(expr)))
      //
      | import_expression
  )

  def import_expression[$: P]: P[Expression] = P(
    import_only | completion_expression
  )
    .memoize

  def completion_expression[$: P]: P[Expression] = P(
    selector_expression ~ (whsp ~ complete ~ whsp ~ selector_expression).?
  ).map {
    case (expr, None) => expr
    case (expr, Some(tipe)) => Completion(expr, tipe)
  }

  def selector_expression[$: P]: P[Expression] = P(
    primitive_expression ~ (whsp ~ "." ~ whsp ~ /* No cut here, or else (List ./imported.file) cannot be parsed. */ selector).rep
  ).map { case (base, selectors) => selectors.foldLeft(base)((prev, selector) => selector.chooseExpression(prev)) }
    .memoize

  sealed trait ExpressionSelector {
    def chooseExpression(base: Expression): Expression = this match {
      case ExpressionSelector.ByField(fieldName) => Field(base, fieldName)
      case ExpressionSelector.ByLabels(fieldNames) => ProjectByLabels(base, fieldNames)
      case ExpressionSelector.ByType(typeExpr) => ProjectByType(base, typeExpr)
    }
  }

  object ExpressionSelector {
    final case class ByField(fieldName: FieldName) extends ExpressionSelector

    final case class ByLabels(fieldName: Seq[FieldName]) extends ExpressionSelector

    final case class ByType(typeExpr: Expression) extends ExpressionSelector
  }

  def selector[$: P]: P[ExpressionSelector] = P(
    any_label.map(FieldName).map(ExpressionSelector.ByField)
      | labels.map(ExpressionSelector.ByLabels)
      | type_selector.map(ExpressionSelector.ByType)
  )

  def labels[$: P]: P[Seq[FieldName]] = P(
    "{" ~ whsp ~ ("," ~ whsp).? ~ (any_label_or_some ~ whsp ~ ("," ~ whsp ~ any_label_or_some ~ whsp).rep ~ ("," ~ whsp).?).? ~ "}"
  ).map(_.map { case (x, y) => x +: y }.toSeq.flatten).map(_.map(FieldName))

  def type_selector[$: P] = P(
    "(" ~ whsp ~ expression ~ whsp ~ ")"
  )

  def primitive_expression[$: P]: P[Expression] = P(
    temporal_literal
      // Put bytes_literal first, or else we will just parse the initial 0 as natural_literal
      //  '0x"01234567689abcdef"'
      | bytes_literal.map(Expression.apply)
      //
      //  "2.0"
      | double_literal.map(Expression.apply)
      //
      //  "2"
      | natural_literal.map(Expression.apply)
      //
      //  "+2" or "-2"
      | integer_literal.map(Expression.apply)
      //
      //  '"ABC"'
      | text_literal.map(Expression.apply)
      //
      //  "{ foo = 1      , bar = True }"
      //  "{ foo : Integer, bar : Bool }"
      | ("{" ~/ whsp ~ ("," ~ whsp).? ~ record_type_or_literal ~ whsp ~ "}")
      .map(_.getOrElse(Expression(RecordType(Seq()))))
      //
      //  "< Foo : Integer | Bar : Bool >"
      //  "< Foo | Bar : Bool >"
      | P("<" ~/ whsp ~ ("|" ~/ whsp).? ~ union_type ~ whsp ~ ">")
      //
      //  "[1, 2, 3]"
      | non_empty_list_literal
      //
      //  "x"
      //  "x@2"
      | identifier
      //
      //  "( e )"
      | P("(" ~/ complete_expression ~/ ")")
  )
    .memoize

  def record_type_or_literal[$: P]: P[Option[Expression]] = P(
    empty_record_literal.map(Expression.apply).map(Some.apply)
      | non_empty_record_type_or_literal.?
  )

  def empty_record_literal[$: P]: P[RecordLiteral[Expression]] = P(
    "=" ~/ (whsp ~ ",").?
  ).map(_ => RecordLiteral(Seq()))

  def non_empty_record_type_or_literal[$: P]: P[Expression] = P(
    non_empty_record_type | non_empty_record_literal
  ).map(Expression.apply)

  def non_empty_record_type[$: P]: P[RecordType[Expression]] = P(
    record_type_entry ~ (whsp ~ "," ~ whsp ~ record_type_entry).rep ~ (whsp ~ ",").?
  ).map { case (headName, headExpr, tail) => (headName, headExpr) +: tail }.map(RecordType[Expression]).map(_.sorted)

  def record_type_entry[$: P]: P[(FieldName, Expression)] = P(
    any_label_or_some.map(FieldName) ~ whsp ~ ":" ~/ whsp1 ~/ expression
  )

  def non_empty_record_literal[$: P]: P[RecordLiteral[Expression]] = P(
    record_literal_entry ~ (whsp ~ "," ~ whsp ~ record_literal_entry).rep ~ (whsp ~ ",").?
  ).map { case (head, tail) => RecordLiteral.of(head +: tail).sorted }

  def record_literal_entry[$: P]: P[RawRecordLiteral] = P(
    any_label_or_some.map(FieldName) ~ record_literal_normal_entry.?
  ).map { case (base, defs) => RawRecordLiteral(base, defs)}

  def record_literal_normal_entry[$: P]: P[(Seq[FieldName], Expression)] = P(
    (whsp ~ "." ~ whsp ~/ any_label_or_some.map(FieldName)).rep ~ whsp ~ "=" ~ whsp ~/ expression
  )
    .memoize

  def union_type[$: P]: P[Expression] = P(
    (union_type_entry ~ (whsp ~ "|" ~ whsp ~ union_type_entry).rep ~ (whsp ~ "|").?).?
  ).map {
    case Some((headName, headType, tail)) => UnionType((headName, headType) +: tail).sorted
    case None => UnionType(Seq())
  }

  def union_type_entry[$: P] = P(
    any_label_or_some.map(ConstructorName) ~ (whsp ~ ":" ~/ whsp1 ~/ expression).?
  )
    .memoize

  def non_empty_list_literal[$: P]: P[Expression] = P(
    "[" ~/ whsp ~ ("," ~ whsp).? ~ expression ~ whsp ~ ("," ~ whsp ~ /* No cut here, or else [, ,] cannot be parsed. */ expression ~ whsp).rep ~ ("," ~/ whsp).? ~ "]"
  ).map { case (head, tail) => Expression(NonEmptyList(head +: tail)) }

  def shebang[$: P] = P(
    "#!" ~/ not_end_of_line.rep.! ~ end_of_line
  )

  def complete_dhall_file[$: P] = P( // TODO: figure out whether we need ~ End here.
    shebang.rep ~ (line_comment | block_comment | whsp1).rep.! ~ complete_expression ~ line_comment_prefix.? ~ End
  ).map { case (shebangContents, headerComments, expr) => DhallFile(shebangContents, headerComments, expr) }

  def complete_expression[$: P] = P(
    whsp ~ expression ~ whsp
  )
    .memoize

  // Helpers to make sure we are using valid keyword and operator names.
  def requireKeyword[$: P](name: String): P[Unit] = {
    assert(simpleKeywordsSet contains name, s"Keyword $name must be one of the supported Dhall keywords")
    P(name)
  }

}
