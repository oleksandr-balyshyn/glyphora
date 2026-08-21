package io.worxbend.tui.testsupport

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Widget}

import java.util.regex.Pattern

/** Renders buffers to plain strings for test assertions — the lightweight equivalent of TamboUI's buffer-assertion
  * module.
  */
object BufferAssertions:

  /** Whitespace at the end of a row, compiled once instead of per row of every frame. */
  private val TrailingSpaces: Pattern = Pattern.compile("\\s+$")

  /** Each row of the buffer as a string. Continuation cells of wide graphemes are skipped, so a row's string content
    * matches what a terminal shows, and trailing blanks are kept (every row spans the full width).
    */
  def lines(buffer: Buffer): Seq[String] =
    for y <- buffer.area.y until buffer.area.bottom
    yield rowText(buffer, y)

  /** Like [[lines]] but with trailing whitespace stripped — the usual shape for readable expected values. This is the
    * row-level half of the normalisation golden-frame comparison relies on; `GoldenFrames.normalise` does the
    * frame-level half by dropping trailing blank rows.
    */
  def trimmedLines(buffer: Buffer): Seq[String] =
    lines(buffer).map(TrailingSpaces.matcher(_).replaceFirst(""))

  /** The whole buffer as one newline-joined string (trailing blanks stripped per row). */
  def text(buffer: Buffer): String =
    trimmedLines(buffer).mkString("\n")

  /** Renders `widget` into a fresh `width` x `height` buffer and returns the buffer for assertions. */
  def rendered(widget: Widget, width: Int, height: Int): Buffer =
    val buffer = Buffer(Rect(0, 0, width, height))
    widget.render(buffer.area, buffer)
    buffer

  /** One row of the buffer as a string, stepping over the continuation cells of wide graphemes.
    *
    * The lower bound of 1 on the step is load-bearing: a zero-width symbol (a combining mark, or anything the width
    * table reports as 0) would otherwise leave `x` unchanged and loop forever. Do not reduce it to `CharWidth.of`.
    */
  private def rowText(buffer: Buffer, y: Int): String =
    val row = StringBuilder()
    var x   = buffer.area.x
    while x < buffer.area.right do
      val cell = buffer.get(x, y)
      row ++= cell.symbol
      x += math.max(1, CharWidth.of(cell.symbol))
    row.result()
