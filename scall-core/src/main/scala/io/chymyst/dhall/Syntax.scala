package io.chymyst.dhall

import enumeratum._
import io.chymyst.dhall.CBORmodel.CBytes
import io.chymyst.dhall.Grammar.{TextLiteralNoInterp, hexStringToByteArray}
import io.chymyst.dhall.Semantics.BetaNormalizingOptions
import io.chymyst.dhall.Syntax.Expression
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.SyntaxConstants.Operator.Plus
import io.chymyst.dhall.SyntaxConstants._
import io.chymyst.tc.Applicative.{ApplicativeId, ApplicativeOps, Id, seqOption, seqSeq, seqTuple2, seqTuple3}
import io.chymyst.tc.Monoid.MonoidSyntax
import io.chymyst.tc.{Applicative, Monoid}

import java.nio.file.Paths
import java.time.{LocalDate, LocalTime, ZoneOffset}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.language.implicitConversions
import scala.util.chaining.scalaUtilChainingOps

object SyntaxConstants {
  final case class VarName(name: String) extends AnyVal {
    def escape: String = if (
      (Grammar.simpleKeywordsSet contains name) || (Grammar.builtinSymbolNamesSet contains name) || (SyntaxConstants.Constant.namesToValuesMap.keySet contains name) || (name matches ".*[^-/_A-Za-z0-9].*")
    ) s"`$name`"
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

    case object Or                 extends Operator("||", 0)
    case object Plus               extends Operator("+", 4)
    case object TextAppend         extends Operator("++", 6)
    case object ListAppend         extends Operator("#", 7)
    case object And                extends Operator("&&", 1)
    case object CombineRecordTerms extends Operator("∧", 8)
    case object Prefer             extends Operator("⫽", 9)
    case object CombineRecordTypes extends Operator("⩓", 10)
    case object Times              extends Operator("*", 5)
    case object Equal              extends Operator("==", 2)
    case object NotEqual           extends Operator("!=", 3)
    case object Equivalent         extends Operator("≡", 12)
    case object Alternative        extends Operator("?", 11)
  }

  sealed abstract class Builtin(override val entryName: String) extends EnumEntry {
    def unary_~ : Expression = Expression(ExprBuiltin(this))
  }

  object Builtin extends Enum[Builtin] {
    case object Bool             extends Builtin("Bool")
    case object Bytes            extends Builtin("Bytes")
    case object Date             extends Builtin("Date")
    case object DateShow         extends Builtin("Date/show")
    case object Double           extends Builtin("Double")
    case object DoubleShow       extends Builtin("Double/show")
    case object Integer          extends Builtin("Integer")
    case object IntegerClamp     extends Builtin("Integer/clamp")
    case object IntegerNegate    extends Builtin("Integer/negate")
    case object IntegerShow      extends Builtin("Integer/show")
    case object IntegerToDouble  extends Builtin("Integer/toDouble")
    case object List             extends Builtin("List")
    case object ListBuild        extends Builtin("List/build")
    case object ListFold         extends Builtin("List/fold")
    case object ListHead         extends Builtin("List/head")
    case object ListIndexed      extends Builtin("List/indexed")
    case object ListLast         extends Builtin("List/last")
    case object ListLength       extends Builtin("List/length")
    case object ListReverse      extends Builtin("List/reverse")
    case object Natural          extends Builtin("Natural")
    case object NaturalBuild     extends Builtin("Natural/build")
    case object NaturalEven      extends Builtin("Natural/even")
    case object NaturalFold      extends Builtin("Natural/fold")
    case object NaturalIsZero    extends Builtin("Natural/isZero")
    case object NaturalOdd       extends Builtin("Natural/odd")
    case object NaturalShow      extends Builtin("Natural/show")
    case object NaturalSubtract  extends Builtin("Natural/subtract")
    case object NaturalToInteger extends Builtin("Natural/toInteger")
    case object None             extends Builtin("None")
    case object Optional         extends Builtin("Optional")
    case object Text             extends Builtin("Text")
    case object TextReplace      extends Builtin("Text/replace")
    case object TextShow         extends Builtin("Text/show")
    case object Time             extends Builtin("Time")
    case object TimeShow         extends Builtin("Time/show")
    case object TimeZone         extends Builtin("TimeZone")
    case object TimeZoneShow     extends Builtin("TimeZone/show")

    override def values = findValues
  }

  sealed trait Constant extends EnumEntry {
    def unary_~ : Expression = Expression(ExprConstant(this))

    def union(other: Constant): Constant = (this, other) match {
      case (Constant.Sort, _) | (_, Constant.Sort) => Constant.Sort
      case (Constant.Kind, _) | (_, Constant.Kind) => Constant.Kind
      case _                                       => Constant.Type
    }
  }

  object Constant extends Enum[Constant] {
    val values = findValues

    case object Type  extends Constant
    case object Kind  extends Constant
    case object Sort  extends Constant
    case object True  extends Constant
    case object False extends Constant
  }

  sealed abstract class ImportMode(val cborCode: Int) extends EnumEntry with HasCborCode[ImportMode, Int]

  object ImportMode extends Enum[ImportMode] with HasCborCodeDict[Int, ImportMode] {
    val values = findValues

    case object Code     extends ImportMode(0)
    case object RawBytes extends ImportMode(3)
    case object RawText  extends ImportMode(1)
    case object Location extends ImportMode(2)
  }

  sealed abstract class Scheme(val cborCode: Int, val defaultPort: Int) extends EnumEntry with HasCborCode[Scheme, Int]

  object Scheme extends Enum[Scheme] with HasCborCodeDict[Int, Scheme] {
    val values = findValues

    case object HTTP  extends Scheme(0, 80)
    case object HTTPS extends Scheme(1, 443)
  }

  sealed abstract class FilePrefix(val cborCode: Int, val prefix: String) extends EnumEntry with HasCborCode[FilePrefix, Int]

  object FilePrefix extends Enum[FilePrefix] with HasCborCodeDict[Int, FilePrefix] {
    val values = findValues

    case object Absolute extends FilePrefix(2, "/")   // /absolute/path/to/file
    case object Here     extends FilePrefix(3, "./")  // ./something relative to the current working directory
    case object Parent   extends FilePrefix(4, "../") // ./something relative to the parent working directory
    case object Home     extends FilePrefix(5, "~/")  // ~/something relative to the user's home directory
  }

  sealed abstract class ImportType[+E] {
    def map[H](f: E => H): ImportType[H] = this match {
      case ImportType.Remote(url, headers) => ImportType.Remote(url, headers map f)
      case _                               => this.asInstanceOf[ImportType[H]]
    }

    def traverse[F[_]: Applicative, H](f: E => F[H]): F[ImportType[H]] = this match {
      case ImportType.Remote(url, headers) => seqOption(headers.map(f)).map(headers => ImportType.Remote(url, headers))
      case _                               => Applicative[F].pure(this.asInstanceOf[ImportType[H]])
    }

    protected def safetyLevelRequired: Int

    // This import may depend on another import only if this import's safety level does not require greater safety than another import's.
    def allowedToImportAnother(anotherImportType: ImportType[_]): Boolean =
      this.safetyLevelRequired <= anotherImportType.safetyLevelRequired

    def remoteOrigin: Option[(Scheme, String)] = None

    def userHeaders: Option[E] = None
  }

  object ImportType {
    case object Missing extends ImportType[Nothing] {
      override def safetyLevelRequired: Int = 1 // The `Missing` import is always a failure and cannot import anything else.
    }

    final case class Remote[E](url: ImportURL, headers: Option[E]) extends ImportType[E] {
      override def safetyLevelRequired: Int = 0 // This can import itself or Missing.

      override def remoteOrigin: Option[(Scheme, String)] = Some(url.scheme, url.authority)

      override def userHeaders: Option[E] = headers
    }

    final case class ImportPath(filePrefix: FilePrefix, file: FilePath) extends ImportType[Nothing] {
      override def safetyLevelRequired: Int = -1 // This can import anything else.

      override def toString: String = filePrefix.prefix + file.toString

      def toJavaPath: java.nio.file.Path = {
        val initialPath = filePrefix match {
          case FilePrefix.Home => System.getProperty("user.home")
          case _               => filePrefix.prefix
        }
        Paths.get(initialPath, file.canonicalize.segments: _*)
        // file.canonicalize.segments.foldLeft(initialPath)((prev, segment) => prev.resolve(segment))
      }

    }

    final case class Env(envVarName: String) extends ImportType[Nothing] {
      override def safetyLevelRequired: Int = -1 // This can import anything else.
    }
  }

  // The authority of http://user@host:port/foo is stored as "user@host:port".
  // The query of ?foo=1&bar=true is stored as "foo=1&bar=true".
  final case class ImportURL(scheme: Scheme, authority: String, path: FilePath, query: Option[String]) {
    override def toString: String = httpAuthority + "/" + path.toString + (query match {
      case Some(value) => "?" + value
      case None        => ""
    })

    def httpAuthority: String = scheme.entryName.toLowerCase + "://" + authority
  }

  // This is called `File` in the Dhall standard.
  // The last segment is a file name (but may be a empty string), all previous segments are path components (may be none).
  final case class FilePath(segments: Seq[String]) {
    require(segments.nonEmpty)

    override def toString: String = segments.mkString("/")

    // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/imports.md
    def canonicalize: FilePath = {
      val newSegments: Seq[String] = segments.foldLeft(List[String]()) { (prev, segment) =>
        segment match {
          case "."                                       => prev
          case ".." if prev.headOption.exists(_ != "..") => prev.tail
          case s                                         => s :: prev
        }
      }
      if (newSegments.isEmpty) FilePath(Seq("")) else FilePath(newSegments.reverse)
    }

    def chain(child: FilePath): FilePath = if (segments.isEmpty) child else FilePath(segments.init ++ child.segments)

    def chainToParent(child: FilePath): FilePath = chain(FilePath(".." +: child.segments))
  }

  object FilePath {
    def of(segments: Seq[String]): FilePath = if (segments.isEmpty) FilePath(Seq("")) else FilePath(segments)

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

    def urlEscapePathSegment(segment: String): String =
      Seq(":", ",", "?", "#", "[", "]", "@", "!", "$", "&", "'", "(", ")", "*", "+", ",", ";", "=").foldLeft(segment) { (prev, s) =>
        prev.replace(s, String.format("%%%2H", s))
      }
  }
}

object Syntax {
  def print1(expr: Expression): String = dhallForm1(0, IndexedSeq(Left((0, expr, TermPrecedence.min))), Map())

  private def inPrecedence(exprDhallForm: String, innerPrec: Int, outerPrec: Int): String = if (innerPrec < outerPrec) s"($exprDhallForm)" else exprDhallForm

  @tailrec private def dhallForm1(
    freshIndex: Int,
    pending: IndexedSeq[Either[(Int, Expression, Int), (Int, Set[Int], Map[Int, String] => String)]],
    results: Map[Int, String],
  ): String = {
    pending.lastOption match {
      case Some(last) =>
        val (newFresh: Int, newPending: IndexedSeq[Either[(Int, Expression, Int), (Int, Set[Int], Map[Int, String] => String)]], newResults: Map[Int, String]) =
          last match {
            case Left((indexToStore, expr, outerPrec)) =>
              // Helper functions to reduce boilerplate.
              def result(r: String) = (freshIndex + 1, IndexedSeq(), results.updated(indexToStore, inPrecedence(r, expr.scheme.precedence, outerPrec)))

              def more(storeResult: (Int => String) => String)(steps: (Expression, Int)*) = {
                val newPendingSteps                                                          = steps.toIndexedSeq.zipWithIndex.map { case ((e, p), i) => Left((freshIndex + i, e, p)) }
                val storageStep: Right[Nothing, (Int, Set[Int], Map[Int, String] => String)] =
                  Right(
                    (
                      indexToStore,
                      (freshIndex to freshIndex + steps.length).toSet,
                      m => inPrecedence(storeResult(i => m(freshIndex + i)), expr.precedence, outerPrec),
                    )
                  )
                (freshIndex + steps.length + 1, storageStep +: newPendingSteps, results)
              }

              val p    = expr.scheme.precedence
              val minP = TermPrecedence.min
              val appP = TermPrecedence.applicationPrecedence
              expr.scheme match {
                case Variable(name, index)                  => result(s"${name.escape}${if (index > 0) "@" + index.toString(10) else ""}")
                case Lambda(name, tipe, body)               =>
                  more(m => s"λ(${name.escape} : ${m(0)}) → ${m(1)}")((tipe, p), (body, p))
                case Forall(name, tipe, body)               =>
                  more(m => s"∀(${name.escape} : ${m(0)}) → ${m(1)}")((tipe, p), (body, p))
                case Let(name, tipe, subst, body)           =>
                  tipe match {
                    case Some(t) => more(m => s"let ${name.escape} : ${m(0)} = ${m(1)}\nin ${m(2)}")((t, p), (subst, p), (body, p))
                    case None    => more(m => s"let ${name.escape} = ${m(0)}\nin ${m(1)}")((subst, p), (body, p))
                  }
                case If(cond, ifTrue, ifFalse)              => more(m => s"if ${m(0)} then ${m(1)} else ${m(2)}")((cond, p), (ifTrue, p), (ifFalse, p))
                case Merge(record, update, tipe)            =>
                  tipe match {
                    case Some(t) => more(m => s"merge ${m(0)} ${m(1)} : ${m(2)}")((record, appP), (update, appP), (t, minP))
                    case None    => more(m => s"merge ${m(0)} ${m(1)}")((record, appP), (update, appP))
                  } // TODO: verify precedence of merge a b c where (merge a b) returns a function.
                case ToMap(data, tipe)                      =>
                  tipe match {
                    case Some(t) => more(m => s"toMap ${m(0)} : ${m(1)}")((data, appP), (t, minP))
                    case None    => more(m => s"toMap ${m(0)}")((data, appP))
                  }
                case EmptyList(tipe)                        => more(m => s"[] : ${m(0)}")((tipe, p))
                case NonEmptyList(exprs)                    => more(m => (exprs.indices).map(i => m(i)).mkString("[", ", ", "]"))(exprs.map(e => (e, TermPrecedence.min)): _*)
                case Annotation(data, tipe)                 => more(m => s"${m(0)} : ${m(1)}")((data, p), (tipe, p - 1))
                case ExprOperator(lop, op, rop)             => more(m => s"${m(0)} ${op.name} ${m(1)}")((lop, p), (rop, p))
                case Application(func, arg)                 =>
                  more(m => s"${m(0)} ${m(1)}")((func, p), (arg, p + 1)) // Application of Application must be in parentheses.
                case Field(base, name)                      => more(m => m(0) + "." + name.name)((base, p))
                case ProjectByLabels(base, labels)          => more(m => m(0) + "." + "{" + labels.map(_.name).mkString(", ") + "}")((base, p))
                case ProjectByType(base, by)                => more(m => m(0) + "." + "(" + m(1) + ")")((base, p), (by, p))
                case Completion(base, target)               => more(m => m(0) + " :: " + m(1))((base, p), (target, p))
                case Assert(assertion)                      => more(m => s"assert : ${m(0)}")((assertion, p))
                case With(data, pathComponents, body)       =>
                  more(m =>
                    m(0) + " with " + pathComponents
                      .map {
                        case PathComponent.Label(name)     => name.name
                        case PathComponent.DescendOptional => "?"
                      }.mkString(".") + " = " + m(1)
                  )((data, p), (body, p))
                case DoubleLiteral(value)                   => result(value.toString)
                case NaturalLiteral(value)                  => result(value.toString(10))
                case IntegerLiteral(value)                  => result((if (value >= 0) "+" else "") + value.toString(10))
                case TextLiteral(interpolations, trailing)  =>
                  more(m =>
                    "\"" + interpolations.toIndexedSeq.zipWithIndex.map { case ((prefix, _), i) => prefix + "${" + m(i) + "}" }.mkString + trailing + "\""
                  )(interpolations.map { case (_, expr) => (expr, p) }: _*)
                case BytesLiteral(hex)                      => result(s"0x\"$hex\"")
                case DateLiteral(year, month, day)          => result(f"$year%04d-$month%02d-$day%02d")
                case t @ TimeLiteral(_, _, _, _)            => result(t.toString)
                case t @ TimeZoneLiteral(_)                 => result(f"${if (t.isPositive) "+" else "-"}${t.hours}%02d:${t.minutes}%02d")
                case r @ RecordType(_)                      =>
                  if (r.defs.isEmpty) result("{}") // Special case.
                  else
                    more(m => r.sorted.defs.toIndexedSeq.zipWithIndex.map { case ((name, _), i) => name.name + " : " + m(i) }.mkString("{ ", ", ", " }"))(
                      r.sorted.defs.map { case (_, expr) => (expr, TermPrecedence.min) }: _*
                    )
                case r @ RecordLiteral(_)                   =>
                  if (r.defs.isEmpty) result("{=}") // Special case.
                  else
                    more(m => r.sorted.defs.toIndexedSeq.zipWithIndex.map { case ((name, _), i) => name.name + " = " + m(i) }.mkString("{ ", ", ", " }"))(
                      r.sorted.defs.map { case (_, expr) => (expr, TermPrecedence.min) }: _*
                    )
                case u @ UnionType(_)                       =>
                  if (u.defs.isEmpty) result("<>") // Special case.
                  else {
                    more { m =>
                      u.sorted.defs.toIndexedSeq.zipWithIndex
                        .map { case ((name, otipe), i) => name.name + otipe.map(_ => " : " + m(i)).getOrElse("") }.mkString("< ", " | ", " > ")
                    }(u.sorted.defs.map(_._2.getOrElse(Expression(ExprConstant(SyntaxConstants.Constant.Sort)))).map((_, TermPrecedence.min)): _*)
                    // The "Sort" will never be actually used but we don't have a NO-OP code in this mini-language.
                  }
                case ShowConstructor(data)                  => more(m => "showConstructor " + m(0))((data, p))
                case Import(importType, importMode, digest) =>
                  val maybeHeaders = importType.userHeaders match {
                    case Some(value) => Seq((value, TermPrecedence.min))
                    case None        => Seq()
                  }
                  more { m =>
                    val digestString     = digest.map(b => " sha256:" + b.hex.toLowerCase).getOrElse("")
                    val importModeString = importMode match {
                      case ImportMode.Code     => ""
                      case ImportMode.RawBytes => " as Bytes"
                      case ImportMode.RawText  => " as Text"
                      case ImportMode.Location => " as Location"
                    }
                    val importTypeString = importType match {
                      case ImportType.Missing              => "missing"
                      case ImportType.Remote(url, headers) =>
                        url.toString + (headers match {
                          case Some(value) => " using " + m(0)
                          case None        => ""
                        })
                      case p @ ImportType.ImportPath(_, _) => p.toString
                      case ImportType.Env(envVarName)      => "env:" + envVarName
                    }
                    importTypeString + digestString + importModeString
                  }(maybeHeaders: _*)
                case KeywordSome(data)                      => more(m => s"Some ${m(0)}")((data, p))
                case ExprBuiltin(builtin)                   => result(builtin.entryName)
                case ExprConstant(constant)                 => result(constant.entryName)
              }

            case Right((newIndex, toDelete, storeResult)) =>
              val newString = storeResult(results)
              (freshIndex + 1, IndexedSeq(), results.removedAll(toDelete).updated(newIndex, newString))

          }
        dhallForm1(newFresh, pending.init ++ newPending, newResults)

      case None => results(0) // The final result is always stored at key = 0.
    }
  }

  final case class DhallFile(shebangs: Seq[String], value: Expression)

  type Natural = BigInt
  type Integer = BigInt

  // Define a recursion scheme for Expression.
  sealed trait ExpressionScheme[+E] extends TermPrecedence {

    import ExpressionScheme._

    def map[H](f: E => H): ExpressionScheme[H] = {
      implicit def ff(e: E): H = f(e)

      implicit def ffOption(e: Option[E]): Option[H] = e map f

      implicit def fseq(e: Seq[E]): Seq[H] = e map f

      implicit def fseqE[A](e: Seq[(A, E)]): Seq[(A, H)] = e map { case (a, b) => (a, b) }

      implicit def fseqOption[A](e: Seq[(A, Option[E])]): Seq[(A, Option[H])] = e map { case (a, b) => (a, b) }

      implicit def flist[A](e: List[(A, E)]): List[(A, H)] = e map { case (a, b) => (a, b) }

      implicit def fImport(e: ImportType[E]): ImportType[H] = e map f

      this match {
        case Lambda(name, tipe, body)               => Lambda(name, tipe, body)
        case Forall(name, tipe, body)               => Forall(name, tipe, body)
        case Let(name, tipe, subst, body)           => Let(name, tipe, subst, body)
        case If(cond, ifTrue, ifFalse)              => If(cond, ifTrue, ifFalse)
        case Merge(record, update, tipe)            => Merge(record, update, tipe)
        case ToMap(data, tipe)                      => ToMap(data, tipe)
        case EmptyList(tipe)                        => EmptyList(tipe)
        case NonEmptyList(exprs)                    => NonEmptyList(exprs)
        case Annotation(data, tipe)                 => Annotation(data, tipe)
        case ExprOperator(lop, op, rop)             => ExprOperator(lop, op, rop)
        case Application(func, arg)                 => Application(func, arg)
        case Field(base, name)                      => Field(base, name)
        case ProjectByLabels(base, labels)          => ProjectByLabels(base, labels)
        case ProjectByType(base, by)                => ProjectByType(base, by)
        case Completion(base, target)               => Completion(base, target)
        case Assert(assertion)                      => Assert(assertion)
        case With(data, pathComponents, body)       => With(data, pathComponents, body)
        case TextLiteral(interpolations, trailing)  => TextLiteral(interpolations, trailing)
        case RecordType(defs)                       => RecordType(defs)
        case RecordLiteral(defs)                    => RecordLiteral(defs)
        case UnionType(defs)                        => UnionType(defs)
        case ShowConstructor(data)                  => ShowConstructor(data)
        case Import(importType, importMode, digest) => Import(importType, importMode, digest)
        case KeywordSome(data)                      => KeywordSome(data)
        case _                                      => this.asInstanceOf[ExpressionScheme[H]]
      }
    }

    def traverse[H, F[_]](f: E => F[H])(implicit ev: Applicative[F]): F[ExpressionScheme[H]] = {

      this match {
        case Lambda(name, tipe, body)               => seqTuple2(f(tipe), f(body)).map { case (tipe, body) => Lambda(name, tipe, body) }
        case Forall(name, tipe, body)               => seqTuple2(f(tipe), f(body)).map { case (tipe, body) => Forall(name, tipe, body) }
        case Let(name, tipe, subst, body)           =>
          seqTuple3(seqOption(tipe.map(f)), f(subst), f(body)).map { case (tipe, subst, body) => Let(name, tipe, subst, body) }
        case If(cond, ifTrue, ifFalse)              => seqSeq(Seq(cond, ifTrue, ifFalse).map(f)).map { case Seq(cond, ifTrue, ifFalse) => If(cond, ifTrue, ifFalse) }
        case Merge(record, update, tipe)            =>
          seqTuple3((f(record), f(update), seqOption(tipe.map(f)))).map { case (record, update, tipe) => Merge(record, update, tipe) }
        case ToMap(data, tipe)                      => seqTuple2((f(data), seqOption(tipe map f))).map { case (data, tipe) => ToMap(data, tipe) }
        case EmptyList(tipe)                        => f(tipe).map(EmptyList(_))
        case NonEmptyList(exprs)                    => seqSeq(exprs.map(f)).map(NonEmptyList(_))
        case Annotation(data, tipe)                 => seqTuple2(f(data), f(tipe)).map { case (data, tipe) => Annotation(data, tipe) }
        case ExprOperator(lop, op, rop)             => seqTuple2(f(lop), f(rop)).map { case (lop, rop) => ExprOperator(lop, op, rop) }
        case Application(func, arg)                 => seqTuple2(f(func), f(arg)).map { case (func, arg) => Application(func, arg) }
        case Field(base, name)                      => f(base).map(Field(_, name))
        case ProjectByLabels(base, labels)          => f(base).map(ProjectByLabels(_, labels))
        case ProjectByType(base, target)            => seqTuple2(f(base), f(target)).map { case (base, target) => ProjectByType(base, target) }
        case Completion(base, target)               => seqTuple2(f(base), f(target)).map { case (base, target) => Completion(base, target) }
        case Assert(assertion)                      => f(assertion).map(Assert(_))
        case With(data, pathComponents, body)       => seqSeq(Seq(data, body).map(f)).map { case Seq(data, body) => With(data, pathComponents, body) }
        case TextLiteral(interpolations, trailing)  =>
          seqSeq(interpolations.map { case (prefix, expr) => f(expr).map((prefix, _)) }).map(_.toList).map(TextLiteral(_, trailing))
        case RecordType(defs)                       => seqSeq(defs.map { case (field, expr) => f(expr).map((field, _)) }).map(RecordType(_))
        case RecordLiteral(defs)                    => seqSeq(defs.map { case (field, expr) => f(expr).map((field, _)) }).map(RecordLiteral(_))
        case UnionType(defs)                        => seqSeq(defs.map { case (field, expr) => seqOption(expr.map(f)).map((field, _)) }).map(UnionType(_))
        case ShowConstructor(data)                  => f(data).map(ShowConstructor(_))
        case Import(importType, importMode, digest) => importType.traverse(f).map(importType => Import(importType, importMode, digest))
        case KeywordSome(data)                      => f(data).map(KeywordSome(_))
        case _                                      => Applicative[F].pure(this.asInstanceOf[ExpressionScheme[H]])
      }
    }
    import scala.util.control.TailCalls._

    def traverseTC[H, F[_]](f: E => TailRec[F[H]])(implicit ev: Applicative[F]): TailRec[F[ExpressionScheme[H]]] = {
      type G[A] = TailRec[F[A]]
      implicit val ApplicativeG: Applicative[G] = new Applicative[G] {
        override def zip[A, B](fa: G[A], fb: G[B]): G[(A, B)] = for {
          a <- fa
          b <- fb
        } yield ev.zip(a, b)

        override def map[A, B](f: A => B)(fa: G[A]): G[B] = fa.map(_.map(f))

        override def pure[A](a: A): G[A] = done(ev.pure(a))
      }
      traverse[H, G](e => tailcall(f(e)))
    }

    def mapTC[H](f: E => TailRec[H]): TailRec[ExpressionScheme[H]] = {
      implicit val applicativeId: Applicative[Id] = ApplicativeId
      traverseTC[H, Id](e => tailcall(f(e)))
    }

  }

  object ExpressionScheme {
    val underscore = VarName("_")

    implicit def toExpression(s: ExpressionScheme[Expression]): Expression = Expression(s)

    implicit class ExprOpsString(name: String) {
      def unary_~ : Expression = Expression(Variable(VarName(name), BigInt(0)))
    }

    /*
    Precedence rules:

    - High precedence means high binding power. Example: `+` has precedence 4 and `*` has precedence 5.
    - If an operand has inner precedence x and outer precedence y, parentheses are needed if x < y.
    - Each operation has inner precedence and some surrounding precedence value. (Left and right must be taken the max of.)
    - Each operand has its own precedence.
    - An expression can have operands that are shielded from the left, from the right, or both. For example, in `[x, y]` the operand `x` is shielded from both sides by special symbols. In that case, we never need to add parentheses around x. This is as if the operand x's outer precedence were -Infinity. We also don't need to add parentheses around `[x, y]`. This is as if the inner precedence of `[ , ]` were +Infinity.
    - An atom (e.g., `1` or `x`) never needs parentheses. This is as if its inner precedence were +Infinity.
    - The outer expression never needs parentheses. This is as if its outer precedence were -Infinity.
    - If an expression shields operands only on one side but not on the other side:
         Example: (if cond then x else 1 + 1) + 1
         The half-shielded operands (or any other operands) never need parentheses but the entire expression always does, unless outer precedence is -Infinity. Its precedence is -Infinity.
     */

    sealed trait TermPrecedence {
      def precedence: Int = TermPrecedence.default
    }

    object TermPrecedence {
      def ofOperator(op: Operator): Int = ofOperator(op.cborCode)
      def ofOperator(prec: Int): Int    = offsetForOperators + prec * 2

      val high                  = 2000
      val applicationPrecedence = 1000
      val offsetForOperators    = 500
      val default               = 200
      val low                   = 100
      val max                   = 10000
      val min                   = 0
    }

    sealed trait VarPrecedence extends TermPrecedence {
      override def precedence: Int = TermPrecedence.max
    }

    sealed trait HighPrecedence extends TermPrecedence {
      override def precedence: Int = TermPrecedence.high
    }

    sealed trait ApplicationPrecedence extends TermPrecedence {
      override def precedence: Int = TermPrecedence.applicationPrecedence // Higher than any operators.
    }

    sealed trait MinPrecedence extends TermPrecedence {
      override def precedence: Int = TermPrecedence.min
    }

    final case class Variable(name: VarName, index: Natural) extends ExpressionScheme[Nothing] with VarPrecedence {
      override def equals(other: Any): Boolean = other.isInstanceOf[Variable] && {
        val otherVar = other.asInstanceOf[Variable]
        (otherVar.name equals name) && (otherVar.index equals index)
      }
    }

    final case class Lambda[E](name: VarName, tipe: E, body: E)                    extends ExpressionScheme[E] with MinPrecedence
    final case class Forall[E](name: VarName, tipe: E, body: E)                    extends ExpressionScheme[E] with MinPrecedence
    final case class Let[E](name: VarName, tipe: Option[E], subst: E, body: E)     extends ExpressionScheme[E] with MinPrecedence
    final case class If[E](cond: E, ifTrue: E, ifFalse: E)                         extends ExpressionScheme[E] with MinPrecedence
    final case class Merge[E](record: E, update: E, tipe: Option[E])               extends ExpressionScheme[E] with ApplicationPrecedence
    final case class ToMap[E](data: E, tipe: Option[E])                            extends ExpressionScheme[E] with ApplicationPrecedence
    final case class EmptyList[E](tipe: E)                                         extends ExpressionScheme[E] with MinPrecedence
    final case class NonEmptyList[E](exprs: Seq[E])                                extends ExpressionScheme[E] with HighPrecedence      {
      require(exprs.nonEmpty)
    }
    final case class Annotation[E](data: E, tipe: E)                               extends ExpressionScheme[E] with MinPrecedence
    final case class ExprOperator[E](lop: E, op: SyntaxConstants.Operator, rop: E) extends ExpressionScheme[E]                          {
      override def precedence: Int = TermPrecedence.ofOperator(op)
    }
    final case class Application[E](func: E, arg: E)                               extends ExpressionScheme[E] with ApplicationPrecedence
    final case class Field[E](base: E, name: FieldName)                            extends ExpressionScheme[E] with HighPrecedence
    // Note: `labels` may be an empty list.
    final case class ProjectByLabels[E](base: E, labels: Seq[FieldName])           extends ExpressionScheme[E] with HighPrecedence      {
      def sorted: ProjectByLabels[E] = ProjectByLabels(base, labels.sortBy(_.name))
    }
    final case class ProjectByType[E](base: E, by: E)                              extends ExpressionScheme[E] with HighPrecedence
    // An Expression of the form `T::r` is syntactic sugar for `(T.default // r) : T.Type`.
    final case class Completion[E](base: E, target: E)                             extends ExpressionScheme[E]                          {
      override def precedence: Int = TermPrecedence.ofOperator(13)
    }
    final case class Assert[E](assertion: E)                                       extends ExpressionScheme[E] with MinPrecedence
    final case class With[E](data: E, pathComponents: Seq[PathComponent], body: E) extends ExpressionScheme[E] with MinPrecedence       {
      require(pathComponents.nonEmpty)
    }
    // TODO: report issue: hash codes of DoubleLiteral(-0.0) and DoubleLiteral(+0.0) are the same even though hash codes of -0.0 and +0.0 are different.
    // Workaround: copy the hash of the Double value into the case class.
    final case class DoubleLiteral(value: Double, hash: Int)                       extends ExpressionScheme[Nothing] with VarPrecedence {
      override def equals(other: Any): Boolean = other.isInstanceOf[DoubleLiteral] && {
        val otherValue = other.asInstanceOf[DoubleLiteral].value
        (value == otherValue) || (value.isNaN && otherValue.isNaN)
      }

      def getValue = value
    }
    object DoubleLiteral {
      def apply(value: Double): DoubleLiteral = DoubleLiteral(value, value.hashCode)

      def unapply(x: DoubleLiteral): Option[Double] = Some(x.getValue)
    }
    final case class NaturalLiteral(value: Natural)                                extends ExpressionScheme[Nothing] with VarPrecedence {
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
    final case class IntegerLiteral(value: Integer)                                extends ExpressionScheme[Nothing] with VarPrecedence {
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
      def ofString[E](s: String)                              = TextLiteral[E](List(), s)
      def ofText[E](textLiteralNoInterp: TextLiteralNoInterp) = ofString[E](textLiteralNoInterp.value)
      def empty[E]                                            = ofString[E]("")
      def ofExpression[E](expr: E)                            = TextLiteral[E](interpolations = List(("", expr)), trailing = "")
    }

    final case class TextLiteral[+E](interpolations: List[(String, E)], trailing: String) extends ExpressionScheme[E] with VarPrecedence {
      def ++[G >: E, H <: G](other: TextLiteral[H]): TextLiteral[G] = other.interpolations match {
        case List()                       =>
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
              val l0        = headSplit.head // Guaranteed to exist.
              val ls        = headSplit.tail
              if (ls.isEmpty) loop(currentLine ++ TextLiteral[H](List((head, interpolation)), ""), TextLiteral(nextLine.interpolations.tail, trailing))
              else
                (currentLine ++ TextLiteral.ofString[H](l0)) +: (ls.init.map(TextLiteral.ofString[H]) ++ loop(
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
        val longestCommonIndent: String      = removeEmpty.map(_.whitespacePrefix).reduceRight(longestCommonPrefix)
        removeIndentsAndConcatenate(longestCommonIndent.length)
      }

      private def splitByAllNewlines(s: String): Seq[String] =
        s.split("\r\n", -1).flatMap(_.split("\n", -1)).toSeq.pipe(s => if (s.isEmpty) Seq("") else s)

      private def removeIndentsAndConcatenate(indent: Int): TextLiteral[E] = {
        def join(a: TextLiteral[E], b: TextLiteral[E]): TextLiteral[E] = a ++ TextLiteral.ofString("\n") ++ b

        def joinLines(lines: Seq[TextLiteral[E]]): TextLiteral[E] = lines.reduceRight(join)

        joinLines(lines.map(_.stripPrefix(indent))).escape
      }

      def stripPrefix(indent: Int): TextLiteral[E] = interpolations.headOption match {
        case Some((head, tail)) => copy(interpolations = (head.drop(indent), tail) +: interpolations.tail)
        case None               => copy(trailing = trailing.drop(indent))
      }

      def mapStrings(f: String => String): TextLiteral[E] =
        copy(interpolations = interpolations.map { case (head, tail) => (f(head), tail) }, trailing = f(trailing))

      private def reEscape(s: String): String = s.replace("'''", "''").replace("''${", "${")

      private def escape: TextLiteral[E] = mapStrings(reEscape)

    }

    // The hex string must be lowercase.
    final case class BytesLiteral(hex: String) extends ExpressionScheme[Nothing] with VarPrecedence {
      val bytes: Array[Byte] = hexStringToByteArray(hex)
    }

    object BytesLiteral {
      def of(hex: String) = BytesLiteral(hex.toUpperCase)

      def of(bytes: Array[Byte]) = BytesLiteral(CBytes.byteArrayToHexString(bytes))
    }

    final case class DateLiteral(year: Int, month: Int, day: Int) extends ExpressionScheme[Nothing] with VarPrecedence {
      lazy val toLocalDate: LocalDate = LocalDate.of(year, month, day)
    }

    final case class TimeLiteral(hours: Int, minutes: Int, seconds: Int, nanosPrinted: String) extends ExpressionScheme[Nothing] with VarPrecedence {
      lazy val cborTotalSeconds: BigInt = BigInt(seconds.toString + nanosPrinted)

      // This is a negative number such that cborTotalSeconds * math.pow(10, cborPrecision) = seconds + math.pow(10, -9)*nanoSeconds
      // and the number of digits in cborTotalSeconds must be the same as the length of nanosPrinted.
      val cborPrecision: Int = -nanosPrinted.length

      /** The Dhall `TimeLiteral` has arbitrary-precision nanoseconds. Truncate them for converting to `java.time.LocalTime`.
        */
      val nanosTruncated: Int = (nanosPrinted.take(9) + "0" * 9).take(9).toInt

      val toLocalTime: LocalTime = LocalTime.of(hours, minutes, seconds, nanosTruncated) // Do not make this lazy, in order to validate the time values.

      override def toString: String = f"$hours%02d:$minutes%02d:$seconds%02d${if (nanosPrinted.isEmpty) "" else "." + nanosPrinted}"
    }

    object TimeLiteral {
      def of(hours: Int, minutes: Int, totalSeconds: BigInt, precision: Int): TimeLiteral = {
        require(precision <= 0)
        val fracLength                  = -precision
        // Example: totalSeconds = 102003000 and precision = -8
        // We will compute seconds = 1 and secFraction = Some("02003000")
        val power                       = BigInt(10).pow(fracLength)
        val (secondsBigInt, fracBigInt) = totalSeconds /% power
        val seconds: Int                =
          if (secondsBigInt.isValidInt && secondsBigInt.intValue >= 0 && secondsBigInt.intValue <= 59)
            secondsBigInt.intValue
          else
            throw new Exception(
              s"Invalid TimeLiteral: totalSeconds = $totalSeconds, precision = $precision is inconsistent because seconds = $secondsBigInt and is not between 0 and 59"
            )
        if (fracLength == 0) TimeLiteral(hours, minutes, seconds, "")
        else {
          val fracBigIntString     = fracBigInt.toString(10)
          val leadingZeros: String = "0" * (fracLength - fracBigIntString.length)
          val secFraction          = leadingZeros + fracBigIntString
          TimeLiteral(hours, minutes, seconds, truncateSecondsFraction(secFraction))
        }
      }

      private def truncateSecondsFraction(secFraction: String): String = secFraction // secFraction.take(12) + "0" * math.max(0, secFraction.length - 12)

      def of(hours: Int, minutes: Int, seconds: Int, secFraction: String): TimeLiteral = {
        require(hours >= 0 && hours <= 59 && minutes >= 0 && minutes <= 59 && seconds >= 0 && seconds <= 59 && (secFraction matches "^[0-9]*$"))
        TimeLiteral(hours, minutes, seconds, truncateSecondsFraction(secFraction))
      }
    }

    final case class TimeZoneLiteral(totalMinutes: Int) extends ExpressionScheme[Nothing] with VarPrecedence {
      lazy val toZoneOffset: ZoneOffset = {
        val sign = totalMinutes.sign
        ZoneOffset.ofHoursMinutes(hours * sign, minutes * sign)
      }

      val hours: Int          = math.abs(totalMinutes) / 60
      val minutes: Int        = math.abs(totalMinutes) % 60
      val isPositive: Boolean = totalMinutes >= 0
    }

    final case class RecordType[E](defs: Seq[(FieldName, E)]) extends ExpressionScheme[E] with HighPrecedence {
      lazy val sorted = RecordType(defs.sortBy(_._1.name))

      def lookup(field: FieldName): Option[E] = defs.find(_._1 == field).map(_._2) // TODO: do we need a faster lookup here?
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
          case RawRecordLiteral(base, Some((fields, target))) =>
            (base, fields.foldRight(target) { (field, expr) => Expression(RecordLiteral(Seq((field, expr)))) })
        }

        // Desugar repeated field names { x = { y = 1 }, x = { z = 1 } } into { x = { y = 1 } ∧ { z = 1} }. This is needed at the top nested level only.
        def desugarRepetition(defs: Seq[(FieldName, Expression)]): Seq[(FieldName, Expression)] = {
          val recordMap: Map[FieldName, Expression] =
            defs.groupBy(_._1).map { case (field, subDefs) =>
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

      def lookup(field: ConstructorName): Option[Option[E]] = defs.find(_._1 == field).map(_._2) // TODO: do we need a faster lookup here?
    }

    final case class ShowConstructor[E](data: E) extends ExpressionScheme[E] with ApplicationPrecedence

    final case class Import[+E](importType: SyntaxConstants.ImportType[E], importMode: SyntaxConstants.ImportMode, digest: Option[BytesLiteral])
        extends ExpressionScheme[E] {
      override def precedence: Int = (importType, importMode) match {
        case (ImportType.Remote(_, Some(_)), ImportMode.Code) => TermPrecedence.min
        case _                                                => TermPrecedence.max
      }

      def canonicalize: Import[E] = importType match {
        case i @ ImportType.Remote(_, _)     =>
          val canonicalPath = i.url.path.canonicalize
          copy(importType = i.copy(url = i.url.copy(path = canonicalPath)))
        case i @ ImportType.ImportPath(_, _) =>
          val canonicalPath = i.file.canonicalize
          copy(importType = i.copy(file = canonicalPath))
        case _                               => this
      }
    }

    object Import {
      def chainWith[E](parent: Import[E], child: Import[E]): Import[E] =
        child.copy(importType = ImportResolution.chainWith(parent.importType, child.importType))

      implicit def ofJavaPath(path: java.nio.file.Path): Import[Nothing] = {
        val prefix = if (path.isAbsolute) FilePrefix.Absolute else FilePrefix.Here
        Import(ImportType.ImportPath(prefix, SyntaxConstants.FilePath(path.iterator.asScala.toSeq.map(_.toString))), ImportMode.Code, digest = None)
      }

      implicit def ofJavaFile(file: java.io.File): Import[Nothing] = ofJavaPath(file.toPath)

      implicit def ofString(fileName: String): Import[Nothing] = ofJavaPath(Paths.get(fileName))
    }
    final case class KeywordSome[E](data: E) extends ExpressionScheme[E] with ApplicationPrecedence
    final case class ExprBuiltin(builtin: SyntaxConstants.Builtin)    extends ExpressionScheme[Nothing] with VarPrecedence
    final case class ExprConstant(constant: SyntaxConstants.Constant) extends ExpressionScheme[Nothing] with VarPrecedence
  }

  final case class Expression(scheme: ExpressionScheme[Expression]) {
    lazy val exprCount: Int = {
      implicit val monoidInt: Monoid[Int]                         = new Monoid[Int] {
        override def empty: Int = 1

        override def combine(a: Int, b: Int): Int = a + b
      }
      implicit val monoidConst: Applicative[Monoid.Const[Int, *]] = Monoid.trivialApplicative[Int]
      traverseRecursive[Monoid.Const[Int, *]] { a => 1 }.result
    }

    import scala.util.control.TailCalls._

    def traverseRecursive[F[_]: Applicative](f: Expression => F[Expression]): TailRec[F[Expression]] =
      scheme.traverseTC[Expression, F](e => tailcall(e.traverseRecursive(f))).map(_.map(Expression.apply))

    /*def uniqueSubexpressionReferences: Expression = {
      val t: UniqueReferences[Expression] = traverseRecursive[UniqueReferences](UniqueReferences.make)
      val u: (Expression, mutable.Set[Expression]) = t.run(mutable.Set())
      u._1
    }*/

    def resolveImports(currentFile: java.nio.file.Path = Paths.get(".")): Expression = ImportResolution.resolveAllImports(this, currentFile)

    def op(operator: Operator)(arg: Expression): Expression = Expression(ExprOperator(scheme, operator, arg))

    def toCBORmodel: CBORmodel = CBOR.toCborModel(scheme)

    def inferType: TypecheckResult[Expression] = TypeCheck.inferType(TypeCheck.emptyContext, this)

    def inferTypeWith(gamma: TypeCheck.KnownVars): TypecheckResult[Expression] = TypeCheck.inferType(gamma, this)

    /*
    The main user-facing function is typeCheckAndBetaNormalize() because betaNormalize is not safe without type-checking.

    inferType() returns a possibly modified expression whose full type has been inferred.
    Sub-expressions will be annotated with a type context `gamma`.

    expr.typeCheckAndBetaNormalize() calls Semantics.betaNormalizeAndExpand(expr, default options).
    Semantics.betaNormalizeAndExpand(expr, options) checks the cache and, if needed, calls betaNormalizeUncached(expr, options).
    betaNormalizeUncached(expr, options) performs pattern-matching on expr and sometimes calls betaNormalizeOrUnexpand(expr, options) with different options.
    betaNormalizeOrUnexpand(expr, options) will again check the cache. If shortcut was taken, it will not cache the result.
    TODO: simplify that logic
    TODO: make betaNormalize() stack-safe using TailRec
     */

    def typeCheckAndBetaNormalize(gamma: TypeCheck.KnownVars = TypeCheck.KnownVars.empty): TypecheckResult[Expression] =
      TypeCheck.inferType(gamma, this).map(_ => Semantics.betaNormalizeAndExpand(this, BetaNormalizingOptions.default))

    def inferAndValidateTypeWith(gamma: TypeCheck.KnownVars): TypecheckResult[Expression] = for {
      t <- TypeCheck.inferType(gamma, this)
      _ <- TypeCheck.inferType(gamma, t)
    } yield t

    def toPrimitiveValue: Option[AnyRef] = scheme match {
      case EmptyList(_)                 => Some(Nil)
      case NonEmptyList(defs)           =>
        val decoded = defs.map(_.toPrimitiveValue)
        if (decoded.forall(_.nonEmpty)) Some(decoded.flatten) else None
      case DoubleLiteral(d)             => Some(java.lang.Double.valueOf(d))
      case NaturalLiteral(n)            => Some(n)
      case IntegerLiteral(n)            => Some(n)
      case TextLiteral(Nil, trailing)   => Some(trailing)
      case b @ BytesLiteral(_)          => Some(b.bytes)
      case DateLiteral(y, m, d)         => Some(LocalDate.of(y, m, d))
      case t @ TimeLiteral(_, _, _, _)  => Some(t.toLocalTime)
      case TimeZoneLiteral(t)           => Some(ZoneOffset.ofTotalSeconds(t * 60))
      case ExprConstant(Constant.True)  => Some(java.lang.Boolean.valueOf(true))
      case ExprConstant(Constant.False) => Some(java.lang.Boolean.valueOf(false))
      case KeywordSome(data)            => data.toPrimitiveValue.map(Some.apply)
      case RecordLiteral(defs)          =>
        val decoded = defs.map { case (fieldName, e) => e.toPrimitiveValue.map(v => (fieldName.name -> v)) }
        if (decoded.forall(_.nonEmpty)) Some(decoded.flatten.toMap) else None
      case _                            => None
    }

    // TODO: count usages of these lazy vals and determine if they are actually important for efficiency
    lazy val alphaNormalized: Expression = Semantics.alphaNormalize(this)
    lazy val betaNormalized: Expression  = Semantics.betaNormalizeAndExpand(this, BetaNormalizingOptions.default)

    /** Print `this` to Dhall syntax.
      *
      * @return
      *   A string representation of `this` expression in (valid but only approximately standard) Dhall syntax.
      */
    lazy val print: String = Syntax.print1(this)

    private val dummyHashCode = 1234567890

    private def hashCodeTC: TailRec[Int] =
      scheme
        .mapTC[Int](e => tailcall(e.hashCodeTC)) // Produce TailRec[ExpressionScheme[Int]].
        .map(_.hashCode)                         // Produce TailRec[Int] using non-recursive ExpressionScheme#hashCode().

    // We don't fail the test "avoid expanding Natural/fold" when hashCode is overloaded with tail recursion.
    override def hashCode(): Int = {
      hashCodeTC.result
    }

    override def toString: String = {
      val result = print
      if (result.length > 256) result.take(256) + s" ... (${result.length - 256} characters omitted)" else result
    }
    /*
    @inline private def inPrecedence(level: Int) = if (scheme.precedence < level) "(" + dhallForm + ")" else dhallForm

    private lazy val dhallForm: String = {
      val p    = scheme.precedence
      val minP = TermPrecedence.min
      val appP = TermPrecedence.applicationPrecedence
      scheme match {
        case Variable(name, index)                  => s"${name.escape}${if (index > 0) "@" + index.toString(10) else ""}"
        case Lambda(name, tipe, body)               => s"λ(${name.escape} : ${tipe.inPrecedence(p)}) → ${body.inPrecedence(p)}"
        case Forall(name, tipe, body)               => s"∀(${name.escape} : ${tipe.inPrecedence(p)}) → ${body.inPrecedence(p)}"
        case Let(name, tipe, subst, body)           =>
          s"let ${name.escape} ${tipe.map(t => ": " + t.inPrecedence(p)).getOrElse("")} = ${subst.inPrecedence(p)}\nin ${body.inPrecedence(p)}"
        case If(cond, ifTrue, ifFalse)              => s"if ${cond.inPrecedence(p)} then ${ifTrue.inPrecedence(p)} else ${ifFalse.inPrecedence(p)}"
        case Merge(record, update, tipe)            =>                                                       // TODO: verify precedence of merge a b c where (merge a b) returns a function.
          "merge " + record.inPrecedence(appP) + " " + update.inPrecedence(appP) + (tipe match {
            case Some(value) => " : " + value.inPrecedence(minP)
            case None        => ""
          })
        case ToMap(data, tipe)                      =>
          "toMap " + data.inPrecedence(appP) + (tipe match {
            case Some(value) => " : " + value.inPrecedence(minP)
            case None        => ""
          })
        case EmptyList(tipe)                        => s"[] : ${tipe.inPrecedence(p)}"
        case NonEmptyList(exprs)                    => exprs.map(_.inPrecedence(TermPrecedence.min)).mkString("[", ", ", "]")
        case Annotation(data, tipe)                 => s"${data.inPrecedence(p)} : ${tipe.inPrecedence(p - 1)}"
        case ExprOperator(lop, op, rop)             => s"${lop.inPrecedence(p)} ${op.name} ${rop.inPrecedence(p)}"
        case Application(func, arg)                 => s"${func.inPrecedence(p)} ${arg.inPrecedence(p + 1)}" // Application of Application must be in parentheses.
        case Field(base, name)                      => base.inPrecedence(p) + "." + name.name
        case ProjectByLabels(base, labels)          => base.inPrecedence(p) + "." + "{" + labels.map(_.name).mkString(", ") + "}"
        case ProjectByType(base, by)                => base.inPrecedence(p) + "." + "(" + by.inPrecedence(p) + ")"
        case Completion(base, target)               => base.inPrecedence(p) + " :: " + target.inPrecedence(p)
        case Assert(assertion)                      => s"assert : ${assertion.inPrecedence(p)}"
        case With(data, pathComponents, body)       =>
          data.inPrecedence(p) + " with " + pathComponents
            .map {
              case PathComponent.Label(name)     => name.name
              case PathComponent.DescendOptional => "?"
            }.mkString(".") + " = " + body.inPrecedence(p)
        case DoubleLiteral(value)                   => value.toString
        case NaturalLiteral(value)                  => value.toString(10)
        case IntegerLiteral(value)                  => (if (value >= 0) "+" else "") + value.toString(10)
        case TextLiteral(interpolations, trailing)  =>
          "\"" + interpolations.map { case (prefix, expr) => prefix + "${" + expr.inPrecedence(p) + "}" }.mkString + trailing + "\""
        case BytesLiteral(hex)                      => s"0x\"$hex\""
        case DateLiteral(year, month, day)          => f"$year%04d-$month%02d-$day%02d"
        case t @ TimeLiteral(_, _, _, _)            => t.toString
        case t @ TimeZoneLiteral(_)                 => f"${if (t.isPositive) "+" else "-"}${t.hours}%02d:${t.minutes}%02d"
        case r @ RecordType(_)                      =>
          if (r.defs.isEmpty) "{}" // Special case.
          else
            r.sorted.defs.map { case (name, expr) => name.name + " : " + expr.inPrecedence(TermPrecedence.min) }.mkString("{ ", ", ", " }")
        case r @ RecordLiteral(_)                   =>
          if (r.defs.isEmpty) "{=}" // Special case.
          else r.sorted.defs.map { case (name, expr) => name.name + " = " + expr.inPrecedence(TermPrecedence.min) }.mkString("{ ", ", ", " }")
        case u @ UnionType(_)                       =>
          "< " + u.sorted.defs
            .map { case (name, expr) => name.name + expr.map(_.inPrecedence(TermPrecedence.min)).map(": " + _).getOrElse("") }.mkString(" | ") + " > "
        case ShowConstructor(data)                  => "showConstructor " + data.inPrecedence(p)
        case Import(importType, importMode, digest) =>
          val digestString     = digest.map(b => " sha256:" + b.hex.toLowerCase).getOrElse("")
          val importModeString = importMode match {
            case ImportMode.Code     => ""
            case ImportMode.RawBytes => " as Bytes"
            case ImportMode.RawText  => " as Text"
            case ImportMode.Location => " as Location"
          }
          val importTypeString = importType match {
            case ImportType.Missing              => "missing"
            case ImportType.Remote(url, headers) =>
              url.toString + (headers match {
                case Some(value) => " using " + value.inPrecedence(TermPrecedence.min)
                case None        => ""
              })
            case p @ ImportType.ImportPath(_, _) => p.toString
            case ImportType.Env(envVarName)      => "env:" + envVarName
          }
          importTypeString + digestString + importModeString
        case KeywordSome(data)                      => s"Some ${data.inPrecedence(p)}"
        case ExprBuiltin(builtin)                   => builtin.entryName
        case ExprConstant(constant)                 => constant.entryName
      }
    }
     */

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
      case _                                                              => throw new Exception(s"Invalid lambda in DSL: base must be an Annotation with zero-index variable but is ${this.print}: $this")
    }

    // Forall type expressions. The argument must be an annotation.
    def ->:(tipe: Expression): Expression = tipe.scheme match {
      case Annotation(Expression(Variable(name, index)), t) if index == 0 => Forall(name, t, this)
      case _                                                              => Forall(underscore, tipe, this)
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

    case object DescendOptional extends PathComponent {
      override def isOptionalLabel: Boolean = true
    }
  }

  // Raw record syntax: { x.y.z = 1 } that needs to be processed further. This is a part of a RecordLiteral but not an Expression.
  final case class RawRecordLiteral(base: FieldName, defs: Option[(Seq[FieldName], Expression)])

}
