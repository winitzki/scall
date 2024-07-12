package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.{Main, Parser}
import io.chymyst.dhall.Main.OutputMode
import io.chymyst.dhall.Yaml.YamlOptions
import io.chymyst.test.{ResourceFiles, TestTimings}
import munit.FunSuite

import java.io.{ByteArrayOutputStream, FileInputStream}
import java.nio.file.{Files, Paths}

class PerfTest extends FunSuite with ResourceFiles with TestTimings {

  test("create yaml from realistic example 1") {
    val file           = resourceAsFile("yaml-perftest/create_yaml.dhall").get
    val options        = YamlOptions()
    val testOut        = new ByteArrayOutputStream
    val (_, elapsedNs) = elapsedNanos {
      try {
        Main.process(file.toPath, new FileInputStream(file), testOut, OutputMode.Yaml, options)
      } finally {
        testOut.close()
      }
    }
    val resultYaml     = new String(testOut.toByteArray)
    val expectedYaml   = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath.replace(".dhall", ".yaml"))))
    val elapsed        = elapsedNs.toDouble / 1e9
    println(s"Yaml created in $elapsed seconds")
    expect(resultYaml == expectedYaml)
    expect(elapsed / 1e9 < 10.0)
  }

  test("parse schema.dhall 20 times") {
    val file    = resourceAsFile("yaml-perftest/schema.dhall").get
    val results = (1 to 20).map { i =>
      val (_, elapsed) = elapsedNanos(Parser.parseDhallStream(new FileInputStream(file)).get.value.value)
      println(s"iteration $i : schema.dhall parsed in ${elapsed / 1e9} seconds")
      expect(elapsed / 1e9 < 0.5)
    }
  }

  test("parse renderAs.dhall") {
    val file         = resourceAsFile("yaml-perftest/renderAs.dhall").get
    val (_, elapsed) = elapsedNanos(Parser.parseDhallStream(new FileInputStream(file)).get.value.value)
    println(s"Prelude/JSON/renderAs.dhall parsed in ${elapsed / 1e9} seconds")
    expect(elapsed / 1e9 < 1.0)
  }

  test("parse largeExpressionA.dhall") {
    val file         = resourceAsFile("yaml-perftest/largeExpressionA.dhall").get
    val (_, elapsed) = elapsedNanos(Parser.parseDhallStream(new FileInputStream(file)).get.value.value)
    println(s"largeExpressionA.dhall parsed in ${elapsed / 1e9} seconds")
    expect(elapsed / 1e9 < 0.5)
  }

  test("parse nested parentheses") {
    val n            = 30 // More than 30 gives stack overflow.
    val input        = "(" * n + "1" + ")" * n
    val (_, elapsed) = elapsedNanos(Parser.parseDhall(input).get.value.value)
    println(s"$n nested parentheses parsed in ${elapsed / 1e9} seconds")
    expect(elapsed / 1e9 < 0.5)
  }
}
