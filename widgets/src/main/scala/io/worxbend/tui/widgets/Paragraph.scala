package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Line, Measured, Rect, Span, Style, Text, Widget}

/** Multi-line styled text with alignment and optional wrapping.
  *
  * With [[Overflow.Wrap]] the text breaks at grapheme-cluster boundaries (not word boundaries — good enough for v1 and
  * never splits a wide character or emoji); with [[Overflow.Clip]], the default, long lines are cut at the area edge.
  *
  * [[heightAt]] reports the rows the paragraph needs from the same `overflow` field `render` draws with, so the two
  * cannot disagree about whether the text wraps.
  */
final case class Paragraph(
    text: Text,
    alignment: Alignment = Alignment.Left,
    overflow: Overflow = Overflow.Clip,
    style: Style = Style.Default,
) extends Widget
    with Measured:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      // lazily: wrapping the whole document to draw one screenful makes render cost scale with the text, not the area
      val lines = overflow match
        case Overflow.Wrap => text.lines.iterator.flatMap(Paragraph.wrapLine(_, area.width))
        case Overflow.Clip => text.lines.iterator
      lines.take(area.height).zipWithIndex.foreach { (line, row) =>
        val lineWidth = math.min(line.width, area.width)
        val startX    = alignment.originAt(area.x, area.width, lineWidth)
        val _         = LineRenderer.render(buffer, startX, area.y + row, line, area.right - startX, style)
      }

  /** The rows this text occupies at `width` — the measurement counterpart of [[render]], and always an answer: a
    * paragraph always knows its own height. A clipping paragraph is one row per line; a wrapping one counts the rows
    * `wrapLine` would produce, which is why the two share that function rather than each doing the arithmetic.
    */
  override def heightAt(width: Int): Option[Int] =
    val rows = overflow match
      case Overflow.Clip               => text.lines.size
      case Overflow.Wrap if width <= 0 => text.lines.size
      case Overflow.Wrap               => text.lines.map(line => math.max(1, Paragraph.wrapLine(line, width).size)).sum
    Some(rows)

object Paragraph:

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
              // A single cluster wider than the whole area. It cannot be split, but deleting it is worse than
              // clipping it: `heightAt` counts the rows this same function returns, so a dropped cluster makes
              // measurement and rendering disagree and shifts every following line up a row. Give it a row of its
              // own and let the renderer clip it, the way overflow is handled everywhere else.
              val clusterLength = firstClusterLength(pending)
              currentSpans = currentSpans :+ Span(pending.take(clusterLength), span.style)
              pending = pending.drop(clusterLength)
              flush()
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
