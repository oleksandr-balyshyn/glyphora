package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style}

/** The geometry every column chart in this module shares.
  *
  * [[BarChart]] and [[StackedBarChart]] differ only in what they paint inside a column; where the columns are, how tall
  * the plot area is, and where a label goes underneath are the same arithmetic in both. It lives here for the same
  * reason [[BlockLadder]] owns the shared fill: a correction to the clipping rule below has to land in one place to be
  * worth anything.
  *
  * These are pure functions and hold no state; a caller runs them on the render thread and inherits no constraint of
  * their own.
  */
private[widgets] object ColumnChart:

  /** The rows available for the bars themselves — the whole area, less one row when labels are drawn under them. */
  def chartHeight(area: Rect, showLabels: Boolean): Int =
    if showLabels then area.height - 1 else area.height

  /** Whether labels are worth a row: there has to be a row to spare and at least one non-empty label to put in it. */
  def showLabels(area: Rect, labels: Seq[String]): Boolean =
    area.height >= 2 && labels.exists(_.nonEmpty)

  /** Columns from one bar's left edge to the next bar's.
    *
    * At least one, because a negative `barGap` can otherwise walk each bar left of the one before it. That matters
    * beyond a cosmetic overlap: `Buffer.set` clips to the buffer, not to the widget's `Rect`, so a bar at a negative
    * offset paints straight over whatever widget owns the columns to the left.
    */
  def stride(barWidth: Int, barGap: Int): Int =
    math.max(1, barWidth + barGap)

  /** The left edge of bar number `index`. */
  def barLeftAt(area: Rect, index: Int, stride: Int): Int =
    area.x + index * stride

  /** Whether the bar starting at `barLeft` lies wholly inside `area` — a partly-visible bar is dropped rather than
    * clipped, so a chart never shows a bar whose height cannot be read against its neighbours.
    */
  def fits(area: Rect, barLeft: Int, barWidth: Int): Boolean =
    barLeft >= area.x && barLeft + barWidth <= area.right

  /** Draws `label` centred under a bar, on the area's last row, truncated to `barWidth` columns.
    *
    * Truncation goes through [[io.worxbend.tui.core.CharWidth]], so a label of wide (CJK) or combining characters is
    * measured in columns rather than in code points and cannot overrun its bar.
    */
  def drawCentredLabel(buffer: Buffer, area: Rect, barLeft: Int, barWidth: Int, label: String, style: Style): Unit =
    val fitted = CharWidth.substringByWidth(label, barWidth)
    val startX = Alignment.Center.originAt(barLeft, barWidth, CharWidth.of(fitted))
    buffer.setString(startX, area.bottom - 1, fitted, style)
