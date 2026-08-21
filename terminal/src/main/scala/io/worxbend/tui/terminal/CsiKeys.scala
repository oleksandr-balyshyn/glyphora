package io.worxbend.tui.terminal

import io.worxbend.tui.core.KeyCode

/** The legacy `CSI n ~` key vocabulary — the numbered navigation and function keys every terminal predating the kitty
  * protocol emits.
  *
  * A lookup table only: no input is read and no state is held, so it is safe to call from any thread. It sits beside
  * [[KittyKeys]] rather than inside [[InputDecoder]] because both are specification transcriptions that a reader checks
  * against an external table, not part of how bytes are pulled off the wire.
  */
private[terminal] object CsiKeys:

  /** The key named by the first parameter of a `CSI n ~` sequence, or `None` when the number names nothing. */
  def tildeKey(code: Int): Option[KeyCode] =
    code match
      case 1 | 7                   => Some(KeyCode.Home)
      case 2                       => Some(KeyCode.Insert)
      case 3                       => Some(KeyCode.Delete)
      case 4 | 8                   => Some(KeyCode.End)
      case 5                       => Some(KeyCode.PageUp)
      case 6                       => Some(KeyCode.PageDown)
      case n if n >= 11 && n <= 15 => Some(KeyCode.F(n - 10)) // F1-F5
      case n if n >= 17 && n <= 21 => Some(KeyCode.F(n - 11)) // F6-F10, the block skipping 16
      case 23                      => Some(KeyCode.F(11))
      case 24                      => Some(KeyCode.F(12))
      case _                       => None
