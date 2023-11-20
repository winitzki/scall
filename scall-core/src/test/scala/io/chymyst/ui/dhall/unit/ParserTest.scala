package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse._
import io.chymyst.ui.dhall.Syntax.Expression
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.SyntaxConstants.{Builtin, FieldName, VarName}
import io.chymyst.ui.dhall.unit.TestFixtures._
import io.chymyst.ui.dhall.unit.TestUtils._
import io.chymyst.ui.dhall.{Grammar, SyntaxConstants}
import munit.FunSuite

import java.nio.file.{Files, Paths}

class ParserTest extends FunSuite {

  test("quoted_label_char") {
    val Parsed.Success((), 1)                 = parse("expected", Grammar.quoted_label_char(_))
    val f @ Parsed.Failure(failure, index, _) = parse("`expected", Grammar.quoted_label_char(_))
    expect(failure == "")
    expect(index == 0)
    expect(f.msg == """Position 1:1, found "`expected"""")
  }

  test("requireKeyword") {
    val Parsed.Success(_, 5) = parse("merge", Grammar.requireKeyword("merge")(_))
    intercept[AssertionError] {
      parse("blah", Grammar.requireKeyword("blah")(_))
    }
  }

  test("end_of_line") {
    check(Grammar.end_of_line(_), "\n\n\n", (), 1)
    check(Grammar.end_of_line(_), "\r\n\n", (), 2)
    check(Grammar.end_of_line(_), "\n\r\n", (), 1)
  }

  test("valid_non_ascii") {
    toFail(Grammar.valid_non_ascii(_), "\n\n\n", "", " found ", 0)
    toFail(Grammar.valid_non_ascii(_), "", "", " found \"\"", 0)
    toFail(Grammar.valid_non_ascii(_), "abc", "", " found ", 0)

    "_ -`~,<?/\\\b".foreach { c =>
      toFail(Grammar.valid_non_ascii(_), s"$c", "", " found ", 0)
    }

    check(Grammar.valid_non_ascii(_), "ф  ", (), 1)
    "¡™£¢∞§¶•ªº≠œ∑´®†¥¨ˆøπ“‘«åß∂ƒ©˙∆˚¬…æ≥≤µ˜∫√ç≈Ω⁄€‹›ﬁ°·‚—±»’”∏Ø¨ÁÅÍÎÏÓÔÒÚÆ˘¯Â¸фывапролджэאבגדהוזחטיכךלמםנןסעפףצץקרששׂשׁתבּגּדּהּוּוֹזּטּיּכּךּךָךְלּמּנּסּפּףּצּקּשּׂשּׁתּ"
      .foreach { c =>
        check(Grammar.valid_non_ascii(_), s"$c", (), 1)
      }
  }

  test("valid_non_ascii with large Unicode values from UTF-16 surrogates") {
    check(Grammar.valid_non_ascii(_), "\uD800\uDC02", (), 2)
    check(Grammar.valid_non_ascii(_), "\uD83E\uDFFF", (), 2)
    check(Grammar.valid_non_ascii(_), "\uD83F\uDFFD", (), 2)
    toFail(Grammar.valid_non_ascii(_), "\uD83F\uDFFE", "", " found ", 0)
    toFail(Grammar.valid_non_ascii(_), "\uD83F\uDFFF", "", " found ", 0)
  }

  test("valid_non_ascii with large Unicode values from file") {
    import fastparse._
    import NoWhitespace._

    val input: String = TestUtils.readFileToString((this.getClass.getResource("/valid_non_ascii.txt").toURI.getPath))

    def rule[$: P] = P(Grammar.valid_non_ascii.rep)

    check(rule(_), input, (), 8)
  }

  test("tab") {
    check(Grammar.tab(_), "\t", (), 1)
    toFail(Grammar.tab(_), "\n\t", "", " found ", 0)
  }

  test("block_comment") {
    blockComments.foreach { input =>
      check(Grammar.block_comment(_), input, (), input.length)
    }
  }

  test("whsp") {
    (blockComments ++ multilineComments).foreach { input =>
      check(Grammar.whsp(_), input, (), input.length)
    }
  }

  test("comment fails when not closed") {
    import fastparse._
    import NoWhitespace._

    // Incomplete comments will not fail to parse without ~End unless `{-` cuts. But if it cuts we cannot parse identifiers with trailing comments.
    def whspClosed[$: P] = Grammar.whsp ~ End

    toFail(whspClosed(_), "{-", "", "", 2)
    toFail(whspClosed(_), "{- {- -} -0", "", "", 8)
  }

  test("comment fails when incomplete") {
    // Nothing gets parsed.
    Seq( // Examples may contain trailing whitespace or leading whitespace.
      "",
      "фыва3 ç≈Ω⁄€‹›ﬁ° }}-}",
    ).foreach { input =>
      check(Grammar.whsp(_), input, (), 0)
    }

    import fastparse._
    import NoWhitespace._

    // Incomplete comments will not fail to parse without ~End unless `{-` cuts. But if it cuts we cannot parse identifiers with trailing comments.
    def whspClosed[$: P] = Grammar.whsp ~ End

    Seq( // Examples may contain trailing whitespace or leading whitespace.
      "   {- 1 - }- }  ",
      """   {-2
        | - }-
        |}  |
        |- }""".stripMargin,
    ).foreach { input =>
      toFail(whspClosed(_), input, "", "", 5)
    }

  }

  test("whitespace_chunk") {
    (blockComments ++ whitespaceComments1).foreach { input =>
      check(Grammar.whitespace_chunk(_), input, (), input.length)
    }

    check(Grammar.whitespace_chunk(_), " -- \n", (), 1)
  }

  test("whsp1") {
    (blockComments ++ whitespaceComments1 ++ whitespaceCommentsWithLeadingSpace).foreach { input =>
      check(Grammar.whsp1(_), input, (), input.length)
    }
  }

  test("keyword") {
    Grammar.simpleKeywords.foreach { input =>
      check(Grammar.keyword(_), input, input, input.length)
      check(Grammar.keyword(_), input + " ", input, input.length)
      check(Grammar.keyword(_), input + "(", input, input.length)
    }

    check(Grammar.forall(_), "forall", (), 6)
    check(Grammar.forall(_), "∀ ", (), 1)
  }

  test("simple_label") {
    Seq("abcd", "witha", "awith", "if_", "asa", "_in", "asif", "forallx").foreach { input =>
      check(Grammar.simple_label(_), input, input, input.length)
    }
    check(Grammar.simple_label(_), "x∀ ", "x", 1)
    toFail(Grammar.simple_label(_), "∀", "", "", 0)
    toFail(Grammar.simple_label(_), "∀x", "", "", 0)
  }

  test("builtin names") {
    val names = SyntaxConstants.Builtin.namesToValuesMap
    expect(names.keySet.size == 37)
    names.foreach { case (name, c) =>
      check(Grammar.builtin(_), name, Expression(ExprBuiltin(c)), name.length)
    }
  }

  test("constant names") {
    val names = SyntaxConstants.Constant.namesToValuesMap
    expect(names.keySet.size == 5)
    names.foreach { case (name, c) =>
      check(Grammar.builtin(_), name, Expression(ExprConstant(c)), name.length)
    }
  }

  test("parse Location as a field name") {
    val input    = "Location : Natural"
    val expected = Expression(Annotation(Expression(Variable(VarName("Location"), BigInt(0))), Expression(ExprBuiltin(Builtin.Natural))))
    check(Grammar.import_expression(_), input, Expression(Variable(VarName("Location"), BigInt(0))))
    check(Grammar.annotated_expression(_), input, expected)
    check(Grammar.complete_expression(_), input, expected)
    check(
      Grammar.complete_expression(_),
      "{ Bytes : Natural, Text: Natural, Location: Natural }",
      Expression(
        RecordType(
          List(
            (FieldName("Bytes"), Expression(ExprBuiltin(Builtin.Natural))),
            (FieldName("Location"), Expression(ExprBuiltin(Builtin.Natural))),
            (FieldName("Text"), Expression(ExprBuiltin(Builtin.Natural))),
          )
        )
      ),
    )
  }

  test("numeric_double_literal") {
    Map("1.0" -> 1.0, "-1.0" -> -1.0, "1.2" -> 1.2, "1.5" -> 1.5, "100e2" -> 100e2, "-100e12" -> -100e12, "100e-2" -> 100e-2, "1.0e-3" -> 1.0e-3).foreach {
      case (s, d) =>
        check(Grammar.numeric_double_literal(_), s, DoubleLiteral(d), s.length)
    }
  }

  test("natural_literal") {
    Map(
      "9"                                        -> BigInt(9),
      "1234512345123451234512345123451234512345" -> BigInt("1234512345123451234512345123451234512345"),
      "0"                                        -> BigInt(0),
      "0x10"                                     -> BigInt(16),
      "0xFFFF"                                   -> BigInt(65535),
    ).foreach { case (s, d) =>
      check(Grammar.natural_literal(_), s, NaturalLiteral(d), s.length)
    }
    // Leading zero digits are not allowed.
    check(Grammar.natural_literal(_), "00001", NaturalLiteral(BigInt(0)), 1)
  }

  test("integer_literal") {

    Map(
      "+9"                                        -> BigInt(9),
      "+1234512345123451234512345123451234512345" -> BigInt("1234512345123451234512345123451234512345"),
      "+0"                                        -> BigInt(0),
      "+0x10"                                     -> BigInt(16),
      "+0xFFFF"                                   -> BigInt(65535),
      "-9"                                        -> BigInt(-9),
      "-1234512345123451234512345123451234512345" -> BigInt("-1234512345123451234512345123451234512345"),
      "-0"                                        -> BigInt(0),
      "-0x10"                                     -> BigInt(-16),
      "-0xFFFF"                                   -> BigInt(-65535),
    ).foreach { case (s, d) =>
      check(Grammar.integer_literal(_), s, IntegerLiteral(d), s.length)
    }
    // Leading zero digits are not allowed.
    check(Grammar.integer_literal(_), "+00001", IntegerLiteral(BigInt(0)), 2)
    check(Grammar.integer_literal(_), "-00001", IntegerLiteral(BigInt(0)), 2)
    // Either plus or minus sign is required.
    toFail(Grammar.integer_literal(_), "0", "", "", 0)
    toFail(Grammar.integer_literal(_), "1", "", "", 0)
    toFail(Grammar.integer_literal(_), " ", "", "", 0)
  }

  // TODO: tests for date and time literals

  test("identifier") {
    check(identifiers, Grammar.identifier(_))
  }

  test("identifier with backquotes") {
    check(identifiersWithBackquote, Grammar.identifier(_))
  }

  test("variable with backquotes") {
    check(identifiersWithBackquote, Grammar.variable(_))
  }

  test("nonreserved_label with backquotes") {
    check(identifiersWithBackquote.map { case (k, v) => (k, v.scheme.asInstanceOf[Variable].name) }, Grammar.nonreserved_label(_))
  }

  test("label with backquotes") {
    check(identifiersWithBackquote.map { case (k, v) => (k, v.scheme.asInstanceOf[Variable].name.name) }, Grammar.label(_))
  }

  test("identifier special cases") {
    check(Grammar.identifier(_), "Natural+blahblah", Expression(ExprBuiltin(SyntaxConstants.Builtin.Natural)), 7)
    toFail(Grammar.identifier(_), "-abc", "", "", 0)
    toFail(Grammar.identifier(_), "/abc", "", "", 0)
  }

  test("bytes_literal") {
    val Parsed.Success(result, 12) = parse("0x\"64646464\"", Grammar.bytes_literal(_))
    expect(new String(result.bytes) == "dddd")
  }

  test("primitive_expression") {
    check(primitiveExpressions, Grammar.primitive_expression(_))

    val Parsed.Success(Expression(result: BytesLiteral), 12) = parse("0x\"64646464\"", Grammar.primitive_expression(_))
    expect(new String(result.bytes) == "dddd")
  }

  test("selector_expression") {
    check(primitiveExpressions ++ selectorExpressions, Grammar.selector_expression(_))
  }

  test("completion_expression") {
    check(primitiveExpressions ++ selectorExpressions ++ completionExpressions, Grammar.completion_expression(_))
  }

}
