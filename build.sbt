import sbt.Keys.homepage
import sbt.url
import sbtassembly.AssemblyKeys.assembly
import xerial.sbt.Sonatype.GitHubHosting

val thisReleaseVersion = "0.2.1"

val scala2V                = "2.13.16"
val scala212V              = "2.12.20"
val scala3V                = "3.4.2"
val scalaV                 = scala2V
val supportedScalaVersions = Seq(scala2V, scala3V)

def munitFramework = new TestFramework("munit.Framework")

val munitTest         = "org.scalameta"        %% "munit"   % "0.7.29" % Test
val assertVerboseTest = "com.eed3si9n.expecty" %% "expecty" % "0.16.0" % Test

val fastparse        = "com.lihaoyi"               %% "fastparse"                   % "3.1.1"
val antlr4           = "org.antlr"                  % "antlr4-runtime"              % "4.13.1"
val anltr4_formatter = "com.khubla.antlr4formatter" % "antlr4-formatter-standalone" % "1.2.1" % Provided

val os_lib              = "com.lihaoyi"    %% "os-lib"                % "0.9.2"
val httpRequest         = "com.lihaoyi"    %% "requests"              % "0.8.0"
val enumeratum          = "com.beachape"   %% "enumeratum"            % "1.7.3"
val izumi_reflect       = "dev.zio"        %% "izumi-reflect"         % "2.3.8"
val zio_schema          = "dev.zio"        %% "zio-schema"            % "1.2.1"
val zio_schema_deriving = "dev.zio"        %% "zio-schema-derivation" % "1.1.1"
val kindProjector       = "org.typelevel"   % "kind-projector"        % "0.13.3" cross CrossVersion.full
val jnr_posix           = "com.github.jnr"  % "jnr-posix"             % "3.1.19"
val cbor1               = "co.nstant.in"    % "cbor"                  % "0.9"
val cbor2               = "com.upokecenter" % "cbor"                  % "4.5.3"
val reflections         = "org.reflections" % "reflections"           % "0.10.2"
val mainargs            = "com.lihaoyi"    %% "mainargs"              % "0.7.0"
val sourcecode          = "com.lihaoyi"    %% "sourcecode"            % "0.4.2"

// Not used now:
val flatlaf      = "com.formdev"               % "flatlaf"       % "3.2.2"
val cbor3        = "io.bullet"                %% "borer-core"    % "1.8.0"
val scalahashing = "com.desmondyeung.hashing" %% "scala-hashing" % "0.1.0"

val kindProjectorPlugin = compilerPlugin(kindProjector)

def scala_reflect(value: String) = "org.scala-lang" % "scala-reflect" % value % Compile

lazy val publishingOptions = Seq(
  organization           := "io.chymyst",
  version                := thisReleaseVersion,
  ThisBuild / version    := thisReleaseVersion,
  licenses               := Seq("Apache License, Version 2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage               := Some(url("https://github.com/winitzki/scall")),
  description            := "Implementation of the Dhall language in Scala, with Scala language bindings",
  publishTo              := sonatypePublishToBundle.value,
  sonatypeProjectHosting := Some(GitHubHosting("winitzki", "scall", "winitzki@gmail.com")),
)

lazy val noPublishing =
  Seq(version := thisReleaseVersion, publishArtifact := false, publishMavenStyle := true, publish := {}, publishLocal := {}, publish / skip := true)

lazy val jdkModuleOptions: Seq[String] = {
  val jdkVersion = scala.sys.props.get("JDK_VERSION")
  val options    = if (jdkVersion exists (_ startsWith "8.")) Seq() else Seq("--add-opens", "java.base/java.util=ALL-UNNAMED")
  println(s"Additional JDK ${jdkVersion.getOrElse("")} options: ${options.mkString(" ")}")
  options ++ Seq("-Xss2097152")
}

lazy val root = (project in file("."))
  .settings(noPublishing)
  .settings(scalaVersion := scalaV, crossScalaVersions := Seq(scalaV), name := "scall-root")
  .aggregate(scall_core, scall_testutils, dhall_codec, abnf, scall_macros, scall_typeclasses, scall_cli, nano_dhall, fastparse_memoize)

lazy val nano_dhall = (project in file("nano-dhall")) // This is a POC project.
  .settings(noPublishing)
  .settings(
    name                     := "nano-dhall",
    scalaVersion             := scalaV,
    crossScalaVersions       := supportedScalaVersions,
    Test / parallelExecution := true,
    Test / fork              := true,
    coverageEnabled          := false,
    scalafmtFailOnErrors     := false, // Cannot disable the unicode surrogate pair error in Parser.scala?
    testFrameworks += munitFramework,
    Test / javaOptions ++= jdkModuleOptions,
    Compile / scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _))       => Seq("-Ydebug")
        case Some((2, 12 | 13)) => Seq("-Ypatmat-exhaust-depth", "10") // Cannot make it smaller than 10. Want to speed up compilation.
      }
    },
    ThisBuild / scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _))       => Seq("-Ykind-projector") // Seq("-Ykind-projector:underscores")
        case Some((2, 12 | 13)) => Seq()                   // Seq("-Xsource:3", "-P:kind-projector:underscore-placeholders")
      }
    },
    // We need to run tests in forked JVM starting with the current directory set to the base resource directory.
    // That base directory should contain `./dhall-lang` and all files below that.
    Test / baseDirectory     := (Test / resourceDirectory).value,
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
      cbor2,
      //      scalahashing,
      //    cbor3,
      httpRequest,
      os_lib % Test,
    ),
  ).dependsOn(scall_testutils % "test->compile", scall_typeclasses, fastparse_memoize)

lazy val fastparse_memoize = (project in file("fastparse-memoize"))
  .settings(publishingOptions)
  .settings(
    name               := "fastparse-memoize",
    scalaVersion       := scalaV,
    crossScalaVersions := supportedScalaVersions,
    testFrameworks += munitFramework,
    Test / javaOptions ++= jdkModuleOptions,
    libraryDependencies ++= Seq(fastparse, sourcecode, munitTest, assertVerboseTest),
  ).dependsOn(scall_testutils % "test->compile")

lazy val scall_core = (project in file("scall-core"))
  .settings(publishingOptions)
  .settings(
    name                     := "dhall-scala-core",
    scalaVersion             := scalaV,
    crossScalaVersions       := supportedScalaVersions,
    Test / parallelExecution := true,
    Test / fork              := true,
    scalafmtFailOnErrors     := false, // Cannot disable the unicode surrogate pair error in Parser.scala?
    testFrameworks += munitFramework,
    Test / javaOptions ++= jdkModuleOptions,
    Compile / scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _))       => Seq("-Ydebug")
        case Some((2, 12 | 13)) => Seq("-Ypatmat-exhaust-depth", "10") // Cannot make it smaller than 10. Want to speed up compilation.
      }
    },
    ThisBuild / scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _))       => Seq("-Ykind-projector") // Seq("-Ykind-projector:underscores")
        case Some((2, 12 | 13)) => Seq()                   // Seq("-Xsource:3", "-P:kind-projector:underscore-placeholders")
      }
    },
    // We need to run tests in forked JVM starting with the current directory set to the base resource directory.
    // That base directory should contain `./dhall-lang` and all files below that.
    Test / baseDirectory     := (Test / resourceDirectory).value,
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
//      scalahashing,
      cbor3,
      httpRequest,
      os_lib % Test,
    ),
  ).dependsOn(scall_testutils % "test->compile", scall_typeclasses, fastparse_memoize)

lazy val scall_testutils = (project in file("scall-testutils"))
  .settings(publishingOptions)
  .settings(
    name                     := "dhall-scala-testutils",
    scalaVersion             := scalaV,
    crossScalaVersions       := supportedScalaVersions,
    Test / parallelExecution := true,
    Test / fork              := true,
    testFrameworks += munitFramework,
    Test / javaOptions ++= jdkModuleOptions,
    libraryDependencies ++= Seq(munitTest, assertVerboseTest, jnr_posix),
  )

lazy val dhall_codec = (project in file("dhall-codec"))
  .settings(publishingOptions)
  .settings(
    name                       := "dhall-scala-bindings",
    scalaVersion               := scalaV,
    crossScalaVersions         := supportedScalaVersions,
    Test / parallelExecution   := true,
    Test / fork                := true,
    testFrameworks += munitFramework,
    Test / javaOptions ++= jdkModuleOptions,
    libraryDependencies ++= Seq(izumi_reflect, munitTest, assertVerboseTest, reflections),
    assembly / mainClass       := Some("io.chymyst.dhall.codec.DhallShim"),
    assembly / assemblyJarName := "dhall-shim.jar",
    assembly / assemblyMergeStrategy ~= (old => {
      case PathList("com", "upokecenter", "util", "DataUtilities.class") => MergeStrategy.last
      case PathList("module-info.class")                                 => MergeStrategy.discard
      case x                                                             => old(x)
    }),
  ).dependsOn(scall_core, scall_testutils % "test->compile")

lazy val scall_cli = (project in file("scall-cli"))
  .settings(publishingOptions)
  .settings(
    name                       := "dhall-scala-cli",
    scalaVersion               := scalaV,
    crossScalaVersions         := supportedScalaVersions,
    Test / parallelExecution   := true,
    Test / fork                := true,
    testFrameworks += munitFramework,
    Test / javaOptions ++= jdkModuleOptions,
    libraryDependencies ++= Seq(munitTest, assertVerboseTest, mainargs),
    assembly / mainClass       := Some("io.chymyst.dhall.Main"),
    assembly / assemblyJarName := "dhall.jar",
    assembly / assemblyMergeStrategy ~= (old => {
      case PathList("com", "upokecenter", "util", "DataUtilities.class") => MergeStrategy.last
      case x                                                             => old(x)
    }),
    // Want to publish the application JAR.
    Compile / assembly / artifact ~= { art: Artifact =>
      art.withClassifier(Some("assembly"))
    },
    addArtifact(Compile / assembly / artifact, assembly),
  ).dependsOn(scall_core, scall_testutils % "test->compile")

lazy val abnf = (project in file("abnf"))
  .settings(noPublishing)
  .settings(
    name                     := "dhall-scala-abnf",
    scalaVersion             := scalaV,
    crossScalaVersions       := supportedScalaVersions,
    Test / parallelExecution := true,
    testFrameworks += munitFramework,
    libraryDependencies ++= Seq(fastparse, munitTest, assertVerboseTest),
  )

lazy val scall_macros = (project in file("scall-macros"))
  .settings(publishingOptions)
  .settings(
    name                     := "dhall-scala-macros",
    scalaVersion             := scalaV,
    crossScalaVersions       := supportedScalaVersions,
    Test / parallelExecution := true,
    testFrameworks += munitFramework,
    libraryDependencies ++= Seq(izumi_reflect, munitTest, assertVerboseTest),
    libraryDependencies ++=
      (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => Seq(scala_reflect(scalaVersion.value), kindProjectorPlugin)
        case Some((3, _)) => Seq.empty // No need for scala-reflect with Scala 3.
      }),
  )

lazy val scall_typeclasses = (project in file("scall-typeclasses"))
  .settings(publishingOptions)
  .settings(
    name                     := "dhall-scala-typeclasses",
    scalaVersion             := scalaV,
    crossScalaVersions       := supportedScalaVersions,
    Test / parallelExecution := true,
    testFrameworks += munitFramework,
    libraryDependencies ++= Seq(munitTest, assertVerboseTest),
    libraryDependencies ++=
      (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => Seq(kindProjectorPlugin)
        case Some((3, _)) => Seq.empty
      }),
  )

/////////////////////////////////////////////////////////////////////////////////////////////////////
// Publishing to Sonatype Maven repository
publishMavenStyle      := true
publishTo              := sonatypePublishToBundle.value
sonatypeProfileName    := "io.chymyst"
//ThisBuild / sonatypeCredentialHost := sonatypeCentralHost  // Not relevant because io.chymyst was created before 2021.
//
Test / publishArtifact := false
//
/////////////////////////////////////////////////////////////////////////////////////////////////////
