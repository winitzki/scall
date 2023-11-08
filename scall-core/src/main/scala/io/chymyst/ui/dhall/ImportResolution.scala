package io.chymyst.ui.dhall

import fastparse.Parsed
import geny.Generator.from
import io.chymyst.ui.dhall.CBORmodel.CBytes
import io.chymyst.ui.dhall.ImportResolution.{ImportContext, dhallCacheRoots}
import io.chymyst.ui.dhall.ImportResolutionResult._
import io.chymyst.ui.dhall.Parser.InlineDhall
import io.chymyst.ui.dhall.Syntax.{DhallFile, Expression}
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.SyntaxConstants.ImportType.{Path, Remote}
import io.chymyst.ui.dhall.SyntaxConstants.{Builtin, ConstructorName, FieldName, FilePrefix, ImportMode, ImportType, Operator, URL}

import java.nio.file.{Files, Paths}
import scala.util.{Failure, Success, Try}

object ImportResolution {
  // TODO: missing sha256:... should be resolved if cache is available.
  def chainWith[E](parent: ImportType[E], child: ImportType[E]): ImportType[E] = (parent, child) match {
    case (Remote(URL(scheme1, authority1, path1, query1), headers1), Path(FilePrefix.Here, path2)) => Remote(URL(scheme1, authority1, path1 chain path2, query1), headers1)
    case (Path(filePrefix, path1), Path(FilePrefix.Here, path2)) => Path(filePrefix, path1 chain path2)

    case (Remote(URL(scheme1, authority1, path1, query1), headers1), Path(FilePrefix.Parent, path2)) => Remote(URL(scheme1, authority1, path1 chainToParent path2, query1), headers1)
    case (Path(filePrefix, path1), Path(FilePrefix.Parent, path2)) => Path(filePrefix, path1 chainToParent path2)

    case _ => child
  }

  val corsHeader = "Access-Control-Allow-Origin"

  // This function returns `None` if there is no error in CORS compliance.
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

  def readCached(cacheRoot: java.nio.file.Path, digest: BytesLiteral): Try[Expression] = for {
    bytes <- Try(Files.readAllBytes(cacheRoot.resolve("1220" + digest.hex.toLowerCase)))
    ourHash <- Try(CBytes.byteArrayToHexString(bytes))
    if ourHash.toLowerCase == digest.hex.toLowerCase // TODO: alert user if this fails
    expr <- Try(CBORmodel.decodeCbor1(bytes).toScheme: Expression)
  } yield expr

  def readFirstCached(digest: BytesLiteral): Option[Expression] =
    dhallCacheRoots.map(readCached(_, digest))
      .filter(_.isSuccess)
      .take(1).map(_.toOption).headOption.flatten // Force evaluation of the first valid operation over all candidate cache roots.

  def validateHashAndCacheResolved(expr: Expression, digest: Option[BytesLiteral]): ImportResolutionResult[Expression] = digest match {
    case None => Resolved(expr)

    case Some(BytesLiteral(hex)) =>
      val ourBytes = expr.alphaNormalized.betaNormalized.toCBORmodel.encodeCbor1
      val ourHash = Semantics.computeHash(ourBytes).toLowerCase
      if (hex.toLowerCase == ourHash) {
        dhallCacheRoots.map { cachePath =>
            Try(Files.write(cachePath.resolve("1220" + ourHash), ourBytes))
            // TODO: log errors while writing the cache file
          }.filter(_.isSuccess)
          .take(1).headOption // Force evaluation of the first valid operation over all candidate cache roots.
        Resolved(expr)
      } else PermanentFailure(Seq(s"sha-256 mismatch: found $ourHash from expression ${expr.alphaNormalized.betaNormalized.toDhall} instead of specified $hex"))
  }

  lazy val isWindowsOS: Boolean = System.getProperty("os.name").toLowerCase.contains("windows")

  private def createAndCheckReadableWritable(path: java.nio.file.Path): Try[java.nio.file.Path] = Try {
    Files.createDirectories(path)
    if (Files.isReadable(path) && Files.isWritable(path)) path else throw new Exception(s"Path $path is not readable or not writable")
  }

  private def dhallCacheRoots: Iterator[java.nio.file.Path] = Seq(
    Try(Paths.get(scala.sys.env("XDG_CACHE_HOME")).resolve("dhall")),
    Try(if (isWindowsOS) Paths.get(scala.sys.env("LOCALAPPDATA")).resolve("dhall")
    else Paths.get(System.getProperty("user.home")).resolve(".cache").resolve("dhall")),
  ).iterator.map(_.flatMap(createAndCheckReadableWritable))
    .collect { case Success(path) => path }

  final case class ImportContext(resolved: Map[Import[Expression], Expression]) {
    override def toString: String = resolved.map { case (k, v) => k.toDhall + " -> " + v.toDhall }.mkString("Map(", ", ", ")")
  }

  def httpHeaders(headers: Option[Expression]): Iterable[(String, String)] = headers match {
    case Some(Expression(_)) => ???
    case None => Seq()
  }

  // TODO report issue - imports.md does not say how to bootstrap reading a dhall expression, what is the initial "parent" import?
  // TODO workaround: allow the "visited" list to be empty initially
  def resolveAllImports(expr: Expression, currentDir: java.nio.file.Path): Expression = {
    val initialVisited = Import[Expression](
      ImportType.Path(FilePrefix.Here, SyntaxConstants.File(Seq(""))),
      ImportMode.Code,
      None
    )
    val initState =   ImportContext(Map())
    resolveImportsStep(expr, Seq(initialVisited), currentDir).run(initState) match {
      case (resolved, finalState) => resolved match {
        case TransientFailure(messages) => throw new Exception(s"Transient failure resolving ${expr.toDhall}: $messages")
        case PermanentFailure(messages) => throw new Exception(s"Permanent failure resolving ${expr.toDhall}: $messages")
        case Resolved(r) => r
      }
    }
  }

  def printVisited(visited: Seq[Import[Expression]]): String = visited.map(_.toDhall).mkString("[", ", ", "]")

  // Recursively resolve imports. See https://github.com/dhall-lang/dhall-lang/blob/master/standard/imports.md
  // We will use `traverse` on `ExpressionScheme` with this Kleisli function, in order to track changes in the resolution context.
  // TODO: report issue to mention in imports.md (at the end) that the updates of the resolution context must be threaded through while resolving subexpressions.
  def resolveImportsStep(expr: Expression, visited: Seq[Import[Expression]], currentDir: java.nio.file.Path): ImportResolutionStep[Expression] = ImportResolutionStep[Expression] { case state0@ImportContext( gamma) =>
    println(s"DEBUG 0 resolveImportsStep(${expr.toDhall.take(160)}${if (expr.toDhall.length > 160) "..." else ""}, currentDir=${currentDir.toAbsolutePath.toString} with initial $state0")
    expr.scheme match {
      case i@Import(_, _, _) =>
        val (parent, child, referentialCheck) = visited.lastOption match {
          case Some(parent) =>
            val child = (parent chainWith i).canonicalize
            val referentialCheck = if (parent.importType allowedToImportAnother child.importType) Right(()) else Left(PermanentFailure(Seq(s"parent import expression ${parent.toDhall} may not import child ${child.toDhall}")))
            (parent, child, referentialCheck)
          case None =>
            // Special case: we are resolving imports in a dhall source that is not a file. We do not have a `parent` import.
            val child = i.canonicalize
            (child, child, Right(()))
        }
        println(s"DEBUG 1 got parent = ${parent.toDhall} and child = ${child.toDhall}")
        lazy val resolveIfAlreadyResolved = gamma.get(child) match {
          case Some(r) => Left(ImportResolutionResult.Resolved(r))
          case None => Right(())
        }
        // TODO report issue - imports.md does not clearly explain `Γ(headersPath) = userHeadersExpr` and also whether Γ1 is being reused

        val xdgOption = Option(System.getenv("XDG_CONFIG_HOME")).map(xdg => s""" ? "$xdg/dhall/headers.dhall"""").getOrElse("")
        lazy val resolveDefaultHeaders = s"""env:DHALL_HEADERS $xdgOption ? ~/.config/dhall/headers.dhall ? []""".dhall

        lazy val resolveIfLocation: Either[ImportResolutionResult[Expression], Array[Byte] => ImportResolutionResult[Expression]] = child.importMode match {
          case ImportMode.Location =>
            val canonical = child.canonicalize
            // Need to process this first, because `missing as Location` is not a failure while `missing as` anything else must be a failure.
            val (field: FieldName, arg: Option[String]) = canonical.importType match {
              case ImportType.Missing => (FieldName("Missing"), None)
              case Remote(url, _) => (FieldName("Remote"), Some(url.toString))
              case p@Path(_, _) => (FieldName("Local"), Some(p.toString))
              case ImportType.Env(envVarName) => (FieldName("Environment"), Some(envVarName))
            }
            val withField: Expression = Field(typeOfImportAsLocation, field)
            val expr: Expression = arg match {
              case Some(text) => withField.apply(TextLiteral.ofString(text))
              case None => withField
            }
            Left(validateHashAndCacheResolved(expr, child.digest))

          case ImportMode.Code => Right(bytes =>
            Parser.parseDhallBytes(bytes, currentDir) match {
              case Parsed.Success(DhallFile(_, expr), _) => Resolved(expr)
              case failure: Parsed.Failure => PermanentFailure(Seq(s"failed to parse imported file: $failure"))
            }
          )
          case ImportMode.RawBytes => Right(bytes => Resolved(Expression(BytesLiteral(CBytes.byteArrayToHexString(bytes)))))
          case ImportMode.RawText => Right(bytes => Resolved(Expression(TextLiteral.ofString(new String(bytes)))))
        }
        lazy val missingOrData: Either[ImportResolutionResult[Expression], Array[Byte]] = child.importType match {
          case ImportType.Missing => Left(TransientFailure(Seq("import designated as `missing`")))

          case Remote(url, headers) => // TODO: use headers and also receive headers
            Try(requests.get(url.toString, headers = httpHeaders(headers)).bytes) match {
              case Failure(exception) => Left(TransientFailure(Seq(s"import failed from url $url: $exception")))
              case Success(bytes) => Right(bytes)
            }

          case path@Path(_, _) => (for {
            javaPath <- Try(path.toJavaPath(currentDir))
            bytes <- Try(Files.readAllBytes(javaPath))
          } yield bytes) match {
            case Failure(exception) => Left(TransientFailure(Seq(s"failed to read imported file: $exception")))
            case Success(bytes) => Right(bytes)
          }

          case ImportType.Env(envVarName) => Option(System.getenv(envVarName)) match {
            case Some(value) => Try(value.getBytes("UTF-8")) match {
              case Failure(exception) => Left(PermanentFailure(Seq(s"Env variable '$envVarName' is not a valid UTF-8 string: $exception")))
              case Success(utf8bytes) => Right(utf8bytes)
            }
            case None => Left(TransientFailure(Seq(s"Env variable '$envVarName' is undefined")))
          }
        }
        // Resolve imports in the expression we just parsed.
        val result: Either[ImportResolutionResult[Expression], Expression] = for {
          _ <- resolveIfAlreadyResolved
          _ <- referentialCheck
          readByImportMode <- resolveIfLocation
          bytes <- missingOrData
          expr <- Right(readByImportMode(bytes))
          successfullyRead <- expr match {
            case Resolved(x) => Right(x)
            case _ => Left(expr)
          }

        } yield successfullyRead

        val newState: (ImportResolutionResult[Expression], ImportContext) = result match {
          case Left(gotEarlyResult) => (gotEarlyResult, state0)
          case Right(readExpression) =>
            resolveImportsStep(readExpression, visited :+ child, currentDir).run(  state0) match {
              case (result1, state1) => result1 match {
                case Resolved(r) => r.inferType match {
                  case TypeCheckResult.Valid(_) => (result1, state1)
                  case i@TypeCheckResult.Invalid(_) => (PermanentFailure(Seq(s"Imported expression ${readExpression.toDhall} fails to typecheck: $i")), state1)
                }
                case _ => (result1, state1)
              }
            }
        }
        // Add the new resolved expression to the import context.
        newState match {
          case (result2, state2) => result2 match {
            case Resolved(r) => (result2, state2.copy(state2.resolved.updated(child, r)))
            case _ => newState
          }
        }

      // Try resolving `lop`. If failed non-permanently, try resolving `rop`. Accumulate error messages.
      case ExprOperator(lop, Operator.Alternative, rop) =>
        resolveImportsStep(lop, visited, currentDir).run(state0) match {
          case resolved@(Resolved(_), _) => resolved

          case failed@(PermanentFailure(_), _) => failed

          case (TransientFailure(messages1), state1) => resolveImportsStep(rop, visited, currentDir).run(state1) match {
            case resolved@(Resolved(_), _) => resolved
            case (PermanentFailure(messages2), state2) => (PermanentFailure(messages1 ++ messages2), state2)
            case (TransientFailure(messages2), state2) => (TransientFailure(messages1 ++ messages2), state2)
          }

        }

      case _ => expr.scheme.traverse(resolveImportsStep(_, visited, currentDir)).run(state0) match {
        case (scheme, state) => (scheme.map(Expression.apply), state)
      }
    }
  }

}

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

// A State monad used as an applicative functor to update the state during import resolution.
final case class ImportResolutionStep[+E](run: ImportContext => (ImportResolutionResult[E], ImportContext))

object ImportResolutionStep {
  implicit val ApplicativeImportResolutionStep: Applicative[ImportResolutionStep] = new Applicative[ImportResolutionStep] {
    override def zip[A, B](fa: ImportResolutionStep[A], fb: ImportResolutionStep[B]): ImportResolutionStep[(A, B)] =
      ImportResolutionStep[(A, B)] { s0 =>
        fa.run(s0) match {
          case (Resolved(a), s1) =>
            fb.run(s1) match {
              case (Resolved(b), s2) => (Resolved((a, b)), s2)
              case (failure: ImportResolutionResult[Nothing], s2) => (failure, s2)
            }
          case (failure: ImportResolutionResult[Nothing], s1) => (failure, s1)
        }
      }

    override def map[A, B](f: A => B)(fa: ImportResolutionStep[A]): ImportResolutionStep[B] =
      ImportResolutionStep[B](s => fa.run(s) match {
        case (a, s) => (a.map(f), s)
      })

    override def pure[A](a: A): ImportResolutionStep[A] =
      ImportResolutionStep[A](s => (Resolved(a), s))
  }

}
