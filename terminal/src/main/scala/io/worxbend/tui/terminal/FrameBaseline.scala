package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Rect}

/** What the terminal is believed to be showing: the frame [[JLine3Backend.draw]] last flushed, kept so the next frame
  * can be sent as a diff against it.
  *
  * One buffer, recycled in place rather than a fresh `snapshot` per frame. Copying into it costs two array copies and
  * no allocation; snapshotting allocated a `Buffer` plus a cell array plus a flag array — 10 000 entries each on a
  * 200x50 screen — on every frame, at the tick rate, all of it immediately garbage.
  *
  * Not thread-safe, and not meant to be: the owning backend keeps it render-thread-private and a thread that disturbs
  * the screen raises a redraw request instead of reaching in here.
  */
private[terminal] final class FrameBaseline:

  private var buffer: Buffer = Buffer(Rect(0, 0, 0, 0))

  // whether `buffer` describes what is on screen. False before the first frame, and again whenever a frame was
  // composed but could not be written: a baseline that describes a frame the terminal never received would make the
  // next diff skip exactly the cells that are wrong.
  private var valid: Boolean = false

  /** The area of the last flushed frame, or `None` when nothing has been flushed. */
  def area: Option[Rect] = Option.when(valid)(buffer.area)

  /** The grid the next frame is diffed against.
    *
    * A resize gives it a new shape, and there is nothing to recycle then; otherwise the same grid is reused for the
    * lifetime of the size. `blank = true` diffs the next frame against emptiness rather than against a picture the
    * terminal no longer shows — `reset()` produces exactly the all-empty grid a freshly allocated buffer would, without
    * allocating one.
    */
  def prepareFor(next: Rect, blank: Boolean): Buffer =
    if buffer.area != next then
      buffer = Buffer(next)
      valid = false
    else if blank then
      buffer.reset()
      valid = false
    buffer

  /** Records `frame` as what the terminal is now showing. Call only after the write succeeded.
    *
    * A private copy, so later writes into the caller's buffer cannot corrupt the next diff — the same guarantee
    * `snapshot` gave, without the per-frame allocation.
    */
  def commit(frame: Buffer): Unit =
    buffer.copyFrom(frame)
    valid = true

  /** Shifts the baseline exactly as the terminal just shifted the screen.
    *
    * Otherwise every row of the band reads as changed and the next frame repaints them all, which is the work a scroll
    * exists to avoid. A no-op while the baseline is invalid: before the first frame it describes nothing.
    */
  def shift(region: RowRange, lines: Int, direction: ScrollDirection): Unit =
    if valid then buffer.copyFrom(ScrollDirection.shifted(buffer, region, lines, direction))
