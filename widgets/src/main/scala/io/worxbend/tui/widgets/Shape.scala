package io.worxbend.tui.widgets

import io.worxbend.tui.core.Style

/** Something drawable on a [[Canvas]] in world coordinates. */
trait Shape:
  def draw(painter: Painter): Unit

object Shape:

  final case class Points(points: Seq[(Double, Double)], style: Style = Style.Default) extends Shape:
    def draw(painter: Painter): Unit =
      points.foreach((x, y) => painter.paint(x, y, style))

  /** A straight segment between two world points.
    *
    * The drawing is [[Painter.paintSegment]]'s, because only the painter knows the canvas bounds and how many dots a
    * world unit is worth. The segment is clipped to those bounds first — so a line arriving from off-screen still draws
    * a solid run up to the edge — and then stepped once per dot, so it is continuous whether the world spans `0.0` to
    * `1.0` or `0.0` to a billion.
    *
    * A segment with any non-finite endpoint is not drawn: its direction is undefined.
    */
  final case class SegmentShape(
      x1: Double,
      y1: Double,
      x2: Double,
      y2: Double,
      style: Style = Style.Default,
  ) extends Shape:
    def draw(painter: Painter): Unit = painter.paintSegment(x1, y1, x2, y2, style)

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

  /** A segment plus the area between it and the horizontal line `baselineY` — one span of an area plot.
    *
    * `baselineY` is a world y like the others, so it goes with the coordinates rather than with the styling, and it
    * does not have to be inside the canvas bounds: a baseline below the visible range fills to the bottom edge. The
    * fill is a scanline on the dot grid, done by [[Painter.paintFilledSegment]], so it has neither the stripes a
    * too-coarse step leaves nor the wasted repainting a too-fine one causes.
    */
  final case class FilledLine(
      x1: Double,
      y1: Double,
      x2: Double,
      y2: Double,
      baselineY: Double,
      style: Style = Style.Default,
  ) extends Shape:
    def draw(painter: Painter): Unit = painter.paintFilledSegment(x1, y1, x2, y2, baselineY, style)

  /** Consecutive points joined and filled down to `baselineY` — what an area chart plots.
    *
    * The area-chart counterpart of [[Polyline]], and built the same way: one [[FilledLine]] per adjacent pair. A single
    * point on its own fills nothing, because a span needs two ends.
    */
  final case class FilledPolyline(
      points: Seq[(Double, Double)],
      baselineY: Double,
      style: Style = Style.Default,
  ) extends Shape:
    def draw(painter: Painter): Unit =
      points.lazyZip(points.drop(1)).foreach { case ((x1, y1), (x2, y2)) =>
        FilledLine(x1, y1, x2, y2, baselineY, style).draw(painter)
      }

  /** A solid axis-aligned rectangle — a highlighted band, a selection box, a heat cell.
    *
    * A separate shape rather than a `filled` flag on [[RectangleShape]], for two reasons. A boolean parameter tells the
    * reader of a call site nothing (`RectangleShape(0, 0, 4, 2, true)` is unreadable), and adding one in the middle of
    * an existing constructor would silently rebind every positional `style` argument already written.
    *
    * Negative `width` or `height` describe the same rectangle read from the opposite corner, and fill the same area.
    */
  final case class FilledRectangle(
      x: Double,
      y: Double,
      width: Double,
      height: Double,
      style: Style = Style.Default,
  ) extends Shape:
    def draw(painter: Painter): Unit =
      val top    = math.max(y, y + height)
      val bottom = math.min(y, y + height)
      FilledLine(math.min(x, x + width), top, math.max(x, x + width), top, bottom, style).draw(painter)

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

  /** A circle outline, sampled at even angles around its circumference.
    *
    * Unlike a segment, a circle cannot be walked dot by dot with integer arithmetic here, so it is sampled — but how
    * *many* samples it needs is a question about the surface, not about the data. The count is taken from the
    * circumference measured in dots ([[Painter.dotsPerWorldUnit]]), so a circle of radius `0.4` on a canvas with bounds
    * of `0.0` to `1.0` and a circle of radius `400` on a canvas scaled a thousand times wider — the same circle, drawn
    * onto the same grid — get the same number of samples. Sizing the count off the world radius, as it used to be, drew
    * the first as an octagon and asked for tens of thousands of samples for the second.
    *
    * A circle whose centre or radius is not a finite number is not drawn at all.
    */
  final case class CircleShape(
      centerX: Double,
      centerY: Double,
      radius: Double,
      style: Style = Style.Default,
  ) extends Shape:
    def draw(painter: Painter): Unit =
      if centerX.isFinite && centerY.isFinite && radius.isFinite then
        val (dotsAcross, dotsDown) = painter.dotsPerWorldUnit
        val dotRadius              = math.abs(radius) * math.max(dotsAcross, dotsDown)
        val steps                  = sampleCount(2 * math.Pi * dotRadius * Oversample)
        (0 until steps).foreach { i =>
          val angle = 2 * math.Pi * i / steps
          painter.paint(centerX + radius * math.cos(angle), centerY + radius * math.sin(angle), style)
        }

  /** Two samples per dot of arc, so that neighbouring samples land on neighbouring dots rather than skipping one.
    *
    * Sampling a curve at exactly one point per dot of its length only works if the points space themselves perfectly
    * evenly over the dots, and around a circle they do not — near the top and bottom the x step is large and the y step
    * near zero. Doubling closes the gaps that rounding would otherwise leave.
    */
  private val Oversample = 2

  /** The smallest sample count worth drawing: below this a "circle" reads as a polygon whatever the surface. */
  private val MinimumSamples = 8

  /** Sample counts are capped as well as floored.
    *
    * A canvas is a few hundred dots across at most, so no honest curve needs more than this; the ceiling is there so
    * that a wildly scaled input asks for a bounded amount of work on the render thread instead of stalling it.
    */
  private val MaximumSamples = 1 << 14

  private def sampleCount(wanted: Double): Int =
    if !wanted.isFinite then MinimumSamples
    else math.max(MinimumSamples, math.min(MaximumSamples.toDouble, wanted.ceil)).toInt
