package io.worxbend.tui.terminal

/** How much mouse traffic the terminal is asked to send.
  *
  * A terminal only reports the mouse activity it has been told to report, and the two useful settings differ enormously
  * in volume:
  *
  *   - `Buttons` asks for DEC private modes 1000 and 1002 — presses, releases, the wheel, and motion *only while a
  *     button is held down*. That is everything a click, a scroll and a drag need, and it is silent while the user is
  *     merely moving the pointer across the window.
  *   - `AllMotion` adds mode 1003, under which the terminal reports every single pointer movement over the window. That
  *     is what hover highlighting, tooltips and drop-target previews need, and it is also a report per cell crossed — a
  *     stream that is free locally and a genuine cost over a slow ssh link. It is opt-in for that reason.
  *
  * Which one is in force decides whether [[io.worxbend.tui.core.MouseEventKind.Moved]] can ever be delivered: motion
  * with no button held is only reported under `AllMotion`.
  */
enum MouseCaptureMode:
  case Buttons, AllMotion
