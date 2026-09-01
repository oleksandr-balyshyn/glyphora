package io.worxbend.tui.core

/** A modifier key reported in its own right — as a key that was pressed, rather than as a bit set on some other key's
  * event.
  *
  * Two different things are called a "modifier" here. [[KeyModifiers]] is the set of modifier keys that were held down
  * while another key was pressed: `Ctrl+a` arrives as `KeyCode.Char('a')` carrying the `Ctrl` bit, and the Ctrl key
  * itself produces no event of its own. A `ModifierKey`, by contrast, names the Ctrl key as the key: it is what a
  * terminal sends when the user presses or releases Ctrl and nothing else. Applications that want to show a "Ctrl held"
  * hint, or to treat holding a key as a mode, need the second kind; everything else only ever needs the first.
  *
  * Only terminals speaking the kitty keyboard protocol report these, and only once the application has asked for them.
  * Left and right are separate cases because the protocol distinguishes them and a binding on "the right Alt key"
  * cannot be written if the two collapse into one name.
  *
  * A plain immutable value: no ownership, no thread constraints.
  */
enum ModifierKey:
  case LeftShift, LeftControl, LeftAlt, LeftSuper, LeftHyper, LeftMeta
  case RightShift, RightControl, RightAlt, RightSuper, RightHyper, RightMeta
  case IsoLevel3Shift, IsoLevel5Shift
