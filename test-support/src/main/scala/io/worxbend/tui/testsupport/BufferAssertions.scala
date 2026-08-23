package io.worxbend.tui.testsupport

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, StatefulWidget, Widget}

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

  /** One row of `buffer` as a string, with trailing blanks kept — [[lines]] for a single row.
    *
    * For the common assertion that names one row of a frame, so a test does not index into [[lines]] and does not
    * re-derive the wide-grapheme stepping rule for itself.
    */
  def line(buffer: Buffer, y: Int): String = rowText(buffer, y)

  /** Renders `widget` into a fresh `width` x `height` buffer and returns the buffer for assertions. */
  def rendered(widget: Widget, width: Int, height: Int): Buffer =
    val buffer = Buffer(Rect(0, 0, width, height))
    widget.render(buffer.area, buffer)
    buffer

  /** Renders `widget` against caller-owned `state` into a fresh `width` x `height` buffer.
    *
    * The stateful counterpart of [[rendered]]. Interactive widgets in this toolkit are `StatefulWidget[S]` by design —
    * the renderer adjusts the caller's scroll offset to keep the selection visible — so a harness that only accepted
    * plain `Widget`s made every interactive widget's suite write its own three-line renderer, which is exactly the
    * duplication the harness exists to absorb.
    *
    * `state` is mutated by the render, as it would be in an app, and stays the caller's to inspect afterwards.
    */
  def rendered[S](widget: StatefulWidget[S], state: S, width: Int, height: Int): Buffer =
    val buffer = Buffer(Rect(0, 0, width, height))
    widget.render(buffer.area, buffer, state)
    buffer

  /** Renders `widget` into `area` of a fresh `width` x `height` buffer.
    *
    * For the clipping tests: a widget given a sub-rect must stay inside it, and the only way to see that it did is to
    * leave room around the rect and assert the surrounding cells are untouched. `area` may sit anywhere in the buffer,
    * including partly outside it — the buffer's own bounds checking is what is under test in that case.
    */
  def renderedInto(widget: Widget, area: Rect, width: Int, height: Int): Buffer =
    val buffer = Buffer(Rect(0, 0, width, height))
    widget.render(area, buffer)
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
