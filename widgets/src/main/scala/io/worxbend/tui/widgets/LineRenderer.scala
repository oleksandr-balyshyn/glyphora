package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Line, Style}

/** Shared span-aware single-row text rendering: writes a [[Line]]'s spans in order, clipping at a column budget,
  * layering each span's style over a base style.
  */
private[widgets] object LineRenderer:

  /** Renders `line` starting at `(x, y)`, using at most `maxWidth` columns. Returns the columns written.
    *
    * The clipping rule itself belongs to [[RowCursor]]; this only knows that a line is spans laid end to end and how
    * the three style layers stack. Every character is drawn with `baseStyle`, then the line's own
    * [[io.worxbend.tui.core.Line.style]] layered on top of it, then the span's style on top of that — each step is a
    * [[io.worxbend.tui.core.Style.patch]], so the more specific layer wins wherever it speaks and the outer one shows
    * through wherever it says nothing.
    *
    * `skipWidth` throws the first `skipWidth` columns of the line away before drawing, which is how a caller shows the
    * *end* of a line too wide for the space it has instead of its beginning. A span that falls entirely inside the
    * skipped columns is not drawn at all, the span straddling the edge is cut from its left through
    * [[io.worxbend.tui.core.CharWidth.dropByWidth]], and because that never splits a grapheme cluster the drawing can
    * start one column later than asked for rather than half-way through a wide character.
    *
    * `alignment` places the line inside the `maxWidth` columns it was given. `Left` — the default, and what every
    * caller got before this parameter existed — starts drawing at `x`; `Center` and `Right` start further right by the
    * columns the line does not use. A line at least as wide as the budget starts at `x` whatever the alignment says,
    * because [[io.worxbend.tui.core.Alignment.originAt]] clamps the leftover at zero, so over-wide content still clips
    * from the right exactly as before.
    *
    * The return value is always measured from `x`, so it counts the blank columns a non-`Left` alignment left in front
    * of the line as well as the line itself. A caller laying widgets end to end (see [[Tabs]]) therefore keeps working
    * unchanged: it uses `Left`, where there is no leading gap to count.
    */
  def render(
      buffer: Buffer,
      x: Int,
      y: Int,
      line: Line,
      maxWidth: Int,
      baseStyle: Style = Style.Default,
      alignment: Alignment = Alignment.Left,
      skipWidth: Int = 0,
  ): Int =
    // what is left of the line once the skipped columns are gone is what has to be placed, not the whole line
    val drawnWidth = math.max(0, line.width - math.max(0, skipWidth))
    val start      = alignment.originAt(x, maxWidth, drawnWidth)
    val cursor     = RowCursor(buffer, y, start, x + maxWidth)
    val lineStyle  = baseStyle.patch(line.style)
    var remaining  = math.max(0, skipWidth)
    line.spans.foreach { span =>
      if remaining <= 0 then cursor.write(span.content, lineStyle.patch(span.style))
      else
        val spanWidth = CharWidth.of(span.content)
        if spanWidth <= remaining then remaining -= spanWidth
        else
          cursor.write(CharWidth.dropByWidth(span.content, remaining), lineStyle.patch(span.style))
          remaining = 0
    }
    cursor.at - x
