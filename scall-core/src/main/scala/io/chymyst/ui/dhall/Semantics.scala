package io.chymyst.ui.dhall

import io.chymyst.ui.dhall.Syntax.Expression.v
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.Syntax.{Expression, ExpressionScheme, Natural}
import io.chymyst.ui.dhall.SyntaxConstants.Builtin.{Natural, NaturalFold}
import io.chymyst.ui.dhall.SyntaxConstants.Constant.{False, True}
import io.chymyst.ui.dhall.SyntaxConstants.{Builtin, File, Operator, VarName}

import java.util.regex.Pattern
import scala.util.chaining.scalaUtilChainingOps

object Semantics {

  final case class GammaTypeContext(defs: Seq[(VarName, Expression)])

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/shift.md
  def shift(positive: Boolean, x: VarName, minIndex: Natural, expr: Expression): Expression = {
    val d = if (positive) 1 else -1
    expr.scheme match {
      case Variable(name, index) => if (name != x || index < minIndex) expr else Variable(name, index + d)

      case Lambda(name, tipe, body) =>
        val newMinIndex = if (name != x) minIndex else minIndex + 1
        Lambda(name, shift(positive, x, minIndex, tipe), shift(positive, x, newMinIndex, body))

      case Forall(name, tipe, body) =>
        val newMinIndex = if (name != x) minIndex else minIndex + 1
        Forall(name, shift(positive, x, minIndex, tipe), shift(positive, x, newMinIndex, body))

      case Let(name, tipe, subst, body) =>
        val newMinIndex = if (name != x) minIndex else minIndex + 1
        Let(name, tipe.map(shift(positive, x, minIndex, _)), shift(positive, x, minIndex, subst), shift(positive, x, newMinIndex, body))

      case other => other.map(expression => shift(positive, x, minIndex, expression))
    }
  }

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/substitution.md
  def substitute(expr: Expression, substVar: VarName, substIndex: Natural, substTarget: Expression): Expression = expr.scheme match {
    case Variable(name, index) => if (name == substVar && index == substIndex) substTarget else expr

    case Lambda(name, tipe, body) =>
      val newIndex = if (name != substVar) substIndex else substIndex + 1
      val newType = substitute(tipe, name, substIndex, substTarget)
      val newTarget = shift(true, name, 0, substTarget)
      val newBody = substitute(body, name, newIndex, newTarget)
      Lambda(name, newType, newBody)

    case Forall(name, tipe, body) =>
      val newIndex = if (name != substVar) substIndex else substIndex + 1
      val newType = substitute(tipe, name, substIndex, substTarget)
      val newTarget = shift(true, name, 0, substTarget)
      val newBody = substitute(body, name, newIndex, newTarget)
      Forall(name, newType, newBody)

    case Let(name, tipe, subst, body) =>
      val newIndex = if (name != substVar) substIndex else substIndex + 1
      val newType = tipe.map(substitute(_, name, substIndex, substTarget))
      val newSubst = substitute(subst, name, substIndex, substTarget)
      val newTarget = shift(true, name, 0, substTarget)
      val newBody = substitute(body, name, newIndex, newTarget)
      Let(name, newType, newSubst, newBody)

    case other => other.map(expression => substitute(expression, substVar, substIndex, substTarget))
  }

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/alpha-normalization.md
  def alphaNormalize(expr: Expression): Expression = expr.scheme match { // TODO: make alphaNormalize a lazy val inside Expression.
    case Variable(_, _) => expr

    case Lambda(name, tipe, body) => if (name == underscore) expr.map(alphaNormalize) else {
      val body1 = shift(true, underscore, 0, body)
      val body2 = substitute(body1, name, 0, Variable(underscore, 0))
      val body3 = shift(false, name, 0, body2)
      Lambda(underscore, alphaNormalize(tipe), alphaNormalize(body3))
    }

    case Forall(name, tipe, body) => if (name == underscore) expr.map(alphaNormalize) else {
      val body1 = shift(true, underscore, 0, body)
      val body2 = substitute(body1, name, 0, Variable(underscore, 0))
      val body3 = shift(false, name, 0, body2)
      Forall(underscore, alphaNormalize(tipe), alphaNormalize(body3))
    }

    case Let(name, tipe, subst, body) => if (name == underscore) expr.map(alphaNormalize) else {
      val body1 = shift(true, underscore, 0, body)
      val body2 = substitute(body1, name, 0, Variable(underscore, 0))
      val body3 = shift(false, name, 0, body2)
      Let(underscore, tipe.map(alphaNormalize), alphaNormalize(subst), alphaNormalize(body3))
    }

    case Import(_, _, _) => throw new Exception(s"alphaNormalize($expr): Unresolved imports cannot be α-normalized")

    case other => other.map(alphaNormalize)
  }

  private def textShow(string: String): String = string
    .replace("\\", "\\\\")
    .replace("\t", "\\t")
    .replace("\r", "\\r")
    .replace("\n", "\\n")
    .replace("\f", "\\f")
    .replace("\b", "\\b")
    .replace("$", "\\u0024")
    .replace("\"", "\\\"")
    .pipe(s => "\"" + s + "\"") // TODO: report an issue to dhall-lang that this step is not shown in beta-normalization.md

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/beta-normalization.md
  def betaNormalize(expr: Expression): Expression = { // TODO: make betaNormalize a lazy val inside Expression.
    lazy val normalized: ExpressionScheme[Expression] = expr.map(betaNormalize)
    expr.scheme match {
      // These expression types are already in beta-normal form.
      case Variable(_, _) | ExprBuiltin(_) | ExprConstant(_) | NaturalLiteral(_) | IntegerLiteral(_) | DoubleLiteral(_) |
           BytesLiteral(_) | DateLiteral(_, _, _) | TimeLiteral(_) | TimeZoneLiteral(_) =>
        expr

      // These expression types only need to normalize their arguments.
      case EmptyList(_) | NonEmptyList(_) | KeywordSome(_) | Lambda(_, _, _) | Forall(_, _, _) | Assert(_) => normalized

      case Let(name, tipe, subst, body) => ???

      case If(cond, ifTrue, ifFalse) =>
        val If(condN, ifTrueN, ifFalseN) = normalized
        if (condN.scheme == ExprBuiltin(Builtin.True)) ifTrueN
        else if (condN.scheme == ExprBuiltin(Builtin.False)) ifFalseN
        else if (equivalent(ifTrue, ifFalse)) ifTrueN
        else normalized

      case Merge(record, update, tipe) => ???
      case ToMap(data, tipe) => ???

      case Annotation(data, tipe) => betaNormalize(data)

      case ExprOperator(lop, op, rop) =>
        val ExprOperator(lopN, _, ropN) = normalized
        op match {
          case Operator.Or =>
            if (lopN.scheme == ExprBuiltin(Builtin.False) || ropN.scheme == ExprBuiltin(Builtin.True)) ropN
            else if (lopN.scheme == ExprBuiltin(Builtin.True) || ropN.scheme == ExprBuiltin(Builtin.False)) lopN
            else if (equivalent(lop, rop)) lopN
            else normalized

          case Operator.Plus => (lopN.scheme, ropN.scheme) match { // Simplified only for Natural arguments.
            case (NaturalLiteral(a), _) if a == 0 => ropN
            case (_, NaturalLiteral(b)) if b == 0 => lopN
            case (NaturalLiteral(a), NaturalLiteral(b)) => NaturalLiteral(a + b)
            case _ => normalized
          }

          case Operator.TextAppend => betaNormalize(Expression(TextLiteral(List(("", lopN), ("", ropN)), "")))

          case Operator.ListAppend => ???

          case Operator.And =>
            if (lopN.scheme == ExprBuiltin(Builtin.False) || ropN.scheme == ExprBuiltin(Builtin.True)) lopN
            else if (lopN.scheme == ExprBuiltin(Builtin.True) || ropN.scheme == ExprBuiltin(Builtin.False)) ropN
            else if (equivalent(lop, rop)) lopN
            else normalized

          case Operator.CombineRecordTerms => ???
          case Operator.Prefer => ???
          case Operator.CombineRecordTypes => ???
          case Operator.Times => (lopN.scheme, ropN.scheme) match { // Simplified only for Natural arguments.
            case (NaturalLiteral(a), _) if a == 0 => NaturalLiteral(0)
            case (_, NaturalLiteral(b)) if b == 0 => NaturalLiteral(0)
            case (NaturalLiteral(a), _) if a == 1 => ropN
            case (_, NaturalLiteral(b)) if b == 1 => lopN
            case (NaturalLiteral(a), NaturalLiteral(b)) => NaturalLiteral(a * b)
            case _ => normalized
          }
          case Operator.Equal =>
            if (lopN.scheme == ExprBuiltin(Builtin.True)) ropN
            else if (ropN.scheme == ExprBuiltin(Builtin.True)) lopN
            else if (equivalent(lop, rop)) ExprBuiltin(Builtin.True)
            else normalized

          case Operator.NotEqual =>
            if (lopN.scheme == ExprBuiltin(Builtin.False)) ropN
            else if (ropN.scheme == ExprBuiltin(Builtin.False)) lopN
            else if (equivalent(lop, rop)) ExprBuiltin(Builtin.False)
            else normalized

          case Operator.Equivalent => normalized
          case Operator.Alternative => throw new Exception(s"Unresolved import alternative $this cannot be beta-normalized")
        }

      case Application(_, _) =>
        val Application(funcN, argN) = normalized

        def matchOrNormalize(expr: Expression, default: Expression = normalized)(matcher: PartialFunction[ExpressionScheme[Expression], Expression]): Expression =
          if (matcher.isDefinedAt(expr.scheme)) matcher(expr.scheme) else default

        funcN.scheme match {
          case ExprBuiltin(Builtin.NaturalBuild) => // Natural/build g = g Natural (λ(x : Natural) → x + 1) 0
            betaNormalize(argN(~Natural)(v("x") | ~Natural -> v("x") + NaturalLiteral(1))(NaturalLiteral(0)))
          case Application(Expression(Application(Expression(Application(Expression(ExprBuiltin(Builtin.NaturalFold)), Expression(NaturalLiteral(m)))), b)), g) =>
            // g (Natural/fold n b g argN)
            if (m == 0) argN else betaNormalize(g((~NaturalFold)(NaturalLiteral(m - 1))(b)(g)(argN)))
          case ExprBuiltin(Builtin.NaturalIsZero) => matchOrNormalize(argN) { case NaturalLiteral(a) => if (a == 0) ~True else ~False }
          case ExprBuiltin(Builtin.NaturalEven) => matchOrNormalize(argN) { case NaturalLiteral(a) => if (a % 2 == 0) ~True else ~False }
          case ExprBuiltin(Builtin.NaturalOdd) => matchOrNormalize(argN) { case NaturalLiteral(a) => if (a % 2 != 0) ~True else ~False }
          case ExprBuiltin(Builtin.NaturalShow) => matchOrNormalize(argN) { case NaturalLiteral(a) => TextLiteral.ofString(a.toString(10)) } // Convert a Natural number to a decimal string representation.
          case ExprBuiltin(Builtin.NaturalToInteger) => matchOrNormalize(argN) { case NaturalLiteral(a) => IntegerLiteral(a) }
          case Application(Expression(ExprBuiltin(Builtin.NaturalSubtract)), a) =>
            val aN = betaNormalize(a)
            (argN.scheme, aN.scheme) match { // subtract y x = x - y. If the result is negative, return 0.
              case (NaturalLiteral(x), _) if x == 0 => NaturalLiteral(0)
              case (_, NaturalLiteral(y)) if y == 0 => argN
              case (NaturalLiteral(x), NaturalLiteral(y)) =>
                val difference = x - y
                if (difference < 0) NaturalLiteral(0) else NaturalLiteral(difference)
              case _ if equivalent(argN, a) => NaturalLiteral(0)
              case _ => Application(Expression(Application(Expression(ExprBuiltin(Builtin.NaturalSubtract)), aN)), argN)
            }
          case ExprBuiltin(Builtin.TextShow) => matchOrNormalize(argN) { case TextLiteral(List(), string) => TextLiteral.ofString(textShow(string)) }
          case Application(Expression(Application(Expression(ExprBuiltin(Builtin.TextReplace)), needle)), replacement) =>
            (needle.scheme, replacement.scheme, argN.scheme) match {
              case (TextLiteral(List(), ""), _, _) | (_, _, TextLiteral(List(), "")) => argN // One more case of beta-normalization: empty haystack needs no replacement even if needle is not a TextLiteral.
              case (TextLiteral(List(), needleString), _, TextLiteral(List(), haystack)) =>
                val chunks = haystack.split(Pattern.quote(needleString), -1).toList

                def loop(chunks: List[String]): TextLiteral[Expression] = chunks match {
                  case Nil => TextLiteral.empty
                  case List(s) => TextLiteral.ofString(s)
                  case head :: tail =>
                    val tl = loop(tail)
                    TextLiteral((head, replacement) +: tl.interpolations, tl.trailing)
                }

                betaNormalize(loop(chunks))

              case _ => normalized
            }

          // TODO: any other cases where Application(_, _) can be simplified? Certainly if funcN is a Lambda?
          case _ => normalized
        }

      case Field(base, name) => ???
      case ProjectByLabels(base, labels) => ???
      case ProjectByType(base, by) => ???
      case Completion(base, target) => ???

      case With(data, pathComponents, body) => ???

      case TextLiteral(_, _) =>
        val TextLiteral(interpolationsN, trailing) = normalized

        // TODO: replace this with foldRight
        def loop(t: TextLiteral[Expression]): TextLiteral[Expression] = t.interpolations match {
          case (head, Expression(tl@TextLiteral(_, _))) :: next => TextLiteral.ofString[Expression](head) ++ tl ++ loop(TextLiteral(next, t.trailing))
          case (head, headExpr) :: next => TextLiteral(List((head, headExpr)), "") ++ loop(TextLiteral(next, t.trailing))
          case Nil => t
        }

        loop(TextLiteral(interpolationsN, trailing)) match {
          case TextLiteral(List(("", chunkN)), "") => chunkN
          case t => t
        }

      case RecordType(defs) => ???
      case RecordLiteral(defs) => ???
      case UnionType(defs) => ???
      case ShowConstructor(data) => ???

      case Import(_, _, _) => throw new Exception(s"Unresolved import $this cannot be beta-normalized")
    }
  }

  // https://github.com/dhall-lang/dhall-lang/blob/master/standard/equivalence.md
  def equivalent(x: Expression, y: Expression): Boolean =
    CBOR.exprToBytes(betaNormalize(alphaNormalize(x))) sameElements CBOR.exprToBytes(betaNormalize(alphaNormalize(y)))

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/type-inference.md
  def inferType(gamma: GammaTypeContext, expr: Expression): Expression = ???

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/imports.md
  def canonicalize(x: File): File = ???
  // TODO: implement other functions for import handling

}
