package io.chymyst.nanodhall

import enumeratum.{Enum, EnumEntry}

trait HasBigInt {
  def value: BigInt
}

trait NanoExpr[R <: NanoExpr[R]] {
  def NaturalLiteral(value: BigInt): R with HasBigInt

  def Variable(name: VarName, index: BigInt): R

  def Lambda(name: VarName, tipe: R, body: R): R

  def Forall(name: VarName, tipe: R, body: R): R

  def Let(name: VarName, subst: R, body: R): R

  def Annotation(body: R, tipe: R): R

  def Application(func: R, arg: R): R

  def Constant(constant: NanoConstant): R

  def Operator(l: R, op: NanoOperator, r: R): R
}


final case class VarName(name: String) extends AnyVal

object VarName {
  val underscore = VarName("_")
}

sealed trait NanoExprADT extends NanoExpr[NanoExprADT] {
  override def NaturalLiteral(value: BigInt): NanoExprADT with HasBigInt = NanoExprADT.NaturalLiteral(value)

  override def Variable(name: VarName, index: BigInt): NanoExprADT = NanoExprADT.Variable(name, index)

  override def Lambda(name: VarName, tipe: NanoExprADT, body: NanoExprADT): NanoExprADT = NanoExprADT.Lambda(name, tipe, body)

  override def Forall(name: VarName, tipe: NanoExprADT, body: NanoExprADT): NanoExprADT = NanoExprADT.Forall(name, tipe, body)

  override def Let(name: VarName, subst: NanoExprADT, body: NanoExprADT): NanoExprADT = NanoExprADT.Let(name, subst, body)

  override def Annotation(body: NanoExprADT, tipe: NanoExprADT): NanoExprADT = NanoExprADT.Annotation(body, tipe)

  override def Application(func: NanoExprADT, arg: NanoExprADT): NanoExprADT = NanoExprADT.Application(func, arg)

  override def Constant(constant: NanoConstant): NanoExprADT = NanoExprADT.Constant(constant)

  override def Operator(l: NanoExprADT, op: NanoOperator, r: NanoExprADT): NanoExprADT = NanoExprADT.Operator(l, op, r)
}

object NanoExprADT {

  object CreateADT extends NanoExprADT

  final case class NaturalLiteral(value: BigInt) extends NanoExprADT with HasBigInt {
    override def equals(other: Any): Boolean = other.isInstanceOf[NaturalLiteral] && {
      val otherValue = other.asInstanceOf[NaturalLiteral].value
      value equals otherValue
    }
  }

  final case class Variable(name: VarName, index: BigInt) extends NanoExprADT {
    override def equals(other: Any): Boolean = other.isInstanceOf[Variable] && {
      val otherVar = other.asInstanceOf[Variable]
      (otherVar.name equals name) && (otherVar.index equals index)
    }
  }

  final case class Lambda(name: VarName, tipe: NanoExprADT, body: NanoExprADT) extends NanoExprADT

  final case class Forall(name: VarName, tipe: NanoExprADT, body: NanoExprADT) extends NanoExprADT

  final case class Let(name: VarName, subst: NanoExprADT, body: NanoExprADT) extends NanoExprADT

  final case class Annotation(body: NanoExprADT, tipe: NanoExprADT) extends NanoExprADT

  final case class Application(func: NanoExprADT, arg: NanoExprADT) extends NanoExprADT

  final case class Constant(constant: NanoConstant) extends NanoExprADT

  final case class Operator(l: NanoExprADT, op: NanoOperator, r: NanoExprADT) extends NanoExprADT
}


sealed abstract class NanoBuiltIn(override val entryName: String) extends EnumEntry {

}

object NanoBuiltIn extends Enum[NanoBuiltIn] {

  override def values = findValues

  case object Natural extends NanoBuiltIn("Natural")

  case object NaturalBuild extends NanoBuiltIn("Natural/build")

  case object NaturalFold extends NanoBuiltIn("Natural/fold")

  case object NaturalIsZero extends NanoBuiltIn("Natural/isZero")

  case object NaturalSubtract extends NanoBuiltIn("Natural/subtract")

}

sealed trait NanoConstant extends EnumEntry {

}

object NanoConstant extends Enum[NanoConstant] {
  val values = findValues

  case object Type extends NanoConstant
}


sealed abstract class NanoOperator(val name: String) extends EnumEntry

object NanoOperator extends Enum[NanoOperator] {
  val values = findValues

  case object Plus extends NanoOperator("+")
}
