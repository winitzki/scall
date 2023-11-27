package io.chymyst.test

import jnr.posix.POSIX

import java.io.File
import java.nio.file.Paths
import scala.util.Try
import scala.util.chaining.scalaUtilChainingOps

object ResourceFiles extends ResourceFiles

trait ResourceFiles {
  def resourceAsFile(resource: String): Option[File] = Try(new File(getClass.getResource("/" + resource).toURI)).toOption

  // Recursively enumerate all files (not directories) with names matching a given suffix.
  def enumerateResourceFiles(directory: String, filterBySuffix: Option[String] = None): Seq[File] = Try(
    enumerateFilesRecursively(getClass.getClassLoader.getResource(directory).getPath.pipe(new File(_)), filterBySuffix)
  ).toOption.toSeq.flatten

  def enumerateFilesRecursively(directory: File, filterBySuffix: Option[String] = None): Seq[File] = Try(Option(directory.listFiles).map(_.toSeq) match {
    case Some(files) =>
      (files.filter(_.isFile) ++
        files.filter(_.isDirectory).flatMap(d => enumerateFilesRecursively(d, filterBySuffix)))
        .filter(f => filterBySuffix.forall(suffix => f.getName.endsWith(suffix)))
        .sortBy(_.getName)

    case None => throw new Exception(s"File $directory is not a directory, cannot list.")
  }).toOption.toSeq.flatten

  // Note: `Paths#toAbsolutePath` and `File#getAbsolutePath` will always resolve with respect to the current working directory at the time JVM started and `user.dir` was first read.
  // Calls to `Paths#toAbsolutePath` and `File#getAbsolutePath` will not re-read `user.dir` each time.
  // So, `changeCurrentWorkingDirectory()` has no effect on relative files and paths via java.io and java.nio!
  // This may have an effect on other processes that depend on the current directory.
  def changeCurrentWorkingDirectory(directory: File): Boolean = {
    val nativePosix: POSIX = jnr.posix.POSIXFactory.getNativePOSIX()
    nativePosix.isNative && jnr.posix.JavaLibCHelper.chdir(directory.getAbsolutePath) == 0 && nativePosix.chdir(directory.getAbsolutePath) == 0
  }

  def getCurrentWorkingDirectory: File = {
    val nativePosix: POSIX = jnr.posix.POSIXFactory.getNativePOSIX()
    new File(nativePosix.getcwd())
  }
}
