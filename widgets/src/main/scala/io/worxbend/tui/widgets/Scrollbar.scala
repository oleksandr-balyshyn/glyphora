package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Direction, Rect, Style, Widget}

/** A scrollbar strip: a vertical bar down one of the area's side edges, or a horizontal bar along its top or bottom
  * edge. Which of the two `side` picks; by default it is the conventional right edge or bottom edge.
  *
  * The thumb's size is proportional to how much of the content the viewport covers; when the content fits entirely,
  * only the track is drawn.
  *
  * Stateless on purpose. In this toolkit a `StatefulWidget` is a widget whose *render* adjusts caller-owned state — a
  * list scrolling itself to keep its selection visible, for instance. A scrollbar adjusts nothing: where the thumb goes
  * is a pure function of `contentLength`, `position` and the area it is given, so both numbers are passed in as
  * ordinary parameters and the caller keeps whatever state it already had.
  *
  * @param contentLength
  *   the full extent of the content being scrolled, in rows (vertical) or columns (horizontal)
  * @param position
  *   how far into the content the viewport starts, in the same units; clamped, so an out-of-range value pins the thumb
  *   to an end rather than drawing it off the track
  * @param viewportLength
  *   how much of the content the reader can actually see, in the same units. `None` — the default — means "as much as
  *   the bar itself is long", which is right whenever the strip runs the full height (or width) of the content it
  *   describes. Pass a value when it does not: a bar drawn beside a bordered pane covers two rows more than the pane
  *   shows, and a bar sharing its column with a header covers one row more. Getting this wrong makes the thumb the
  *   wrong length and stops it reaching the end of the track.
  * @param side
  *   which edge of the area the strip lands on, read along its own axis: `Far` — the default — is the right edge of a
  *   vertical bar and the bottom edge of a horizontal one, `Near` the left edge and the top edge
  * @param capStyle
  *   the style of the two arrow caps, when there are any
  * @param beginSymbol
  *   an arrow cap drawn in the strip's first cell — the top of a vertical bar, the left end of a horizontal one. Each
  *   cap takes a cell away from the track, so a 10-row bar with both caps places its thumb in 8 rows. `None`, the
  *   default, means no cap and a whole strip of track, which is what this widget has always drawn. [[ScrollbarSymbols]]
  *   collects the conventional glyph sets, and [[Scrollbar.withSymbols]] builds a bar from one of them.
  * @param endSymbol
  *   an arrow cap drawn in the strip's last cell, or `None`
  */
final case class Scrollbar(
    contentLength: Int,
    position: Int = 0,
    orientation: Direction = Direction.Vertical,
    style: Style = Style.Default,
    thumbStyle: Style = Style.Default,
    trackSymbol: String = "│",
    thumbSymbol: String = "█",
    viewportLength: Option[Int] = None,
    side: ScrollbarSide = ScrollbarSide.Far,
    capStyle: Style = Style.Default,
    beginSymbol: Option[String] = None,
    endSymbol: Option[String] = None,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      // `extent` is the whole strip; the track is what is left of it once the arrow caps have taken their cells
      val extent                                               = orientation match
        case Direction.Vertical   => area.height
        case Direction.Horizontal => area.width
      // a cap is measured, not counted: a double-width glyph such as an emoji arrow takes two cells of the strip, and
      // starting the track one cell later would let the track's first character land on the cap's right half
      val trackStart                                           = beginSymbol.fold(0)(capCells)
      val endCells                                             = endSymbol.fold(0)(capCells)
      val trackLength                                          = math.max(0, extent - trackStart - endCells)
      val thumb                                                = thumbRange(trackLength, visibleLength(trackLength))
      // the lane the strip occupies: a column for a vertical bar, a row for a horizontal one. Both are computed
      // because each orientation reads only its own, and an unused Int costs nothing.
      val column                                               = side match
        case ScrollbarSide.Near => area.x
        case ScrollbarSide.Far  => area.right - 1
      val row                                                  = side match
        case ScrollbarSide.Near => area.y
        case ScrollbarSide.Far  => area.bottom - 1
      // paint one cell `at` cells along the strip, ignoring anything that would fall outside it
      def put(at: Int, symbol: String, cellStyle: Style): Unit =
        if at >= 0 && at < extent then
          orientation match
            case Direction.Vertical   => buffer.set(column, area.y + at, Cell(symbol, cellStyle))
            case Direction.Horizontal => buffer.set(area.x + at, row, Cell(symbol, cellStyle))
      // the end cap goes down first so that on a one-cell strip, where both caps want the same cell, the begin cap
      // is the one left visible rather than whichever happened to be written last
      endSymbol.foreach(symbol => put(extent - capCells(symbol), symbol, capStyle))
      beginSymbol.foreach(symbol => put(0, symbol, capStyle))
      var along                                                = 0
      while along < trackLength do
        val inThumb = thumb.exists((start, size) => along >= start && along < start + size)
        put(trackStart + along, if inThumb then thumbSymbol else trackSymbol, if inThumb then thumbStyle else style)
        along += 1

  /** How many cells of the strip a cap glyph occupies: two for a double-width character, one for everything else. A
    * zero-width cluster — a lone combining mark, say — would otherwise reserve nothing and be painted over, so it is
    * charged one cell like any ordinary character.
    */
  private def capCells(symbol: String): Int =
    math.max(1, CharWidth.of(symbol))

  /** How much of the content is on screen: whatever the caller declared, or the length of the track when it declared
    * nothing. A declared value of zero or less would make the thumb arithmetic meaningless, so it is raised to one.
    */
  private def visibleLength(trackLength: Int): Int =
    viewportLength match
      case Some(declared) => math.max(1, declared)
      case None           => trackLength

  /** `(start, size)` of the thumb along the track, or `None` when the content fits the viewport.
    *
    * `trackLength` is how many cells the strip is drawn across; `visible` is how much of the content the reader can
    * see. The two are the same number unless the caller overrode `viewportLength`, and only `visible` decides whether
    * there is anything to scroll at all.
    */
  private def thumbRange(trackLength: Int, visible: Int): Option[(Int, Int)] =
    if contentLength <= visible || trackLength == 0 then None
    else
      val size            = math.max(1, math.min(trackLength, trackLength * visible / contentLength))
      val maxPosition     = contentLength - visible
      val clampedPosition = math.max(0, math.min(position, maxPosition))
      val start           = math.round(clampedPosition.toDouble / maxPosition * (trackLength - size)).toInt
      Some((start, size))

object Scrollbar:

  /** A scrollbar drawn with one of the named glyph sets, e.g. `Scrollbar.withSymbols(200, 40,
    * ScrollbarSymbols.DoubleVertical)`.
    *
    * This is the same widget the constructor builds; it exists so a caller picking a whole look does not have to unpack
    * the set's four fields into four arguments by hand.
    */
  def withSymbols(
      contentLength: Int,
      position: Int,
      symbols: ScrollbarSymbols,
      orientation: Direction = Direction.Vertical,
      viewportLength: Option[Int] = None,
      side: ScrollbarSide = ScrollbarSide.Far,
      style: Style = Style.Default,
      thumbStyle: Style = Style.Default,
      capStyle: Style = Style.Default,
  ): Scrollbar =
    Scrollbar(
      contentLength = contentLength,
      position = position,
      orientation = orientation,
      style = style,
      thumbStyle = thumbStyle,
      trackSymbol = symbols.track,
      thumbSymbol = symbols.thumb,
      viewportLength = viewportLength,
      side = side,
      capStyle = capStyle,
      beginSymbol = symbols.begin,
      endSymbol = symbols.end,
    )
