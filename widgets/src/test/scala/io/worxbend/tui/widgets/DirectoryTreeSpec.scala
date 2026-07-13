package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Modifiers, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.{Files, Path}

final class DirectoryTreeSpec extends AnyFunSuite:

  /** Fixture: root/{src/{Main.scala, util/Io.scala}, README.md} in a temp directory. */
  private def fixture(): Path =
    val root = Files.createTempDirectory("glyphora-dtree")
    Files.createDirectories(root.resolve("src/util"))
    Files.writeString(root.resolve("src/Main.scala"), "")
    Files.writeString(root.resolve("src/util/Io.scala"), "")
    Files.writeString(root.resolve("README.md"), "")
    root.toFile.deleteOnExit()
    root

  private def renderedWith(state: DirectoryTreeState, height: Int = 6): Buffer =
    val buffer = Buffer(Rect(0, 0, 25, height))
    DirectoryTree().render(buffer.area, buffer, state)
    buffer

  test("the collapsed root shows directories first, then files"):
    val state = DirectoryTreeState(fixture())
    assert(trimmedLines(renderedWith(state)).take(2) == Seq("▸ src/", "  README.md"))

  test("expanding a directory reveals its entries indented"):
    val state = DirectoryTreeState(fixture())
    state.selectNext() // src/
    state.toggle()
    val lines = trimmedLines(renderedWith(state))
    assert(lines.take(4) == Seq("▾ src/", "  ▸ util/", "    Main.scala", "  README.md"))

  test("selection walks visible entries and toggling a file is a no-op"):
    val state = DirectoryTreeState(fixture())
    state.selectNext()
    state.selectNext() // README.md
    assert(state.selected.exists(_.getFileName.toString == "README.md"))
    state.toggle()
    assert(state.expanded.isEmpty)

  test("the selected row is highlighted"):
    val state  = DirectoryTreeState(fixture())
    state.selectNext()
    val buffer = renderedWith(state)
    assert(buffer.get(0, 0).style.modifiers.has(Modifiers.Reverse))

  test("listings are cached until invalidated"):
    val root  = fixture()
    val state = DirectoryTreeState(root)
    assert(state.childrenOf(root).size == 2)
    Files.writeString(root.resolve("new.txt"), "")
    assert(state.childrenOf(root).size == 2) // cached
    state.invalidate()
    assert(state.childrenOf(root).size == 3)

  test("an unreadable directory renders as empty instead of crashing"):
    val state = DirectoryTreeState(Path.of("/nonexistent-glyphora-path"))
    assert(state.visiblePaths().isEmpty)
    assert(trimmedLines(renderedWith(state)).forall(_.isEmpty))
