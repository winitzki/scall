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
  test("yaml output for record of lists with indent") {
    expect(Yaml.toYaml("{a = [1, 2, 3], b= [4, 5]}".dhall, 4).merge == """a:
                                                              |    - 1
                                                              |    - 2
                                                              |    - 3
                                                              |b:
                                                              |    - 4
                                                              |    - 5
                                                              |""".stripMargin)
  }

  test("yaml output for strings with special characters") {
    expect(
      Yaml.toYaml("{a = \"-\"}".dhall, 2).merge ==
        """a: "-"
        |""".stripMargin
    )
    expect(
      Yaml.toYaml("{a = \"a-b\"}".dhall, 2).merge ==
        """a: "a-b"
        |""".stripMargin
    )
    expect(
      Yaml.toYaml("""{a = "\"abc\""}""".dhall, 2).merge ==
        """a: "\"abc\""
        |""".stripMargin
    )
  }

  test("yaml output for multiline strings with special characters 1") {
    val result = Yaml.toYaml("{a = \"-\\n\\\"-\\\"\"}".dhall, 2).merge
    expect(
      result ==
        """a: |
        |  -
        |  -
        |""".stripMargin
    )
  }

  test("yaml output for multiline strings with special characters 1") {
    val result = Yaml.toYaml("{a = \"a-b\"}".dhall, 2).merge
    expect(result == """a: 'a-b'""")
  }

}
