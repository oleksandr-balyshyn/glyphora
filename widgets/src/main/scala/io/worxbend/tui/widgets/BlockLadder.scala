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
    *
    * Kept as a name of its own because it is the ladder every chart in this module used before the glyphs became
    * substitutable; [[BarSet.Eighths]] is the same eight glyphs wrapped in the record a caller now passes.
    */
  val Eighths: Vector[String] = BarSet.Eighths.eighths

  /** The eight block elements again, one eighth of a cell wider each, from `▏` (one eighth) to `█` (a full cell).
    *
    * The sideways twin of [[Eighths]]: `LeftEighths(n - 1)` is the glyph for `n` eighths of fill, growing rightwards
    * from the left of the cell — which is why a bar is walked from its left end towards its right.
    */
  val LeftEighths: Vector[String] = Vector("▏", "▎", "▍", "▌", "▋", "▊", "▉", "█")

  /** Paints one column of the chart: `value` measured against `ceiling`, filling upwards from row `bottom` and stopping
    * at row `top`.
    *
    * `columns` is how many terminal columns wide the bar is — one for a sparkline, `barWidth` for a bar chart — all
    * painted with the same glyph on each row, so a wide bar is a thin bar repeated sideways rather than a separate
    * algorithm. The rows `top..bottom` inclusive are the chart's own vertical extent, so the caller has already taken
    * off any space it reserves for labels; the height that the value is scaled against is that extent.
    *
    * `value` outside `0..ceiling` is clamped, so a caller may pass raw data against an explicit ceiling.
    *
    * `set` picks the glyphs. Its `empty` decides what happens to the cells above the fill: left untouched by default,
    * which is what lets a chart draw over an existing background, or painted with the set's empty glyph, which is what
    * gives each bar a visible track.
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
      set: BarSet = BarSet.Eighths,
  ): Unit =
    val height  = bottom - top + 1
    val clamped = math.max(0L, math.min(value, ceiling))
    var eighths = math.round(clamped.toDouble / ceiling * height * 8).toInt
    var y       = bottom
    while y >= top && eighths > 0 do
      val levelIndex = math.min(eighths, 8)
      paintRow(buffer, x, columns, y, set.eighths(levelIndex - 1), style)
      eighths -= levelIndex
      y -= 1
    set.empty.foreach { glyph =>
      // whatever is left of the column above the fill: from where the loop above stopped up to the chart's top row
      while y >= top do
        paintRow(buffer, x, columns, y, glyph, style)
        y -= 1
    }

  /** How many whole cells of an `extent`-cell track a bar covers, partial leading cell included.
    *
    * The fill loops above hand out eight eighths per cell and stop when they run out, so a bar that comes to two and a
    * half cells touches three of them. A caller that wants to write something *next to* a bar — a value label, say —
    * needs that same number to know where the bar ends, and computing it here rather than at the call site is what
    * stops the label and the bar disagreeing about where the boundary is.
    */
  def filledCells(value: Long, ceiling: Long, extent: Int): Int =
    val clamped = math.max(0L, math.min(value, ceiling))
    val eighths = math.round(clamped.toDouble / ceiling * extent * 8).toInt
    math.min(extent, (eighths + 7) / 8)

  /** Paints one horizontal bar: `value` measured against `ceiling`, filling rightwards from column `left` and stopping
    * at column `right`.
    *
    * The mirror image of [[fillColumn]], sharing its rounding rule so a value drawn sideways and the same value drawn
    * upright never disagree about how full a cell is. `rows` is how many terminal rows tall the bar is — one for a
    * single-row bar, more for a thick one — every row painted with the same glyph, so a thick bar is a thin bar
    * repeated downwards rather than a separate algorithm.
    *
    * `value` outside `0..ceiling` is clamped.
    *
    * `set` picks the glyphs, exactly as it does for [[fillColumn]], and its `empty` decides what happens to the cells
    * past the fill in the same way: left untouched by default, painted with the empty glyph when the set names one. The
    * set's ladder is written for an upright bar, so it is turned on its side by [[sideways]] before it is used here — a
    * set that says "fill a cell four eighths" means the *left* four eighths of the cell in this direction.
    *
    * Renders into `buffer` and nothing else, so it carries the same thread constraint as any
    * [[io.worxbend.tui.core.Widget]]: call it from the render thread that owns the buffer.
    */
  def fillRow(
      buffer: Buffer,
      y: Int,
      rows: Int,
      left: Int,
      right: Int,
      value: Long,
      ceiling: Long,
      style: Style,
      set: BarSet = BarSet.Eighths,
  ): Unit =
    val width   = right - left + 1
    val clamped = math.max(0L, math.min(value, ceiling))
    val ladder  = sideways(set.eighths)
    var eighths = math.round(clamped.toDouble / ceiling * width * 8).toInt
    var x       = left
    while x <= right && eighths > 0 do
      val levelIndex = math.min(eighths, 8)
      paintColumn(buffer, x, rows, y, ladder(levelIndex - 1), style)
      eighths -= levelIndex
      x += 1
    set.empty.foreach { glyph =>
      // whatever is left of the track past the fill: from where the loop above stopped up to the bar's last column
      while x <= right do
        paintColumn(buffer, x, rows, y, glyph, style)
        x += 1
    }

  /** The same ladder drawn sideways: each upward-growing block element swapped for the rightward-growing one that fills
    * the same fraction of a cell, and every other glyph left exactly as it is.
    *
    * A [[BarSet]] carries one ladder, and it is written the way a column chart reads it — `▄` means "the bottom half of
    * this cell". Drawn along a row that same fraction is the *left* half, `▌`. Glyphs with no such twin, such as the
    * `#` of [[BarSet.Ascii]] or a caller's own [[BarSet.uniform]] marker, look the same in both directions and so pass
    * straight through; that is why the substitution is a lookup rather than a second ladder on the set, which would
    * make every caller define a sideways spelling of a glyph that does not have one.
    */
  private def sideways(eighths: Vector[String]): Vector[String] =
    eighths.map(glyph => Sideways.getOrElse(glyph, glyph))

  private val Sideways: Map[String, String] = Eighths.zip(LeftEighths).toMap

  /** Writes one glyph down the `rows` cells a bar occupies in column `x`. A thick bar is a thin bar repeated downwards,
    * so there is one loop for it rather than a second algorithm.
    */
  private def paintColumn(buffer: Buffer, x: Int, rows: Int, y: Int, glyph: String, style: Style): Unit =
    var row = 0
    while row < rows do
      buffer.set(x, y + row, Cell(glyph, style))
      row += 1

  /** Writes one glyph across the `columns` cells a bar occupies on row `y`. A wide bar is a thin bar repeated sideways,
    * so there is one loop for it rather than a second algorithm.
    */
  private def paintRow(buffer: Buffer, x: Int, columns: Int, y: Int, glyph: String, style: Style): Unit =
    var column = 0
    while column < columns do
      buffer.set(x + column, y, Cell(glyph, style))
      column += 1
