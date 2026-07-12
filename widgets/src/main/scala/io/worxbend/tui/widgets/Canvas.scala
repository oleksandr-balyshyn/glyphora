package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style, Widget}

/** How many drawable sub-pixels one terminal cell contributes to a [[Canvas]]. */
enum CanvasResolution:
  /** One marker glyph per hit cell (the coarsest, works everywhere). */
  case Cell

  /** 1×2 sub-pixels per cell via half-block glyphs (`▀`, `▄`, `█`). */
  case HalfBlock

  /** 2×4 sub-pixels per cell via braille patterns — the smoothest lines. */
  case Braille

/** Paints world-coordinate points into terminal cells for one [[Canvas]] render, accumulating sub-pixel hits
  * and flushing them as glyphs.
  */
final class Painter private[widgets] (
    area: Rect,
    xBounds: (Double, Double),
    yBounds: (Double, Double),
    resolution: CanvasResolution,
    marker: String,
):

  private val (subWidth, subHeight) = resolution match
    case CanvasResolution.Cell      => (1, 1)
    case CanvasResolution.HalfBlock => (1, 2)
    case CanvasResolution.Braille   => (2, 4)

  private val gridWidth = area.width * subWidth
  private val gridHeight = area.height * subHeight
  private val masks = Array.fill(area.area)(0)
  private val styles = Array.fill(area.area)(Style.Default)

  /** Marks the sub-pixel containing the world-coordinate point; points outside the bounds are dropped. The
    * y axis points up (world), while rows grow down (terminal) — the mapping flips it.
    */
  def paint(x: Double, y: Double, style: Style): Unit =
    val (xMin, xMax) = xBounds
    val (yMin, yMax) = yBounds
    if x >= xMin && x <= xMax && y >= yMin && y <= yMax && xMax > xMin && yMax > yMin then
      val column = ((x - xMin) / (xMax - xMin) * (gridWidth - 1)).round.toInt
      val row = ((yMax - y) / (yMax - yMin) * (gridHeight - 1)).round.toInt
      val cellIndex = (row / subHeight) * area.width + (column / subWidth)
      masks(cellIndex) |= bitFor(column % subWidth, row % subHeight)
      styles(cellIndex) = style

  private[widgets] def flush(buffer: Buffer): Unit =
    var index = 0
    while index < masks.length do
      if masks(index) != 0 then
        val x = area.x + index % area.width
        val y = area.y + index / area.width
        buffer.set(x, y, Cell(glyphFor(masks(index)), styles(index)))
      index += 1

  private def bitFor(dx: Int, dy: Int): Int =
    resolution match
      case CanvasResolution.Cell      => 1
      case CanvasResolution.HalfBlock => if dy == 0 then 1 else 2
      case CanvasResolution.Braille   => Painter.BrailleBits(dy)(dx)

  private def glyphFor(mask: Int): String =
    resolution match
      case CanvasResolution.Cell => marker
      case CanvasResolution.HalfBlock =>
        mask match
          case 1 => "▀"
          case 2 => "▄"
          case _ => "█"
      case CanvasResolution.Braille => (0x2800 + mask).toChar.toString

private object Painter:
  /** Braille dot bit for sub-position `(dx, dy)`: dots 1–8 per the Unicode braille block layout. */
  private val BrailleBits: Vector[Vector[Int]] = Vector(
    Vector(0x01, 0x08),
    Vector(0x02, 0x10),
    Vector(0x04, 0x20),
    Vector(0x40, 0x80),
  )

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

/** A free-form drawing surface: shapes describe themselves in a world coordinate system (`xBounds` right-ward,
  * `yBounds` up-ward) and the canvas maps them onto its cell grid — at cell, half-block, or braille resolution.
  */
final case class Canvas(
    xBounds: (Double, Double),
    yBounds: (Double, Double),
    shapes: Seq[Shape],
    marker: String = "•",
    resolution: CanvasResolution = CanvasResolution.Cell,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val painter = Painter(area, xBounds, yBounds, resolution, marker)
      shapes.foreach(_.draw(painter))
      painter.flush(buffer)
