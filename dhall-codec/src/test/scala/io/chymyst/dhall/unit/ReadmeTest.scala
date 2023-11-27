package io.chymyst.dhall.unit

import munit.FunSuite

class ReadmeTest extends FunSuite {

  test("example 1 from readme") {
    import io.chymyst.dhall.Parser.StringAsDhallExpression
    import io.chymyst.dhall.codec.FromDhall.DhallExpressionAsScala

    val a = "Natural/odd 123".dhall.typeCheckAndBetaNormalize().unsafeGet.asScala[Boolean].toOption.get.value

    assert(a == true)

    val b = "1 + 2".dhall.typeCheckAndBetaNormalize().unsafeGet.asScala[BigInt].toOption.get.value.intValue

    assert(b == 3)
  }
}
