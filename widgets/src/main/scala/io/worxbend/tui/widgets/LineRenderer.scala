package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Line, Style}

/** Shared span-aware single-row text rendering: writes a [[Line]]'s spans in order, clipping at a column budget,
  * layering each span's style over a base style.
  */
private[widgets] object LineRenderer:

  /** Renders `line` starting at `(x, y)`, using at most `maxWidth` columns. Returns the columns written.
    *
    * The clipping rule itself belongs to [[RowCursor]]; this only knows that a line is spans laid end to end and that
    * each span's style layers over `baseStyle`.
    *
    * `skipWidth` throws the first `skipWidth` columns of the line away before drawing, which is how a caller shows the
    * *end* of a line too wide for the space it has instead of its beginning. A span that falls entirely inside the
    * skipped columns is not drawn at all, the span straddling the edge is cut from its left through
    * [[io.worxbend.tui.core.CharWidth.dropByWidth]], and because that never splits a grapheme cluster the drawing can
    * start one column later than asked for rather than half-way through a wide character.
    */
  def render(
      buffer: Buffer,
      x: Int,
      y: Int,
      line: Line,
      maxWidth: Int,
      baseStyle: Style = Style.Default,
      skipWidth: Int = 0,
  ): Int =
    val cursor    = RowCursor(buffer, y, x, x + maxWidth)
    var remaining = math.max(0, skipWidth)
    line.spans.foreach { span =>
      if remaining <= 0 then cursor.write(span.content, baseStyle.patch(span.style))
      else
        val spanWidth = CharWidth.of(span.content)
        if spanWidth <= remaining then remaining -= spanWidth
        else
          cursor.write(CharWidth.dropByWidth(span.content, remaining), baseStyle.patch(span.style))
          remaining = 0
    }
    cursor.at - x
