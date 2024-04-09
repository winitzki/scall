package io.chymyst.test.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.test.Throwables
import munit.FunSuite

import scala.util.Try

class ThrowablesTest extends FunSuite {
  test("print throwables, stack traces, and causes") {
    val t1      = new Exception("test1")
    val t2      = new Exception("test2", t1)
    val t3      = new Exception("test3", t2)
    val result1 = Throwables.printThrowable(Try(throw t1).failed.get)

    val result2 = Throwables.printThrowable(Try(throw t2).failed.get)

    val result3 = Throwables.printThrowable(Try(throw t3).failed.get)

    expect(
      result1.contains("java.lang.Exception: test1") &&
        result1.contains("$1(ThrowablesTest.scala:11)")
    )

    expect(
      result2.contains("java.lang.Exception: test2") &&
        result2.contains("$1(ThrowablesTest.scala:12)") &&
        result2.contains("Caused by: java.lang.Exception: test1") &&
        result2.contains("$1(ThrowablesTest.scala:11)")
    )

    expect(
      result3.contains("java.lang.Exception: test3") &&
        result3.contains("$1(ThrowablesTest.scala:13)") &&
        result3.contains("java.lang.Exception: test2") &&
        result3.contains("$1(ThrowablesTest.scala:12)") &&
        result3.contains("Caused by: java.lang.Exception: test1") &&
        result3.contains("$1(ThrowablesTest.scala:11)")
    )
  }

}
