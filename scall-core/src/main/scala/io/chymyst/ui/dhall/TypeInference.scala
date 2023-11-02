package io.chymyst.ui.dhall

import io.chymyst.ui.dhall.Syntax.Expression
import io.chymyst.ui.dhall.SyntaxConstants.VarName

object TypeInference {
  final case class GammaTypeContext(defs: Seq[(VarName, Expression)])

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/type-inference.md
  def inferType(gamma: GammaTypeContext, expr: Expression): Expression = ???
}
