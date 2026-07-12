package io.worxbend.tui.core

/** Something that can draw itself into a region of a frame buffer.
  *
  * A single abstract method, so any `(area, buffer) => ()` lambda is a valid widget. Implementations must confine their
  * writes to `area` (the buffer clips stray writes, but relying on that is a defect) and must route all
  * width/truncation math through [[CharWidth]].
  */
trait Widget:
  def render(area: Rect, buffer: Buffer): Unit
