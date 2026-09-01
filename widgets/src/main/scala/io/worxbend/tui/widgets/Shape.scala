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

  /** One upright bar per point: a segment from `baseline` up (or down) to the point's own y.
    *
    * This is what a bar-style chart plots. The bars are drawn in world coordinates like every other shape, so a point
    * below the baseline draws downwards without any special case.
    */
  final case class Bars(points: Seq[(Double, Double)], baseline: Double, style: Style = Style.Default) extends Shape:
    def draw(painter: Painter): Unit =
      points.foreach((x, y) => SegmentShape(x, baseline, x, y, style).draw(painter))

  /** The region between a polyline and a horizontal `baseline`, filled in.
    *
    * Each pair of consecutive points is walked in small steps, and at every step an upright segment is painted from the
    * baseline to the interpolated height. Stepping along the line rather than dropping one bar per point is what makes
    * the fill solid even when the series has far fewer points than the plot has columns: a series of three points would
    * otherwise draw as three lonely bars.
    */
  final case class AreaShape(points: Seq[(Double, Double)], baseline: Double, style: Style = Style.Default)
      extends Shape:
    def draw(painter: Painter): Unit =
      points.lazyZip(points.drop(1)).foreach { case ((x1, y1), (x2, y2)) =>
        val steps = math.max(1, math.max(math.abs(x2 - x1), math.abs(y2 - y1)).ceil.toInt * 4)
        (0 to steps).foreach { step =>
          val t = step.toDouble / steps
          SegmentShape(x1 + (x2 - x1) * t, baseline, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, style).draw(painter)
        }
      }
      // a single-point series has no pair to walk, so it still deserves its one bar
      if points.sizeIs == 1 then points.foreach((x, y) => SegmentShape(x, baseline, x, y, style).draw(painter))

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
