package io.chymyst.ui.dhall

import io.chymyst.ui.dhall.Applicative.ApplicativeOps
import io.chymyst.ui.dhall.CBORmodel.CBytes
import io.chymyst.ui.dhall.Syntax.Expression.v
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.Syntax.{Expression, ExpressionScheme, Natural, PathComponent}
import io.chymyst.ui.dhall.SyntaxConstants.Builtin.{ListFold, ListLength, Natural, NaturalFold, NaturalSubtract}
import io.chymyst.ui.dhall.SyntaxConstants.Constant.{False, True}
import io.chymyst.ui.dhall.SyntaxConstants.Operator.ListAppend
import io.chymyst.ui.dhall.SyntaxConstants._

import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.regex.Pattern
import scala.collection.mutable
import scala.util.chaining.scalaUtilChainingOps

final case class ObservedCache[A, B](cache: mutable.Map[A, B], var requests: Long = 0, var hits: Long = 0) {
  def getOrElseUpdate(key: A, default: => B): B = {
    requests += 1
    if (cache contains key) hits += 1
    cache.getOrElseUpdate(key, default)
  }

  def statistics: String = s"Total requests: $requests, cache hits: $hits, total cache size: ${cache.size}"
}

object ObservedCache {
  def chooseCache[A, B](maybeSize: Option[Int]): ObservedCache[A, B] = ObservedCache(maybeSize match {
    case Some(maxSize) => new LRUCache[A, B](maxSize)
    case None => mutable.Map[A, B]()
  })
}

object Semantics {
  // TODO: make sure this algorithm is correct for variables with de Bruijn indices!
  private def freeVarsForLambda(name: VarName, tipe: Option[Expression], body: Expression): FreeVars[Expression] = {
    val freeVarsInType: Set[VarName] = tipe.map(t => freeVars(t).names).getOrElse(Set())
    val freeVarsInBody: Set[VarName] = freeVars(shift(true, name, 0, body)).names
    FreeVars(freeVarsInType union (freeVarsInBody -- Set(name)))
  }

  def freeVars(expr: Expression): FreeVars[Expression] = expr.scheme match {
    case Variable(name, index) => FreeVars(Set(name).filter(_ => index == 0))
    case Lambda(name, tipe, body) => freeVarsForLambda(name, Some(tipe), body)
    case Forall(name, tipe, body) => freeVarsForLambda(name, Some(tipe), body)
    case Let(name, tipe, subst, body) => FreeVars(freeVarsForLambda(name, tipe, body).names union freeVars(subst).names)
    case other => other.traverse(expr => freeVars(expr)).map(Expression.apply)
  }

  // TODO find and use a limited-size cache with least-recent-used cleanup
  //  private val cacheListFold: mutable.Map[(Expression, Seq[Expression], Expression, Expression, Expression), Expression] = mutable.Map()
  //  private val cacheNaturalFold: mutable.Map[(Natural, Expression, Expression, Expression), Expression] = mutable.Map()

  def computeHash(bytes: Array[Byte]): String =
    CBytes.byteArrayToHexString(MessageDigest.getInstance("SHA-256").digest(bytes)).toLowerCase

  def semanticHash(expr: Expression, currentFile: java.nio.file.Path): String =
    computeHash(expr.resolveImports(currentFile).alphaNormalized.betaNormalized.toCBORmodel.encodeCbor1)

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
    .flatMap { c => if (c.toInt < 32) String.format("\\u00%02x", c.toInt) else String.valueOf(c) }
    .pipe(s => "\"" + s + "\"")

  // TODO: implement and use a function that determines whether a given Dhall function will return literals when applied to literals. Implement such functions efficiently. -- Isn't every Dhall function in this class?
  // TODO: implement and use a function that determines which literals can be given to a function so that it will then ignore another (curried) argument. Use this to implement foldWhile efficiently.

  private def mergeRecordPartsPreferringSecond(defs1: Seq[(FieldName, Expression)], operator: Operator, defs2: Seq[(FieldName, Expression)]): Seq[(FieldName, Expression)] =
    (defs1.toSeq ++ defs2.toSeq)
      .groupMapReduce(_._1)(_._2)((l, r) => l.op(operator)(r))
      .toSeq
      .sortBy(_._1.name)

  val chooseLRUcache: Option[Int] = Some(30000)

  // TODO: possibly remove special lazy handling for .betaNormalized because that is not effective enough and we have to cache all betaNormalized results anyway.
  val cacheBetaNormalize = ObservedCache.chooseCache[ExpressionScheme[Expression], Expression](chooseLRUcache)

  def betaNormalize(expr: Expression): Expression = cacheBetaNormalize.getOrElseUpdate(expr.scheme, betaNormalizeUncached(expr))

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/beta-normalization.md
  private def betaNormalizeUncached(expr: Expression): Expression = {
    lazy val normalizeArgs: ExpressionScheme[Expression] = expr.schemeWithBetaNormalizedArguments

    def matchOrNormalize(expr: Expression, default: => Expression = normalizeArgs)(matcher: PartialFunction[ExpressionScheme[Expression], Expression]): Expression =
      matcher.applyOrElse(expr.betaNormalized.scheme, { _: ExpressionScheme[Expression] => default })

    expr.scheme match {
      // These expression types are already in beta-normal form.
      case Variable(_, _) | ExprBuiltin(_) | ExprConstant(_) | NaturalLiteral(_) | IntegerLiteral(_) | DoubleLiteral(_) |
           BytesLiteral(_) | DateLiteral(_, _, _) | TimeLiteral(_, _, _, _) | TimeZoneLiteral(_) =>
        expr

      // These expressions only need to normalize their arguments.
      case EmptyList(_) | NonEmptyList(_) | KeywordSome(_) | Lambda(_, _, _) | Forall(_, _, _) | Assert(_) => normalizeArgs

      // `let name : A = subst in body` is equivalent to `(λ(name : A) → body) subst`
      // We use Natural as the type here, because betaNormalize of Application(Lambda(...),...) ignores the type annotation inside Lambda().
      case Let( name , _, subst, body) => ((v(name.name) | ~Natural) -> body)(subst).betaNormalized

      case If(cond, ifTrue, ifFalse) =>
        if (cond.betaNormalized.scheme == ExprConstant(Constant.True)) ifTrue.betaNormalized
        else if (cond.betaNormalized.scheme == ExprConstant(Constant.False)) ifFalse.betaNormalized
        else if (ifFalse.betaNormalized.scheme == ExprConstant(Constant.False) && ifTrue.betaNormalized.scheme == ExprConstant(Constant.True)) cond.betaNormalized
        else if (equivalent(ifTrue, ifFalse)) ifTrue.betaNormalized
        else normalizeArgs

      case Merge(record, update, _) => matchOrNormalize(record) {
        case r@RecordLiteral(_) => matchOrNormalize(update) {
          case Application(Expression(Field(Expression(UnionType(_)), x)), a) => (r.lookup(x).get)(a).betaNormalized
          case Field(Expression(UnionType(_)), x) => r.lookup(x).get
          case KeywordSome(a) => (r.lookup(FieldName("Some")).get)(a).betaNormalized
          case Application(Expression(ExprBuiltin(Builtin.None)), _) => r.lookup(FieldName("None")).get
        }
      }

      case ToMap(Expression(RecordLiteral(Seq())), Some(tipe)) => EmptyList(tipe.betaNormalized)
      case ToMap(data, _) => matchOrNormalize(data) {
        case RecordLiteral(defs) =>
          NonEmptyList(defs.map { case (name, expr) => Expression(RecordLiteral(Seq(
            (FieldName("mapKey"), TextLiteral.ofString(name.name)),
            (FieldName("mapValue"), expr.betaNormalized),
          )))
          })
      }

      case Annotation(data, _) => data.betaNormalized

      case ExprOperator(lop, op, rop) =>
        lazy val ExprOperator(lopN, _, ropN) = normalizeArgs
        op match {
          case Operator.Or =>
            if (lopN.scheme == ExprConstant(Constant.False) || ropN.scheme == ExprConstant(Constant.True)) ropN
            else if (lopN.scheme == ExprConstant(Constant.True) || ropN.scheme == ExprConstant(Constant.False)) lopN
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
            if (lopN.scheme == ExprConstant(Constant.False) || ropN.scheme == ExprConstant(Constant.True)) lopN
            else if (lopN.scheme == ExprConstant(Constant.True) || ropN.scheme == ExprConstant(Constant.False)) ropN
            else if (equivalent(lop, rop)) lopN
            else normalizeArgs

          case Operator.CombineRecordTerms => (lopN.scheme, ropN.scheme) match {
            case (RecordLiteral(Seq()), _) => ropN
            case (_, RecordLiteral(Seq())) => lopN
            case (RecordLiteral(defs1), RecordLiteral(defs2)) =>
              RecordLiteral(mergeRecordPartsPreferringSecond(defs1, Operator.CombineRecordTerms, defs2)).betaNormalized // TODO report issue that we need to beta-normalize this, otherwise tests fail
            case _ => normalizeArgs
          }

          case Operator.Prefer => (lopN.scheme, ropN.scheme) match {
            case (RecordLiteral(Seq()), _) => ropN
            case (_, RecordLiteral(Seq())) => lopN
            case (RecordLiteral(defs1), RecordLiteral(defs2)) =>
              // Do not need to beta-normalize the resulting RecordLiteral.
              val mergedFields =
                (defs1.toMap ++ defs2.toMap) // The operation ++ on Map prefers the second map's value when keys are the same.
                  .toSeq
                  .sortBy(_._1.name)
              RecordLiteral(mergedFields) //.betaNormalized  - not needed here.
            case _ if equivalent(lopN, ropN) => lopN // TODO report issue: beta-normalization.md does not include this rule in Haskell code after `betaNormalize (Operator ls₀ Prefer rs₀)`
            case _ => normalizeArgs
          }

          case Operator.CombineRecordTypes => (lopN.scheme, ropN.scheme) match {
            case (RecordType(Seq()), _) => ropN
            case (_, RecordType(Seq())) => lopN
            case (RecordType(defs1), RecordType(defs2)) =>
              RecordType(mergeRecordPartsPreferringSecond(defs1, Operator.CombineRecordTypes, defs2)).betaNormalized // TODO report issue that we need to beta-normalize this, otherwise tests fail.
            case _ => normalizeArgs
          }

          case Operator.Times => (lopN.scheme, ropN.scheme) match { // Simplified only for Natural arguments.
            case (NaturalLiteral(a), _) if a == 0 => NaturalLiteral(0)
            case (_, NaturalLiteral(b)) if b == 0 => NaturalLiteral(0)
            case (NaturalLiteral(a), _) if a == 1 => ropN
            case (_, NaturalLiteral(b)) if b == 1 => lopN
            case (NaturalLiteral(a), NaturalLiteral(b)) => NaturalLiteral(a * b)
            case _ => normalizeArgs
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
            else normalizeArgs

          case Operator.Equivalent => normalizeArgs
          case Operator.Alternative => throw new Exception(s"Unresolved import alternative in $expr cannot be beta-normalized")
        }

      case Application(func, arg) =>
        lazy val argN = arg.betaNormalized
        // If funcN evaluates to a builtin name, and if it is fully applied to all required arguments, implement the builtin here.
        func.betaNormalized.scheme match {
          case ExprBuiltin(Builtin.NaturalBuild) => // Natural/build g = g Natural (λ(x : Natural) → x + 1) 0
            argN(~Natural)((v("x") | ~Natural) -> (v("x") + NaturalLiteral(1)))(NaturalLiteral(0)).betaNormalized
          case Application(Expression(Application(Expression(Application(Expression(ExprBuiltin(Builtin.NaturalFold)), Expression(NaturalLiteral(m)))), b)), g) =>
            // g (Natural/fold n b g argN)
            if (m == 0) argN else
            //              cacheNaturalFold.getOrElseUpdate(
            //              (m, b, g, argN),
              g((~NaturalFold)(NaturalLiteral(m - 1))(b)(g)(argN)).betaNormalized
          //        )
          case ExprBuiltin(Builtin.NaturalIsZero) => matchOrNormalize(arg) { case NaturalLiteral(a) => if (a == 0) ~True else ~False }
          case ExprBuiltin(Builtin.NaturalEven) => matchOrNormalize(arg) { case NaturalLiteral(a) => if (a % 2 == 0) ~True else ~False }
          case ExprBuiltin(Builtin.NaturalOdd) => matchOrNormalize(arg) { case NaturalLiteral(a) => if (a % 2 != 0) ~True else ~False }
          // NaturalShow is defined later.
          case ExprBuiltin(Builtin.NaturalToInteger) => matchOrNormalize(arg) { case NaturalLiteral(a) => IntegerLiteral(a) }
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

          case ExprBuiltin(Builtin.TextShow) => matchOrNormalize(arg) { case TextLiteral(List(), string) => TextLiteral.ofString(textShow(string)) }

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
            // The Dhall standard and the tests require the names "a" and "as".
            val freshName = "a"
            val a = v(freshName)
            val aseq = v("as")

            val newType = shift(true, VarName(freshName), 0, tipe)
            // g (List A₀) (λ(a : A₀) → λ(as : List A₁) → [ a ] # as) ([] : List A₀) ⇥ b
            argN((~Builtin.List)(tipe))((a | tipe) -> (
              (aseq | (~Builtin.List)(newType)) ->
                Expression(NonEmptyList(Seq(a))).op(ListAppend)(aseq)
              ))(Expression(EmptyList((~Builtin.List)(tipe)))).betaNormalized

          case Application(Expression(Application(Expression(Application(Expression(Application(Expression(ExprBuiltin(ListFold)), typeA0)), expressions)), b)), g) =>
            matchOrNormalize(expressions) {
              case NonEmptyList(exprs) => // Guaranteed a non-empty list.
                // We need to beta-normalize the expression List/fold typeA0 expressions b g argN. Check if it is in the cache; otherwise compute it.
                //   cacheListFold.getOrElseUpdate((typeA0, exprs, b, g, argN), {
                val rest = if (exprs.length == 1) Expression(EmptyList(typeA0)) else Expression(NonEmptyList(exprs.tail))
                //                  println(s"DEBUG ${LocalDateTime.now} betaNormalizing List/fold (${typeA0.toDhall}) ${exprs.map(_.toDhall).mkString("[ ", ", ", " ]")} (${b.toDhall}) (${g.toDhall}) (${argN.toDhall})")
                // List/fold A₀ ([] : List A₁) B g b₀  ⇥  g a (List/fold A₀ [ as… ] B g b₀)
                (g(exprs.head)((~ListFold)(typeA0)(rest)(b)(g)(argN))).betaNormalized
              // })

              // List/fold A₀ ([] : List A₁) B g b₀  ⇥  b₁
              case EmptyList(_) => argN
            }

          case Application(Expression(ExprBuiltin(Builtin.ListLength)), _) => matchOrNormalize(arg) {
            case EmptyList(_) => NaturalLiteral(0)
            case NonEmptyList(exprs) => NaturalLiteral(exprs.length)
            case ExprOperator(lop, Operator.ListAppend, rop) => (~ListLength)(lop.betaNormalized).betaNormalized.op(Operator.Plus)((~ListLength)(rop.betaNormalized).betaNormalized).betaNormalized // TODO: report issue to add this reduction rule to the standard?
          }

          case Application(Expression(ExprBuiltin(Builtin.ListHead)), tipe) => matchOrNormalize(arg) {
            case EmptyList(_) => (~Builtin.None)(tipe)
            case NonEmptyList(exprs) => KeywordSome(exprs.head)

            // TODO: report issue to add this reduction rule to the standard?
            // Simplify a ListAppend when (List/head lop) evaluates to something concrete.
            case ExprOperator(lop, Operator.ListAppend, rop) => matchOrNormalize((~Builtin.ListHead)(lop.betaNormalized)) {
              case Application(Expression(ExprBuiltin(Builtin.None)), _) => (~Builtin.ListHead)(rop.betaNormalized).betaNormalized
              case KeywordSome(r) => r.betaNormalized
            }
          }

          case Application(Expression(ExprBuiltin(Builtin.ListLast)), tipe) => matchOrNormalize(arg) {
            case EmptyList(_) => (~Builtin.None)(tipe)
            case NonEmptyList(exprs) => KeywordSome(exprs.last)
          }

          case Application(Expression(ExprBuiltin(Builtin.ListIndexed)), tipe) => matchOrNormalize(arg) {
            case EmptyList(_) => EmptyList((~Builtin.List)(Expression(RecordType(Seq((FieldName("index"), ~Builtin.Natural), (FieldName("value"), tipe))))))
            case NonEmptyList(exprs) => NonEmptyList(exprs.zipWithIndex.map { case (e, index) =>
              Expression(RecordLiteral(Seq((FieldName("index"), NaturalLiteral(index)), (FieldName("value"), e))))
            })
          }

          case Application(Expression(ExprBuiltin(Builtin.ListReverse)), _) => matchOrNormalize(arg) {
            case EmptyList(t) => EmptyList(t)
            case NonEmptyList(exprs) => NonEmptyList(exprs.reverse)
          }

          case Lambda(name, _, body) => // betaNormalize of Lambda() ignores the type annotation.
            val a1 = shift(true, name, 0, arg)
            val b1 = substitute(body, name, 0, a1)
            val b2 = shift(false, name, 0, b1)
            b2.betaNormalized

          case ExprBuiltin(Builtin.DateShow) => matchOrNormalize(arg) { case d@DateLiteral(_, _, _) => TextLiteral.ofString(d.toDhall) }
          case ExprBuiltin(Builtin.TimeShow) => matchOrNormalize(arg) { case d@TimeLiteral(_, _, _, _) => TextLiteral.ofString(d.toDhall) }
          case ExprBuiltin(Builtin.TimeZoneShow) => matchOrNormalize(arg) { case d@TimeZoneLiteral(_) => TextLiteral.ofString(d.toDhall) }
          case ExprBuiltin(Builtin.DoubleShow) => matchOrNormalize(arg) { case d@DoubleLiteral(_) => TextLiteral.ofString(d.toDhall) }
          case ExprBuiltin(Builtin.IntegerShow) => matchOrNormalize(arg) { case d@IntegerLiteral(_) => TextLiteral.ofString(d.toDhall) }
          case ExprBuiltin(Builtin.NaturalShow) => matchOrNormalize(arg) { case d@NaturalLiteral(_) => TextLiteral.ofString(d.toDhall) }
          case ExprBuiltin(Builtin.IntegerClamp) => matchOrNormalize(arg) { case IntegerLiteral(a) => NaturalLiteral(a.max(0)) }
          case ExprBuiltin(Builtin.IntegerNegate) => matchOrNormalize(arg) { case IntegerLiteral(a) => IntegerLiteral(-a) }
          case ExprBuiltin(Builtin.IntegerToDouble) => matchOrNormalize(arg) { case IntegerLiteral(a) => DoubleLiteral(a.toDouble) }
          // TODO: write here all other cases where Application(_, _) can be simplified
          case _ => normalizeArgs
        }

      case Field(base, name) => matchOrNormalize(base) {
        case r@RecordLiteral(_) =>
          val x = r.lookup(name)
          x.getOrElse(throw new Exception(s"Error in typechecker: record access in $expr has invalid field name $name not occurring among record fields ${r.defs.map(_._1).mkString(", ")}"))

        case ProjectByLabels(base1, _) => Field(base1, name).betaNormalized

        case ExprOperator(Expression(r@RecordLiteral(_)), Operator.Prefer, target) => r.lookup(name) match {
          // Should not beta-normalize this Field() because it is pointless and may result in an infinite loop.
          case Some(v) => Field(Expression(ExprOperator(Expression(RecordLiteral(Seq((name, v)))), Operator.Prefer, target)), name)
          case None => Field(target, name).betaNormalized
        }
        case ExprOperator(target, Operator.Prefer, Expression(r@RecordLiteral(_))) => r.lookup(name) match {
          case Some(v) => v
          case None => Field(target, name).betaNormalized
        }
        case ExprOperator(Expression(r@RecordLiteral(_)), Operator.CombineRecordTerms, target) => r.lookup(name) match {
          // Do not normalize this again because it won't be possible.
          case Some(v) => Field(Expression(ExprOperator(Expression(RecordLiteral(Seq((name, v)))), Operator.CombineRecordTerms, target)), name)
          case None => Field(target, name).betaNormalized
        }
        case ExprOperator(target, Operator.CombineRecordTerms, Expression(r@RecordLiteral(_))) => r.lookup(name) match {
          // Do not normalize this again because it won't be possible.
          case Some(v) => Field(Expression(ExprOperator(target, Operator.CombineRecordTerms, Expression(RecordLiteral(Seq((name, v)))))), name)
          case None => Field(target, name).betaNormalized
        }

      }

      case ProjectByLabels(_, Seq()) => RecordLiteral(Seq())

      case p@ProjectByLabels(base, labels) => matchOrNormalize(base) {
        case RecordLiteral(defs) => RecordLiteral(defs.filter { case (name, _) => labels contains name }) // TODO: do we need a faster lookup here?
        case ProjectByLabels(t, _) => ProjectByLabels(t, labels).betaNormalized
        case ExprOperator(left, Operator.Prefer, right@Expression(RecordLiteral(defs))) =>
          val newL: Expression = ProjectByLabels(left, labels diff defs.map(_._1))
          val newR: Expression = ProjectByLabels(right, labels intersect defs.map(_._1))
          ExprOperator(newL, Operator.Prefer, newR).betaNormalized
        case _ => p.sorted.schemeWithBetaNormalizedArguments
      }

      case ProjectByType(base, labels) => matchOrNormalize(labels) {
        case RecordType(defs) => ProjectByLabels(base, defs.map(_._1)).betaNormalized
        // TODO report issue: does beta-normalization.md say that ProjectByLabels(...) must be beta-normalized? If not, tests fail. -- Has this been corrected already?
      }

      // T::r is syntactic sugar for (T.default // r) : T.Type
      case c@Completion(_, _) => desugar(c).betaNormalized

      case With(data, pathComponents, body) => matchOrNormalize(data) {
        case r@RecordLiteral(defs) => pathComponents match { // This is a non-empty list.
          case Seq(PathComponent.Label(single)) =>
            RecordLiteral((defs.toMap ++ Map(single -> body.betaNormalized)).toSeq)
          case _ if pathComponents.length > 1 =>
            val PathComponent.Label(head) = pathComponents.head
            val tail = pathComponents.tail
            r.lookup(head) match {
              case Some(e1) =>
                val e2 = With(e1, tail, body).betaNormalized
                RecordLiteral((defs.toMap ++ Map(head -> e2)).toSeq)
              case None =>
                val e1 = With(Expression(RecordLiteral(Seq())), tail, body).betaNormalized
                RecordLiteral((defs.toMap ++ Map(head -> e1)).toSeq)
            }
          case _ => normalizeArgs
        }
        case none@Application(Expression(ExprBuiltin(Builtin.None)), _) if pathComponents.head.isOptionalLabel => none
        case KeywordSome(_) if pathComponents.length == 1 && pathComponents.head.isOptionalLabel => KeywordSome(body.betaNormalized)
        case KeywordSome(data) if pathComponents.length > 1 && pathComponents.head.isOptionalLabel => KeywordSome(With(data, pathComponents.tail, body).betaNormalized)
      }


      case TextLiteral(_, _) =>
        lazy val TextLiteral(interpolationsN, trailing) = normalizeArgs

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

      case RecordType(_) => normalizeArgs.asInstanceOf[RecordType[Expression]].sorted

      case RecordLiteral(_) => normalizeArgs.asInstanceOf[RecordLiteral[Expression]].sorted

      case UnionType(_) => normalizeArgs.asInstanceOf[UnionType[Expression]].sorted

      case ShowConstructor(data) =>
        matchOrNormalize(data) {
          case Application(Expression(Field(Expression(UnionType(_)), fieldName)), _) => TextLiteral.ofString(fieldName.name)
          case Field(Expression(UnionType(_)), fieldName) => TextLiteral.ofString(fieldName.name)
          // Builtin union type: Optional
          case Application(Expression(ExprBuiltin(Builtin.None)), _) => TextLiteral.ofString(Builtin.None.entryName)
          case KeywordSome(_) => TextLiteral.ofString("Some")
        }

      case Import(_, _, _) => throw new Exception(s"Unresolved import in $expr cannot be beta-normalized")
    }
  }

  // https://github.com/dhall-lang/dhall-lang/blob/master/standard/equivalence.md
  def equivalent(x: Expression, y: Expression): Boolean =
    x.alphaNormalized.betaNormalized.toCBORmodel.encodeCbor1 sameElements y.alphaNormalized.betaNormalized.toCBORmodel.encodeCbor1

  def desugar(c: Completion[Expression]): Expression =
    Expression(ExprOperator(Field(c.base, FieldName("default")), Operator.Prefer, c.target)) | Field(c.base, FieldName("Type"))
}

final case class FreeVars[A](names: Set[VarName]) // Quick-and-dirty foldMap replacement.

object FreeVars {
  implicit val ApplicativeFreeVars: Applicative[FreeVars] = new Applicative[FreeVars] {
    override def zip[A, B](fa: FreeVars[A], fb: FreeVars[B]): FreeVars[(A, B)] = FreeVars(fa.names union fb.names)

    override def map[A, B](f: A => B)(fa: FreeVars[A]): FreeVars[B] = FreeVars(fa.names)

    override def pure[A](a: A): FreeVars[A] = FreeVars(Set())
  }
}

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
        case (a, d) => fb.run(d) match {
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
