package io.worxbend.tui.core

/** What the mouse did.
  *
  * `Drag` is motion with a button held; `Moved` is motion with none. The terminal backend requests button-event
  * tracking (DEC modes 1000/1002/1006), under which terminals only report motion while a button is down — so `Moved` is
  * part of the vocabulary but no decoder in this library currently produces it.
  */
enum MouseEventKind:
  case Down, Up, Drag, Moved, ScrollUp, ScrollDown

/** A mouse action at an absolute terminal position.
  *
  * `position` is absolute and zero-based, in the same coordinate space as [[Position]] and [[Rect]]: column `0`, row
  * `0` is the top-left cell of the terminal, not of any widget. A handler that wants coordinates relative to the area
  * it was given subtracts that area's own origin (`event.position.x - area.x`).
  */
final case class MouseEvent(position: Position, kind: MouseEventKind, modifiers: KeyModifiers)
