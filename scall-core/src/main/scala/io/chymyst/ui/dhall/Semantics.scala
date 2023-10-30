package io.chymyst.ui.dhall

import io.chymyst.ui.dhall.Syntax.Expression.v
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.Syntax.{Expression, ExpressionScheme, Natural}
import io.chymyst.ui.dhall.SyntaxConstants.Builtin.{ListFold, ListLength, Natural, NaturalFold, NaturalSubtract}
import io.chymyst.ui.dhall.SyntaxConstants.Constant.{False, True}
import io.chymyst.ui.dhall.SyntaxConstants.Operator.ListAppend
import io.chymyst.ui.dhall.SyntaxConstants.{Builtin, FieldName, File, Operator, VarName}

import java.util.regex.Pattern
import scala.util.chaining.scalaUtilChainingOps

object Semantics {

  final case class GammaTypeContext(defs: Seq[(VarName, Expression)])

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/shift.md
  def shift(positive: Boolean, x: VarName, minIndex: Natural, expr: Expression): Expression = {
    expr.scheme match {
      case Variable(name, index) =>
        val d = if (positive) 1 else -1
        if (name != x || index < minIndex) expr else Variable(name, index + d)

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
  // Report issue: The notation in the Haskell reference code is quite confusing. The names x, x', y need to be chosen more consistently.
  def substitute(expr: Expression, substVar: VarName, substIndex: Natural, substTarget: Expression): Expression = expr.scheme match {
    case Variable(name, index) => if (name == substVar && index == substIndex) substTarget else expr

    case Lambda(name, tipe, body) =>
      val newIndex = if (name != substVar) substIndex else substIndex + 1
      val newType = substitute(tipe, substVar, substIndex, substTarget)
      val newTarget = shift(true, name, 0, substTarget)
      val newBody = substitute(body, substVar, newIndex, newTarget)
      Lambda(name, newType, newBody)

    case Forall(name, tipe, body) =>
      val newIndex = if (name != substVar) substIndex else substIndex + 1
      val newType = substitute(tipe, substVar, substIndex, substTarget)
      val newTarget = shift(true, name, 0, substTarget)
      val newBody = substitute(body, substVar, newIndex, newTarget)
      Forall(name, newType, newBody)

    case Let(name, tipe, subst, body) =>
      val newIndex = if (name != substVar) substIndex else substIndex + 1
      val newType = tipe.map(substitute(_, substVar, substIndex, substTarget))
      val newSubst = substitute(subst, substVar, substIndex, substTarget)
      val newTarget = shift(true, name, 0, substTarget)
      val newBody = substitute(body, substVar, newIndex, newTarget)
      Let(name, newType, newSubst, newBody)

    case other => other.map(expression => substitute(expression, substVar, substIndex, substTarget))
  }

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/alpha-normalization.md
  def alphaNormalize(expr: Expression): Expression = expr.scheme match {
    case Variable(_, _) => expr

    case Lambda(name, tipe, body) => if (name == underscore) expr.map(_.alphaNormalized) else {
      val body1 = shift(true, underscore, 0, body)
      val body2 = substitute(body1, name, 0, Variable(underscore, 0))
      val body3 = shift(false, name, 0, body2)
      Lambda(underscore, tipe.alphaNormalized, body3.alphaNormalized)
    }

    case Forall(name, tipe, body) => if (name == underscore) expr.map(alphaNormalize) else {
      val body1 = shift(true, underscore, 0, body)
      val body2 = substitute(body1, name, 0, Variable(underscore, 0))
      val body3 = shift(false, name, 0, body2)
      Forall(underscore, tipe.alphaNormalized, body3.alphaNormalized)
    }

    case Let(name, tipe, subst, body) => if (name == underscore) expr.map(alphaNormalize) else {
      val body1 = shift(true, underscore, 0, body)
      val body2 = substitute(body1, name, 0, Variable(underscore, 0))
      val body3 = shift(false, name, 0, body2)
      Let(underscore, tipe.map(_.alphaNormalized), subst.alphaNormalized, body3.alphaNormalized)
    }

    case Import(_, _, _) => throw new Exception(s"alphaNormalize($expr): Unresolved imports cannot be α-normalized")

    case other => other.map(_.alphaNormalized)
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

  // TODO: implement and use a function that determines whether a given Dhall function will return literals when applied to literals. Implement such functions efficiently.
  // TODO: implement and use a function that determines which literals can be given to a function so that it will then ignore another (curried) argument. Use this to implement foldWhile efficiently.

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/beta-normalization.md
  def betaNormalize(expr: Expression): Expression = {
    lazy val normalizeArgs: ExpressionScheme[Expression] = expr.schemeWithBetaNormalizedArguments

    def matchOrNormalize(expr: Expression, default: => Expression = normalizeArgs)(matcher: PartialFunction[ExpressionScheme[Expression], Expression]): Expression =
      if (matcher.isDefinedAt(expr.scheme)) matcher(expr.scheme) else default

    expr.scheme match {
      // These expression types are already in beta-normal form.
      case Variable(_, _) | ExprBuiltin(_) | ExprConstant(_) | NaturalLiteral(_) | IntegerLiteral(_) | DoubleLiteral(_) |
           BytesLiteral(_) | DateLiteral(_, _, _) | TimeLiteral(_) | TimeZoneLiteral(_) =>
        expr

      // These expressions only need to normalize their arguments.
      case EmptyList(_) | NonEmptyList(_) | KeywordSome(_) | Lambda(_, _, _) | Forall(_, _, _) | Assert(_) => normalizeArgs

      case If(cond, ifTrue, ifFalse) =>
        lazy val If(condN, ifTrueN, ifFalseN) = normalizeArgs
        if (condN.scheme == ExprBuiltin(Builtin.True)) ifTrueN
        else if (condN.scheme == ExprBuiltin(Builtin.False)) ifFalseN
        else if (equivalent(ifTrue, ifFalse)) ifTrueN
        else normalizeArgs

      case Merge(record, update, tipe) => ???
      case ToMap(data, tipe) => ???

      case Annotation(data, tipe) =>
        val Annotation(data, _) = normalizeArgs
        data

      case ExprOperator(lop, op, rop) =>
        val ExprOperator(lopN, _, ropN) = normalizeArgs
        op match {
          case Operator.Or =>
            if (lopN.scheme == ExprBuiltin(Builtin.False) || ropN.scheme == ExprBuiltin(Builtin.True)) ropN
            else if (lopN.scheme == ExprBuiltin(Builtin.True) || ropN.scheme == ExprBuiltin(Builtin.False)) lopN
            else if (equivalent(lop, rop)) lopN
            else normalizeArgs

          case Operator.Plus => (lopN.scheme, ropN.scheme) match { // Simplified only for Natural arguments.
            case (NaturalLiteral(a), _) if a == 0 => ropN
            case (_, NaturalLiteral(b)) if b == 0 => lopN
            case (NaturalLiteral(a), NaturalLiteral(b)) => NaturalLiteral(a + b)
            case _ => normalizeArgs
          }

          case Operator.TextAppend => Expression(TextLiteral(List(("", lopN), ("", ropN)), "")).betaNormalized

          case Operator.ListAppend =>
            (lopN.scheme, ropN.scheme) match {
              case (EmptyList(_), _) => ropN
              case (_, EmptyList(_)) => lopN
              case (NonEmptyList(exprs1), NonEmptyList(exprs2)) => NonEmptyList(exprs1 ++ exprs2).betaNormalized
              case _ => normalizeArgs
            }

          case Operator.And =>
            if (lopN.scheme == ExprBuiltin(Builtin.False) || ropN.scheme == ExprBuiltin(Builtin.True)) lopN
            else if (lopN.scheme == ExprBuiltin(Builtin.True) || ropN.scheme == ExprBuiltin(Builtin.False)) ropN
            else if (equivalent(lop, rop)) lopN
            else normalizeArgs

          case Operator.CombineRecordTerms => ???
          case Operator.Prefer => ???
          case Operator.CombineRecordTypes => ???
          case Operator.Times => (lopN.scheme, ropN.scheme) match { // Simplified only for Natural arguments.
            case (NaturalLiteral(a), _) if a == 0 => NaturalLiteral(0)
            case (_, NaturalLiteral(b)) if b == 0 => NaturalLiteral(0)
            case (NaturalLiteral(a), _) if a == 1 => ropN
            case (_, NaturalLiteral(b)) if b == 1 => lopN
            case (NaturalLiteral(a), NaturalLiteral(b)) => NaturalLiteral(a * b)
            case _ => normalizeArgs
          }
          case Operator.Equal =>
            if (lopN.scheme == ExprBuiltin(Builtin.True)) ropN
            else if (ropN.scheme == ExprBuiltin(Builtin.True)) lopN
            else if (equivalent(lop, rop)) ExprBuiltin(Builtin.True)
            else normalizeArgs

          case Operator.NotEqual =>
            if (lopN.scheme == ExprBuiltin(Builtin.False)) ropN
            else if (ropN.scheme == ExprBuiltin(Builtin.False)) lopN
            else if (equivalent(lop, rop)) ExprBuiltin(Builtin.False)
            else normalizeArgs

          case Operator.Equivalent => normalizeArgs
          case Operator.Alternative => throw new Exception(s"Unresolved import alternative $this cannot be beta-normalized")
        }

      case Application(func, arg) =>
        lazy val argN = arg.betaNormalized
        // If funcN evaluates to a builtin name, and if it is fully applied to all required arguments, implement the builtin here.
        func.betaNormalized.scheme match {
          case ExprBuiltin(Builtin.NaturalBuild) => // Natural/build g = g Natural (λ(x : Natural) → x + 1) 0
            argN(~Natural)(v("x") | ~Natural -> v("x") + NaturalLiteral(1))(NaturalLiteral(0)).betaNormalized
          case Application(Expression(Application(Expression(Application(Expression(ExprBuiltin(Builtin.NaturalFold)), Expression(NaturalLiteral(m)))), b)), g) =>
            // g (Natural/fold n b g argN)
            if (m == 0) argN else g((~NaturalFold)(NaturalLiteral(m - 1))(b)(g)(argN)).betaNormalized
          case ExprBuiltin(Builtin.NaturalIsZero) => matchOrNormalize(argN) { case NaturalLiteral(a) => if (a == 0) ~True else ~False }
          case ExprBuiltin(Builtin.NaturalEven) => matchOrNormalize(argN) { case NaturalLiteral(a) => if (a % 2 == 0) ~True else ~False }
          case ExprBuiltin(Builtin.NaturalOdd) => matchOrNormalize(argN) { case NaturalLiteral(a) => if (a % 2 != 0) ~True else ~False }
          case ExprBuiltin(Builtin.NaturalShow) => matchOrNormalize(argN) { case NaturalLiteral(a) => TextLiteral.ofString(a.toString(10)) } // Convert a Natural number to a decimal string representation.
          case ExprBuiltin(Builtin.NaturalToInteger) => matchOrNormalize(argN) { case NaturalLiteral(a) => IntegerLiteral(a) }
          case Application(Expression(ExprBuiltin(Builtin.NaturalSubtract)), a) =>
            val aN = a.betaNormalized
            (argN.scheme, aN.scheme) match { // subtract y x = x - y. If the result is negative, return 0.
              case (NaturalLiteral(x), _) if x == 0 => NaturalLiteral(0)
              case (_, NaturalLiteral(y)) if y == 0 => argN
              case (NaturalLiteral(x), NaturalLiteral(y)) =>
                val difference = x - y
                if (difference < 0) NaturalLiteral(0) else NaturalLiteral(difference)
              case _ if equivalent(argN, a) => NaturalLiteral(0)
              case _ => (~NaturalSubtract)(aN)(argN)
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

                loop(chunks).betaNormalized

              case _ => normalizeArgs
            }

          case Application(Expression(ExprBuiltin(Builtin.ListBuild)), tipe) =>
            val freshName = "a"
            val a = v(freshName)
            val newType = shift(true, VarName(freshName), 0, tipe)
            // g (List A₀) (λ(a : A₀) → λ(as : List A₁) → [ a ] # as) ([] : List A₀) ⇥ b
            argN((~Builtin.List)(tipe))((a | tipe) -> (v("as") | (~Builtin.List)(newType)) -> Expression(NonEmptyList(Seq(a))).op(ListAppend)(v("as")))(Expression(EmptyList(tipe))).betaNormalized

          case Application(Expression(Application(Expression(Application(Expression(Application(Expression(ExprBuiltin(ListFold)), typeA0)), expressions)), b)), g) =>
            expressions match {
              case Expression(NonEmptyList(exprs)) => // Guaranteed a non-empty list.
                val rest = if (exprs.length == 1) Expression(EmptyList(typeA0)) else Expression(NonEmptyList(exprs.tail))
                // g a (List/fold A₀ [ as… ] B g b₀) ⇥ b₁
                g(exprs.head)((~ListFold)(typeA0)(rest)(g)(argN)).betaNormalized
              case Expression(EmptyList(_)) => b.betaNormalized
            }

          case Application(Expression(ExprBuiltin(Builtin.ListLength)), _) => matchOrNormalize(argN) {
            case EmptyList(_) => NaturalLiteral(0)
            case NonEmptyList(exprs) => NaturalLiteral(exprs.length)
            case ExprOperator(lop, Operator.ListAppend, rop) => (~ListLength)(lop.betaNormalized).betaNormalized.op(Operator.Plus)((~ListLength)(rop.betaNormalized).betaNormalized).betaNormalized // TODO: report issue to add this reduction rule to the standard?
          }

          case Application(Expression(ExprBuiltin(Builtin.ListHead)), tipe) => matchOrNormalize(argN) {
            case EmptyList(_) => (~Builtin.None)(tipe)
            case NonEmptyList(exprs) => KeywordSome(exprs.head)

            // TODO: report issue to add this reduction rule to the standard?
            // Simplify a ListAppend when (List/head lop) evaluates to something concrete.
            case ExprOperator(lop, Operator.ListAppend, rop) => matchOrNormalize((~Builtin.ListHead)(lop.betaNormalized)) {
              case Application(Expression(ExprBuiltin(Builtin.None)), _) => (~Builtin.ListHead)(rop.betaNormalized).betaNormalized
              case KeywordSome(r) => r.betaNormalized
            }
          }

          case Application(Expression(ExprBuiltin(Builtin.ListLast)), tipe) => matchOrNormalize(argN) {
            case EmptyList(_) => (~Builtin.None)(tipe)
            case NonEmptyList(exprs) => KeywordSome(exprs.last)
          }

          case Application(Expression(ExprBuiltin(Builtin.ListIndexed)), tipe) => matchOrNormalize(argN) {
            case EmptyList(_) => EmptyList((~Builtin.List)(Expression(RecordType(Seq((FieldName("index"), ~Builtin.Natural), (FieldName("value"), tipe))))))
            case NonEmptyList(exprs) => NonEmptyList(exprs.zipWithIndex.map { case (e, index) =>
              Expression(RecordLiteral(Seq((FieldName("index"), NaturalLiteral(index)), (FieldName("value"), e))))
            })
          }

          case Application(Expression(ExprBuiltin(Builtin.ListReverse)), _) => matchOrNormalize(argN) {
            case EmptyList(t) => EmptyList(t)
            case NonEmptyList(exprs) => NonEmptyList(exprs.reverse)
          }

          case Lambda(name, _, body) =>
            val a1 = shift(true, name, 0, arg)
            val b1 = substitute(body, name, 0, a1)
            val b2 = shift(false, name, 0, b1)
            b2.betaNormalized

          // let name : A = subst in body is equivalent to (λ(name : A) → body) subst
          case Let(VarName(name), _, subst, body) => (((v(name) | ~Natural) -> body)(subst)).betaNormalized

          // TODO: all other cases where Application(_, _) can be simplified
          case _ => normalizeArgs
        }

      case Field(base, name) => ???
      case ProjectByLabels(base, labels) => ???
      case ProjectByType(base, by) => ???
      case Completion(base, target) => ???

      case With(data, pathComponents, body) => ???

      case TextLiteral(_, _) =>
        val TextLiteral(interpolationsN, trailing) = normalizeArgs

        // TODO: replace this code by foldRight somehow?
        def loop(t: TextLiteral[Expression]): TextLiteral[Expression] = t.interpolations match {
          case (head, Expression(tl@TextLiteral(_, _))) :: next => TextLiteral.ofString[Expression](head) ++ tl ++ loop(TextLiteral(next, t.trailing))
          case (head, headExpr) :: next => TextLiteral(List((head, headExpr)), "") ++ loop(TextLiteral(next, t.trailing))
          case Nil => t
        }

        loop(TextLiteral(interpolationsN, trailing)) match {
          case TextLiteral(List(("", chunkN)), "") => chunkN
          case t => t
        }

      case RecordType(defs) => normalizeArgs.asInstanceOf[RecordType[Expression]].sorted
      case RecordLiteral(defs) => ???
      case UnionType(defs) => normalizeArgs.asInstanceOf[UnionType[Expression]].sorted
      case ShowConstructor(data) =>
        matchOrNormalize(data) {
          case Application(Expression(Field(Expression(UnionType(_)), FieldName(name))), _) => TextLiteral.ofString(name)
          case Field(Expression(UnionType(_)), FieldName(name)) => TextLiteral.ofString(name)
          // Builtin union type: Optional
          case Application(Expression(ExprBuiltin(Builtin.None)), _) => TextLiteral.ofString(Builtin.None.entryName)
          case KeywordSome(_) => TextLiteral.ofString("Some")
        }

      case Import(_, _, _) => throw new Exception(s"Unresolved import $this cannot be beta-normalized")
    }
  }

  // https://github.com/dhall-lang/dhall-lang/blob/master/standard/equivalence.md
  def equivalent(x: Expression, y: Expression): Boolean =
    CBOR.exprToBytes(x.alphaNormalized.betaNormalized) sameElements CBOR.exprToBytes(y.alphaNormalized.betaNormalized)

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/type-inference.md
  def inferType(gamma: GammaTypeContext, expr: Expression): Expression = ???

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/imports.md
  def canonicalize(x: File): File = ???
  // TODO: implement other functions for import handling

}
