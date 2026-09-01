package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style, Widget}

/** Which end of the area the series is anchored to when it has more points than the area has columns.
  *
  * [[SparkDirection.LeftToRight]] keeps the *oldest* points: the first data point sits in the leftmost column and
  * anything past the right edge is dropped. That is right for a fixed series you want to read from its start.
  *
  * [[SparkDirection.RightToLeft]] keeps the *newest* points: the last data point sits in the rightmost column and the
  * oldest history scrolls off the left. That is what a live metric wants, because the column the reader watches — the
  * latest reading — never moves.
  */
enum SparkDirection:
  case LeftToRight, RightToLeft

/** A compact bar-per-column chart using the eight block-element glyphs, scaled over the full area height.
  *
  * Each data point maps to one column. `direction` decides which end of the series survives when there are more points
  * than columns (see [[SparkDirection]]); the default, `LeftToRight`, keeps the oldest points and clips the newest off
  * the right. `max` overrides the scale ceiling (defaults to the data's maximum).
  *
  * The ceiling is taken from the *whole* series, not from the columns that happen to be visible, so a `RightToLeft`
  * trace does not silently rescale itself as points leave the left edge.
  *
  * `barSet` swaps the glyphs the columns are drawn from — [[BarSet.Ascii]] for a terminal with no block elements,
  * [[BarSet.Solid]] for blunt whole-cell columns, or a set of your own. Its `empty` glyph, if it has one, also fills
  * the cells above each column, which is how the sparkline gets a visible track instead of showing whatever was already
  * on the screen behind it.
  *
  * `absentColumns` names the positions in `data` that carry no reading at all — a sensor that was offline, a minute the
  * metric was not collected. A column named there is drawn as [[absentSymbol]] rather than as a bar, so a hole in the
  * series reads as a hole. Without it the only way to plot a missing reading is to pass a zero, and a zero is a
  * measurement: "the queue was empty" and "nobody looked at the queue" would draw the same picture. Build the set with
  * [[Sparkline.ofReadings]] rather than counting indices by hand.
  *
  * @param absentColumns
  *   indices into `data` — not into the visible columns — that hold no reading. An index outside `data` is ignored, and
  *   the value stored in `data` at an absent index is never read, so a caller may leave any placeholder there.
  * @param absentSymbol
  *   what to draw down an absent column. `None`, the default, leaves those cells exactly as they were, which shows the
  *   gap as a hole in the trace and lets the sparkline be drawn over an existing background. A glyph such as `"·"`
  *   marks the gap explicitly, which is the clearer choice when the trace has a visible track behind it.
  * @param absentStyle
  *   the style of that glyph, layered over `style`
  */
final case class Sparkline(
    data: Seq[Long],
    max: Option[Long] = None,
    direction: SparkDirection = SparkDirection.LeftToRight,
    style: Style = Style.Default,
    barSet: BarSet = BarSet.Eighths,
    // Appended rather than placed in the layout-and-behaviour slot the widget conventions ask for: inserting a
    // parameter mid-list would silently change what every positional caller written against 0.12.0 means.
    absentColumns: Set[Int] = Set.empty,
    absentSymbol: Option[String] = None,
    absentStyle: Style = Style.Default,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty && data.nonEmpty then
      // The scale ignores absent points: whatever placeholder sits in `data` at an absent index is not a reading, and
      // letting it set the ceiling would rescale the whole trace around a number nobody measured.
      val readings = data.zipWithIndex.collect { case (value, index) if !absentColumns.contains(index) => value }
      val ceiling  = math.max(1L, max.getOrElse(if readings.isEmpty then 0L else readings.max))
      // Indices are carried alongside the values, because which points are absent is stated in terms of positions in
      // `data` and a `takeRight` would otherwise renumber them.
      val indexed  = data.zipWithIndex
      val visible  = direction match
        case SparkDirection.LeftToRight => indexed.take(area.width)
        case SparkDirection.RightToLeft => indexed.takeRight(area.width)
      // A series shorter than the area still hugs the edge it is anchored to: LeftToRight starts at column 0,
      // RightToLeft is pushed right so its last point lands in the last column.
      val offset   = direction match
        case SparkDirection.LeftToRight => 0
        case SparkDirection.RightToLeft => area.width - visible.size
      visible.zipWithIndex.foreach { case ((value, index), column) =>
        val x = area.x + offset + column
        if absentColumns.contains(index) then drawAbsent(buffer, x, area)
        else
          // one terminal column per data point, filled over the full area height
          BlockLadder.fillColumn(
            buffer,
            x = x,
            columns = 1,
            bottom = area.bottom - 1,
            top = area.y,
            value = value,
            ceiling = ceiling,
            style = style,
            set = barSet,
          )
      }

  /** Draws the whole of column `x` as the absent glyph, or leaves it untouched when there is none.
    *
    * The column is painted top to bottom rather than only where a bar would have reached, because there is no bar: the
    * point of the glyph is to say the reading is missing, and half a column of it would read as a value.
    */
  private def drawAbsent(buffer: Buffer, x: Int, area: Rect): Unit =
    absentSymbol.foreach { glyph =>
      var y = area.y
      while y < area.bottom do
        buffer.set(x, y, Cell(glyph, style.patch(absentStyle)))
        y += 1
    }

object Sparkline:

  /** A sparkline over readings that may be missing, which is the shape a series of samples usually arrives in.
    *
    * `None` marks a point with no reading: it is stored as a zero in `data` — a placeholder the widget never reads —
    * and its position is recorded in `absentColumns`, so the column is drawn as a gap rather than as a bar of height
    * zero. Working the indices out here rather than at the call site is the point: counting them by hand is the kind of
    * arithmetic that goes wrong quietly and plots the wrong column as missing.
    *
    * Every other parameter means exactly what it does on the constructor.
    */
  def ofReadings(
      readings: Seq[Option[Long]],
      max: Option[Long] = None,
      direction: SparkDirection = SparkDirection.LeftToRight,
      style: Style = Style.Default,
      barSet: BarSet = BarSet.Eighths,
      absentSymbol: Option[String] = None,
      absentStyle: Style = Style.Default,
  ): Sparkline =
    Sparkline(
      data = readings.map(_.getOrElse(0L)),
      max = max,
      direction = direction,
      style = style,
      barSet = barSet,
      absentColumns = readings.zipWithIndex.collect { case (None, index) => index }.toSet,
      absentSymbol = absentSymbol,
      absentStyle = absentStyle,
    )
