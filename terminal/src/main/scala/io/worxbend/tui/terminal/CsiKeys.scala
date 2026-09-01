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
      // xterm and rxvt report the shifted function keys — what a keyboard sends for Shift+F1 through Shift+F8 — as
      // eight further tilde numbers, which an application sees as F13-F20. The numbers 27, 30 and 35 name nothing in
      // those terminfo tables, so the block is spelled out case by case instead of computed from an offset: the gaps
      // are in the specification, not in this transcription of it.
      //
      // 29 is deliberately absent from that run. Two conventions claim it: xterm's shifted-function block calls it
      // F16, while the DEC VT220 "Do" key and rxvt's context-menu key both report it as a menu press. Only one
      // mapping can win, and Menu is the one this decoder already emitted, so changing it would silently break
      // applications that bind the menu key. F16 is therefore unreachable on terminals that use the tilde encoding;
      // a terminal speaking the kitty protocol reports it unambiguously and is decoded elsewhere.
      case 25                      => Some(KeyCode.F(13))
      case 26                      => Some(KeyCode.F(14))
      case 28                      => Some(KeyCode.F(15))
      case 29                      => Some(KeyCode.Menu)      // xterm/rxvt's context-menu key; see the note above
      case 31                      => Some(KeyCode.F(17))
      case 32                      => Some(KeyCode.F(18))
      case 33                      => Some(KeyCode.F(19))
      case 34                      => Some(KeyCode.F(20))
      case _                       => None
