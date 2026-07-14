package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Line, Rect, Span, Style, Text, Widget}

/** Multi-line styled text with alignment and optional wrapping.
  *
  * Wrapping breaks at grapheme-cluster boundaries (not word boundaries — good enough for v1 and never splits a wide
  * character or emoji); without wrapping, long lines are clipped at the area edge.
  */
final case class Paragraph(
    text: Text,
    alignment: Alignment = Alignment.Left,
    wrap: Boolean = false,
    style: Style = Style.Default,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val lines = if wrap then text.lines.flatMap(Paragraph.wrapLine(_, area.width)) else text.lines
      lines.take(area.height).zipWithIndex.foreach { (line, row) =>
        val lineWidth = math.min(line.width, area.width)
        val startX    = alignment match
          case Alignment.Left   => area.x
          case Alignment.Center => area.x + (area.width - lineWidth) / 2
          case Alignment.Right  => area.x + area.width - lineWidth
        val _         = LineRenderer.render(buffer, startX, area.y + row, line, area.right - startX, style)
      }

object Paragraph:

  /** How many rows `text` occupies at `width` — the measurement counterpart of rendering. */
  def heightOf(text: Text, width: Int, wrap: Boolean = true): Int =
    if !wrap || width <= 0 then text.lines.size
    else text.lines.map(line => math.max(1, wrapLine(line, width).size)).sum

  private[widgets] def wrapLine(line: Line, width: Int): Seq[Line] =
    if width <= 0 then Seq.empty
    else if line.width <= width then Seq(line)
    else
      val wrapped      = List.newBuilder[Line]
      var currentSpans = Vector.empty[Span]
      var currentWidth = 0

      def flush(): Unit =
        wrapped += Line(currentSpans)
        currentSpans = Vector.empty
        currentWidth = 0

      line.spans.foreach { span =>
        var pending = span.content
        while pending.nonEmpty do
          val fitted = CharWidth.substringByWidth(pending, width - currentWidth)
          if fitted.isEmpty then
            if currentWidth == 0 then
              // a single cluster wider than the whole area: drop it or loop forever
              pending = pending.drop(firstClusterLength(pending))
            else flush()
          else
            currentSpans = currentSpans :+ Span(fitted, span.style)
            currentWidth += CharWidth.of(fitted)
            pending = pending.drop(fitted.length) // removes the exact prefix just cut, not layout math
          if currentWidth >= width then flush()
      }
      if currentSpans.nonEmpty then flush()
      wrapped.result()

  private def firstClusterLength(text: String): Int =
    val clusters = CharWidth.graphemeClusters(text)
    if clusters.hasNext then clusters.next().length else text.length
