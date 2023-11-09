package io.chymyst.test

import scala.util.chaining.scalaUtilChainingOps

/**
 * Test-only utility to run test code with fake values of the Unix shell environment variables.
 * The environment overrides will be in effect only during JVM tests; the actual environment remains unchanged as it cannot be changed.
 * The test code can just read System.getenv() as usual and it will get the fake environment variables.
 * If the test code throws exceptions, they will not be caught but the previous environment will be restored.
 *
 * Fake environments may be nested within test code.
 *
 * Implementation according to https://stackoverflow.com/questions/318239/how-do-i-set-environment-variables-from-java/496849
 *
 * Example:
 *
 * {{{
 *   import io.chymyst.test.OverrideEnvironment
 *
 *   object MyEnv extends OverrideEnvironment // Can use several of those with independent overrides.
 *
 *   MyEnv.runInFakeEnvironmentWith("HOME" -> "/fake/home", "TMPDIR" -> "/fake/tmpdir") {
 *      myTestCode()
 *   }
 *
 *   // Test code will now see the modified environment.
 *   def myTestCode() = {
 *      val home = System.getenv("HOME")
 *
 *      val tmpdir = Option(System.getenv("TMPDIR")).getOrElse("/tmp")
 *
 *      assert(home == "/fake/home" && tmpdir == "/fake/tmpdir")
 *   }
 * }}}
 *
 * Tests with recent JDK will require the options: `java --add-opens java.base/java.util=ALL-UNNAMED`
 *
 * In build.sbt these options are set like this:
 *
 * {{{
 *   Test / javaOptions ++= Seq("--add-opens", "java.base/java.util=ALL-UNNAMED"),
 *   Test / fork := true,
 * }}}
 */
trait OverrideEnvironment {

  /** Create a fake environment with some variables and run the code with that fake environment.
   * Note: this will not catch any exceptions that the test code might throw.
   *
   * @param vars        The names and values of the environment variables that need to be changed.
   * @param testProgram The test code that needs to run in the fake environment.
   * @tparam R The type of test code's returned result value.
   * @return The result value computed by the test code.
   */
  def runInFakeEnvironmentWith[R](vars: (String, String)*)(testProgram: => R): R = {
    val fakeVars: Map[String, String] = vars.toMap
    val correspondingOldVariablesIfExisted: Map[String, String] = fakeVars.keys.flatMap { key =>
      Option(System.getenv(key)).map(value => (key, value)) // Return None if this variable was not present in the old environment.
    }.toMap
    fakeVars.foreach { case (k, v) => putIntoFakeEnvironment(k, v) }
    try {
      testProgram
    } finally {
      fakeVars.keys.foreach { k =>
        correspondingOldVariablesIfExisted.get(k) match {
          case None => removeFromFakeEnvironment(k)
          case Some(oldValue) => putIntoFakeEnvironment(k, oldValue)
        }
      }
    }
  }

  /** Create a fake environment where some variables are _not_ present, and run the code with that fake environment.
   * Note: this will not catch any exceptions that the test code might throw.
   *
   * @param vars        The names of the environment variables that need to be removed.
   * @param testProgram The test code that needs to run in the fake environment.
   * @tparam R The type of test code's returned result value.
   * @return The result value computed by the test code.
   */
  def runInFakeEnvironmentWithout[R](vars: String*)(testProgram: => R): R = {
    val correspondingOldVariablesIfExisted: Map[String, String] = vars.flatMap { key =>
      Option(System.getenv(key)).map(value => (key, value)) // Return None if this variable was not present in the old environment.
    }.toMap
    vars.foreach(removeFromFakeEnvironment)
    try {
      testProgram
    } finally {
      correspondingOldVariablesIfExisted.foreach { case (k, oldValue) => putIntoFakeEnvironment(k, oldValue) }
    }
  }

  // This is a mutable dictionary.
  private lazy val fakeEnvironment: java.util.Map[String, String] =
    System.getenv.getClass
      .getDeclaredField("m")
      .tap(_ setAccessible true)
      .get(System.getenv)
      .asInstanceOf[java.util.Map[String, String]]

  private def putIntoFakeEnvironment(envVar: String, envVarValue: String): Unit = fakeEnvironment.put(envVar, envVarValue)

  private def removeFromFakeEnvironment(envVar: String): Unit = fakeEnvironment.remove(envVar)
}
