package io.worxbend.tui.core

/** Which physical mouse button a [[MouseEvent]] is about.
  *
  * Terminals number the buttons in the report they send: 0 is the left button, 1 the middle one (the wheel pressed as a
  * button), 2 the right one. `Unknown` is the honest answer whenever the report names no button at all — a wheel notch
  * scrolls without pressing anything, a `Moved` event happens with nothing held, and the legacy X10 encoding has a
  * single "some button came up" release code that does not say which one it was.
  *
  * The case is called `Unknown` rather than `None` so that it never reads as, or shadows, Scala's own `None` at a use
  * site such as `event.button == MouseButton.Unknown`.
  */
enum MouseButton:
  case Left, Middle, Right, Unknown
