package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, StatefulWidget, Style}

import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Caller-owned [[DirectoryTree]] state rooted at a directory.
  *
  * Directory listings are loaded lazily on first visibility and cached together with each entry's directory flag — the
  * filesystem is only touched when a branch is first listed (or after [[invalidate]]), never while rendering or moving
  * the selection. Unreadable directories degrade to empty.
  */
final class DirectoryTreeState(val root: Path):
  var selected: Option[Path]      = None
  var offset: Int                 = 0
  val expanded: mutable.Set[Path] = mutable.Set.empty
  private val childrenCache       = mutable.Map[Path, Vector[(Path, Boolean)]]()

  /** Sorted entries of `directory` (directories first, then files, alphabetical), cached after the first read. */
  def childrenOf(directory: Path): Vector[Path] =
    cachedEntries(directory).map(_._1)

  /** Cached `(entry, isDirectory)` pairs for `directory`, listing it on first use. Internal callers use this rather
    * than [[childrenOf]] so the per-frame walk allocates no projected vector.
    */
  private def cachedEntries(directory: Path): Vector[(Path, Boolean)] =
    childrenCache.getOrElseUpdate(directory, listDirectory(directory))

  /** Cached directory flag for `path`, read from its parent's listing (which is listed if not cached yet). */
  private def isDirectory(path: Path): Boolean =
    if path == root then true // the tree's root is a directory by construction
    else Option(path.getParent).exists(parent => cachedEntries(parent).exists((child, flag) => child == path && flag))

  /** Drops the cached listing for `directory` (or everything, when `None`) so the next render re-reads it. */
  def invalidate(directory: Option[Path] = None): Unit =
    directory match
      case Some(path) => childrenCache.remove(path)
      case None       => childrenCache.clear()

  def selectNext(): Unit = moveSelection(+1)

  def selectPrevious(): Unit = moveSelection(-1)

  /** Expands/collapses the selected directory; selecting a file is a no-op. */
  def toggle(): Unit =
    selected.filter(isDirectory).foreach { path =>
      if expanded.contains(path) then expanded -= path else expanded += path
    }

  /** All paths currently visible, depth-first: children of expanded directories only. */
  def visiblePaths(): Vector[Path] = visibleEntries().map(_._1)

  /** [[visiblePaths]] paired with each entry's cached directory flag, so rendering needs no second lookup. */
  private[widgets] def visibleEntries(): Vector[(Path, Boolean)] =
    def walk(directory: Path): Vector[(Path, Boolean)] =
      cachedEntries(directory).flatMap { entry =>
        val (child, isDir) = entry
        if expanded.contains(child) && isDir then entry +: walk(child)
        else Vector(entry)
      }
    walk(root)

  private def moveSelection(delta: Int): Unit =
    val visible = visiblePaths()
    if visible.nonEmpty then
      val noSelectionStart = if delta > 0 then -1 else 1
      val currentIndex     = selected.map(visible.indexOf).filter(_ >= 0).getOrElse(noSelectionStart)
      val nextIndex        = math.max(0, math.min(currentIndex + delta, visible.size - 1))
      selected = Some(visible(nextIndex))

  private def listDirectory(directory: Path): Vector[(Path, Boolean)] =
    try
      val entries = Files.list(directory)
      try
        entries
          .iterator()
          .asScala
          .map(path => (path, Files.isDirectory(path)))
          .toVector
          .sortBy((path, isDir) => (!isDir, path.getFileName.toString.toLowerCase))
      finally entries.close()
    catch case NonFatal(_) => Vector.empty // unreadable directory: show as empty rather than crash the UI

/** A filesystem browser (the Tier 5 filesystem-aware [[Tree]]): lazy-loaded directory listings with expand/collapse
  * markers, `/`-suffixed directory names, selection highlight, and scroll-to-selection.
  */
final case class DirectoryTree(
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
) extends StatefulWidget[DirectoryTreeState]:

  def render(area: Rect, buffer: Buffer, state: DirectoryTreeState): Unit =
    if !area.isEmpty then
      val visible       = state.visibleEntries()
      val selectedIndex = state.selected.map(path => visible.indexWhere(_._1 == path)).filter(_ >= 0)
      state.offset = ScrollWindow.offsetFor(state.offset, selectedIndex, visible.size, area.height)
      val rows          = visible.slice(state.offset, state.offset + area.height)
      rows.zipWithIndex.foreach { case ((path, isDir), row) =>
        val rowStyle = if state.selected.contains(path) then style.patch(highlightStyle) else style
        val text     = CharWidth.substringByWidth(rowText(path, isDir, state), area.width)
        buffer.setString(area.x, area.y + row, text, rowStyle)
      }

  private def rowText(path: Path, isDirectory: Boolean, state: DirectoryTreeState): String =
    val depth  = path.getNameCount - state.root.getNameCount - 1
    val indent = "  ".repeat(math.max(0, depth))
    val name   = path.getFileName.toString
    if isDirectory then
      val marker = if state.expanded.contains(path) then "▾ " else "▸ "
      s"$indent$marker$name/"
    else s"$indent  $name"
