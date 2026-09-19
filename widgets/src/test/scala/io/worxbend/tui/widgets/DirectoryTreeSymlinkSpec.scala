package io.worxbend.tui.widgets

import java.nio.file.Files

import org.scalatest.funsuite.AnyFunSuite

/** Pins the symlink-containment contract [[DirectoryTreeState]]'s own Scaladoc now states explicitly: a symbolic link
  * inside the tree is followed even when its target resolves outside `root`. This is not asserted here because it is
  * desirable — see the Scaladoc note on `DirectoryTreeState` — but because a change to it (in either direction) should
  * be a deliberate, documented decision and not a silent behaviour drift this suite fails to notice.
  */
final class DirectoryTreeSymlinkSpec extends AnyFunSuite:

  test("expanding a symlink whose target is outside root reaches paths outside root"):
    val outside = Files.createTempDirectory("glyphora-outside")
    Files.writeString(outside.resolve("secret.txt"), "s")
    val root    = Files.createTempDirectory("glyphora-root")
    Files.writeString(root.resolve("inside.txt"), "i")
    Files.createSymbolicLink(root.resolve("escape"), outside)

    val state = DirectoryTreeState(root)
    val link  = state.visiblePaths().find(_.getFileName.toString == "escape").get
    state.selected = Some(link)
    state.toggle()

    val leaked = state.visiblePaths().filter(_.toRealPath().startsWith(outside.toRealPath()))
    assert(
      leaked.nonEmpty,
      "expected the symlink's target to be reachable — if this now fails, containment was added and " +
        "DirectoryTreeState's Scaladoc note above should be updated to match",
    )
