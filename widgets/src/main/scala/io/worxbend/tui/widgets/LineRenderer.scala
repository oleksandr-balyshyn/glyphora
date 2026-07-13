package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Line, Style}

/** Shared span-aware single-row text rendering: writes a [[Line]]'s spans in order, clipping at a column budget,
  * layering each span's style over a base style.
  */
private[widgets] object LineRenderer:

  /** Renders `line` starting at `(x, y)`, using at most `maxWidth` columns. Returns the columns written. */
  def render(buffer: Buffer, x: Int, y: Int, line: Line, maxWidth: Int, baseStyle: Style = Style.Default): Int =
    var column    = x
    var remaining = maxWidth
    line.spans.foreach { span =>
      if remaining > 0 then
        val fitted  = CharWidth.substringByWidth(span.content, remaining)
        buffer.setString(column, y, fitted, baseStyle.patch(span.style))
        val written = CharWidth.of(fitted)
        column += written
        remaining -= written
    }
    column - x
