package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Main
import io.chymyst.test.TestTimings
import munit.FunSuite

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, StringReader}

class MainSpec extends FunSuite with TestTimings {

  def runMain(input: String): String = {
    val testOut = new ByteArrayOutputStream()
    val testIn  = new ByteArrayInputStream(input.getBytes)
    try {
      Main.process(testIn, testOut)
    } finally {
      testOut.close()
      testIn.close()
    }
    new String(testOut.toByteArray)
  }

  test("run the Main.process function") {
    expect(runMain("1 + 1 + 1\n") == "3")
  }

  test("Main.process with parse failure") {
    expect(runMain("1 +") contains "Error parsing Dhall input")
  }

  test("Main.process with evaluation failure") {
    expect(runMain("xyz_undefined") contains "Variable xyz_undefined is not defined in the current type inference context")
  }

}
