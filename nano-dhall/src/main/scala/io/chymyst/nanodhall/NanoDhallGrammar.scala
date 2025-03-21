package io.chymyst.nanodhall

import fastparse.NoWhitespace._
import fastparse._
import io.chymyst.fastparse.Memoize.MemoizeParser
import io.chymyst.nanodhall.VarName.underscore

final case class NanoDhallGrammar[R <: NanoExpr[R]](create: NanoExpr[R]) {

  def ALPHA[$: P] = P(
    CharIn("\u0041-\u005A", "\u0061-\u007A") //  A_Z | a_z
  )

  def DIGIT[$: P] = P(
    CharIn("0-9")
    //  0_9
  )

  def end_of_line[$: P] = P("\n" | "\r\n")

  def tab[$: P] = P("\t")

  def whitespace_chunk[$: P] = P(
    " "
      | tab
      | end_of_line
  ).memoize

  def whsp[$: P]: P[Unit] = P(NoCut(whitespace_chunk.rep))

  def whsp1[$: P]: P[Unit] = P(NoCut(whitespace_chunk.rep(1)))

  def ALPHANUM[$: P] = P(ALPHA | DIGIT)

  def simple_label_first_char[$: P] = P(ALPHA | "_").memoize

  def simple_label_next_char[$: P] = P(ALPHANUM | "-" | "/" | "_").memoize

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

  def quoted_label[$: P] = P(quoted_label_char.rep).!

  // Note: identifiers in backquotes may contain arbitrary text, including the name of a Dhall keyword.
  // Example: "let `in` = 1 in `in`" evaluates to "1".
  // A successfully parsed `label` is guaranteed to be either quoted or not a keyword.
  def label[$: P]: P[String] = P(("`" ~ quoted_label ~ "`") | simple_label)

  // A successfully parsed `nonreserved_label` is guaranteed to be either quoted or not a builtin.
  def nonreserved_label[$: P] = P((builtin ~ simple_label_next_char.rep(1)).! | (!builtin ~ label)).map(VarName.apply)

  def any_label[$: P]: P[String] = P(label)

  def forall_symbol[$: P] = P(
    "\u2200" // Unicode FOR ALL
  )

  def forall[$: P] = P(forall_symbol | requireKeyword("forall"))

  private def concatKeywords[$: P](keywords: Seq[String]): P[String] = {
    keywords
      .sorted(Ordering[String].reverse) // Reversing the order will disambiguate parsing of keywords that are substrings of another keyword.
      .map { k => implicit ctx: P[_] =>
        P(k)
      }.reduce { (p1, p2) => implicit ctx: P[_] =>
        P(p1(ctx) | p2(ctx))
      }(implicitly[P[$]]).!
  }
  //    .memoize  // Do not memoize: breaks parsing!

  def keyword[$: P]: P[String] = concatKeywords(simpleKeywords).memoize

  def builtin[$: P]: P[R] = {
    (concatKeywords(constantSymbolNames).map(NanoConstant.withName).map(create.Constant))
  }.memoize

  // Helpers to make sure we are using valid keyword and operator names.
  def requireKeyword[$: P](name: String): P[Unit] = {
    assert(simpleKeywordsSet contains name, s"Keyword $name must be one of the supported Dhall keywords")
    P(name)
  }

  val constantSymbolNames = NanoConstant.namesToValuesMap.keySet.toIndexedSeq

  val simpleKeywords = Seq("let", "in", "forall")

  val simpleKeywordsSet = simpleKeywords.toSet

  def lambda[$: P] = P("\u03BB" | "\\")

  def arrow[$: P] = P("\u2192" | "->")

  def identifier[$: P]: P[R] = P(variable | builtin)

  /*
    If the identifier matches one of the names in the `builtin` rule, then it is a
    builtin, and should be treated as the corresponding item in the list of
    "Reserved identifiers for builtins" specified in the `standard/README.md` document.
    It is a syntax error to specify a de Bruijn index in this case.
    Otherwise, this is a variable with name and index matching the label and index.

    This is guaranteed because `nonreserved_label` does not match any keyword or builtin, and we match builtins separately without a de Bruijn index.
   */
  def variable[$: P]: P[R] = P(nonreserved_label ~ (whsp ~ "@" ~/ whsp ~ natural_literal).?).map { case (name, index) =>
    create.Variable(name, index.map(_.value).getOrElse(BigInt(0)))
  }

  def expression_lambda[$: P]: P[R] = P(
    lambda ~ whsp ~/ "(" ~ whsp ~/ nonreserved_label ~ whsp ~ ":" ~ whsp1 ~/ expression ~ whsp ~ ")" ~ whsp ~/ arrow ~/
      whsp ~ expression
  ).map { case (name, tipe, body) => create.Lambda(name, tipe, body) }

  def expression_let_binding[$: P]: P[R] = P(let_binding.rep(1) ~ requireKeyword("in") ~ whsp1 ~/ expression).map { case (letBindings, expr) =>
    letBindings.foldRight(expr) { case ((varName, body), prev) => create.Let(varName, body, prev) }
  }

  def expression_forall[$: P]: P[R] = P(
    forall ~ whsp ~/ "(" ~ whsp ~ nonreserved_label ~ whsp ~/ ":" ~ whsp1 ~/ expression ~ whsp ~ ")" ~ whsp ~ arrow ~/
      whsp ~ expression
  ).map { case (varName, tipe, body) => create.Forall(varName, tipe, body) }

  // (`A → B` is short-hand for `∀(_ : A) → B`)
  def expression_arrow[$: P]: P[R] = P(operator_expression ~ whsp ~ arrow ~/ whsp ~ expression).map { case (head, body) =>
    create.Forall(underscore, head, body)
  }

  def expression[$: P]: P[R] = P(
    //  "\(x : a) -> b"
    expression_lambda./
    //
    //  "let x     = e1 in e2"
    //  We allow dropping the `in` between adjacent let_expressions; the following are equivalent:
    //  "let x = e1 let y = e2 in e3"
    //  "let x = e1 in let y = e2 in e3"
      | expression_let_binding./
      //
      //  "forall (x : a) -> b"
      | expression_forall./
      //
      //  "a -> b"
      //
      //  NOTE: Backtrack if parsing this alternative fails
      | NoCut(expression_arrow)
      //
      //  "x : t"
      | annotated_expression./
  ).memoize

  def annotated_expression[$: P]: P[R] = P(operator_expression ~ (whsp ~ ":" ~ whsp1 ~/ expression).?).map { case (expr, tipe) =>
    tipe match {
      case Some(t) => create.Annotation(expr, t)
      case None    => expr
    }
  }

  def let_binding[$: P] = P( // let a = b in x; no type annotations allowed here.
    requireKeyword("let") ~ whsp1 ~/ nonreserved_label ~ whsp ~ "=" ~ whsp ~/ expression ~ whsp1./
  )

  def application_expression[$: P]: P[R] = P(
    primitive_expression ~ (whsp1 ~ primitive_expression).rep // Do not insert a cut after whsp1 here.
  ).map { case (head, tail) => tail.foldLeft(head)((prev, expr) => create.Application(prev, expr)) }

  def operator_expression[$: P]: P[R] = P(plus_expression)

  def natural_literal[$: P]: P[R with HasBigInt] = P(
    // Decimal; leading 0 digits are not allowed
    (CharIn("1-9") ~ DIGIT.rep).!.map(digits => BigInt(digits, 10))
    // ... except for 0 itself
      | P("0").map(_ => BigInt(0))
  ).map(create.NaturalLiteral)

  private implicit class FoldOpExpression(resultWithExpressionSequence: P[(R, Seq[R])]) {
    def withOperator(op: NanoOperator): P[R] =
      resultWithExpressionSequence.map { case (head, tail) => tail.foldLeft(head)((prev, arg) => create.Operator(prev, op, arg)) }
  }

  def opPlus[$: P] = P("+")

  def plus_expression[$: P]: P[R] = P(application_expression ~ (whsp ~ opPlus ~ whsp1 ~/ application_expression).rep).withOperator(NanoOperator.Plus).memoize

  def complete_expression[$: P] = P(whsp ~ expression ~ whsp).memoize

  def primitive_expression[$: P]: P[R] = P(
    //
    //  "2"
    natural_literal
    //  "x"
    //  "x@2"
      | identifier
      //
      //  "( e )"
      | P("(" ~/ complete_expression ~/ ")")
  ).memoize
}
