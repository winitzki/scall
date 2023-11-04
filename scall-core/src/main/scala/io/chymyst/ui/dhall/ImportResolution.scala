package io.chymyst.ui.dhall

import io.chymyst.ui.dhall.ImportResolution.ImportContext
import io.chymyst.ui.dhall.ImportResolutionMonad.Errors
import io.chymyst.ui.dhall.Syntax.Expression
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.SyntaxConstants.{FilePrefix, ImportType, Operator, URL}
import io.chymyst.ui.dhall.SyntaxConstants.ImportType.{Path, Remote}

import scala.util.chaining.scalaUtilChainingOps

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

  final case class ImportContext(resolved: Map[Import[Expression], Expression])

  // Recursively resolve imports. Use .traverse with this Kleisli function.
  def resolveImports(expr: Expression): ImportResolutionMonad[Expression] = ImportResolutionMonad[Expression] { case state0@ImportResolutionState(visited, gamma) =>
    expr.scheme match {
      case Import(importType, importMode, digest) => ???

      case ExprOperator(lop, Operator.Alternative, rop) => // Try resolving `lop`. If failed non-permanently, try resolving `rop`. Accumulate error messages.
        resolveImports(lop).run(state0).flatMap {
          case (Left(failure1), state1) => resolveImports(rop).run(state1) match {
            case Left(failure2) => Left(failure1 ++ failure2)
            case resolved@Right((Right(_), _)) => resolved
            case Right((Left(failure2), state2)) => Right((Left(failure1 ++ failure2), state2))
          }
          case resolved@(Right(_), _) => Right(resolved)
        }

      case _ => expr.scheme.traverse(resolveImports).run(state0).map { case (scheme, state) => (scheme.map(Expression.apply), state) }
    }
  }

}

final case class ImportResolutionState(visited: Seq[Import[Expression]] /* non-empty */ , gamma: ImportContext)

// Import resolution may fail in a recoverable way (with `?`) or in a way that disallows further attempts via `?`.
final case class ImportResolutionMonad[E](run: ImportResolutionState => Either[Errors, (Either[Errors, E], ImportResolutionState)])

object ImportResolutionMonad {
  type Errors = List[String]

  implicit val ApplicativeIRMonad: Applicative[ImportResolutionMonad] = new Applicative[ImportResolutionMonad] {
    override def zip[A, B](fa: ImportResolutionMonad[A], fb: ImportResolutionMonad[B]): ImportResolutionMonad[(A, B)] =
      ImportResolutionMonad[(A, B)] { s0 =>
        fa.run(s0).flatMap { case (a, s1) =>
          fb.run(s1).map { case (b, s2) => (a.flatMap { x => b.map((x, _)) }, s2) }
        }
      }

    override def map[A, B](f: A => B)(fa: ImportResolutionMonad[A]): ImportResolutionMonad[B] =
      ImportResolutionMonad[B](s => fa.run(s).map { case (a, s) => (a.map(f), s) })

    override def pure[A](a: A): ImportResolutionMonad[A] =
      ImportResolutionMonad[A](s => Right((Right(a), s)))
  }

}
