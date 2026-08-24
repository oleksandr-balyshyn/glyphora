package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyCode, KeyEvent, Line, MouseEvent, MouseEventKind, Rect, Widget}
import io.worxbend.tui.widgets as w

import java.nio.file.Path

/** A scrollable single-selection list. Up/Down move the selection while focused, the wheel does the same on hover; the
  * widget scrolls to keep the selection visible. `state` is caller-owned, so the app can read or set the selection.
  */
final case class ListElement(
    items: Seq[String],
    state: w.ListState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = ListElement
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(wheelScrolls(() => state.selectPrevious(items.size), () => state.selectNext(items.size)))
  def widget: Widget =
    // no whole-body focus styling: the selection highlight is the focus cue for scrollable widgets
    val view = w.ListView(items, style = props.style, highlightStyle = props.focusStyle)
    (area, buffer) => view.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): ListElement               = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]     =
    Some(selectionKeys(() => state.selectNext(items.size), () => state.selectPrevious(items.size)))

/** A collapsible tree over an in-memory node list. Up/Down move the selection through the *visible* rows and Enter
  * expands or collapses the selected branch (a no-op on a leaf), all while focused; the wheel moves the selection on
  * hover, as it does over a `list`.
  */
final case class TreeElement(
    nodes: Seq[w.TreeNode],
    state: w.TreeState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = TreeElement
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(wheelScrolls(() => state.selectPrevious(nodes), () => state.selectNext(nodes)))
  def widget: Widget =
    // no whole-body focus styling: the selection highlight is the focus cue for scrollable widgets
    val tree = w.Tree(nodes, style = props.style, highlightStyle = props.focusStyle)
    (area, buffer) => tree.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): TreeElement               = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]     =
    Some(
      keys { case KeyEvent(KeyCode.Enter, _) => state.toggle(nodes) }
        .orElse(selectionKeys(() => state.selectNext(nodes), () => state.selectPrevious(nodes)))
    )

/** A vertical menu / dropdown / context menu popup. Up/Down move the highlight (skipping separators and disabled
  * items), Enter (or a click) fires `onSelect` with the chosen index, the wheel scrolls. Escape is left unconsumed so
  * an enclosing app can close it.
  */
final case class MenuElement(
    items: Seq[w.MenuEntry],
    state: w.MenuState,
    onSelect: Int => Unit,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = MenuElement
  def widget: Widget                                                         =
    val menu = w.Menu(items, style = props.style, highlightStyle = props.focusStyle)
    (area, buffer) => menu.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): MenuElement               = copy(props = props)
  private[dsl] override def claim: SizeClaim =
    // the popup asks for exactly the box it paints; `Fill` is the honest fallback if it ever stops knowing that
    val menu = w.Menu(items)
    (menu.widthAt(0), menu.heightAt(0)) match
      case (Some(width), Some(height)) => SizeClaim.box(width, height)
      case _                           => SizeClaim.Fill
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]     =
    Some(
      keys { case KeyEvent(KeyCode.Enter | KeyCode.Char(' '), _) =>
        state.selected.filter(index => items.lift(index).exists(_.selectable)).foreach(onSelect)
      }.orElse(selectionKeys(() => state.selectNext(items), () => state.selectPrevious(items)))
    )
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] = Some(handleMouse)

  private def handleMouse(event: MouseEvent, area: Rect): Boolean =
    event.kind match
      case MouseEventKind.ScrollUp   =>
        state.selectPrevious(items)
        true
      case MouseEventKind.ScrollDown =>
        state.selectNext(items)
        true
      case MouseEventKind.Down       =>
        val row = event.position.y - (area.y + 1) + state.offset // +1 skips the top border
        if row >= 0 && row < items.size && items(row).selectable then
          state.selected = Some(row)
          onSelect(row)
        true
      case _                         => false

/** A multi-select list: Up/Down move the cursor, Space toggles membership of the cursor row; the wheel moves the cursor
  * on hover, as it does over a `list`.
  *
  * `selected` is the set the view read (tracked at construction, see [[CheckboxElement]]); `onToggle` is handed the row
  * whose membership Space flipped.
  */
final case class SelectionListElement(
    items: Seq[String],
    selected: Set[Int],
    onToggle: Int => Unit,
    state: w.ListState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = SelectionListElement
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(wheelScrolls(() => state.selectPrevious(items.size), () => state.selectNext(items.size)))
  def widget: Widget                                                         =
    val rendered = items.zipWithIndex.map { (item, index) =>
      val marker = if selected.contains(index) then "[x] " else "[ ] "
      Line.raw(marker + item)
    }
    val view     = w.ListView(rendered, style = props.style, highlightStyle = props.focusStyle)
    (area, buffer) => view.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): SelectionListElement      = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]     =
    Some(
      keys { case KeyEvent(KeyCode.Char(' '), _) => state.selected.foreach(onToggle) }
        .orElse(selectionKeys(() => state.selectNext(items.size), () => state.selectPrevious(items.size)))
    )

/** A file chooser over a [[FilePickerState]]: arrows navigate, Enter opens directories or accepts a file into
  * `state.chosen`, and the wheel moves the selection on hover. `chosen` is the accepted path the view read, tracked at
  * construction.
  */
final case class FilePickerElement(
    state: FilePickerState,
    chosen: Option[Path],
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = FilePickerElement
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(wheelScrolls(() => state.tree.selectPrevious(), () => state.tree.selectNext()))
  def widget: Widget                                                         =
    val tree = w.DirectoryTree(style = props.style, highlightStyle = props.focusStyle)
    (area, buffer) =>
      val treeArea   = Rect(area.x, area.y, area.width, math.max(0, area.height - 1))
      tree.render(treeArea, buffer, state.tree)
      val chosenLine = chosen.map(path => s"→ $path").getOrElse("→ (nothing selected)")
      buffer.setString(area.x, area.bottom - 1, chosenLine, props.style.dim)
  private[dsl] def withProps(props: ElementProps): FilePickerElement         = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]     =
    Some(
      // the guard keeps Enter with nothing selected unconsumed, so it still reaches the app's own bindings
      keys { case KeyEvent(KeyCode.Enter, _) if state.tree.selected.isDefined => openOrAccept(state.tree.selected) }
        .orElse(selectionKeys(() => state.tree.selectNext(), () => state.tree.selectPrevious()))
    )

  /** Enter on a directory expands or collapses it; on a file it accepts it. */
  private def openOrAccept(selected: Option[Path]): Unit =
    selected match
      case Some(path) if java.nio.file.Files.isDirectory(path) => state.tree.toggle()
      case Some(path)                                          => state.chosen.set(Some(path))
      case None                                                => ()

/** A filesystem browser. Up/Down move the selection, Enter expands or collapses the selected directory, while focused;
  * the wheel moves the selection on hover. Listings are read lazily and cached in `state` — it touches the disk on
  * expansion, never per frame.
  */
final case class DirectoryTreeElement(
    state: w.DirectoryTreeState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = DirectoryTreeElement
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(wheelScrolls(() => state.selectPrevious(), () => state.selectNext()))
  def widget: Widget                                                         =
    val tree = w.DirectoryTree(style = props.style, highlightStyle = props.focusStyle)
    (area, buffer) => tree.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): DirectoryTreeElement      = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]     =
    Some(
      keys { case KeyEvent(KeyCode.Enter, _) => state.toggle() }
        .orElse(selectionKeys(() => state.selectNext(), () => state.selectPrevious()))
    )

/** A sortable, filterable table. Up/Down move the row selection while focused, and PageUp/PageDown turn the page once
  * `state.paging` is set (they are left unconsumed otherwise, so they keep bubbling). Sorting and filtering have no
  * built-in keys — drive `state.sortBy`/`state.setFilter` from the app's own bindings.
  *
  * Alone among the selectable collections this one has no wheel behavior. Its selection indexes the *visible* page
  * (`table.visibleRows(state)`), so a wheel step would have to decide whether it moves within the page or turns it, and
  * either answer is wrong for half the tables; the page keys say which one the app meant. Bind `onMouseEvent` if this
  * table wants one of the two.
  */
final case class DataTableElement(
    table: w.DataTable,
    state: w.DataTableState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = DataTableElement
  def widget: Widget                                                     =
    (area, buffer) => table.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): DataTableElement      = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler] =
    Some(
      keys {
        case KeyEvent(KeyCode.PageDown, _) if state.paging.nonEmpty => state.nextPage(table.filteredRows(state).size)
        case KeyEvent(KeyCode.PageUp, _) if state.paging.nonEmpty   => state.previousPage()
      }.orElse(
        selectionKeys(
          () => state.selectNext(table.visibleRows(state).size),
          () => state.selectPrevious(table.visibleRows(state).size),
        )
      )
    )

/** A scrollable log panel: Up/Down (and PageUp/PageDown) scroll while focused; the tail re-follows when scrolled back
  * to the bottom.
  */
final case class LogElement(
    state: w.LogState,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = LogElement
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(wheelScrolls(() => state.scrollUp(), () => state.scrollDown()))
  def widget: Widget                                                         =
    val log = w.Log(props.style)
    (area, buffer) => log.render(area, buffer, state)
  private[dsl] def withProps(props: ElementProps): LogElement                = copy(props = props)
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]     =
    Some(scrollKeys(rows => state.scrollUp(rows), rows => state.scrollDown(rows)))
