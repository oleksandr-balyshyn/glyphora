package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style}

/** A one-cell-thick sub-cell track over one [[Rect]], addressed in slots rather than in cells.
  *
  * This is what lets a horizontal linear spinner move in half-cell steps instead of jumping a whole cell at a time: a
  * braille cell packs 2 dots across, so a `w`-cell row carries `2w` positions. The axes swap for a vertical track — 4
  * dots down, so 4 positions per cell — and with them the brightness budget, which is whatever the *other* axis has
  * left: 4 levels horizontally, 2 vertically.
  *
  * ONE STYLE PER CELL — the constraint this is shaped around. A cell carries one [[Style]] but several slots, so two
  * rules follow, both chosen rather than fallen into. Masks are OR-ed, so a cell holding both a bright and a dim slot
  * keeps every dot and the resting rail never erodes. Styles take the brightest slot in the cell, so such a cell reads
  * as head: the quantisation error runs in one predictable direction — a cell can read brighter than it should, never
  * dimmer — and the trail therefore never appears to break.
  *
  * `resolution` and `marker` are the pair [[Canvas]] already ships, and reusing them is what gives this an ASCII floor
  * for free: [[CanvasResolution.Cell]] draws the same track one marker glyph per cell, at one position and one
  * brightness per cell. `marker` is read at that resolution only.
  *
  * Allocated per render rather than retained, so its only state is the caller's intensity array.
  */
private[widgets] final class LinearDots(
    area: Rect,
    axis: LinearAxis,
    resolution: CanvasResolution,
    marker: String,
):

  private val (dotsAcross, dotsDown) = SubCell.dotsPerCell(resolution)

  /** Sub-cell positions one cell contributes along the direction of travel. */
  private val slotsPerCell: Int = axis match
    case LinearAxis.Horizontal => dotsAcross
    case LinearAxis.Vertical   => dotsDown

  /** Distinguishable brightnesses one slot can show, from the dots left over on the cross axis. */
  private val levels: Int = axis match
    case LinearAxis.Horizontal => dotsDown
    case LinearAxis.Vertical   => dotsAcross

  /** Cells the track occupies along the direction of travel. */
  val cells: Int = axis match
    case LinearAxis.Horizontal => math.max(0, area.width)
    case LinearAxis.Vertical   => math.max(0, area.height)

  /** Positions the track carries — what [[LinearMotion.intensities]] wants for `slots`. */
  val slots: Int = cells * slotsPerCell

  /** The one-cell-thick rectangle the dots are actually accumulated over.
    *
    * A track ignores its cross-axis extent — a horizontal one only ever writes `area.y` — so the surface is given
    * exactly the row or column that gets drawn, rather than the whole [[Rect]] and a second guard to keep it inside.
    */
  private def trackArea: Rect = axis match
    case LinearAxis.Horizontal => Rect(area.x, area.y, area.width, 1)
    case LinearAxis.Vertical   => Rect(area.x, area.y, 1, area.height)

  /** Writes every cell holding at least one dot, colored `styleFor(brightestSlotInCell)`.
    *
    * Cells with no dots are left untouched, so the track composes over whatever is beneath it under `layers` — the same
    * rule [[Canvas]] and [[DotGrid]] follow. Slots past the end of the area are dropped rather than wrapped, so an
    * over-long array cannot write outside the [[Rect]].
    */
  def render(intensities: Array[Double], buffer: Buffer, styleFor: Double => Style): Unit =
    if !area.isEmpty then
      val surface = SubCellSurface(trackArea, resolution, marker)
      // brightest intensity seen in each *cell*, which is what decides that cell's one style
      val peaks   = new Array[Double](surface.cellCount)
      val drawn   = math.min(cells, intensities.length / slotsPerCell)
      var index   = 0
      while index < drawn do
        var sub = 0
        while sub < slotsPerCell do
          val intensity = intensities(index * slotsPerCell + sub)
          val level     = levelFor(intensity)
          if level > 0 then
            lightLadder(surface, index, sub, level)
            peaks(index) = math.max(peaks(index), intensity)
          sub += 1
        index += 1
      surface.flush(buffer, slot => styleFor(peaks(slot / surface.slotsPerCell)))

  /** Lights the dots one slot contributes at brightness `level`.
    *
    * A horizontal track fills its dot column from the floor up, which is the direction [[ProgressPreset.Dots]] and
    * [[SpinnerPreset.GrowVertical]] already fill a cell — a linear spinner sitting beside either should grow the same
    * way. At braille that gives the ladder `⣀ ⣤ ⣶ ⣿`, and a half-cell head reads `⡇` or `⢸` before it straddles into
    * `⣿`. A vertical track fills its dot row from the left, matching how a partial block glyph grows.
    */
  private def lightLadder(surface: SubCellSurface, index: Int, sub: Int, level: Int): Unit =
    var i = 0
    while i < level do
      val _ = axis match
        case LinearAxis.Horizontal => surface.light(index * dotsAcross + sub, dotsDown - 1 - i)
        case LinearAxis.Vertical   => surface.light(i, index * dotsDown + sub)
      i += 1

  /** `NaN` reads as unlit rather than as a lit cell, so a caller's arithmetic mistake shows up as a gap it can see. */
  private def levelFor(intensity: Double): Int =
    if intensity.isNaN || intensity <= 0.0 then 0
    else math.min(levels, math.max(1, math.ceil(intensity * levels).toInt))
