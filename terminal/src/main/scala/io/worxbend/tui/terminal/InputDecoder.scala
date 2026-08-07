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
    if first == 0x1b then decodeEscape() else decodeControl(first, KeyModifiers.None)

  /** One non-`ESC` input byte as a key, folding in whatever modifiers its prefix already established.
    *
    * Shared by the bare and the `ESC`-prefixed paths, so Alt+Backspace reports [[KeyCode.Backspace]] with
    * [[KeyModifiers.Alt]] rather than a raw `Char(127)`. A key spec names the key, so a decoded event that names
    * something else is a binding that can never fire — and the kitty path (`CSI 127;3u`) already reports the named key,
    * which would otherwise make the very same binding work on one terminal and not on another.
    *
    * The C0 controls that are not a named key are reported as the Ctrl-plus-character they encode: `0x01`-`0x1a` are
    * Ctrl+A to Ctrl+Z, `0x00` is Ctrl+Space, and `0x1c`-`0x1f` are Ctrl plus `\`, `]`, `^`, `_` (ASCII caret notation).
    * Reporting those as an unmodified `Char` is worse than useless — text inputs insert any character that arrives with
    * no modifier, so the control code lands in the model as an invisible, zero-width, corrupt cell.
    */
  private def decodeControl(byte: Int, modifiers: KeyModifiers): Option[Event] =
    byte match
      case 0x0d | 0x0a                 => key(KeyCode.Enter, modifiers)
      case 0x09                        => key(KeyCode.Tab, modifiers)
      case 0x7f | 0x08                 => key(KeyCode.Backspace, modifiers)
      case c if c >= 1 && c <= 26      => key(KeyCode.Char('a' + c - 1), modifiers | KeyModifiers.Ctrl)
      case 0                           => key(KeyCode.Char(' '), modifiers | KeyModifiers.Ctrl)
      case c if c >= 0x1c && c <= 0x1f => key(KeyCode.Char(c + 0x40), modifiers | KeyModifiers.Ctrl)
      case c                           => printable(c, modifiers)

  /** A printable character, recombining a UTF-16 surrogate pair into the single code point it encodes.
    *
    * The reader hands back UTF-16 code units, so an astral character (any emoji) arrives as two surrogates; reporting
    * them separately would deliver two unpaired halves that corrupt whatever string they land in. A surrogate that
    * turns out to have no partner is dropped for the same reason: half a character is not a keypress, and the byte that
    * followed it is pushed back so the next `decode` still sees it.
    */
  private def printable(first: Int, modifiers: KeyModifiers): Option[Event] =
    if Character.isHighSurrogate(first.toChar) then
      val low = next(escapeTimeoutMillis)
      if low >= 0 && Character.isLowSurrogate(low.toChar) then
        key(KeyCode.Char(Character.toCodePoint(first.toChar, low.toChar)), modifiers)
      else
        pushBack(low)
        None
    else if Character.isLowSurrogate(first.toChar) then None
    else key(KeyCode.Char(first), modifiers)

  /** A lone ESC is the Escape key; ESC `[`/`O` opens a control sequence; ESC `]`/`P`/`_` opens a terminal reply; ESC
    * plus anything else is Alt plus whatever that byte decodes to on its own.
    */
  private def decodeEscape(): Option[Event] =
    next(escapeTimeoutMillis) match
      case c if c < 0      => key(KeyCode.Escape)
      case '['             => decodeCsi()
      case 'O'             => decodeSs3()
      case 0x1b            => key(KeyCode.Escape) // ESC ESC: report one Escape, the second opens nothing
      case ']' | 'P' | '_' => skipControlString()
      case c               => decodeControl(c, KeyModifiers.Alt)

  /** Consumes an OSC (`ESC ]`), DCS (`ESC P`) or APC (`ESC _`) control string up to its terminator and drops it.
    *
    * These carry terminal *replies* — an OSC 11 background-colour answer, a DECRQSS status report, a kitty graphics
    * acknowledgement. glyphora never asks for one, but anything sharing the tty (tmux, a shell prompt, a program run
    * under [[Backend.suspend]]) can leave one in the buffer, and without this the introducer surfaces as Alt+`]` and
    * every byte of the payload is dispatched as a keystroke — where a `q` quits and an Enter submits.
    *
    * The price is that Alt+`]`, Alt+Shift+P and Alt+`_` can no longer be bound: their legacy encoding is byte-identical
    * to a control-string introducer, and one byte of lookahead cannot tell the two apart. That trade is deliberate — a
    * dead rare key beats a reply typing itself into the focused widget.
    */
  private def skipControlString(): Option[Event] =
    var consumed = 0
    var done     = false
    while !done && consumed < MaxControlStringLength do
      val c = next(escapeTimeoutMillis)
      consumed += 1
      if c < 0 || c == Bel then done = true
      else if c == 0x1b then
        // the string terminator is `ESC \`; an ESC followed by anything else means a new sequence has started, so the
        // reply was truncated and that byte is handed back rather than swallowed
        val terminator = next(escapeTimeoutMillis)
        if terminator != '\\' then pushBack(terminator)
        done = true
    None

  /** CSI sequences: parameters (digits, `;`, `:` and the private `<`/`>`/`=`/`?` prefixes) then a final byte in
    * 0x40-0x7E.
    *
    * Parameters past [[MaxParamLength]] are not kept, but the sequence is still read to its final byte before being
    * dropped: a verbose primary-DA reply really does overrun that budget on some terminals, and stopping mid-sequence
    * left the remaining parameter digits in the buffer to be decoded as keystrokes at startup. [[MaxSequenceLength]] is
    * the backstop that keeps that resynchronization from running forever.
    */
  private def decodeCsi(): Option[Event] =
    val params    = StringBuilder()
    var finalByte = NoChar
    var torn      = false
    var runaway   = false
    var consumed  = 0
    while finalByte < 0 && !torn do
      val c = next(escapeTimeoutMillis)
      consumed += 1
      if c < 0 then torn = true // the sequence never completed: drop it rather than invent a key
      else if c >= 0x40 && c <= 0x7e then finalByte = c
      else if params.length < MaxParamLength then params.append(c.toChar)
      else if consumed >= MaxSequenceLength then torn = true // not a sequence at all: stop looking for a final byte
      else runaway = true // over the parameter budget: read on to the final byte, then drop the whole sequence
    if torn || runaway then None else decodeCsiFinal(params.result(), finalByte)

  private def decodeCsiFinal(params: String, finalByte: Int): Option[Event] =
    if params.isEmpty && finalByte == 'M' then decodeX10Mouse()
    else if params.startsWith("<") && (finalByte == 'M' || finalByte == 'm') then
      decodeSgrMouse(params.drop(1), finalByte == 'M')
    else if isPrivateReply(params) then None // DA/DECRPM/kitty-query replies: not input, must not surface as keys
    else
      val numbers = params.split(';').toSeq.filter(_.nonEmpty).flatMap(_.takeWhile(_ != ':').toIntOption)
      numbers.drop(1).headOption match
        case Some(code) => modifiersFromCode(code).flatMap(decodeCsiKey(numbers, finalByte, _))
        case None       => decodeCsiKey(numbers, finalByte, KeyModifiers.None)

  private def decodeCsiKey(numbers: Seq[Int], finalByte: Int, modifiers: KeyModifiers): Option[Event] =
    finalByte match
      case 'A'                                            => key(KeyCode.Up, modifiers)
      case 'B'                                            => key(KeyCode.Down, modifiers)
      case 'C'                                            => key(KeyCode.Right, modifiers)
      case 'D'                                            => key(KeyCode.Left, modifiers)
      case 'H'                                            => key(KeyCode.Home, modifiers)
      case 'F'                                            => key(KeyCode.End, modifiers)
      case 'P' | 'Q' | 'S'                                => key(functionKey(finalByte), modifiers)
      case 'R' if isFunctionKey3(numbers)                 => key(KeyCode.F(3), modifiers)
      case 'Z'                                            => key(KeyCode.Tab, KeyModifiers.Shift)
      case 'I'                                            => Some(Event.FocusGained)
      case 'O'                                            => Some(Event.FocusLost)
      case 'u'                                            => decodeKittyKey(numbers, modifiers)
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

  /** Whether a `CSI … R` is F3 rather than a cursor-position report.
    *
    * The two collide: F3 (modified) is `CSI 1 ; modifier R`, a CPR is `CSI row ; column R`, and a CPR must never
    * surface as a key. They are told apart by F3's first parameter always being 1 and its second always being a
    * plausible xterm modifier code — rejecting every two-parameter form instead, as this used to, drops Ctrl+F3 and
    * Shift+F3 while their F1/F2/F4 neighbours work. A report of row 1 at a column of 16 or less stays ambiguous and is
    * read as F3; glyphora never requests a CPR, so nothing it sends can produce one.
    */
  private def isFunctionKey3(numbers: Seq[Int]): Boolean =
    numbers match
      case Seq()        => true
      case Seq(1)       => true
      case Seq(1, code) => code >= 1 && code <= MaxModifierCode
      case _            => false

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
      case Some(cp) if cp >= 32 && isTextCodePoint(cp)                   => Some(KeyCode.Char(cp))
      case _                                                             => None
    code.map(c => Event.Key(KeyEvent(c, modifiers)))

  /** Whether `codePoint` is a character a string can actually hold.
    *
    * `Character.isValidCodePoint` only bounds by 0x10FFFF, so on its own it admits the surrogate range — and a lone
    * surrogate corrupts every string it is appended to, which is exactly what [[printable]] exists to prevent.
    */
  private def isTextCodePoint(codePoint: Int): Boolean =
    Character.isValidCodePoint(codePoint) && !(codePoint >= 0xd800 && codePoint <= 0xdfff)

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
    * The terminator is matched against a rolling tail rather than by consuming a fixed lookahead. A payload may
    * legitimately contain `ESC` (bracketed paste does not guarantee control-free text, and terminals differ on what
    * they filter), and any speculative read that cannot push back would swallow the real terminator's own `ESC [`.
    *
    * [[PasteLimit]] caps how much of the payload is *kept*, never how much is read. Stopping the loop at the cap left
    * the rest of the payload — and its terminator — in the buffer, where the following `decode` calls read it as
    * ordinary keystrokes and dispatch it into whatever has focus: a `q` quits, an Enter submits. Past the cap the text
    * is truncated but the terminator is still consumed, so the stream stays aligned. [[PasteDrainLimit]] bounds even
    * that, so a paste whose terminator never arrives cannot spin here forever.
    */
  private def decodePaste(): Event =
    val content = StringBuilder()
    val tail    = StringBuilder()
    var read    = 0
    var done    = false
    while !done && read < PasteDrainLimit do
      val c = next(PasteTimeoutMillis)
      if c < 0 then done = true
      else
        read += 1
        if content.length < PasteLimit then content.append(c.toChar)
        tail.append(c.toChar)
        if tail.length > PasteEnd.length then tail.deleteCharAt(0)
        if isTerminator(tail) then
          // trim only the terminator characters that were actually kept: past the cap, none of them were
          val kept = math.max(0, content.length - (read - PasteEnd.length))
          content.setLength(content.length - kept)
          done = true
    Event.Paste(content.result())

  /** Whether the rolling tail *is* the paste terminator. Compared character by character so that a payload the size of
    * a file costs no allocation per byte.
    */
  private def isTerminator(tail: StringBuilder): Boolean =
    tail.length == PasteEnd.length && {
      var offset = 0
      var same   = true
      while same && offset < PasteEnd.length do
        if tail.charAt(offset) != PasteEnd.charAt(offset) then same = false
        offset += 1
      same
    }

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
      case Seq(button, column, row) => mouseEvent(button, column - 1, row - 1, isPress)
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
      mouseEvent(bits, column - 32 - 1, row - 32 - 1, isPress)

  private def mouseEvent(button: Int, x: Int, y: Int, isPress: Boolean): Option[Event] =
    val kind      =
      if (button & 64) != 0 then wheelKind(button)
      else if (button & 32) != 0 then Some(MouseEventKind.Drag)
      else if isPress then Some(MouseEventKind.Down)
      else Some(MouseEventKind.Up)
    val modifiers =
      combine(
        if (button & 4) != 0 then Some(KeyModifiers.Shift) else None,
        if (button & 8) != 0 then Some(KeyModifiers.Alt) else None,
        if (button & 16) != 0 then Some(KeyModifiers.Ctrl) else None,
      )
    kind.map(k => Event.Mouse(MouseEvent(math.max(0, x), math.max(0, y), k, modifiers)))

  /** Wheel buttons 64 and 65 are wheel-up and wheel-down; 66 and 67 are wheel-left and wheel-right, which
    * [[MouseEventKind]] has no vocabulary for.
    *
    * A horizontal report is therefore dropped rather than folded onto the vertical kinds — reading the low bit alone
    * turned every sideways trackpad swipe into a scroll of the focused list, and doing nothing is the honest answer
    * until the event ADT grows horizontal variants.
    */
  private def wheelKind(button: Int): Option[MouseEventKind] =
    button & 3 match
      case 0 => Some(MouseEventKind.ScrollUp)
      case 1 => Some(MouseEventKind.ScrollDown)
      case _ => None

  /** xterm modifier parameter: `code - 1` is a bitmask of shift/alt/ctrl.
    *
    * kitty extends it with super (8), hyper (16) and meta (32), which have no [[KeyModifiers]] counterpart, and with
    * caps-lock (64) and num-lock (128), which are lock *states* rather than held modifiers and carry no meaning for a
    * binding. A key held with a modifier glyphora cannot represent is dropped, never delivered bare: Super+Q arriving
    * as a plain `q` fires the quit binding.
    */
  private def modifiersFromCode(code: Int): Option[KeyModifiers] =
    val bits = code - 1
    if (bits & UnrepresentableModifiers) != 0 then None
    else
      Some(
        combine(
          if (bits & 1) != 0 then Some(KeyModifiers.Shift) else None,
          if (bits & 2) != 0 then Some(KeyModifiers.Alt) else None,
          if (bits & 4) != 0 then Some(KeyModifiers.Ctrl) else None,
        )
      )

  private def combine(flags: Option[KeyModifiers]*): KeyModifiers =
    flags.flatten.foldLeft(KeyModifiers.None)(_ | _)

  private def key(code: KeyCode, modifiers: KeyModifiers = KeyModifiers.None): Option[Event] =
    Some(Event.Key(KeyEvent(code, modifiers)))

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
  private val Bel                = 7
  private val PasteTimeoutMillis = 200L
  private val PasteLimit         = 1 << 20
  private val PasteStart         = 200
  private val PasteEnd           = "[201~"
  private val MaxParamLength     = 64
  private val FunctionalKeyLow   = 57344
  private val FunctionalKeyHigh  = 63743

  /** How much of an oversized paste is read (and discarded) while looking for the terminator, so that a payload whose
    * terminator never arrives cannot hold the event loop indefinitely.
    */
  private val PasteDrainLimit = PasteLimit * 16

  /** How many bytes one CSI sequence may consume before it is abandoned outright. Only reachable once the parameter
    * budget has already overflowed, and far past anything a real terminal emits.
    */
  private val MaxSequenceLength = 4096

  /** The same bound for a control string, whose payload (a colour, a title, a graphics acknowledgement) is short. */
  private val MaxControlStringLength = 4096

  /** The largest xterm modifier parameter: `1 + shift|alt|ctrl|meta`. Used only to tell a modified F3 from a
    * cursor-position report, which are otherwise the same shape.
    */
  private val MaxModifierCode = 16

  /** kitty's super, hyper and meta bits, none of which [[KeyModifiers]] can express. */
  private val UnrepresentableModifiers = 8 | 16 | 32
