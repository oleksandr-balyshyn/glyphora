package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Line, Style}

/** Shared span-aware single-row text rendering: writes a [[Line]]'s spans in order, clipping at a column budget,
  * layering each span's style over a base style.
  */
private[widgets] object LineRenderer:

  /** Renders `line` starting at `(x, y)`, using at most `maxWidth` columns. Returns the columns written.
    *
    * The clipping rule itself belongs to [[RowCursor]]; this only knows that a line is spans laid end to end and that
    * each span's style layers over `baseStyle`.
    */
  def render(buffer: Buffer, x: Int, y: Int, line: Line, maxWidth: Int, baseStyle: Style = Style.Default): Int =
    val cursor = RowCursor(buffer, y, x, x + maxWidth)
    line.spans.foreach(span => cursor.write(span.content, baseStyle.patch(span.style)))
    cursor.at - x
