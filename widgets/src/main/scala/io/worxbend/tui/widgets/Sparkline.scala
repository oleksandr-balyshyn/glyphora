package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style, Widget}

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
  */
final case class Sparkline(
    data: Seq[Long],
    max: Option[Long] = None,
    direction: SparkDirection = SparkDirection.LeftToRight,
    style: Style = Style.Default,
    barSet: BarSet = BarSet.Eighths,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty && data.nonEmpty then
      val ceiling = math.max(1L, max.getOrElse(data.max))
      val visible = direction match
        case SparkDirection.LeftToRight => data.take(area.width)
        case SparkDirection.RightToLeft => data.takeRight(area.width)
      // A series shorter than the area still hugs the edge it is anchored to: LeftToRight starts at column 0,
      // RightToLeft is pushed right so its last point lands in the last column.
      val offset  = direction match
        case SparkDirection.LeftToRight => 0
        case SparkDirection.RightToLeft => area.width - visible.size
      visible.zipWithIndex.foreach { (value, column) =>
        // one terminal column per data point, filled over the full area height
        BlockLadder.fillColumn(
          buffer,
          x = area.x + offset + column,
          columns = 1,
          bottom = area.bottom - 1,
          top = area.y,
          value = value,
          ceiling = ceiling,
          style = style,
          set = barSet,
        )
      }
