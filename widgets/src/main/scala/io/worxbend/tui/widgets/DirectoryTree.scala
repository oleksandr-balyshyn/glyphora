package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, StatefulWidget, Style}

import java.nio.file.{Files, Path}
import java.util.Locale
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Caller-owned [[DirectoryTree]] state rooted at a directory.
  *
  * Directory listings are loaded lazily on first visibility and cached together with each entry's directory flag, so a
  * directory is read from disk once and then served from memory until [[invalidate]] drops it.
  *
  * WHERE THAT READ HAPPENS IS WORTH KNOWING. "On first visibility" means the first frame that makes a branch visible
  * does the listing, and that frame runs on the render thread: [[DirectoryTree.render]] calls [[visibleEntries]], and
  * [[selectNext]] / [[selectPrevious]] call [[visiblePaths]], both of which walk into a blocking `Files.list` for any
  * branch not cached yet. On a local disk that is not measurable; on a network mount, a fuse filesystem, or a directory
  * with very many entries, it stalls the render loop for as long as the listing takes. A caller that expects such a
  * filesystem should pre-warm the cache by calling [[childrenOf]] from a background thread before expanding the branch,
  * so the render-thread call finds the entries already there.
  *
  * Unreadable directories degrade to empty rather than raising, so a permission-denied folder shows as a leaf.
  *
  * Symbolic links are followed, including ones that resolve outside [[root]] — `Files.list`/`Files.isDirectory`
  * traverse them like any other directory entry, and nothing here checks a resolved target against `root`. That is the
  * same containment gap `java.nio.file.Files.walk`/`list` themselves have (they are not sandboxes either), so a caller
  * browsing a directory it does not fully trust — an upload folder, an extracted archive, anything not laid out by this
  * application — is responsible for containment itself, for instance by resolving `path.toRealPath()` before rendering
  * it and refusing to expand one that escapes `root`. A future `followSymlinks` toggle could make refusal the default
  * instead of every caller's job; until then this is deliberately spelled out rather than left for a caller to discover
  * from behaviour.
  *
  * The one thing following links cannot be allowed to do is recurse forever: [[visibleEntries]] resolves each expanded
  * directory's real path and skips one that is already an ancestor of the walk, so a symlink loop — a link back to the
  * root, a parent, or itself — renders as an expanded entry with no children instead of overflowing the render thread's
  * stack. Two sibling links to the same target both still expand: only the current walk's chain is compared, not every
  * directory visited so far.
  *
  * Render-thread-only, and mutating it does not by itself schedule a frame. This is a plain mutable object, invisible
  * to the reactive layer: a background result written straight into it stays off screen until something unrelated
  * happens to repaint. Pair the mutation with a `Signal` write, or call `TuiApp.requestRedraw()` from the same
  * render-thread callback that made it.
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
    // `ancestors` holds the real paths of the directories the walk is currently inside, so a link — to the root, to a
    // parent, or to itself — that would recurse back onto that chain is drawn as an expanded entry with no children
    // rather than followed again: the listing below it would be the one already on the stack, and following it means
    // a symlink loop recurses until the render thread overflows its stack.
    def walk(directory: Path, ancestors: Set[Path]): Vector[(Path, Boolean)] =
      cachedEntries(directory).flatMap { entry =>
        val (child, isDir) = entry
        if expanded.contains(child) && isDir then
          val target = realPathOf(child)
          if ancestors.contains(target) then Vector(entry)
          else entry +: walk(child, ancestors + target)
        else Vector(entry)
      }
    walk(root, Set(realPathOf(root)))

  /** `path` with every link resolved — the identity two routes to the same directory share. A dangling link resolves to
    * nothing, and rather than drop it (its listing fails to empty anyway) it falls back to the unresolved path, which
    * no real directory on the chain can equal.
    */
  private def realPathOf(path: Path): Path =
    try path.toRealPath()
    catch case NonFatal(_) => path

  private def moveSelection(delta: Int): Unit =
    val visible = visiblePaths()
    if visible.nonEmpty then selected = Selection.moveWithin(visible, selected, delta)

  private def listDirectory(directory: Path): Vector[(Path, Boolean)] =
    try
      val entries = Files.list(directory)
      try
        entries
          .iterator()
          .asScala
          .map(path => (path, Files.isDirectory(path)))
          .toVector
          // ROOT, not the default locale: see core.KeyEvent.keyCodeFor for why matching/sorting a lowercased
          // user-visible string must not depend on the platform's default locale.
          .sortBy((path, isDir) => (!isDir, path.getFileName.toString.toLowerCase(Locale.ROOT)))
      finally entries.close()
    catch case NonFatal(_) => Vector.empty // unreadable directory: show as empty rather than crash the UI

/** A filesystem browser — [[Tree]] with the filesystem as its node source: lazy-loaded directory listings with
  * expand/collapse markers, `/`-suffixed directory names, selection highlight, and scroll-to-selection.
  */
final case class DirectoryTree(
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
) extends StatefulWidget[DirectoryTreeState]:

  def render(area: Rect, buffer: Buffer, state: DirectoryTreeState): Unit =
    if !area.isEmpty then
      val visible       = state.visibleEntries()
      val selectedIndex = state.selected.map(path => visible.indexWhere(_._1 == path)).filter(_ >= 0)
      state.offset = TreeRows.render(
        area,
        buffer,
        visible,
        state.offset,
        selectedIndex,
        (path, _) => state.selected.contains(path),
        (path, isDir) => rowText(path, isDir, state),
        style,
        highlightStyle,
      )

  private def rowText(path: Path, isDirectory: Boolean, state: DirectoryTreeState): String =
    val depth  = path.getNameCount - state.root.getNameCount - 1
    val indent = "  ".repeat(math.max(0, depth))
    val name   = path.getFileName.toString
    if isDirectory then
      val marker = if state.expanded.contains(path) then "▾ " else "▸ "
      s"$indent$marker$name/"
    else s"$indent  $name"
