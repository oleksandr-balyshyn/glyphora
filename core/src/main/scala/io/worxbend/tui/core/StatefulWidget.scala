package io.worxbend.tui.core

/** A widget whose rendering reads mutable state owned by the caller (scroll offsets, selections).
  *
  * The widget value itself stays immutable and reusable; all per-instance mutability lives in `S`, which the
  * application owns and passes in at render time.
  */
trait StatefulWidget[S]:
  def render(area: Rect, buffer: Buffer, state: S): Unit
