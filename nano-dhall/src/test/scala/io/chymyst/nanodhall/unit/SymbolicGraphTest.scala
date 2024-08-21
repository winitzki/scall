package io.chymyst.nanodhall.unit

import com.eed3si9n.expecty.Expecty.expect
import munit.FunSuite
import sourcecode.Name

class SymbolicGraphTest extends FunSuite {

  test("refactor the working example") {

    sealed trait GrammarExpr {
      def ~(o: GrammarExpr): GrammarExpr = GrammarExpr.~(this, o)
      def /(o: GrammarExpr): GrammarExpr = GrammarExpr./(this, o) // Cannot use `|` because the Scala pattern matcher does not accept `|` as infix.
    }
    object GrammarExpr       {
      final case class Li(s: String) extends GrammarExpr

      final case class Rul(name: String, rule: () => GrammarExpr) extends GrammarExpr

      final case class ~(l: GrammarExpr, r: GrammarExpr) extends GrammarExpr

      final case class /(l: GrammarExpr, r: GrammarExpr) extends GrammarExpr
    }

    import GrammarExpr._
    def rul(x: => GrammarExpr)(implicit valName: Name): Rul = Rul(name = valName.value, rule = () => x)

    def a: Rul = rul(Li("x") ~ a ~ b)
    def b: Rul = rul(b ~ Li("y") / a)

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
  }

}
