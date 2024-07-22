package io.chymyst.nanodhall

import sourcecode.{File, Line, Name}

object SymbolicGraph {
  sealed trait RuleDef
  final case class RuleLiteral(literalMatch: LiteralMatch) extends RuleDef
  final class RuleSymbol(val name: String, rule: () => GrammarRule) extends RuleDef {
    override def equals(obj: Any): Boolean = obj match {
      case r: RuleSymbol => name == r.name
      case _          => false
    }
    def grammarRule: GrammarRule           = rule()
  }

  sealed trait GrammarRule
  final case class LiteralMatch(text: String)                       extends GrammarRule
  final case class GrammarSymbol(name: String, rule: () => RuleDef) extends GrammarRule {
    override def equals(obj: Any): Boolean = obj match {
      case GrammarSymbol(`name`, _) => true
      case _                        => false
    }

  }
  final case class And(rule1: GrammarRule, rule2: GrammarRule) extends GrammarRule
  final case class Or(rule1: GrammarRule, rule2: GrammarRule)  extends GrammarRule

  implicit class SymbolicGraphOps(r: => RuleSymbol) {
    def ~(next: => RuleSymbol)(implicit file: File, line: Line, varName: Name): RuleDef = {
      new RuleSymbol(
        name = varName.value,
        rule = { () =>
          println(s"DEBUG: evaluating And(${r.name}, ${next.name}")
          And(r.grammarRule, next.grammarRule)
        },
      )
      def ~(next: RuleLiteral)(implicit file: File, line: Line, varName: Name): RuleDef =
    }

    def |(next: => RuleDef)(implicit file: File, line: Line, varName: Name): RuleDef =
      new RuleDef(
        name = varName.value,
        rule = { () =>
          println(s"DEBUG: evaluating Or(${r.name}, ${next.name}")
          Or(r.grammarRule, next.grammarRule)
        },
      )
  }

  def lit(s: String)(implicit file: File, line: Line, varName: Name): RuleDef = new RuleDef(name = varName.value, rule = () => LiteralMatch(s))

}
