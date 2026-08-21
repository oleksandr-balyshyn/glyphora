package io.worxbend.tui.core

/** A widget whose rendering reads mutable state owned by the caller (scroll offsets, selections).
  *
  * The widget value itself stays immutable and reusable; all per-instance mutability lives in `S`, which the
  * application owns and passes in at render time.
  *
  * '''Threading and ownership.''' `render` runs on the render thread that owns `buffer` (see [[Widget]] and
  * [[Buffer]]), and it is allowed to mutate `state` in place — clamping a scroll offset to the area it was just given,
  * or memoising a viewport, is the reason the state is passed rather than copied. Because that mutation is
  * unsynchronized, one `S` value must not be shared across threads, and must not be handed to two widgets that render
  * concurrently. Like `buffer`, `area` and `buffer` must not be retained past the call; `state` belongs to the caller
  * and outlives it.
  */
trait StatefulWidget[S]:
  def render(area: Rect, buffer: Buffer, state: S): Unit
