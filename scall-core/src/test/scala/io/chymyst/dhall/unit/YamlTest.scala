package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.DhallFile
import io.chymyst.dhall.Yaml
import munit.FunSuite

class YamlTest extends FunSuite {
  test("yaml output for comment headers 1") {
    val dhallFile = DhallFile(Seq(), "-- comment", """{ a = "True" }""".dhall)
    val result    = Yaml.toYaml(dhallFile).merge
    expect(
      result ==
        """# comment
        |a: 'True'
        |""".stripMargin
    )
  }

  test("yaml output for comment headers 2") {
    val dhallFile = DhallFile(Seq(), "-- comment\n--\n   --  comment\n", """{ a = "True" }""".dhall)
    val result    = Yaml.toYaml(dhallFile).merge
    expect(
      result ==
        """# comment
        |#
        |#  comment
        |a: 'True'
        |""".stripMargin
    )
  }

  test("yaml output for comment headers 3") {
    val dhallFile = DhallFile(Seq(), "-- comment\n   -- comment\n{- abc -}\n", """{ a = "True" }""".dhall)
    val result    = Yaml.toYaml(dhallFile).merge
    expect(
      result ==
        """# comment
        |# comment
        |#{- abc -}
        |a: 'True'
        |""".stripMargin
    )
  }
}
