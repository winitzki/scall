package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.DhallFile
import io.chymyst.dhall.Yaml
import io.chymyst.dhall.Yaml.YamlOptions
import munit.FunSuite

class YamlTest extends FunSuite {
  val options = YamlOptions()

  test("yaml output for comment headers 1") {
    val dhallFile = DhallFile(Seq(), "-- comment", """{ a = "True" }""".dhall)
    val result    = Yaml.toYaml(dhallFile, options).merge
    expect(
      result ==
        """# comment
          |a: 'True'
          |""".stripMargin
    )
  }

  test("yaml output for comment headers 2") {
    val dhallFile = DhallFile(Seq(), "-- comment\n--\n   --  comment\n", """{ a = "True" }""".dhall)
    val result    = Yaml.toYaml(dhallFile, options).merge
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
    val result    = Yaml.toYaml(dhallFile, options).merge
    expect(
      result ==
        """# comment
          |# comment
          |#{- abc -}
          |a: 'True'
          |""".stripMargin
    )
  }

  test("yaml output for record of lists with indent 4") {
    expect(
      Yaml.toYaml("{a = [1, 2, 3], b= [4, 5]}".dhall, options.copy(indent = 4)).merge ==
        """a:
        |    -   1
        |    -   2
        |    -   3
        |b:
        |    -   4
        |    -   5
        |""".stripMargin
    )
  }

  test("yaml output for strings with special characters") {
    val result1 = Yaml.toYaml("{a = \"-\"}".dhall, options).merge
    expect(
      result1 ==
        """a: "-"
          |""".stripMargin
    )
    val result2 = Yaml.toYaml("{a = \"a-b\"}".dhall, options).merge
    expect(
      result2 ==
        """a: a-b
          |""".stripMargin
    )
    val result3 = Yaml.toYaml("""{a = "\"abc\""}""".dhall, options).merge
    expect(
      result3 ==
        """a: "\"abc\""
          |""".stripMargin
    )
  }

  test("json output with longer indentation") {
    expect(
      Yaml.toYaml("{a = [1, 2, 3], b=True,c= [[4], [5]]}".dhall, options.copy(indent = 4, jsonFormat = true)).merge
        ==
          """{
        |    "a":   [
        |        1,
        |        2,
        |        3
        |    ],
        |    "b":   true,
        |    "c":   [
        |        [
        |            4
        |        ],
        |        [
        |            5
        |        ]
        |    ]
        |}
        |""".stripMargin
    )
  }

}
