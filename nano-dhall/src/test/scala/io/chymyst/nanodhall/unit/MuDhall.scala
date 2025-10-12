package io.chymyst.nanodhall.unit

import com.eed3si9n.expecty.Expecty.expect
import izumi.reflect.{Tag, TagKK}

import scala.annotation.tailrec
import scala.util.Try

object MuDhall extends App {

  /* Expressions */

  import enumeratum.{Enum, EnumEntry}

  import scala.language.implicitConversions
  // Define the set of built-in symbols supported in µDhall.

  sealed abstract class Constant(override val entryName: String) extends EnumEntry {}

  object Constant extends Enum[Constant] {
    override def values = findValues

    case object Natural extends Constant("Natural")

    case object NaturalFold extends Constant("Natural/fold")

    case object NaturalSubtract extends Constant("Natural/subtract")

    case object Kind extends Constant("Kind")

    case object Type extends Constant("Type")
  }

  import Constant._

  // Define the set of built-in binary operators supported in µDhall.

  sealed abstract class Operator(val name: String) extends EnumEntry

  object Operator extends Enum[Operator] {
    val values = findValues

    // These operators work only with values of type Natural.
    case object Plus extends Operator("+")

    case object Times extends Operator("*")
  }

  sealed trait Expr

  object Expr {
    // Natural literals, for example 123
    final case class NaturalLiteral(value: Int) extends Expr {
      require(value >= 0)
    }

    // Variables with their de Bruijn indices.
    final case class Variable(name: String, index: Int = 0) extends Expr {
      require(index >= 0)
    }

    // λ(name : tipe) → body  -- Function literal value.
    final case class Lambda(name: String, tipe: Expr, body: Expr) extends Expr

    // ∀(name : tipe) → body  -- Function type.
    final case class Forall(name: String, tipe: Expr, body: Expr) extends Expr

    // let name = subst in body  -- Locally scoped variable definition.
    final case class Let(name: String, subst: Expr, body: Expr) extends Expr

    // body : tipe   -- Expression that is annotated with a type.
    final case class Annotated(body: Expr, tipe: Expr) extends Expr

    // func arg   -- Application of a function to an argument.
    final case class Applied(func: Expr, arg: Expr) extends Expr

    // Built-in constant symbols such as "Natural" or "Type".
    final case class Builtin(constant: Constant) extends Expr

    // Binary operations such as "n + 123".
    final case class BinaryOp(left: Expr, op: Operator, right: Expr) extends Expr
  }

  implicit class ExprMap(e: Expr) {
    def map(f: Expr => Expr): Expr = e match {
      case Expr.NaturalLiteral(_) | Expr.Builtin(_) | Expr.Variable(_, _) => e
      case Expr.Lambda(name, tipe, body)                                  => Expr.Lambda(name, f(tipe), f(body))
      case Expr.Forall(name, tipe, body)                                  => Expr.Forall(name, f(tipe), f(body))
      case Expr.Let(name, subst, body)                                    => Expr.Let(name, f(subst), f(body))
      case Expr.Annotated(body, tipe)                                     => Expr.Annotated(f(body), f(tipe))
      case Expr.Applied(func, arg)                                        => Expr.Applied(f(func), f(arg))
      case Expr.BinaryOp(left, op, right)                                 => Expr.BinaryOp(f(left), op, f(right))
    }
  }

  object DSL { // Helper methods for creating µDhall values more easily in Scala.

    import Expr._
    import Operator._

    implicit class IntroduceVar(name: String) {
      def ! : Variable = Variable(name)

      def !!(index: Int): Variable = Variable(name, index)
    }

    implicit class IntroduceNatural(n: Int) {
      def ! : NaturalLiteral = NaturalLiteral(n)
    }

    implicit class IntroduceSymbol(c: Constant) {
      def ! : Expr = Builtin(c)
    }

    implicit class NaturalOps(e: Expr) {
      def +(other: Expr): Expr = BinaryOp(e, Plus, other)

      def *(other: Expr): Expr = BinaryOp(e, Times, other)
    }

    implicit class ExprAnnotate(e: Expr) {
      def :~(tipe: Expr): Expr = Annotated(e, tipe)

      def apply(arg: Expr): Expr = Applied(e, arg)

      // Instead of "let x = e in body" we write body.let(x, e)
      def let(arg: String, subst: Expr): Expr = Let(arg, subst, e)
    }

    implicit class ExprFunc(x: Expr) {
      // Instead of "λ(name : tipe) → body" we write name.! :~ tipe ~> body
      def ~>(body: Expr): Expr = x match {
        case Annotated(Variable(v, 0), tipe) => Lambda(v, tipe, body)
        case _                               => throw new Exception(s"Invalid Lambda: argument must be an Annotated name but instead got $x")
      }

      // Instead of "∀(name : tipe) → body" we write name.! :~ tipe :~> body
      def :~>(body: Expr): Expr = x match {
        case Annotated(Variable(v, 0), tipe) => Forall(v, tipe, body)
        case _                               => throw new Exception(s"Invalid Forall: argument must be an Annotated name but instead got $x")
      }
    }
  }

  import DSL._

  object Test1 {
    // A simple test.

    val test0 = 1.! + 2.! + 3.!
    val test1 = "n".! + 123.! :~ Natural.!
    val test2 = ("n".! :~ Natural.!) ~> ("n".! + 1.!)
    val test3 = ("n".! :~ Natural.!) :~> Natural.!
    val test4 = test2 :~ test3
    val test5 = test2(test1)
    val test6 = "f".!("g".!("x".!))
  }

  /*
   The pretty-printer works by computing the "inner" and "outer" binding precedence of each expression.

   - Parentheses are required whenever the outer precedence is below the inner precedence.

   Some examples:

   a   *   b   +   c                 Plus( Times (a, b), c )
      10      20

   (a   +   b)   *   f     (g   c)           Times ( Plus (a, b), Applied(f, Applied(g, c) ) )
       20       10     5,4    5

   f   a     (b   +   c)   +   d       Plus( Applied ( Applied (f, a), Plus (b, c) ), d )
     5   5,4     20       20

   λ(a : Natural) → λ(b : Natural)  →  f   a   +   b    Lambda ( a, Natural, Lambda(b, Natural, Plus (Applied(f, a), b) ) )
       8          3     8           3    5    20

   (λ(a : Natural)  →  a)   b         Applied (Lambda (a, Natural, a), b)
        8           3     5

   - Each of the Expr constructors has an overall outer precedence and a separate inner precedence for each Expr argument.

   - Precedence values must be specified separately for each constructor and each argument.

   Applied(f, a) has outer precedence 5 and inner precedence

   - Binary operations have equal outer and inner precedence values. This is the "precedence of the operation".

   - Other constructors sometimes have unequal outer and inner precedence values.
   */

  def precedence(op: Operator): Int = op match {
    case Operator.Plus  => 20
    case Operator.Times => 10
  }

  // Return (outer, List(inner1, inner2, ...)) for each constructor that may have Expr arguments.
  def precedence(e: Expr): (Int, List[Int]) = e match {
    case Expr.NaturalLiteral(_) | Expr.Variable(_, _) | Expr.Builtin(_) => (0, List())      // Never need parentheses.
    case Expr.Lambda(name, tipe, body)                                  => (50, List(50, 50))
    case Expr.Forall(name, tipe, body)                                  => (50, List(50, 50))
    case Expr.Let(name, subst, body)                                    => (50, List(50, 50))
    case Expr.Annotated(body, tipe)                                     => (8, List(7, 60)) //   ( 1 : Natural ) : Natural : Type
    case Expr.Applied(func, arg)                                        => (5, List(4, 4))  //   f (g x)
    case Expr.BinaryOp(_, op, _)                                        =>
      val prec = precedence(op)
      (prec, List(prec, prec)) // For binary operators, all 3 precedence priorities are equal.
  }

  def inPrecedence(expr: String, innerPrec: Int, outerPrec: Int): String =
    if (innerPrec > outerPrec) s"($expr)" else expr

  def prettyprint(e: Expr, outside: Int = 100): String = {
    val (outer, inner) = precedence(e)

    def printpair(prefix: String, left: Expr, middle: String, right: Expr): String =
      prefix + prettyprint(left, inner(0)) + middle + prettyprint(right, inner(1))

    val exprPrinted = e match {
      case Expr.NaturalLiteral(value)     => value.toString
      case Expr.Variable(name, index)     => name + (if (index != 0) s"@$index" else "")
      case Expr.Builtin(constant)         => constant.entryName
      case Expr.Lambda(name, tipe, body)  => printpair(s"λ($name : ", tipe, ") → ", body)
      case Expr.Forall(name, tipe, body)  => printpair(s"∀($name : ", tipe, ") → ", body)
      case Expr.Let(name, subst, body)    => printpair(s"let $name = ", subst, " in ", body)
      case Expr.Annotated(body, tipe)     => printpair("", body, " : ", tipe)
      case Expr.Applied(func, arg)        => printpair("", func, " ", arg)
      case Expr.BinaryOp(left, op, right) => printpair("", left, " " + op.name + " ", right)
    }

    inPrecedence(exprPrinted, outer, outside)
  }

  // Helper function for tests.
  implicit class BatchTest[A, B](fixtures: Seq[(A, B)]) {
    def validate(f: A => B) = fixtures.zipWithIndex.foreach { case ((expr, expected), i) => expect(i >= 0 && f(expr) == expected) }
  }

  // Test the pretty-printer.
  Seq(
    (Test1.test0 -> "1 + 2 + 3"),
    (Test1.test1 -> "(n + 123) : Natural"),
    (Test1.test2 -> "λ(n : Natural) → n + 1"),
    (Test1.test3 -> "∀(n : Natural) → Natural"),
    (Test1.test4 -> "(λ(n : Natural) → n + 1) : ∀(n : Natural) → Natural"),
    (Test1.test5 -> "(λ(n : Natural) → n + 1) ((n + 123) : Natural)"),
    (Test1.test6 -> "f (g x)"),
  ).validate(prettyprint(_))

  println("Tests passed for prettyprint().")

  /* De Bruijn indices. */

  def shift(d: Int, x: String, m: Int, target: Expr): Expr = target match {
    case Expr.Variable(name, index) if name == x && index >= m => Expr.Variable(name, index + d) // Shifted.
    case Expr.Lambda(name, tipe, body) if name == x            => Expr.Lambda(name, shift(d, x, m, tipe), shift(d, x, m + 1, body))
    case Expr.Forall(name, tipe, body) if name == x            => Expr.Forall(name, shift(d, x, m, tipe), shift(d, x, m + 1, body))
    case Expr.Let(name, subst, body) if name == x              => Expr.Let(name, shift(d, x, m, subst), shift(d, x, m + 1, body))
    case _                                                     => target.map(shift(d, x, m, _))
  }

  val f1 = ("n".! :~ Natural.!) ~> ("n".! + 1.!)
  expect(prettyprint(f1) == "λ(n : Natural) → n + 1")

  val f2 = ("n".! :~ Natural.!) ~> ("p".! + 1.!)
  expect(prettyprint(f2) == "λ(n : Natural) → p + 1")

  Seq(
    shift(1, "x", 0, "x".!)       -> "x".!!(1),
    shift(-1, "x", 0, "x".!!(1))  -> "x".!,
    shift(1, "x", 0, "x".! + 1.!) -> ("x".!!(1) + 1.!),
    shift(1, "x", 1, "x".!)       -> "x".!,
    shift(1, "x", 0, "y".!)       -> "y".!,
    shift(1, "n", 0, f1)          -> f1,
    shift(1, "n", 0, f2)          -> f2,
    shift(1, "p", 0, f1)          -> f1,
    shift(1, "p", 0, f2)          -> ("n".! :~ Natural.!) ~> ("p".!!(1) + 1.!),
  ).zipWithIndex.foreach { case ((e, expected), i) => expect(i >= 0 && e == expected) }

  println("Tests passed for shift().")

  def sub(v: Expr.Variable, s: Expr, target: Expr): Expr = {
    def getBodyAfterSubst(name: String, body: Expr): Expr = {
      val v1 = if (name == v.name) Expr.Variable(v.name, v.index + 1) else v
      val e1 = shift(1, name, 0, s)
      sub(v1, e1, body)
    }

    target match {
      case Expr.Variable(name, index) if name == v.name && index == v.index => s // Substituted.
      case Expr.Lambda(name, tipe, body)                                    => Expr.Lambda(name, sub(v, s, tipe), getBodyAfterSubst(name, body))
      case Expr.Forall(name, tipe, body)                                    => Expr.Forall(name, sub(v, s, tipe), getBodyAfterSubst(name, body))
      case Expr.Let(name, subst, body)                                      => Expr.Let(name, sub(v, s, subst), getBodyAfterSubst(name, body))
      case _                                                                => target.map(sub(v, s, _))
    }
  }

  Seq(
    // Substitution has no effect on expressions without free variables.
    sub("x".!, 123.!, ("x".! :~ Natural.!) ~> ("x".! + 100.!))
      -> (("x".! :~ Natural.!) ~> ("x".! + 100.!)),

    // Substitution will replace free variables.
    sub("x".!, 123.!, ("y".! :~ Natural.!) ~> ("x".! + 100.!))
      -> (("y".! :~ Natural.!) ~> (123.! + 100.!)),

    // A variable can be still be free if its de Bruijn index is large enough.
    sub("x".!, 123.!, ("x".! :~ Natural.!) ~> ("x" !! 1))
      -> (("x".! :~ Natural.!) ~> 123.!),

    // Descending past a matching bound variable increments the index to substitute.
    sub("x" !! 1, 123.!, ("x".! :~ Natural.!) ~> ("x" !! 2))
      -> (("x".! :~ Natural.!) ~> 123.!),

    // Substitution must avoid creating a name clash ("variable capture").
    sub("y".!, "x".!, ("x".! :~ Natural.!) ~> "y".!)
      -> (("x".! :~ Natural.!) ~> ("x" !! 1)),
  ).zipWithIndex.foreach { case ((e, expected), i) => expect(i >= 0 && e == expected) }

  println("Tests passed for sub().")

  /* Parsing. */

  import fastparse.NoWhitespace._
  import fastparse._

  object Grammar {

    // end-of-line = %x0A / %x0D.0A
    def end_of_line[_: P] = P("\n" | "\r\n")

    // valid-non-ascii = %x80-10FFFD
    def valid_non_ascii[_: P]: P[Unit] = P(
      CharIn("\u0080-\uD7FF") | // U+0080 to U+D7FF (excludes surrogates)
        CharIn("\uE000-\uFFFD") // U+E000 to U+FFFD
    )

    // tab = %x09
    def tab[_: P] = P("\t")

    // not-end-of-line = %x20-7F / valid-non-ascii / tab
    def not_end_of_line[_: P] = P(CharIn("\u0020-\u007F") | valid_non_ascii | tab)

    // line-comment = "--" *not-end-of-line end-of-line
    def line_comment[_: P] = P("--" ~ not_end_of_line.rep ~ end_of_line)

    // whitespace-chunk = " " / tab / end-of-line / line-comment
    def whitespace_chunk[_: P] = P(" " | tab | end_of_line | line_comment)

    // whsp = *whitespace-chunk
    def whsp[_: P] = P(whitespace_chunk.rep)

    // whsp1 = 1*whitespace-chunk
    def whsp1[_: P] = P(whitespace_chunk.rep(1))

    // ALPHA = %x41-5A / %x61-7A
    def ALPHA[_: P] = P(CharIn("A-Z", "a-z"))

    // DIGIT = %x30-39
    def DIGIT[_: P] = P(CharIn("0-9"))

    // ALPHANUM = ALPHA / DIGIT
    def ALPHANUM[_: P] = P(ALPHA | DIGIT)

    // label-first-char = ALPHA / "_"
    def label_first_char[_: P] = P(ALPHA | "_")

    // label-next-char = ALPHANUM / "-" / "/" / "_"
    // NOTE: CharIn("-") cannot be used in fastparse!
    def label_next_char[_: P] = P(ALPHANUM | "-" | "/" | "_")

    // label = label-first-char *label-next-char
    // label =
    //   keyword 1*label-next-char
    //   / !keyword (label-first-char *label-next-char)

    def label[_: P] = P((keyword ~ label_next_char.rep(1)) | (!keyword ~ label_first_char ~ label_next_char.rep))

    // nonreserved-label =]
    //      builtin 1*label-next-char
    //    / !builtin label

    def nonreserved_label[_: P]: P[String] = P((builtin ~ label_next_char.rep(1)) | (!builtin ~ label)).!

    // let = "let"
    def let[_: P] = P("let")

    // in = "in"
    def in[_: P] = P("in")

    // forall-keyword = "forall"
    def forall_keyword[_: P] = P("forall")

    // forall-symbol = %x2200 ; Unicode FOR ALL: ∀
    def forall_symbol[_: P] = P("∀")

    // forall = forall-symbol / forall-keyword
    def forall[_: P] = P(forall_symbol | forall_keyword)

    // keyword = let / in / forall-keyword
    def keyword[_: P] = P(let | in | forall_keyword)

    // Builtin constants.
    def Natural[_: P] = P("Natural")

    def Natural_fold[_: P] = P("Natural/fold")

    def Natural_subtract[_: P] = P("Natural/subtract")

    def Type[_: P] = P("Type")

    def Kind[_: P] = P("Kind")

    // builtin = Natural / Natural-fold / Natural-subtract / Type / Kind
    // Need to reverse the order to disambiguate parsing of symbols that are substrings of each other.
    def builtin[_: P] = P(Natural_fold | Natural_subtract | Natural | Type | Kind).!.map(s => Expr.Builtin(Constant.withName(s)))

    // lambda = %x3BB / "\"
    def lambda[_: P] = P("λ" | "\\")

    // arrow = %x2192 / "->"
    def arrow[_: P] = P("→" | "->")

    // plus = "+"
    def plus[_: P] = P("+")

    // times = "*"
    def times[_: P] = P("*")

    // natural-literal = ("1"/..."9") *DIGIT / "0"
    def natural_literal[_: P]: P[Expr.NaturalLiteral] = P(CharIn("1-9") ~ DIGIT.rep | "0").!.map(n => Expr.NaturalLiteral(n.toInt))

    // variable = nonreserved-label [ whsp "@" whsp natural-literal ]
    def variable[_: P]: P[Expr.Variable] = P(nonreserved_label ~ (whsp ~ "@" ~ whsp ~ natural_literal).?).map {
      case (name, Some(i)) => Expr.Variable(name, i.value)
      case (name, None)    => Expr.Variable(name, 0)
    }

    // identifier = variable / builtin
    def identifier[_: P]: P[Expr] = P(variable | builtin)

    // parenthesized-expression = "(" whsp expression whsp ")"
    def parenthesized_expression[_: P] = P("(" ~ whsp ~ expression ~ whsp ~ ")")

    // primitive-expression = natural-literal / identifier / parenthesized-expression
    def primitive_expression[_: P]: P[Expr] = P(natural_literal | identifier | parenthesized_expression)

    // application-expression = primitive-expression *(whsp1 primitive-expression)
    def application_expression[_: P]: P[Expr] =
      P(primitive_expression ~ (whsp1 ~ primitive_expression).rep).map { case (a, tail) => tail.foldLeft(a) { case (prev, arg) => Expr.Applied(prev, arg) } }

    // times-expression = application-expression *(whsp times whsp application-expression)
    def times_expression[_: P]: P[Expr] =
      P(application_expression ~ (whsp ~ times ~ whsp ~ application_expression).rep).map { case (a, tail) =>
        tail.foldLeft(a) { case (prev, arg) => Expr.BinaryOp(prev, Operator.Times, arg) }
      }

    // plus-expression = times-expression *(whsp plus whsp times-expression)
    def plus_expression[_: P]: P[Expr] =
      P(times_expression ~ (whsp ~ plus ~ whsp ~ times_expression).rep).map { case (a, tail) =>
        tail.foldLeft(a) { case (prev, arg) => Expr.BinaryOp(prev, Operator.Plus, arg) }
      }

    // operator-expression = plus-expression
    def operator_expression[_: P]: P[Expr] = P(plus_expression)

    // annotated-expression = operator-expression [ whsp ":" whsp1 expression ]
    def annotated_expression[_: P]: P[Expr] =
      P(operator_expression ~ (whsp ~ ":" ~ whsp1 ~ expression).?).map {
        case (a, Some(b)) => Expr.Annotated(a, b)
        case (a, None)    => a
      }

    // let-binding = let whsp1 nonreserved-label whsp "=" whsp expression whsp1
    def let_binding[_: P]: P[(String, Expr)] =
      P(let ~ whsp1 ~ nonreserved_label ~ whsp ~ "=" ~ whsp ~ expression ~ whsp1)

    def expression[_: P]: P[Expr] = P(lambda_abstraction | let_expression | forall_abstraction | function_type | annotated_expression)

    def let_expression[_: P]: P[Expr] = P(
      // 1*let-binding in whsp1 expression
      let_binding.rep(1) ~ in ~ whsp1 ~ expression
    ).map { case (lets, e) => lets.foldRight(e) { case ((varName, body), prev) => Expr.Let(varName, body, prev) } }

    def function_type[_: P]: P[Expr] = P(
      // a -> b (shorthand for forall (_ : a) -> b)
      operator_expression ~ whsp ~ arrow ~ whsp ~ expression
    ).map { case (a, b) => Expr.Forall("_", a, b) }

    def lambda_abstraction[_: P]: P[Expr] = P(
      // \(x : a) -> b
      lambda ~ whsp ~ "(" ~ whsp ~ nonreserved_label ~ whsp ~ ":" ~ whsp1 ~ expression ~ whsp ~ ")" ~ whsp ~ arrow ~ whsp ~ expression
    ).map { case (name, tipe, body) => Expr.Lambda(name, tipe, body) }

    def forall_abstraction[_: P]: P[Expr] = P(
      // forall (x : a) -> b
      forall ~ whsp ~ "(" ~ whsp ~ nonreserved_label ~ whsp ~ ":" ~ whsp1 ~ expression ~ whsp ~ ")" ~ whsp ~ arrow ~ whsp ~ expression
    ).map { case (name, tipe, body) => Expr.Forall(name, tipe, body) }

    // The main production of the grammar is "expression" that may be surrounded by whitespace.
    def full_expression[_: P]: P[Expr] = P(Start ~ whsp ~ expression ~ whsp ~ End)

    def parse(input: String): Expr = fastparse.parse(input, full_expression(_)).get.value

    def debugParse(input: String): Option[Expr] = {
      println(s"Parsing test string:\n---$input---\n")
      fastparse.parse(input, full_expression(_)) match {
        case Parsed.Success(e, index) =>
          println(s"Parsing succeeded! Reached index: $index (Full length)")
          Some(e)

        case Parsed.Failure(expected, index, extra) =>
          println(s"Parsing failed at index: $index")
          println(s"Expected: '$expected'")
          // Print context of the failure.
          val pre  = input.substring(0, index)
          val post = input.substring(index)
          println(s"Context:\n'${pre}█${post}'")
          println(s"Trace: ${extra.trace().longAggregateMsg}")
          None
      }
    }

  }

  val testString =
    """
    let x = 1 + y@0 * 2 -- A comment
    let z = Natural in
    λ(a : Type) → ∀(b : Type) → a → b → a
    """

  prettyprint(Grammar.debugParse(testString).get)

  implicit class ParseDhall(input: String) {
    def dhall: Expr = Grammar.parse(input)
  }

  "1 + 1".dhall

  implicit class PrintDhall(e: Expr) {
    def print: String = prettyprint(e)
  }

  Seq("(1 + 1) + 1" -> "1 + 1 + 1", "1 + (1 + (1))" -> "1 + 1 + 1").validate(_.dhall.print)

  println("Tests passed for prettyprint() associativity.")

  /* Evaluation. */

  def alphaNormalize(e: Expr): Expr = {
    def getBody(name: String, expr: Expr): Expr = {
      val b1 = shift(1, "_", 0, expr)
      val b2 = sub(name.!, "_".!, b1)
      val b3 = shift(-1, name, 0, b2)
      alphaNormalize(b3)
    }

    e match {
      case Expr.Variable(_, _)                          => e
      case Expr.Lambda(name, tipe, body) if name != "_" => Expr.Lambda("_", alphaNormalize(tipe), getBody(name, body))
      case Expr.Forall(name, tipe, body) if name != "_" => Expr.Forall("_", alphaNormalize(tipe), getBody(name, body))
      case Expr.Let(name, subst, body) if name != "_"   => Expr.Let("_", alphaNormalize(subst), getBody(name, body))
      case _                                            => e.map(alphaNormalize)
    }
  }

  Seq("λ(a : Type) → λ(b : Type) → a" -> "λ(_ : Type) → λ(_ : Type) → _@1", "λ(x : Type) → _" -> "λ(_ : Type) → _@1").validate(e =>
    alphaNormalize(e.dhall).print
  )

  println("Tests passed for alphaNormalize().")

  def equiv(x: Expr, y: Expr): Boolean =
    alphaNormalize(betaNormalize(x)) == alphaNormalize(betaNormalize(y))

  def shiftSubShift(x: String, subst: Expr, body: Expr, normalize: Boolean = true): Expr = {
    val a1 = shift(1, x, 0, subst)
    val b1 = sub(Expr.Variable(x, 0), a1, body)
    val b2 = shift(-1, x, 0, b1)
    if (normalize) betaNormalize(b2) else b2
  }

  def betaNormalize(e: Expr): Expr = {
    import Expr._
    e match {
      case Annotated(e, t)                => betaNormalize(e)
      case Let(name, subst, body)         => shiftSubShift(name, subst, body)
      case Applied(func, arg)             =>
        (betaNormalize(func), betaNormalize(arg)) match {
          case (Lambda(x, tipe, body), _) => shiftSubShift(x, arg, body)

          // f = Natural/subtract a
          case (Applied(Builtin(NaturalSubtract), a), b1)                                         =>
            (betaNormalize(a), b1) match {
              case (NaturalLiteral(0), _)                 => b1
              case (_, NaturalLiteral(0))                 => NaturalLiteral(0)
              case (a1, b1) if equiv(a1, b1)              => NaturalLiteral(0)
              case (NaturalLiteral(m), NaturalLiteral(n)) => NaturalLiteral(math.max(0, n - m))
              case (a1, b1)                               => Applied(Applied(Builtin(NaturalSubtract), a1), b1)
            }

          // f = Natural/fold n B g
          case (Applied(Applied(Applied(Builtin(NaturalFold), NaturalLiteral(n)), bType), g), b1) =>
            if (n == 0) b1
            else betaNormalize(Applied(g, Applied(Applied(Applied(Applied(Builtin(NaturalFold), NaturalLiteral(n - 1)), bType), g), b1)))

          // No other rules can be used:
          case (f1, b1)                                                                           => Applied(f1, b1)
        }
      case BinaryOp(l, Operator.Plus, r)  =>
        (betaNormalize(l), betaNormalize(r)) match {
          case (NaturalLiteral(0), r1)                => r1
          case (l1, NaturalLiteral(0))                => l1
          case (NaturalLiteral(m), NaturalLiteral(n)) => NaturalLiteral(m + n)
          // No other rules can be used:
          case (l1, r1)                               => BinaryOp(l1, Operator.Plus, r1)
        }
      case BinaryOp(l, Operator.Times, r) =>
        (betaNormalize(l), betaNormalize(r)) match {
          case (NaturalLiteral(0), _) | (_, NaturalLiteral(0)) => NaturalLiteral(0)
          case (l1, NaturalLiteral(1))                         => l1
          case (NaturalLiteral(1), r1)                         => r1
          case (NaturalLiteral(m), NaturalLiteral(n))          => NaturalLiteral(m * n)
          // No other rules can be used:
          case (l1, r1)                                        => BinaryOp(l1, Operator.Times, r1)
        }
      // No other rules can be used:
      case _                              => e.map(betaNormalize)
    }
  }

  Seq(
    "Natural/fold 5 Natural (λ(x : Natural) → x + 10) 100" -> "150",
    "1 + x"                                                -> "1 + x",
    "0 + x"                                                -> "x",
    "0 * x"                                                -> "0",
    "x + 0"                                                -> "x",
    "x * 0"                                                -> "0",
    "x * 1"                                                -> "x",
    "1 * x"                                                -> "x",
    "(λ(x : Natural) → x + a) 100"                         -> "100 + a",
    "Natural/subtract 20 30"                               -> "10",
    "Natural/subtract 30 20"                               -> "0",
    "Natural/fold 5 Natural (λ(x : Natural) → x + 10) 100" -> "150",
    "Natural/fold 5 Natural (λ(x : Natural) → x + a) 100"  -> "100 + a + a + a + a + a",
  ).validate(e => betaNormalize(e.dhall).print)

  println("Tests passed for betaNormalize().")

  /* Type-checking. */

  final case class TypeError(gamma: List[(String, Expr)], focus: Expr, target: Expr, message: String) {
    override def toString: String = {
      val printGamma = gamma.map { case (v, t) => v + " : " + t.print }.mkString("[", ", ", "]")
      s"In typing context Γ=$printGamma, while type-checking ${focus.print}, type error with ${target.print}: $message"
    }
  }

  def VFI(e: Expr): Boolean = e match {
    case Expr.Builtin(Type) | Expr.Builtin(Kind) => true
    case _                                       => false
  }

  def typeCheck(gamma: List[(String, Expr)], e: Expr): Either[TypeError, Expr] = {
    import Expr._

    def requireEqual(a: Expr, b: Expr): Either[TypeError, Unit] =
      if (equiv(a, b)) Right()
      else Left(TypeError(gamma, e, a, s"Expression ${a.print} must equal ${b.print}"))

    e match {
      case Variable(name, index) =>
        gamma match {
          case Nil              => Left(TypeError(gamma, e, e, s"Variable $name not in typing context"))
          case ((v, t)) :: tail =>
            if (name != v) typeCheck(tail, e)
            else if (index == 0) Right(t)
            else typeCheck(tail, Variable(name, index - 1))
        }

      case Builtin(Type)     => Right(Kind.!)
      case Builtin(Natural)  => Right(Type.!)
      case NaturalLiteral(_) => Right(Natural.!)

      case Builtin(NaturalFold)     => Right("_".! :~ Natural.! :~> ("N".! :~ Type.! :~> (("_".! :~ ("_".! :~ "N".! :~> "N".!) :~> ("_".! :~ "N".! :~> "N".!)))))
      case Builtin(NaturalSubtract) => Right("_".! :~ Natural.! :~> ("_".! :~ Natural.! :~> Natural.!))

      case BinaryOp(l, op, r) if op == Operator.Plus || op == Operator.Times =>
        for {
          t1 <- typeCheck(gamma, l)
          _  <- requireEqual(t1, Natural.!)
          t2 <- typeCheck(gamma, r)
          _  <- requireEqual(t2, Natural.!)
        } yield Natural.!

      case Annotated(body, tipe) =>
        if (tipe == Kind.!) for {
          t1 <- typeCheck(gamma, body)
          _  <- requireEqual(t1, Kind.!)
        } yield Kind.!
        else
          for {
            _  <- typeCheck(gamma, tipe)
            t1 <- typeCheck(gamma, body)
            _  <- requireEqual(t1, tipe)
          } yield t1

      case Forall(name, tipe, body) =>
        for {
          inputType  <- typeCheck(gamma, tipe)
          gamma1      = ((name, tipe) :: gamma).map { case (v, t) => (v, shift(1, name, 0, t)) }
          outputType <- typeCheck(gamma1, body)
          _          <- if (VFI(inputType)) Right(())
                        else Left(TypeError(gamma, e, tipe, s"A function's input type must have type Type or Kind, but instead is ${inputType.print}"))
        } yield outputType

      case Lambda(name, tipe, body) =>
        for {
          _        <- typeCheck(gamma, tipe)
          a1        = betaNormalize(tipe)
          gamma1    = ((name, a1) :: gamma).map { case (v, t) => (v, shift(1, name, 0, t)) }
          bodyType <- typeCheck(gamma1, body)
          result    = Forall(name, a1, bodyType)
          _        <- typeCheck(gamma, result)
        } yield result

      case Let(name, subst, body) =>
        for {
          _      <- typeCheck(gamma, subst)
          a1      = betaNormalize(subst)
          b1      = shiftSubShift(name, a1, body, normalize = false)
          result <- typeCheck(gamma, b1)
        } yield result

      case Applied(func, arg) =>
        for {
          funcType                <- typeCheck(gamma, func)
          argType                 <- typeCheck(gamma, arg)
          p                       <- funcType match {
                                       case Forall(xName, xType, bodyType) => Right((xName, xType, bodyType))
                                       case _                              => Left(TypeError(gamma, e, func, s"Only functions can be applied to arguments, but got a value of type ${funcType.print}"))
                                     }
          (xName, xType, bodyType) = p
          _                       <- requireEqual(argType, xType)
          result                   = shiftSubShift(xName, arg, bodyType, normalize = true)
        } yield result

      case _ => Left(TypeError(gamma, e, e, "No rule matches"))
    }
  }

  implicit class ExprTypecheck(e: Expr) {
    def inferType: Expr = typeCheck(Nil, e) match {
      case Left(error) => throw new Exception(error.toString)
      case Right(t)    => t
    }
  }

  Seq(
    "123"                      -> "Natural",
    "1 + 1"                    -> "Natural",
    "Natural/subtract 1 1"     -> "Natural",
    "Natural/subtract 1"       -> "∀(_ : Natural) → Natural",
    "λ(x : Natural) → x + 123" -> "∀(x : Natural) → Natural",
    "Natural/fold 1 Natural"   -> "∀(_ : ∀(_ : Natural) → Natural) → ∀(_ : Natural) → Natural",
  ).validate(s => s.dhall.inferType.print)

  println("Tests passed for inferType().")

  /* Convert µDhall type expressions to Scala izumi-reflect's type tags (Tag typeclass) when possible. */
  // The type expression must be in the beta-normalized form: no "let"-expressions or type annotations may be left.
  // µDhall's `Type` is represented by Any.
  // Lambda expressions are not types and will not be supported for now.
  def asTag(e: Expr): Either[String, Tag[_]] = {
    e match {
      case Expr.Forall(name, tipe, body) =>
        for { // For now we do not support type variables or dependent types.
          tipeTag <- asTag(tipe)
          bodyTag <- asTag(body)
        } yield Tag.appliedTag(TagKK[Function1], List(tipeTag.tag, bodyTag.tag))

      case Expr.Builtin(Constant.Natural) => Right(Tag[Natural])
      case Expr.Builtin(Constant.Type)    => Right(Tag[Nothing])
      // case Expr.Applied(func, arg) => ???
      // case Expr.Lambda(name, tipe, body) => ???
      case _                              => Left(s"Cannot convert µDhall expression ${e.print} to a Scala type")
    }
  }

  Seq[(String, Tag[_])](
    "Natural"                         -> Tag[Natural],
    "Type"                            -> Tag[Nothing],
    "Natural -> Natural"              -> Tag[Natural => Natural],
    "Natural -> Natural -> Natural"   -> Tag[Natural => Natural => Natural],
    "(Natural -> Natural) -> Natural" -> Tag[(Natural => Natural) => Natural],
  ).validate(s => asTag(s.dhall).right.get)

  println("Tests passed for asTag().")

  /* Convert µDhall values to native Scala values when possible. */

  // This value models an expression translated into Scala but still depending on some variables in an environment.
  // final case class ValE(scalaExpr: Map[Expr.Variable, Either[ValE, Any]] => Any)

  // This is still broken when using Natural/fold with a function type.
  def asScala[A](e: Expr)(implicit tag: Tag[A]): A = (for {
    inferredType <- typeCheck(Nil, e)
    inferredTag  <- asTag(inferredType)
    _            <- if (inferredTag == tag) Right(())
                    else
                      Left(
                        s"Cannot convert from Dhall expression ${e.print} having incompatible type ${inferredType.print}, tag ${inferredTag}, to expected Scala type $tag"
                      )
    scalaValue   <- convertValueToScala(e)
  } yield scalaValue) match {
    case Left(error)       =>
      throw new Exception(s"Cannot convert from Dhall expression ${e.print} to Scala type $tag: $error")
    case Right(scalaValue) => scalaValue.value.asInstanceOf[A]
  }

  final class AsScalaV(v: => Any) { // Required to be lazy, or else lambdas do not work.
    def value: Any = v // Cannot make this a lazy val!
  }

  // A function look-alike but allows us to assign the argument and the function body externally.
  final case class FuncWithVar[A, B](e: Expr, var func: A => B = { (_: A) => null.asInstanceOf[B] }) extends Function1[A, B] {
    override def toString(): String = "{ " + e.print + " }"

    var argument: A = null.asInstanceOf[A]

    override def apply(a: A): B = {
      println(s"DEBUG: inside a closure translated from '${e.print}', setting argument = $a")
      argument = a
      func(a)
    }
  }

  private def convertValueToScala(e: Expr, scalaVars: Map[Expr.Variable, AsScalaV] = Map()): Either[String, AsScalaV] = {
    e match {
      case Expr.NaturalLiteral(value) => Right(new AsScalaV(value))

      case v @ Expr.Variable(_, _) =>
        scalaVars.get(v) match {
          case Some(knownVariableAssignment) =>
            Right(knownVariableAssignment)
          case None                          =>
            Left(s"Error: undefined variable $v while known variables are $scalaVars")
        }

      case Expr.Lambda(name, _, body) =>
        // Create a Scala function with variable named "x". Substitute name = x in body but first shift name upwards in body.
        // Example:
        //    "λ(n : Natural) → n + (λ(n : Natural) → n + n@1) 2" should evaluate to "λ(n : Natural) → n + 2 + n"
        // should be replaced by:
        //    { x: Any => x.asInstanceOf[BigInt] + {x2 : Any => x2 + x}(2) }
        val lambdaFunction = FuncWithVar[Any, Any](e)
        val varX           = new AsScalaV(lambdaFunction.argument)
        val variables1     = shiftVars(up = true, name, scalaVars)
        val variables2     = variables1 ++ Map(Expr.Variable(name, 0) -> varX)
        convertValueToScala(body, variables2).map { bodyAsScala =>
          lambdaFunction.func = { _ => bodyAsScala.value }
          println(s"DEBUG: returning a new lambda function $name → ${body.print}, bound variables $variables2")
          new AsScalaV(lambdaFunction)
        }

      case Expr.Forall(_, tipe, body) =>
        for { // Only support non-dependent type signatures without type parameters.
          tag <- asTag(e) // Forall is always a function type, so we just convert it to a tag.
        } yield new AsScalaV(tag)

      case Expr.Let(name, subst, body) => // Evaluated as (λ(name) => body) subst.
        convertValueToScala(letExprAsApplied(name, subst, body), scalaVars)

      case Expr.Annotated(body, _) => convertValueToScala(body, scalaVars)

      case Expr.Applied(func, arg) =>
        for {
          functionHead <- convertValueToScala(func, scalaVars)
          argument     <- convertValueToScala(arg, scalaVars)
        } yield new AsScalaV(functionHead.value.asInstanceOf[Function1[Any, Any]].apply(argument.value))

      case Expr.Builtin(constant) =>
        constant match {
          case Constant.NaturalFold     => Right(new AsScalaV(Natural_fold_native))
          case Constant.NaturalSubtract => Right(new AsScalaV(Natural_subtract_native))
          case Constant.Natural         => Right(new AsScalaV(Tag[Natural]))
          case _                        => Left(s"Type symbol $constant cannot be converted to a Scala value")
        }

      case Expr.BinaryOp(left, op, right) =>
        // Helper function: apply a binary operation.
        // No checking needed here, because all expressions were already type-checked.
        def useOp[P: Tag, Q: Tag, R: Tag](operator: (P, Q) => R): Either[String, AsScalaV] = {
          // The final value must be of the given type.
          for {
            evalLop <- convertValueToScala(left, scalaVars)
            evalRop <- convertValueToScala(right, scalaVars)
          } yield new AsScalaV(operator(evalLop.value.asInstanceOf[P], evalRop.value.asInstanceOf[Q]))
        }

        op match {
          case Operator.Plus  => useOp[Natural, Natural, Natural](_ + _)
          case Operator.Times => useOp[Natural, Natural, Natural](_ * _)
        }
    }
  }

  // Helper function: apply shift() to all variables in a given dictionary.
  def shiftVars[T](up: Boolean, varName: String, vars: Map[Expr.Variable, T]): Map[Expr.Variable, T] = vars.map { case (variable, value) =>
    shift(if (up) 1 else -1, varName, 0, variable).asInstanceOf[Expr.Variable] -> value // shift() transforms Variable into Variable.
  }

  // Helper function: replace "let name = subst in body" by "(λ(name) => body) subst". This is equivalent for evaluation (but not for type-checking!).
  def letExprAsApplied(name: String, subst: Expr, body: Expr): Expr =
    Expr.Applied(Expr.Lambda(name, subst.inferType, body), subst)

  implicit class ExprAsScala(e: Expr) {
    def asScala[A: Tag]: A = MuDhall.asScala[A](e)
  }

  type Natural = Int

  val Natural_subtract_native: Natural => Natural => Natural = x => y => math.max(0, y - x)

  val Natural_fold_native: Natural => Tag[Nothing] => (Any => Any) => Any => Any = { m => _ => update => init =>
    @tailrec
    def loop(currentResult: Any, counter: Natural): Any =
      if (counter >= m) currentResult
      else {
        val newResult = update(currentResult)
        println(s"DEBUG: Natural/fold computes newResult = $newResult")
        if (newResult == currentResult) {
          // Shortcut: the result did not change after applying `update`, so no need to continue the loop.
          currentResult
        } else {
          loop(newResult, counter + 1)
        }
      }
    loop(currentResult = init, counter = 0)
  }

  Seq[(String, Natural)](
    "12345"                                                    -> 12345,
    "1 + 2 + 3 + 4"                                            -> 10,
    "1 * 2 * 3 * 4"                                            -> 24,
    "Natural/subtract 1 1000"                                  -> 999,
    "(λ(n : Natural) → n + 1) 1000"                            -> 1001,
    "(λ(n : Natural) → n + 1) ((λ(n : Natural) → n + 1) 1000)" -> 1002,
    "Natural/fold 10 Natural (λ(n : Natural) → n + 1) 100"     -> 110,
  ).validate(s => s.dhall.asScala[Natural])

  expect(
    Try(
      "λ(n : Natural) → n".dhall.asScala[Natural => Boolean]
    ).failed.get.getMessage == "Cannot convert from Dhall expression λ(n : Natural) → n to Scala type Tag[Function1[-Int,+Boolean]]: Cannot convert from Dhall expression λ(n : Natural) → n having incompatible type ∀(n : Natural) → Natural, tag Tag[Function1[-Int,+Int]], to expected Scala type Tag[Function1[-Int,+Boolean]]"
  )

  // Convert a function of type Natural → Natural from µDhall to a Scala function of type Int => Int.
  // Then apply that Scala function to a Scala argument of type Int. Verify the expected result.
  Seq[(String, Natural, Natural)](
    ("λ(n : Natural) → n", 10, 10),
    ("λ(n : Natural) → n + 20", 10, 30),
    ("λ(n : Natural) → 3 * n + (λ(n : Natural) → n + n@1) 2", 10, 42),
    ("(λ(n : Natural) → λ(a : Natural) → a + n) 100", 20, 120),
    ("Natural/subtract 2", 10, 8),
    ("Natural/subtract 20", 10, 0),
    ("(λ(f : Natural → Natural) → λ(x : Natural) → x + f x) (λ(a : Natural) → a + 1)", 10, 21),
  ).zipWithIndex.foreach { case ((funcMuDhall, x, expected), i) =>
    val f: Natural => Natural = funcMuDhall.dhall.asScala[Natural => Natural]
    expect(i >= 0 && f(x) == expected)
  }

  val x: Tag[Natural => Natural] = Tag[Natural => Natural]
  expect("Natural → Natural".dhall.asScala == Tag[Natural => Natural])

  val g1             = "λ(f : Natural → Natural) → λ(n : Natural) → 1 + f n".dhall
  val g1scala        = g1.asScala[(Natural => Natural) => Natural => Natural]
  expect(g1scala.toString == "{ λ(f : ∀(_ : Natural) → Natural) → λ(n : Natural) → 1 + f n }")
  val g2             = "λ(n : Natural) → n + 2".dhall
  val g2scala        = g2.asScala[Natural => Natural]
  expect(g2scala.toString == "{ λ(n : Natural) → n + 2 }")
  expect(g2scala(2) == 4)
  val g3             = g1(g2)
  val g3scala        = g3.asScala[Natural => Natural]
  expect(g3scala.toString == "{ λ(n : Natural) → 1 + f n }")
  expect(g3scala(2) == 5)
  val g3scalaByScala = g1scala(g2scala)
  expect(g3scalaByScala(2) == 5)
  val g4scalaByScala = g1scala(g3scalaByScala)
  expect(g4scalaByScala(2) == 6) // fails already!
  val foldByScala = Natural_fold_native(3)(Tag[Nothing])(g1scala.asInstanceOf[Any => Any])(g2scala).asInstanceOf[Any => Any]
  expect(foldByScala(10) == 15) // fails!

  println("A loop involving a function type.")
  val fDhall = "Natural/fold 3 (Natural → Natural) (λ(f : Natural → Natural) → λ(n : Natural) → 1 + f n) (λ(n : Natural) → n + 2)".dhall
  val fScala = fDhall.asScala[Natural => Natural]
  expect(fScala(10) == 15) // fails!

  println("Tests passed for asScala().")
}
