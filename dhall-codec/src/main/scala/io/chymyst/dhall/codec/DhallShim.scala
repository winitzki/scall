package io.chymyst.dhall.codec

import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Semantics.computeHash
import io.chymyst.dhall.Syntax.Expression

import java.nio.file.Paths

trait DhallShim {
  def hash: String
  def dhallSource: String
  def dhallExpression: Expression
  final def verifyHash: Boolean        = {
    hash == computeHash(dhallExpression.resolveImports(Paths.get(".")).alphaNormalized.betaNormalized.toCBORmodel.encodeCbor2)
  }
  final def verifyDhallSource: Boolean = {
    hash == computeHash(dhallSource.dhall.resolveImports(Paths.get(".")).alphaNormalized.betaNormalized.toCBORmodel.encodeCbor2)
  }
}
