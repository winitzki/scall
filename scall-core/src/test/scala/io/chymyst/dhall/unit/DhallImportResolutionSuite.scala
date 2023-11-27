package io.chymyst.dhall.unit

import com.eed3si9n.expecty.Expecty.expect
import fastparse.Parsed
import io.chymyst.dhall.Parser
import io.chymyst.dhall.Syntax.DhallFile
import io.chymyst.test.Throwables.printThrowable
import io.chymyst.test.{OverrideEnvironment, ResourceFiles}
import os.root

import java.io.{File, FileInputStream}
import scala.util.Try

object DhallImportResolutionSuite {
  def readHeadersFromEnv(envVarsFile: File): Seq[(String, String)] = if (envVarsFile.exists) {
    val Parsed.Success(DhallFile(_, envs), _) = Parser.parseDhallStream(new FileInputStream(envVarsFile))
    envs.betaNormalized.toPrimitiveValue match {
      case Some(t: List[Map[String, AnyRef]]) =>
        t.flatMap {
          case x: Map[String, AnyRef] if x.keys.toList.sorted == List("mapKey", "mapValue") && x.values.forall(_.isInstanceOf[String]) =>
            Some(x("mapKey").asInstanceOf[String] -> x("mapValue").asInstanceOf[String])
          case _                                                                                                                       => None
        }

      case None => Seq()
    }
  } else Seq()
}

class DhallImportResolutionSuite extends DhallTest with OverrideEnvironment with ResourceFiles {

  def setupEnvironment[R](code: => R): R = {
    val tempDir     = os.temp.dir(root / "tmp", deleteOnExit = true)
    val oldUserHome = System.getProperty("user.home")
    try {
      os.copy(from = os.Path(resourceAsFile("dhall-lang/tests/import/cache").get.getAbsolutePath), to = tempDir, replaceExisting = true)
      val dhallHome = resourceAsFile("dhall-lang/tests/import/home").get.getAbsolutePath
      System.setProperty("user.home", dhallHome)
      val envVars   = Seq("DHALL_TEST_VAR" -> "6 * 7", "XDG_CACHE_HOME" -> tempDir.toNIO.toAbsolutePath.toString, "HOME" -> dhallHome)
      runInFakeEnvironmentWith(envVars: _*)(code)
    } finally {
      os.remove.all(tempDir)
      System.setProperty("user.home", oldUserHome)
    }
  }

  test("import resolution success") {
    val parentPath = resourceAsFile("dhall-lang").get.toPath.getParent
    setupEnvironment {
      val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/import/success", Some("A.dhall")).map { file =>
        val relativePathForTest                 = parentPath.relativize(file.toPath)
        val envVarsFile                         = new File(file.getAbsolutePath.replace("A.dhall", "ENV.dhall"))
        val extraEnvVars: Seq[(String, String)] = DhallImportResolutionSuite.readHeadersFromEnv(envVarsFile)
        val validationFile                      = new File(file.getAbsolutePath.replace("A.dhall", "B.dhall"))
        // if (envVarsFile.exists) println(s"DEBUG: env vars for file ${file.toPath} are $extraEnvVars")
        runInFakeEnvironmentWith(extraEnvVars: _*) {
          val result = Try {
            val Parsed.Success(DhallFile(_, ourResult), _)        = Parser.parseDhallStream(new FileInputStream(file))
            val Parsed.Success(DhallFile(_, validationResult), _) = Parser.parseDhallStream(new FileInputStream(validationFile))
            val x                                                 = ourResult.resolveImports(
              relativePathForTest
            ) // Here we must avoid beta-normalizing entire expressions. Only the import contents must be normalized.
            val y = validationResult.resolveImports(relativePathForTest)

            if (x.toDhall != y.toDhall)
              println(
                s"DEBUG: ${file.getName}: The Dhall texts differ. Our parser gives:\n${ourResult.toDhall}\n\t\tafter beta-normalization:\n${x.toDhall}\n\t\texpected correct answer:\n${y.toDhall}\n"
              )
            else if (x != y)
              println(
                s"DEBUG: ${file.getName}: The expressions differ. Our parser gives:\n${ourResult.toDhall}\n\t\tafter beta-normalization:\n${x.toDhall}\n\t\tDhall texts are equal but expressions differ: our normalized expression is:\n$x\n\t\tThe expected correct expression is:\n$y\n"
              )

            expect(x.toDhall == y.toDhall && x == y)
            file.getName
          }
          if (result.isFailure)
            println(s"${file.getName}: ${result.failed.get}\n${printThrowable(result.failed.get)}")
          result
        }
      }
      TestUtils.requireSuccessAtLeast(72, results)
    }
  }

  test("import resolution failure") {
    setupEnvironment {
      val parentPath                = resourceAsFile("dhall-lang").get.toPath.getParent
      val results: Seq[Try[String]] = enumerateResourceFiles("dhall-lang/tests/import/failure", Some(".dhall"))
        .filterNot(_.getAbsolutePath endsWith "ENV.dhall") // Otherwise we cannot distinguish between test files and their ENV files.
        .map { file =>
          val relativePathForTest                 = parentPath.relativize(file.toPath)
          val envVarsFile                         = new File(file.getAbsolutePath.replace(".dhall", "ENV.dhall"))
          val extraEnvVars: Seq[(String, String)] = DhallImportResolutionSuite.readHeadersFromEnv(envVarsFile)
          // if (envVarsFile.exists) println(s"DEBUG: env vars for file ${file.toPath} are $extraEnvVars")
          runInFakeEnvironmentWith(extraEnvVars: _*) {
            val result = Try {
              val Parsed.Success(DhallFile(_, ourResult), _) = Parser.parseDhallStream(new FileInputStream(file))
              val x                                          = Try(ourResult.resolveImports(relativePathForTest))
              expect(x.isFailure)
              file.getName
            }
            if (result.isFailure) println(s"${file.getName}: ${result.failed.get.getMessage}")
            result
          }
        }

      TestUtils.requireSuccessAtLeast(24, results)
    }
  }

}
