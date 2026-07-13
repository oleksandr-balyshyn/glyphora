package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style, Widget}

enum GraphType:
  case Line, Scatter

/** One plotted series: points in world coordinates, drawn as a connected polyline or scattered markers. */
final case class Dataset(
    name: String,
    points: Seq[(Double, Double)],
    style: Style = Style.Default,
    graphType: GraphType = GraphType.Line,
)

/** An x/y chart with drawn axes; the plot region is a [[Canvas]] over the datasets' shapes. */
final case class Chart(
    datasets: Seq[Dataset],
    xBounds: (Double, Double),
    yBounds: (Double, Double),
    axisStyle: Style = Style.Default,
    marker: String = "•",
    resolution: CanvasResolution = CanvasResolution.Cell,
    showLabels: Boolean = false,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if area.width >= 3 && area.height >= 3 then
      drawAxes(area, buffer)
      val plotArea = Rect(area.x + 1, area.y, area.width - 1, area.height - 1)
      val shapes   = datasets.map { dataset =>
        dataset.graphType match
          case GraphType.Line    => Shape.Polyline(dataset.points, dataset.style)
          case GraphType.Scatter => Shape.Points(dataset.points, dataset.style)
      }
      Canvas(xBounds, yBounds, shapes, marker, resolution).render(plotArea, buffer)
      if showLabels then
        buffer.setString(area.x + 1, area.y, formatBound(yBounds._2), axisStyle)
        buffer.setString(area.x + 1, area.bottom - 2, formatBound(yBounds._1), axisStyle)

  private def formatBound(value: Double): String =
    if value == value.floor && math.abs(value) < 1e9 then value.toLong.toString else f"$value%.1f"

  private def drawAxes(area: Rect, buffer: Buffer): Unit =
    var y = area.y
    while y < area.bottom - 1 do
      buffer.set(area.x, y, Cell("│", axisStyle))
      y += 1
    var x = area.x + 1
    while x < area.right do
      buffer.set(x, area.bottom - 1, Cell("─", axisStyle))
      x += 1
    buffer.set(area.x, area.bottom - 1, Cell("└", axisStyle))
