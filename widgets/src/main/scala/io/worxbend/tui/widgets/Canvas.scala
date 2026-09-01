package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Widget}

/** How many drawable sub-pixels one terminal cell contributes to a [[Canvas]].
  *
  * The cases trade resolution against how likely a terminal's font is to have the glyphs. Roughly, in descending order
  * of safety: [[Cell]] and [[HalfBlock]] work everywhere, [[Quadrant]] and [[Braille]] work almost everywhere,
  * [[Sextant]] needs a font from 2020 or later, and [[Octant]] one from 2024 or later. A missing glyph shows as the
  * terminal's replacement box, so pick the finest resolution you are willing to see fail.
  *
  * Resolution is not the only difference. Braille draws *sparse dots* with gaps between them, which reads as a line;
  * the block-drawing cases fill their whole sub-pixel, which reads as a solid area. For a filled chart the block cases
  * look better even at the same dot count, which is why [[Octant]] exists next to [[Braille]] at the identical 2×4.
  */
enum CanvasResolution:
  /** One marker glyph per hit cell (the coarsest, works everywhere). */
  case Cell

  /** 1×2 sub-pixels per cell via half-block glyphs (`▀`, `▄`, `█`). */
  case HalfBlock

  /** 2×2 sub-pixels per cell via quadrant blocks (`▘`, `▚`, `▟`, …) — four times the area of a cell, solid rather than
    * dotted, and in every font that has the half blocks.
    */
  case Quadrant

  /** 2×3 sub-pixels per cell via the sextant blocks of Unicode 13's Symbols for Legacy Computing. Half again the
    * vertical resolution of [[Quadrant]], at the cost of needing a font that covers `U+1FB00`–`U+1FB3B`.
    */
  case Sextant

  /** 2×4 sub-pixels per cell via braille patterns — the smoothest lines. */
  case Braille

  /** 2×4 sub-pixels per cell via the octant blocks of Unicode 16's Symbols for Legacy Computing Supplement.
    *
    * The same dot count as [[Braille]] but filled instead of dotted, so areas and thick strokes read as solid. It is
    * the newest of the six and the least widely supported: fonts published before 2024 will not have `U+1CD00`–
    * `U+1CDE5` and the terminal will draw replacement boxes.
    */
  case Octant

/** A free-form drawing surface: shapes describe themselves in a world coordinate system (`xBounds` right-ward,
  * `yBounds` up-ward) and the canvas maps them onto its cell grid, at whichever [[CanvasResolution]] it was given.
  *
  * `marker` is read only at [[CanvasResolution.Cell]], where there is one dot per cell; a marker that is not exactly
  * one column wide is replaced by [[Marker.Dot]] rather than allowed to smear into the next cell.
  */
final case class Canvas(
    xBounds: (Double, Double),
    yBounds: (Double, Double),
    shapes: Seq[Shape],
    marker: String = Marker.Dot,
    resolution: CanvasResolution = CanvasResolution.Cell,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val painter = Painter(area, xBounds, yBounds, resolution, marker)
      shapes.foreach(_.draw(painter))
      painter.flush(buffer)
