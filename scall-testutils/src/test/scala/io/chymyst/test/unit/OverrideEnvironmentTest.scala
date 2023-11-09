package io.chymyst.test.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.test.OverrideEnvironment
import munit.FunSuite

import scala.util.{Failure, Success, Try}

class OverrideEnvironmentTest extends FunSuite with OverrideEnvironment {

  test("example in the code") {
    object MyEnv extends OverrideEnvironment
    MyEnv.runInFakeEnvironmentWith("HOME" -> "/fake/home", "TMPDIR" -> "/fake/tmpdir") {
      myTestCode()
    }

    // Test code will now see the modified environment.
    def myTestCode() = {
      val home = System.getenv("HOME")
      val tmpdir = Option(System.getenv("TMPDIR")).getOrElse("/tmp")
      assert(home == "/fake/home" && tmpdir == "/fake/tmpdir")
    }
  }
  val fakeVars = Seq(
    "xyzpdq1" -> "value1",
    "xyzpdq2" -> "value2",
    "xyzpdq3" -> "value3",
  )

  test("fake environment variables can be added") {
    // Initially the environment should not contain those variables.
    expect(fakeVars.map(_._1).forall(System.getenv(_) == null))

    // Add them and verify that they are present.
    runInFakeEnvironmentWith(fakeVars: _*) {
      expect(fakeVars.map(_._1).map(System.getenv) == fakeVars.map(_._2))
    }
    // Now the environment again does not contain those variables.
    expect(fakeVars.map(_._1).forall(System.getenv(_) == null))
  }

  test("exceptions are passed through but fake variables are still removed") {
    // Initially the environment should not contain those variables.
    expect(fakeVars.map(_._1).forall(System.getenv(_) == null))

    // Add them and verify that they are present. Then throw an exception.
    val testResult = Try {
      runInFakeEnvironmentWith(fakeVars: _*) {
        expect(fakeVars.map(_._1).map(System.getenv) == fakeVars.map(_._2))
        throw new Exception("testing with exceptions")
      }
    }
    expect(testResult.failed.get.getMessage == "testing with exceptions")

    // Now the environment again does not contain those variables.
    expect(fakeVars.map(_._1).forall(System.getenv(_) == null))
  }

  test("fake environment variables can be removed") {
    expect(System.getenv("HOME") != null && System.getenv("HOME").matches("^.*/[-A-z0-9_].*"))
    expect(System.getenv("USER") != null && System.getenv("USER").matches(".*[-A-z0-9_.].*"))
    runInFakeEnvironmentWithout("HOME", "USER") {
      expect(System.getenv("HOME") == null && System.getenv("USER") == null)
    }
    expect(System.getenv("HOME") != null && System.getenv("HOME").matches("^.*/[-A-z0-9_].*"))
    expect(System.getenv("USER") != null && System.getenv("USER").matches(".*[-A-z0-9_.].*"))
  }

  test("fake environment variables can be nested, added, and removed") {
    // Initially the environment should not contain those variables.
    expect(fakeVars.map(_._1).forall(System.getenv(_) == null))
    runInFakeEnvironmentWith("HOME" -> "fakehome", fakeVars(0)) {
      expect(System.getenv("HOME") == "fakehome")
      expect(System.getenv(fakeVars(0)._1) == fakeVars(0)._2)
      runInFakeEnvironmentWithout(fakeVars.map(_._1): _*) {
        expect(System.getenv(fakeVars(0)._1) == null)
        expect(System.getenv("HOME") == "fakehome")
        runInFakeEnvironmentWith(fakeVars(0)._1 -> "fakevalue") {
          expect(System.getenv("HOME") == "fakehome")
          expect(System.getenv(fakeVars(0)._1) == "fakevalue")
        }
        expect(System.getenv(fakeVars(0)._1) == null)
        expect(System.getenv("HOME") == "fakehome")
      }
      expect(System.getenv("HOME") == "fakehome")
      expect(System.getenv(fakeVars(0)._1) == fakeVars(0)._2)
    }
    expect(System.getenv("HOME") != null && System.getenv("HOME").matches("^.*/[-A-z0-9_].*"))
    expect(fakeVars.map(_._1).forall(System.getenv(_) == null))
  }
}
