package io.chymyst.ui.dhall

import io.chymyst.ui.dhall.Syntax.Expression
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.SyntaxConstants.{FilePrefix, ImportType, URL}
import io.chymyst.ui.dhall.SyntaxConstants.ImportType.{Path, Remote}

object Imports {

  def chainWith(parent: ImportType[Expression], child: ImportType[Expression]): ImportType[Expression] = (parent, child) match {
    case (Remote(URL(scheme1, authority1, path1, query1), headers1), Path(FilePrefix.Here, path2)) => Remote(URL(scheme1, authority1, path1 chain path2, query1), headers1)
    case (Path(filePrefix, path1), Path(FilePrefix.Here, path2)) => Path(filePrefix, path1 chain path2)

    case (Remote(URL(scheme1, authority1, path1, query1), headers1), Path(FilePrefix.Parent, path2)) => Remote(URL(scheme1, authority1, path1 chainToParent path2, query1), headers1)
    case (Path(filePrefix, path1), Path(FilePrefix.Parent, path2)) => Path(filePrefix, path1 chainToParent path2)

    case _ => child
  }

  def corsHeader = "Access-Control-Allow-Origin"

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

  // TODO report issue: in imports.md probably `Γ(headersPath) = userHeadersExpr` should be rewritten as `userHeadersExpr = Γ(headersPath)`
  def resolveImports(visited: Seq[Import[Expression]] /* non-empty */ , gamma: ImportContext, expr: Expression): (Expression, ImportContext) = {
    ???
  }
}
