package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style, Symbols, Widget}

/** A value grid rendered as shade intensity: each cell maps its value (against the grid's max) onto a shade ramp — rows
  * are y (top first), columns are x.
  */
final case class Heatmap(
    values: Seq[Seq[Double]],
    style: Style = Style.Default,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    val ceiling = values.flatten.filter(_ > 0).maxOption.getOrElse(0.0)
    if !area.isEmpty && ceiling > 0 then
      values.take(area.height).zipWithIndex.foreach { (row, y) =>
        row.take(area.width).zipWithIndex.foreach { (value, x) =>
          val normalized = Fraction.clamped(value / ceiling)
          val level      = math.round(normalized * (Heatmap.Ramp.size - 1)).toInt
          buffer.set(area.x + x, area.y + y, Cell(Heatmap.Ramp(level), style))
        }
      }

object Heatmap:

  /** The shade ramp a cell's value is mapped onto, empty to full. The shared vocabulary in `core.Symbols` rather than
    * four literals here, so a heat map, a loading placeholder and a progress track all step through the same glyphs.
    */
  private val Ramp: Vector[String] = Symbols.Shade.Ramp
