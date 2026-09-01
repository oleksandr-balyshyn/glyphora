package io.worxbend.tui.core

/** What the mouse did.
  *
  * `Drag` is motion with a button held; `Moved` is motion with none. The terminal backend requests button-event
  * tracking (DEC modes 1000/1002/1006), under which terminals only report motion while a button is down — so `Moved` is
  * part of the vocabulary but no decoder in this library currently produces it.
  *
  * `ScrollUp`/`ScrollDown` are the vertical wheel. `ScrollLeft`/`ScrollRight` are the horizontal one (xterm buttons 66
  * and 67), which is what a sideways trackpad swipe sends. No built-in element consumes the horizontal pair, because no
  * widget in this library scrolls sideways; they are decoded and delivered so that an application's own `onMouseEvent`
  * can act on them, and they bubble untouched past every built-in that declines them.
  */
enum MouseEventKind:
  case Down, Up, Drag, Moved, ScrollUp, ScrollDown, ScrollLeft, ScrollRight

/** A mouse action at an absolute terminal position.
  *
  * `position` is absolute and zero-based, in the same coordinate space as [[Position]] and [[Rect]]: column `0`, row
  * `0` is the top-left cell of the terminal, not of any widget. A handler that wants coordinates relative to the area
  * it was given subtracts that area's own origin (`event.position.x - area.x`).
  *
  * `button` names which physical button the action concerns, so an application can tell a right-click (open a context
  * menu) from a left-click (activate the thing under the pointer). It defaults to [[MouseButton.Left]] so that every
  * three-argument construction written before the field existed still compiles and still means what it meant. That
  * default is a convenience for hand-built events only: what the decoder reports for a wheel notch, and for an X10
  * release that cannot name a button, is [[MouseButton.Unknown]].
  */
final case class MouseEvent(
    position: Position,
    kind: MouseEventKind,
    modifiers: KeyModifiers,
    button: MouseButton = MouseButton.Left,
)
