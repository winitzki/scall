package io.chymyst.nanodhall

import sourcecode.{File, Line, Name}

object SymbolicGraph {
  sealed trait Rule

  final case class LiteralMatch(text: String)  extends Rule

  final class GrammarSymbol(name: String, rule: => RuleDef) extends Rule

  implicit class SymbolicGraphOps(r: => Rule) {
    def ~(next: => Rule)
  }

  def chars(x : String) = ???

  sealed trait RuleDef {
def name: String = "undefined"
//    def ~(next: => RuleDef)(implicit file: File, line: Line, varName: Name): RuleDef =
//      new And(varName.value, Seq(this, next))
//
//    def |(next: => RuleDef)(implicit file: File, line: Line, varName: Name): RuleDef =
//      new Or(varName.value, Seq(this, next))

    def print: String
  }

  final case class Literal(matcher: LiteralMatch)(implicit file: File, line: Line, varName: Name) extends RuleDef {
    val name: String = varName.value
    def print: String = s""
  }
//
//  final class RuleX(val name: String, definition: => RuleDef) extends RuleDef {
//    def print: String = s"$name := ${definition.print}"
//  }

  final class And( rules:   Seq[Rule ]) extends RuleDef {

    def print: String = s""
  }

  final class Or(  rules:  Seq[Rule]) extends RuleDef {
    def print: String = s""
  }
}
