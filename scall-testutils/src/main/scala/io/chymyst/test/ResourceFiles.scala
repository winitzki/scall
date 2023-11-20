package io.chymyst.test

import java.io.File
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
}
