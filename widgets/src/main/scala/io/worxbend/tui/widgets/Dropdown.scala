package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Measured, Rect, StatefulWidget, Style}

/** Caller-owned dropdown state: whether the option list is showing, and the highlight and scroll position of that list
  * while it is.
  *
  * The highlight lives in a [[MenuState]] and is deliberately not the committed value. Moving it while the list is open
  * changes nothing the application can see; only committing it does. That is what lets a user open the list, look
  * around, and back out with the value they started with.
  *
  * Render-thread-only, and mutating it does not by itself schedule a frame — the same contract every widget state in
  * this module carries. It is a plain mutable object the reactive layer cannot see, so a change written into it stays
  * off screen until something else repaints; pair it with a `Signal` write or a `requestRedraw()`.
  */
final class DropdownState(var open: Boolean = false, val menu: MenuState = MenuState()):

  /** Shows the list with the highlight parked on the option that is currently chosen, and the list scrolled back to the
    * top. Parking the highlight on the current value is what makes "open it and press Enter" a no-op rather than a
    * silent jump to the first option.
    */
  def openAt(selected: Int): Unit =
    open = true
    menu.selected = Some(selected)
    menu.offset = 0

  def close(): Unit = open = false

/** A collapsed option chooser: one row showing the option in force, and, while [[DropdownState.open]], the whole option
  * list drawn directly beneath that row as a bordered popup.
  *
  * The popup is painted inside the widget's own `Rect` rather than floating over the rest of the screen, so the caller
  * has to give it the room — see [[openHeight]], which is what the DSL node claims while the list is open. A dropdown
  * that is given only one row still draws its closed row correctly and simply shows no list.
  *
  * The popup itself is a [[Menu]]. Drawing the list a second way here would be a second set of border, highlight,
  * disabled and scrolling rules to keep in step with the first.
  *
  * @param maxVisibleRows
  *   how many options the popup shows at once. It is layout rather than appearance — it decides what is drawn where —
  *   which is why it sits before the styles. A longer list scrolls past it as the highlight moves.
  */
final case class Dropdown(
    options: Seq[String],
    selected: Int,
    maxVisibleRows: Int = 8,
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
    borderType: BorderType = BorderType.Rounded,
    closedGlyph: String = "▸",
    openGlyph: String = "▾",
) extends StatefulWidget[DropdownState]
    with Measured:

  /** How many rows the popup needs: one per visible option, plus its top and bottom border. Zero when there is nothing
    * to show, so an empty dropdown never draws an empty box.
    */
  def popupHeight: Int =
    if options.isEmpty then 0 else math.min(math.max(1, maxVisibleRows), options.size) + 2

  /** The rows this widget needs while its list is showing: the closed row plus the popup. */
  def openHeight: Int = 1 + popupHeight

  /** Natural width: the widest option plus the glyph and the space after it, and at least what the popup's own border
    * and padding need, so opening the list never makes the widget look too narrow for its own content.
    */
  override def widthAt(height: Int): Option[Int] =
    val _      = height
    val glyph  = math.max(CharWidth.of(closedGlyph), CharWidth.of(openGlyph))
    val widest = options.map(CharWidth.of).maxOption.getOrElse(0)
    Some(math.max(glyph + 1 + widest, widest + 4))

  /** Natural height: one row.
    *
    * This reports the *closed* height on purpose, even when the list happens to be open. [[Measured]] answers from the
    * widget value alone and is handed no state, so it cannot see whether this particular dropdown is open; reporting
    * anything else would be a guess. A caller that needs the open height asks [[openHeight]], which is exactly what the
    * DSL node does.
    */
  override def heightAt(width: Int): Option[Int] =
    val _ = width
    Some(1)

  def render(area: Rect, buffer: Buffer, state: DropdownState): Unit =
    if !area.isEmpty then
      renderClosedRow(area, buffer, state)
      if state.open && options.nonEmpty && area.height > 1 then
        val height = math.min(popupHeight, area.height - 1)
        Menu(options.map(label => MenuEntry.Item(label)), style, highlightStyle, borderType = borderType)
          .render(Rect(area.x, area.y + 1, area.width, height), buffer, state.menu)

  /** The always-visible row: an open/closed glyph, then the option in force.
    *
    * The label is truncated by display columns rather than by character count, so a CJK or emoji option that is wider
    * than it is long cannot spill past the widget's right edge.
    */
  private def renderClosedRow(area: Rect, buffer: Buffer, state: DropdownState): Unit =
    val glyph = if state.open then openGlyph else closedGlyph
    val label = options.lift(clampedSelection).getOrElse("")
    val row   = CharWidth.substringByWidth(s"$glyph $label", area.width)
    buffer.setString(area.x, area.y, " ".repeat(area.width), style)
    buffer.setString(area.x, area.y, row, style)

  /** The chosen index pulled back inside the option list. A dropdown handed a stale index — the list shrank underneath
    * it — shows the nearest real option rather than an empty row.
    */
  private def clampedSelection: Int =
    if options.isEmpty then 0 else math.max(0, math.min(selected, options.size - 1))
