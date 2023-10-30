package io.chymyst.ui.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import com.upokecenter.cbor.CBORObject
import fastparse.{Parsed, parse}
import io.chymyst.ui.dhall.Syntax._
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.Syntax.{DhallFile, Expression}
import io.chymyst.ui.dhall.SyntaxConstants.Builtin.Natural
import io.chymyst.ui.dhall.SyntaxConstants.FilePrefix.Here
import io.chymyst.ui.dhall.SyntaxConstants.ImportMode.{Code, RawText}
import io.chymyst.ui.dhall.SyntaxConstants.ImportType.{Env, Missing, Path}
import io.chymyst.ui.dhall.SyntaxConstants.{ConstructorName, FieldName, File, VarName}
import io.chymyst.ui.dhall.unit.TestUtils.{check, printFailure, toFail, v}
import io.chymyst.ui.dhall._
import munit.FunSuite

import scala.util.Try

class SimpleExpressionTest extends FunSuite {

  test("simple invalid expression: 1+1") {
    toFail(Grammar.complete_dhall_file(_), "1+1", "", "", 1)
    //    val Parsed.Success(DhallFile(Seq(), result), _) = Parser.parseDhall("1+1")
    //    val expected = NaturalLiteral(1)
    //    expect(result == expected, "1+1 must be parsed as 1".nonEmpty)
  }

  test("simple expression: { foo, bar }") {
    val Parsed.Success(DhallFile(Seq(), result), _) = Parser.parseDhall("{ foo, bar }")
    val expected: Expression = RecordLiteral[Expression](List(
      (FieldName("foo"), v("foo")),
      (FieldName("bar"), v("bar")),
    )).sorted
    expect(result == expected, "{ foo, bar } must be parsed in the order bar, foo".nonEmpty)
  }

  test("simple expression: x") {
    expect(parse("x", Grammar.expression(_)).get.value == v("x"))
  }

  test("simple expression: let x = 1 in x with hand-written grammar") {
    expect(parse("1", Grammar.expression(_)).get.value.scheme == NaturalLiteral(1))
    expect(parse("x", Grammar.expression(_)).get.value == v("x"))
    val expected: (VarName, Option[Expression], Expression) = (VarName("x"), None, Expression(NaturalLiteral(1)))
    expect(parse("let x = 1 ", Grammar.let_binding(_)).get.value == expected)

    TestUtils.toFail(Grammar.identifier(_), "in", "", "", 0)

    expect(parse("1 in", Grammar.application_expression(_)).get.value.scheme == NaturalLiteral(1))

    import fastparse._
    import NoWhitespace._
    def grammar1[$: P] = P(Grammar.let_binding)

    expect(parse("let x = 1 ", grammar1(_)).get.value == expected)

    def grammar2[$: P] = P(
      Grammar.let_binding ~ Grammar.requireKeyword("in")
    )

    expect(parse("let x=1 in ", grammar2(_)).get.value == expected)

    def grammar3[$: P] = (Grammar.let_binding ~ Grammar.requireKeyword("in"))

    expect(parse("let x = 1 in x", grammar3(_)).get.value == expected)
  }

  test("simple expression: let x = 1 in y") {
    val Parsed.Success(DhallFile(Seq(), result), _) = Parser.parseDhall("let x = 1 in y")
    val expected = Let(VarName("x"), None, Expression(NaturalLiteral(1)), Expression(v("y")))
    expect(result.scheme == expected)
  }

  test("simple expression: (x)") {
    val Parsed.Success(result, _) = parse("(x)", Grammar.primitive_expression(_))
    val expected = v("x")
    expect(result == expected)
  }

  test("parse a string interpolation") {
    val Parsed.Success(DhallFile(Seq(), result), _) = Parser.parseDhall(""" "${1}" """)
    val expected = TextLiteral[Expression](List(("", NaturalLiteral(1))), "")
    expect(result.scheme == expected)
  }

  test("parse a sample file") {
    val testFile = getClass.getClassLoader.getResourceAsStream("tests/parser/success/whitespaceBuffetA.dhall")
    val Parsed.Success(DhallFile(Seq(), result), _) = Parser.parseDhall(testFile)
  }

  test("expression and a block comment") {
    val Parsed.Success(DhallFile(Seq(), result), _) = Parser.parseDhall("""1 {- -}""")
    val expected = NaturalLiteral(1)
    expect(result.scheme == expected)
  }

  test("expression and a line comment") {
    val Parsed.Success(DhallFile(Seq(), result), _) = Parser.parseDhall("""1 -- aaa \n""")
    val expected = NaturalLiteral(1)
    expect(result.scheme == expected)
  }

  test("expression followed by comment") {
    import fastparse._
    import NoWhitespace._
    val input = "x {- -}"
    val expected = v("x")

    def grammar1[$: P] = Grammar.whsp ~ Grammar.expression ~ Grammar.whsp

    val failures = Seq(
      Try(check(Grammar.whsp(_), " {- -}", ())),
      Try(check(Grammar.whsp(_), "", ())),
      Try(check(Grammar.expression(_), "x", expected)),
      Try(check(Grammar.primitive_expression(_), "x", expected)),
      Try(check(grammar1(_), "x {- -}", expected)),
    ).filter(_.isFailure).map(_.failed.get.getMessage)
    if (failures.nonEmpty) println(s"Found ${failures.size} failures:\n${failures.mkString("\n")}")
    expect(failures.isEmpty)
  }

  test("parse assert : x") {
    val input = "assert : x"
    val expected = Assert[Expression](v("x"))
    val Parsed.Success(result1, _) = parse(input, Grammar.expression_assert(_))
    expect(result1.scheme == expected)
    val Parsed.Success(result2, _) = parse(input, Grammar.expression(_))
    expect(result2.scheme == expected)
    val Parsed.Success(result3, _) = parse(input, Grammar.complete_expression(_))
    expect(result3.scheme == expected)
    val Parsed.Success(result4, _) = parse(input, Grammar.complete_dhall_file(_))
    expect(result4.value.scheme == expected)
  }

  test("parse x === y") {
    import fastparse._
    val input = "x === y"
    val expected = ExprOperator[Expression](v("x"), SyntaxConstants.Operator.Equivalent, v("y"))

    check(Grammar.equivalent(_), "===", ())
    check(Grammar.import_alt_expression(_), "x", v("x"))

    // Expression "x === y" must be parsed as "x", leaving " === y" unconsumed!
    val failures = Seq(
      Try(check(Grammar.completion_expression(_), input, v("x"))),
      Try(check(Grammar.import_expression(_), input, v("x"))),
      Try(check(Grammar.first_application_expression(_), input, v("x"))),
      Try(check(Grammar.application_expression(_), input, v("x"))),
      Try(check(Grammar.not_equal_expression(_), input, v("x"))),
      Try(check(Grammar.equal_expression(_), input, v("x"))),
      Try(check(Grammar.times_expression(_), input, v("x"))),
      Try(check(Grammar.combine_types_expression(_), input, v("x"))),
      Try(check(Grammar.prefer_expression(_), input, v("x"))),
      Try(check(Grammar.combine_expression(_), input, v("x"))),
      Try(check(Grammar.and_expression(_), input, v("x"))),
      Try(check(Grammar.list_append_expression(_), input, v("x"))),
      Try(check(Grammar.text_append_expression(_), input, v("x"))),
      Try(check(Grammar.plus_expression(_), input, v("x"))),
      Try(check(Grammar.or_expression(_), input, v("x"))),
      Try(check(Grammar.import_alt_expression(_), input, v("x"))),
    ).filter(_.isFailure).map(_.failed.get).map(printFailure).mkString("\n\n")
    if (failures.nonEmpty) println(s"ERROR failures = $failures")

    val Parsed.Success(result1, _) = parse(input, Grammar.equivalent_expression(_))
    expect(result1.scheme == expected)
  }

  test("parse assert : x === y") {
    val input = "assert : x === y"
    val expected = Assert[Expression](ExprOperator[Expression](v("x"), SyntaxConstants.Operator.Equivalent, v("y")))
    val Parsed.Success(result1, _) = parse(input, Grammar.expression_assert(_))
    expect(result1.scheme == expected)
    val Parsed.Success(result2, _) = parse(input, Grammar.expression(_))
    expect(result2.scheme == expected)
    val Parsed.Success(result3, _) = parse(input, Grammar.complete_expression(_))
    expect(result3.scheme == expected)
    val Parsed.Success(result4, _) = parse(input, Grammar.complete_dhall_file(_))
    expect(result4.value.scheme == expected)
  }

  test("simple_label") {
    val input = "witha"
    check(Grammar.simple_label(_), input, "witha")
  }

  test("empty record literal") {
    check(Grammar.complete_expression(_), "{ }", Expression(RecordType(Seq())))
    check(Grammar.complete_expression(_), "{=}", Expression(RecordLiteral(Seq())))
    check(Grammar.complete_expression(_), "{,}", Expression(RecordType(Seq())))
    check(Grammar.complete_expression(_), "{}", Expression(RecordType(Seq())))
  }

  test("variables or missing import ambiguity 1") {
    check(Grammar.complete_expression(_), "missingas Text", Expression(Application(v("missingas"), ExprBuiltin(SyntaxConstants.Builtin.Text))))
  }

  test("variables or missing import ambiguity 2") {
    toFail(Grammar.complete_expression(_), "missing as text", "", "", 11)
  }

  test("variables or missing import ambiguity 3") {
    import fastparse._, NoWhitespace._, Grammar._
    def grammar[$: P] = P(("a" ~ !"b" | "ab") ~ End)

    check(grammar(_), "ab", ())
  }

  test("variable name missing//foo, conflict with import declaration") {

    check(Grammar.simple_label(_), "missingas", "missingas")
    check(Grammar.identifier(_), "missingas", v("missingas"))

    check(Seq(
      "missing as Text" -> Expression(Import(Missing, RawText, None)),
      "missingas text" -> Expression(Application(v("missingas"), v("text"))),
      "missing//foo" -> v("missing//foo"),
    ) ++ Seq("missingas", "Natural/blah", "lets").map { m =>
      s"let $m = \\(x: Natural) -> x in let text = 2 in $m text" -> Expression(Let(VarName(m), None, Lambda[Expression](VarName("x"), ExprBuiltin(Natural), v("x")), Let[Expression](VarName("text"), None, NaturalLiteral(2), Application(v(m), v("text")))))
    }, Grammar.complete_expression(_))

  }

  test("invalid utf-8") {
    // The byte sequence 0xED, 0xA0, 0x80 is not a valid UTF-8 sequence.
    val input = Array(0x20, 0xED, 0xA0, 0x80, 0x20).map(_.toByte)
    import fastparse._
    import NoWhitespace._
    def grammar[$: P] = P(SingleChar.rep)

    val result = parse(input, grammar(_))
    // \uFFFD is the "replacement character" that is substituted for invalid UTF-8 sequences.
    assert(result.get.value == Seq(0x20, 0xFFFD, 0x20).map(_.toChar))
    // TODO: figure out how `fastparse` decodes a byte array into characters. This test shows that the non-UTF8 sequence is decoded as a single character \uFFFD.

    val invalidUtf8 = " {-".getBytes ++ input ++ "-}".getBytes
    toFail(Grammar.whsp(_), invalidUtf8, 3)

    val utfReplacementChar = " {- \uFFFD -}"
    toFail(Grammar.whsp(_), utfReplacementChar.getBytes("UTF-8"), 3)
  }

  test("quoted multiline string ends with newline") {
    check(Grammar.complete_expression(_),
      """''
        |  a
        |  ''""".stripMargin, Expression(TextLiteral(List(), "a\n")))

    check(Grammar.complete_expression(_),
      """''
        |  b
        |''""".stripMargin, Expression(TextLiteral(List(), "  b\n")))

    check(Grammar.complete_expression(_),
      """''
        |c
        |''""".stripMargin, Expression(TextLiteral(List(), "c\n")))
  }

  test("empty url path") {
    Seq(
      "http://example.com",
      "https://example.com",
      "http://example.com/",
    ).foreach { urlString =>
      val expr1a = Parser.parseDhall(urlString).get.value.value.scheme.asInstanceOf[Import[Expression]]
      val expr1b = CBOR.bytesToExpr(CBOR.exprToBytes(expr1a)).scheme.asInstanceOf[Import[Expression]]
      expect(expr1a == expr1b)
    }
  }

  test("posix env var names") {
    Seq(
      "\\\"",
      "\\\\",
      "\\a",
      "\\b",
      "\\f",
      "\\n",
      "\\r",
      "\\t",
      "\\v",
      "!",
      "<",
      "[",
      "~",
    ).foreach { input =>
      val envVarName = Character.toString(Grammar.mapPosixEnvCharacter(if (input.length == 2) input.drop(1) else input))
      check(Grammar.env(_), s"env:\"$input\"", Env(envVarName))
      check(Grammar.posix_environment_variable_character(_), input, envVarName.head)
      check(Grammar.posix_environment_variable(_), input, envVarName)
    }
  }

  test("leading delimiter in union type") {
    val expected = Expression(UnionType(List((ConstructorName("Foo"), Some(ExprBuiltin(Natural))))))
    check(Grammar.primitive_expression(_), "< Foo : Natural >", expected)
    check(Grammar.primitive_expression(_), "<  Foo : Natural  | >", expected)
    check(Grammar.primitive_expression(_), "< | Foo : Natural >", expected)
  }

  test("records have sorted fields in CBOR") {
    val testFileA = getClass.getClassLoader.getResourceAsStream("tests/parser/success/leadingSeparatorsA.dhall")
    val expr = Parser.parseDhall(testFileA).get.value.value.scheme
    val testFileB = getClass.getClassLoader.getResourceAsStream("tests/parser/success/leadingSeparatorsB.dhallb")
    val cborModelFromExampleFile = CBORmodel.fromCbor(CBORObject.Read(testFileB))
    val cborModelAfterRoundtrip = CBORmodel.fromCbor(CBORObject.DecodeFromBytes(CBOR.exprToBytes(expr)))
    expect(cborModelFromExampleFile.toString == cborModelAfterRoundtrip.toString)
    val exprFromExampleFile = cborModelFromExampleFile.toScheme
    expect(exprFromExampleFile == expr)
  }

  test("application to an import") {
    val expected = Expression(Application(ExprBuiltin(SyntaxConstants.Builtin.List), Import(Path(Here, File(List("file"))), Code, None)))
    check(Grammar.application_expression(_), "List ./file", expected)
  }

  test("import with a long file path") { // TODO report issue: parser tests should exercise various characters that are allowed or disallowed in import paths
    val input = "./path0/path1/path2/file"
    check(Grammar.import_expression(_), input, Expression(Import(Path(Here, File(List("path0", "path1", "path2", "file"))), Code, None)))
  }
}
