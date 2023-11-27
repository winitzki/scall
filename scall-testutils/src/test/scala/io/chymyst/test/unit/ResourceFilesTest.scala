package io.chymyst.test.unit

import com.eed3si9n.expecty.Expecty.expect
import io.chymyst.test.ResourceFiles
import munit.FunSuite

import java.io.File
import java.nio.file.{Files, Paths}

class ResourceFilesTest extends FunSuite with ResourceFiles {
  test("get resource as file") {
    val file1  = resourceAsFile("testfile1.txt")
    val file2  = resourceAsFile("testdir")
    val file2a = resourceAsFile("testdir/")
    val file3  = resourceAsFile("testdir/testfile.txt")
    val file4  = resourceAsFile("nonexisting")
    val file5  = resourceAsFile("testdir/nonexisting")
    expect(Seq(file1, file2, file2a, file3).forall(_.nonEmpty))
    expect(Seq(file4, file5).forall(_.isEmpty))

    expect(file2.get.isDirectory)
    expect(file2a.get.isDirectory)
    expect(file2.get.listFiles.toList.map(_.getName) == List("testfile.txt"))
    expect(file2a.get.listFiles.toList.map(_.getName) == List("testfile.txt"))
  }

  test("enumerate resource files by pattern") {
    expect(enumerateResourceFiles("", Some(".txt")).map(_.getName) == Seq("testfile.txt", "testfile1.txt"))
    expect(enumerateResourceFiles("testdir").map(_.getName) == Seq("testfile.txt"))
    expect(enumerateResourceFiles("nonexisting").map(_.getName) == Seq())
  }

  test("change current working directory") {
    val cwd    = new File(".").toPath.toAbsolutePath
    val newCwd = cwd.getParent.getParent
    expect(ResourceFiles.changeCurrentWorkingDirectory(newCwd.toFile))
    expect(ResourceFiles.getCurrentWorkingDirectory.getAbsolutePath == newCwd.toAbsolutePath.toString)
  }
}
