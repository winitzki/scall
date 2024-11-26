package io.chymyst.nanodhall.unit

import com.eed3si9n.expecty.Expecty.expect
import munit.FunSuite
import sourcecode.Name

class SymbolicGraphTest extends FunSuite {

  test("working example 1") {

    sealed trait GrammarExpr {
      def ~(o: GrammarExpr): GrammarExpr = GrammarExpr.~(this, o)

      def /(o: GrammarExpr): GrammarExpr = GrammarExpr./(this, o) // Cannot use `|` because the Scala pattern matcher does not accept `|` as infix.
    }
    object GrammarExpr {
      final case class Li(s: String) extends GrammarExpr

      final case class Rul(name: String, rule: () => GrammarExpr) extends GrammarExpr

      final case class ~(l: GrammarExpr, r: GrammarExpr) extends GrammarExpr

      final case class /(l: GrammarExpr, r: GrammarExpr) extends GrammarExpr
    }

    import GrammarExpr._
    implicit def rul(x: => GrammarExpr)(implicit valName: Name): Rul = Rul(name = valName.value, rule = () => x)

    // An example grammar represented as a symbolic graph. Both `a` and `b` depend on each other.
    def a: Rul = Li("x") ~ a ~ b

    def b: Rul = b ~ Li("y") / a

    def root: Rul = (a / root) ~ Li("z")

    expect(a.rule() match {
      case Li("x") ~ Rul("a", ax) ~ Rul("b", bx) =>
        (ax() match {
          case Li("x") ~ Rul("a", ax) ~ Rul("b", bx) => true
        }) && (bx() match {
          case Rul("b", bx) ~ Li("y") / Rul("a", ax) => true
        })
    })
    expect(b.rule() match {
      case Rul("b", bx) ~ Li("y") / Rul("a", ax) =>
        (bx() match {
          case Rul("b", bx) ~ Li("y") / Rul("a", ax) => true
        }) && (ax() match {
          case Li("x") ~ Rul("a", ax) ~ Rul("b", bx) => true
        })
    })

    // Find all rules used by a given starting rule.
    def rulesUsed(start: Rul): Set[Rul] = rulesUsedRec(start, Set())

    def rulesUsedRec(start: GrammarExpr, visited: Set[Rul]): Set[Rul] = start match {
      case GrammarExpr.Li(_) => visited
      case r@GrammarExpr.Rul(name, _) =>
        if (visited.map(_.name) contains name) visited + r else rulesUsedRec(r.rule(), visited + r)
      case GrammarExpr.~(l, r) =>
        val left = rulesUsedRec(l, visited)
        val right = rulesUsedRec(r, left)
        right
      case GrammarExpr./(l, r) =>
        val left = rulesUsedRec(l, visited)
        val right = rulesUsedRec(r, left)
        right
    }

    expect(rulesUsed(a).map(_.name) == Set("a", "b"))
    expect(rulesUsed(b).map(_.name) == Set("a", "b"))
    expect(rulesUsed(root).map(_.name) == Set("a", "b", "root"))

  }

  test("working example 2") {
    sealed trait GraphExpr[+F[+_]]

    object GraphExpr {
      final case class Rule[+A, +F[+_]](id: A, rule: () => GraphExpr[F]) extends GraphExpr[F]

      final case class Wrap[+F[+_]](wrap: F[GraphExpr[F]]) extends GraphExpr[F]
    }

    import GraphExpr._

    def rule[F[+_]](x: => GraphExpr[F])(implicit valName: Name): Rule[String, F] =
      Rule[String, F](id = valName.value, rule = () => x)

    sealed trait ExampleF[+A]

    object ExampleF {
      final case class Lit(s: String) extends ExampleF[Nothing]

      final case class And[+A](l: A, r: A) extends ExampleF[A]

      final case class Or[+A](l: A, r: A) extends ExampleF[A]

      final case class Not[+A](x: A) extends ExampleF[A]
    }
    import ExampleF._

    type G = Rule[String, ExampleF]

    def a: G = rule(Wrap(And(Wrap(Lit("x")), Wrap(And(a, b)))))

    def b: G = rule(Wrap(Or(b, Wrap(And(Wrap(Not(Wrap(Lit("y")))), a)))))

    expect(a.rule() match {
      case Wrap(And(Wrap(_), Wrap(_))) => true
      // case Wrap(And(Wrap(Lit("x")), Wrap(And(Rule("a", _), Rule("b", _)))) ) => true  // Causes scalac error with Scala 2.13
    })
  }

  test("trigger a Scala compiler error") {

    final case class Fix[+F[+_]](wrap: F[Fix[F]])

    sealed trait ExampleF[+A]

    object ExampleF {
      final case class Leaf(s: String) extends ExampleF[Nothing]

      final case class Branch[+A](l: A, r: A) extends ExampleF[A]
    }

    import ExampleF._

    val x = Fix[ExampleF](Branch(Fix(Leaf("x")), Fix(Leaf("y"))))
    val y = Fix[ExampleF](Branch(x, Fix(Leaf("z"))))

    expect(x match {
      case Fix(Branch(Fix(_), Fix(_))) => true
    })
    /*
        expect(x match {
          case Fix(Branch(Fix(_), Fix(Leaf(_)))) => true// Causes scalac error with Scala 2.13
        })

        expect(y match {
          case Fix(Branch(Fix(Branch(_, _)), Fix(_))) => true // Causes scalac error with Scala 2.13
        })
    */
  }

}
