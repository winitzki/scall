package io.chymyst.ui.dhall.unit

import io.chymyst.ui.dhall.Grammar.TextLiteralNoInterp
import io.chymyst.ui.dhall.Syntax.{Expression, ExpressionScheme}
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.SyntaxConstants._
import io.chymyst.ui.dhall.{Grammar, SyntaxConstants}
import io.chymyst.ui.dhall.unit.TestUtils.v

import io.chymyst.test.ResourceFiles.enumerateResourceFiles
import io.chymyst.test.Throwables.printThrowable

object TestFixtures {

  val blockComments = Seq( // Examples should not contain trailing whitespace or leading whitespace.
    "{- - }- } -}",
    """{-
      | - }-
      |}  |
      |-}""".stripMargin,
    "{-фыва ç≈Ω⁄€‹›ﬁ° }}-}",
    "{--}",
    "{-{--}-}",
    "{-{--}--}",
  )

  val multilineComments = Seq( // Examples may contain trailing whitespace or leading whitespace.
    """ -- {-
      | {-
      | }- -}
      |""".stripMargin,
    """
      |
      |     -- asl;dkjfalskdjфыва ç≈Ω⁄€‹›ﬁ°flakj
      |
      |     {-
      |
      |     фыва ç≈Ω⁄€‹›ﬁ°
      |
      |     --  -}
      |
      |     """.stripMargin
  )

  val whitespaceComments1 = Seq( // Examples should not contain trailing whitespace or leading whitespace.
    " ",
    "--\n",
    "-- \n",
  )

  val whitespaceCommentsWithLeadingSpace = Seq(
    " -- \n",
    " --{-\n",
  )

  def convertSeq(s: (String, Any)): (String, Expression) = s match {
    case (a, b: Expression) => (a, b)
    case (a, b: ExpressionScheme[_]) => (a, Expression(b.asInstanceOf[ExpressionScheme[Expression]]))
  }

  val identifiers: Seq[(String, Expression)] = Seq(
    "Natural-blahblah" -> v("Natural-blahblah"),
    "Natural/blahblah" -> v("Natural/blahblah"),
    "Natural/show123" -> v("Natural/show123"),
    "abc" -> v("abc"),
    "a-b/c" -> v("a-b/c"),
    "_xyz       @   \t\t\t\n\n           123451234512345123451234512345" -> Variable(VarName("_xyz"), BigInt("123451234512345123451234512345")),
    "Kind" -> ExprConstant(SyntaxConstants.Constant.Kind),
    "Natural/show" -> ExprBuiltin(SyntaxConstants.Builtin.NaturalShow),
    "Natural" -> ExprBuiltin(SyntaxConstants.Builtin.Natural),
  ) map convertSeq

  val identifiersWithBackquote: Seq[(String, Expression)] = (Seq(
    "`abc`" -> v("abc"),
    "` `" -> v(" "),
    "`0%!#${}%^`" -> v("0%!#${}%^"),
  ) ++ (Grammar.simpleKeywords ++ Grammar.builtinSymbolNames).sorted.map { name => s"`$name`" -> v(name) }
    ) map convertSeq
  val primitiveExpressions: Seq[(String, Expression)] = Seq(
    "12345" -> NaturalLiteral(BigInt(12345)),
    "-4312.2" -> DoubleLiteral(-4312.2),
    "\"123\"" -> TextLiteral.ofText[Expression](TextLiteralNoInterp("123")),
    """''
      |line
      |''""".stripMargin -> TextLiteral.ofText[Expression](TextLiteralNoInterp("line\n")),
    "x" -> v("x"),
    "(x)" -> v("x"),
    "( x )" -> v("x"),
    "( -12345  )" -> IntegerLiteral(BigInt(-12345)),
    "a-b/c" -> v("a-b/c"),
    "_xyz       @   \t\t\t\n\n           123451234512345123451234512345" -> Variable(VarName("_xyz"), BigInt("123451234512345123451234512345")),
    "[1,2,3]" -> NonEmptyList[Expression](Seq(1, 2, 3).map(x => NaturalLiteral(x))),
    "Kind" -> ExprConstant(SyntaxConstants.Constant.Kind),
    "Natural/show" -> ExprBuiltin(SyntaxConstants.Builtin.NaturalShow),
    "Natural" -> ExprBuiltin(SyntaxConstants.Builtin.Natural),
    "{foo: Natural, bar: Type}" -> RecordType[Expression](Seq(
      (FieldName("bar"), ExprConstant(SyntaxConstants.Constant.Type)),
      (FieldName("foo"), ExprBuiltin(SyntaxConstants.Builtin.Natural)),
    )),
    "{ foo = 1, bar = 2 }" -> RecordLiteral[Expression](Seq(
      (FieldName("bar"), NaturalLiteral(2)),
      (FieldName("foo"), NaturalLiteral(1)),
    )),
    "< Foo : Integer | Bar : Bool >" -> UnionType[Expression](Seq(
      (ConstructorName("Foo"), Some(ExprBuiltin(SyntaxConstants.Builtin.Integer))),
      (ConstructorName("Bar"), Some(ExprBuiltin(SyntaxConstants.Builtin.Bool)))),
    ).sorted,
    "< Foo | Bar : Bool >" -> UnionType[Expression](List((ConstructorName("Foo"), None), (ConstructorName("Bar"), Some(ExprBuiltin(SyntaxConstants.Builtin.Bool))))).sorted,
  ) map convertSeq

  val selectorExpressions: Map[String, Expression] = Map(
    "x.y" -> Field[Expression](v("x"), FieldName("y")),
    "x . y . z" -> Field[Expression](Field[Expression](v("x"), FieldName("y")), FieldName("z")),
    "x .y . (Natural)" -> ProjectByType[Expression](Field[Expression](v("x"), FieldName("y")), ExprBuiltin(SyntaxConstants.Builtin.Natural)),
    "x. {y,z }" -> ProjectByLabels[Expression](v("x"), Seq(FieldName("y"), FieldName("z"))),
  ) map convertSeq

  val sha256example = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
  val sha256lc = "16173e984d35ee3ffd8b6b79167df89480e67d1cd03ea5d0fc93689e4d928e61"

  val completionExpressions = Seq(
    "x .y .( Natural) ::x .{y ,z}" -> Completion[Expression](selectorExpressions("x .y . (Natural)"),
      selectorExpressions("x. {y,z }")),
  ) map convertSeq

  val importExpressions: Seq[(String, Expression)] = Seq(
    s"./a.dhall sha256:$sha256example" -> Import[Expression](ImportType.Path(FilePrefix.Here, File(Seq("a.dhall"))), ImportMode.Code, Some(BytesLiteral(sha256example))),
    s"./a.dhall sha256:$sha256lc" -> Import[Expression](ImportType.Path(FilePrefix.Here, File(Seq("a.dhall"))), ImportMode.Code, Some(BytesLiteral(sha256lc.toUpperCase))),
    "./local/import as Location" -> Import[Expression](ImportType.Path(FilePrefix.Here, File(Seq("local", "import"))), ImportMode.Location, None),
    s"./local/import sha256:$sha256example" -> Import[Expression](ImportType.Path(FilePrefix.Here, File(Seq("local", "import"))), ImportMode.Code, Some
    (BytesLiteral(sha256example))),
    s"./local/import.dhall sha256:$sha256example as Text" -> Import[Expression](ImportType.Path(FilePrefix.Here, File(Seq("local", "import.dhall"))), ImportMode.RawText,
      Some(BytesLiteral(sha256example))),
    s"./local/import sha256:$sha256example as Bytes" -> Import[Expression](ImportType.Path(FilePrefix.Here, File(Seq("local", "import"))), ImportMode
      .RawBytes, Some(BytesLiteral(sha256example))),
    "env:HOME as Text" -> Import[Expression](ImportType.Env("HOME"), ImportMode.RawText, None),
    s"https://example.com/a/b?c=d using headers123 sha256:$sha256example as Bytes" -> Import[Expression](ImportType.Remote(URL(Scheme.HTTPS, "example.com", File
    (Seq("a", "b")), Some("c=d")), Some(v("headers123"))), ImportMode.RawBytes, Some(BytesLiteral(sha256example))),
  ) map convertSeq

  val plusExpressions: Seq[(String, Expression)] = Seq(
    "1 + 1" -> ExprOperator[Expression](NaturalLiteral(1), SyntaxConstants.Operator.Plus, NaturalLiteral(1)),
    "10 + +10" -> ExprOperator[Expression](NaturalLiteral(10), SyntaxConstants.Operator.Plus, IntegerLiteral(10)),
    "10 + -10" -> ExprOperator[Expression](NaturalLiteral(10), SyntaxConstants.Operator.Plus, IntegerLiteral(-10)),
    "10 +10" -> Application[Expression](NaturalLiteral(10), IntegerLiteral(10)),
    "10 ++10" -> ExprOperator[Expression](NaturalLiteral(10), SyntaxConstants.Operator.TextAppend, NaturalLiteral(10)),
    "1.0 + 2.0" -> ExprOperator[Expression](DoubleLiteral(1.0), SyntaxConstants.Operator.Plus, DoubleLiteral(2.0)),
    "1.0 -2.0" -> Application[Expression](DoubleLiteral(1.0), DoubleLiteral(-2.0)),
    "1 ++ [1,2,3]" -> ExprOperator[Expression](NaturalLiteral(1), SyntaxConstants.Operator.TextAppend, primitiveExpressions.toMap.apply("[1,2,3]")),
  ) map convertSeq

  val recordExpressions: Seq[(String, Expression)] = Seq(
    "{ foo, bar }" -> RecordLiteral[Expression](List(
      (FieldName("bar"), v("bar")),
      (FieldName("foo"), v("foo")),
    )),
  ) map convertSeq

  // Note: a `let_binding` must end with a whitespace.
  val letBindings: Seq[(String, (VarName, Option[Expression], Expression))] = Seq(
    "let x = 1 " -> (VarName("x"), None, NaturalLiteral(1)),
    "let x : Integer = 1 " -> (VarName("x"), Some(ExprBuiltin(SyntaxConstants.Builtin.Integer)), NaturalLiteral(1)),
  ).map { case (k, (v, p, n)) => (k, (v, p.map(Expression.apply), Expression(n))) }

  val letBindingExpressions: Seq[(String, Expression)] = Seq(
    "let x = 1 in y" -> Let[Expression](VarName("x"), None, NaturalLiteral(1), v("y")),
    "let x = 1 let y = 2 in z" -> Let[Expression](VarName("x"), None, NaturalLiteral(1), Let[Expression](VarName("y"), None, NaturalLiteral(2), v("z"))),
    "let x = 1 in let y = 2 in z" -> Let[Expression](VarName("x"), None, NaturalLiteral(1), Let[Expression](VarName("y"), None, NaturalLiteral(2), v("z"))),
    "let `in` = 1 in `let`" -> Let[Expression](VarName("in"), None, NaturalLiteral(1), v("let")),
  ) map convertSeq

  val interpolationExpressions: Seq[(String, Expression)] =
    Seq(
      "${x}" -> v("x"),
      "${1}" -> NaturalLiteral(1),
      "${\"x\"}" -> TextLiteral(List(), "x"),
    ) map convertSeq

  val doubleQuotedExpressions: Seq[(String, TextLiteral[Expression])] =
    Seq(
      "\"\"" -> TextLiteral[Expression](List(
      ), ""),
      "\"x\"" -> TextLiteral[Expression](List(
      ), "x"),
      "\"${x}\"" -> TextLiteral(List(
        ("", v("x")),
      ), ""),
      "\"a${x}\"" -> TextLiteral(List(
        ("a", v("x")),
      ), ""),
      "\"${x}b\"" -> TextLiteral(List(
        ("", v("x")),
      ), "b"),
      "\"a${x}b\"" -> TextLiteral(List(
        ("a", v("x")),
      ), "b"),
      "\"${x}a${y}b\"" -> TextLiteral(List(
        ("", v("x")),
        ("a", v("y")),
      ), "b"),
    )

  val singleQuotedExpressions: Seq[(String, TextLiteral[Expression])] = doubleQuotedExpressions.map { case (s, expr) => (s.replaceFirst("\"",
    "''\n").replace("\"", "''"), expr)
  }

  val timeLiterals: Seq[String] = Seq(
    "00:09:59",
    "10:00:00.010",
    "10:00:00.1111111",
    "10:00:00.111111111",
    "10:00:00.000001111",
    "10:00:00.011110000",
    "10:00:00.111110000",
    "10:00:00.000001111111",
    "10:00:01.000001111111",
    "10:00:11.000001111111",
    "10:00:11.0000011111110",
    "00:19:59.0",
    "00:19:59.1",
    "00:19:09.0",
    "00:19:09.1",
    "00:19:09.01",
    "00:19:09.10",
    "00:19:09.010",
    "00:19:09.011",
    "00:19:09.0100",
    "00:19:09.0110",
    "00:59:00.000000000",
    "00:59:00.00000000000000000000000000",
    "00:59:00.0000000000000000000000000012345678900",
  )
}
