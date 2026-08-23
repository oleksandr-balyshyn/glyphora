package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, StatefulWidget, Style}

/** One entry in a [[Menu]]: either a row the user can land on, or a rule that divides two groups of them.
  *
  * A sum type rather than one case class with a `separator: Boolean`, because that flag made a labelled, shortcut-
  * carrying, enabled separator representable — four fields describing something with no label, no shortcut and no
  * enabled state. Here a `Separator` has nothing to configure, which is exactly what it is.
  */
enum MenuEntry:

  /** A row the user can land on. A disabled item renders dimmed and keyboard navigation skips it; `shortcut` is
    * right-aligned hint text (e.g. `^S`), never a live binding — the app wires the action.
    */
  case Item(label: String, shortcut: Option[String] = None, enabled: Boolean = true)

  /** A horizontal rule between groups of items; never selectable. */
  case Separator

  /** Whether keyboard/mouse navigation can land on this entry. */
  def selectable: Boolean = this match
    case Item(_, _, enabled) => enabled
    case Separator           => false

/** Caller-owned menu state: the highlighted entry and the scroll offset for menus taller than their popup. Mutable on
  * purpose (the `StatefulWidget` contract); navigation helpers skip separators and disabled entries.
  *
  * `selected` is `None` when nothing is highlighted, which is the honest state of a menu whose entries are all
  * separators or all disabled — [[Menu.render]] normalizes to it, and the widget then paints no highlight at all. Owned
  * by whoever constructs it and read by the render thread, like every other widget state in this module.
  */
final class MenuState(var selected: Option[Int] = None, var offset: Int = 0):

  /** Moves the highlight to the next selectable entry, wrapping; a no-op when nothing is selectable. */
  def selectNext(items: Seq[MenuEntry]): Unit = step(items, 1)

  /** Moves the highlight to the previous selectable entry, wrapping. */
  def selectPrevious(items: Seq[MenuEntry]): Unit = step(items, -1)

  private def step(items: Seq[MenuEntry], delta: Int): Unit =
    if items.exists(_.selectable) then
      val size    = items.size
      var next    = selected.getOrElse(0)
      var landed  = false
      // one full lap at most: `items.exists` guarantees a landing spot, the bound just rules out a spin
      var stepped = 0
      while !landed && stepped < size do
        next = (next + delta + size) % size
        stepped += 1
        landed = items(next).selectable
      selected = Some(next)

  /** Snaps the highlight onto the first selectable entry when it sits on a non-selectable one, and clears it entirely
    * when the menu has nothing selectable at all.
    */
  private[widgets] def normalize(items: Seq[MenuEntry]): Unit =
    if !selected.flatMap(items.lift).exists(_.selectable) then
      selected = Some(items.indexWhere(_.selectable)).filter(_ >= 0)

/** A vertical menu / dropdown / context menu rendered as a bordered popup.
  *
  * Labels sit on the left, `shortcut` hints right-aligned; the highlighted row draws with `highlightStyle`, disabled
  * rows dim, separators become a full-width rule. Backend-agnostic and render-to-`Buffer` tested; the DSL wrapper adds
  * focus, key, and mouse handling.
  */
final case class Menu(
    items: Seq[MenuEntry],
    borderType: BorderType = BorderType.Rounded,
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
    disabledStyle: Style = Style.Default.dim,
) extends StatefulWidget[MenuState]:

  /** The popup's natural width in cells (widest `label  shortcut` plus borders and padding). */
  def width: Int =
    val content = items
      .map {
        case MenuEntry.Item(label, shortcut, _) =>
          CharWidth.of(label) + shortcut.map(hint => CharWidth.of(hint) + 2).getOrElse(0)
        case MenuEntry.Separator                => 0
      }
      .maxOption
      .getOrElse(0)
    content + 4 // 1 border + 1 pad each side

  /** The popup's natural height in cells (one row per item plus borders). */
  def height: Int = items.size + 2

  def render(area: Rect, buffer: Buffer, state: MenuState): Unit =
    if !area.isEmpty then
      state.normalize(items)
      val block = Block(borderType = borderType, borderStyle = style)
      block.render(area, buffer)
      val inner = block.inner(area)
      if !inner.isEmpty && items.nonEmpty then
        val visible = math.min(inner.height, items.size)
        state.offset = ScrollWindow.offsetFor(state.offset, state.selected, items.size, inner.height)
        var row     = 0
        while row < visible do
          val index = state.offset + row
          if index < items.size then renderItem(buffer, inner, row, index, state.selected)
          row += 1

  private def renderItem(buffer: Buffer, inner: Rect, row: Int, index: Int, selected: Option[Int]): Unit =
    val y = inner.y + row
    items(index) match
      case MenuEntry.Separator                      =>
        val rule = "─".repeat(math.max(0, inner.width))
        buffer.setString(inner.x, y, rule, style)
      case MenuEntry.Item(label, shortcut, enabled) =>
        val rowStyle =
          if selected.contains(index) then style.patch(highlightStyle)
          else if !enabled then disabledStyle
          else style
        // paint the full row so the highlight spans the popup width
        buffer.setString(inner.x, y, " ".repeat(inner.width), rowStyle)
        val fitted   = CharWidth.substringByWidth(" " + label, inner.width)
        buffer.setString(inner.x, y, fitted, rowStyle)
        shortcut.foreach { hint =>
          val padded = hint + " "
          val hintW  = CharWidth.of(padded)
          if hintW + 2 <= inner.width then buffer.setString(inner.right - hintW, y, padded, rowStyle)
        }
