package io.worxbend.tui.core

/** Which moment of a keystroke a [[KeyEvent]] reports: the key going down, the keyboard's own auto-repeat while it is
  * held, or the key coming back up.
  *
  * Almost no terminal can tell these apart. The legacy encodings every terminal speaks carry one byte sequence per
  * character produced and say nothing at all about the key going up, so a decoder that cannot know reports [[Press]].
  * Only a terminal speaking the kitty keyboard protocol with its "report event types" enhancement distinguishes them,
  * and only when the application has asked for that enhancement — see `JLine3Backend.create`'s `reportKeyEventKinds`
  * flag in `tui-terminal`, which is off by default precisely so that an application written against press-only input
  * cannot start seeing every keystroke twice.
  *
  * The consequence for a reader: [[Press]] means "a press, or a terminal that cannot say", never "a release will
  * follow". Code that must pair a press with its release only works on a terminal that reports both, and has to cope
  * with never seeing the second half.
  */
enum KeyEventKind:

  /** The key went down — or the terminal cannot report event types, which is the usual case. */
  case Press

  /** The keyboard's auto-repeat fired while the key was held down. */
  case Repeat

  /** The key came back up. */
  case Release
