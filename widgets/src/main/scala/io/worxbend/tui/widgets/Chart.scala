package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Constraint, Rect, Style, Widget}

/** How a [[Dataset]]'s points are drawn: `Line` joins consecutive points with segments, `Scatter` plots them alone.
  *
  * `Line` follows the points in the order the dataset lists them — it does not sort by x, so an unsorted series draws
  * as a zig-zag rather than a function plot.
  */
enum GraphType:
  case Line, Scatter

/** One plotted series: points in world coordinates, drawn as a connected polyline or scattered markers. */
final case class Dataset(
    name: String,
    points: Seq[(Double, Double)],
    style: Style = Style.Default,
    graphType: GraphType = GraphType.Line,
)

/** An x/y chart with drawn axes; the plot region is a [[Canvas]] over the datasets' shapes.
  *
  * With `showLabels` the two y bounds are printed in a gutter reserved to the *left* of the vertical axis, and the axis
  * moves right by the width of the widest of them. Before that gutter existed the labels were written at the first plot
  * column, so a four-digit bound painted over the leftmost points of every series; now the numbers and the data never
  * share a cell. `labelAlignment` places a label inside that gutter: `Right` (the default) presses it against the axis
  * line, `Left` against the frame, `Center` between the two.
  *
  * With `showLegend` the plot's top-right corner carries a key: one entry per dataset with a non-empty `name`, each
  * drawn in that dataset's own style, so several series in one plot can be told apart by more than colour alone. The
  * key is drawn over the plot, so it costs the data no space — but only while it stays small: `hiddenLegendConstraints`
  * is `(horizontal, vertical)` and the whole key is dropped unless it satisfies both. The default allows it a quarter
  * of the plot in either direction, so a chart that shrinks loses its key and keeps its data.
  *
  * @param labelAlignment
  *   by the widget parameter-order convention this is placement and would belong before `axisStyle`; it sits last
  *   because `Chart` is a published 0.12.0 signature and inserting a parameter in the middle would silently repoint
  *   every positional call site.
  */
final case class Chart(
    datasets: Seq[Dataset],
    xBounds: (Double, Double),
    yBounds: (Double, Double),
    axisStyle: Style = Style.Default,
    marker: String = "•",
    resolution: CanvasResolution = CanvasResolution.Cell,
    showLabels: Boolean = false,
    labelAlignment: Alignment = Alignment.Right,
    showLegend: Boolean = false,
    hiddenLegendConstraints: (Constraint, Constraint) = (Constraint.Ratio(1, 4), Constraint.Ratio(1, 4)),
    legendMarker: String = "■",
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if area.width >= 3 && area.height >= 3 then
      val labels   = if showLabels then Seq(formatBound(yBounds._2), formatBound(yBounds._1)) else Seq.empty
      val gutter   = labelGutter(area, labels)
      val axisX    = area.x + gutter
      drawAxes(area, axisX, buffer)
      val plotArea = Rect(axisX + 1, area.y, area.width - gutter - 1, area.height - 1)
      val shapes   = datasets.map { dataset =>
        dataset.graphType match
          case GraphType.Line    => Shape.Polyline(dataset.points, dataset.style)
          case GraphType.Scatter => Shape.Points(dataset.points, dataset.style)
      }
      Canvas(xBounds, yBounds, shapes, marker, resolution).render(plotArea, buffer)
      if gutter > 0 then
        drawLabel(buffer, area.x, gutter, area.y, labels.head)
        drawLabel(buffer, area.x, gutter, area.bottom - 2, labels.last)
      if showLegend then drawLegend(plotArea, buffer)

  /** Draws one right-aligned entry per named dataset, top-down in the plot area, each in that dataset's own style.
    *
    * Every [[Dataset]] already carries a `name`; without a key nothing ever showed it, so three series were three
    * indistinguishable colours. A dataset with an empty name gets no entry — that is how a caller keeps a series out of
    * the key.
    *
    * The whole key is dropped when it would be larger than `hiddenLegendConstraints` allows, so a chart in a pane too
    * small for both shows the data rather than the names. Dropping the key is deliberately all-or-nothing: half a key
    * says less than none, because a reader cannot tell which series the missing entries belonged to.
    */
  private def drawLegend(plotArea: Rect, buffer: Buffer): Unit =
    val named  = datasets.filter(_.name.nonEmpty)
    val labels = named.map(dataset => s"$legendMarker ${dataset.name}")
    val width  = LegendFit.width(labels, padding = 0)
    if LegendFit.fits(plotArea, width, named.size, hiddenLegendConstraints) then
      val x = plotArea.right - width
      named.zip(labels).zipWithIndex.foreach { case ((dataset, label), index) =>
        buffer.setString(x, plotArea.y + index, label, dataset.style)
      }

  /** Columns reserved left of the axis for the y-bound labels: the widest label, or none at all when reserving it would
    * leave fewer than two columns of plot.
    *
    * Answering zero rather than a squeezed gutter is what keeps a chart in a narrow pane a chart instead of a column of
    * numbers: in that case the labels are dropped and the whole width goes to the data.
    */
  private def labelGutter(area: Rect, labels: Seq[String]): Int =
    if labels.isEmpty then 0
    else
      val widest = labels.map(CharWidth.of).max
      if area.width - widest - 1 >= 2 then widest else 0

  private def drawLabel(buffer: Buffer, areaX: Int, gutter: Int, y: Int, label: String): Unit =
    buffer.setString(labelAlignment.originAt(areaX, gutter, CharWidth.of(label)), y, label, axisStyle)

  private def formatBound(value: Double): String =
    if value == value.floor && math.abs(value) < 1e9 then value.toLong.toString else f"$value%.1f"

  private def drawAxes(area: Rect, axisX: Int, buffer: Buffer): Unit =
    var y = area.y
    while y < area.bottom - 1 do
      buffer.set(axisX, y, Cell("│", axisStyle))
      y += 1
    var x = axisX + 1
    while x < area.right do
      buffer.set(x, area.bottom - 1, Cell("─", axisStyle))
      x += 1
    buffer.set(axisX, area.bottom - 1, Cell("└", axisStyle))
