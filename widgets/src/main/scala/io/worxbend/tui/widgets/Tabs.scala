package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Line, Measured, Rect, Style, Widget}

/** A single-row tab bar: titles separated by a divider, the selected title highlighted.
  *
  * Each title can be given its own left and right padding, and the padding belongs to the tab rather than to the
  * divider: `highlightStyle` covers it, so a reversed highlight paints a bar the full width of the padded tab instead
  * of hugging the glyphs of the title. That distinction is what separates `▏ Tab ▕` from `▏Tab▕` on screen, and it is
  * also why padding cannot be expressed by widening the divider — a divider is drawn between tabs, so the first and the
  * last tab would get none of it, and no part of it would ever be highlighted.
  *
  * Both paddings are empty by default, which draws exactly what this widget drew before they existed: the default
  * `divider` carries its own blanks. For the padded look, set the divider to a bare `"│"` and both paddings to `" "` —
  * or call [[Tabs.padded]], which does that for you.
  */
final case class Tabs(
    titles: Seq[Line],
    selected: Int = 0,
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.reverse,
    divider: String = " │ ",
    paddingLeft: String = "",
    paddingRight: String = "",
) extends Widget
    with Measured:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val cursor = RowCursor(buffer, area.y, area.x, area.right)
      titles.zipWithIndex.foreach { (title, index) =>
        val tabStyle = if index == selected then style.patch(highlightStyle) else style
        cursor.write(paddingLeft, tabStyle)
        cursor.skip(LineRenderer.render(buffer, cursor.at, area.y, title, cursor.remaining, tabStyle))
        cursor.write(paddingRight, tabStyle)
        val isLast   = index == titles.size - 1
        if !isLast then cursor.write(divider, style)
      }

  /** The columns this bar needs to draw every tab in full: each title inside its padding, plus one divider between
    * neighbours. A tab bar is always one row and its width never depends on how many rows it is given, so `height` is
    * ignored.
    *
    * Answering this lets a `row(...)` size a tab bar from its own content instead of the caller hardcoding a constraint
    * that has to be edited every time a tab is renamed.
    */
  override def widthAt(height: Int): Option[Int] =
    val padding  = CharWidth.of(paddingLeft) + CharWidth.of(paddingRight)
    val dividers = math.max(0, titles.size - 1) * CharWidth.of(divider)
    Some(titles.map(title => title.width + padding).sum + dividers)

object Tabs:

  /** The padded look: a bare `│` between tabs and one blank column inside each tab, so the highlight of the selected
    * tab covers a column either side of its title rather than stopping at the glyphs.
    */
  def padded(
      titles: Seq[Line],
      selected: Int = 0,
      style: Style = Style.Default,
      highlightStyle: Style = Style.Default.reverse,
  ): Tabs =
    Tabs(titles, selected, style, highlightStyle, divider = "│", paddingLeft = " ", paddingRight = " ")
