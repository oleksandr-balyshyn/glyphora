package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Style}

/** A left-to-right write head over one row of a [[Buffer]], carrying the column it has reached and the column it must
  * stop before.
  *
  * Every widget that lays a row out as "one piece of text after another" needs the same rule: clip the next string to
  * the columns still left, write it, and advance by the width that was *actually* written rather than by the width that
  * was asked for. Written out by hand that rule is a running `x`, a `CharWidth.substringByWidth` against `right - x`,
  * and an `if x < right` guard in front of every segment — and the guard is exactly the part that is easy to get subtly
  * wrong, because a caller-supplied wide (CJK) or emoji string is the case it exists for. Stating it once means the
  * widgets below cannot disagree about it.
  *
  * The guard lives inside [[write]], which no-ops when there is no room left, so callers do not repeat it. Instances
  * are created per render, used on the render thread, and thrown away; the mutable column is local to one call and
  * never escapes.
  *
  * @param buffer
  *   the buffer being drawn into
  * @param y
  *   the row being written
  * @param x
  *   the column the head starts at; advanced by [[write]] and [[skip]]
  * @param right
  *   the first column the head must not write to — an area's `right`, i.e. exclusive
  */
private[widgets] final class RowCursor(buffer: Buffer, y: Int, private var x: Int, right: Int):

  /** Columns still available before `right`. Never negative, so a caller can size a sub-area from it directly. */
  def remaining: Int = math.max(0, right - x)

  /** The column the head is at — where the next [[write]] would start. */
  def at: Int = x

  /** Moves the head `cells` columns right without drawing, for the separator blanks a widget leaves between segments. A
    * negative count is ignored: a cursor only ever moves forward.
    */
  def skip(cells: Int): Unit =
    x += math.max(0, cells)

  /** Writes as much of `text` as fits in the columns still left and advances the head by the width written.
    *
    * Clipping goes through [[io.worxbend.tui.core.CharWidth.substringByWidth]], so a two-column cluster that would only
    * half-fit is dropped whole rather than split. With no room left this does nothing and the head does not move.
    */
  def write(text: String, style: Style): Unit =
    if remaining > 0 then
      val fitted = CharWidth.substringByWidth(text, remaining)
      buffer.setString(x, y, fitted, style)
      x += CharWidth.of(fitted)
