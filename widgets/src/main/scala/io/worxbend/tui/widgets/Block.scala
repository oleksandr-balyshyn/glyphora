package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Line, Rect, Span, Style, Widget}

/** The named frame a [[Block]] draws itself with — the box-drawing set, the dashed set, or the block-element set that
  * picks its corner and edge glyphs. [[BorderGlyphs.of]] turns one of these into the eight glyphs it stands for, and
  * `Block(borderSet = ...)` takes such a record directly when none of the named sets is the one you want.
  *
  * Every glyph in every set here is one terminal column wide, so choosing a set never changes a block's [[Block.inner]]
  * geometry — only its look.
  *
  *   - [[Plain]] `┌─┐`, [[Rounded]] `╭─╮`, [[Double]] `╔═╗`, [[Thick]] `┏━┓` — the four classic box-drawing weights.
  *   - [[Ascii]] `+-+` — plain ASCII, for a terminal or a font with no box-drawing glyphs, and for output that has to
  *     survive being pasted somewhere that is not a terminal at all.
  *   - The six dashed sets — light or heavy, broken into two, three or four dashes per cell — draw a frame that reads
  *     as provisional or inactive next to a solid one. Their corners stay solid, because a dashed corner glyph does not
  *     exist.
  *   - [[QuadrantInside]] and [[QuadrantOutside]] treat each cell as a 2x2 grid of half-cell "pixels", so the frame
  *     sits half a cell inside the block's area or half a cell outside it. Two nested blocks drawn one inside and one
  *     outside meet with no gap between them.
  *   - [[OneEighthWide]] and [[OneEighthTall]] are the "McGugan box", named after Will McGugan: one-eighth block
  *     elements pushed right up against the cell edge, which reads as a hairline rather than as a row of glyphs.
  *   - [[ProportionalWide]] and [[ProportionalTall]] compensate for a terminal cell being about twice as tall as it is
  *     wide, so the frame looks the same thickness horizontally and vertically.
  *   - [[Full]] `█` is a solid slab, and [[Blank]] draws spaces — the way to give a block a border-styled margin, or to
  *     keep a title's border row without any frame under it.
  */
enum BorderType:
  case Plain, Rounded, Double, Thick
  case Ascii
  case LightDoubleDashed, LightTripleDashed, LightQuadrupleDashed
  case HeavyDoubleDashed, HeavyTripleDashed, HeavyQuadrupleDashed
  case QuadrantInside, QuadrantOutside
  case OneEighthWide, OneEighthTall
  case ProportionalWide, ProportionalTall
  case Full, Blank

/** Which horizontal border a [[BlockTitle]] is written into. */
enum TitlePosition:
  case Top, Bottom

/** One caption drawn into a [[Block]]'s border: what to write, which border to write it on, and where along that border
  * to put it.
  *
  * A block may carry several — the common shape is a name at the top left and a status at the bottom right, which needs
  * two. A title never widens the block, and on a side that has a border it costs no content row either: it is written
  * *over* border cells that are being drawn anyway, so [[Block.inner]] is unchanged. On a side with *no* border there
  * is no such row to borrow, so the block reserves the outermost row for the title and [[Block.inner]] starts one row
  * in — otherwise the title would be painted straight over the first line of content.
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
  * There are two styles, and they do different jobs. [[style]] is painted over the *whole* area — border cells and
  * interior alike — before anything is drawn, which is how a panel gets a background colour of its own against the
  * screen behind it; because it only patches the style of each cell and never its glyph, content already drawn there
  * keeps its text and its foreground colour. [[borderStyle]] is then layered on top of it for the border glyphs and the
  * titles, so it only has to say what is *different* about the frame. A block left at the default `style` paints no
  * fill at all and behaves exactly as it did before the parameter existed.
  *
  * [[borderType]] names one of the built-in frames. [[borderSet]] is the escape hatch under it: hand it a
  * [[BorderGlyphs]] of your own and it wins over `borderType`, which is how a frame nothing in the enum describes gets
  * drawn without waiting for the enum to grow a case.
  *
  * Titles sharing a border *and* an alignment are drawn as one run separated by a single space, in the order given.
  * Titles that share a border with different alignments can still collide on a narrow block; the block clips rather
  * than reflows, matching the library-wide silent-clipping philosophy.
  */
final case class Block(
    titles: Seq[BlockTitle] = Seq.empty,
    borders: Borders = Borders.All,
    padding: Padding = Padding.zero,
    style: Style = Style.Default,
    borderStyle: Style = Style.Default,
    borderType: BorderType = BorderType.Plain,
    borderSet: Option[BorderGlyphs] = None,
) extends Widget:

  /** The content region inside the borders, the reserved title rows, and the padding. */
  def inner(area: Rect): Rect =
    val topInset    = rowsAbove(Borders.Top, TitlePosition.Top)
    val bottomInset = rowsAbove(Borders.Bottom, TitlePosition.Bottom)
    val left        = area.x + borderWidth(Borders.Left) + math.max(0, padding.left)
    val top         = area.y + topInset + math.max(0, padding.top)
    val width       = area.width - borderWidth(Borders.Left) - borderWidth(Borders.Right) - padding.horizontalCells
    val height      = area.height - topInset - bottomInset - padding.verticalCells
    if width <= 0 || height <= 0 then Rect(left, top, 0, 0) else Rect(left, top, width, height)

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      // `mapStyle` with `patch`, not `setStyle`: the panel background has to layer *onto* whatever is already there,
      // keeping each cell's foreground colour and modifiers. `setStyle` would replace them outright.
      if style != Style.Default then buffer.mapStyle(area)(_.patch(style))
      val glyphs = borderSet.getOrElse(BorderGlyphs.of(borderType))
      val top    = area.y
      val bottom = area.bottom - 1
      val left   = area.x
      val right  = area.right - 1
      if borders.hasAny(Borders.Left) then verticalEdge(buffer, area, left, glyphs.verticalLeft)
      if borders.hasAny(Borders.Right) && area.width > 1 then verticalEdge(buffer, area, right, glyphs.verticalRight)
      if borders.hasAny(Borders.Top) then horizontalEdge(buffer, area, top, glyphs.horizontalTop)
      if borders.hasAny(Borders.Bottom) && area.height > 1 then
        horizontalEdge(buffer, area, bottom, glyphs.horizontalBottom)
      if area.width > 1 && area.height > 1 then
        corner(buffer, left, top, glyphs.topLeft, Borders.Top, Borders.Left)
        corner(buffer, right, top, glyphs.topRight, Borders.Top, Borders.Right)
        corner(buffer, left, bottom, glyphs.bottomLeft, Borders.Bottom, Borders.Left)
        corner(buffer, right, bottom, glyphs.bottomRight, Borders.Bottom, Borders.Right)
      if titles.nonEmpty then renderTitles(buffer, area)

  private def horizontalEdge(buffer: Buffer, area: Rect, y: Int, glyph: String): Unit =
    var x = area.x
    while x < area.right do
      buffer.set(x, y, Cell(glyph, edgeStyle))
      x += 1

  private def verticalEdge(buffer: Buffer, area: Rect, x: Int, glyph: String): Unit =
    var y = area.y
    while y < area.bottom do
      buffer.set(x, y, Cell(glyph, edgeStyle))
      y += 1

  private def corner(buffer: Buffer, x: Int, y: Int, glyph: String, first: Borders, second: Borders): Unit =
    if borders.hasAny(first) && borders.hasAny(second) then buffer.set(x, y, Cell(glyph, edgeStyle))

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
    val _      = LineRenderer.render(buffer, startX, y, line, available - (startX - area.x - insetLeft), edgeStyle)

  /** The style the border glyphs and titles are drawn in: the whole-area [[style]] with [[borderStyle]] layered on top.
    *
    * Layering rather than replacing is what keeps a panel's background continuous. If the border were drawn in
    * `borderStyle` alone, a block given a blue background and a bold border would paint the frame with no background at
    * all and leave a one-cell hole around the panel; with the layering, `borderStyle` only has to say what is
    * *different* about the border.
    */
  private def edgeStyle: Style = style.patch(borderStyle)

  private def borderWidth(side: Borders): Int =
    if borders.hasAny(side) then 1 else 0

  /** How many rows one horizontal edge of the block costs the content: one for a border, one for a title with no border
    * under it, and one — not two — when there is both.
    *
    * A title is written *over* the border row, so a bordered side costs the same whether it carries a title or not. A
    * side with no border is the case this exists for: the title still has to go somewhere, and it goes on the block's
    * outermost row. Before this the content area started on that same row, so a titled borderless block drew its first
    * line of content and then painted the title straight over the top of it.
    */
  private def rowsAbove(side: Borders, position: TitlePosition): Int =
    if borders.hasAny(side) then 1
    else if titles.exists(_.position == position) then 1
    else 0
