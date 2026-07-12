package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Line, Rect, Style, Widget}

enum BorderType:
  case Plain, Rounded, Double, Thick

/** A bordered box with an optional title — the basic chrome widget almost everything else nests inside.
  *
  * Borders are per-side (`Borders.Top | Borders.Bottom` for a horizontal band, `Borders.All` for the classic box);
  * corner glyphs appear only where two adjacent sides meet. `padding` adds blank cells inside the borders on every
  * side. Content belongs in `inner(area)`; rendering the block never touches the interior, so it composes with any
  * content widget drawn after it.
  */
final case class Block(
    title: Option[Line] = None,
    borderType: BorderType = BorderType.Plain,
    borderStyle: Style = Style.Default,
    borders: Borders = Borders.All,
    titleAlignment: Alignment = Alignment.Left,
    padding: Int = 0,
) extends Widget:

  /** The content region inside the borders and padding. */
  def inner(area: Rect): Rect =
    val left = area.x + borderWidth(Borders.Left) + padding
    val top = area.y + borderWidth(Borders.Top) + padding
    val width = area.width - borderWidth(Borders.Left) - borderWidth(Borders.Right) - 2 * padding
    val height = area.height - borderWidth(Borders.Top) - borderWidth(Borders.Bottom) - 2 * padding
    if width <= 0 || height <= 0 then Rect(left, top, 0, 0) else Rect(left, top, width, height)

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val glyphs = BorderGlyphs.of(borderType)
      val top = area.y
      val bottom = area.bottom - 1
      val left = area.x
      val right = area.right - 1
      if borders.has(Borders.Left) then verticalEdge(buffer, area, left, glyphs)
      if borders.has(Borders.Right) && area.width > 1 then verticalEdge(buffer, area, right, glyphs)
      if borders.has(Borders.Top) then horizontalEdge(buffer, area, top, glyphs)
      if borders.has(Borders.Bottom) && area.height > 1 then horizontalEdge(buffer, area, bottom, glyphs)
      if area.width > 1 && area.height > 1 then
        corner(buffer, left, top, glyphs.topLeft, Borders.Top, Borders.Left)
        corner(buffer, right, top, glyphs.topRight, Borders.Top, Borders.Right)
        corner(buffer, left, bottom, glyphs.bottomLeft, Borders.Bottom, Borders.Left)
        corner(buffer, right, bottom, glyphs.bottomRight, Borders.Bottom, Borders.Right)
      title.foreach(renderTitle(buffer, area, _))

  private def horizontalEdge(buffer: Buffer, area: Rect, y: Int, glyphs: BorderGlyphs): Unit =
    var x = area.x
    while x < area.right do
      buffer.set(x, y, Cell(glyphs.horizontal, borderStyle))
      x += 1

  private def verticalEdge(buffer: Buffer, area: Rect, x: Int, glyphs: BorderGlyphs): Unit =
    var y = area.y
    while y < area.bottom do
      buffer.set(x, y, Cell(glyphs.vertical, borderStyle))
      y += 1

  private def corner(buffer: Buffer, x: Int, y: Int, glyph: String, first: Borders, second: Borders): Unit =
    if borders.has(first) && borders.has(second) then buffer.set(x, y, Cell(glyph, borderStyle))

  private def renderTitle(buffer: Buffer, area: Rect, line: Line): Unit =
    val insetLeft = if borders.has(Borders.Left) then 1 else 0
    val insetRight = if borders.has(Borders.Right) then 1 else 0
    val available = area.width - insetLeft - insetRight
    if available > 0 then
      val titleWidth = math.min(line.width, available)
      val startX = titleAlignment match
        case Alignment.Left   => area.x + insetLeft
        case Alignment.Center => area.x + insetLeft + (available - titleWidth) / 2
        case Alignment.Right  => area.x + insetLeft + available - titleWidth
      val _ = LineRenderer.render(buffer, startX, area.y, line, available - (startX - area.x - insetLeft), borderStyle)

  private def borderWidth(side: Borders): Int =
    if borders.has(side) then 1 else 0

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
