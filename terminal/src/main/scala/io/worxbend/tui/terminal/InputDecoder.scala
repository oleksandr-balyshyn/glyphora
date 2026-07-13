package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent, KeyModifiers, MouseEvent, MouseEventKind}

/** Decodes terminal input bytes into [[Event]]s: printable keys, control keys, ANSI CSI/SS3 escape sequences for
  * navigation and function keys, and SGR-encoded mouse reports.
  *
  * Reads through an injected `read(timeoutMillis) => Int` function (negative result = nothing available), so the
  * decoder is testable with scripted input and independent of JLine.
  */
private[terminal] final class InputDecoder(read: Long => Int):

  /** Decodes the next event, blocking up to `timeoutMillis` for the first character. `None` on timeout. */
  def decode(timeoutMillis: Long): Option[Event] =
    val first = read(timeoutMillis)
    if first < 0 then None
    else Some(decodeFirst(first))

  private def decodeFirst(first: Int): Event =
    first match
      case 0x1b                   => decodeEscape()
      case 0x0d | 0x0a            => key(KeyCode.Enter)
      case 0x09                   => key(KeyCode.Tab)
      case 0x7f | 0x08            => key(KeyCode.Backspace)
      case c if c >= 1 && c <= 26 => Event.Key(KeyEvent(KeyCode.Char(('a' + c - 1).toChar), KeyModifiers.Ctrl))
      case c                      => key(KeyCode.Char(c.toChar))

  /** A lone ESC is the Escape key; ESC `[`/`O` opens a control sequence; ESC + printable is Alt+key. */
  private def decodeEscape(): Event =
    val second = read(EscapeTimeoutMillis)
    second match
      case -1 | -2 => key(KeyCode.Escape)
      case '['     => decodeCsi()
      case 'O'     => decodeSs3()
      case c       => Event.Key(KeyEvent(KeyCode.Char(c.toChar), KeyModifiers.Alt))

  /** CSI sequences: parameters (digits, `;`, and the SGR-mouse `<` prefix) followed by a final byte in 0x40–0x7E. */
  private def decodeCsi(): Event =
    val params    = StringBuilder()
    var finalByte = -1
    while finalByte < 0 do
      val c = read(EscapeTimeoutMillis)
      if c < 0 then finalByte = 0 // torn sequence: decodeCsiFinal's default arm reports Escape
      else if c >= 0x40 && c <= 0x7e then finalByte = c
      else params.append(c.toChar)
    decodeCsiFinal(params.result(), finalByte)

  private def decodeCsiFinal(params: String, finalByte: Int): Event =
    val isSgrMouse = params.startsWith("<")
    if isSgrMouse && (finalByte == 'M' || finalByte == 'm') then decodeSgrMouse(params.drop(1), finalByte == 'M')
    else
      val numbers   = params.split(';').toSeq.filter(_.nonEmpty).flatMap(_.toIntOption)
      val modifiers = numbers.drop(1).headOption.map(modifiersFromCode).getOrElse(KeyModifiers.None)
      finalByte match
        case 'A' => Event.Key(KeyEvent(KeyCode.Up, modifiers))
        case 'B' => Event.Key(KeyEvent(KeyCode.Down, modifiers))
        case 'C' => Event.Key(KeyEvent(KeyCode.Right, modifiers))
        case 'D' => Event.Key(KeyEvent(KeyCode.Left, modifiers))
        case 'H' => Event.Key(KeyEvent(KeyCode.Home, modifiers))
        case 'F' => Event.Key(KeyEvent(KeyCode.End, modifiers))
        case 'Z' => Event.Key(KeyEvent(KeyCode.Tab, KeyModifiers.Shift))
        case '~' => decodeTilde(numbers)
        case _   => key(KeyCode.Escape)

  /** `CSI n ~` navigation/function keys; the modifier, when present, is the second parameter. */
  private def decodeTilde(numbers: Seq[Int]): Event =
    val modifiers = numbers.drop(1).headOption.map(modifiersFromCode).getOrElse(KeyModifiers.None)
    val code      = numbers.headOption match
      case Some(1)                       => KeyCode.Home
      case Some(2)                       => KeyCode.Insert
      case Some(3)                       => KeyCode.Delete
      case Some(4)                       => KeyCode.End
      case Some(5)                       => KeyCode.PageUp
      case Some(6)                       => KeyCode.PageDown
      case Some(n) if n >= 11 && n <= 15 => KeyCode.F(n - 10)
      case Some(n) if n >= 17 && n <= 21 => KeyCode.F(n - 11)
      case Some(23)                      => KeyCode.F(11)
      case Some(24)                      => KeyCode.F(12)
      case _                             => KeyCode.Escape
    Event.Key(KeyEvent(code, modifiers))

  /** SS3 sequences (`ESC O x`): F1–F4 and some terminals' Home/End. */
  private def decodeSs3(): Event =
    read(EscapeTimeoutMillis) match
      case 'P' => key(KeyCode.F(1))
      case 'Q' => key(KeyCode.F(2))
      case 'R' => key(KeyCode.F(3))
      case 'S' => key(KeyCode.F(4))
      case 'H' => key(KeyCode.Home)
      case 'F' => key(KeyCode.End)
      case _   => key(KeyCode.Escape)

  /** SGR mouse report `CSI < b ; x ; y (M|m)`: button bits carry drag/scroll/modifier flags, coordinates are one-based.
    */
  private def decodeSgrMouse(params: String, isPress: Boolean): Event =
    params.split(';').toSeq.flatMap(_.toIntOption) match
      case Seq(button, column, row) =>
        val kind      =
          if (button & 64) != 0 then if (button & 1) != 0 then MouseEventKind.ScrollDown else MouseEventKind.ScrollUp
          else if (button & 32) != 0 then MouseEventKind.Drag
          else if isPress then MouseEventKind.Down
          else MouseEventKind.Up
        val modifiers =
          combine(
            if (button & 4) != 0 then Some(KeyModifiers.Shift) else None,
            if (button & 8) != 0 then Some(KeyModifiers.Alt) else None,
            if (button & 16) != 0 then Some(KeyModifiers.Ctrl) else None,
          )
        Event.Mouse(MouseEvent(column - 1, row - 1, kind, modifiers))
      case _                        => key(KeyCode.Escape)

  /** xterm modifier parameter: `code - 1` is a bitmask of shift/alt/ctrl. */
  private def modifiersFromCode(code: Int): KeyModifiers =
    val bits = code - 1
    combine(
      if (bits & 1) != 0 then Some(KeyModifiers.Shift) else None,
      if (bits & 2) != 0 then Some(KeyModifiers.Alt) else None,
      if (bits & 4) != 0 then Some(KeyModifiers.Ctrl) else None,
    )

  private def combine(flags: Option[KeyModifiers]*): KeyModifiers =
    flags.flatten.foldLeft(KeyModifiers.None)(_ | _)

  private def key(code: KeyCode): Event =
    Event.Key(KeyEvent(code, KeyModifiers.None))

  private val EscapeTimeoutMillis = 50L
