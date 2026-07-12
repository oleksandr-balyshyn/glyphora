package io.worxbend.tui.core

enum MouseEventKind:
  case Down, Up, Drag, Moved, ScrollUp, ScrollDown

/** A mouse action at an absolute terminal position. */
final case class MouseEvent(x: Int, y: Int, kind: MouseEventKind, modifiers: KeyModifiers)
