package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style, Widget}

/** Paints world-coordinate points into terminal cells for one [[Canvas]] render. */
final class Painter private[widgets] (
    area: Rect,
    xBounds: (Double, Double),
    yBounds: (Double, Double),
    buffer: Buffer,
    marker: String,
):

  /** Marks the cell containing the world-coordinate point; points outside the bounds are dropped. The y axis
    * points up (world), while rows grow down (terminal) — the mapping flips it.
    */
  def paint(x: Double, y: Double, style: Style): Unit =
    val (xMin, xMax) = xBounds
    val (yMin, yMax) = yBounds
    if x >= xMin && x <= xMax && y >= yMin && y <= yMax && xMax > xMin && yMax > yMin then
      val column = ((x - xMin) / (xMax - xMin) * (area.width - 1)).round.toInt
      val row = ((yMax - y) / (yMax - yMin) * (area.height - 1)).round.toInt
      buffer.set(area.x + column, area.y + row, Cell(marker, style))

/** Something drawable on a [[Canvas]] in world coordinates. */
trait Shape:
  def draw(painter: Painter): Unit

object Shape:

  final case class Points(points: Seq[(Double, Double)], style: Style = Style.Default) extends Shape:
    def draw(painter: Painter): Unit =
      points.foreach((x, y) => painter.paint(x, y, style))

  /** A straight segment, painted by parametric stepping (resolution-independent, no Bresenham needed at
    * terminal-cell densities).
    */
  final case class SegmentShape(
      x1: Double,
      y1: Double,
      x2: Double,
      y2: Double,
      style: Style = Style.Default,
  ) extends Shape:
    def draw(painter: Painter): Unit =
      val steps = math.max(1, math.max(math.abs(x2 - x1), math.abs(y2 - y1)).ceil.toInt * 2)
      (0 to steps).foreach { i =>
        val t = i.toDouble / steps
        painter.paint(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, style)
      }

  /** Consecutive points joined by segments — what a line chart plots. */
  final case class Polyline(points: Seq[(Double, Double)], style: Style = Style.Default) extends Shape:
    def draw(painter: Painter): Unit =
      points.lazyZip(points.drop(1)).foreach { case ((x1, y1), (x2, y2)) =>
        SegmentShape(x1, y1, x2, y2, style).draw(painter)
      }

  final case class RectangleShape(
      x: Double,
      y: Double,
      width: Double,
      height: Double,
      style: Style = Style.Default,
  ) extends Shape:
    def draw(painter: Painter): Unit =
      Seq(
        SegmentShape(x, y, x + width, y, style),
        SegmentShape(x, y + height, x + width, y + height, style),
        SegmentShape(x, y, x, y + height, style),
        SegmentShape(x + width, y, x + width, y + height, style),
      ).foreach(_.draw(painter))

  final case class CircleShape(
      centerX: Double,
      centerY: Double,
      radius: Double,
      style: Style = Style.Default,
  ) extends Shape:
    def draw(painter: Painter): Unit =
      val steps = math.max(8, (radius * 16).toInt)
      (0 until steps).foreach { i =>
        val angle = 2 * math.Pi * i / steps
        painter.paint(centerX + radius * math.cos(angle), centerY + radius * math.sin(angle), style)
      }

/** A free-form drawing surface: shapes describe themselves in a world coordinate system (`xBounds` right-ward,
  * `yBounds` up-ward) and the canvas maps them onto its cell grid with `marker`.
  */
final case class Canvas(
    xBounds: (Double, Double),
    yBounds: (Double, Double),
    shapes: Seq[Shape],
    marker: String = "•",
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val painter = Painter(area, xBounds, yBounds, buffer, marker)
      shapes.foreach(_.draw(painter))
