package io.worxbend.tui.core

/** Something that can draw itself into a region of a frame buffer.
  *
  * A single abstract method, so any `(area, buffer) => ()` lambda is a valid widget. Implementations must confine their
  * writes to `area` (the buffer clips stray writes, but relying on that is a defect) and must route all
  * width/truncation math through [[CharWidth]].
  *
  * '''Threading and ownership.''' `render` is called on the thread that owns `buffer` — in a running application the
  * runner's render thread, the same thread `Signal` writes are pinned to (see [[Buffer]]'s ownership paragraph).
  * Neither `area` nor `buffer` may be retained beyond the call: the runner reuses and resets its buffer between frames,
  * so a stored reference is written to under a later frame's ownership and produces a torn frame rather than an error.
  * Writing into `buffer` from any other thread is likewise a defect.
  */
trait Widget:
  def render(area: Rect, buffer: Buffer): Unit
