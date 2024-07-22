package io.chymyst.nanodhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.nanodhall.SymbolicGraph._
import munit.FunSuite

class SymbolicGraphTest extends FunSuite {

  test("grammar without circular dependencies") {
    def a: RuleDef = lit("x")
    def b: RuleDef = lit("y") ~ a
    def c: RuleDef = lit("y") ~ a | b

    expect(a.name == "a")
    expect(a.grammarRule  match {
      case LiteralMatch("x") => true
    })

    expect(b.name == "b")
    expect(b.grammarRule match {
      case And(LiteralMatch("y"), GrammarSymbol("a", _)) => true
    })
  }

  test("circular dependencies do not create an infinite loop 1") {

    def a: RuleDef = lit("x")

    def b: RuleDef = (lit("y") ~ b) | lit("z")

    expect(a.name == "a")
    expect(b.name == "b")

    expect(a.grammarRule match {
      case LiteralMatch("x") => true
    })

    b.grammarRule

    expect(b.grammarRule match {
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

//    expect(a == new RuleDef(name = "a", rule = () => And(GrammarSymbol("b", () => b), GrammarSymbol("c", () => c))))
//    expect(
//      b == new RuleDef(name = "b", rule = () => Or(And(And(LiteralMatch("x"), GrammarSymbol("a", () => a)), GrammarSymbol("b", () => b)), LiteralMatch("y")))
//    )
//    expect(c == new RuleDef(name = "c", rule = () => And(LiteralMatch("z"), GrammarSymbol("a", () => a))))
  }
}
