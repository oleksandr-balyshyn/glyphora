package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Line, Rect, Span, Style, Widget}

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
  * [[titleStyle]] is a third style, layered over the border's, for the titles alone. Without it the only way to give
  * every caption on a block the same emphasis — bold panel names, a dimmed status line — was to restyle each
  * [[BlockTitle]]'s [[io.worxbend.tui.core.Line]] one at a time and keep them in step by hand. A title's own spans
  * still layer over it, so one word of a caption can differ from the rest.
  *
  * [[borderType]] names one of the built-in frames. [[borderSet]] is the escape hatch under it: hand it a
  * [[BorderGlyphs]] of your own and it wins over `borderType`, which is how a frame nothing in the enum describes gets
  * drawn without waiting for the enum to grow a case.
  *
  * [[shadow]] casts an offset drop shadow, which is what makes a dialog or a popup read as floating rather than cut
  * into the screen. Unlike a CSS box shadow it is paid for *inside* the block's own area — a widget never draws outside
  * the rectangle it is handed — so a block with the default one-cell shadow frames itself one column narrower and one
  * row shorter, and the strip that frees up is the shadow. On an area too small to leave a usable frame the shadow is
  * dropped and the block renders as it would without one.
  *
  * Titles sharing a border *and* an alignment are drawn as one run separated by a single space, in the order given.
  * Titles that share a border with different alignments can still collide on a narrow block; the block clips rather
  * than reflows, matching the library-wide silent-clipping philosophy.
  *
  * [[mergeBorders]] decides what happens where this block's border lands on a border that is already in the buffer. The
  * default, [[MergeStrategy.Replace]], overwrites — two panels sharing a column then draw one wall whose corners do not
  * join. `Exact` and `Fuzzy` combine the two glyphs instead, so the seam becomes `┬`, `┴` or `┼`. See [[MergeStrategy]]
  * for the difference between them.
  */
final case class Block(
    titles: Seq[BlockTitle] = Seq.empty,
    borders: Borders = Borders.All,
    padding: Padding = Padding.zero,
    style: Style = Style.Default,
    borderStyle: Style = Style.Default,
    borderType: BorderType = BorderType.Plain,
    borderSet: Option[BorderGlyphs] = None,
    shadow: Option[Shadow] = None,
    // Appended rather than placed in the layout-and-behaviour slot the widget conventions ask for: inserting a
    // parameter mid-list would silently change what every positional caller written against 0.12.0 means.
    mergeBorders: MergeStrategy = MergeStrategy.Replace,
    titleStyle: Style = Style.Default,
) extends Widget:

  /** The rectangle the frame itself occupies inside `area`: everything except the rows and columns given up to the
    * shadow.
    *
    * A widget never draws outside the `Rect` it is handed, so — unlike a CSS box shadow, which spills beyond its
    * element — the shadow is paid for *inside* the block's own area. A block with the default one-cell shadow is one
    * column narrower and one row shorter than the area it was given, and the shadow occupies the strip that frees up.
    *
    * When the area is too small for the shadow to leave a usable frame behind (fewer than two rows or columns left),
    * the shadow is dropped and the whole area is the frame. A shadow with nothing left to shade is worse than no
    * shadow: it is the same degrade-quietly rule the rest of the widget follows on a tiny area.
    */
  private def frame(area: Rect): Rect =
    shadow match
      case Some(cast) =>
        val width  = area.width - cast.reservedColumns
        val height = area.height - cast.reservedRows
        if width < 2 || height < 2 then area
        else
          // a negative offset casts up and to the left, so the frame moves down and right to make the room there
          val x = area.x + (if cast.offsetX < 0 then cast.reservedColumns else 0)
          val y = area.y + (if cast.offsetY < 0 then cast.reservedRows else 0)
          Rect(x, y, width, height)
      case None       => area

  /** The content region inside the shadow, the borders, the reserved title rows, and the padding. */
  def inner(outer: Rect): Rect =
    val area        = frame(outer)
    val topInset    = rowsAbove(Borders.Top, TitlePosition.Top)
    val bottomInset = rowsAbove(Borders.Bottom, TitlePosition.Bottom)
    val left        = area.x + borderWidth(Borders.Left) + math.max(0, padding.left)
    val top         = area.y + topInset + math.max(0, padding.top)
    val width       = area.width - borderWidth(Borders.Left) - borderWidth(Borders.Right) - padding.horizontalCells
    val height      = area.height - topInset - bottomInset - padding.verticalCells
    if width <= 0 || height <= 0 then Rect(left, top, 0, 0) else Rect(left, top, width, height)

  def render(outer: Rect, buffer: Buffer): Unit =
    if !outer.isEmpty then
      val area    = frame(outer)
      // painted first, so the frame, the fill and the titles all win wherever they meet the band
      if area != outer then shadow.foreach(_.render(area, outer, buffer))
      // `mapStyle` with `patch`, not `setStyle`: the panel background has to layer *onto* whatever is already there,
      // keeping each cell's foreground colour and modifiers. `setStyle` would replace them outright.
      if style != Style.Default then buffer.mapStyle(area)(_.patch(style))
      val glyphs  = borderSet.getOrElse(BorderGlyphs.of(borderType))
      val top     = area.y
      val bottom  = area.bottom - 1
      val left    = area.x
      val right   = area.right - 1
      // a corner needs both of its sides and a cell of its own to live in; where one is drawn, the edges leave that
      // cell to it, so no cell of the frame is written twice and `mergeBorders` sees only what was there before
      val corners = area.width > 1 && area.height > 1
      if borders.hasAny(Borders.Left) then verticalEdge(buffer, area, left, glyphs.verticalLeft, corners)
      if borders.hasAny(Borders.Right) && area.width > 1 then
        verticalEdge(buffer, area, right, glyphs.verticalRight, corners)
      if borders.hasAny(Borders.Top) then horizontalEdge(buffer, area, top, glyphs.horizontalTop, corners)
      if borders.hasAny(Borders.Bottom) && area.height > 1 then
        horizontalEdge(buffer, area, bottom, glyphs.horizontalBottom, corners)
      if corners then
        corner(buffer, left, top, glyphs.topLeft, Borders.Top, Borders.Left)
        corner(buffer, right, top, glyphs.topRight, Borders.Top, Borders.Right)
        corner(buffer, left, bottom, glyphs.bottomLeft, Borders.Bottom, Borders.Left)
        corner(buffer, right, bottom, glyphs.bottomRight, Borders.Bottom, Borders.Right)
      if titles.nonEmpty then renderTitles(buffer, area)

  /** One horizontal run, minus the two cells the corners own when there are corners. */
  private def horizontalEdge(buffer: Buffer, area: Rect, y: Int, glyph: String, corners: Boolean): Unit =
    val from = if corners && borders.hasAny(Borders.Left) then area.x + 1 else area.x
    val to   = if corners && borders.hasAny(Borders.Right) then area.right - 1 else area.right
    var x    = from
    while x < to do
      put(buffer, x, y, glyph)
      x += 1

  /** One vertical run, minus the two cells the corners own when there are corners. */
  private def verticalEdge(buffer: Buffer, area: Rect, x: Int, glyph: String, corners: Boolean): Unit =
    val from = if corners && borders.hasAny(Borders.Top) then area.y + 1 else area.y
    val to   = if corners && borders.hasAny(Borders.Bottom) then area.bottom - 1 else area.bottom
    var y    = from
    while y < to do
      put(buffer, x, y, glyph)
      y += 1

  private def corner(buffer: Buffer, x: Int, y: Int, glyph: String, first: Borders, second: Borders): Unit =
    if borders.hasAny(first) && borders.hasAny(second) then put(buffer, x, y, glyph)

  /** Writes one border glyph, joining it to whatever box-drawing glyph is already there when asked to.
    *
    * Under the default [[MergeStrategy.Replace]] this is a plain `buffer.set` and costs nothing extra; under `Exact` or
    * `Fuzzy` the cell underneath is read first and the two glyphs are combined, which is what turns the seam between
    * two touching panels into `┬`/`┴` instead of a doubled corner. Titles do not come through here — they are drawn by
    * `LineRenderer` afterwards and must overwrite the border they sit on.
    */
  private def put(buffer: Buffer, x: Int, y: Int, glyph: String): Unit =
    val resolved =
      if mergeBorders == MergeStrategy.Replace then glyph
      else BorderMerge.merge(buffer.get(x, y).symbol, glyph, mergeBorders)
    buffer.set(x, y, Cell(resolved, edgeStyle))

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
    val _      = LineRenderer.render(buffer, startX, y, line, available - (startX - area.x - insetLeft), captionStyle)

  /** The style the border glyphs and titles are drawn in: the whole-area [[style]] with [[borderStyle]] layered on top.
    *
    * Layering rather than replacing is what keeps a panel's background continuous. If the border were drawn in
    * `borderStyle` alone, a block given a blue background and a bold border would paint the frame with no background at
    * all and leave a one-cell hole around the panel; with the layering, `borderStyle` only has to say what is
    * *different* about the border.
    */
  private def edgeStyle: Style = style.patch(borderStyle)

  /** The style the titles are drawn in: the border's own style with [[titleStyle]] layered on top.
    *
    * Titles start from the border style rather than from the block's fill because a title is written *into* the border
    * row and has to sit on the same background as the frame around it. `titleStyle` then says only what is different
    * about the caption — bold for a panel name, dim for a status — without having to restate the colours of the frame,
    * and a block that leaves it at `Style.Default` draws its titles exactly as it did before the field existed. A
    * [[io.worxbend.tui.core.Span]] inside the title line still layers over this, so one word of a caption can be
    * coloured without touching the rest.
    */
  private def captionStyle: Style = edgeStyle.patch(titleStyle)

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
