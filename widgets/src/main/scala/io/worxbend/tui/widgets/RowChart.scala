package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style}

/** The geometry a bar chart drawn sideways shares, the mirror image of [[ColumnChart]].
  *
  * A horizontal bar chart asks the same four questions an upright one does — where does bar number `n` start, does it
  * fit, how much room do the labels need, where does a label go — with rows and columns swapped. Keeping the answers
  * here rather than inline in one branch of [[BarChart]] is what keeps the two directions symmetrical: a correction to
  * the clipping rule lands once and applies to both.
  *
  * One deliberate difference from ratatui, whose horizontal bar chart writes each label *over* its bar: here the labels
  * get a reserved gutter down the left edge instead. A [[io.worxbend.tui.core.Buffer]] cell holds one style, so text
  * written over a filled bar would take the bar's own colours and could be unreadable against them. Reserving a strip
  * is also what [[ColumnChart]] already does for the row of labels under an upright chart.
  *
  * These are pure functions and hold no state; a caller runs them on the render thread and inherits no constraint of
  * their own.
  */
private[widgets] object RowChart:

  /** Whether labels are worth a gutter: there has to be room for a label plus a bar, and at least one non-empty label.
    */
  def showLabels(area: Rect, labels: Seq[String]): Boolean =
    area.width >= 4 && labels.exists(_.nonEmpty)

  /** Columns reserved down the left edge for the labels: the widest label plus one blank column, but never more than
    * half the area, so the bars keep at least as much room as the names.
    */
  def labelGutter(area: Rect, labels: Seq[String], showLabels: Boolean): Int =
    if !showLabels then 0
    else math.min(labels.map(CharWidth.of).maxOption.getOrElse(0) + 1, area.width / 2)

  /** Rows from one bar's top edge to the next bar's.
    *
    * At least one, because a negative `barGap` can otherwise walk each bar above the one before it — and `Buffer.set`
    * clips to the buffer rather than to the widget's `Rect`, so a bar at a negative offset paints over whatever widget
    * owns the rows above.
    */
  def stride(barHeight: Int, barGap: Int): Int =
    math.max(1, barHeight + barGap)

  /** The top edge of bar number `index`. */
  def barTopAt(area: Rect, index: Int, stride: Int): Int =
    area.y + index * stride

  /** Whether the bar starting at `barTop` lies wholly inside `area` — a partly-visible bar is dropped rather than
    * clipped, the same rule [[ColumnChart.fits]] applies to an upright chart, so a reader never compares a bar against
    * a neighbour that is only partly drawn.
    */
  def fits(area: Rect, barTop: Int, barHeight: Int): Boolean =
    barTop >= area.y && barTop + barHeight <= area.bottom

  /** Draws `label` right-aligned in the gutter, on the bar's first row, truncated to the gutter's width.
    *
    * One column of the gutter is left blank between the label and the bar, so a full-width name does not run into the
    * bar it belongs to. Truncation goes through [[io.worxbend.tui.core.CharWidth]], so a name of wide (CJK) or
    * combining characters is measured in columns rather than in code points.
    */
  def drawGutterLabel(buffer: Buffer, area: Rect, barTop: Int, gutter: Int, label: String, style: Style): Unit =
    val room   = math.max(0, gutter - 1)
    val fitted = CharWidth.substringByWidth(label, room)
    buffer.setString(Alignment.Right.originAt(area.x, room, CharWidth.of(fitted)), barTop, fitted, style)
