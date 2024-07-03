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
  test("yaml output for record of lists with indent") {
    expect(Yaml.toYaml("{a = [1, 2, 3], b= [4, 5]}".dhall, options.copy(indent = 4)).merge == """a:
                                                              |    - 1
                                                              |    - 2
                                                              |    - 3
                                                              |b:
                                                              |    - 4
                                                              |    - 5
                                                              |""".stripMargin)
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

  test("yaml output for multiline strings with special characters") {
    val result1 = Yaml.toYaml("{a = \"-\\n\\\"-\\\"\"}".dhall, options).merge
    expect(result1 == """a: |
        |  -
        |  "-"
        |""".stripMargin)
    val result2 = Yaml.toYaml("[  \"-\\n\\\"-\\\"\", \"a\", \"b\"]".dhall, options).merge
    expect(result2 == """- |
          |  -
          |  "-"
          |- a
          |- b
          |""".stripMargin)
    val result3 = Yaml.toYaml("{a = [\"-\\n\\\"-\\\"\", \"a\", \"b\"]}".dhall, options).merge
    expect(result3 == """a:
          |  - |
          |    -
          |    "-"
          |  - a
          |  - b
          |""".stripMargin)

  }

  test("yaml output for strings with special characters") {
    val result = Yaml.toYaml("{a = \"a-b: c\"}".dhall, options).merge
    expect(
      result ==
        """a: "a-b: c"
        |""".stripMargin
    )
  }

  test("yaml output for record of lists of strings") {
    val result = Yaml.toYaml("{a = [\" - c\",\"d\",\"e\"]}".dhall, options).merge
    expect(
      result ==
        """a:
          |  - " - c"
          |  - d
          |  - e
          |""".stripMargin
    )
  }

  test("yaml output for record of length-1 lists of strings") {
    val result = Yaml.toYaml("{a = [\"e\"]}".dhall, options).merge
    expect(
      result ==
        """a:
          |  - e
          |""".stripMargin
    )
  }

  test("yaml output for record of records of strings") {
    val result = Yaml.toYaml("{a.b =\"e\", a.c = \"f\" }".dhall.betaNormalized, options).merge
    expect(
      result ==
        """a:
          |  b: e
          |  c: f
          |""".stripMargin
    )
  }

  test("yaml output for record of length-1 records of strings") {
    val result = Yaml.toYaml("{a.b =\"e\" }".dhall, options).merge
    expect(
      result ==
        """a:
          |  b: e
          |""".stripMargin
    )
  }

  test("yaml output for record of length-0 records") {
    val result = Yaml.toYaml("{a.b = {=} }".dhall, options).merge
    expect(
      result ==
        """a:
          |  b: {}
          |""".stripMargin
    )
  }

  test("yaml output for record of length-0 lists") {
    val result = Yaml.toYaml("{a.b = [] : List Bool }".dhall, options).merge
    expect(
      result ==
        """a:
          |  b: []
          |""".stripMargin
    )
  }

  test("yaml should fail if expression does not typecheck") {
    val result = Yaml.toYaml("1 + True".dhall, options)
    expect(
      result == Left("List(Inferred type Bool differs from the expected type Natural, expression under type inference: True, type inference context = {})")
    )
  }
}
