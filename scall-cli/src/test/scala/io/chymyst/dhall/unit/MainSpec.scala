package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Main
import io.chymyst.test.TestTimings
import munit.FunSuite

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class MainSpec extends FunSuite with TestTimings {
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
      Main.process(testIn, testOut, Main.parseArgs(Array(outputMode)))
    } finally {
      testOut.close()
      testIn.close()
    }
    testOut.toByteArray
  }

  test("run the Main.process function") {
    expect(runMain("1 + 1 + 1\n", "") == "3")
  }

  test("Main.process with parse failure") {
    expect(runMain("1 +", "") contains "Error parsing Dhall input")
  }

  test("Main.process with evaluation failure") {
    expect(runMain("xyz_undefined", "") contains "Variable xyz_undefined is not defined in the current type inference context")
  }

  test("obtain type") {
    expect(runMain("1 + 1 + 1", "type") == "Natural")
    expect(runMain("3.14159", "type") == "Double")
  }

  test("obtain hash 1") {
    expect(runMain("1 + 1 + 1", "hash") == "sha256:15f52ecf91c94c1baac02d5a4964b2ed8fa401641a2c8a95e8306ec7c1e3b8d2")
  }

  test("obtain hash 2") {
    expect(runMain("\\(x : Natural) -> x + 1", "hash") == "sha256:b5fe21628a38725865cb68ff5a9973cecb3f65efaa3096a451e43d336a84eb45")
  }

  test("encode / decode roundtrip") {
    expect(runMain(runMainByteArray("1 + 1 + 1", "encode"), "decode") == "3")
  }

  test("yaml output for literals") {
    expect(runMain("3", "yaml") == "3\n")
    expect(runMain("3.14159", "yaml") == "3.14159\n")
    expect(runMain("\"3\"", "yaml") == "\"3\"\n")
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
    println(result)
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
}
