package io.worxbend.tui.core

/** What the mouse did.
  *
  * `Drag` is motion with a button held; `Moved` is motion with none. The terminal backend requests button-event
  * tracking (DEC modes 1000/1002/1006), under which terminals only report motion while a button is down — so `Moved` is
  * part of the vocabulary but no decoder in this library currently produces it.
  */
enum MouseEventKind:
  case Down, Up, Drag, Moved, ScrollUp, ScrollDown

/** A mouse action at an absolute terminal position. */
final case class MouseEvent(x: Int, y: Int, kind: MouseEventKind, modifiers: KeyModifiers)
