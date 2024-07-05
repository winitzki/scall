package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Yaml
import io.chymyst.dhall.Yaml.YamlOptions
import munit.FunSuite

class JsonTest extends FunSuite {
  val options = YamlOptions(jsonFormat = true)
  test("yaml output for record of lists with indent") {
    expect(Yaml.toYaml("{a = [1, 2, 3], b= [4, 5]}".dhall, options.copy(indent = 4)).merge == """{
                                                                                                |"a":
                                                                                                |    [
                                                                                                |       1,
                                                                                                |       2,
                                                                                                |       3
                                                                                                |    ],
                                                                                                |"b":
                                                                                                |    [
                                                                                                |       4,
                                                                                                |       5
                                                                                                |    ]
                                                                                                |}
                                                                                                |""".stripMargin)
  }

}
