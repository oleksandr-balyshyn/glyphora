package io.worxbend.tui.widgets

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.{Files, Path}

final class ScratchSymlinkSpec extends AnyFunSuite:
  test("symlink escapes root"):
    val outside = Files.createTempDirectory("outside")
    Files.writeString(outside.resolve("secret.txt"), "s")
    val root = Files.createTempDirectory("root")
    Files.writeString(root.resolve("inside.txt"), "i")
    Files.createSymbolicLink(root.resolve("escape"), outside)

    val state = DirectoryTreeState(root)
    val top   = state.visiblePaths()
    info(s"top: ${top.mkString(", ")}")
    val link = top.find(_.getFileName.toString == "escape").get
    state.selected = Some(link)
    state.toggle()
    val after = state.visiblePaths()
    info(s"after expand: ${after.mkString(", ")}")
    val leaked = after.filter(p => !p.toAbsolutePath.normalize.startsWith(root.toAbsolutePath.normalize) || {
      val real = p.toRealPath(); !real.startsWith(root.toRealPath())
    })
    info(s"paths whose real target is outside root: ${leaked.mkString(", ")}")
    assert(leaked.nonEmpty)
