package io.chymyst.dhall.codec

import io.chymyst.dhall.Syntax.{Expression, ExpressionScheme}
import io.chymyst.dhall.SyntaxConstants.{ConstructorName, FieldName, VarName}

import scala.util.control.TailCalls.{TailRec, done, tailcall}

object ToScala {

  /** Convert a Dhall expression to Scala source code that creates the same Dhall expression.
    */

  def print(packageName: String, exprName: String, hash: String, expr: Expression): String =
    s"""package io.chymyst.dhall_shim.$packageName
      |import io.chymyst.dhall.Syntax.Expression
      |import io.chymyst.dhall.Syntax.ExpressionScheme._
      |import io.chymyst.dhall.SyntaxConstants.Builtin
      |import io.chymyst.dhall.SyntaxConstants.Constant
      |import io.chymyst.dhall.SyntaxConstants.Operator
      |import io.chymyst.dhall.codec.DhallShim
      |
      |object `$exprName` extends DhallShim {
      |  val name: String = "$exprName"
      |  val packageName: String = "io.chymyst.dhall_shim.$packageName"
      |  val hash: String = "$hash"
      |  val dhallSource: String = ${escape(expr.print)}
      |  lazy val dhallExpression: Expression = ${printSourceCodeTC(expr).result}
      |}
      |""".stripMargin

  // The type of `es` is actually Expression | Option[Expression].
  private def printSeveral(es: Any*): TailRec[List[String]] = es.toList match {
    case Nil                            => done(Nil)
    case (head: Expression) :: tail     =>
      for {
        x    <- tailcall(printSourceCodeTC(head))
        rest <- tailcall(printSeveral(tail: _*))
      } yield x :: rest
    case Some(head: Expression) :: tail =>
      for {
        x    <- tailcall(printSourceCodeTC(head))
        rest <- tailcall(printSeveral(tail: _*))
      } yield "Some(" + x + ")" :: rest
    case None :: tail                   =>
      for {
        rest <- tailcall(printSeveral(tail: _*))
      } yield "None" :: rest
  }

  private def escape(name: Any): String = name match {
    case s: String          => s"\"\"\"$s\"\"\"" // TODO: implement proper escape for Scala code strings.
    case s: FieldName       => s"FieldName(\"${s.name}\")"
    case s: ConstructorName => s"ConstructorName(\"${s.name}\")"
    case s: VarName         => s"VarName(\"${s.name}\")"
  }

  private def simple(prefix: String, postfix: String)(es: List[String]): String = prefix + es.mkString(", ") + postfix

  private[dhall] def printSourceCodeTC(expr: Expression): TailRec[String] = expr.scheme match {
    case ExpressionScheme.Variable(name, index)                 => done(s"Expression(Variable(${escape(name)}, BigInt($index)))")
    case ExpressionScheme.Lambda(name, tipe, body)              =>
      printSeveral(tipe, body).map { case List(t, b) => s"Expression(Lambda(${escape(name)}, $t,\n  $b))" }
    case ExpressionScheme.Forall(name, tipe, body)              =>
      printSeveral(tipe, body).map { case List(t, b) => s"Expression(Forall(${escape(name)}, $t,\n  $b))" }
    case ExpressionScheme.Let(name, tipe, subst, body)          =>
      printSeveral(tipe, subst, body) map simple(s"\nExpression(Let(${escape(name)}, ", "))")
    case ExpressionScheme.If(cond, ifTrue, ifFalse)             =>
      printSeveral(cond, ifTrue, ifFalse) map simple("Expression(If(", "))")
    case ExpressionScheme.Merge(record, update, tipe)           =>
      printSeveral(record, update, tipe) map simple("Expression(Merge(", "))")
    case ExpressionScheme.ToMap(data, tipe)                     =>
      printSeveral(data, tipe) map simple("Expression(ToMap(", "))")
    case ExpressionScheme.EmptyList(tipe)                       =>
      printSeveral(tipe) map simple("Expression(EmptyList(", "))")
    case ExpressionScheme.NonEmptyList(exprs)                   =>
      printSeveral(exprs: _*) map simple("Expression(NonEmptyList(Seq(", ")))")
    case ExpressionScheme.Annotation(data, tipe)                =>
      printSeveral(data, tipe) map simple("Expression(Annotation(", "))")
    case ExpressionScheme.ExprOperator(lop, op, rop)            =>
      printSeveral(lop, rop) map { case List(l, r) => s"Expression(ExprOperator($l, Operator.$op, $r))" }
    case ExpressionScheme.Application(func, arg)                =>
      printSeveral(func, arg) map simple("Expression(Application(", "))")
    case ExpressionScheme.Field(base, name)                     =>
      printSeveral(base) map simple("Expression(Field(", s", $name))")
    case ExpressionScheme.ProjectByLabels(base, labels)         =>
      printSeveral(base) map simple("Expression(ProjectByLabels(", s", Seq(${labels.mkString(", ")})))")
    case ExpressionScheme.ProjectByType(base, by)               =>
      printSeveral(base, by) map simple("Expression(ProjectByType(", "))")
    case ExpressionScheme.Completion(base, target)              =>
      printSeveral(base, target) map simple("Expression(Completion(", "))")
    case ExpressionScheme.Assert(assertion)                     =>
      printSeveral(assertion) map simple("Expression(Assert(", "))")
    case ExpressionScheme.With(data, pathComponents, body)      =>
      printSeveral(data, body) map { case List(d, b) =>
        s"Expression(With($d, $pathComponents, $b))"
      } //  s"Expression(With($d, Seq(${pathComponents.mkString(",")}), $b))"}
    case ExpressionScheme.RecordType(defs)                      =>
      printSeveral(defs.map(_._2): _*) map { es => s"Expression(RecordType(${defs.zip(es).map { case ((field, _), e) => s"(${escape(field)}, $e)" }}))" }
    case ExpressionScheme.RecordLiteral(defs)                   =>
      printSeveral(defs.map(_._2): _*) map { es => s"Expression(RecordLiteral(${defs.zip(es).map { case ((field, _), e) => s"(${escape(field)}, $e)" }}))" }
    case ExpressionScheme.UnionType(defs)                       =>
      printSeveral(defs.map(_._2): _*) map { es =>
        s"Expression(UnionType(${defs.zip(es).map { case ((constructorName, _), e) => s"(${escape(constructorName)}, $e)" }}))"
      }
    case ExpressionScheme.ShowConstructor(data)                 =>
      printSeveral(data) map simple("Expression(ShowConstructor(", "))")
    case ExpressionScheme.KeywordSome(data)                     =>
      printSeveral(data) map simple("Expression(KeywordSome(", "))")
    case ExpressionScheme.TextLiteral(interpolations, trailing) =>
      printSeveral(interpolations.map(_._2): _*) map { es =>
        s"Expression(TextLiteral(${interpolations.zip(es).map { case ((field, _), e) => s"(${escape(field)}, $e" }}, ${escape(trailing)}))"
      }

    case ExpressionScheme.Import(importType, importMode, digest)             => done(expr.toString)
    case ExpressionScheme.DoubleLiteral(value)                               => done(s"Expression(DoubleLiteral($value))")
    case ExpressionScheme.NaturalLiteral(value)                              => done(s"Expression(NaturalLiteral(BigInt($value)))")
    case ExpressionScheme.IntegerLiteral(value)                              => done(s"Expression(IntegerLiteral(BigInt($value)))")
    case ExpressionScheme.BytesLiteral(hex)                                  => done(s"Expression(BytesLiteral(\"$hex\"))")
    case ExpressionScheme.DateLiteral(year, month, day)                      => done(s"Expression(DateLiteral($year,$month,$day))")
    case ExpressionScheme.TimeLiteral(hours, minutes, seconds, nanosPrinted) => done(s"Expression(TimeLiteral($hours,$minutes,$seconds,$nanosPrinted))")
    case ExpressionScheme.TimeZoneLiteral(totalMinutes)                      => done(s"Expression(TimeZoneLiteral($totalMinutes))")
    case ExpressionScheme.ExprBuiltin(builtin)                               => done(s"Expression(ExprBuiltin(Builtin.$builtin))")
    case ExpressionScheme.ExprConstant(constant)                             => done(s"Expression(ExprConstant(Constant.$constant))")
  }
}
