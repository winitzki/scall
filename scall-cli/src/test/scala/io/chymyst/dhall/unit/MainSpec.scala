package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Main.OutputMode
import io.chymyst.dhall.Main
import io.chymyst.dhall.Yaml.YamlOptions
import io.chymyst.test.{ManyFixtures, ResourceFiles, TestTimings}
import munit.FunSuite

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, FileInputStream}
import java.nio.file.{Files, Paths}
import scala.util.Try

class MainSpec extends FunSuite with TestTimings with ResourceFiles with ManyFixtures {

  def requireSuccessAtLeast(totalTests: Int, results: Seq[Try[_]], allowFailures: Int = 0) = {
    val (failures, successes) = failureAndSuccessCounts(totalTests, results, allowFailures)
    expect(failures <= allowFailures && successes >= totalTests - allowFailures)
  }

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
      Main.process(Paths.get("."), testIn, testOut, Main.parseArgs(Array(outputMode)), YamlOptions())
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
    expect(runMain("\"3\"", "yaml") == "'3'\n")
    expect(runMain("\"3.14159\"", "yaml") == "'3.14159'\n")
    expect(runMain("True", "yaml") == "true\n")
    expect(runMain("False || False", "yaml") == "false\n")
  }

  test("fail to export yaml if Dhall expression contains unsupported types") {
    expect(runMain("{ a = Natural, b = 2 }", "yaml") == "Error: Unsupported expression type for Yaml export: Natural of type Type\n")
    expect(
      runMain("{ a = 1, b = \\(x : Bool) -> x }", "yaml") == "Error: Unsupported expression type for Yaml export: λ(x : Bool) → x of type ∀(x : Bool) → Bool\n"
    )
    expect(runMain("{ a = Type }", "yaml") == "Error: Unsupported expression type for Yaml export: Type of type Kind\n")
  }

  test("json main test cases") {
    val results = enumerateResourceFiles("yaml-main-cases", Some(".dhall")).map { file =>
      val createDocuments = file.getName matches ".*-document.*"
      val options         = YamlOptions(createDocuments = createDocuments)
      val testOut         = new ByteArrayOutputStream
      try {
        Main.process(file.toPath, new FileInputStream(file), testOut, OutputMode.Json, options.copy(jsonFormat = true))
      } finally {
        testOut.close()
      }
      val resultJson      = new String(testOut.toByteArray)
      val expectedJson    = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath.replace(".dhall", ".json"))))
      if (resultJson != expectedJson) println(s"DEBUG failure in $file, resultJson=$resultJson")
      Try(expect(resultJson == expectedJson))
    }

    requireSuccessAtLeast(totalTests = 24, results, allowFailures = 0)
  }

  test("yaml main test cases") {
    val results = enumerateResourceFiles("yaml-main-cases", Some(".dhall")).map { file =>
      val needToQuote     = file.getName == "quoted.dhall"
      val createDocuments = file.getName matches ".*-document.*"
      val options         = YamlOptions(quoteAllStrings = needToQuote, createDocuments = createDocuments)
      val testOut         = new ByteArrayOutputStream
      try {
        Main.process(file.toPath, new FileInputStream(file), testOut, OutputMode.Yaml, options)
      } finally {
        testOut.close()
      }
      val resultYaml      = new String(testOut.toByteArray)
      val expectedYaml    = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath.replace(".dhall", ".yaml"))))
      if (resultYaml != expectedYaml) println(s"DEBUG failure in $file, resultYaml=$resultYaml")
      Try(expect(resultYaml == expectedYaml))
    }

    requireSuccessAtLeast(totalTests = 24, results, allowFailures = 0)
  }

  test("yaml corner cases from dhall-haskell/yaml") {
    val results = enumerateResourceFiles("yaml-corner-cases", Some(".dhall")).map { file =>
      val needToQuote     = file.getName == "quoted.dhall"
      val createDocuments = file.getName matches ".*-document.*"
      val options         = YamlOptions(quoteAllStrings = needToQuote, createDocuments = createDocuments)
      val testOut         = new ByteArrayOutputStream
      try {
        Main.process(file.toPath, new FileInputStream(file), testOut, OutputMode.Yaml, options)
      } finally {
        testOut.close()
      }
      val resultYaml      = new String(testOut.toByteArray)
      val expectedYaml    = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath.replace(".dhall", ".yaml"))))
      if (resultYaml != expectedYaml) println(s"DEBUG failure in $file, resultYaml=$resultYaml")
      Try(expect(resultYaml == expectedYaml))
    }

    requireSuccessAtLeast(totalTests = 10, results, allowFailures = 0)
  }

  test("parse command-line argument") {
    import OutputMode._
    Seq(
      Array[String]()         -> Dhall,
      Array("text")           -> Text,
      Array("encode")         -> Encode,
      Array("decode")         -> Decode,
      Array("type")           -> GetType,
      Array("hash")           -> GetHash,
      Array("encode", "text") -> Text,
      Array("decode")         -> Decode,
      Array("yaml")           -> Yaml,
      Array("unrecognized")   -> Dhall,
    ).foreach { case (args, mode) =>
      expect(Main.parseArgs(args) == mode)
    }

  }

}
