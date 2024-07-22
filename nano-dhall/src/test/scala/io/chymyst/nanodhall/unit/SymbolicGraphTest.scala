package io.chymyst.nanodhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.nanodhall.SymbolicGraph._
import munit.FunSuite

class SymbolicGraphTest extends FunSuite {

  test("circular dependencies do not create an infinite loop 1") {

    def a: RuleDef = lit("x")

    def b: RuleDef = lit("y") ~ b | lit("z")

    expect(a.name == "a")
    expect(b.name == "b")

    expect(a == new RuleDef(name = "a", rule = () => LiteralMatch("x")))
    expect(b == new RuleDef(name = "b", rule = () => Or(And(LiteralMatch("y"), new GrammarSymbol("b", () => b)), LiteralMatch("z"))))
  }

  test("circular dependencies do not create an infinite loop 2") {

    def a: RuleDef = b ~ c

    def b: RuleDef = lit("x") ~ a ~ b | lit("y")

    def c: RuleDef = lit("z") ~ a

    expect(a.name == "a")
    expect(b.name == "b")
    expect(c.name == "c")

    expect(a == new RuleDef(name = "a", rule = () => And(new GrammarSymbol("b", () => b), new GrammarSymbol("c", () => c))))
    expect(
      b == new RuleDef(
        name = "b",
        rule = () => Or(And(And(LiteralMatch("x"), new GrammarSymbol("a", () => a)), new GrammarSymbol("b", () => b)), LiteralMatch("y")),
      )
    )
    expect(c == new RuleDef(name = "c", rule = () => And(LiteralMatch("z"), new GrammarSymbol("a", () => a))))
  }
}
