package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Syntax.DhallFile
import io.chymyst.dhall.Toml.TomlOptions
import io.chymyst.dhall.{Toml, Yaml}
import io.chymyst.dhall.Yaml.YamlOptions
import munit.FunSuite

class TomlTest extends FunSuite {

  test("toml output should ignore comment headers 1") {
    val dhallFile = DhallFile(Seq(), "-- comment", """{ a = "True" }""".dhall)
    val result    = Toml.toToml(dhallFile, TomlOptions()).merge
    expect(
      result ==
        """a = "True"
          |""".stripMargin
    )
  }

  test("toml output for record of lists with indent 4") {
    expect(
      Toml.toToml("{a = [1, 2, 3], b = [4, 5]}".dhall).merge ==
        """a = [ 1, 2, 3 ]
        |b = [ 4, 5 ]
        |""".stripMargin
    )
  }

  test("toml output for strings with special characters") {
    val result1 = Toml.toToml("{a = \"-\"}".dhall).merge
    expect(
      result1 ==
        """a = "-"
          |""".stripMargin
    )
    val result2 = Toml.toToml("{a = \"a-b\"}".dhall).merge
    expect(
      result2 ==
        """a = "a-b"
          |""".stripMargin
    )
    val result3 = Toml.toToml("""{a = "\"abc\""}""".dhall).merge
    expect(
      result3 ==
        """a = "\"abc\""
          |""".stripMargin
    )
  }

  test("toml output with nested records") {
    expect(
      Toml.toToml("{ a = { b = [1, 2, 3] , c = True } }".dhall).merge
        ==
          """[a]
        |b = [ 1, 2, 3 ]
        |c = true
        |""".stripMargin
    )
  }

}
