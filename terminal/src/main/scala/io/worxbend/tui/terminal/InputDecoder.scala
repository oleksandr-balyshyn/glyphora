package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent, KeyModifiers, MouseEvent, MouseEventKind}

/** Decodes terminal input bytes into [[Event]]s: printable keys, control keys, ANSI CSI/SS3 escape sequences for
  * navigation and function keys, kitty-protocol keys, bracketed paste, and both SGR and legacy X10 mouse reports.
  *
  * Reads through an injected `read(timeoutMillis) => Int` function (negative result = nothing available), so the
  * decoder is testable with scripted input and independent of JLine.
  *
  * A sequence the decoder does not recognize — a device-attributes reply, a cursor-position report, a torn sequence
  * that timed out mid-flight — decodes to `None` and is dropped. It must never be reported as a key: synthesizing an
  * `Escape` from unparsed bytes means a capability probe silently closes the user's dialog.
  *
  * `escapeTimeoutMillis` bounds how long the decoder waits for the rest of a sequence after `ESC`. It also decides how
  * long a lone `ESC` takes to report; raise it on high-latency links where a real `ESC [ A` can arrive split.
  */
private[terminal] final class InputDecoder(
    read: Long => Int,
    escapeTimeoutMillis: Long = InputDecoder.DefaultEscapeTimeoutMillis,
):

  import InputDecoder.*

  /** One character of lookahead, so a speculative read can be undone instead of swallowing input. */
  private var pushedBack = NoChar

  /** Decodes the next event, blocking up to `timeoutMillis` for the first character.
    *
    * `None` means "no event": either the timeout elapsed or the bytes read were a sequence with no glyphora meaning.
    */
  def decode(timeoutMillis: Long): Option[Event] =
    val first = next(timeoutMillis)
    if first < 0 then None
    else decodeFirst(first)

  private def decodeFirst(first: Int): Option[Event] =
    first match
      case 0x1b                   => decodeEscape()
      case 0x0d | 0x0a            => key(KeyCode.Enter)
      case 0x09                   => key(KeyCode.Tab)
      case 0x7f | 0x08            => key(KeyCode.Backspace)
      case c if c >= 1 && c <= 26 => Some(Event.Key(KeyEvent(KeyCode.Char('a' + c - 1), KeyModifiers.Ctrl)))
      case c                      => Some(printable(c, KeyModifiers.None))

  /** A printable character, recombining a UTF-16 surrogate pair into the single code point it encodes.
    *
    * The reader hands back UTF-16 code units, so an astral character (any emoji) arrives as two surrogates; reporting
    * them separately would deliver two unpaired halves that corrupt whatever string they land in.
    */
  private def printable(first: Int, modifiers: KeyModifiers): Event =
    if Character.isHighSurrogate(first.toChar) then
      val low = next(escapeTimeoutMillis)
      if low >= 0 && Character.isLowSurrogate(low.toChar) then
        Event.Key(KeyEvent(KeyCode.Char(Character.toCodePoint(first.toChar, low.toChar)), modifiers))
      else
        pushBack(low)
        Event.Key(KeyEvent(KeyCode.Char(first), modifiers))
    else Event.Key(KeyEvent(KeyCode.Char(first), modifiers))

  /** A lone ESC is the Escape key; ESC `[`/`O` opens a control sequence; ESC + printable is Alt+key. */
  private def decodeEscape(): Option[Event] =
    next(escapeTimeoutMillis) match
      case c if c < 0 => key(KeyCode.Escape)
      case '['        => decodeCsi()
      case 'O'        => decodeSs3()
      case 0x1b       => key(KeyCode.Escape) // ESC ESC: report one Escape, the second opens nothing
      case c          => Some(printable(c, KeyModifiers.Alt))

  /** CSI sequences: parameters (digits, `;`, `:` and the private `<`/`>`/`=`/`?` prefixes) then a final byte in
    * 0x40-0x7E.
    */
  private def decodeCsi(): Option[Event] =
    val params    = StringBuilder()
    var finalByte = NoChar
    var torn      = false
    while finalByte < 0 && !torn do
      val c = next(escapeTimeoutMillis)
      if c < 0 then torn = true // the sequence never completed: drop it rather than invent a key
      else if c >= 0x40 && c <= 0x7e then finalByte = c
      else if params.length < MaxParamLength then params.append(c.toChar)
      else torn = true // runaway parameter string: resynchronize rather than loop forever
    if torn then None else decodeCsiFinal(params.result(), finalByte)

  private def decodeCsiFinal(params: String, finalByte: Int): Option[Event] =
    if params.isEmpty && finalByte == 'M' then decodeX10Mouse()
    else if params.startsWith("<") && (finalByte == 'M' || finalByte == 'm') then
      decodeSgrMouse(params.drop(1), finalByte == 'M')
    else if isPrivateReply(params) then None // DA/DECRPM/kitty-query replies: not input, must not surface as keys
    else
      val numbers   = params.split(';').toSeq.filter(_.nonEmpty).flatMap(_.takeWhile(_ != ':').toIntOption)
      val modifiers = numbers.drop(1).headOption.map(modifiersFromCode).getOrElse(KeyModifiers.None)
      finalByte match
        case 'A'                       => Some(Event.Key(KeyEvent(KeyCode.Up, modifiers)))
        case 'B'                       => Some(Event.Key(KeyEvent(KeyCode.Down, modifiers)))
        case 'C'                       => Some(Event.Key(KeyEvent(KeyCode.Right, modifiers)))
        case 'D'                       => Some(Event.Key(KeyEvent(KeyCode.Left, modifiers)))
        case 'H'                       => Some(Event.Key(KeyEvent(KeyCode.Home, modifiers)))
        case 'F'                       => Some(Event.Key(KeyEvent(KeyCode.End, modifiers)))
        case 'P' | 'Q' | 'S'           => Some(Event.Key(KeyEvent(functionKey(finalByte), modifiers)))
        case 'R' if numbers.sizeIs < 2 => Some(Event.Key(KeyEvent(KeyCode.F(3), modifiers)))
        case 'Z'                       => Some(Event.Key(KeyEvent(KeyCode.Tab, KeyModifiers.Shift)))
        case 'I'                       => Some(Event.FocusGained)
        case 'O'                       => Some(Event.FocusLost)
        case 'u'                       => decodeKittyKey(numbers, modifiers)
        case '~' if numbers.headOption.contains(PasteStart) => Some(decodePaste())
        case '~'                                            => decodeTilde(numbers, modifiers)
        case _                                              => None

  /** kitty reports F1/F2/F4 as `CSI 1 P/Q/S` (and F3 as `CSI 1 R`), matching the SS3 forms. */
  private def functionKey(finalByte: Int): KeyCode =
    finalByte match
      case 'P' => KeyCode.F(1)
      case 'Q' => KeyCode.F(2)
      case 'S' => KeyCode.F(4)
      case _   => KeyCode.F(3)

  /** Private-parameter CSI sequences are replies from the terminal (DA, DECRPM, XTVERSION), never user input. */
  private def isPrivateReply(params: String): Boolean =
    params.nonEmpty && (params.head == '?' || params.head == '>' || params.head == '=')

  /** `CSI n ~` navigation/function keys; the modifier, when present, is the second parameter. */
  private def decodeTilde(numbers: Seq[Int], modifiers: KeyModifiers): Option[Event] =
    val code = numbers.headOption match
      case Some(1) | Some(7)             => Some(KeyCode.Home)
      case Some(2)                       => Some(KeyCode.Insert)
      case Some(3)                       => Some(KeyCode.Delete)
      case Some(4) | Some(8)             => Some(KeyCode.End)
      case Some(5)                       => Some(KeyCode.PageUp)
      case Some(6)                       => Some(KeyCode.PageDown)
      case Some(n) if n >= 11 && n <= 15 => Some(KeyCode.F(n - 10))
      case Some(n) if n >= 17 && n <= 21 => Some(KeyCode.F(n - 11))
      case Some(23)                      => Some(KeyCode.F(11))
      case Some(24)                      => Some(KeyCode.F(12))
      case _                             => None
    code.map(c => Event.Key(KeyEvent(c, modifiers)))

  /** Kitty keyboard protocol `CSI codepoint ; modifiers u`: unambiguous keys, no Esc timeout heuristic.
    *
    * Code points in the Private Use Area 57344-63743 are the protocol's functional keys, not text — emitting them as
    * characters would insert garbage glyphs into text inputs.
    */
  private def decodeKittyKey(numbers: Seq[Int], modifiers: KeyModifiers): Option[Event] =
    val code = numbers.headOption match
      case Some(27)                                                      => Some(KeyCode.Escape)
      case Some(13)                                                      => Some(KeyCode.Enter)
      case Some(9)                                                       => Some(KeyCode.Tab)
      case Some(127)                                                     => Some(KeyCode.Backspace)
      case Some(cp) if cp >= FunctionalKeyLow && cp <= FunctionalKeyHigh => kittyFunctionalKey(cp)
      case Some(cp) if cp >= 32 && Character.isValidCodePoint(cp)        => Some(KeyCode.Char(cp))
      case _                                                             => None
    code.map(c => Event.Key(KeyEvent(c, modifiers)))

  /** The kitty functional-key block, mapped onto glyphora's [[KeyCode]] vocabulary.
    *
    * Keypad keys report as their non-keypad equivalents (`KP_7` is `Home`, `KP_ENTER` is `Enter`, `KP_3` is `3`) —
    * glyphora has no separate keypad concept and an application almost never wants one. Media keys and the
    * modifier-only keys (a bare Shift press) are dropped: they are not key events in this model.
    */
  private def kittyFunctionalKey(codePoint: Int): Option[KeyCode] =
    codePoint match
      case 57358 | 57359 | 57360 | 57361 | 57362 | 57363 => None // caps/scroll/num lock, print screen, pause, menu
      case cp if cp >= 57376 && cp <= 57398 => Some(KeyCode.F(cp - 57376 + 13))       // F13-F35
      case cp if cp >= 57399 && cp <= 57408 => Some(KeyCode.Char('0' + (cp - 57399))) // KP_0-KP_9
      case 57409                            => Some(KeyCode.Char('.'))
      case 57410                            => Some(KeyCode.Char('/'))
      case 57411                            => Some(KeyCode.Char('*'))
      case 57412                            => Some(KeyCode.Char('-'))
      case 57413                            => Some(KeyCode.Char('+'))
      case 57414                            => Some(KeyCode.Enter)
      case 57415                            => Some(KeyCode.Char('='))
      case 57416                            => Some(KeyCode.Char(','))
      case 57417                            => Some(KeyCode.Left)
      case 57418                            => Some(KeyCode.Right)
      case 57419                            => Some(KeyCode.Up)
      case 57420                            => Some(KeyCode.Down)
      case 57421                            => Some(KeyCode.PageUp)
      case 57422                            => Some(KeyCode.PageDown)
      case 57423                            => Some(KeyCode.Home)
      case 57424                            => Some(KeyCode.End)
      case 57425                            => Some(KeyCode.Insert)
      case 57426                            => Some(KeyCode.Delete)
      case _ => None // media keys, modifier-only keys, unassigned

  /** Bracketed paste: everything between `CSI 200~` and `CSI 201~` is one paste payload.
    *
    * The terminator is matched against the accumulated tail rather than by consuming a fixed lookahead. A payload may
    * legitimately contain `ESC` (bracketed paste does not guarantee control-free text, and terminals differ on what
    * they filter), and any speculative read that cannot push back would swallow the real terminator's own `ESC [`.
    */
  private def decodePaste(): Event =
    val content = StringBuilder()
    var done    = false
    while !done && content.length < PasteLimit do
      val c = next(PasteTimeoutMillis)
      if c < 0 then done = true
      else
        content.append(c.toChar)
        if endsWithTerminator(content) then
          content.setLength(content.length - PasteEnd.length)
          done = true
    Event.Paste(content.result())

  private def endsWithTerminator(content: StringBuilder): Boolean =
    val start = content.length - PasteEnd.length
    if start < 0 then false
    else
      var offset = 0
      var same   = true
      while same && offset < PasteEnd.length do
        if content.charAt(start + offset) != PasteEnd.charAt(offset) then same = false
        offset += 1
      same

  /** SS3 sequences (`ESC O x`): F1-F4, Home/End, and the DECCKM application-mode cursor keys. */
  private def decodeSs3(): Option[Event] =
    next(escapeTimeoutMillis) match
      case 'P' => key(KeyCode.F(1))
      case 'Q' => key(KeyCode.F(2))
      case 'R' => key(KeyCode.F(3))
      case 'S' => key(KeyCode.F(4))
      case 'H' => key(KeyCode.Home)
      case 'F' => key(KeyCode.End)
      // application cursor keys: what a terminal sends once DECCKM (`CSI ?1h`) is on, e.g. under tmux `xterm-keys`
      case 'A' => key(KeyCode.Up)
      case 'B' => key(KeyCode.Down)
      case 'C' => key(KeyCode.Right)
      case 'D' => key(KeyCode.Left)
      case _   => None

  /** SGR mouse report `CSI < b ; x ; y (M|m)`: button bits carry drag/scroll/modifier flags, coordinates are one-based.
    */
  private def decodeSgrMouse(params: String, isPress: Boolean): Option[Event] =
    params.split(';').toSeq.flatMap(_.toIntOption) match
      case Seq(button, column, row) => Some(mouseEvent(button, column - 1, row - 1, isPress))
      case _                        => None

  /** Legacy X10 mouse report `CSI M b x y`: three raw bytes, each the value biased by 32.
    *
    * Needed because [[AnsiSequences.EnableMouseCapture]] *requests* SGR 1006 but a terminal that does not implement it
    * keeps sending X10 — and those three bytes would otherwise be decoded as text and injected as keystrokes.
    * Coordinates saturate at 223 (the byte is `32 + coordinate`), so a click past column 223 reports as 223.
    */
  private def decodeX10Mouse(): Option[Event] =
    val button = next(escapeTimeoutMillis)
    val column = next(escapeTimeoutMillis)
    val row    = next(escapeTimeoutMillis)
    if button < 0 || column < 0 || row < 0 then None
    else
      val bits    = button - 32
      // X10 has no separate release code: button 3 means "some button came up"
      val isPress = (bits & 3) != 3
      Some(mouseEvent(bits, column - 32 - 1, row - 32 - 1, isPress))

  private def mouseEvent(button: Int, x: Int, y: Int, isPress: Boolean): Event =
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
    Event.Mouse(MouseEvent(math.max(0, x), math.max(0, y), kind, modifiers))

  /** xterm modifier parameter: `code - 1` is a bitmask of shift/alt/ctrl (kitty adds super/hyper/meta/lock bits we
    * ignore).
    */
  private def modifiersFromCode(code: Int): KeyModifiers =
    val bits = code - 1
    combine(
      if (bits & 1) != 0 then Some(KeyModifiers.Shift) else None,
      if (bits & 2) != 0 then Some(KeyModifiers.Alt) else None,
      if (bits & 4) != 0 then Some(KeyModifiers.Ctrl) else None,
    )

  private def combine(flags: Option[KeyModifiers]*): KeyModifiers =
    flags.flatten.foldLeft(KeyModifiers.None)(_ | _)

  private def key(code: KeyCode): Option[Event] =
    Some(Event.Key(KeyEvent(code, KeyModifiers.None)))

  private def next(timeoutMillis: Long): Int =
    if pushedBack >= 0 then
      val c = pushedBack
      pushedBack = NoChar
      c
    else read(timeoutMillis)

  private def pushBack(c: Int): Unit =
    if c >= 0 then pushedBack = c

private[terminal] object InputDecoder:

  /** How long to wait for the rest of an escape sequence — and therefore how long a lone `ESC` takes to report. */
  val DefaultEscapeTimeoutMillis: Long = 50L

  private val NoChar             = -1
  private val PasteTimeoutMillis = 200L
  private val PasteLimit         = 1 << 20
  private val PasteStart         = 200
  private val PasteEnd           = "[201~"
  private val MaxParamLength     = 64
  private val FunctionalKeyLow   = 57344
  private val FunctionalKeyHigh  = 63743
