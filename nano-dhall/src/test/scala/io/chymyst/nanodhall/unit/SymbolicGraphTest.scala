package io.chymyst.nanodhall.unit

import com.eed3si9n.expecty.Expecty.expect
import SGraph._
import munit.FunSuite
import sourcecode.Name

object SGraph {

  def lit(s: String)(implicit varName: Name): RuleDef = RuleDef(varName.value, () => LiteralMatch(s))

  implicit def toRuleDef(rule: LiteralMatch)(implicit varName: Name): RuleDef = RuleDef(varName.value, () => rule)

  final case class RuleDef(name: String, grammarRule: () => GrammarExpr) {
    lazy val rule: GrammarExpr = grammarRule()
  }

  sealed trait GrammarExpr
  final case class LiteralMatch(s: String)                              extends GrammarExpr
  final case class GrammarSymbol(name: String, rule: () => GrammarExpr) extends GrammarExpr
  final case class And(l: GrammarExpr, r: GrammarExpr)                  extends GrammarExpr
  final case class Or(l: GrammarExpr, r: GrammarExpr)                   extends GrammarExpr

  implicit class RuleDefOps(r: => RuleDef) {
    def ~(next: => RuleDef)(implicit varName: Name): RuleDef = RuleDef(varName.value, () => And(r.rule, next.rule))
    def |(next: => RuleDef)(implicit varName: Name): RuleDef = RuleDef(varName.value, () => Or(r.rule, next.rule))
  }

}

class SymbolicGraphTest extends FunSuite {

  test("graph with only symbol names") {
    final case class RD(name: String)
    sealed trait GrammarExp
    final case class LM(s: String)                        extends GrammarExp
    final case class GS(name: String)                     extends GrammarExp
    final case class Andx(l: GrammarExpr, r: GrammarExpr) extends GrammarExp
    final case class Orx(l: GrammarExpr, r: GrammarExpr)  extends GrammarExp

    implicit class RuleDefOpsx(r: => RD) {
      def ~(next: => RD)(implicit varName: Name): RD = RD(varName.value)
      def |(next: => RD)(implicit varName: Name): RD = RD(varName.value)
    }

    def litx(s: String)(implicit varName: Name): RD = RD(varName.value)

    lazy val a: RD = litx("x")
    lazy val b: RD = litx("y") ~ a
    lazy val c: RD = litx("y") ~ a | b
    lazy val d: RD = litx("z") ~ d | b | e
    lazy val e: RD = litx("z") ~ e | (b ~ d)
    expect(a.name == "a")
    expect(b.name == "b")
    expect(c.name == "c")
    expect(d.name == "d")
    expect(e.name == "e")
  }
  /*
  test("grammar without circular dependencies") {
    lazy val a: RuleDef = lit("x")
    lazy val b: RuleDef = lit("y") ~ a
    lazy val c: RuleDef = lit("y") ~ a | b

    expect(a.name == "a")
    expect(a.rule match {
      case LiteralMatch("x") => true
    })

    expect(b.name == "b")
    expect(b.rule match {
      case And(LiteralMatch("y"), GrammarSymbol("a", _)) => true
    })

    expect(c.name == "c")
    expect(c.rule match {
      case Or(And(LiteralMatch("y"), GrammarSymbol("a", _)), GrammarSymbol("b", _)) => true
    })
  }

  test("circular dependencies do not create an infinite loop 1") {

    lazy val b: RuleDef = (lit("y") ~ b) | lit("z")

    expect(b.name == "b")

    expect(b.rule match {
      case Or(And(LiteralMatch("y"), GrammarSymbol("b", _)), LiteralMatch("z")) => true
    })
  }

  test("circular dependencies do not create an infinite loop 2") {

    def a: RuleDef = b ~ c

    def b: RuleDef = lit("x") ~ a ~ b | lit("y")

    def c: RuleDef = lit("z") ~ a

    expect(a.name == "a")
    expect(b.name == "b")
    expect(c.name == "c")

    expect(a.rule match {
      case And(GrammarSymbol("b", bx), GrammarSymbol("c", cx)) => true
    })
    expect(b.rule match {
      case Or(And(And(LiteralMatch("x"), GrammarSymbol("a", _)), GrammarSymbol("b", _)), LiteralMatch("y")) => true
    })
    expect(c.rule match {
      case And(LiteralMatch("z"), GrammarSymbol("a", _)) => true
    })

  }

   */

  test("another take on graph") {
    class Rul(val name: String, val ruleDef: () => GrammarExpr)
    object Rul                           {
      def apply(x: => GrammarExpr)(implicit valName: Name): Rul = new Rul(name = valName.value, ruleDef = () => x)
    }
    def li(x: String) = LiteralMatch(x)
    implicit class GOps(ge: GrammarExpr) {
      def ~(o: Rul)         = And(ge, GrammarSymbol(o.name, o.ruleDef))
      def |(o: Rul)         = Or(ge, GrammarSymbol(o.name, o.ruleDef))
      def ~(o: GrammarExpr) = And(ge, o)
      def |(o: GrammarExpr) = Or(ge, o)
    }
    implicit class ROps(r: Rul)          {
      def ~(o: Rul)         = And(GrammarSymbol(r.name, r.ruleDef), GrammarSymbol(o.name, o.ruleDef))
      def ~(o: GrammarExpr) = And(GrammarSymbol(r.name, r.ruleDef), o)
      def |(o: Rul)         = Or(GrammarSymbol(r.name, r.ruleDef), GrammarSymbol(o.name, o.ruleDef))
      def |(o: GrammarExpr) = Or(GrammarSymbol(r.name, r.ruleDef), o)
    }

    def a: Rul = Rul(li("x") ~ a ~ b)
    def b: Rul = Rul(b ~ li("y") | a)

    expect(a.name == "a")
    expect(b.name == "b")

    expect(a.ruleDef() match {
      case And(And(LiteralMatch("x"), GrammarSymbol("a", ax)), GrammarSymbol("b", bx)) =>
        (ax() match {
          case And(And(LiteralMatch("x"), GrammarSymbol("a", ax)), GrammarSymbol("b", bx)) => true
        }) && (bx() match {
          case Or(And(GrammarSymbol("b", bx), LiteralMatch("y")), GrammarSymbol("a", ax)) => true
        })
    })
    expect(b.ruleDef() match {
      case Or(And(GrammarSymbol("b", bx), LiteralMatch("y")), GrammarSymbol("a", ax)) =>
        (bx() match {
          case Or(And(GrammarSymbol("b", bx), LiteralMatch("y")), GrammarSymbol("a", ax)) => true
        }) && (ax() match {
          case And(And(LiteralMatch("x"), GrammarSymbol("a", ax)), GrammarSymbol("b", bx)) => true
        })
    })
  }
}
