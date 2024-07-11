package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Main.OutputMode
import io.chymyst.dhall.{Main, Parser}
import io.chymyst.dhall.Yaml.YamlOptions
import io.chymyst.test.ResourceFiles
import munit.FunSuite

import java.io.{ByteArrayOutputStream, FileInputStream}
import java.nio.file.{Files, Paths}

class PerfTest  extends FunSuite with ResourceFiles {

  test( "create yaml from realistic example 1") {
    val file = resourceAsFile("yaml-perftest/create_yaml.dhall").get
    val options         = YamlOptions()
    val testOut         = new ByteArrayOutputStream
    try {
      Main.process(file.toPath, new FileInputStream(file), testOut, OutputMode.Yaml, options)
    } finally {
      testOut.close()
    }
    val resultYaml      = new String(testOut.toByteArray)
    val expectedYaml    = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath.replace(".dhall", ".yaml"))))
    expect(resultYaml == expectedYaml)
  }
}
