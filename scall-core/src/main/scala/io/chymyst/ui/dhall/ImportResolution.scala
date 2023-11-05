package io.chymyst.ui.dhall

import io.chymyst.ui.dhall.ImportResolution.ImportContext
import io.chymyst.ui.dhall.ImportResolutionResult._
import io.chymyst.ui.dhall.Syntax.Expression
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.SyntaxConstants.ImportType.{Path, Remote}
import io.chymyst.ui.dhall.SyntaxConstants.{Builtin, ConstructorName, FieldName, FilePrefix, ImportMode, ImportType, Operator, URL}

import java.nio.file.{Files, Paths}
import scala.util.{Success, Try}

object ImportResolution {

  def chainWith(parent: ImportType[Expression], child: ImportType[Expression]): ImportType[Expression] = (parent, child) match {
    case (Remote(URL(scheme1, authority1, path1, query1), headers1), Path(FilePrefix.Here, path2)) => Remote(URL(scheme1, authority1, path1 chain path2, query1), headers1)
    case (Path(filePrefix, path1), Path(FilePrefix.Here, path2)) => Path(filePrefix, path1 chain path2)

    case (Remote(URL(scheme1, authority1, path1, query1), headers1), Path(FilePrefix.Parent, path2)) => Remote(URL(scheme1, authority1, path1 chainToParent path2, query1), headers1)
    case (Path(filePrefix, path1), Path(FilePrefix.Parent, path2)) => Path(filePrefix, path1 chainToParent path2)

    case _ => child
  }

  val corsHeader = "Access-Control-Allow-Origin"

  // If `None` there is no error.
  def corsComplianceError(parent: ImportType[Expression], child: ImportType[Expression], responseHeaders: Map[String, Seq[String]]): Option[String] = (parent, child) match {
    // TODO: report issue: what if parent = Remote but child = Path, does the cors judgment then always fail?
    case (Remote(URL(scheme1, authority1, path1, query1), headers1), Remote(URL(scheme2, authority2, path2, query2), headers2)) =>
      if (scheme1 == scheme2 && authority1 == authority2) None
      else responseHeaders.get(corsHeader) match {
        case Some(Seq("*")) => None
        case Some(Seq(other)) if other.toLowerCase == s"$scheme2://$authority2".toLowerCase => None
        case Some(_) => Some(s"Scheme or authority differs from parent $parent but CORS headers in child $child is $responseHeaders and does not allow importing")
        case None => Some(s"Scheme or authority differs from parent $parent but no CORS header in child $child, headers $responseHeaders")
      }
    case (Remote(URL(_, _, _, _), _), _) => Some(s"Remote parent $parent may not import a non-remote $child")
    case _ => None
  }

  val TextType = Expression(ExprBuiltin(Builtin.Text))
  val ListType = Expression(ExprBuiltin(Builtin.List))

  lazy val typeOfImportAsLocation: Expression = UnionType(Seq(
    (ConstructorName("Local"), Some(TextType)),
    (ConstructorName("Remote"), Some(TextType)),
    (ConstructorName("Environment"), Some(TextType)),
    (ConstructorName("Missing"), None),
  ))

  lazy val typeOfGenericHeadersForHost: Expression = Application(ListType, Expression(RecordType(Seq(
    (FieldName("mapKey"), TextType),
    (FieldName("mapValue"), TextType),
  ))))

  lazy val typeOfUserDefinedAlternativeHeadersForHost: Expression = Application(ListType, Expression(RecordType(Seq(
    (FieldName("header"), TextType),
    (FieldName("value"), TextType),
  ))))

  lazy val typeOfGenericHeadersForAllHosts: Expression = Application(ListType, Expression(RecordType(Seq(
    (FieldName("mapKey"), TextType),
    (FieldName("mapValue"), typeOfGenericHeadersForHost),
  ))))

  def readCached(digest: BytesLiteral): Option[Expression] = (for {
    cacheRoot <- dhallCacheRoot
    bytes <- Try(Files.readAllBytes(cacheRoot.resolve("1220" + digest.hex.toLowerCase)))
    model <- Try(CBORmodel.decodeCbor1(bytes))
    expr <- Try(toExpression(model.toScheme))
  } yield expr).toOption

  def cacheResolved(expr: Expression, digest: Option[BytesLiteral]): ImportResolutionResult[Expression] = digest match {
    case None => Resolved(expr)
    case Some(BytesLiteral(hex)) =>
      val ourBytes = expr.alphaNormalized.betaNormalized.toCBORmodel.encodeCbor1
      val ourHash = Semantics.computeHash(ourBytes)
      if (hex.toLowerCase == ourHash) {
        dhallCacheRoot.foreach { cachePath =>
          Try(Files.write(cachePath.resolve("1220" + ourHash), ourBytes))
        }
        Resolved(expr)
      } else PermanentFailure(Seq(s"sha-256 mismatch: found $ourHash from expression ${expr.alphaNormalized.betaNormalized.toDhall} instead of specified $hex"))
  }

  lazy val isWindowsOS: Boolean = System.getProperty("os.name").toLowerCase.contains("windows")

  private def dhallCacheRoot: Try[java.nio.file.Path] = Try {
    val cacheHome = Paths.get(scala.sys.env("XDG_CACHE_HOME") + "/dhall")
    if (Files.isReadable(cacheHome) && Files.isWritable(cacheHome)) cacheHome else throw new Exception(s"Path $cacheHome is not readable or not writable")
  } orElse Try {
    val cacheHome = Paths.get(if (isWindowsOS) scala.sys.env("LOCALAPPDATA") + "/dhall" else System.getenv("user.home" + ".cache/dhall"))
    if (Files.isReadable(cacheHome) && Files.isWritable(cacheHome)) cacheHome else throw new Exception(s"Path $cacheHome is not readable or not writable")
  }

  final case class ImportContext(resolved: Map[Import[Expression], Expression])

  // Recursively resolve imports. See https://github.com/dhall-lang/dhall-lang/blob/master/standard/imports.md
  // We will use `traverse` on `ExpressionScheme` with this Kleisli function, in order to track changes in the resolution context.
  // TODO: report issue to mention in imports.md (at the end) that the resolution context must be threaded through, while resolving subexpressions.
  def resolveImports(expr: Expression): ImportResolutionMonad[Expression] = ImportResolutionMonad[Expression] { case state0@ImportResolutionState(visited, gamma) =>
    expr.scheme match {
      case i@Import(importType, importMode, digest) => importMode match {
        case ImportMode.Location => ???
        case ImportMode.Code => ???
        case ImportMode.RawBytes => ???
        case ImportMode.RawText => ???
      }
        importType match {
          case ImportType.Missing => (TransientFailure(Seq("import designated as `missing`")), state0)
          case Remote(url, headers) => ???
          case Path(filePrefix, file) => ???
          case ImportType.Env(envVarName) => ???
        }

      // Try resolving `lop`. If failed non-permanently, try resolving `rop`. Accumulate error messages.
      case ExprOperator(lop, Operator.Alternative, rop) =>
        resolveImports(lop).run(state0) match {
          case resolved@(Resolved(_), _) => resolved

          case failed@(PermanentFailure(_), _) => failed

          case (TransientFailure(messages1), state1) => resolveImports(rop).run(state1) match {
            case resolved@(Resolved(_), _) => resolved
            case (PermanentFailure(messages2), state2) => (PermanentFailure(messages1 ++ messages2), state2)
            case (TransientFailure(messages2), state2) => (TransientFailure(messages1 ++ messages2), state2)
          }

        }

      case _ => expr.scheme.traverse(resolveImports).run(state0) match {
        case (scheme, state) => (scheme.map(Expression.apply), state)
      }
    }
  }

}

final case class ImportResolutionState(visited: Seq[Import[Expression]] /* non-empty */ , gamma: ImportContext)

// Import resolution may fail either in a way that may be recovered via `?`, or in a way that disallows further attempts via `?`.
sealed trait ImportResolutionResult[+E] {
  def map[H](f: E => H): ImportResolutionResult[H] = this match {
    case Resolved(expr) => Resolved(f(expr))
    case failure: ImportResolutionResult[Nothing] => failure
  }
}

object ImportResolutionResult {
  type ResolutionErrors = Seq[String]

  final case class TransientFailure(messages: ResolutionErrors) extends ImportResolutionResult[Nothing]

  final case class PermanentFailure(messages: ResolutionErrors) extends ImportResolutionResult[Nothing]

  final case class Resolved[E](expr: E) extends ImportResolutionResult[E]
}

final case class ImportResolutionMonad[+E](run: ImportResolutionState => (ImportResolutionResult[E], ImportResolutionState))

object ImportResolutionMonad {
  implicit val ApplicativeIRMonad: Applicative[ImportResolutionMonad] = new Applicative[ImportResolutionMonad] {
    override def zip[A, B](fa: ImportResolutionMonad[A], fb: ImportResolutionMonad[B]): ImportResolutionMonad[(A, B)] =
      ImportResolutionMonad[(A, B)] { s0 =>
        fa.run(s0) match {
          case (Resolved(a), s1) =>
            fb.run(s1) match {
              case (Resolved(b), s2) => (Resolved((a, b)), s2)
              case (failure: ImportResolutionResult[Nothing], s2) => (failure, s2)
            }
          case (failure: ImportResolutionResult[Nothing], s1) => (failure, s1)
        }
      }

    override def map[A, B](f: A => B)(fa: ImportResolutionMonad[A]): ImportResolutionMonad[B] =
      ImportResolutionMonad[B](s => fa.run(s) match {
        case (a, s) => (a.map(f), s)
      })

    override def pure[A](a: A): ImportResolutionMonad[A] =
      ImportResolutionMonad[A](s => (Resolved(a), s))
  }

}
