package io.worxbend.tui.widgets

import io.worxbend.tui.core.Style

/** Something drawable on a [[Canvas]] in world coordinates. */
trait Shape:
  def draw(painter: Painter): Unit

object Shape:

  final case class Points(points: Seq[(Double, Double)], style: Style = Style.Default) extends Shape:
    def draw(painter: Painter): Unit =
      points.foreach((x, y) => painter.paint(x, y, style))

  /** A straight segment, painted by parametric stepping (resolution-independent, no Bresenham needed at terminal-cell
    * densities).
    */
  final case class SegmentShape(
      x1: Double,
      y1: Double,
      x2: Double,
      y2: Double,
      style: Style = Style.Default,
  ) extends Shape:
    def draw(painter: Painter): Unit =
      val steps = math.max(1, math.max(math.abs(x2 - x1), math.abs(y2 - y1)).ceil.toInt * 4)
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
      val steps = math.max(8, (radius * 32).toInt)
      (0 until steps).foreach { i =>
        val angle = 2 * math.Pi * i / steps
        painter.paint(centerX + radius * math.cos(angle), centerY + radius * math.sin(angle), style)
      }
