package io.chymyst.nanodhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.nanodhall.SymbolicGraph._
import munit.FunSuite

class SymbolicGraphTest extends FunSuite {

  test("circular dependencies do not create an infinite loop 1") {

    def a: RuleDef = chars("x")

    def b: RuleDef = chars("y") ~ b | chars("z")

    expect(a.name == "a")
    expect(b.name == "b")

    expect(a == RuleDef(name = "a", defs = LiteralMatch("x")))
    expect(b == RuleDef(name = "b", defs = Or(And(LiteralMatch("y"), Rule("b", b)), LiteralMatch("z"))))
  }

  test("circular dependencies do not create an infinite loop 2") {

    def a: RuleDef = b ~ c

    def b: RuleDef = chars("x") ~ a ~ b | chars("y")

    def c: RuleDef = chars("z") ~ a

    expect(a.name == "a")
    expect(b.name == "b")
    expect(c.name == "c")

    expect(a == RuleDef(name = "a", defs = And(Rule("b", b), Rule("c", c))))
    expect(b == RuleDef(name = "b", defs = Or(And(LiteralMatch("x"), Rule("a", a), Rule("b", b)), LiteralMatch("y"))))
    expect(c == RuleDef(name = "c", defs = And(LiteralMatch("z"), Rule("a", a))))
  }
}
