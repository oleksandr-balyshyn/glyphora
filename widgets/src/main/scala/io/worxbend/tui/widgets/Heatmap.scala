package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style, Widget}

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
  private val Ramp: Vector[String] = Vector(" ", "░", "▒", "▓", "█")
