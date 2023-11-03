package io.chymyst.ui.dhall

import enumeratum._
import io.chymyst.ui.dhall.CBORmodel.CBytes
import io.chymyst.ui.dhall.Grammar.{TextLiteralNoInterp, hexStringToByteArray}
import io.chymyst.ui.dhall.Syntax.Expression
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.SyntaxConstants.Operator.Plus
import io.chymyst.ui.dhall.SyntaxConstants.{ConstructorName, FieldName, FilePrefix, ImportMode, ImportType, Operator, VarName}

import java.time.LocalTime
import scala.language.implicitConversions
import scala.util.chaining.scalaUtilChainingOps

object SyntaxConstants {
  final case class VarName(name: String) extends AnyVal {
    def escape: String = if ((Grammar.simpleKeywordsSet contains name) || (Grammar.builtinSymbolNamesSet contains name) || (SyntaxConstants.Constant.namesToValuesMap.keySet contains name) || (name matches ".*[^-/_A-Za-z0-9].*")) s"`$name`"
    else name
  }

  final case class FieldName(name: String) extends AnyVal

  final case class ConstructorName(name: String) extends AnyVal

  trait HasCborCode[A, B] {
    def cborCode: B
  }

  trait HasCborCodeDict[B, A <: EnumEntry with HasCborCode[A, B]] {
    self: Enum[A] =>
    lazy val cborCodeDict: Map[B, A] = values.map { op => (op.cborCode, op) }.toMap
  }

  sealed abstract class Operator(val name: String, val cborCode: Int) extends EnumEntry with HasCborCode[Operator, Int]

  object Operator extends Enum[Operator] with HasCborCodeDict[Int, Operator] {

    val values = findValues

    case object Or extends Operator("||", 0)

    case object Plus extends Operator("+", 4)

    case object TextAppend extends Operator("++", 6)

    case object ListAppend extends Operator("#", 7)

    case object And extends Operator("&&", 1)

    case object CombineRecordTerms extends Operator("∧", 8)

    case object Prefer extends Operator("⫽", 9)

    case object CombineRecordTypes extends Operator("⩓", 10)

    case object Times extends Operator("*", 5)

    case object Equal extends Operator("==", 2)

    case object NotEqual extends Operator("!=", 3)

    case object Equivalent extends Operator("===", 12)

    case object Alternative extends Operator("?", 11)
  }

  sealed abstract class Builtin(override val entryName: String) extends EnumEntry {
    def unary_~ : Expression = Expression(ExprBuiltin(this))
  }

  object Builtin extends Enum[Builtin] {
    case object Bool extends Builtin("Bool")

    case object Bytes extends Builtin("Bytes")

    case object Date extends Builtin("Date")

    case object DateShow extends Builtin("Date/show")

    case object Double extends Builtin("Double")

    case object DoubleShow extends Builtin("Double/show")

    case object Integer extends Builtin("Integer")

    case object IntegerClamp extends Builtin("Integer/clamp")

    case object IntegerNegate extends Builtin("Integer/negate")

    case object IntegerShow extends Builtin("Integer/show")

    case object IntegerToDouble extends Builtin("Integer/toDouble")

    case object List extends Builtin("List")

    case object ListBuild extends Builtin("List/build")

    case object ListFold extends Builtin("List/fold")

    case object ListHead extends Builtin("List/head")

    case object ListIndexed extends Builtin("List/indexed")

    case object ListLast extends Builtin("List/last")

    case object ListLength extends Builtin("List/length")

    case object ListReverse extends Builtin("List/reverse")

    case object Natural extends Builtin("Natural")

    case object NaturalBuild extends Builtin("Natural/build")

    case object NaturalEven extends Builtin("Natural/even")

    case object NaturalFold extends Builtin("Natural/fold")

    case object NaturalIsZero extends Builtin("Natural/isZero")

    case object NaturalOdd extends Builtin("Natural/odd")

    case object NaturalShow extends Builtin("Natural/show")

    case object NaturalSubtract extends Builtin("Natural/subtract")

    case object NaturalToInteger extends Builtin("Natural/toInteger")

    case object None extends Builtin("None")

    case object Optional extends Builtin("Optional")

    case object Text extends Builtin("Text")

    case object TextReplace extends Builtin("Text/replace")

    case object TextShow extends Builtin("Text/show")

    case object Time extends Builtin("Time")

    case object TimeShow extends Builtin("Time/show")

    case object TimeZone extends Builtin("TimeZone")

    case object TimeZoneShow extends Builtin("TimeZone/show")

    override def values = findValues
  }

  sealed trait Constant extends EnumEntry {
    def unary_~ : Expression = Expression(ExprConstant(this))
  }

  object Constant extends Enum[Constant] {
    val values = findValues

    case object Type extends Constant

    case object Kind extends Constant

    case object Sort extends Constant

    case object True extends Constant

    case object False extends Constant

  }

  sealed abstract class ImportMode(val cborCode: Int) extends EnumEntry with HasCborCode[ImportMode, Int]

  object ImportMode extends Enum[ImportMode] with HasCborCodeDict[Int, ImportMode] {
    val values = findValues

    case object Code extends ImportMode(0)

    case object RawBytes extends ImportMode(3)

    case object RawText extends ImportMode(1)

    case object Location extends ImportMode(2)
  }

  sealed abstract class Scheme(val cborCode: Int) extends EnumEntry with HasCborCode[Scheme, Int]

  object Scheme extends Enum[Scheme] with HasCborCodeDict[Int, Scheme] {
    val values = findValues

    case object HTTP extends Scheme(0)

    case object HTTPS extends Scheme(1)
  }

  sealed abstract class FilePrefix(val cborCode: Int, val prefix: String) extends EnumEntry with HasCborCode[FilePrefix, Int]

  object FilePrefix extends Enum[FilePrefix] with HasCborCodeDict[Int, FilePrefix] {
    val values = findValues

    case object Absolute extends FilePrefix(2, "")

    case object Here extends FilePrefix(3, ".") // ./something relative to the current working directory

    case object Parent extends FilePrefix(4, "..") // ./something relative to the parent working directory

    case object Home extends FilePrefix(5, "~") // ~/something relative to the user's home directory
  }

  sealed abstract class ImportType[+E] {
    def map[H](f: E => H): ImportType[H] = this match {
      case ImportType.Remote(url, headers) => ImportType.Remote(url, headers map f)
      case _ => this.asInstanceOf[ImportType[H]]
    }

    protected def safetyLevelRequired: Int

    // This import may depend on another import only if this import's safety level does not require greater safety than another import's.
    def allowedToImportAnother(anotherImportType: ImportType[_]): Boolean =
      this.safetyLevelRequired <= anotherImportType.safetyLevelRequired
  }

  object ImportType {
    final case object Missing extends ImportType[Nothing] {
      override def safetyLevelRequired: Int = 1 // This cannot import anything.
    }

    final case class Remote[E](url: URL, headers: Option[E]) extends ImportType[E] {
      override def safetyLevelRequired: Int = 0 // This can import itself or Missing.
    }

    final case class Path(filePrefix: FilePrefix, file: File) extends ImportType[Nothing] {
      override def safetyLevelRequired: Int = -1 // This can import anything else.
    }

    final case class Env(envVarName: String) extends ImportType[Nothing] {
      override def safetyLevelRequired: Int = -1 // This can import anything else.
    }
  }

  // The authority of http://user@host:port/foo is stored as "user@host:port".
  // The query of ?foo=1&bar=true is stored as "foo=1&bar=true".
  final case class URL(scheme: Scheme, authority: String, path: File, query: Option[String]) {
    override def toString: String = scheme.entryName.toLowerCase + "://" + authority + "/" + path.toString + (query match {
      case Some(value) => "?" + value
      case None => ""
    })
  }

  final case class File(segments: Seq[String]) {
    require(segments.nonEmpty) // The last segment is the file name (may be a empty string), all previous segments are path components (may be none)

    override def toString: String = segments.mkString("/")

    // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/imports.md
    def canonicalize: File = {
      val newSegments: Seq[String] = segments.foldLeft(List[String]())((prev, segment) => segment match {
        case "." => prev
        case ".." if prev.tail.nonEmpty => prev.tail
        case s => s :: prev
      }).reverse
      File(newSegments)
    }

    def chain(child: File): File = if (segments.isEmpty) child else File(segments.init ++ child.segments)

    def chainToParent(child: File): File = chain(File(".." +: child.segments))
  }

  object File {
    def of(segments: Seq[String]): File = if (segments.isEmpty) File(Seq("")) else File(segments)

    def unescapePathSegment(segment: String): String = Seq(
      ("\\a", "\u0007"),
      ("\\b", "\n"),
      ("\\f", "\f"),
      ("\\n", "\n"),
      ("\\r", "\r"),
      ("\\t", "\t"),
      ("\\v", "\u000B"),
      ("\\/", "/"),
      ("\\\"", "\""),
      ("\\\\", "\\"),
    ).foldLeft(segment) { case (prev, (s, replacement)) => prev.replace(s, replacement) }

    def urlEscapePathSegment(segment: String): String = Seq(
      ":", ",", "?", "#", "[", "]", "@", "!", "$", "&", "'", "(", ")", "*", "+", ",", ";", "=",
    ).foldLeft(segment) { (prev, s) => prev.replace(s, String.format("%%%2H", s)) }
  }
}

object Syntax {
  final case class DhallFile(shebangs: Seq[String], value: Expression)

  type Natural = BigInt

  type Integer = BigInt

  // Define a recursion scheme for Expression.
  sealed trait ExpressionScheme[+E] extends TermPrecedence {

    import ExpressionScheme._

    def map[H](f: E => H): ExpressionScheme[H] = {
      implicit val ff: E => H = f
      implicit val ffOption: Option[E] => Option[H] = _ map f

      implicit def fseq[A]: Seq[E] => Seq[H] = _ map f

      implicit def fseqE[A]: Seq[(A, E)] => Seq[(A, H)] = _ map { case (a, b) => (a, b) }

      implicit def fseqOption[A]: Seq[(A, Option[E])] => Seq[(A, Option[H])] = _ map { case (a, b) => (a, b) }

      implicit def flist[A]: List[(A, E)] => List[(A, H)] = _ map { case (a, b) => (a, b) }

      implicit def fImport[A]: ImportType[E] => ImportType[H] = _ map f

      this match {
        case Lambda(name, tipe, body) => Lambda(name, tipe, body)
        case Forall(name, tipe, body) => Forall(name, tipe, body)
        case Let(name, tipe, subst, body) => Let(name, tipe, subst, body)
        case If(cond, ifTrue, ifFalse) => If(cond, ifTrue, ifFalse)
        case Merge(record, update, tipe) => Merge(record, update, tipe)
        case ToMap(data, tipe) => ToMap(data, tipe)
        case EmptyList(tipe) => EmptyList(tipe)
        case NonEmptyList(exprs) => NonEmptyList(exprs)
        case Annotation(data, tipe) => Annotation(data, tipe)
        case ExprOperator(lop, op, rop) => ExprOperator(lop, op, rop)
        case Application(func, arg) => Application(func, arg)
        case Field(base, name) => Field(base, name)
        case ProjectByLabels(base, labels) => ProjectByLabels(base, labels)
        case ProjectByType(base, by) => ProjectByType(base, by)
        case Completion(base, target) => Completion(base, target)
        case Assert(assertion) => Assert(assertion)
        case With(data, pathComponents, body) => With(data, pathComponents, body)
        case TextLiteral(interpolations, trailing) => TextLiteral(interpolations, trailing)
        case RecordType(defs) => RecordType(defs)
        case RecordLiteral(defs) => RecordLiteral(defs)
        case UnionType(defs) => UnionType(defs)
        case ShowConstructor(data) => ShowConstructor(data)
        case Import(importType, importMode, digest) => Import(importType, importMode, digest)
        case KeywordSome(data) => KeywordSome(data)
        case _ => this.asInstanceOf[ExpressionScheme[H]]
      }
    }
  }

  object ExpressionScheme {
    val underscore = VarName("_")

    implicit def toExpression(s: ExpressionScheme[Expression]): Expression = Expression(s)

    trait TermPrecedence {
      def prec: Int = TermPrecedence.low // Default is the low precedence.
    }

    object TermPrecedence {
      def ofOperator(op: Operator): Int = offsetForOperators + op.cborCode * 2

      val offsetForOperators = 30
      val low = 100
      val lowest = 1000
    }

    trait VarPrecedence extends TermPrecedence {
      override def prec: Int = TermPrecedence.offsetForOperators / 3
    }

    trait HighPrecedence extends TermPrecedence {
      override def prec: Int = TermPrecedence.offsetForOperators / 2
    }

    trait LowerPrecedence extends TermPrecedence {
      override def prec: Int = TermPrecedence.low + 100
    }

    final case class Variable(name: VarName, index: Natural) extends ExpressionScheme[Nothing] with VarPrecedence {
      override def equals(other: Any): Boolean = other.isInstanceOf[Variable] && {
        val otherVar = other.asInstanceOf[Variable]
        (otherVar.name equals name) && (otherVar.index equals index)
      }
    }

    final case class Lambda[E](name: VarName, tipe: E, body: E) extends ExpressionScheme[E]

    final case class Forall[E](name: VarName, tipe: E, body: E) extends ExpressionScheme[E]

    final case class Let[E](name: VarName, tipe: Option[E], subst: E, body: E) extends ExpressionScheme[E]

    final case class If[E](cond: E, ifTrue: E, ifFalse: E) extends ExpressionScheme[E]

    final case class Merge[E](record: E, update: E, tipe: Option[E]) extends ExpressionScheme[E]

    final case class ToMap[E](data: E, tipe: Option[E]) extends ExpressionScheme[E]

    final case class EmptyList[E](tipe: E) extends ExpressionScheme[E] with HighPrecedence

    final case class NonEmptyList[E](exprs: Seq[E]) extends ExpressionScheme[E] with HighPrecedence {
      require(exprs.nonEmpty)
    }

    final case class Annotation[E](data: E, tipe: E) extends ExpressionScheme[E]

    final case class ExprOperator[E](lop: E, op: SyntaxConstants.Operator, rop: E) extends ExpressionScheme[E] {
      override def prec: Int = TermPrecedence.ofOperator(op)
    }

    final case class Application[E](func: E, arg: E) extends ExpressionScheme[E]

    final case class Field[E](base: E, name: FieldName) extends ExpressionScheme[E] with HighPrecedence

    // Note: `labels` may be an empty list.
    final case class ProjectByLabels[E](base: E, labels: Seq[FieldName]) extends ExpressionScheme[E] {
      def sorted: ProjectByLabels[E] = ProjectByLabels(base, labels.sortBy(_.name))
    }

    final case class ProjectByType[E](base: E, by: E) extends ExpressionScheme[E]

    // An Expression of the form `T::r` is syntactic sugar for `(T.default // r) : T.Type`.
    final case class Completion[E](base: E, target: E) extends ExpressionScheme[E] {
      override def prec: Int = TermPrecedence.offsetForOperators + 13
    }

    final case class Assert[E](assertion: E) extends ExpressionScheme[E]

    final case class With[E](data: E, pathComponents: Seq[PathComponent], body: E) extends ExpressionScheme[E] {
      require(pathComponents.nonEmpty)
    }

    final case class DoubleLiteral(value: Double) extends ExpressionScheme[Nothing] with VarPrecedence {
      override def equals(other: Any): Boolean = other.isInstanceOf[DoubleLiteral] && {
        val otherValue = other.asInstanceOf[DoubleLiteral].value
        (value == otherValue) || (value.isNaN && otherValue.isNaN)
      }
    }

    final case class NaturalLiteral(value: Natural) extends ExpressionScheme[Nothing] with VarPrecedence {
      override def equals(other: Any): Boolean = other.isInstanceOf[NaturalLiteral] && {
        val otherValue = other.asInstanceOf[NaturalLiteral].value
        value equals otherValue
      }
    }

    object NaturalLiteral {
      def apply(value: Int): NaturalLiteral = {
        require(value >= 0)
        NaturalLiteral(BigInt(value))
      }
    }

    final case class IntegerLiteral(value: Integer) extends ExpressionScheme[Nothing] with VarPrecedence {
      override def equals(other: Any): Boolean = other.isInstanceOf[IntegerLiteral] && {
        val otherValue = other.asInstanceOf[IntegerLiteral].value
        value equals otherValue
      }
    }

    object IntegerLiteral {
      def apply(value: Int): IntegerLiteral = {
        IntegerLiteral(BigInt(value))
      }
    }

    object TextLiteral {
      def ofString[E](s: String) = ofText[E](TextLiteralNoInterp(s))

      def ofText[E](textLiteralNoInterp: TextLiteralNoInterp) = TextLiteral[E](List(), textLiteralNoInterp.value)

      def empty[E] = TextLiteral[E](List(), "")

      def ofExpression[E](expr: E) = TextLiteral(interpolations = List(("", expr)), trailing = "")
    }

    final case class TextLiteral[+E](interpolations: List[(String, E)], trailing: String) extends ExpressionScheme[E] with VarPrecedence {

      def ++[G >: E, H <: G](other: TextLiteral[H]): TextLiteral[G] = other.interpolations match {
        case List() =>
          TextLiteral(this.interpolations, this.trailing ++ other.trailing)
        case (headText, headExpr) :: tail =>
          TextLiteral(this.interpolations ++ ((this.trailing + headText, headExpr: G) :: tail), other.trailing)
      }

      // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/multiline.md
      private lazy val whitespacePrefixRegex = "[ \t]*".r

      lazy val whitespacePrefix: String = {
        val firstString = interpolations.headOption.map(_._1).getOrElse(trailing)
        whitespacePrefixRegex.findPrefixMatchOf(firstString).map(_.matched).getOrElse("")
      }

      def isEmpty: Boolean = trailing.isEmpty && interpolations.isEmpty

      private lazy val lines: Seq[TextLiteral[E]] = {
        // Use H >: E to avoid covariance errors.
        def loop[H >: E](currentLine: TextLiteral[H], nextLine: TextLiteral[H]): Seq[TextLiteral[H]] = {
          nextLine.interpolations.headOption match {
            case None =>
              val splitLines = splitByAllNewlines(nextLine.trailing).map(TextLiteral.ofString[H])
              (currentLine ++ splitLines.head) +: splitLines.tail

            case Some((head, interpolation)) =>
              val headSplit = splitByAllNewlines(head)
              val l0 = headSplit.head // Guaranteed to exist.
              val ls = headSplit.tail
              if (ls.isEmpty) loop(
                currentLine ++ TextLiteral[H](List((head, interpolation)), ""),
                TextLiteral(nextLine.interpolations.tail, trailing),
              )
              else (currentLine ++ TextLiteral.ofString[H](l0)) +: (ls.init.map(TextLiteral.ofString[H]) ++ loop(
                TextLiteral[H](List((ls.last, interpolation)), ""),
                TextLiteral[H](nextLine.interpolations.tail, trailing),
              ))
          }
        }

        loop(TextLiteral.empty[E], this)
      }

      def align: TextLiteral[E] = {
        def longestCommonPrefix(a: String, b: String): String = a.iterator.zip(b.iterator).takeWhile { case (x, y) => x == y }.map(_._1).mkString

        val removeEmpty: Seq[TextLiteral[E]] = lines.init.filterNot(_.isEmpty) :+ lines.last
        val longestCommonIndent: String = removeEmpty.map(_.whitespacePrefix).reduceRight(longestCommonPrefix)
        removeIndentsAndConcatenate(longestCommonIndent.length)
      }

      private def splitByAllNewlines(s: String): Seq[String] =
        s.split("\r\n", -1)
          .flatMap(_.split("\n", -1))
          .toSeq
          .pipe(s => if (s.isEmpty) Seq("") else s)

      private def removeIndentsAndConcatenate(indent: Int): TextLiteral[E] = {
        def join(a: TextLiteral[E], b: TextLiteral[E]): TextLiteral[E] = a ++ TextLiteral.ofString("\n") ++ b

        def joinLines(lines: Seq[TextLiteral[E]]): TextLiteral[E] = lines.reduceRight(join)

        joinLines(lines.map(_.stripPrefix(indent))).escape
      }

      def stripPrefix(indent: Int): TextLiteral[E] = interpolations.headOption match {
        case Some((head, tail)) => copy(interpolations = (head.drop(indent), tail) +: interpolations.tail)
        case None => copy(trailing = trailing.drop(indent))
      }

      def mapStrings(f: String => String): TextLiteral[E] = copy(
        interpolations = interpolations.map { case (head, tail) => (f(head), tail) },
        trailing = f(trailing),
      )

      private def reEscape(s: String): String = s.replace("'''", "''").replace("''${", "${")

      private def escape: TextLiteral[E] = mapStrings(reEscape)

    }

    // The hex string must be lowercase.
    final case class BytesLiteral private(hex: String) extends ExpressionScheme[Nothing] with VarPrecedence {
      val bytes: Array[Byte] = hexStringToByteArray(hex)
    }

    object BytesLiteral {
      def of(hex: String) = BytesLiteral(hex.toUpperCase)

      def of(bytes: Array[Byte]) = BytesLiteral(CBytes.byteArrayToHexString(bytes))
    }

    final case class DateLiteral(year: Int, month: Int, day: Int) extends ExpressionScheme[Nothing] with VarPrecedence

    final case class TimeLiteral(time: LocalTime) extends ExpressionScheme[Nothing] with VarPrecedence

    final case class TimeZoneLiteral(totalMinutes: Int) extends ExpressionScheme[Nothing] with VarPrecedence {
      val hours: Int = math.abs(totalMinutes) / 60
      val minutes: Int = math.abs(totalMinutes) % 60
      val isPositive: Boolean = totalMinutes >= 0
    }

    final case class RecordType[E](defs: Seq[(FieldName, E)]) extends ExpressionScheme[E] with HighPrecedence {
      lazy val sorted = RecordType(defs.sortBy(_._1.name))
    }

    final case class RecordLiteral[+E](defs: Seq[(FieldName, E)]) extends ExpressionScheme[E] with HighPrecedence {
      lazy val sorted = RecordLiteral(defs.sortBy(_._1.name))

      def lookup(field: FieldName): Option[E] = defs.find(_._1 == field).map(_._2) // TODO: do we need a faster lookup here?
    }

    object RecordLiteral {
      // Parse a non-empty sequence of RawRecordLiteral's into a RecordLiteral.
      def of(values: Seq[RawRecordLiteral]): RecordLiteral[Expression] = {
        /* See https://github.com/dhall-lang/dhall-lang/blob/master/standard/README.md#record-syntactic-sugar

          ... a record literal of the form:

          { x.y = 1, x.z = 1 }

          ... first desugars dotted fields to nested records:

          { x = { y = 1 }, x = { z = 1 } }

          ... and then desugars duplicate fields by merging them using ∧:

          { x = { y = 1 } ∧ { z = 1} }

          ... this conversion occurs at parse-time ...

          See https://github.com/dhall-lang/dhall-lang/blob/master/standard/record.md

           */
        val desugared: Seq[(FieldName, Expression)] = values.map {
          // Desugar { x } into { x = x }.
          case RawRecordLiteral(base, None) => (base, Expression(Variable(VarName(base.name), BigInt(0))))

          // Desugar { w.x.y.z = expr } into {w = {x = { y = {z = expr }}}}.
          case RawRecordLiteral(base, Some((fields, target))) => (base, fields.foldRight(target) { (field, expr) => Expression(RecordLiteral(Seq((field, expr)))) })
        }

        // Desugar repeated field names { x = { y = 1 }, x = { z = 1 } } into { x = { y = 1 } ∧ { z = 1} }. This is needed at the top nested level only.
        def desugarRepetition(defs: Seq[(FieldName, Expression)]): Seq[(FieldName, Expression)] = {
          val recordMap: Map[FieldName, Expression] =
            defs.groupBy(_._1)
              .map { case (field, subDefs) =>
                (field, subDefs.map(_._2).reduce((a, b) => Expression(ExprOperator(a, SyntaxConstants.Operator.CombineRecordTerms, b))))
              }
          // Preserve the original order of definitions.
          defs.map(_._1).distinct.map { fieldName => (fieldName, recordMap(fieldName)) }
        }

        RecordLiteral[Expression](desugarRepetition(desugared))
      }
    }

    final case class UnionType[E](defs: Seq[(ConstructorName, Option[E])]) extends ExpressionScheme[E] with HighPrecedence {
      lazy val sorted = UnionType(defs.sortBy(_._1.name))
    }

    final case class ShowConstructor[E](data: E) extends ExpressionScheme[E]

    final case class Import[E](importType: SyntaxConstants.ImportType[E], importMode: SyntaxConstants.ImportMode, digest: Option[BytesLiteral]) extends ExpressionScheme[E] {
      override def prec: Int = TermPrecedence.ofOperator(Operator.Alternative) - 1
    }

    final case class KeywordSome[E](data: E) extends ExpressionScheme[E]

    final case class ExprBuiltin(builtin: SyntaxConstants.Builtin) extends ExpressionScheme[Nothing] with VarPrecedence

    final case class ExprConstant(constant: SyntaxConstants.Constant) extends ExpressionScheme[Nothing] with VarPrecedence
  }

  final case class Expression(scheme: ExpressionScheme[Expression]) {
    def op(operator: Operator)(arg: Expression) = Expression(ExprOperator(scheme, operator, arg))

    def toCBORmodel: CBORmodel = CBOR.toCborModel(scheme)

    lazy val schemeWithBetaNormalizedArguments: ExpressionScheme[Expression] = scheme.map(_.betaNormalized)
    lazy val alphaNormalized: Expression = Semantics.alphaNormalize(this)

    // Produce a new Expression that has been beta-normalized and whose .betaNormalized method is precomputed.
    lazy val betaNormalized: Expression = if (betaN != null) betaN else this.synchronized {
      val normalized = Semantics.betaNormalize(this)
      this.betaN = normalized
      normalized.betaN = normalized
      //      println(s"DEBUG: lazy val computed as ($toDhall).betaNormalized = ${normalized.toDhall}")
      normalized
    }

    @volatile private var betaN: Expression = null

    // Print to Dhall syntax.

    def toDhall: String = atPrecedence(TermPrecedence.lowest)

    private def atPrecedence(level: Int) = if (scheme.prec > level) "(" + dhallForm + ")" else dhallForm

    final private def dhallForm: String = {
      val p = scheme.prec
      scheme match {
        case Variable(name, index) => s"${name.escape}${if (index > 0) "@" + index.toString(10) else ""}"
        case Lambda(name, tipe, body) => s"λ(${name.escape}: ${tipe.atPrecedence(p)}) -> ${body.atPrecedence(p)}"
        case Forall(name, tipe, body) => s"∀(${name.escape}: ${tipe.atPrecedence(p)}) -> ${body.atPrecedence(p)}"
        case Let(name, tipe, subst, body) => s"let ${name.escape} ${tipe.map(t => ": " + t.atPrecedence(p)).getOrElse("")} = ${subst.atPrecedence(p)} in ${body.atPrecedence(p)}"
        case If(cond, ifTrue, ifFalse) => s"if ${cond.atPrecedence(p)} then ${ifTrue.atPrecedence(p)} else ${ifFalse.atPrecedence(p)}"
        case Merge(record, update, tipe) => "merge " + record.atPrecedence(p) + " " + update.atPrecedence(p) + (tipe match {
          case Some(value) => ": " + value.atPrecedence(p)
          case None => ""
        })
        case ToMap(data, tipe) => "toMap " + data.atPrecedence(p) + (tipe match {
          case Some(value) => ": " + value.atPrecedence(p)
          case None => ""
        })
        case EmptyList(tipe) => s"[]: ${tipe.atPrecedence(p)}"
        case NonEmptyList(exprs) => exprs.map(_.atPrecedence(p)).mkString("[", ", ", "]")
        case Annotation(data, tipe) => s"${data.atPrecedence(p)}: ${tipe.atPrecedence(p)}"
        case ExprOperator(lop, op, rop) => s"${lop.atPrecedence(p)} ${op.name} ${rop.atPrecedence(p)}"
        case Application(func, arg) => s"${func.atPrecedence(p)} ${arg.atPrecedence(p)}"
        case Field(base, name) => base.atPrecedence(p) + "." + name.name
        case ProjectByLabels(base, labels) => base.atPrecedence(p) + "." + "{" + labels.map(_.name).mkString(", ") + "}"
        case ProjectByType(base, by) => base.atPrecedence(p) + "." + "(" + by.atPrecedence(p) + ")"
        case Completion(base, target) => base.atPrecedence(p) + " :: " + target.atPrecedence(p)
        case Assert(assertion) => s"assert : ${assertion.atPrecedence(p)}"
        case With(data, pathComponents, body) => data.atPrecedence(p) + " with " + pathComponents.map {
          case PathComponent.Label(name) => name.name
          case PathComponent.DescendOptional => "?"
        }.mkString(".") + " = " + body.atPrecedence(p)
        case DoubleLiteral(value) => value.toString
        case NaturalLiteral(value) => value.toString(10)
        case IntegerLiteral(value) => (if (value >= 0) "+" else "") + value.toString(10)
        case TextLiteral(interpolations, trailing) => "\"" + interpolations.map { case (prefix, expr) => prefix + "${" + expr.atPrecedence(p) + "}" }.mkString + trailing + "\""
        case BytesLiteral(hex) => s"0x\"$hex\""
        case DateLiteral(year, month, day) => s"$year-$month-$day"
        case TimeLiteral(time) => s"$time"
        case t@TimeZoneLiteral(_) => s"${if (t.isPositive) "+" else "-"}${t.hours}:${t.minutes}"
        case RecordType(defs) => "{ " + defs.map { case (name, expr) => name.name + ": " + expr.atPrecedence(p) }.mkString(", ") + " }"
        case RecordLiteral(defs) => "{ " + defs.map { case (name, expr) => name.name + " = " + expr.atPrecedence(p) }.mkString(", ") + " }"
        case UnionType(defs) => "< " + defs.map { case (name, expr) => name.name + expr.map(_.atPrecedence(p)).map(": " + _).getOrElse("") }.mkString(" | ") + " > "
        case ShowConstructor(data) => "showConstructor " + data.atPrecedence(p)
        case Import(importType, importMode, digest) =>
          val digestString = digest.map(b => " sha256:" + b.hex.toLowerCase).getOrElse("")
          val importModeString = importMode match {
            case ImportMode.Code => ""
            case ImportMode.RawBytes => " as Bytes"
            case ImportMode.RawText => " as Text"
            case ImportMode.Location => " as Location"
          }
          val importTypeString = importType match {
            case ImportType.Missing => "missing"
            case ImportType.Remote(url, headers) => url.toString + (headers match {
              case Some(value) => "using " + value.atPrecedence(p)
              case None => ""
            })
            case ImportType.Path(filePrefix, file) => filePrefix.prefix + "/" + file.toString
            case ImportType.Env(envVarName) => "env:" + envVarName
          }
          importTypeString + digestString + importModeString
        case KeywordSome(data) => s"Some ${data.atPrecedence(p)}"
        case ExprBuiltin(builtin) => builtin.entryName
        case ExprConstant(constant) => constant.entryName
      }
    }

    // Construct Dhall terms more easily.

    // Natural numbers.
    def +(other: Expression): Expression = ExprOperator(this, Plus, other)

    // Application is a(b)
    def apply(arg: Expression): Expression = Application(this, arg)

    // Type annotation is a :: b
    def |(other: Expression): Expression = Annotation(this, other)

    // Lambda is a -> b where a must be (Variable :: T)
    def ->(other: Expression): Expression = this.scheme match {
      case Annotation(Expression(Variable(name, index)), t) if index == 0 => Lambda(name, t, other)
      case _ => throw new Exception(s"Invalid lambda in DSL: base must be an Annotation but is ${this.toDhall}: $this")
    }
  }

  object Expression {
    implicit def toExpressionScheme(expression: Expression): ExpressionScheme[Expression] = expression.scheme

    def v(name: String): Expression = Expression(Variable(VarName(name), 0))
  }

  sealed trait PathComponent {
    def isOptionalLabel: Boolean = false
  }

  object PathComponent {
    final case class Label(fieldName: FieldName) extends PathComponent

    final case object DescendOptional extends PathComponent {
      override def isOptionalLabel: Boolean = true
    }
  }

  // Raw record syntax: { x.y.z = 1 } that needs to be processed further. This is a part of a RecordLiteral but not an Expression.
  final case class RawRecordLiteral(base: FieldName, defs: Option[(Seq[FieldName], Expression)])

}
