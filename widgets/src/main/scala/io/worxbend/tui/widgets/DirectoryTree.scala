package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, StatefulWidget, Style}

import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Caller-owned [[DirectoryTree]] state rooted at a directory.
  *
  * Directory listings are loaded lazily on first visibility and cached — the filesystem is only touched when
  * a branch expands (or after [[invalidate]]), never per frame. Unreadable directories degrade to empty.
  */
final class DirectoryTreeState(val root: Path):
  var selected: Option[Path] = None
  var offset: Int = 0
  val expanded: mutable.Set[Path] = mutable.Set.empty
  private val childrenCache = mutable.Map[Path, Vector[Path]]()

  /** Sorted entries of `directory` (directories first, then files, alphabetical), cached after the first read. */
  def childrenOf(directory: Path): Vector[Path] =
    childrenCache.getOrElseUpdate(directory, listDirectory(directory))

  /** Drops the cached listing for `directory` (or everything, when `None`) so the next render re-reads it. */
  def invalidate(directory: Option[Path] = None): Unit =
    directory match
      case Some(path) => childrenCache.remove(path)
      case None       => childrenCache.clear()

  def selectNext(): Unit = moveSelection(+1)

  def selectPrevious(): Unit = moveSelection(-1)

  /** Expands/collapses the selected directory; selecting a file is a no-op. */
  def toggle(): Unit =
    selected.filter(Files.isDirectory(_)).foreach { path =>
      if expanded.contains(path) then expanded -= path else expanded += path
    }

  /** All paths currently visible, depth-first: children of expanded directories only. */
  def visiblePaths(): Vector[Path] =
    def walk(directory: Path): Vector[Path] =
      childrenOf(directory).flatMap { child =>
        if Files.isDirectory(child) && expanded.contains(child) then child +: walk(child)
        else Vector(child)
      }
    walk(root)

  private def moveSelection(delta: Int): Unit =
    val visible = visiblePaths()
    if visible.nonEmpty then
      val noSelectionStart = if delta > 0 then -1 else 1
      val currentIndex = selected.map(visible.indexOf).filter(_ >= 0).getOrElse(noSelectionStart)
      val nextIndex = math.max(0, math.min(currentIndex + delta, visible.size - 1))
      selected = Some(visible(nextIndex))

  private def listDirectory(directory: Path): Vector[Path] =
    try
      val entries = Files.list(directory)
      try
        entries
          .iterator()
          .asScala
          .toVector
          .sortBy(path => (!Files.isDirectory(path), path.getFileName.toString.toLowerCase))
      finally entries.close()
    catch case NonFatal(_) => Vector.empty // unreadable directory: show as empty rather than crash the UI

/** A filesystem browser (the Tier 5 filesystem-aware [[Tree]]): lazy-loaded directory listings with
  * expand/collapse markers, `/`-suffixed directory names, selection highlight, and scroll-to-selection.
  */
final case class DirectoryTree(
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
) extends StatefulWidget[DirectoryTreeState]:

  def render(area: Rect, buffer: Buffer, state: DirectoryTreeState): Unit =
    if !area.isEmpty then
      val visible = state.visiblePaths()
      val selectedIndex = state.selected.map(visible.indexOf).filter(_ >= 0)
      state.offset = scrolledOffset(state.offset, selectedIndex, visible.size, area.height)
      visible.slice(state.offset, state.offset + area.height).zipWithIndex.foreach { (path, row) =>
        val rowStyle = if state.selected.contains(path) then style.patch(highlightStyle) else style
        val text = CharWidth.substringByWidth(rowText(path, state), area.width)
        buffer.setString(area.x, area.y + row, text, rowStyle)
      }

  private def rowText(path: Path, state: DirectoryTreeState): String =
    val depth = path.getNameCount - state.root.getNameCount - 1
    val indent = "  ".repeat(math.max(0, depth))
    val name = path.getFileName.toString
    if Files.isDirectory(path) then
      val marker = if state.expanded.contains(path) then "▾ " else "▸ "
      s"$indent$marker$name/"
    else s"$indent  $name"

  private def scrolledOffset(offset: Int, selectedIndex: Option[Int], total: Int, height: Int): Int =
    val maxOffset = math.max(0, total - height)
    val clamped = math.max(0, math.min(offset, maxOffset))
    selectedIndex match
      case None => clamped
      case Some(index) =>
        if index < clamped then index
        else if index >= clamped + height then index - height + 1
        else clamped
