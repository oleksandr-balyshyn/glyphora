package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Style}

/** The bottom-up eighth-block ladder every column chart in this module draws with.
  *
  * One home for two things that used to be copied per chart: the eight glyphs, and the rounding rule that decides how
  * tall a column looks. The rounding matters more than it sounds — it is what makes a value just under a cell boundary
  * show as a partial block rather than as a whole one — and two copies of it means a change lands in one chart and
  * silently not in the other.
  */
private[widgets] object BlockLadder:

  /** The eight block elements, one eighth of a cell taller each, from `▁` (one eighth) to `█` (a full cell).
    *
    * Ordered so that `Eighths(n - 1)` is the glyph for `n` eighths of fill, with the fill growing upwards from the
    * bottom of the cell — which is why a column is walked from its bottom row towards its top.
    */
  val Eighths: Vector[String] = Vector("▁", "▂", "▃", "▄", "▅", "▆", "▇", "█")

  /** Paints one column of the chart: `value` measured against `ceiling`, filling upwards from row `bottom` and stopping
    * at row `top`.
    *
    * `columns` is how many terminal columns wide the bar is — one for a sparkline, `barWidth` for a bar chart — all
    * painted with the same glyph on each row, so a wide bar is a thin bar repeated sideways rather than a separate
    * algorithm. The rows `top..bottom` inclusive are the chart's own vertical extent, so the caller has already taken
    * off any space it reserves for labels; the height that the value is scaled against is that extent.
    *
    * `value` outside `0..ceiling` is clamped, so a caller may pass raw data against an explicit ceiling. Cells above
    * the fill are left untouched rather than blanked, which is what lets a chart draw over an existing background.
    *
    * Renders into `buffer` and nothing else, so it carries the same thread constraint as any
    * [[io.worxbend.tui.core.Widget]]: call it from the render thread that owns the buffer.
    */
  def fillColumn(
      buffer: Buffer,
      x: Int,
      columns: Int,
      bottom: Int,
      top: Int,
      value: Long,
      ceiling: Long,
      style: Style,
  ): Unit =
    val height  = bottom - top + 1
    val clamped = math.max(0L, math.min(value, ceiling))
    var eighths = math.round(clamped.toDouble / ceiling * height * 8).toInt
    var y       = bottom
    while y >= top && eighths > 0 do
      val levelIndex = math.min(eighths, 8)
      var column     = 0
      while column < columns do
        buffer.set(x + column, y, Cell(Eighths(levelIndex - 1), style))
        column += 1
      eighths -= levelIndex
      y -= 1
