package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Line, Rect, Span, Style, Widget}

/** The box-drawing set a [[Block]] frames itself with: `┌─┐`, `╭─╮`, `╔═╗`, or `┏━┓`.
  *
  * All four are single-column glyphs, so the border never changes a block's [[Block.inner]] geometry — only its look.
  */
enum BorderType:
  case Plain, Rounded, Double, Thick

/** Which horizontal border a [[BlockTitle]] is written into. */
enum TitlePosition:
  case Top, Bottom

/** One caption drawn into a [[Block]]'s border: what to write, which border to write it on, and where along that border
  * to put it.
  *
  * A block may carry several — the common shape is a name at the top left and a status at the bottom right, which needs
  * two. Titles never widen the block and never change [[Block.inner]]: they are written *over* border cells that are
  * being drawn anyway, so adding one costs no content row.
  */
final case class BlockTitle(line: Line, position: TitlePosition, alignment: Alignment)

object BlockTitle:

  /** A title on the top border, left-aligned unless told otherwise. */
  def top(line: Line, alignment: Alignment = Alignment.Left): BlockTitle =
    BlockTitle(line, TitlePosition.Top, alignment)

  /** A title on the bottom border, left-aligned unless told otherwise. */
  def bottom(line: Line, alignment: Alignment = Alignment.Left): BlockTitle =
    BlockTitle(line, TitlePosition.Bottom, alignment)

/** A bordered box with any number of border titles — the basic chrome widget almost everything else nests inside.
  *
  * Borders are per-side (`Borders.Top | Borders.Bottom` for a horizontal band, `Borders.All` for the classic box);
  * corner glyphs appear only where two adjacent sides meet. [[padding]] adds blank cells inside the borders, per side.
  * Content belongs in `inner(area)`; rendering the block never touches the interior, so it composes with any content
  * widget drawn after it.
  *
  * Titles sharing a border *and* an alignment are drawn as one run separated by a single space, in the order given.
  * Titles that share a border with different alignments can still collide on a narrow block; the block clips rather
  * than reflows, matching the library-wide silent-clipping philosophy.
  */
final case class Block(
    titles: Seq[BlockTitle] = Seq.empty,
    borderType: BorderType = BorderType.Plain,
    borderStyle: Style = Style.Default,
    borders: Borders = Borders.All,
    padding: Padding = Padding.zero,
) extends Widget:

  /** The content region inside the borders and padding. */
  def inner(area: Rect): Rect =
    val left   = area.x + borderWidth(Borders.Left) + math.max(0, padding.left)
    val top    = area.y + borderWidth(Borders.Top) + math.max(0, padding.top)
    val width  = area.width - borderWidth(Borders.Left) - borderWidth(Borders.Right) - padding.horizontalCells
    val height = area.height - borderWidth(Borders.Top) - borderWidth(Borders.Bottom) - padding.verticalCells
    if width <= 0 || height <= 0 then Rect(left, top, 0, 0) else Rect(left, top, width, height)

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val glyphs = BorderGlyphs.of(borderType)
      val top    = area.y
      val bottom = area.bottom - 1
      val left   = area.x
      val right  = area.right - 1
      if borders.hasAny(Borders.Left) then verticalEdge(buffer, area, left, glyphs)
      if borders.hasAny(Borders.Right) && area.width > 1 then verticalEdge(buffer, area, right, glyphs)
      if borders.hasAny(Borders.Top) then horizontalEdge(buffer, area, top, glyphs)
      if borders.hasAny(Borders.Bottom) && area.height > 1 then horizontalEdge(buffer, area, bottom, glyphs)
      if area.width > 1 && area.height > 1 then
        corner(buffer, left, top, glyphs.topLeft, Borders.Top, Borders.Left)
        corner(buffer, right, top, glyphs.topRight, Borders.Top, Borders.Right)
        corner(buffer, left, bottom, glyphs.bottomLeft, Borders.Bottom, Borders.Left)
        corner(buffer, right, bottom, glyphs.bottomRight, Borders.Bottom, Borders.Right)
      if titles.nonEmpty then renderTitles(buffer, area)

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
    if borders.hasAny(first) && borders.hasAny(second) then buffer.set(x, y, Cell(glyph, borderStyle))

  /** Draws every title into its border.
    *
    * The iteration is over the fixed `(position, alignment)` grid rather than over a `groupBy` of the titles, because a
    * `Map`'s iteration order is unspecified: two titles that overlap on a narrow block would then paint in a different
    * order from one run to the next, and a golden-frame test would flap. Six passes over a short `Seq` costs nothing.
    */
  private def renderTitles(buffer: Buffer, area: Rect): Unit =
    val insetLeft  = if borders.hasAny(Borders.Left) then 1 else 0
    val insetRight = if borders.hasAny(Borders.Right) then 1 else 0
    val available  = area.width - insetLeft - insetRight
    if available > 0 then
      for
        position  <- TitlePosition.values
        alignment <- Alignment.values
      do
        val group = titles.filter(title => title.position == position && title.alignment == alignment)
        if group.nonEmpty then renderTitleGroup(buffer, area, position, alignment, group, insetLeft, available)

  /** Writes one `(position, alignment)` group as a single space-separated run on its border. */
  private def renderTitleGroup(
      buffer: Buffer,
      area: Rect,
      position: TitlePosition,
      alignment: Alignment,
      group: Seq[BlockTitle],
      insetLeft: Int,
      available: Int,
  ): Unit =
    val line   = Line(group.map(_.line.spans).reduce((left, right) => left ++ Seq(Span.raw(" ")) ++ right))
    val y      = position match
      case TitlePosition.Top    => area.y
      case TitlePosition.Bottom => area.bottom - 1
    val width  = math.min(line.width, available)
    val startX = alignment.originAt(area.x + insetLeft, available, width)
    val _      = LineRenderer.render(buffer, startX, y, line, available - (startX - area.x - insetLeft), borderStyle)

  private def borderWidth(side: Borders): Int =
    if borders.hasAny(side) then 1 else 0

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
