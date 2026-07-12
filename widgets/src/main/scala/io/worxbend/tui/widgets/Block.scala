package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Line, Rect, Style, Widget}

enum BorderType:
  case Plain, Rounded, Double, Thick

/** A bordered box with an optional title — the basic chrome widget almost everything else nests inside.
  *
  * Content belongs in `inner(area)`; rendering the block never touches the interior, so it composes with any
  * content widget drawn after it.
  */
final case class Block(
    title: Option[Line] = None,
    borderType: BorderType = BorderType.Plain,
    borderStyle: Style = Style.Default,
) extends Widget:

  /** The content region inside the borders. */
  def inner(area: Rect): Rect = area.inset(1)

  def render(area: Rect, buffer: Buffer): Unit =
    if area.width >= 2 && area.height >= 2 then
      val glyphs = BorderGlyphs.of(borderType)
      val top = area.y
      val bottom = area.bottom - 1
      val left = area.x
      val right = area.right - 1
      var x = left + 1
      while x < right do
        buffer.set(x, top, Cell(glyphs.horizontal, borderStyle))
        buffer.set(x, bottom, Cell(glyphs.horizontal, borderStyle))
        x += 1
      var y = top + 1
      while y < bottom do
        buffer.set(left, y, Cell(glyphs.vertical, borderStyle))
        buffer.set(right, y, Cell(glyphs.vertical, borderStyle))
        y += 1
      buffer.set(left, top, Cell(glyphs.topLeft, borderStyle))
      buffer.set(right, top, Cell(glyphs.topRight, borderStyle))
      buffer.set(left, bottom, Cell(glyphs.bottomLeft, borderStyle))
      buffer.set(right, bottom, Cell(glyphs.bottomRight, borderStyle))
      title.foreach { line =>
        val _ = LineRenderer.render(buffer, left + 1, top, line, area.width - 2, borderStyle)
      }

private final case class BorderGlyphs(
    horizontal: String,
    vertical: String,
    topLeft: String,
    topRight: String,
    bottomLeft: String,
    bottomRight: String,
)

private object BorderGlyphs:
  def of(borderType: BorderType): BorderGlyphs =
    borderType match
      case BorderType.Plain   => BorderGlyphs("─", "│", "┌", "┐", "└", "┘")
      case BorderType.Rounded => BorderGlyphs("─", "│", "╭", "╮", "╰", "╯")
      case BorderType.Double  => BorderGlyphs("═", "║", "╔", "╗", "╚", "╝")
      case BorderType.Thick   => BorderGlyphs("━", "┃", "┏", "┓", "┗", "┛")
