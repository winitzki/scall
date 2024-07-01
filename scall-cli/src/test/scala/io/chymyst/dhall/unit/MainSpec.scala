package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Main
import io.chymyst.dhall.Main.OutputMode
import io.chymyst.test.{ResourceFiles, TestTimings}
import munit.FunSuite

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, FileInputStream}
import java.nio.file.{Files, Path, Paths}
import scala.util.Try
import TestUtils.requireSuccessAtLeast

class MainSpec extends FunSuite with TestTimings with ResourceFiles {
  def runMain(input: String, outputMode: String): String                    = {
    new String(runMainByteArray(input, outputMode))
  }
  def runMain(input: Array[Byte], outputMode: String): String               = {
    new String(runMainByteArray(input, outputMode))
  }
  def runMainByteArray(input: String, outputMode: String): Array[Byte]      = {
    runMainByteArray(input.getBytes("UTF-8"), outputMode)
  }
  def runMainByteArray(input: Array[Byte], outputMode: String): Array[Byte] = {
    val testOut = new ByteArrayOutputStream()
    val testIn  = new ByteArrayInputStream(input)
    try {
      Main.process(Paths.get("."), testIn, testOut, Main.parseArgs(Array(outputMode)))
    } finally {
      testOut.close()
      testIn.close()
    }
    testOut.toByteArray
  }

  test("run the Main.process function") {
    expect(runMain("1 + 1 + 1\n", "") == "3\n")
  }

  test("Main.process with parse failure") {
    expect(runMain("1 +", "") contains "Error parsing Dhall input")
  }

  test("Main.process with evaluation failure") {
    expect(runMain("xyz_undefined", "") contains "Variable xyz_undefined is not defined in the current type inference context")
  }

  test("obtain type") {
    expect(runMain("1 + 1 + 1", "type") == "Natural\n")
    expect(runMain("3.14159", "type") == "Double\n")
  }

  test("obtain hash 1") {
    expect(runMain("1 + 1 + 1", "hash") == "sha256:15f52ecf91c94c1baac02d5a4964b2ed8fa401641a2c8a95e8306ec7c1e3b8d2\n")
  }

  test("obtain hash 2") {
    expect(runMain("\\(x : Natural) -> x + 1", "hash") == "sha256:b5fe21628a38725865cb68ff5a9973cecb3f65efaa3096a451e43d336a84eb45\n")
  }

  test("encode / decode roundtrip") {
    expect(runMain(runMainByteArray("1 + 1 + 1", "encode"), "decode") == "3\n")
  }

  test("export text") {
    expect(runMain("\"3\"", "dhall") == "\"3\"\n")
    expect(runMain("\"3\"", "text") == "3\n")
  }

  test("fail to export text if Dhall expression is not text") {
    expect(runMain("1 + 1 + 1", "text") == "Error: Dhall expression should have type Text but is instead: NaturalLiteral(3)\n")
  }

  test("yaml output for literals") {
    expect(runMain("3", "yaml") == "3\n")
    expect(runMain("3.14159", "yaml") == "3.14159\n")
    expect(runMain("\"3\"", "yaml") == "\"3\"\n")
    expect(runMain("True", "yaml") == "true\n")
    expect(runMain("False || False", "yaml") == "false\n")
  }

  test("fail to export yaml if Dhall expression contains unsupported types") {
    expect(runMain("{ a = 12:00:00, b = 2 }", "yaml") == "Error: Unsupported expression type for Yaml export: 12:00:00\n")
    expect(runMain("{ a = 1, b = \\(x : Bool) -> x }", "yaml") == "Error: Unsupported expression type for Yaml export: λ(x : Bool) → x\n")
    expect(runMain("{ a = Type }", "yaml") == "Error: Unsupported expression type for Yaml export: Type\n")
  }

  test("yaml output for lists of numbers") {
    expect(
      runMain("[1, 2, 3]", "yaml") ==
        """- 1
        |- 2
        |- 3
        |""".stripMargin
    )
  }

  test("yaml output for records of numbers") {
    expect(
      runMain("{ a = 1, b = 2, c = 3 }", "yaml") ==
        """a: 1
        |b: 2
        |c: 3
        |""".stripMargin
    )
  }

  test("yaml output for lists of lists of numbers") {
    expect(
      runMain("[[1, 2, 3], [4, 5]]", "yaml") ==
        """- - 1
        |  - 2
        |  - 3
        |- - 4
        |  - 5
        |""".stripMargin
    )
  }

  test("yaml output for record of lists") {
    expect(runMain("{a = [1, 2, 3], b= [4, 5]}", "yaml") == """a:
                                    |  - 1
                                    |  - 2
                                    |  - 3
                                    |b:
                                    |  - 4
                                    |  - 5
                                    |""".stripMargin)
  }

  test("yaml output for list of records") {
    val result = runMain("[{a = 1, b = 2}, {a = 3, b = 4}]", "yaml")
    expect(result == """- a: 1
                                                                    |  b: 2
                                                                    |- a: 3
                                                                    |  b: 4
                                                                    |""".stripMargin)
  }

  test("yaml output for record of records") {
    expect(runMain("{x = {a = 1, b = 2}, y = {c = 3, d = 4}}", "yaml") == """x:
                                                                            |  a: 1
                                                                            |  b: 2
                                                                            |'y':
                                                                            |  c: 3
                                                                            |  d: 4
                                                                            |""".stripMargin)

  }

  test("yaml corner cases from dhall-haskell/yaml") {
    val parentPath = resourceAsFile("yaml-corner-cases").get.toPath.getParent
    val results    = enumerateResourceFiles("yaml-corner-cases", Some(".dhall")).map { file =>
      // val relativePathForTest                 = parentPath.relativize(file.toPath)
      val testOut      = new ByteArrayOutputStream
      try {
        Main.process(Path.of(file.toURI), new FileInputStream(file), testOut, OutputMode.Yaml)
      } finally {
        testOut.close()
      }
      val resultYaml   = new String(testOut.toByteArray)
      val expectedYaml = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath.replace(".dhall", ".yaml"))))
      Try(expect(resultYaml == expectedYaml))
    }
    requireSuccessAtLeast(11, results, 10)
  }
}
