package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style, Widget}

import scala.concurrent.duration.FiniteDuration

/** How a [[SpinnerGrid]] offsets each slot's animation from its neighbours'.
  *
  * The offset is what turns a block of identical spinners into one moving thing, so this is the grid's whole character.
  * The sign rule is uniform: a slot's offset is `-distance * framesPerCell`, so the feature travels *away* from
  * distance zero, and a negative `framesPerCell` reverses it.
  */
enum GridPhase:

  /** Every slot in lockstep — a synchronised pulse. No motion to track across the block, which makes this the quietest
    * member of the family and the one to reach for under a reduced-motion preference.
    */
  case Uniform

  /** Offset by Manhattan distance from the top-left slot, so the wave runs diagonally out of that corner. At a one-slot
    * height or width it degenerates cleanly into a row or a column wave.
    */
  case Diagonal(framesPerCell: Int = 1)

  /** Offset by Chebyshev distance from the centre slot, so the animation ripples outward in square rings. Reads as
    * "radiating" rather than "travelling", which suits a square block where a diagonal reads as a slant.
    */
  case Radial(framesPerCell: Int = 1)

  /** How many frames slot `(col, row)` runs behind slot zero. */
  private[widgets] def offsetAt(col: Int, row: Int, columns: Int, rows: Int): Int =
    this match
      case Uniform                 => 0
      case Diagonal(framesPerCell) => -(col + row) * framesPerCell
      case Radial(framesPerCell)   =>
        val centreCol = (columns - 1) / 2
        val centreRow = (rows - 1) / 2
        -math.max(math.abs(col - centreCol), math.abs(row - centreRow)) * framesPerCell

/** A block of slots all running the same [[SpinnerPreset]], each offset in time from its neighbours, so the block reads
  * as one animation rather than as many.
  *
  * It consumes the preset catalogue rather than extending it, and that is the point of it existing: a preset is a
  * function of time alone and can never carry a *spatial* offset, while every preset already in the catalogue —
  * braille, block, emoji, and the ASCII ones — becomes an area-filling animation the moment it is put here.
  * `SpinnerPreset.Line` is this family's ASCII floor.
  *
  * This is also the one place in the animated family where per-slot colour is free: every slot holds exactly one frame,
  * so `ramp` shades the block by phase without any of the one-style-per-cell compromise [[OrbitSpinner]] has to make.
  *
  * A slot is `preset.width` columns wide, so a two-column emoji preset gives half as many slots across rather than
  * overwriting its neighbour. An area narrower than one slot draws nothing rather than a clipped half-glyph.
  */
final case class SpinnerGrid(
    elapsed: FiniteDuration,
    preset: SpinnerPreset = SpinnerPreset.DotsRing,
    phase: GridPhase = GridPhase.Diagonal(),
    style: Style = Style.Default,
    ramp: Option[ColorRamp] = None,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val slotWidth = math.max(1, preset.width)
      val columns   = area.width / slotWidth
      if columns > 0 then
        val frames = preset.frames.size
        var row    = 0
        while row < area.height do
          var col = 0
          while col < columns do
            val offset = phase.offsetAt(col, row, columns, area.height)
            // offsetting time rather than indexing the frame vector keeps every slot on the preset's own speed
            val at     = elapsed + preset.frameDuration * offset.toLong
            val glyph  = preset.frameAt(at)
            val shade  = ramp.map: chosen =>
              val position = if frames <= 1 then 0.0 else preset.frameIndexAt(at).toDouble / (frames - 1)
              style.withFg(chosen.at(position))
            val _      =
              ClusterRow.put(buffer, area.x + col * slotWidth, area.y + row, glyph, shade.getOrElse(style), area.right)
            col += 1
          row += 1
