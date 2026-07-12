package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, StatefulWidget, Style}

import scala.collection.mutable

/** A node of a [[Tree]]: a label plus child nodes. */
final case class TreeNode(label: String, children: Seq[TreeNode] = Seq.empty)

/** Caller-owned tree state: which branch paths are expanded, the selected path, and the scroll offset.
  * Paths address nodes by child index at each level (`Seq(1, 0)` = second root's first child).
  */
final class TreeState:
  val expanded: mutable.Set[Seq[Int]] = mutable.Set.empty
  var selected: Option[Seq[Int]] = None
  var offset: Int = 0

  def selectNext(nodes: Seq[TreeNode]): Unit =
    moveSelection(nodes, +1)

  def selectPrevious(nodes: Seq[TreeNode]): Unit =
    moveSelection(nodes, -1)

  /** Expands a collapsed selected branch, collapses an expanded one; leaves are untouched. */
  def toggle(nodes: Seq[TreeNode]): Unit =
    selected.foreach { path =>
      Tree.nodeAt(nodes, path).foreach { node =>
        if node.children.nonEmpty then if expanded.contains(path) then expanded -= path else expanded += path
      }
    }

  private def moveSelection(nodes: Seq[TreeNode], delta: Int): Unit =
    val visible = Tree.visiblePaths(nodes, expanded.toSet)
    if visible.nonEmpty then
      val noSelectionStart = if delta > 0 then -1 else 1 // so the first move in either direction lands on index 0
      val currentIndex = selected.map(path => visible.indexOf(path)).filter(_ >= 0).getOrElse(noSelectionStart)
      val nextIndex = math.max(0, math.min(currentIndex + delta, visible.size - 1))
      selected = Some(visible(nextIndex))

/** A collapsible tree with keyboard-driven selection: branch markers (`▸` collapsed, `▾` expanded), two-column
  * indentation per depth, and ListView-style scroll-to-selection.
  */
final case class Tree(
    nodes: Seq[TreeNode],
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
) extends StatefulWidget[TreeState]:

  def render(area: Rect, buffer: Buffer, state: TreeState): Unit =
    if !area.isEmpty && nodes.nonEmpty then
      val visible = Tree.visiblePaths(nodes, state.expanded.toSet)
      val selectedIndex = state.selected.map(visible.indexOf).filter(_ >= 0)
      state.offset = scrolledOffset(state.offset, selectedIndex, visible.size, area.height)
      visible.slice(state.offset, state.offset + area.height).zipWithIndex.foreach { (path, row) =>
        val node = Tree.nodeAt(nodes, path).getOrElse(TreeNode(""))
        val isSelected = state.selected.contains(path)
        val rowStyle = if isSelected then style.patch(highlightStyle) else style
        val marker =
          if node.children.isEmpty then "  "
          else if state.expanded.contains(path) then "▾ "
          else "▸ "
        val indent = "  ".repeat(path.size - 1)
        val text = CharWidth.substringByWidth(indent + marker + node.label, area.width)
        buffer.setString(area.x, area.y + row, text, rowStyle)
      }

  private def scrolledOffset(offset: Int, selectedIndex: Option[Int], total: Int, height: Int): Int =
    val maxOffset = math.max(0, total - height)
    val clamped = math.max(0, math.min(offset, maxOffset))
    selectedIndex match
      case None => clamped
      case Some(index) =>
        if index < clamped then index
        else if index >= clamped + height then index - height + 1
        else clamped

object Tree:

  /** Depth-first paths of every node whose ancestors are all expanded. */
  def visiblePaths(nodes: Seq[TreeNode], expanded: Set[Seq[Int]]): Seq[Seq[Int]] =
    def walk(siblings: Seq[TreeNode], prefix: Seq[Int]): Seq[Seq[Int]] =
      siblings.zipWithIndex.flatMap { (node, index) =>
        val path = prefix :+ index
        val below = if expanded.contains(path) then walk(node.children, path) else Seq.empty
        path +: below
      }
    walk(nodes, Seq.empty)

  def nodeAt(nodes: Seq[TreeNode], path: Seq[Int]): Option[TreeNode] =
    path match
      case Seq() => None
      case Seq(head, tail*) =>
        nodes.lift(head).flatMap { node =>
          if tail.isEmpty then Some(node) else nodeAt(node.children, tail)
        }
