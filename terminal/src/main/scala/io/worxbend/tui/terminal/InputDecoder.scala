package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent, KeyModifiers, MouseButton, MouseEvent, MouseEventKind, Position}

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
  * An instance is **not** thread-safe: it owns a shared read cursor and one character of pushback, so exactly one
  * reading thread may call `decode`. Two threads calling it at once interleave their reads into sequences neither of
  * them sent, and the single pushback slot can hand one thread's character to the other. `JLine3Backend` upholds this
  * by only ever decoding from the thread parked in its blocking read.
  *
  * `escapeTimeoutMillis` bounds how long the decoder waits for the rest of a sequence after `ESC`. It also decides how
  * long a lone `ESC` takes to report; raise it on high-latency links where a real `ESC [ A` can arrive split.
  *
  * An `ESC` always *starts* a sequence, so one arriving mid-sequence aborts whatever was being read (ECMA-48 says so,
  * and every terminal relies on it). The decoder hands that `ESC` back rather than consuming it, which means two
  * genuine Escape presses report two `Escape` keys — the second one after the escape timeout — where a truncated
  * sequence followed by a real one reports only the real one.
  */
private[terminal] final class InputDecoder(
    read: Long => Int,
    escapeTimeoutMillis: Long = InputDecoder.DefaultEscapeTimeoutMillis,
):

  import InputDecoder.*

  /** One character of lookahead, so a speculative read can be undone instead of swallowing input. */
  private var pushedBack = NoChar

  /** Events decoded while [[readCursorReport]] was waiting for its reply, waiting their turn.
    *
    * A cursor-position query is a round trip: the reply travels back on the same stream the user's keystrokes do, and a
    * key pressed while it is in flight arrives first. Dropping those keys would make an inline app lose input every
    * time it anchored itself, so they are held here and handed to the next [[decode]] in the order they arrived.
    */
  private val deferred = scala.collection.mutable.Queue.empty[Event]

  /** Whether a `CSI … R` currently means a cursor-position report rather than the F3 key. See [[readCursorReport]]. */
  private var awaitingCursorReport = false

  /** Where the report that [[readCursorReport]] is waiting for said the cursor was, once one arrives. */
  private var reportedCursor: Option[Position] = None

  /** Decodes the next event, blocking up to `timeoutMillis` for the first character.
    *
    * `None` means "no event": either the timeout elapsed or the bytes read were a sequence with no glyphora meaning.
    *
    * An event held back by a cursor-position query is delivered first and costs no read at all, so a key pressed during
    * that round trip reaches the application in the order it was typed.
    */
  def decode(timeoutMillis: Long): Option[Event] =
    if deferred.nonEmpty then Some(deferred.dequeue())
    else decodeOnce(timeoutMillis)

  /** [[decode]] without the deferred queue: one read, one decode. */
  private def decodeOnce(timeoutMillis: Long): Option[Event] =
    val first = next(timeoutMillis)
    if first < 0 then None
    else decodeFirst(first)

  /** Reads the terminal's reply to a Device Status Report, giving up after `timeoutMillis`.
    *
    * Called only by [[JLine3Backend.queryCursorPosition]], immediately after it has written `ESC[6n`. While this is
    * running, a `CSI row ; column R` is read as that report; at every other moment the same bytes stay the F3 key, and
    * [[isFunctionKey3]] explains why the two cannot be told apart on their contents alone.
    *
    * Anything else that decodes while the reply is awaited is a real event the user produced, so it is queued rather
    * than dropped and comes back out of the next [[decode]] calls in order. `None` means the terminal did not answer in
    * time — which is the ordinary outcome on a terminal that does not implement the report, not a failure.
    *
    * Subject to the same one-reader rule as [[decode]], and for a sharper reason: a second thread decoding concurrently
    * would take the reply out of this one's hands and drop it as an unrecognised sequence.
    */
  private[terminal] def readCursorReport(timeoutMillis: Long): Option[Position] =
    awaitingCursorReport = true
    reportedCursor = None
    val deadline = System.nanoTime() + timeoutMillis * NanosPerMilli
    try
      while reportedCursor.isEmpty && System.nanoTime() < deadline do
        val remaining = math.max(1L, (deadline - System.nanoTime()) / NanosPerMilli)
        decodeOnce(remaining).foreach(event => deferred.enqueue(event))
      reportedCursor
    finally awaitingCursorReport = false

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
    *
    * The 8-bit C1 range (`0x80`-`0x9f`) names no key at all — it is what an 8-bit-mode terminal sends instead of an
    * `ESC` prefix — so it decodes to `None` for the same reason: reporting one as an unmodified `Char` corrupts every
    * text model it reaches. The reader hands back UTF-16 code units, never raw UTF-8 bytes, so a value in that range
    * here is a genuine C1 character and never a continuation byte.
    */
  private def decodeControl(byte: Int, modifiers: KeyModifiers): Option[Event] =
    byte match
      case 0x0d | 0x0a                 => key(KeyCode.Enter, modifiers)
      case 0x09                        => key(KeyCode.Tab, modifiers)
      case 0x7f | 0x08                 => key(KeyCode.Backspace, modifiers)
      case c if c >= 1 && c <= 26      => key(KeyCode.Char('a' + c - 1), modifiers | KeyModifiers.Ctrl)
      case 0                           => key(KeyCode.Char(' '), modifiers | KeyModifiers.Ctrl)
      case c if c >= 0x1c && c <= 0x1f => key(KeyCode.Char(c + 0x40), modifiers | KeyModifiers.Ctrl)
      case c if c >= 0x80 && c <= 0x9f => None
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
  private def decodeEscape(): Option[Event] = decodeEscapeBody(next(escapeTimeoutMillis))

  /** The body of [[decodeEscape]], taking the character after the `ESC` as an argument rather than reading it.
    *
    * Split out so [[skipControlString]] can re-dispatch a character it has already consumed. It cannot push that
    * character back, because the `ESC` that preceded it is gone by then and the single pushback slot holds one
    * character, not two.
    */
  private def decodeEscapeBody(byte: Int): Option[Event] =
    byte match
      case c if c < 0      => key(KeyCode.Escape)
      case '['             => decodeCsi()
      case 'O'             => decodeSs3()
      case 0x1b            =>
        // ESC ESC: the second ESC opens the *next* sequence, so it is handed back rather than swallowed. That is what
        // makes `ESC` followed by an arrow key report Escape and then Up instead of Escape and the literal text `[A`.
        pushBack(0x1b)
        key(KeyCode.Escape)
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
    *
    * A truncated reply — one whose `ESC` opens a new sequence instead of the `ESC \` terminator — is *not* dropped: the
    * character after that `ESC` is re-dispatched through [[decodeEscapeBody]] and whatever it decodes to is returned
    * from here. Pushing it back instead would lose the `ESC` (the pushback slot holds one character), and the sequence
    * it opened would then be delivered as ordinary keystrokes, where a `q` quits and an Enter submits.
    */
  private def skipControlString(): Option[Event] =
    var consumed              = 0
    var done                  = false
    var result: Option[Event] = None
    while !done && consumed < MaxControlStringLength do
      val c = next(escapeTimeoutMillis)
      consumed += 1
      if c < 0 || c == Bel then done = true
      else if c == 0x1b then
        val terminator = next(escapeTimeoutMillis)
        done = true
        // `ESC \` ends the string and means nothing else; anything else after the ESC is a new sequence that has to be
        // decoded here. A timed-out read stays `None`: turning a truncated reply into an `Escape` is exactly the
        // invented keypress this class promises never to report.
        if terminator >= 0 && terminator != '\\' then result = decodeEscapeBody(terminator)
    result

  /** CSI sequences: parameters (digits, `;`, `:` and the private `<`/`>`/`=`/`?` prefixes) then a final byte in
    * 0x40-0x7E. [[scanCsi]] reads the sequence off the wire and says how it ended; only a complete one becomes an
    * event, because a torn or oversized sequence must never be turned into an invented key.
    */
  private def decodeCsi(): Option[Event] =
    scanCsi() match
      case CsiScan.Complete(params, finalByte) => decodeCsiFinal(params, finalByte)
      case CsiScan.Torn | CsiScan.Overrun      => None

  /** Reads the body of a CSI sequence — everything after the `ESC [` introducer — and names how it ended.
    *
    * Called only from [[decodeCsi]], on the same thread as [[decode]], because it advances the decoder's shared read
    * cursor (and therefore its one-character pushback slot). It never decides what a sequence *means*; it only hands
    * back the parameters and the final byte, or the reason there are none.
    *
    * The three outcomes:
    *   - [[CsiScan.Complete]] — a final byte in 0x40-0x7E arrived with the parameters intact.
    *   - [[CsiScan.Torn]] — the read timed out mid-flight, so the sequence never finished arriving.
    *   - [[CsiScan.Overrun]] — the sequence outgrew [[MaxParamLength]] (and possibly [[MaxSequenceLength]] on top of
    *     that), so its parameters are no longer trustworthy.
    *
    * Only `Complete` produces an event; the other two are dropped by the caller. Past [[MaxParamLength]] the scan keeps
    * reading anyway, discarding characters until the final byte, because a verbose primary-DA reply really does overrun
    * that budget on some terminals and stopping mid-sequence left the remaining parameter digits in the buffer to be
    * decoded as keystrokes at startup. [[MaxSequenceLength]] is the backstop that keeps that resynchronization from
    * running forever: reached only once the parameter budget has already overflowed, it abandons the read outright.
    */
  private def scanCsi(): CsiScan =
    val params = StringBuilder()

    // `overrun` records that parameters have been dropped; the scan continues so the stream stays aligned
    @annotation.tailrec
    def scan(consumed: Int, overrun: Boolean): CsiScan =
      val c    = next(escapeTimeoutMillis)
      val read = consumed + 1
      if c < 0 then CsiScan.Torn
      else if c == 0x1b then
        // an ESC means the sequence in flight was torn off by a new one; hand it back so the next `decode` re-parses it
        // rather than consuming the new sequence's `[` as this one's final byte
        pushBack(c)
        CsiScan.Torn
      else if c >= 0x40 && c <= 0x7e then if overrun then CsiScan.Overrun else CsiScan.Complete(params.result(), c)
      else if params.length < MaxParamLength then
        params.append(c.toChar)
        scan(read, overrun)
      else if read >= MaxSequenceLength then CsiScan.Overrun // not a sequence at all: stop looking for a final byte
      else scan(read, overrun = true)

    scan(consumed = 0, overrun = false)

  private def decodeCsiFinal(params: String, finalByte: Int): Option[Event] =
    if params.isEmpty && finalByte == 'M' then decodeX10Mouse()
    else if params.startsWith("<") && (finalByte == 'M' || finalByte == 'm') then
      decodeSgrMouse(params.drop(1), finalByte == 'M')
    else if finalByte == 'M' && params.nonEmpty && !params.startsWith("<") then decodeUrxvtMouse(params)
    else if isPrivateReply(params) then None // DA/DECRPM/kitty-query replies: not input, must not surface as keys
    // A cursor-position report is caught here rather than down in `decodeCsiKey`, because its second parameter is a
    // *column*, and the modifier extraction below rejects any second parameter above 16 — so a report from anywhere
    // right of column 16 would never have reached a case at all.
    else if awaitingCursorReport && finalByte == 'R' then captureCursorReport(parameterNumbers(params))
    else
      val numbers = parameterNumbers(params)
      numbers.drop(1).headOption match
        case Some(code) => modifiersFromCode(code).flatMap(decodeCsiKey(numbers, finalByte, _))
        case None       => decodeCsiKey(numbers, finalByte, KeyModifiers.None)

  /** The numeric parameters of a CSI sequence, in order.
    *
    * One reading of the parameter string for every sequence shape, because the two that existed disagreed: the mouse
    * one accepted neither an empty field nor a sub-parameter, so an SGR report written as `CSI <0:1;10;5M` — legal, and
    * what a terminal that reports a sub-parameter sends — matched nothing and the whole click was dropped.
    *
    * An empty field (`CSI ;5H`, a sequence that omits its first parameter) is skipped rather than defaulted, and a `:`
    * sub-parameter (kitty writes the shifted key and the base layout key after a colon) is discarded: glyphora has no
    * vocabulary for either, and a parameter it cannot read must not shift the ones after it out of position.
    */
  private def parameterNumbers(params: String): Seq[Int] =
    params.split(';').toSeq.filter(_.nonEmpty).flatMap(_.takeWhile(_ != ':').toIntOption)

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
      case 'Z'                                            => key(KeyCode.Tab, modifiers | KeyModifiers.Shift)
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

  /** Records a cursor-position report and reports no event for it.
    *
    * Deliberately not an [[Event]]. A cursor report is a reply to something this library asked, not something the user
    * did, and adding a case for it to the event ADT would put a branch no application ever writes into every exhaustive
    * `match` over `Event` in every application.
    *
    * The wire format is one-based row then column; [[Position]] is zero-based column then row, which is the coordinate
    * space every other part of glyphora uses. Both conversions happen here, once.
    */
  private def captureCursorReport(numbers: Seq[Int]): Option[Event] =
    numbers match
      case Seq(row, column) if row >= 1 && column >= 1 =>
        reportedCursor = Some(Position(column - 1, row - 1))
        None
      case _                                           => None

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
    numbers.headOption.flatMap(CsiKeys.tildeKey).map(c => Event.Key(KeyEvent(c, modifiers)))

  /** Kitty keyboard protocol `CSI codepoint ; modifiers u`: unambiguous keys, no Esc timeout heuristic.
    *
    * The code point vocabulary itself lives in [[KittyKeys]]; [[foldShiftedChar]] rewrites the one shape kitty reports
    * differently from every legacy terminal.
    */
  private def decodeKittyKey(numbers: Seq[Int], modifiers: KeyModifiers): Option[Event] =
    numbers.headOption.flatMap(KittyKeys.keyCode).map(c => Event.Key(foldShiftedChar(c, modifiers)))

  /** Rewrites a kitty "base key plus a Shift bit" report into the single legacy encoding, so a key spec has one
    * spelling that works on every terminal.
    *
    * Kitty reports the *unshifted* key together with a Shift modifier — Alt+Shift+A arrives as `CSI 97;4u`, which is
    * `Char('a')` with Alt|Shift. A legacy terminal sends `ESC 'A'` for the same keypress, which [[decodeControl]]
    * reports as `Char('A')` with Alt and no Shift bit. Left alone, the two encodings reach the binding table as
    * different keys: `"alt+A"` would fire under xterm and never under kitty, and `"alt+shift+a"` the reverse. Folding
    * here means exactly one vocabulary ever reaches a [[KeyEvent]], and the legacy one is chosen because it is what
    * every other code path in the library already produces.
    *
    * Two cases, matching what the legacy wire format can actually carry:
    *   - Ctrl is *not* held: emit the uppercase code point and clear Shift — `CSI 97;4u` becomes `Char('A')` + Alt.
    *   - Ctrl *is* held: emit the base (lower-case) code point and clear Shift — a legacy terminal collapses both
    *     Ctrl+S and Ctrl+Shift+S onto the control byte `0x13`, which decodes as `Char('s')` + Ctrl, so case cannot
    *     survive there and must not survive here either. `CSI 115;6u` therefore decodes identically to `0x13`.
    *
    * A character with no uppercase form (a digit, punctuation, an emoji) is left exactly as reported: dropping its
    * Shift bit would throw away the only signal that the key was shifted at all, and reconstructing the shifted glyph
    * would need a keyboard-layout table the decoder does not have.
    */
  private def foldShiftedChar(code: KeyCode, modifiers: KeyModifiers): KeyEvent =
    code match
      case KeyCode.Char(codePoint) if modifiers.hasAny(KeyModifiers.Shift) =>
        val upper = Character.toUpperCase(codePoint)
        if upper == codePoint then KeyEvent(code, modifiers)
        else if modifiers.hasAny(KeyModifiers.Ctrl) then KeyEvent(code, modifiers.without(KeyModifiers.Shift))
        else KeyEvent(KeyCode.Char(upper), modifiers.without(KeyModifiers.Shift))
      case _                                                               => KeyEvent(code, modifiers)

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
          trimTerminator(content, read)
          done = true
    Event.Paste(content.result())

  /** Drops whatever part of the paste terminator was appended to `content`.
    *
    * `charsRead` counts everything consumed from the wire, the terminator included, while `content` holds only what
    * fitted under [[PasteLimit]] — so the terminator's own characters are in `content` exactly when the payload stayed
    * under the cap, and only that many of them are trimmed off the end.
    */
  private def trimTerminator(content: StringBuilder, charsRead: Int): Unit =
    val payloadChars        = charsRead - PasteEnd.length
    val terminatorCharsKept = math.max(0, content.length - payloadChars)
    content.setLength(content.length - terminatorCharsKept)

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

  /** SS3 sequences (`ESC O x`): F1-F4, Home/End, the DECCKM application-mode cursor keys, and the DECKPAM application
    * keypad.
    *
    * The keypad block is what a terminal sends for the numeric keypad once DECKPAM (`ESC =`, "application keypad mode")
    * is on — the same tmux `xterm-keys` setting that motivates the DECCKM cursor arms below turns it on too. Before
    * these arms existed every one of those finals fell through to `None`, so on such a terminal the whole numeric
    * keypad was dead: pressing keypad `4` typed nothing at all rather than a `4`.
    *
    * Keypad finals fold onto the plain keys they print, exactly as [[KittyKeys]] folds its `KP_*` code points. glyphora
    * has no separate keypad concept, so the same physical key must produce the same event whichever protocol the
    * terminal happens to speak.
    */
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
      // application keypad (DECKPAM). The finals are those in XTerm's `ctlseqs.ms`, "PC-Style Function Keys".
      case 'M' => key(KeyCode.Enter)     // keypad Enter
      case 'j' => key(KeyCode.Char('*'))
      case 'k' => key(KeyCode.Char('+'))
      case 'l' => key(KeyCode.Char(',')) // keypad separator, a comma on the layouts that have one
      case 'm' => key(KeyCode.Char('-'))
      case 'n' => key(KeyCode.Char('.')) // keypad Delete doubles as the decimal point
      case 'o' => key(KeyCode.Char('/'))
      case 'X' => key(KeyCode.Char('='))
      case ' ' => key(KeyCode.Char(' '))
      case 'I' => key(KeyCode.Tab)
      // `p` through `y` are the digits 0 through 9, in order. Placed after the letter arms above so that the guard
      // cannot shadow one of them if a final is ever added inside the range.
      case digit if digit >= 'p' && digit <= 'y' => key(KeyCode.Char(('0' + (digit - 'p')).toChar))
      case _                                     => None

  /** SGR mouse report `CSI < b ; x ; y (M|m)`: button bits carry drag/scroll/modifier flags, coordinates are one-based.
    */
  private def decodeSgrMouse(params: String, isPress: Boolean): Option[Event] =
    parameterNumbers(params) match
      case Seq(button, column, row) => mouseEvent(button, column - 1, row - 1, isPress)
      case _                        => None

  /** urxvt mouse report `CSI b ; x ; y M` (DEC mode 1015).
    *
    * The same button byte X10 uses, biased by 32, but with the coordinates written as decimal text rather than as raw
    * bytes — so it is readable past the column 95 where [[decodeX10Mouse]] has to give up. It exists for a terminal
    * that ignores the SGR request (mode 1006); a terminal that honours SGR never sends this form, because 1006 is
    * requested after 1015 and wins.
    *
    * Like X10, and unlike SGR, it has no per-button release code — button bits `3` mean "some button came up" — so
    * whether this is a press is derived from the bits, not from the final byte, which is always `M`.
    *
    * The `button >= 32` guard is what keeps some other three-parameter CSI sequence ending in `M` from being read as a
    * click: every real report carries the +32 bias, so a smaller first parameter cannot be one.
    */
  private def decodeUrxvtMouse(params: String): Option[Event] =
    parameterNumbers(params) match
      case Seq(button, column, row) if button >= X10Bias =>
        val bits    = button - X10Bias
        val isPress = (bits & ButtonMask) != NoButtonHeld
        mouseEvent(bits, column - 1, row - 1, isPress)
      case _                                             => None

  /** Legacy X10 mouse report `CSI M b x y`: three raw bytes, each the value biased by 32.
    *
    * This branch exists only for a terminal that ignores [[AnsiSequences.EnableMouseCapture]]'s request for SGR 1006
    * and keeps sending X10 — otherwise those three bytes are decoded as text and injected as keystrokes. SGR 1006 is
    * unaffected by everything below, because its coordinates are decimal *text* and go through [[decodeSgrMouse]].
    *
    * X10's effective ceiling here is column/row 95, not the protocol's own 223. The decoder reads through a reader that
    * UTF-8-decodes the stream (`JLine3Backend` builds its terminal with `stdinEncoding(UTF_8)`), and a coordinate byte
    * at or above 0x80 is not valid UTF-8 on its own: it comes back as U+FFFD, which the range check below rejects so
    * the click is dropped rather than reported at an invented position such as `Position(65500, 9)`. Worse, when two
    * coordinate bytes happen to form a legal UTF-8 sequence (0xC3 0xA0, say) they collapse into one character, and the
    * byte after the report is then consumed as the missing coordinate — damage one character of lookahead cannot
    * repair. Reading raw bytes instead is not an option: the same reader's UTF-16 code units are what
    * [[decodeControl]]'s C1 branch and [[printable]]'s surrogate recombination are built on.
    */
  private def decodeX10Mouse(): Option[Event] =
    val button = next(escapeTimeoutMillis)
    val column = next(escapeTimeoutMillis)
    val row    = next(escapeTimeoutMillis)
    if button < 0 || column < 0 || row < 0 then None
    else if button > 0xff || column > 0xff || row > 0xff then None // a replacement character, not a coordinate byte
    else
      val bits    = button - X10Bias
      // X10 has no separate release code: button 3 means "some button came up"
      val isPress = (bits & ButtonMask) != NoButtonHeld
      mouseEvent(bits, column - X10Bias - 1, row - X10Bias - 1, isPress)

  private def mouseEvent(button: Int, x: Int, y: Int, isPress: Boolean): Option[Event] =
    val kind      =
      if (button & 64) != 0 then wheelKind(button)
      else if (button & MotionBit) != 0 then
        // bit 5 says "this report is motion". The low two bits then name the button that is held, and the value 3
        // means "none" — so 3 is the pointer moving over the window with nothing pressed, which is a hover, and
        // anything else is a drag. Only a terminal asked for `MouseCaptureMode.AllMotion` sends the hover form; under
        // buttons-only tracking this branch never sees a 3, which is why `Moved` used to be unreachable.
        if (button & ButtonMask) == NoButtonHeld then MouseEventKind.Moved else MouseEventKind.Drag
      else if isPress then MouseEventKind.Down
      else MouseEventKind.Up
    // a mouse report carries the same shift/alt/ctrl bitmask as a CSI modifier parameter, shifted up by two positions
    val modifiers = modifiersFromBits(button >> MouseModifierShift)
    val pressed   = pressedButton(button)
    Some(Event.Mouse(MouseEvent(Position(math.max(0, x), math.max(0, y)), kind, modifiers, pressed)))

  /** Which button the report names.
    *
    * The low two bits are the button number: 0 left, 1 middle, 2 right. The fourth value, 3, is X10's "some button came
    * up" release, which genuinely does not say which one — it becomes [[MouseButton.Unknown]] rather than an invented
    * guess. A wheel report (bit 64) reuses those same two bits for the scroll direction, so it names no button either.
    *
    * SGR 1006, unlike X10, keeps the button number on the release as well as the press, which is what makes a
    * right-button drag-and-release usable.
    */
  private def pressedButton(button: Int): MouseButton =
    if (button & 64) != 0 then MouseButton.Unknown
    else
      button & 3 match
        case 0 => MouseButton.Left
        case 1 => MouseButton.Middle
        case 2 => MouseButton.Right
        case _ => MouseButton.Unknown

  /** Wheel buttons 64 and 65 are wheel-up and wheel-down; 66 and 67 are wheel-left and wheel-right, the horizontal
    * wheel a sideways trackpad swipe sends.
    *
    * All four are distinct kinds rather than the low bit folded onto the vertical pair: reading the low bit alone
    * turned every sideways swipe into a scroll of the focused list. They used to be dropped instead, which was the
    * honest answer only while [[MouseEventKind]] had no horizontal vocabulary — it has one now.
    */
  private def wheelKind(button: Int): MouseEventKind =
    button & 3 match
      case 0 => MouseEventKind.ScrollUp
      case 1 => MouseEventKind.ScrollDown
      case 2 => MouseEventKind.ScrollLeft
      case _ => MouseEventKind.ScrollRight

  /** xterm modifier parameter: `code - 1` is a bitmask of shift/alt/ctrl.
    *
    * kitty extends it with super (8), hyper (16) and meta (32), which have no [[KeyModifiers]] counterpart, and with
    * caps-lock (64) and num-lock (128), which are lock *states* rather than held modifiers and carry no meaning for a
    * binding. A key held with a modifier glyphora cannot represent is dropped, never delivered bare: Super+Q arriving
    * as a plain `q` fires the quit binding.
    */
  private def modifiersFromCode(code: Int): Option[KeyModifiers] =
    val bits = code - 1
    if (bits & UnrepresentableModifiers) != 0 then None else Some(modifiersFromBits(bits))

  /** The xterm shift/alt/ctrl bitmask (bit 0 shift, bit 1 alt, bit 2 ctrl) as a [[KeyModifiers]] set.
    *
    * Bits above those three are ignored here; a caller that must reject them — [[modifiersFromCode]], because a key
    * held with a modifier glyphora cannot express must not be delivered bare — checks for them before calling.
    */
  private def modifiersFromBits(bits: Int): KeyModifiers =
    var modifiers = KeyModifiers.None
    if (bits & 1) != 0 then modifiers = modifiers | KeyModifiers.Shift
    if (bits & 2) != 0 then modifiers = modifiers | KeyModifiers.Alt
    if (bits & 4) != 0 then modifiers = modifiers | KeyModifiers.Ctrl
    modifiers

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

  /** How a CSI sequence ended, as read off the wire by `InputDecoder.scanCsi`.
    *
    * Purely a description of the bytes: what a completed sequence *means* is decided afterwards, by `decodeCsiFinal`.
    */
  private enum CsiScan:

    /** A final byte in 0x40-0x7E arrived; `params` is everything between the introducer and it. */
    case Complete(params: String, finalByte: Int)

    /** The read timed out before the final byte: the sequence never finished arriving and is dropped. */
    case Torn

    /** The sequence outgrew its size budget, so its parameters are incomplete and the whole sequence is dropped. */
    case Overrun

  private val NoChar             = -1
  private val Bel                = 7
  private val PasteTimeoutMillis = 200L
  private val PasteLimit         = 1 << 20
  private val PasteStart         = 200
  private val PasteEnd           = "\u001b[201~"
  private val MaxParamLength     = 64

  /** How far a mouse report's shift/alt/ctrl bits sit above the CSI modifier parameter's: mouse uses 4/8/16 where a CSI
    * modifier bitmask uses 1/2/4.
    */
  private val MouseModifierShift = 2

  /** Every coordinate and button value in an X10-derived mouse report is written biased by this much. */
  private val X10Bias = 32

  /** Bit 5 of a mouse report's button byte: set when the report describes motion rather than a press or a release. */
  private val MotionBit = 32

  /** The low two bits of a mouse report's button byte, which name the button involved. */
  private val ButtonMask = 3

  /** The button-bits value that means "no button" — a motion report carrying it is a hover, not a drag. */
  private val NoButtonHeld = 3

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

  /** Nanoseconds in a millisecond, for the deadline arithmetic in `readCursorReport`. A named constant so the two
    * places that convert cannot disagree by a factor of a thousand — which reads as a query that returns instantly or
    * one that hangs, and neither points at the arithmetic.
    */
  private val NanosPerMilli = 1000000L

  /** kitty's super, hyper and meta bits, none of which [[KeyModifiers]] can express. */
  private val UnrepresentableModifiers = 8 | 16 | 32
