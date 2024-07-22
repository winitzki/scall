package io.chymyst.nanodhall

import sourcecode.{File, Line, Name}

object SymbolicGraph {
  final   class RuleDef(val name: String, rule:  => GrammarRule) {
    override def equals(obj: Any): Boolean = obj match {
      case r: RuleDef => name == r.name
      case _          => false
    }
    lazy val grammarRule : GrammarRule = rule
  }

  sealed trait GrammarRule
  final case class LiteralMatch(text: String) extends GrammarRule
  final case class GrammarSymbol(  name: String, rule: () => RuleDef) extends GrammarRule {
    override def equals(obj: Any): Boolean = obj match {
      case  GrammarSymbol(`name`, _)  => true
      case _                => false
    }

  }
  final case class And(rule1: GrammarRule, rule2: GrammarRule) extends GrammarRule
  final case class Or(rule1: GrammarRule, rule2: GrammarRule) extends GrammarRule

  implicit class SymbolicGraphOps(r: => RuleDef) {
    def ~(next: => RuleDef)(implicit file: File, line: Line, varName: Name): RuleDef =
      new RuleDef(name = varName.value, rule =   And(r.grammarRule, next.grammarRule))

    def |(next: => RuleDef)(implicit file: File, line: Line, varName: Name): RuleDef = new RuleDef(name = varName.value, rule =  Or(r.grammarRule, next.grammarRule))
  }

  def lit(s: String)(implicit file: File, line: Line, varName: Name): RuleDef = new RuleDef(name = varName.value, rule =   LiteralMatch(s))

}
