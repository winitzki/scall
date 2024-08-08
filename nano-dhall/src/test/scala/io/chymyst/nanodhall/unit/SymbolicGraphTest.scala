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
}
