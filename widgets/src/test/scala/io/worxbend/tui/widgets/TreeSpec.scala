package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect}
import io.worxbend.tui.testsupport.BufferAssertions.trimmedLines

import org.scalatest.funsuite.AnyFunSuite

final class TreeSpec extends AnyFunSuite:

  private val nodes = Seq(
    TreeNode("src", Seq(TreeNode("main.scala"), TreeNode("util", Seq(TreeNode("io.scala"))))),
    TreeNode("README.md"),
  )

  private def renderedWith(state: TreeState, height: Int = 5): Buffer =
    val buffer = Buffer(Rect(0, 0, 20, height))
    Tree(nodes).render(buffer.area, buffer, state)
    buffer

  test("collapsed branches show only their roots"):
    val buffer = renderedWith(TreeState())
    assert(trimmedLines(buffer).take(2) == Seq("▸ src", "  README.md"))

  test("expanding a branch reveals children with indentation"):
    val state  = TreeState()
    state.expanded += Seq(0)
    val buffer = renderedWith(state)
    assert(trimmedLines(buffer).take(4) == Seq("▾ src", "    main.scala", "  ▸ util", "  README.md"))

  test("selection navigates visible nodes in order"):
    val state = TreeState()
    state.expanded += Seq(0)
    state.selectNext(nodes)
    assert(state.selected.contains(Seq(0)))
    state.selectNext(nodes)
    assert(state.selected.contains(Seq(0, 0)))
    state.selectNext(nodes)
    assert(state.selected.contains(Seq(0, 1)))
    state.selectPrevious(nodes)
    assert(state.selected.contains(Seq(0, 0)))

  test("toggle expands and collapses the selected branch"):
    val state = TreeState()
    state.selectNext(nodes)
    state.toggle(nodes)
    assert(state.expanded.contains(Seq(0)))
    state.toggle(nodes)
    assert(!state.expanded.contains(Seq(0)))

  test("toggle on a leaf is a no-op"):
    val state = TreeState()
    state.selected = Some(Seq(1))
    state.toggle(nodes)
    assert(state.expanded.isEmpty)

  test("the selected row is highlighted"):
    val state  = TreeState()
    state.selected = Some(Seq(1))
    val buffer = renderedWith(state)
    assert(buffer.get(0, 1).style.modifiers.has(io.worxbend.tui.core.Modifiers.Reverse))

  test("selection below the viewport scrolls the tree"):
    val state = TreeState()
    state.expanded += Seq(0)
    state.expanded += Seq(0, 1)
    state.selected = Some(Seq(1)) // last visible path
    val buffer = renderedWith(state, height = 2)
    assert(trimmedLines(buffer) == Seq("      io.scala", "  README.md"))
