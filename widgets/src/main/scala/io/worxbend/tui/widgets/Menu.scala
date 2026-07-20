package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, StatefulWidget, Style}

/** One entry in a [[Menu]].
  *
  * A `separator` renders as a horizontal rule and is never selectable; a disabled item renders dimmed and is skipped by
  * keyboard navigation. `shortcut` is right-aligned hint text (e.g. `^S`), never a live binding — the app wires the
  * action.
  */
final case class MenuItem(
    label: String,
    shortcut: Option[String] = None,
    enabled: Boolean = true,
    separator: Boolean = false,
):
  /** Whether keyboard/mouse navigation can land on this entry (not a separator, not disabled). */
  def selectable: Boolean = enabled && !separator

object MenuItem:
  val Separator: MenuItem = MenuItem("", separator = true)

/** Caller-owned menu state: the highlighted index and the scroll offset for menus taller than their popup. Mutable on
  * purpose (the `StatefulWidget` contract); navigation helpers skip separators and disabled entries.
  */
final class MenuState(var selected: Int = 0, var offset: Int = 0):

  /** Moves the highlight to the next selectable entry, wrapping; a no-op when nothing is selectable. */
  def selectNext(items: Seq[MenuItem]): Unit = step(items, 1)

  /** Moves the highlight to the previous selectable entry, wrapping. */
  def selectPrevious(items: Seq[MenuItem]): Unit = step(items, -1)

  private def step(items: Seq[MenuItem], delta: Int): Unit =
    if items.exists(_.selectable) then
      val size  = items.size
      var next  = selected
      var guard = 0
      while guard < size do
        next = (next + delta + size) % size
        guard += 1
        if items(next).selectable then guard = size
      selected = next

  /** Snaps the highlight onto the first selectable entry if it currently sits on a non-selectable one. */
  private[widgets] def normalize(items: Seq[MenuItem]): Unit =
    if items.nonEmpty && !items.lift(selected).exists(_.selectable) then
      val first = items.indexWhere(_.selectable)
      if first >= 0 then selected = first

/** A vertical menu / dropdown / context menu rendered as a bordered popup.
  *
  * Labels sit on the left, `shortcut` hints right-aligned; the highlighted row draws with `highlightStyle`, disabled
  * rows dim, separators become a full-width rule. Backend-agnostic and render-to-`Buffer` tested; the DSL wrapper adds
  * focus, key, and mouse handling.
  */
final case class Menu(
    items: Seq[MenuItem],
    borderType: BorderType = BorderType.Rounded,
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
    disabledStyle: Style = Style.Default.dim,
) extends StatefulWidget[MenuState]:

  /** The popup's natural width in cells (widest `label  shortcut` plus borders and padding). */
  def width: Int =
    val content = items
      .map { item =>
        val shortcut = item.shortcut.map(s => CharWidth.of(s) + 2).getOrElse(0)
        CharWidth.of(item.label) + shortcut
      }
      .maxOption
      .getOrElse(0)
    content + 4 // 1 border + 1 pad each side

  /** The popup's natural height in cells (one row per item plus borders). */
  def height: Int = items.size + 2

  def render(area: Rect, buffer: Buffer, state: MenuState): Unit =
    if !area.isEmpty then
      state.normalize(items)
      val block = Block(borderType = borderType, borderStyle = style, padding = 0)
      block.render(area, buffer)
      val inner = block.inner(area)
      if !inner.isEmpty && items.nonEmpty then
        val visible = math.min(inner.height, items.size)
        state.offset = scrolledOffset(state.offset, state.selected, inner.height)
        var row     = 0
        while row < visible do
          val index = state.offset + row
          if index < items.size then renderItem(buffer, inner, row, index, state.selected)
          row += 1

  private def renderItem(buffer: Buffer, inner: Rect, row: Int, index: Int, selected: Int): Unit =
    val item = items(index)
    val y    = inner.y + row
    if item.separator then
      val rule = "─".repeat(math.max(0, inner.width))
      buffer.setString(inner.x, y, rule, style)
    else
      val rowStyle =
        if index == selected then style.patch(highlightStyle) else if !item.enabled then disabledStyle else style
      // paint the full row so the highlight spans the popup width
      buffer.setString(inner.x, y, " ".repeat(inner.width), rowStyle)
      val label    = CharWidth.substringByWidth(" " + item.label, inner.width)
      buffer.setString(inner.x, y, label, rowStyle)
      item.shortcut.foreach { shortcut =>
        val hint  = shortcut + " "
        val hintW = CharWidth.of(hint)
        if hintW + 2 <= inner.width then buffer.setString(inner.right - hintW, y, hint, rowStyle)
      }

  private def scrolledOffset(offset: Int, selected: Int, height: Int): Int =
    val maxOffset = math.max(0, items.size - height)
    val clamped   = math.max(0, math.min(offset, maxOffset))
    if selected < clamped then selected
    else if selected >= clamped + height then selected - height + 1
    else clamped
