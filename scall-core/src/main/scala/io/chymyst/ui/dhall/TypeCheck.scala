package io.chymyst.ui.dhall

import io.chymyst.ui.dhall.Applicative.seqSeq
import io.chymyst.ui.dhall.Semantics.equivalent
import io.chymyst.ui.dhall.Syntax.ExpressionScheme._
import io.chymyst.ui.dhall.Syntax.{Expression, ExpressionScheme}
import io.chymyst.ui.dhall.SyntaxConstants.{Builtin, Constant, Operator, VarName}
import io.chymyst.ui.dhall.TypeCheckResult._

sealed trait TypeCheckResult[+A] {
  def isValid: Boolean

  def map[B](f: A => B): TypeCheckResult[B]

  def flatMap[B](f: A => TypeCheckResult[B]): TypeCheckResult[B]

  def zip[B](other: TypeCheckResult[B]): TypeCheckResult[(A, B)]
}

object TypeCheckResult {
  final case class Valid[A](expr: A) extends TypeCheckResult[A] {
    override def isValid: Boolean = true

    override def map[B](f: A => B): TypeCheckResult[B] = Valid(f(expr))

    override def flatMap[B](f: A => TypeCheckResult[B]): TypeCheckResult[B] = f(expr)

    override def zip[B](other: TypeCheckResult[B]): TypeCheckResult[(A, B)] = other match {
      case Valid(expr2) => Valid(expr, expr2)
      case Invalid(errors) => Invalid(errors)
    }
  }

  final case class Invalid(errors: TypeCheck.TypeCheckErrors) extends TypeCheckResult[Nothing] {
    override def isValid: Boolean = false

    override def map[B](f: Nothing => B): TypeCheckResult[B] = Invalid(errors)

    override def flatMap[B](f: Nothing => TypeCheckResult[B]): TypeCheckResult[B] = Invalid(errors)

    override def zip[B](other: TypeCheckResult[B]): TypeCheckResult[(Nothing, B)] = other match {
      case Valid(_) => Invalid(errors)
      case Invalid(otherErrors) => Invalid(errors ++ otherErrors)
    }
  }

  def typeError(message: String): TypeCheckResult[Nothing] = Invalid(Seq(message))

  implicit val ApplicativeTypeCheckResult: Applicative[TypeCheckResult] = new Applicative[TypeCheckResult] {
    override def zip[A, B](fa: TypeCheckResult[A], fb: TypeCheckResult[B]): TypeCheckResult[(A, B)] = fa zip fb

    override def map[A, B](f: A => B)(fa: TypeCheckResult[A]): TypeCheckResult[B] = fa map f

    override def pure[A](a: A): TypeCheckResult[A] = Valid(a)
  }
}

object TypeCheck {
  val emptyContext: GammaTypeContext = GammaTypeContext(Map())

  type TypeCheckErrors = Seq[String] // Non-empty list.

  final case class GammaTypeContext(defs: Map[VarName, IndexedSeq[Expression]]) {
    def lookup(variable: Variable): Option[Expression] = defs.get(variable.name).flatMap { exprs =>
      if (variable.index.isValidInt) exprs.lift(variable.index.intValue) else None
    }

    def append(varName: VarName, expr: Expression) = defs.updatedWith(varName) {
      case Some(exprs) => Some(exprs :+ expr)
      case None => Some(IndexedSeq(expr))
    }
  }

  // See https://github.com/dhall-lang/dhall-lang/blob/master/standard/type-inference.md
  // Check that a given expression has the given type, that is, gamma |- expr : tipe. If this holds, no errors are output.
  def validate(gamma: GammaTypeContext, expr: Expression, tipe: Expression): TypeCheckResult[Expression] = {
    inferType(gamma, expr) match {
      case Valid(inferredType) =>
        if (Semantics.equivalent(tipe, inferredType))
          Valid(expr)
        else
          typeError(s"Expression ${expr.toDhall} has inferred type ${inferredType.toDhall} and not the expected type ${tipe.toDhall}")

      case error@Invalid(_) => error
    }
  }

  def required(cond: Boolean)(error: String): TypeCheckResult[Unit] = if (cond) Valid(()) else typeError(error)

  // Infer the type of a given expression (not necessarily in beta-normalized form). If no errors, return Right(tipe) that fits gamma |- expr : tipe.
  def inferType(gamma: GammaTypeContext, expr: Expression): TypeCheckResult[Expression] = {
    implicit def toExpr(expr: Expression): TypeCheckResult[Expression] = Valid(expr)

    implicit def fromBuiltin(builtin: Builtin): TypeCheckResult[Expression] = Valid(~builtin)

    val _Type: Expression = ExprConstant(Constant.Type)
    val underscore: Expression = Expression(Variable(ExpressionScheme.underscore, BigInt(0)))

    val result: TypeCheckResult[Expression] = expr.scheme match {
      case v@Variable(_, _) =>
        // TODO report issue: "If the natural number associated with the variable is greater than or equal to the number of type annotations in the context matching the variable then that is a type error." This seems to be incorrect: the type error occurs if the index is strictly greater.
        gamma.lookup(v) match {
          case Some(tipe) => tipe.inferTypeWith(gamma) match {
            case Valid(_) => tipe
            case Invalid(errors) => Invalid(errors :+ s"Variable ${expr.toDhall} has type error(s)")
          }
          case None => typeError(s"Variable ${expr.toDhall} is not in type inference context $gamma")
        }
      case Lambda(name, tipe, body) => ???
      case Forall(name, tipe, body) => ???
      case Let(name, tipe, subst, body) => ???

      case If(cond, ifTrue, ifFalse) =>
        val lopType = for {
          typeOfLop <- ifTrue.inferTypeWith(gamma)
          _ <- typeOfLop.inferTypeWith(gamma)
        } yield typeOfLop
        val ropType = for {
          typeOfRop <- ifFalse.inferTypeWith(gamma)
          _ <- typeOfRop.inferTypeWith(gamma)
        } yield typeOfRop
        val equivalenceCheck = for {
          pair <- lopType zip ropType
          _ <- required(Semantics.equivalent(pair._1, pair._2))(s"Types of two If() clauses are not equivalent: ${pair._1.toDhall} and ${pair._2.toDhall}")
        } yield ()
        validate(gamma, cond, ~Builtin.Bool) zip equivalenceCheck map (_._1)

      case Merge(record, update, tipe) => ???
      case ToMap(data, tipe) => ???
      case EmptyList(tipe) => ???
      case NonEmptyList(exprs) => ???
      case Annotation(data, tipe) =>
        if (tipe == Expression(~Constant.Sort)) validate(gamma, data, tipe).map(_ => tipe)
        else {
          for {
            pair <- data.inferTypeWith(gamma) zip tipe.inferTypeWith(gamma)
            _ <- required(Semantics.equivalent(pair._1, tipe))(s"Inferred type ${pair._1.toDhall} is not equal to the type ${tipe.toDhall} given in the annotation")
          } yield pair._1 // Return the inferred type because it is assured to be in a normalized form.
        }
      case ExprOperator(lop, op, rop) => op match {
        case Operator.Or | Operator.And | Operator.Equal | Operator.NotEqual =>
          validate(gamma, lop, ~Builtin.Bool) zip validate(gamma, rop, ~Builtin.Bool) map (_ => ~Builtin.Bool)
        case Operator.Plus | Operator.Times =>
          validate(gamma, lop, ~Builtin.Natural) zip validate(gamma, rop, ~Builtin.Natural) map (_ => ~Builtin.Natural)
        case Operator.TextAppend =>
          validate(gamma, lop, ~Builtin.Text) zip validate(gamma, rop, ~Builtin.Text) map (_ => ~Builtin.Text)
        case Operator.ListAppend => ???
        case Operator.CombineRecordTerms => ???
        case Operator.Prefer => ???
        case Operator.CombineRecordTypes => ???
        case Operator.Equivalent =>
          val lopType = for {
            tLop <- lop.inferTypeWith(gamma)
            _ <- validate(gamma, tLop, _Type) // TODO: see if we can relax this restriction. What if we allow this to be Kind or Sort as well?
          } yield tLop
          val ropType = for {
            tRop <- rop.inferTypeWith(gamma)
            _ <- validate(gamma, tRop, _Type) // TODO: see if we can relax this restriction. What if we allow this to be Kind or Sort as well?
          } yield tRop
          val equivalenceCheck = for {
            pair <- lopType zip ropType
            _ <- required(Semantics.equivalent(pair._1, pair._2))(s"Types of two sides of `===` are not equivalent: ${pair._1.toDhall} and ${pair._2.toDhall}")
          } yield ()
          equivalenceCheck.map(_ => _Type)

        case Operator.Alternative => typeError(s"Cannot typecheck an expression with unresolved imports: ${expr.toDhall}")
      }
      case Application(func, arg) => ???
      case Field(base, name) => ???
      case ProjectByLabels(base, labels) => ???
      case ProjectByType(base, by) => ???
      case Completion(base, target) => ???
      case Assert(assertion) =>
        validate(gamma, assertion, _Type) match {
          case Valid(_) =>
            assertion.betaNormalized.scheme match {
              case exprN@ExprOperator(lop, Operator.Equivalent, rop) =>
                if (Semantics.equivalent(lop, rop)) Expression(exprN) // "The inferred type of an assertion is the same as the provided annotation."
                else typeError(s"Expression `assert` failed: Unequal sides in ${exprN.toDhall}")
              case _ => typeError(s"An `assert` expression must have an equality type but has ${assertion.toDhall}")
            }
          case errors => errors
        }

      case With(data, pathComponents, body) => ???
      case DoubleLiteral(_) => Builtin.Double
      case NaturalLiteral(_) => Builtin.Natural
      case IntegerLiteral(_) => Builtin.Integer

      case TextLiteral(interpolations, _) =>
        val typesOfInterpolations = interpolations.map { case (prefix, expr) => (expr, expr.inferTypeWith(gamma)) }
        val interpolationsWithTypeBuiltinText = typesOfInterpolations.map {
          case (expr, Valid(tipe)) => if (Semantics.equivalent(tipe, ~Builtin.Text)) Valid(expr)
          else typeError(s"Interpolation chunk ${expr.toDhall} is not of type Text but has type ${tipe.toDhall}")
          case (_, other) => other
        }
        seqSeq(interpolationsWithTypeBuiltinText).map(_ => ~Builtin.Text) // If all interpolation expressions have type Text, the entire TextLiteral also does.

      case BytesLiteral(_) => Builtin.Bytes
      case DateLiteral(_, _, _) => Builtin.Date
      case TimeLiteral(_) => Builtin.Time
      case TimeZoneLiteral(_) => Builtin.TimeZone
      case RecordType(defs) => ???
      case RecordLiteral(defs) => ???

      case UnionType(defs) =>
        val constructorNames = defs.map(_._1)
        // Compute the upper bound of all universes.
        val result = for {
          // Verify that all constructor names are distinct.
          _ <- required(constructorNames.size == constructorNames.distinct.size)(s"Some constructor names are duplicated in ${expr.toDhall}")
          // Verify that all expressions are typed as Type, Kind, or Sort.
          exprTypes <- seqSeq(defs.flatMap(_._2.map(_.inferTypeWith(gamma))))
          unexpectedTypes = exprTypes.filterNot(_.scheme match {
            case ExprConstant(Constant.Type) | ExprConstant(Constant.Kind) | ExprConstant(Constant.Sort) => true
            case _ => false
          })
          _ <- required(unexpectedTypes.isEmpty)(s"Unexpected types in UnionType: ${unexpectedTypes.map(_.toDhall).mkString("; ")}")
        } yield exprTypes.map(_.scheme).collect {
          case ExprConstant(Constant.Type) => Constant.Type
          case ExprConstant(Constant.Kind) => Constant.Kind
          case ExprConstant(Constant.Sort) => Constant.Sort
        }

        result.map(_.foldLeft(Constant.Type: Constant)((x, y) => x union y)).map(ExprConstant.apply)

      case ShowConstructor(data) => data.inferTypeWith(gamma) flatMap {
        case Expression(Application(Expression(ExprBuiltin(Builtin.Optional)), _)) |
             Expression(UnionType(_)) => ~Builtin.Text
        case tipe => typeError(s"showConstructor used with data of type ${tipe.toDhall} but must be a union type or Optional")
      }

      case Import(_, _, _) => typeError(s"Cannot typecheck an expression with unresolved imports: ${expr.toDhall}")
      case KeywordSome(data) => ???
      case ExprBuiltin(builtin) => builtin match {
        case Builtin.Bool => _Type
        case Builtin.Bytes => _Type
        case Builtin.Date => _Type
        case Builtin.DateShow => ~Builtin.Date ->: ~Builtin.Text
        case Builtin.Double => _Type
        case Builtin.DoubleShow => ~Builtin.Double ->: ~Builtin.Text
        case Builtin.Integer => _Type
        case Builtin.IntegerClamp => ~Builtin.Integer ->: ~Builtin.Natural
        case Builtin.IntegerNegate => ~Builtin.Integer ->: ~Builtin.Integer
        case Builtin.IntegerShow => ~Builtin.Integer ->: ~Builtin.Text
        case Builtin.IntegerToDouble => ~Builtin.Integer ->: ~Builtin.Double
        case Builtin.List => _Type ->: _Type
        case Builtin.ListBuild => ???
        case Builtin.ListFold => ???
        case Builtin.ListHead => ???
        case Builtin.ListIndexed => ???
        case Builtin.ListLast => ???
        case Builtin.ListLength => ???
        case Builtin.ListReverse => ???
        case Builtin.Natural => _Type
        case buildOrFold@(Builtin.NaturalBuild | Builtin.NaturalFold) =>
          val nat = VarName("natural")
          val natE = Expression(Variable(nat, BigInt(0)))
          val buildFunctionType: Expression = Forall(nat, _Type, (natE ->: natE) ->: natE ->: natE)
          buildOrFold match {
            case Builtin.NaturalBuild => buildFunctionType ->: ~Builtin.Natural
            case Builtin.NaturalFold => ~Builtin.Natural ->: buildFunctionType
          }

        case Builtin.NaturalIsZero => ~Builtin.Natural ->: ~Builtin.Bool
        case Builtin.NaturalOdd | Builtin.NaturalEven => ~Builtin.Natural ->: ~Builtin.Bool
        case Builtin.NaturalShow => ~Builtin.Natural ->: ~Builtin.Text
        case Builtin.NaturalSubtract => ~Builtin.Natural ->: ~Builtin.Natural ->: ~Builtin.Natural
        case Builtin.NaturalToInteger => ~Builtin.Natural ->: ~Builtin.Integer
        case Builtin.None => _Type ->: (~Builtin.Optional)(underscore)
        case Builtin.Optional => _Type ->: _Type
        case Builtin.Text => _Type
        case Builtin.TextReplace => ~Builtin.Text ->: ~Builtin.Text ->: ~Builtin.Text ->: ~Builtin.Text
        case Builtin.TextShow => ~Builtin.Text ->: ~Builtin.Text
        case Builtin.Time => _Type
        case Builtin.TimeShow => ~Builtin.Time ->: ~Builtin.Text
        case Builtin.TimeZone => _Type
        case Builtin.TimeZoneShow => ~Builtin.TimeZone ->: ~Builtin.Text
      }
      case ExprConstant(constant) => constant match {
        case Constant.Type => Valid(ExprConstant(Constant.Kind))
        case Constant.Kind => Valid(ExprConstant(Constant.Sort))
        case Constant.Sort => typeError(s"Expression $expr is not well-typed because it is the top universe")
        case Constant.True | Constant.False => Builtin.Bool

      }
    }
    result.map(_.betaNormalized)
  }
}
