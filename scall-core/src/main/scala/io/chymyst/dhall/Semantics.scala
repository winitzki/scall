package io.chymyst.dhall

import io.chymyst.dhall.CBORmodel.CBytes
import io.chymyst.dhall.Semantics.BetaNormalizingOptions.optionsForAssertChecking
import io.chymyst.dhall.Syntax.Expression.{toExpressionScheme, v}
import io.chymyst.dhall.Syntax.ExpressionScheme._
import io.chymyst.dhall.Syntax.{Expression, ExpressionScheme, Natural, PathComponent}
import io.chymyst.dhall.SyntaxConstants.Builtin.{ListFold, ListLength, Natural, NaturalSubtract}
import io.chymyst.dhall.SyntaxConstants.Constant.{False, True}
import io.chymyst.dhall.SyntaxConstants.Operator.ListAppend
import io.chymyst.dhall.SyntaxConstants._
import io.chymyst.tc.Applicative
import io.chymyst.tc.Applicative.ApplicativeOps

import java.security.MessageDigest
import java.util.regex.Pattern
import scala.annotation.tailrec
import scala.language.implicitConversions
import scala.util.chaining.scalaUtilChainingOps

object Semantics {
  // TODO: make sure this algorithm is correct for variables with de Bruijn indices!
  private def freeVarsForLambda(name: VarName, tipe: Option[Expression], body: Expression): FreeVars[Expression] = {
    val freeVarsInType: Set[VarName] = tipe.map(t => freeVars(t).names).getOrElse(Set())
    val freeVarsInBody: Set[VarName] = freeVars(shift(true, name, 0, body)).names
    FreeVars(freeVarsInType union (freeVarsInBody -- Set(name)))
  }

  def freeVars(expr: Expression): FreeVars[Expression] = expr.scheme match {
    case Variable(name, index)        => FreeVars(Set(name).filter(_ => index == 0))
    case Lambda(name, tipe, body)     => freeVarsForLambda(name, Some(tipe), body)
    case Forall(name, tipe, body)     => freeVarsForLambda(name, Some(tipe), body)
    case Let(name, tipe, subst, body) => FreeVars(freeVarsForLambda(name, tipe, body).names union freeVars(subst).names)
    case other                        => other.traverse(expr => freeVars(expr)).map(Expression.apply)
  }

  def computeHash(bytes: Array[Byte]): String =
    CBytes.byteArrayToHexString(MessageDigest.getInstance("SHA-256").digest(bytes)).toLowerCase

  def semanticHash(expr: Expression, currentFile: java.nio.file.Path): String =
    computeHash(expr.resolveImports(currentFile).alphaNormalized.betaNormalized.toCBORmodel.encodeCbor2)

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
      val newIndex  = if (name != substVar) substIndex else substIndex + 1
      val newType   = substitute(tipe, substVar, substIndex, substTarget)
      val newTarget = shift(true, name, 0, substTarget)
      val newBody   = substitute(body, substVar, newIndex, newTarget)
      Lambda(name, newType, newBody)

    case Forall(name, tipe, body) =>
      val newIndex  = if (name != substVar) substIndex else substIndex + 1
      val newType   = substitute(tipe, substVar, substIndex, substTarget)
      val newTarget = shift(true, name, 0, substTarget)
      val newBody   = substitute(body, substVar, newIndex, newTarget)
      Forall(name, newType, newBody)

    case Let(name, tipe, subst, body) =>
      val newIndex  = if (name != substVar) substIndex else substIndex + 1
      val newType   = tipe.map(substitute(_, substVar, substIndex, substTarget))
      val newSubst  = substitute(subst, substVar, substIndex, substTarget)
      val newTarget = shift(true, name, 0, substTarget)
      val newBody   = substitute(body, substVar, newIndex, newTarget)
      Let(name, newType, newSubst, newBody)

    case other => other.map(expression => substitute(expression, substVar, substIndex, substTarget))
  }

  def alphaNormalize(expr: Expression): Expression = cacheAlphaNormalize.getOrElseUpdate(expr, alphaNormalizeUncached(expr))

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/alpha-normalization.md
  private def alphaNormalizeUncached(expr: Expression): Expression = expr.scheme match {
    case Variable(_, _) => expr

    case Lambda(name, tipe, body) =>
      if (name == underscore) expr.map(_.alphaNormalized)
      else {
        val body1 = shift(true, underscore, 0, body)
        val body2 = substitute(body1, name, 0, Variable(underscore, 0))
        val body3 = shift(false, name, 0, body2)
        Lambda(underscore, tipe.alphaNormalized, body3.alphaNormalized)
      }

    case Forall(name, tipe, body) =>
      if (name == underscore) expr.map(alphaNormalize)
      else {
        val body1 = shift(true, underscore, 0, body)
        val body2 = substitute(body1, name, 0, Variable(underscore, 0))
        val body3 = shift(false, name, 0, body2)
        Forall(underscore, tipe.alphaNormalized, body3.alphaNormalized)
      }

    case Let(name, tipe, subst, body) =>
      if (name == underscore) expr.map(alphaNormalize)
      else {
        val body1 = shift(true, underscore, 0, body)
        val body2 = substitute(body1, name, 0, Variable(underscore, 0))
        val body3 = shift(false, name, 0, body2)
        Let(underscore, tipe.map(_.alphaNormalized), subst.alphaNormalized, body3.alphaNormalized)
      }

    case Import(_, _, _) => throw new Exception(s"alphaNormalize($expr): Unresolved imports cannot be alpha-normalized")

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
    .flatMap { c => if (c.toInt < 32) String.format("\\u00%02x", c.toInt) else String.valueOf(c) }
    .pipe(s => "\"" + s + "\"")

  // TODO: implement and use a function that determines whether a given Dhall function will return literals when applied to literals. Implement such functions efficiently. -- Isn't every Dhall function in this class?
  // TODO: implement and use a function that determines which literals can be given to a function so that it will then ignore another (curried) argument. Use this to implement foldWhile efficiently.

  private def mergeRecordPartsPreferringSecond(
    defs1: Seq[(FieldName, Expression)],
    operator: Operator,
    defs2: Seq[(FieldName, Expression)],
  ): Seq[(FieldName, Expression)] =
    (defs1 ++ defs2).groupMapReduce(_._1)(_._2)((l, r) => l.op(operator)(r)).toSeq.sortBy(_._1.name)

  val maxCacheSize: Option[Int] = Some(2000000) // Specify `None` for no limit.

  // Use this case class as a dictionary key for caching beta normalization, because the results are different depending on options.
  final case class ExprWithOptions(expr: Expression, options: BetaNormalizingOptions)

  val cacheBetaNormalize = IdempotentCache("beta-normalization cache", ObservedCache.createCache[ExprWithOptions, ExprWithOptions](maxCacheSize))

  val cacheAlphaNormalize = IdempotentCache("alpha-normalization cache", ObservedCache.createCache[Expression, Expression](maxCacheSize))

  def betaNormalizeAndExpand(expr: Expression, options: BetaNormalizingOptions): Expression =
    cacheBetaNormalize.getOrElseUpdate(ExprWithOptions(expr, options), ExprWithOptions(betaNormalizeUncached(expr, options).expr, options)).expr

  /** Options for beta-normalization.
    *
    * @param stopExpanding
    *   Enable the optimization to stop expanding the normal form if it grows too much.
    * @param stopExpandingIfFreeVars
    *   Stop expanding the normal form if it contains free variables. (Requires `stopExpanding`.)
    * @param stopExpandingIfAtLeast
    *   Stop expanding if the sub-expression count is at least this. (Requires `stopExpanding`.)
    * @param stopExpandingIfGrewBy
    *   Stop expanding if the sub-expression count grew by at least this. (Requires `stopExpanding`.)
    * @param etaReduce
    *   Rewrite `λ(x : A) → f x` into `f` if `f` does not contain `x` as a free variable.
    * @param rewriteAssociativity
    *   Rewrite `x op (y op z)` into `(x op y) op z` for all associative operations `op`.
    * @param rewriteRecordIdentity
    *   Rewrite `{ a = x.a, b = x.b }` into `x` when `x` has the record type with fields `a`, `b`.
    * @param rewriteMergeOfMerge
    */
  final case class BetaNormalizingOptions(
    stopExpanding: Boolean = false,
    stopExpandingIfFreeVars: Boolean = false,
    stopExpandingIfAtLeast: Int = 500,
    stopExpandingIfGrewBy: Int = 0,
    etaReduce: Boolean = false,
    rewriteAssociativity: Boolean = false,
    rewriteRecordIdentity: Boolean = false,
    rewriteMergeIdentity: Boolean = false,
    rewriteMergeOfMerge: Boolean = false,
  )

  object BetaNormalizingOptions {
    val default = BetaNormalizingOptions()

    val optionsForAssertChecking = BetaNormalizingOptions(
      etaReduce = true,
      rewriteAssociativity = true,
      rewriteRecordIdentity = true,
      rewriteMergeIdentity = true,
      rewriteMergeOfMerge = true,
    )
  }

  private def betaNormalizeOrUnexpand(expr: Expression, options: BetaNormalizingOptions): Expression =
    cacheBetaNormalize.get(ExprWithOptions(expr, options)) match {
      case Some(normalized) => normalized.expr
      case None             =>
        val BNResult(normalized, didShortcut) = betaNormalizeUncached(expr, options)
        if (didShortcut) {
          //        println(s"DEBUG in normalizing $expr, after stopExpanding shortcut, do not cache the result $normalized")
          normalized
        } else cacheBetaNormalize.getOrElseUpdate(ExprWithOptions(expr, options), ExprWithOptions(normalized, options)).expr
    }

  private final case class BNResult(expr: Expression, didStopExpanding: Boolean = false)

  /** Determine if beta-normalization should stop expanding an expression. This is done when the expression grows too much during beta-normalization.
    *
    * @param oldExpr
    *   Expression before a beta-normalization step.
    * @param newExpr
    *   Expression after the beta-normalization step.
    * @param options
    *   Beta-normalization options.
    * @return
    *   `true` if the expression was determined to have "grown too much".
    */
  private def needToStopExpanding(oldExpr: => Expression, newExpr: => Expression, options: BetaNormalizingOptions): Boolean = options.stopExpanding && {
    lazy val oldLength = oldExpr.exprCount
    lazy val newLength = newExpr.exprCount
    // TODO: perhaps enable this optimization. See https://github.com/dhall-lang/dhall-lang/issues/1213#issuecomment-1855878600
    val hasFreeVars    = options.stopExpandingIfFreeVars && Semantics.freeVars(oldExpr).names.nonEmpty

    val result = hasFreeVars || (newLength >= oldLength + options.stopExpandingIfGrewBy && newLength > options.stopExpandingIfAtLeast)
    // if (result) println(s"DEBUG: stop-expanding shortcut detected with $oldExpr")
    result
  }

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/beta-normalization.md
  // stopExpanding = true means: in betaNormalize(Application f arg) we will cut short beta-normalizing Natural/fold or List/fold inside `f` if the result starts growing.
  @tailrec
  private def betaNormalizeUncached(expr: Expression, options: BetaNormalizingOptions): BNResult = {
    //        if (expr.print contains "Natural/fold")
    //          println(s"DEBUG betaNormalizeUncached(${expr.print}, stopExpanding = ${options.stopExpanding})")
    implicit def toBNResult(e: Expression): BNResult = BNResult(e)

    implicit def toBNResultFromScheme(e: ExpressionScheme[Expression]): BNResult = BNResult(e)

    def bn(e: Expression): Expression = betaNormalizeOrUnexpand(e, options)

    def bnStopExpanding(e: Expression): Expression = betaNormalizeOrUnexpand(e, options = options.copy(stopExpanding = true))

    lazy val normalizeArgs: ExpressionScheme[Expression] = expr.scheme.map(betaNormalizeOrUnexpand(_, options))

    // if (stopExpanding) println(s"DEBUG beta-normalize $expr, stopExpanding = $stopExpanding")
    def matchOrNormalize(expr: Expression, default: => Expression = normalizeArgs)(
      matcher: PartialFunction[ExpressionScheme[Expression], Expression]
    ): Expression =
      matcher.applyOrElse(bn(expr).scheme, { (_: ExpressionScheme[Expression]) => default })

    expr.scheme match {
      // These expression types are already in beta-normal form.
      case Variable(_, _) | ExprBuiltin(_) | ExprConstant(_) | NaturalLiteral(_) | IntegerLiteral(_) | DoubleLiteral(_) | BytesLiteral(_) |
          DateLiteral(_, _, _) | TimeLiteral(_, _, _, _) | TimeZoneLiteral(_) =>
        expr

      // These expressions only need to normalize their arguments.
      case EmptyList(_) | NonEmptyList(_) | KeywordSome(_) | Forall(_, _, _) | Assert(_) => normalizeArgs // Lambda(_, _, _) |

      case Lambda(name, tipe, body)  =>
        val bodyNotExpanded                     = bnStopExpanding(body)
        lazy val lambdaWithBetaReducedArguments = Lambda(name, bn(tipe), bodyNotExpanded)
        if (options.etaReduce) {
          bodyNotExpanded.scheme match {
            // TODO: report issue, document the new eta-reduction rules in the Dhall standard.
            // Eta reduction: λ(x : Bool) → f x will be reduced to just f, downshifting free occurrences of x in f.
            // See discussion: https://github.com/dhall-lang/dhall-lang/issues/1376
            case Application(head, Expression(Variable(`name`, index))) if index == 0 =>
              if (freeVars(head).names contains name) { // We may not eta-reduce if the head contains a free occurrence of the variable.
                lambdaWithBetaReducedArguments
              } else {
                val headShifted = shift(positive = false, name, 1, head)
                bn(headShifted)
              }

            // Optimization: do not expand Natural/fold or List/fold under Lambda if the argument is growing.
            case _                                                                    => lambdaWithBetaReducedArguments
          }
        } else lambdaWithBetaReducedArguments
      // `let name : A = subst in body` is equivalent to `(λ(name : A) → body) subst`
      // We use Natural as the type here, because betaNormalize of Application(Lambda(...),...) ignores the type annotation inside Lambda().
      case Let(name, _, subst, body) => ((v(name.name) | ~Natural) -> body)(subst) pipe bn

      case If(cond, ifTrue, ifFalse) =>
        if (bn(cond).scheme == ExprConstant(Constant.True)) ifTrue.pipe(bn)
        else if (bn(cond).scheme == ExprConstant(Constant.False)) ifFalse.pipe(bn)
        else if (bn(ifFalse).scheme == ExprConstant(Constant.False) && bn(ifTrue).scheme == ExprConstant(Constant.True))
          cond.pipe(bn)
        else if (equivalent(ifTrue, ifFalse)) ifTrue.pipe(bn)
        else normalizeArgs

      case Merge(record, update, _) =>
        matchOrNormalize(record) { case r @ RecordLiteral(_) =>
          // If all outputs of handlers are the same, eliminate merge.
          // TODO report issue: add this beta-normalization rule to standard
          r.defs.headOption match {
            case Some((_, output))
                if r.defs.forall(_._2 == output) // Simple equality of case classes.
                =>
              output
            case _ => // Record is empty, or some outputs are different.
              // TODO: eliminate merge if it is an identity function

              // TODO: eliminate nested merge if all outputs are explicit union constructors

              matchOrNormalize(update) {
                case Application(Expression(Field(Expression(UnionType(_)), x)), a) => (r.lookup(x).get)(a).pipe(bn)
                case Field(Expression(UnionType(_)), x)                             => r.lookup(x).get
                case KeywordSome(a)                                                 => (r.lookup(FieldName("Some")).get)(a).pipe(bn)
                case Application(Expression(ExprBuiltin(Builtin.None)), _)          => r.lookup(FieldName("None")).get
              }
          }
        }

      case ToMap(Expression(RecordLiteral(Seq())), Some(tipe)) => EmptyList(tipe.pipe(bn))
      case ToMap(data, _)                                      =>
        matchOrNormalize(data) { case RecordLiteral(defs) =>
          NonEmptyList(defs.map { case (name, expr) =>
            Expression(RecordLiteral(Seq((FieldName("mapKey"), TextLiteral.ofString(name.name)), (FieldName("mapValue"), expr.pipe(bn)))))
          })
        }

      case Annotation(data, _) => data.pipe(bn)

      case ExprOperator(lop, op, rop) =>
        lazy val ExprOperator(lopNbeforeRewrite, _, ropNbeforeRewrite) = normalizeArgs

        lazy val (lopN, ropN) = if (options.rewriteAssociativity) {
          ropNbeforeRewrite.scheme match {
            case ExprOperator(lopNested, `op`, ropNested) =>
              // Rewrite a right-associated operator expression:
              // (lopNbeforeRewrite `op` (lopNested `op` ropNested))
              // into a left-associated operator expression:
              // ((lopNbeforeRewrite `op` lopNested) `op` ropNested)
              val newLop = Expression(ExprOperator(lopNbeforeRewrite, op, lopNested))
              val newRop = ropNested
              (newLop, newRop)

            case _ => (lopNbeforeRewrite, ropNbeforeRewrite)
          }
        } else (lopNbeforeRewrite, ropNbeforeRewrite)

        lazy val normalizeArgsRewritten = Expression(ExprOperator(lopN, op, ropN))

        // Make sure we do not evaluate Bool expressions unnecessarily.
        def booleans(ifLeftFalse: => Expression, ifLeftTrue: => Expression): Expression =
          if (lopN.scheme == ExprConstant(Constant.False) || ropN.scheme == ExprConstant(Constant.True)) ifLeftFalse
          else if (lopN.scheme == ExprConstant(Constant.True) || ropN.scheme == ExprConstant(Constant.False)) ifLeftTrue
          else if (equivalent(lopN, ropN)) ropN
          else normalizeArgsRewritten

        op match {
          case Operator.Or => booleans(ropN, lopN)

          case Operator.And => booleans(lopN, ropN)

          case Operator.Plus =>
            (lopN.scheme, ropN.scheme) match { // Simplified only for Natural arguments.
              case (NaturalLiteral(a), _) if a == 0       => ropN
              case (_, NaturalLiteral(b)) if b == 0       => lopN
              case (NaturalLiteral(a), NaturalLiteral(b)) => NaturalLiteral(a + b)
              case _                                      => normalizeArgsRewritten
            }

          case Operator.TextAppend => Expression(TextLiteral(List(("", lopN), ("", ropN)), "")).pipe(bn)

          case Operator.ListAppend =>
            (lopN.scheme, ropN.scheme) match {
              case (EmptyList(_), _)                            => ropN
              case (_, EmptyList(_))                            => lopN
              case (NonEmptyList(exprs1), NonEmptyList(exprs2)) => Expression(NonEmptyList(exprs1 ++ exprs2)).pipe(bn)
              case _                                            => normalizeArgsRewritten
            }

          case Operator.CombineRecordTerms =>
            (lopN.scheme, ropN.scheme) match {
              case (RecordLiteral(Seq()), _)                    => ropN
              case (_, RecordLiteral(Seq()))                    => lopN
              case (RecordLiteral(defs1), RecordLiteral(defs2)) =>
                Expression(RecordLiteral(mergeRecordPartsPreferringSecond(defs1, Operator.CombineRecordTerms, defs2)))
                  .pipe(bn) // TODO report issue that we need to beta-normalize this, otherwise tests fail
              case _                                            => normalizeArgsRewritten
            }

          case Operator.Prefer =>
            (lopN.scheme, ropN.scheme) match {
              case (RecordLiteral(Seq()), _)                    => ropN
              case (_, RecordLiteral(Seq()))                    => lopN
              case (RecordLiteral(defs1), RecordLiteral(defs2)) =>
                // Do not need to beta-normalize the resulting RecordLiteral.
                val mergedFields =
                  (defs1.toMap ++ defs2.toMap) // The operation ++ on Map prefers the second map's value when keys are the same.
                    .toSeq.sortBy(_._1.name)
                RecordLiteral(mergedFields)    // .pipe(bn)  - not needed here.
              case _ if equivalent(lopN, ropN)                  =>
                lopN // TODO report issue: beta-normalization.md does not include this rule in Haskell code after `betaNormalize (Operator ls₀ Prefer rs₀)`
              case _                                            => normalizeArgsRewritten
            }

          case Operator.CombineRecordTypes =>
            (lopN.scheme, ropN.scheme) match {
              case (RecordType(Seq()), _)                 => ropN
              case (_, RecordType(Seq()))                 => lopN
              case (RecordType(defs1), RecordType(defs2)) =>
                Expression(RecordType(mergeRecordPartsPreferringSecond(defs1, Operator.CombineRecordTypes, defs2)))
                  .pipe(bn) // TODO report issue that we need to beta-normalize this, otherwise tests fail.
              case _                                      => normalizeArgsRewritten
            }

          case Operator.Times =>
            (lopN.scheme, ropN.scheme) match { // Simplified only for Natural arguments.
              case (NaturalLiteral(a), _) if a == 0       => NaturalLiteral(0)
              case (_, NaturalLiteral(b)) if b == 0       => NaturalLiteral(0)
              case (NaturalLiteral(a), _) if a == 1       => ropN
              case (_, NaturalLiteral(b)) if b == 1       => lopN
              case (NaturalLiteral(a), NaturalLiteral(b)) => NaturalLiteral(a * b)
              case _                                      => normalizeArgsRewritten
            }
          case Operator.Equal =>
            if (lopN.scheme == ExprConstant(Constant.True)) ropN
            else if (ropN.scheme == ExprConstant(Constant.True)) lopN
            else if (equivalent(lop, rop)) ExprConstant(Constant.True)
            else normalizeArgs

          case Operator.NotEqual =>
            if (lopN.scheme == ExprConstant(Constant.False)) ropN
            else if (ropN.scheme == ExprConstant(Constant.False)) lopN
            else if (equivalent(lop, rop)) ExprConstant(Constant.False)
            else normalizeArgsRewritten

          case Operator.Equivalent => normalizeArgsRewritten

          case Operator.Alternative => throw new Exception(s"Unresolved import alternative in $expr cannot be beta-normalized")
        }

      case Application(func, arg) =>
        lazy val argN = arg.pipe(bn)
        // If funcN evaluates to a builtin name, and if it is fully applied to all required arguments, implement the builtin here.

        // While expanding the function head (`func`), do not expand when expressions contain free vars. This is an optimization.
        // TODO report issue - add this optimization to the Dhall standard document

        betaNormalizeOrUnexpand(func, options.copy(stopExpandingIfFreeVars = true)).scheme match {
          case ExprBuiltin(Builtin.NaturalBuild)                                => // Natural/build g = g Natural (λ(x : Natural) → x + 1) 0
            argN(~Natural)((v("x") | ~Natural) -> (v("x") + NaturalLiteral(1)))(NaturalLiteral(0)).pipe(bn)
          case Application(
                Expression(Application(Expression(Application(Expression(ExprBuiltin(Builtin.NaturalFold)), Expression(NaturalLiteral(m)))), b)),
                g,
              ) =>
            // Natural/fold m b g argN = g (Natural/fold (m-1) b g argN)
            // We try to optimize this because it's very slow.
            // Assume that `currentResult` is already beta-normalized.
            @tailrec def loop(currentResult: => Expression, counter: BigInt): BNResult = {
              // Loop invariant: currentResult == g(g(...g(argN)...)) with `counter` repetitions of `g`.
              if (counter >= m) currentResult
              else {
                val newResult = betaNormalizeOrUnexpand(g(currentResult), options) // TODO: what normalization options should be used here?
                if (newResult == currentResult) { // Simple equality of case classes.
                  // Shortcut: the result did not change after applying `g` and normalizing, so no need to continue looping.
                  // We use a simple comparison of case classes, not the `equivalence` check, because the expressions were already normalized.
                  currentResult
                } else if (needToStopExpanding(currentResult, newResult, options)) {
                  // If the beta-normalized result grew in size, we return the unevaluated intermediate result:
                  // We are calculating g(g(...g(argN)...)) with `m` repetitions of `g`.
                  // So far, we have calculated currentResult = g(g(...g(argN)...)) with `counter` repetitions of `g`.
                  // The remaining calculation is g(g(...g(currentResult)...)) with `m-counter` repetitions of `g`.
                  // In Dhall, this is `Natural/fold (m-counter) b g currentResult`.
                  val unevaluatedIntermediateResult = (~Builtin.NaturalFold)(NaturalLiteral(m - counter))(b)(g)(currentResult)
                  //                  println(s"DEBUG detected shortcut stopExpanding = true for expression:\n${unevaluatedIntermediateResult.print}")
                  BNResult(unevaluatedIntermediateResult, didStopExpanding = true)
                } else {
                  loop(newResult, counter + 1)
                }
              }
            }

            loop(currentResult = argN, counter = BigInt(0))

          // TODO: perhaps add a reduction rule for NaturalIsZero (1 + x) returning False, etc?
          case ExprBuiltin(Builtin.NaturalIsZero)                               => matchOrNormalize(arg) { case NaturalLiteral(a) => if (a == 0) ~True else ~False }
          case ExprBuiltin(Builtin.NaturalEven)                                 => matchOrNormalize(arg) { case NaturalLiteral(a) => if (a % 2 == 0) ~True else ~False }
          case ExprBuiltin(Builtin.NaturalOdd)                                  => matchOrNormalize(arg) { case NaturalLiteral(a) => if (a % 2 != 0) ~True else ~False }
          // NaturalShow is defined later.
          case ExprBuiltin(Builtin.NaturalToInteger)                            => matchOrNormalize(arg) { case NaturalLiteral(a) => IntegerLiteral(a) }
          case Application(Expression(ExprBuiltin(Builtin.NaturalSubtract)), a) =>
            val aN = a.pipe(bn)
            (argN.scheme, aN.scheme) match { // subtract y x = x - y. If the result is negative, return 0.
              case (NaturalLiteral(x), _) if x == 0       => NaturalLiteral(0)
              case (_, NaturalLiteral(y)) if y == 0       => argN
              case (NaturalLiteral(x), NaturalLiteral(y)) =>
                val difference = x - y
                if (difference < 0) NaturalLiteral(0) else NaturalLiteral(difference)
              case _ if equivalent(argN, a)               => NaturalLiteral(0)
              case _                                      => (~NaturalSubtract)(aN)(argN)
            }

          case ExprBuiltin(Builtin.TextShow) => matchOrNormalize(arg) { case TextLiteral(List(), string) => TextLiteral.ofString(textShow(string)) }

          case Application(Expression(Application(Expression(ExprBuiltin(Builtin.TextReplace)), needle)), replacement) =>
            (needle.scheme, replacement.scheme, argN.scheme) match {
              case (TextLiteral(List(), ""), _, _) | (_, _, TextLiteral(List(), ""))     =>
                argN // TODO report issue: One more case of beta-normalization: empty haystack needs no replacement even if needle is not a TextLiteral.
              case (TextLiteral(List(), needleString), _, TextLiteral(List(), haystack)) =>
                val chunks = haystack.split(Pattern.quote(needleString), -1).toList

                def loop(chunks: List[String]): TextLiteral[Expression] = chunks match {
                  // case Nil          => TextLiteral.empty // This case will never occur because split("", -1) never produces an empty array.
                  case List(s)      => TextLiteral.ofString(s)
                  case head :: tail => // This `tail` is never empty because we already matched on a 1-element list.
                    val tl = loop(tail)
                    TextLiteral((head, replacement) +: tl.interpolations, tl.trailing)
                }

                Expression(loop(chunks)).pipe(bn)

              case _ => normalizeArgs
            }

          case Application(Expression(ExprBuiltin(Builtin.ListBuild)), tipe) =>
            // The Dhall standard and the tests require the names "a" and "as".
            val freshName = "a"
            val a         = v(freshName)
            val aseq      = v("as")

            val newType = shift(true, VarName(freshName), 0, tipe)
            // g (List A₀) (λ(a : A₀) → λ(as : List A₁) → [ a ] # as) ([] : List A₀) ⇥ b
            argN((~Builtin.List)(tipe))(
              (a | tipe)                          -> (
                (aseq | (~Builtin.List)(newType)) ->
                  Expression(NonEmptyList(Seq(a))).op(ListAppend)(aseq)
              )
            )(Expression(EmptyList((~Builtin.List)(tipe)))).pipe(bn)

          case Application(
                Expression(Application(Expression(Application(Expression(Application(Expression(ExprBuiltin(ListFold)), typeA0)), expressions)), typeB)),
                g,
              ) =>
            matchOrNormalize(expressions) {
              // List/fold A₀ ([] : List A₁) B g b₀  ⇥  b₁
              case EmptyList(_) => argN

              // We need to beta-normalize the expression `List/fold typeA0 expressions b g argN`.
              // Try to optimize this as well as Natural/fold because beta-normalization is extremely slow.
              case NonEmptyList(exprs) => // Guaranteed a non-empty list.
                exprs match {
                  case Seq(head) => g(head)(argN).pipe(bn)
                  case _         => exprs.foldRight(argN) { case (a, rest) => g(a)(rest).pipe(bn) }.pipe(bn)
                }
              /*
                val rest = if (exprs.length == 1) Expression(EmptyList(typeA0)) else Expression(NonEmptyList(exprs.tail))
                //                  println(s"DEBUG ${LocalDateTime.now} betaNormalizing List/fold (${typeA0.print}) ${exprs.map(_.print).mkString("[ ", ", ", " ]")} (${b.print}) (${g.print}) (${argN.print})")
                // List/fold A₀ ([] : List A₁) B g b₀  ⇥  g a (List/fold A₀ [ as… ] B g b₀)
                (g(exprs.head)((~ListFold)(typeA0)(rest)(typeB)(g)(argN))).pipe(bn)
               */
            }

          case Application(Expression(ExprBuiltin(Builtin.ListLength)), tipe) =>
            matchOrNormalize(arg) {
              case EmptyList(_)                                => NaturalLiteral(0)
              case NonEmptyList(exprs)                         => NaturalLiteral(exprs.length)
              case ExprOperator(lop, Operator.ListAppend, rop) =>
                (~ListLength)(tipe)(lop).op(Operator.Plus)((~ListLength)(tipe)(rop)).pipe(bn) // TODO: report issue to add this reduction rule to the standard?
            }

          case Application(Expression(ExprBuiltin(Builtin.ListHead)), tipe) =>
            matchOrNormalize(arg) {
              case EmptyList(_)        => (~Builtin.None)(tipe)
              case NonEmptyList(exprs) => KeywordSome(exprs.head)

              // TODO: report issue to add this reduction rule to the standard?
              // Simplify a List/head(lop # rop) when (List/head lop) evaluates to something concrete.
              case ExprOperator(lop, Operator.ListAppend, rop) =>
                matchOrNormalize((~Builtin.ListHead)(tipe)(lop)) {
                  // case Application(Expression(ExprBuiltin(Builtin.None)), _) => (~Builtin.ListHead)(tipe)(rop).pipe(bn) // This will never occur because we already normalized `arg`, and [] # x normalizes to just x.
                  case KeywordSome(r) => KeywordSome(r)
                }
            }

          case Application(Expression(ExprBuiltin(Builtin.ListLast)), tipe) =>
            matchOrNormalize(arg) {
              case EmptyList(_)        => (~Builtin.None)(tipe)
              case NonEmptyList(exprs) => KeywordSome(exprs.last)

              // TODO: report issue to add this reduction rule to the standard?
              // Simplify a List/last(lop # rop) when (List/last rop) evaluates to something concrete.
              case ExprOperator(lop, Operator.ListAppend, rop) =>
                matchOrNormalize((~Builtin.ListLast)(tipe)(rop)) {
                  // case Application(Expression(ExprBuiltin(Builtin.None)), _) => (~Builtin.ListLast)(tipe)(lop).pipe(bn) // This will never occur.
                  case KeywordSome(r) => KeywordSome(r)
                }
            }

          case Application(Expression(ExprBuiltin(Builtin.ListIndexed)), tipe) =>
            matchOrNormalize(arg) {
              case EmptyList(_)        => EmptyList((~Builtin.List)(Expression(RecordType(Seq((FieldName("index"), ~Builtin.Natural), (FieldName("value"), tipe))))))
              case NonEmptyList(exprs) =>
                NonEmptyList(exprs.zipWithIndex.map { case (e, index) =>
                  Expression(RecordLiteral(Seq((FieldName("index"), NaturalLiteral(index)), (FieldName("value"), e))))
                })
            }

          case Application(Expression(ExprBuiltin(Builtin.ListReverse)), _) =>
            matchOrNormalize(arg) {
              case EmptyList(t)        => EmptyList(t)
              case NonEmptyList(exprs) => NonEmptyList(exprs.reverse)
            }

          // Application of a Lambda() to argN.
          case Lambda(name, _, body)                                        => // betaNormalize of Lambda() ignores the type annotation.
            val a1 = shift(true, name, 0, arg)
            val b1 = substitute(body, name, 0, a1) // Shift free variables in body.
            val b2 = shift(false, name, 0, b1)
            b2.pipe(bn)

          case ExprBuiltin(Builtin.DateShow)        => matchOrNormalize(arg) { case d @ DateLiteral(_, _, _) => TextLiteral.ofString(d.print) }
          case ExprBuiltin(Builtin.TimeShow)        => matchOrNormalize(arg) { case d @ TimeLiteral(_, _, _, _) => TextLiteral.ofString(d.print) }
          case ExprBuiltin(Builtin.TimeZoneShow)    => matchOrNormalize(arg) { case d @ TimeZoneLiteral(_) => TextLiteral.ofString(d.print) }
          case ExprBuiltin(Builtin.DoubleShow)      => matchOrNormalize(arg) { case d @ DoubleLiteral(_) => TextLiteral.ofString(d.print) }
          case ExprBuiltin(Builtin.IntegerShow)     => matchOrNormalize(arg) { case d @ IntegerLiteral(_) => TextLiteral.ofString(d.print) }
          case ExprBuiltin(Builtin.NaturalShow)     => matchOrNormalize(arg) { case d @ NaturalLiteral(_) => TextLiteral.ofString(d.print) }
          case ExprBuiltin(Builtin.IntegerClamp)    => matchOrNormalize(arg) { case IntegerLiteral(a) => NaturalLiteral(a.max(0)) }
          case ExprBuiltin(Builtin.IntegerNegate)   => matchOrNormalize(arg) { case IntegerLiteral(a) => IntegerLiteral(-a) }
          case ExprBuiltin(Builtin.IntegerToDouble) => matchOrNormalize(arg) { case IntegerLiteral(a) => DoubleLiteral(a.toDouble) }
          // TODO: write here all other cases where Application(_, _) can be simplified
          case _                                    => normalizeArgs
        }

      case Field(base, name) =>
        def lookupOrFailure(defs: Seq[(FieldName, _)], str: String, maybeExpression: Option[Expression]): Expression =
          maybeExpression.getOrElse(
            throw new Exception(
              s"Record access has invalid field name (${name.name}), which should be one of the $str's fields: (${defs.map(_._1.name).mkString(", ")}), expression being evaluated: $expr"
            )
          )

        matchOrNormalize(base) {
          case r @ RecordLiteral(_) => lookupOrFailure(r.defs, "record literal", r.lookup(name))

          case r @ RecordType(_) => lookupOrFailure(r.defs, "record type", r.lookup(name))

          case ProjectByLabels(base1, _) => Expression(Field(base1, name)).pipe(bn)

          case ExprOperator(Expression(r @ RecordLiteral(_)), Operator.Prefer, target)             =>
            r.lookup(name) match {
              // Should not beta-normalize this Field() because it is pointless and may result in an infinite loop.
              case Some(v) => Field(Expression(ExprOperator(Expression(RecordLiteral(Seq((name, v)))), Operator.Prefer, target)), name)
              case None    => Expression(Field(target, name)).pipe(bn)
            }
          case ExprOperator(target, Operator.Prefer, Expression(r @ RecordLiteral(_)))             =>
            r.lookup(name) match {
              case Some(v) => v
              case None    => Expression(Field(target, name)).pipe(bn)
            }
          case ExprOperator(Expression(r @ RecordLiteral(_)), Operator.CombineRecordTerms, target) =>
            r.lookup(name) match {
              // Do not normalize this again because it won't be possible.
              case Some(v) => Field(Expression(ExprOperator(Expression(RecordLiteral(Seq((name, v)))), Operator.CombineRecordTerms, target)), name)
              case None    => Expression(Field(target, name)).pipe(bn)
            }
          case ExprOperator(target, Operator.CombineRecordTerms, Expression(r @ RecordLiteral(_))) =>
            r.lookup(name) match {
              // Do not normalize this again because it won't be possible.
              case Some(v) => Field(Expression(ExprOperator(target, Operator.CombineRecordTerms, Expression(RecordLiteral(Seq((name, v)))))), name)
              case None    => Expression(Field(target, name)).pipe(bn)
            }

        }

      //      case ProjectByLabels(_, Seq()) => // This code is moved below.

      case p @ ProjectByLabels(base, labels) =>
        matchOrNormalize(base) {
          case RecordLiteral(defs)   => RecordLiteral(defs.filter { case (name, _) => labels contains name }) // TODO: do we need a faster lookup here?
          case RecordType(defs)      => RecordType(defs.filter { case (name, _) => labels contains name })    // TODO: do we need a faster lookup here?
          case ProjectByLabels(t, _) => Expression(ProjectByLabels(t, labels)).pipe(bn)

          case ExprOperator(left, Operator.Prefer, right @ Expression(RecordLiteral(defs))) =>
            val newL: Expression = ProjectByLabels(left, labels diff defs.map(_._1))
            val newR: Expression = ProjectByLabels(right, labels intersect defs.map(_._1))
            Expression(ExprOperator(newL, Operator.Prefer, newR)).pipe(bn)

          // This case is t.{} where t could be a record type literal, or an unknown value of a record type.
          // TODO make typecheck fail for t.{} unless t is a literal record type or t is a value of record type, otherwise this code is wrong. Follow https://github.com/dhall-lang/dhall-lang/pull/1371
          case _ if labels.isEmpty                                                          => RecordLiteral(Seq())
          // TODO normalize x.{a, b} to just x if x's type has exactly those fields
          // case _ => ???

          case _ => p.sorted.scheme.map(betaNormalizeOrUnexpand(_, options))
        }

      case ProjectByType(base, labels) =>
        matchOrNormalize(labels) { case RecordType(defs) =>
          Expression(ProjectByLabels(base, defs.map(_._1))).pipe(bn)
        // TODO report issue: does beta-normalization.md say that ProjectByLabels(...) must be beta-normalized? If not, tests fail. -- Has this been corrected already?
        }

      // T::r is syntactic sugar for (T.default // r) : T.Type
      case c @ Completion(_, _)        => desugar(c).pipe(bn)

      case With(data, pathComponents, body) =>
        matchOrNormalize(data) {
          case r @ RecordLiteral(defs)                                                                             =>
            pathComponents match { // This is a non-empty list.
              case Seq(PathComponent.Label(single)) =>
                RecordLiteral((defs.toMap ++ Map(single -> body.pipe(bn))).toSeq)
              case _ if pathComponents.length > 1   =>
                val PathComponent.Label(head) = pathComponents.head
                val tail                      = pathComponents.tail
                r.lookup(head) match {
                  case Some(e1) =>
                    val e2 = Expression(With(e1, tail, body)).pipe(bn)
                    RecordLiteral((defs.toMap ++ Map(head -> e2)).toSeq)
                  case None     =>
                    val e1 = Expression(With(Expression(RecordLiteral(Seq())), tail, body)).pipe(bn)
                    RecordLiteral((defs.toMap ++ Map(head -> e1)).toSeq)
                }
              //              case _                                => normalizeArgs // This case will never occur because pathComponents is an empty list.
            }
          case none @ Application(Expression(ExprBuiltin(Builtin.None)), _) if pathComponents.head.isOptionalLabel => none
          case KeywordSome(_) if pathComponents.length == 1 && pathComponents.head.isOptionalLabel                 => KeywordSome(body.pipe(bn))
          case KeywordSome(data) if pathComponents.length > 1 && pathComponents.head.isOptionalLabel               =>
            Expression(KeywordSome(With(data, pathComponents.tail, body))).pipe(bn)
        }

      case TextLiteral(_, _) =>
        lazy val TextLiteral(interpolationsN, trailing) = normalizeArgs

        // TODO: replace this code by foldRight somehow?
        def loop(t: TextLiteral[Expression]): TextLiteral[Expression] = t.interpolations match {
          case (head, Expression(tl @ TextLiteral(_, _))) :: next => TextLiteral.ofString[Expression](head) ++ tl ++ loop(TextLiteral(next, t.trailing))
          case (head, headExpr) :: next                           => TextLiteral(List((head, headExpr)), "") ++ loop(TextLiteral(next, t.trailing))
          case Nil                                                => t
        }

        loop(TextLiteral(interpolationsN, trailing)) match {
          case TextLiteral(List(("", chunkN)), "") => chunkN
          case t                                   => t
        }

      // TODO rewrite RecordType identity expressions
      case RecordType(_)     => normalizeArgs.asInstanceOf[RecordType[Expression]].sorted

      case RecordLiteral(_) => {
        val normalizedFields = normalizeArgs.asInstanceOf[RecordLiteral[Expression]].sorted
        // TODO report issue - add to standard, rewrite { a = x.a, b = x.b } as x.{a, b}
        if (options.rewriteRecordIdentity && normalizedFields.defs.nonEmpty) {
          rewriteRecordAsProjection(normalizedFields) match {
            case Some(replacedByProjection) => betaNormalizeUncached(replacedByProjection, options)
            case None                       => normalizedFields
          }
        } else normalizedFields
      }

      case UnionType(_) => normalizeArgs.asInstanceOf[UnionType[Expression]].sorted

      case ShowConstructor(data) =>
        matchOrNormalize(data) {
          case Application(Expression(Field(Expression(UnionType(_)), fieldName)), _) => TextLiteral.ofString(fieldName.name)
          case Field(Expression(UnionType(_)), fieldName)                             => TextLiteral.ofString(fieldName.name)
          // Builtin union type: Optional, with built-in constructors Some and None.
          // `None` is a built-in symbol of type `∀(A : Type) → Optional A`, but `Some` is a keyword.
          case Application(Expression(ExprBuiltin(Builtin.None)), _)                  => TextLiteral.ofString(Builtin.None.entryName)
          case KeywordSome(_)                                                         => TextLiteral.ofString("Some")
        }

      case Import(_, _, _) => throw new Exception(s"Unresolved import in $expr cannot be beta-normalized")
    }
  }

  // TODO report issue - add to the standard, rewrite a non-empty record { a = x.a, b = x.b } as x.{a, b} as beta-reduction
  // For now, we do this only in assert checking.
  private def rewriteRecordAsProjection(recordLiteral: RecordLiteral[Expression]): Option[Expression] = {
    recordLiteral.defs.head._2.scheme match {
      case Field(headExpr, _) =>
        val allFieldsMatch = recordLiteral.defs.forall { case (fieldName, expr) => // This field is fieldName = expr and expr must be headExpr.fieldName
          expr.scheme match {
            case Field(base, name) => base == headExpr && name == fieldName
            case _                 => false
          }
        }
        if (allFieldsMatch)
          Some(Expression(ProjectByLabels(headExpr, recordLiteral.defs.map(_._1))))
        else
          None
      case _                  => None
    }

  }

  // Shortcut: identical JVM object references are equivalent.
  // But we should not use x == y for Double values, because that would incorrectly judge -0.0 === 0.0, which we don't want.
  private def simpleEquivalence(x: Expression, y: Expression): Boolean = x.eq(y) || {
    x.scheme match {
      case DoubleLiteral(_) => false
      case _                => x == y // Equivalent as case classes.
    }
  }

  // https://github.com/dhall-lang/dhall-lang/blob/master/standard/equivalence.md
  // TODO: report issue, activate eta-reduction and associativity rewrite only when type-checking an `assert` value.
  def equivalent(x: Expression, y: Expression): Boolean = simpleEquivalence(x, y) || {
    val normalizedX = betaNormalizeAndExpand(x.alphaNormalized, optionsForAssertChecking)
    val normalizedY = betaNormalizeAndExpand(y.alphaNormalized, optionsForAssertChecking)
    normalizedX.toCBORmodel.encodeCbor2 sameElements normalizedY.toCBORmodel.encodeCbor2
  }

  def desugar(c: Completion[Expression]): Expression =
    Expression(ExprOperator(Field(c.base, FieldName("default")), Operator.Prefer, c.target)) | Field(c.base, FieldName("Type"))
}

final case class FreeVars[A](names: Set[VarName])
// Quick-and-dirty foldMap replacement.
// FreeVars is a constant functor and a monoid, so we define an applicative functor evidence for it.

object FreeVars {
  implicit val ApplicativeFreeVars: Applicative[FreeVars] = new Applicative[FreeVars] {
    override def zip[A, B](fa: FreeVars[A], fb: FreeVars[B]): FreeVars[(A, B)] = FreeVars(fa.names union fb.names)

    override def map[A, B](f: A => B)(fa: FreeVars[A]): FreeVars[B] = FreeVars(fa.names)

    override def pure[A](a: A): FreeVars[A] = FreeVars(Set())
  }
}

/*
// We need to define this as an applicative functor, but actually we will use this only with A = Expression.
final case class UniqueReferences[A](run: mutable.Set[Expression] => (A, mutable.Set[Expression]))

object UniqueReferences {
  // Replace a given value by a unique reference if available. Otherwise update the dictionary of unique references.
  def make(expression: Expression): UniqueReferences[Expression] = UniqueReferences { cache =>
    val newExpression = cache.find(_ == expression).getOrElse {
      cache.add(expression)
      expression
    }
    (newExpression, cache)
  }

  implicit val ApplicativeUniqueReferences: Applicative[UniqueReferences] = new Applicative[UniqueReferences] {
    override def zip[A, B](fa: UniqueReferences[A], fb: UniqueReferences[B]): UniqueReferences[(A, B)] = UniqueReferences(c =>
      fa.run(c) match {
        case (a, d) =>
          fb.run(d) match {
            case (b, e) => ((a, b), e)
          }
      }
    )

    override def map[A, B](f: A => B)(fa: UniqueReferences[A]): UniqueReferences[B] = UniqueReferences(prev =>
      fa.run(prev) match {
        case (a, dict) => (f(a), dict)
      }
    )

    override def pure[A](a: A): UniqueReferences[A] = UniqueReferences(prev => (a, prev))
  }
}
 */
