val scala2V = "2.13.11"
val scala3V = "3.3.1"
val scalaV = scala2V

val munitTest = "org.scalameta" %% "munit" % "0.7.29" % Test
def munitFramework = new TestFramework("munit.Framework")

val fastparse = "com.lihaoyi" %% "fastparse" % "3.0.2"
val os_lib = "com.lihaoyi" %% "os-lib" % "0.9.2"
val httpRequest = "com.lihaoyi" %% "requests" % "0.8.0"
val assertVerboseTest = "com.eed3si9n.expecty" %% "expecty" % "0.16.0" % Test
val enumeratum = "com.beachape" %% "enumeratum" % "1.7.3"
val flatlaf = "com.formdev" % "flatlaf" % "3.2.2"

val cbor1 = "co.nstant.in" % "cbor" % "0.9"
val cbor2 = "com.upokecenter" % "cbor" % "4.5.2"
val cbor3 = "io.bullet" %% "borer-core" % "1.8.0"

val curryhoward = "io.chymyst" %% "curryhoward" % "0.3.8"

lazy val root = (project in file(".")).settings(
  scalaVersion := scalaV,
  crossScalaVersions := Seq(scalaV),
  name := "scall-root",
).aggregate(
  scall_core,
  scall_testutils,
)

lazy val scall_core = (project in file("scall-core")).settings(
  scalaVersion := scalaV,
  crossScalaVersions := Seq(scala2V, scala3V),
  testFrameworks += munitFramework,
  Test / parallelExecution := true,
  libraryDependencies ++= Seq(
    fastparse,
    munitTest,
    assertVerboseTest,
    enumeratum,
    cbor1,
    cbor2,
    //    cbor3,
    //    curryhoward,
    httpRequest,
    os_lib % Test,
  ),
).dependsOn(scall_testutils % "test->compile")

lazy val scall_testutils = (project in file("scall-testutils")).settings(
  scalaVersion := scalaV,
  crossScalaVersions := Seq(scala2V, scala3V),
  testFrameworks += munitFramework,
  Test / parallelExecution := false,
  Test / fork := true,
  Test / javaOptions ++= Seq("--add-opens", "java.base/java.util=ALL-UNNAMED"),
  libraryDependencies ++= Seq(
    munitTest,
    assertVerboseTest,
  ),
)
