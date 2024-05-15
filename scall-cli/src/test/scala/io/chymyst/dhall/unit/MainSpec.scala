package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.dhall.Main
import io.chymyst.test.TestTimings
import munit.FunSuite

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, StringReader}

class MainSpec extends FunSuite with TestTimings {
  test("run the Main.process function") {
    val testOut = new ByteArrayOutputStream()
    val testIn  = new ByteArrayInputStream("1 + 1 + 1\n".getBytes)
    try {
      Main.process(testIn, testOut)
    } finally {
      testOut.close()
      testIn.close()
    }
    expect(new String(testOut.toByteArray) == "3")
  }

}
