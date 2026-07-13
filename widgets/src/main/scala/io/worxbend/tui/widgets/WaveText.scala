package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style, Widget}

/** Text with a highlight wave rolling through it: clusters near the wave crest (advanced by `phase`, one position per
  * tick) render with `crestStyle`.
  */
final case class WaveText(
    content: String,
    phase: Int,
    style: Style = Style.Default,
    crestStyle: Style = Style.Default.bold,
    crestWidth: Int = 2,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val clusters = CharWidth.graphemeClusters(content).toVector
      if clusters.nonEmpty then
        val crest = math.floorMod(phase, clusters.size)
        var x     = area.x
        clusters.zipWithIndex.foreach { (cluster, index) =>
          val width = CharWidth.of(cluster)
          if width > 0 && x + width <= area.right then
            val distance  = math.abs(index - crest)
            val cellStyle = if distance < crestWidth then crestStyle else style
            buffer.set(x, area.y, Cell(cluster, cellStyle))
            if width == 2 then buffer.set(x + 1, area.y, Cell.Empty)
          x += width
        }
