package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Main
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
    val elapsedS       = elapsedNs.toDouble / 1e9
    println(s"Yaml created in $elapsedS seconds")
    expect(resultYaml == expectedYaml)
  }
}
