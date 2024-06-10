package io.chymyst.dhall.codec

import io.chymyst.dhall.Parser
import io.chymyst.dhall.Parser.StringAsDhallExpression
import io.chymyst.dhall.Semantics.computeHash
import io.chymyst.dhall.Syntax.Expression
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.{ConfigurationBuilder, FilterBuilder}

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.SetHasAsScala
import scala.reflect.runtime.universe
import scala.util.{Failure, Success, Try}

trait DhallShim {
  def hash: String

  def dhallSource: String

  def dhallExpression: Expression

  def verifyHash: Boolean = {
    hash == computeHash(dhallExpression.resolveImports(Paths.get(".")).alphaNormalized.betaNormalized.toCBORmodel.encodeCbor2)
  }

  def verifyDhallSource: Boolean = {
    hash == computeHash(dhallSource.dhall.resolveImports(Paths.get(".")).alphaNormalized.betaNormalized.toCBORmodel.encodeCbor2)
  }
}

object DhallShim {
  def findAll: Map[String, DhallShim] = {
    val reflections = new Reflections(
      new ConfigurationBuilder().setScanners(Scanners.SubTypes).forPackage("io.chymyst").filterInputsBy(new FilterBuilder().includePackage("io.chymyst"))
    )
    val classes     =
      reflections.getSubTypesOf(classOf[DhallShim]).asScala.filter(c => c.getName.endsWith("$")) // Select only Scala `object` classes.

    // map classes to actual object instances
    classes.map { clazz =>
      val runtimeMirror = universe.runtimeMirror(clazz.getClassLoader)
      val module        = runtimeMirror.staticModule(clazz.getName)
      val shim          = runtimeMirror.reflectModule(module).instance.asInstanceOf[DhallShim]
      (shim.hash, shim)
    }.toMap
  }

  def main(args: Array[String]) = {
    args.foreach { dhallSourceFile =>
      val path = Paths.get(dhallSourceFile)
      val name = path.getName(path.getNameCount - 1).toString.replace(".dhall", "")
      Try {
        val expr      = Parser.parseDhallBytes(Files.readAllBytes(path)).get.value.value.resolveImports(path).alphaNormalized.typeCheckAndBetaNormalize().unsafeGet
        val hash      = computeHash(expr.toCBORmodel.encodeCbor2)
        val scalaCode = ToScala.print("scala", name, hash, expr)
        val output    = Paths.get(name + ".scala")
        Files.write(output, scalaCode.getBytes("UTF-8"))
      } match {
        case Failure(exception) => println(s"Failure for source '$dhallSourceFile': $exception")
        case Success(value)     =>
      }
    }
  }

}
