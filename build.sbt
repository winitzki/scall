val scala2V = "2.13.12"
val scala3V = "3.3.1"
val scalaV  = scala2V

def munitFramework = new TestFramework("munit.Framework")

val munitTest         = "org.scalameta"        %% "munit"   % "0.7.29" % Test
val assertVerboseTest = "com.eed3si9n.expecty" %% "expecty" % "0.16.0" % Test

val fastparse        = "com.lihaoyi"               %% "fastparse"                   % "3.0.2"
val antlr4           = "org.antlr"                  % "antlr4-runtime"              % "4.13.1"
val anltr4_formatter = "com.khubla.antlr4formatter" % "antlr4-formatter-standalone" % "1.2.1" % Compile

val os_lib        = "com.lihaoyi"    %% "os-lib"         % "0.9.2"
val httpRequest   = "com.lihaoyi"    %% "requests"       % "0.8.0"
val enumeratum    = "com.beachape"   %% "enumeratum"     % "1.7.3"
val flatlaf       = "com.formdev"     % "flatlaf"        % "3.2.2"
val izumi_reflect = "dev.zio"        %% "izumi-reflect"  % "2.3.8"
val curryhoward   = "io.chymyst"     %% "curryhoward"    % "0.3.8"
val kindProjector = "org.typelevel"   % "kind-projector" % "0.13.2" cross CrossVersion.full
val jnr_posix     = "com.github.jnr"  % "jnr-posix"      % "3.1.18"
val cbor1         = "co.nstant.in"    % "cbor"           % "0.9"
val cbor2         = "com.upokecenter" % "cbor"           % "4.5.3"
val cbor3         = "io.bullet"      %% "borer-core"     % "1.8.0"

val kindProjectorPlugin = compilerPlugin(kindProjector)

def scala_reflect(value: String) = "org.scala-lang" % "scala-reflect" % value % Compile

lazy val jdkModuleOptions: Seq[String] = {
  val jdkVersion = scala.sys.props.get("JDK_VERSION")
  val options    = if (jdkVersion exists (_ startsWith "8.")) Seq() else Seq("--add-opens", "java.base/java.util=ALL-UNNAMED")
  println(s"Additional JDK ${jdkVersion.getOrElse("")} options: ${options.mkString(" ")}")
  options
}

lazy val root = (project in file("."))
  .settings(scalaVersion := scalaV, crossScalaVersions := Seq(scalaV), name := "scall-root")
  .aggregate(scall_core, scall_testutils, dhall_codec, abnf, scall_macros, scall_typeclasses)

lazy val scall_core = (project in file("scall-core"))
  .settings(
    scalaVersion             := scalaV,
    crossScalaVersions       := Seq(scala2V, scala3V),
    Test / parallelExecution := true,
    Test / fork              := true,
    scalafmtFailOnErrors     := false, // Cannot disable the unicode surrogate pair error in Parser.scala?
    testFrameworks += munitFramework,
    Test / javaOptions ++= jdkModuleOptions,
    Compile / scalacOptions ++= Seq("-Ypatmat-exhaust-depth", "10"), // Cannot make it smaller than 10. Want to speed up compilation.
    // We need to run tests in forked JVM starting with the current directory set to the base resource directory.
    // That base directory should contain `./dhall-lang` and all files below that.
    Test / baseDirectory := (Test / resourceDirectory).value,
    // addCompilerPlugin is a shortcut for libraryDependencies += compilerPlugin(dependency)
    // See https://stackoverflow.com/questions/67579041
    libraryDependencies ++=
      (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => Seq(scala_reflect(scalaVersion.value), kindProjectorPlugin)
        case Some((3, _)) => Seq.empty // No need for scala-reflect with Scala 3.
      }),
    libraryDependencies ++= Seq(
      fastparse,
      antlr4,
      anltr4_formatter,
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
  ).dependsOn(scall_testutils % "test->compile", scall_typeclasses)

lazy val scall_testutils = (project in file("scall-testutils")).settings(
  scalaVersion             := scalaV,
  crossScalaVersions       := Seq(scala2V, scala3V),
  Test / parallelExecution := true,
  Test / fork              := true,
  testFrameworks += munitFramework,
  Test / javaOptions ++= jdkModuleOptions,
  libraryDependencies ++= Seq(munitTest, assertVerboseTest, jnr_posix),
)

lazy val dhall_codec = (project in file("dhall-codec"))
  .settings(
    scalaVersion             := scalaV,
    crossScalaVersions       := Seq(scala2V, scala3V),
    Test / parallelExecution := true,
    Test / fork              := true,
    testFrameworks += munitFramework,
    Test / javaOptions ++= jdkModuleOptions,
    libraryDependencies ++= Seq(izumi_reflect, munitTest, assertVerboseTest),
  ).dependsOn(scall_core, scall_testutils % "test->compile")

lazy val abnf = (project in file("abnf")).settings(
  name                     := "scall-abnf",
  scalaVersion             := scalaV,
  crossScalaVersions       := Seq(scala2V, scala3V),
  Test / parallelExecution := true,
  testFrameworks += munitFramework,
  libraryDependencies ++= Seq(fastparse, munitTest, assertVerboseTest),
)

lazy val scall_macros = (project in file("scall-macros")).settings(
  name                     := "scall-macros",
  scalaVersion             := scalaV,
  crossScalaVersions       := Seq(scala2V, scala3V),
  Test / parallelExecution := true,
  testFrameworks += munitFramework,
  libraryDependencies ++= Seq(izumi_reflect, munitTest, assertVerboseTest),
  libraryDependencies ++=
    (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq(scala_reflect(scalaVersion.value), kindProjectorPlugin)
      case Some((3, _)) => Seq.empty // No need for scala-reflect with Scala 3.
    }),
)

lazy val scall_typeclasses = (project in file("scall-typeclasses")).settings(
  name                     := "scall-typeclasses",
  scalaVersion             := scalaV,
  crossScalaVersions       := Seq(scala2V, scala3V),
  Test / parallelExecution := true,
  testFrameworks += munitFramework,
  libraryDependencies ++= Seq(curryhoward, munitTest, assertVerboseTest),
  libraryDependencies ++=
    (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq(kindProjectorPlugin)
      case Some((3, _)) => Seq.empty
    }),
)
