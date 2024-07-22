package io.chymyst.nanodhall

import sourcecode.{File, Line, Name}

object SymbolicGraph {
  final case class RuleDef(val name: String, rule: () => GrammarRule) {
    override def equals(obj: Any): Boolean = obj match {
      case r: RuleDef => name == r.name
      case _          => false
    }
  }

  sealed trait GrammarRule

  final case class LiteralMatch(text: String) extends GrammarRule

  final case class GrammarSymbol(val name: String, rule: () => RuleDef) extends GrammarRule {
    override def equals(obj: Any): Boolean = obj match {
      case s: GrammarSymbol => name == s.name
      case _                => false
    }

  }

  implicit class SymbolicGraphOps(r: => RuleDef) {
    def ~(next: => RuleDef)(implicit file: File, line: Line, varName: Name): RuleDef =
      new RuleDef(name = varName.value, rule = () => And(r.rule(), next.rule()))

    def |(next: => RuleDef)(implicit file: File, line: Line, varName: Name): RuleDef = new RuleDef(name = varName.value, rule = () => Or(r.rule(), next.rule()))
  }

  def lit(s: String)(implicit file: File, line: Line, varName: Name): RuleDef = new RuleDef(name = varName.value, rule = () => LiteralMatch(s))

  final case class And(rule1: GrammarRule, rule2: GrammarRule) extends GrammarRule

  final case class Or(rule1: GrammarRule, rule2: GrammarRule) extends GrammarRule
}
