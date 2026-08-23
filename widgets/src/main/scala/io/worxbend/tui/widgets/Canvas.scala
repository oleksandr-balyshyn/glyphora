package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Widget}

/** How many drawable sub-pixels one terminal cell contributes to a [[Canvas]]. */
enum CanvasResolution:
  /** One marker glyph per hit cell (the coarsest, works everywhere). */
  case Cell

  /** 1×2 sub-pixels per cell via half-block glyphs (`▀`, `▄`, `█`). */
  case HalfBlock

  /** 2×4 sub-pixels per cell via braille patterns — the smoothest lines. */
  case Braille

/** A free-form drawing surface: shapes describe themselves in a world coordinate system (`xBounds` right-ward,
  * `yBounds` up-ward) and the canvas maps them onto its cell grid — at cell, half-block, or braille resolution.
  *
  * `marker` is read only at [[CanvasResolution.Cell]], where there is one dot per cell; a marker that is not exactly
  * one column wide is replaced by [[SubCell.FallbackMarker]] rather than allowed to smear into the next cell.
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
