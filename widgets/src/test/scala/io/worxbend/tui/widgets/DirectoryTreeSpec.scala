package io.worxbend.tui.widgets

import io.worxbend.tui.core.Modifiers
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

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

  private val tree = DirectoryTree()

  test("the collapsed root shows directories first, then files"):
    val state = DirectoryTreeState(fixture())
    assert(trimmedLines(rendered(tree, state, 25, 6)).take(2) == Seq("▸ src/", "  README.md"))

  test("expanding a directory reveals its entries indented"):
    val state = DirectoryTreeState(fixture())
    state.selectNext() // src/
    state.toggle()
    val lines = trimmedLines(rendered(tree, state, 25, 6))
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
    val buffer = rendered(tree, state, 25, 6)
    assert(buffer.get(0, 0).style.modifiers.hasAny(Modifiers.Reverse))

  test("listings are cached until invalidated"):
    val root  = fixture()
    val state = DirectoryTreeState(root)
    assert(state.childrenOf(root).size == 2)
    Files.writeString(root.resolve("new.txt"), "")
    assert(state.childrenOf(root).size == 2) // cached
    state.invalidate()
    assert(state.childrenOf(root).size == 3)

  test("visiblePaths walks the cached tree without re-reading the filesystem"):
    val root  = fixture()
    val state = DirectoryTreeState(root)
    state.selectNext() // src/
    state.toggle()
    state.selectNext() // src/util/
    state.toggle()
    val before = state.visiblePaths()
    assert(before.map(_.getFileName.toString) == Vector("src", "util", "Io.scala", "Main.scala", "README.md"))
    Files.delete(root.resolve("src/util/Io.scala"))
    Files.delete(root.resolve("src/util"))
    assert(state.visiblePaths() == before) // nothing re-stat-ed: the directory flag came from the cached listing
    state.invalidate()
    assert(state.visiblePaths().map(_.getFileName.toString) == Vector("src", "Main.scala", "README.md"))

  test("rowText renders from the cached flag, not a fresh stat"):
    val root  = fixture()
    val state = DirectoryTreeState(root)
    state.selectNext() // src/
    state.toggle()
    val before = trimmedLines(rendered(tree, state, 25, 6))
    assert(before.take(4) == Seq("▾ src/", "  ▸ util/", "    Main.scala", "  README.md"))
    Files.delete(root.resolve("src/util/Io.scala"))
    Files.delete(root.resolve("src/util"))
    Files.writeString(root.resolve("src/util"), "") // same name, now a plain file
    assert(trimmedLines(rendered(tree, state, 25, 6)) == before)
    state.invalidate()
    val after = trimmedLines(rendered(tree, state, 25, 6))
    assert(after.take(4) == Seq("▾ src/", "    Main.scala", "    util", "  README.md"))

  test("invalidate(Some(directory)) re-reads that branch's directory flags"):
    // Guards the parallel-Map[Path, Boolean] design, which would leave `util -> true` behind and keep rendering "▸".
    val root  = fixture()
    val state = DirectoryTreeState(root)
    val src   = root.resolve("src")
    state.expanded += src
    assert(trimmedLines(rendered(tree, state, 25, 6)).take(2) == Seq("▾ src/", "  ▸ util/"))
    Files.delete(root.resolve("src/util/Io.scala"))
    Files.delete(src.resolve("util"))
    Files.writeString(src.resolve("util"), "")
    state.invalidate(Some(src))
    assert(trimmedLines(rendered(tree, state, 25, 6)).take(3) == Seq("▾ src/", "    Main.scala", "    util"))

  test("toggle works on a path the caller assigned directly"):
    val root  = fixture()
    val state = DirectoryTreeState(root)
    state.selected = Some(root.resolve("src"))
    state.toggle()
    assert(state.expanded.contains(root.resolve("src")))
    state.selected = Some(root.resolve("README.md"))
    state.toggle()
    assert(!state.expanded.contains(root.resolve("README.md")))
    assert(state.expanded.size == 1)

  test("an unreadable directory renders as empty instead of crashing"):
    val state = DirectoryTreeState(Path.of("/nonexistent-glyphora-path"))
    assert(state.visiblePaths().isEmpty)
    assert(trimmedLines(rendered(tree, state, 25, 6)).forall(_.isEmpty))
