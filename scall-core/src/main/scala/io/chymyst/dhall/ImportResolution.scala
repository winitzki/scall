package io.chymyst.dhall

import fastparse.Parsed
import geny.Generator.from
import io.chymyst.dhall.CBORmodel.CBytes
import io.chymyst.dhall.ImportResolution.ImportContext
import io.chymyst.dhall.ImportResolutionResult._
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.Syntax.{DhallFile, Expression}
import io.chymyst.dhall.SyntaxConstants.FilePrefix.Here
import io.chymyst.dhall.SyntaxConstants.ImportMode.Location
import io.chymyst.dhall.SyntaxConstants.ImportType.{ImportPath, Remote}
import io.chymyst.dhall.SyntaxConstants.Operator.Alternative
import io.chymyst.dhall.SyntaxConstants._
import io.chymyst.tc.Applicative

import java.nio.file
import java.nio.file.{Files, Paths}
import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}
import sourcecode.{File => SourceFile, Line => SourceLine}

object ImportResolution {

  def chainWith[E](parent: ImportType[E], child: ImportType[E]): ImportType[E] = (parent, child) match {
    case (Remote(ImportURL(scheme1, authority1, path1, query1), headers1), ImportPath(Here, path2))              =>
      Remote(ImportURL(scheme1, authority1, path1 chain path2, query1), headers1)
    case (ImportPath(filePrefix, path1), ImportPath(FilePrefix.Here, path2))                                     => ImportPath(filePrefix, path1 chain path2)
    case (Remote(ImportURL(scheme1, authority1, path1, query1), headers1), ImportPath(FilePrefix.Parent, path2)) =>
      Remote(ImportURL(scheme1, authority1, path1 chainToParent path2, query1), headers1)
    case (ImportPath(filePrefix, path1), ImportPath(FilePrefix.Parent, path2))                                   => ImportPath(filePrefix, path1 chainToParent path2)
    case _                                                                                                       => child
  }

  val corsHeader = "Access-Control-Allow-Origin".toLowerCase

  // This function returns `None` if there is no error in CORS compliance.
  def corsComplianceError(parent: ImportType[Expression], child: ImportType[Expression], responseHeaders: Map[String, Seq[String]]): Option[String] =
    (parent, child) match {
      // TODO: report issue: what if parent = Remote but child = Path, does the cors judgment then always succeed? The standard should say explicitly.
      case (Remote(ImportURL(scheme1, authority1, path1, query1), headers1), Remote(ImportURL(scheme2, authority2, path2, query2), headers2)) =>
        if (scheme1 == scheme2 && authority1 == authority2) None
        else
          responseHeaders.map { case (k, v) => (k.toLowerCase, v) }.get(corsHeader) match {
            case Some(Seq("*"))                                                                 => None
            case Some(Seq(other)) if other.toLowerCase == s"$scheme1://$authority1".toLowerCase => None
            case Some(other)                                                                    =>
              Some(
                s"Scheme or authority differs from parent $parent but response headers when fetching child $child are $responseHeaders, the CORS header is $other, importing is prohibited"
              )
            case None                                                                           => Some(s"Scheme or authority differs from parent $parent but no CORS header in child $child, headers $responseHeaders")
          }
      case (Remote(ImportURL(_, _, _, _), _), _)                                                                                              => Some(s"Remote parent $parent may not import a non-remote $child")
      case _                                                                                                                                  => None
    }

  lazy val typeOfImportAsLocation: Expression = UnionType(
    Seq(
      (ConstructorName("Local"), Some(~Builtin.Text)),
      (ConstructorName("Remote"), Some(~Builtin.Text)),
      (ConstructorName("Environment"), Some(~Builtin.Text)),
      (ConstructorName("Missing"), None),
    )
  ).sorted

  lazy val typeOfGenericHeadersForHost: Expression =
    Application(~Builtin.List, Expression(RecordType(Seq((FieldName("mapKey"), ~Builtin.Text), (FieldName("mapValue"), ~Builtin.Text)))))

  lazy val typeOfUserDefinedAlternativeHeadersForHost: Expression =
    Application(~Builtin.List, Expression(RecordType(Seq((FieldName("header"), ~Builtin.Text), (FieldName("value"), ~Builtin.Text)))))

  lazy val typeOfGenericHeadersForAllHosts: Expression =
    Application(~Builtin.List, Expression(RecordType(Seq((FieldName("mapKey"), ~Builtin.Text), (FieldName("mapValue"), typeOfGenericHeadersForHost)))))

  lazy val emptyHeadersForHost: Iterable[(String, String)] = Seq()

  private def readCached(cacheRoot: java.nio.file.Path, digest: BytesLiteral): Try[Expression] = {
    val digestHex  = digest.hex.toLowerCase
    val cachedPath = cacheRoot.resolve("1220" + digestHex)
    for {
      bytes   <- Try(Files.readAllBytes(cachedPath))
      ourHash <- Try(Semantics.computeHash(bytes))
      _       <- if (ourHash == digestHex) Success(())
                 else Failure(new Exception(s"SHA256 mismatch: cached at $cachedPath has a different hash ($ourHash)"))
      expr    <- Try(CBORmodel.decodeCbor1(bytes).toScheme: Expression)
    } yield expr
  }

  private def readFirstCached(digest: BytesLiteral): Option[Expression] =
    dhallCacheRoots
      .map(readCached(_, digest))
      .map { t =>
        if (t.isFailure && t.failed.get.getMessage.contains("SHA256 mismatch")) println(s"Warning: failure reading from cache: ${t.failed.get}")
        t
      }                                           // Print this failure only when the error is important (hash mismatch).
      .filter(_.isSuccess)
      .take(1).map(_.toOption).headOption.flatten // Force evaluation of the first valid operation over all candidate cache roots.

  private def validateHashAndCacheResolved(expr: Expression, digest: Option[BytesLiteral], enableCache: Boolean): ImportResolutionResult[Expression] =
    digest match {
      case None => Resolved(expr)

      case Some(BytesLiteral(hex)) =>
        val ourBytes = expr.alphaNormalized.betaNormalized.toCBORmodel.encodeCbor2
        val ourHash  = Semantics.computeHash(ourBytes).toLowerCase
        if (hex.toLowerCase == ourHash) {
          dhallCacheRoots
            .filter(_ => enableCache)
            .map { cachePath =>
              Try(Files.write(cachePath.resolve("1220" + ourHash), ourBytes))
              // TODO: log errors while writing the cache file
              // TODO verify that we will attempt to read the cached file from any of the locations, not just from the first one.
            }.filter(_.isSuccess)
            .take(1).headOption // Force evaluation of the first valid operation over all candidate cache roots.
          Resolved(expr)
        } else {
          val message = s"sha-256 mismatch: found $ourHash instead of specified $hex from expression ${expr.print}"
          println(s"Error: $message")
          PermanentFailure(Seq(message))
        }
    }

  lazy val isWindowsOS: Boolean = System.getProperty("os.name").toLowerCase.contains("windows")

  private def createAndCheckReadableWritable(path: java.nio.file.Path): Try[java.nio.file.Path] = Try {
    Files.createDirectories(path)
    if (Files.isReadable(path) && Files.isWritable(path)) path else throw new Exception(s"Path $path is not readable or not writable")
  }

  private def dhallCacheRoots: Iterator[java.nio.file.Path] = Seq(
    Try(Paths.get(scala.sys.env("XDG_CACHE_HOME")).resolve("dhall")),
    Try(
      if (isWindowsOS) Paths.get(scala.sys.env("LOCALAPPDATA")).resolve("dhall")
      else Paths.get(System.getProperty("user.home")).resolve(".cache").resolve("dhall")
    ),
  ).iterator.map(_.flatMap(createAndCheckReadableWritable)).collect { case Success(path) => path }

  final case class ImportContext(resolved: Map[Import[Expression], Expression]) {
    override def toString: String = resolved
      .map { case (k, v) =>
        k.print.take(160) + (if (k.print.length > 160) "..." else "") + " -> " + v.print.take(160) + (if (v.print.length > 160) "..." else "")
      }.mkString("Map(\n\t", "\n\t", "\n)")
  }

  // TODO report issue - imports.md does not say how to bootstrap reading a dhall expression from string, what is the initial "parent" import?
  // TODO workaround: allow the "visited" list to be empty initially? Or make the initial import "."?
  // TODO: return an Either instead of throwing exceptions?
  def resolveAllImports(expr: Expression, currentImport: Import[Expression], enableCache: Boolean): Expression = {
    val initialVisited = currentImport

    val initState = ImportContext(Map())

    resolveImportsStep(expr, Seq(initialVisited), currentImport, enableCache).run(initState) match {
      case (resolved, finalState) =>
        resolved match {
          case TransientFailure(messages) =>
            throw new Exception(s"Transient failure resolving ${expr.print}: ${ImportResolutionResult.printFailures(messages)}")
          case PermanentFailure(messages) =>
            throw new Exception(s"Permanent failure resolving ${expr.print}: ${ImportResolutionResult.printFailures(messages)}")
          case Resolved(r)                => r
        }
    }
  }

  // TODO: remove this code
//  def printVisited(visited: Seq[Import[Expression]]): String = visited.map(_.print).mkString("[", ", ", "]")

  private def extractHeaders(h: Expression, keyName: String, valueName: String): Iterable[(String, String)] = h.betaNormalized.scheme match {
    case EmptyList(_)        => emptyHeadersForHost
    case NonEmptyList(exprs) =>
      exprs.map(_.scheme).map { case d @ RecordLiteral(_) =>
        (
          d.lookup(FieldName(keyName)).get.toPrimitiveValue.get.asInstanceOf[String],
          d.lookup(FieldName(valueName)).get.toPrimitiveValue.get.asInstanceOf[String],
        )
      }
    case other               =>
      throw new Exception(s"ERROR: internal error - headers must be of type ${typeOfGenericHeadersForHost.print} but instead have ${other.print}")
  }

  private def messageForNonexistingImportFile(javaPath: file.Path) =
    if (javaPath.isAbsolute) s"Imported file $javaPath does not exist."
    else
      s"Imported file at relative path $javaPath does not exist, absolute path ${javaPath.toAbsolutePath}, current directory is ${Paths.get(".").toAbsolutePath}"

  // TODO: report issue to mention in imports.md (at the end) that the updates of the resolution context must be threaded through all resolved subexpressions.
  /** Perform one step of import resolution. This function may call itself on sub-expressions.
    *
    * See https://github.com/dhall-lang/dhall-lang/blob/master/standard/imports.md
    *
    * We will use `traverse` on `ExpressionScheme` with this Kleisli function, in order to track changes in the resolution context.
    *
    * Example:
    *
    * Suppose a file `./program` contains the URL string `http://a.org/lib/func1`.
    *
    * When we fetch that URL, we get the string `./func2`. When we fetch `http://a.org/lib/func2`, we get the string `0`.
    *
    * These imports are resolved like this:
    *
    * After parsing the file `./program`, we get an expression of type [[Import]]:
    *
    * {{{
    *   "http://a.org/lib/func1".dhall
    * }}}
    *
    * To resolve that, we will call:
    *
    * {{{
    *    resolveImportsStep(expr = "http://a.org/lib/func1".dhall, visited = Seq(), currentImport = "./program".dhall)
    * }}}
    *
    * Here, `visited` is empty because there are no previous imports before `./program`.
    *
    * This call will read the URL `http://a.org/lib/func1` and obtain the text `"./func2"`. Parsing that text will give an expression of type [[Import]].
    *
    * So, this is a nested import. To resolve this, we need to call:
    *
    * {{{
    *    resolveImportsStep(expr = "./func2".dhall, visited = Seq("./program".dhall), currentImport = "http://a.org/lib/func1".dhall)
    * }}}
    *
    * Because `"./func2".dhall` is again an expression of type [[Import]], we continue the import resolution.
    *
    * We chain the current import with `./func2` and obtain the new child import expression: `http://a.org/lib/func2`. We fetch that URL and parse the string
    * `0`. Then we call:
    *
    * {{{
    *   resolveImportsStep(expr = "0".dhall, visited = Seq("./program".dhall, "http://a.org/lib/func1".dhall), currentImport = "http://a.org/lib/func2".dhall)
    * }}}
    *
    * At this point the import resolution is finished and the final expression is computed as `0`.
    *
    * @param expr
    *   An expression in which imports need to be resolved.
    * @param visited
    *   List of nested import expressions that have been resolved before encountering this one. This list may be empty.
    * @param parent
    *   The parent import expression that has been already resolved, its contents was fetched and parsed as `expr`.
    * @return
    *   A function that updates the import context and returns an [[ImportResolutionResult]].
    */
  private def resolveImportsStep(
    expr: Expression,
    visited: Seq[Import[Expression]],
    parent: Import[Expression],
    enableCache: Boolean,
  ): ImportResolutionStep[Expression] =
    ImportResolutionStep[Expression] { case stateGamma0 @ ImportContext(gamma) =>
      val (importResolutionResult, finalState) = expr.scheme match {
        // If `expr` is not an Import, we will defer to other `case` clauses to iterate over its subexpressions.
        case i @ Import(_, _, _)                 =>
          val child             = Import.chainWith(parent, i).canonicalize
          val cyclicImportCheck =
            if (visited.contains(child) || parent == child)
              Left(
                PermanentFailure(Seq(s"cyclic import of $child is not allowed, imports already visited: ${(visited :+ parent).map(_.print).mkString("; ")}"))
              )
            else Right(())
          val referentialCheck  =
            if (parent.importType allowedToImportAnother child.importType) Right(())
            else if (child.importMode == ImportMode.Location) Right(()) // Corner case: import as `Location` does not need the referential check.
            else Left(PermanentFailure(Seq(s"parent import expression ${parent.print} may not import child ${child.print} due to the referential check")))

          // println(s"DEBUG 1 got parent = ${parent.print} and child = ${child.print}")
          lazy val checkIfAlreadyResolved = gamma.get(child) match {
            case Some(r) => Left(ImportResolutionResult.Resolved(r))
            case None    => Right(())
          }
          // TODO report issue - imports.md does not clearly explain `Γ(headersPath) = userHeadersExpr` and also whether Γ1 is being reused

          lazy val defaultHeadersLocation =
            """env:DHALL_HEADERS ? "${env:XDG_CONFIG_HOME as Text}/dhall/headers.dhall" ? ~/.config/dhall/headers.dhall""".dhall

          lazy val (defaultHeadersForHost, stateGamma1) = child.importType.remoteOrigin match {
            case None                                  => (emptyHeadersForHost, stateGamma0)
            case Some((remoteScheme, remoteAuthority)) =>
              val (result, state01) = resolveImportsStep(defaultHeadersLocation, visited, parent, enableCache).run(stateGamma0)
              result match {
                case Resolved(expr)             =>
                  val headersForOrigin: Iterable[(String, String)] = (expr | typeOfGenericHeadersForAllHosts).inferType match {
                    case TypecheckResult.Valid(_)        =>
                      expr.betaNormalized.scheme match { // TODO report issue: imports.md does not say that headers must be beta-normalized before use, but tests require that.
                        case NonEmptyList(exprs) =>
                          exprs.find {
                            case Expression(r @ RecordLiteral(_)) =>
                              r.lookup(FieldName("mapKey")) match {
                                case Some(
                                      Expression(TextLiteral(List(), originInHeaders))
                                    ) => // remoteOrigin = https://host  may match originInHeaders = host:443 and http://host may match host:80 unless port is specified in remoteAuthority.
                                  (originInHeaders.toLowerCase == remoteAuthority.toLowerCase) ||
                                  (!remoteAuthority.matches(":[0-9]+$") && originInHeaders.toLowerCase == s"$remoteAuthority:${remoteScheme.defaultPort}")
                                case None => false
                              }

                            case _ => false
                          } match {
                            case Some(Expression(r @ RecordLiteral(_))) => extractHeaders(r.lookup(FieldName("mapValue")).get, "mapKey", "mapValue")
                            case None                                   =>
                              println(s"Warning: headers resolved from ${expr.print} do not contain a map entry for origin '$remoteAuthority'")
                              emptyHeadersForHost
                          }
                        case _                   => emptyHeadersForHost // TODO report issue - if headers have wrong type, do we ignore them or fail the entire expression?
                      }
                    case TypecheckResult.Invalid(errors) =>
                      println(s"Warning: headers resolved from ${expr.print} have a wrong type: $errors")
                      emptyHeadersForHost
                  }
                  (headersForOrigin, state01)
                case TransientFailure(_)        => (emptyHeadersForHost, stateGamma0)
                case PermanentFailure(messages) =>
                  // TODO report issue - if we permanently failed to resolve the headers' own transitive imports, do we need to fail the entire expression or use empty headers? Looks like we need to fail the entire expression but there are no tests for this.
                  println(s"Error: permanently failed to resolve headers: $messages")
                  (emptyHeadersForHost, stateGamma0)
              }
          }

          // Values of type Either[ImportResolutionResult[Expression], _] are used to report early results as Left().
          // At the end of the computation, we will have Either[ImportResolutionResult[Expression], ImportResolutionResult[Expression]] and we will `merge` that.
          lazy val resolveByImportMode: Either[ImportResolutionResult[Expression], Array[Byte] => ImportResolutionResult[Expression]] = child.importMode match {
            case Location =>
              val canonical                               = child.canonicalize
              // Need to process this first, because `missing as Location` is not a failure while `missing as` anything else must be a failure.
              val (field: FieldName, arg: Option[String]) = canonical.importType match {
                case ImportType.Missing         => (FieldName("Missing"), None)
                case Remote(url, _)             => (FieldName("Remote"), Some(url.toString))
                case p @ ImportPath(_, _)       => (FieldName("Local"), Some(p.toString))
                case ImportType.Env(envVarName) => (FieldName("Environment"), Some(envVarName))
              } // TODO report issue: imports.md incorrectly specifies the field name as `.Location` whereas it must be `.Local`
              val withField: Expression                   = Field(typeOfImportAsLocation, field)
              val expr: Expression                        = arg match {
                case Some(text) => withField.apply(TextLiteral.ofString(text))
                case None       => withField
              }
              Left(Resolved(expr))

            case ImportMode.Code     =>
              Right(bytes =>
                Parser.parseDhallBytes(bytes) match {
                  case Parsed.Success(DhallFile(_, _, expr), _) => Resolved(expr)
                  case failure: Parsed.Failure                  => PermanentFailure(Seq(s"failed to parse imported file: $failure"))
                }
              )
            case ImportMode.RawBytes => Right(bytes => Resolved(Expression(BytesLiteral(CBytes.byteArrayToHexString(bytes)))))
            case ImportMode.RawText  => Right(bytes => Resolved(Expression(TextLiteral.ofString(new String(bytes)))))
          }

          // Corner case: If the import mode is `Location`, we must not attempt to use the cache.
          lazy val resolveIfCached: Either[ImportResolutionResult[Expression], Unit] =
            if (child.importMode == ImportMode.Location)
              Right(())
            else
              child.digest.flatMap(readFirstCached) match {
                case Some(expr) => Left(Resolved(expr))
                case None       => Right(())
              }

          lazy val missingOrData: Either[ImportResolutionResult[Expression], Array[Byte]] = child.importType match {
            case ImportType.Missing => Left(TransientFailure(Seq("import is `missing` (perhaps not an error)")))

            case Remote(childUrl, childUserHeaders) =>
              // Verify the type of headers. `extractHeaders` will beta-normalize only if typechecking succeeds.
              val checkHeaderTypeGeneric: Either[ImportResolutionResult[Expression], Iterable[(String, String)]] = childUserHeaders match {
                case Some(headersExpr) =>
                  (headersExpr | typeOfGenericHeadersForHost).inferType match {
                    case TypecheckResult.Valid(_)        => Right(extractHeaders(headersExpr, "mapKey", "mapValue"))
                    case TypecheckResult.Invalid(errors) =>
                      Left(
                        PermanentFailure(Seq(s"import from url $childUrl failed typecheck for headers of type ${typeOfGenericHeadersForHost.print}: $errors"))
                      )
                  }
                case None              => Right(emptyHeadersForHost)
              }
              val checkHeaderTypeSpecial: Either[ImportResolutionResult[Expression], Iterable[(String, String)]] = childUserHeaders match {
                case Some(headersExpr) =>
                  (headersExpr | typeOfUserDefinedAlternativeHeadersForHost).inferType match {
                    case TypecheckResult.Valid(_)        => Right(extractHeaders(headersExpr, "header", "value"))
                    case TypecheckResult.Invalid(errors) =>
                      Left(
                        PermanentFailure(
                          Seq(s"import from url $childUrl failed typecheck for headers of type ${typeOfUserDefinedAlternativeHeadersForHost.print}: $errors")
                        )
                      )
                  }
                case None              => Right(emptyHeadersForHost)
              }
              (checkHeaderTypeGeneric orElse checkHeaderTypeSpecial) flatMap { userHeadersForHost =>
                val combinedHeaders: Iterable[(String, String)] =
                  (userHeadersForHost.toMap ++ defaultHeadersForHost.toMap).to(Iterable) // The default headers must override user headers.
                Try(requests.get(childUrl.toString, headers = combinedHeaders)) match {
                  case Failure(exception) => Left(TransientFailure(Seq(s"import failed from url $childUrl: $exception")))
                  case Success(response)  =>
                    corsComplianceError(parent.importType, child.importType, response.headers) match {
                      case Some(corsError) => Left(PermanentFailure(Seq(s"import from url $childUrl failed CORS check: $corsError")))
                      case None            => Right(response.bytes)
                    }
                }
              }

            case path @ ImportPath(_, _) =>
              (for {
                javaPath <- Try(path.toJavaPath)
                _        <- if (javaPath.toFile.exists) Success(()) else Failure(new Exception(messageForNonexistingImportFile(javaPath)))
                bytes    <- Try(Files.readAllBytes(javaPath))
              } yield bytes) match {
                case Failure(exception) => Left(TransientFailure(Seq(s"Failed to read imported file: $exception")))
                case Success(bytes)     =>
                  Right(bytes)
              }

            case ImportType.Env(envVarName) =>
              Option(System.getenv(envVarName)) match {
                case Some(value) =>
                  Try(value.getBytes("UTF-8")) match {
                    case Failure(exception) => Left(PermanentFailure(Seq(s"Env variable '$envVarName' is not a valid UTF-8 string: $exception")))
                    case Success(utf8bytes) => Right(utf8bytes)
                  }
                case None        => Left(TransientFailure(Seq(s"Env variable '$envVarName' is undefined")))
              }
          }

          // Resolve imports in the expression we just parsed.
          val importReadSuccessOrFailure: Either[ImportResolutionResult[Expression], Expression] = for {
            _                <- checkIfAlreadyResolved
            _                <- if (enableCache) resolveIfCached else Right(())
            _                <- cyclicImportCheck
            _                <- referentialCheck
            readByImportMode <- resolveByImportMode
            bytes            <- missingOrData // This is slow.
            expr             <- Right(readByImportMode(bytes))
            successfullyRead <- expr match {
                                  case Resolved(x) => Right(x)
                                  case _           => Left(expr)
                                }
          } yield successfullyRead

          val newState: (ImportResolutionResult[Expression], ImportContext) = importReadSuccessOrFailure match {
            case Left(gotEarlyResult)  => (gotEarlyResult, stateGamma1)
            case Right(readExpression) =>
              resolveImportsStep(readExpression, visited :+ parent, child, enableCache).run(stateGamma1) match {
                case (result1, stateGamma2) =>
                  // If the expression was successfully imported, we need to type-check and beta-normalize it.
                  val result2: ImportResolutionResult[Expression] = result1.flatMap { r =>
                    r.inferType match { // Note: this type inference is done with empty context because imports may not have any free variables.
                      case TypecheckResult.Valid(_)          =>
                        Resolved(r.betaNormalized)
                      case TypecheckResult.Invalid(messages) =>
                        PermanentFailure(Seq(s"Type error in imported expression ${readExpression.print}:${messages.mkString("\n\t", "\n\t", "\n")}"))
                    }
                  }
                  (result2, stateGamma2)
              }
          }
          // The new expression has been resolved (or failed).
          // Add the new resolved expression to the import context.
          newState match {
            case (result2, state2) =>
              // Corner case: import as Location must not attempt to use the digest cache.
              val effectiveDigest = if (child.importMode == ImportMode.Location) None else child.digest
              result2.flatMap(validateHashAndCacheResolved(_, effectiveDigest, enableCache)) match {
                case Resolved(r) => (result2, state2.copy(state2.resolved.updated(child, r)))
                case failure     => (failure, state2)
              }
          }

        // Try resolving `lop`. If failed non-permanently, try resolving `rop`. Accumulate error messages.
        case ExprOperator(lop, Alternative, rop) =>
          resolveImportsStep(lop, visited, parent, enableCache).run(stateGamma0) match {
            case resolved @ (Resolved(_), _) => resolved

            case failed @ (PermanentFailure(_), _) => failed

            case (TransientFailure(messages1), state1) =>
              resolveImportsStep(rop, visited, parent, enableCache).run(state1) match {
                case resolved @ (Resolved(_), _)           => resolved
                case (PermanentFailure(messages2), state2) => (PermanentFailure(messages1 ++ messages2), state2)
                case (TransientFailure(messages2), state2) => (TransientFailure(messages1 ++ messages2), state2)
              }
          }

        case _ =>
          expr.scheme.traverse(resolveImportsStep(_, visited, parent, enableCache)).run(stateGamma0) match {
            case (scheme, state) => (scheme.map(Expression.apply), state)
          }
      }
      val checkDigest                          = importResolutionResult.flatMap {
        case e @ Expression(Import(importType, importMode, digest)) => validateHashAndCacheResolved(e, digest, enableCache)
        case e @ _                                                  => Resolved(e)
      }
      (checkDigest, finalState)
    }

}

// Import resolution may fail either in a way that may be recovered via `?`, or in a way that disallows further attempts via `?`.
sealed trait ImportResolutionResult[+E] {
  def flatMap[H](f: E => ImportResolutionResult[H]): ImportResolutionResult[H] = this match {
    case Resolved(expr)                           => f(expr)
    case failure: ImportResolutionResult[Nothing] => failure
  }

  def map[H](f: E => H): ImportResolutionResult[H] = this match {
    case Resolved(expr)                           => Resolved(f(expr))
    case failure: ImportResolutionResult[Nothing] => failure
  }
}

object ImportResolutionResult {

  def printFailures(messages: ResolutionErrors): String = messages.mkString("\n\t", "\n\t", "\n")

  type ResolutionErrors = Seq[String]

  final case class TransientFailure(messages: ResolutionErrors) extends ImportResolutionResult[Nothing]

  final case class PermanentFailure(messages: ResolutionErrors) extends ImportResolutionResult[Nothing]

  final case class Resolved[E](expr: E) extends ImportResolutionResult[E]
}

// A State monad-transformed ImportResolutionResult, used as an applicative functor to update the state during import resolution.
final case class ImportResolutionStep[+E](run: ImportContext => (ImportResolutionResult[E], ImportContext))

object ImportResolutionStep {
  implicit val ApplicativeImportResolutionStep: Applicative[ImportResolutionStep] = new Applicative[ImportResolutionStep] {
    override def zip[A, B](fa: ImportResolutionStep[A], fb: ImportResolutionStep[B]): ImportResolutionStep[(A, B)] =
      ImportResolutionStep[(A, B)] { s0 =>
        fa.run(s0) match {
          case (Resolved(a), s1)                              =>
            fb.run(s1) match {
              case (Resolved(b), s2)                              => (Resolved((a, b)), s2)
              case (failure: ImportResolutionResult[Nothing], s2) => (failure, s2)
            }
          case (failure: ImportResolutionResult[Nothing], s1) => (failure, s1)
        }
      }

    override def map[A, B](f: A => B)(fa: ImportResolutionStep[A]): ImportResolutionStep[B] =
      ImportResolutionStep[B](s =>
        fa.run(s) match {
          case (a, s) => (a.map(f), s)
        }
      )

    override def pure[A](a: A): ImportResolutionStep[A] =
      ImportResolutionStep[A](s => (Resolved(a), s))
  }

}
