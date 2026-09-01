package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Color, Modifiers, Style, UnderlineStyle}

/** ANSI escape sequences the backend emits. Pure string construction, no I/O — separately testable. */
private[terminal] object AnsiSequences:

  private val Esc = "\u001b"

  val EnterAlternateScreen: String  = s"$Esc[?1049h"
  val LeaveAlternateScreen: String  = s"$Esc[?1049l"
  val ClearScreen: String           = clear(ClearType.All)
  val HideCursor: String            = s"$Esc[?25l"
  val ShowCursor: String            = s"$Esc[?25h"
  // Why four modes, in that order. 1000 asks for presses and releases and 1002 adds motion while a button is held;
  // 1003 (see MouseCaptureMode) adds motion with none held. The other two are *encodings* of the report rather than
  // requests for more of them. The original X10 encoding writes each coordinate as one byte biased by 32, which cannot
  // express a column past 223 — and, because this decoder reads a UTF-8 stream, cannot be read past column 95 at all
  // (see InputDecoder.decodeX10Mouse). 1015 (urxvt) and 1006 (SGR) both replace that with decimal text and lift the
  // ceiling. 1015 is requested *before* 1006 and released *after* it, so a terminal understanding both settles on SGR,
  // which is the better of the two because it names which button was released instead of reporting "one came up".
  // 1015 is here only for a terminal that ignores 1006: without it such a terminal keeps sending X10, and its clicks
  // are unreadable across most of a modern window. Pixel reporting (1016) is deliberately not requested — its
  // coordinates are pixels, and every layer above `Backend` addresses cells.
  val EnableMouseCapture: String    = s"$Esc[?1000h$Esc[?1002h$Esc[?1015h$Esc[?1006h"
  val EnableMouseAllMotion: String  = s"$Esc[?1000h$Esc[?1002h$Esc[?1003h$Esc[?1015h$Esc[?1006h"
  // resets 1003 as well, whether or not it was ever set: a DEC private-mode reset for a mode that is already off is a
  // no-op on every terminal, and sending it unconditionally means no path can leave all-motion tracking stuck on,
  // flooding the user's shell with reports after the app has exited
  val DisableMouseCapture: String   = s"$Esc[?1006l$Esc[?1015l$Esc[?1003l$Esc[?1002l$Esc[?1000l"
  val ResetStyle: String            = s"$Esc[0m"
  val EnableBracketedPaste: String  = s"$Esc[?2004h"
  val DisableBracketedPaste: String = s"$Esc[?2004l"
  val EnableFocusReporting: String  = s"$Esc[?1004h"
  val DisableFocusReporting: String = s"$Esc[?1004l"
  val BeginSynchronized: String     = s"$Esc[?2026h"
  val EndSynchronized: String       = s"$Esc[?2026l"

  /** The mouse-capture request for `mode` — see [[MouseCaptureMode]] for what each one costs and buys. */
  def enableMouseCapture(mode: MouseCaptureMode): String =
    mode match
      case MouseCaptureMode.Buttons   => EnableMouseCapture
      case MouseCaptureMode.AllMotion => EnableMouseAllMotion

  /** The ED and EL erase forms — ECMA-48 §8.3.39 "Erase in Display" and §8.3.41 "Erase in Line".
    *
    * The cursor-relative variants are what a viewport that does not own the whole screen needs: an inline app drawing a
    * few rows under the shell prompt erases its own rows and nothing else, where `CSI 2J` would take the user's
    * scrollback with it.
    *
    * None of these moves the cursor; they only blank cells, using the current background colour.
    */
  def clear(kind: ClearType): String =
    kind match
      case ClearType.All          => s"$Esc[2J"
      case ClearType.AfterCursor  => s"$Esc[0J"
      case ClearType.BeforeCursor => s"$Esc[1J"
      case ClearType.CurrentLine  => s"$Esc[2K"
      case ClearType.UntilNewLine => s"$Esc[0K"

  /** Kitty keyboard protocol, progressive enhancement flag 1 (disambiguate escape codes): a lone Esc arrives as
    * `CSI 27 u` instead of a bare ESC byte, removing the read-timeout heuristic on terminals that support it.
    * Unsupported terminals ignore the sequence and keep sending legacy encoding.
    */
  val PushKittyKeyboard: String = s"$Esc[>1u"
  val PopKittyKeyboard: String  = s"$Esc[<u"
  val LinkClose: String         = s"$Esc]8;;$Esc\\"

  /** DECSTBM with no parameters: the scrolling region becomes the whole screen again.
    *
    * Idempotent, like the mode resets it sits beside in [[RestoreAll]] — resetting a region that was never set does
    * nothing at all.
    */
  val ResetScrollRegion: String = s"$Esc[r"

  /** DECSC (`ESC 7`), "save cursor": stores the cursor position, and the terminal's own graphic-rendition and
    * character-set state, in a one-slot register.
    *
    * The two-byte form rather than `CSI s`: `CSI s` is also DECSLRM, "set left and right margin", on terminals that
    * have margin support switched on, so the same bytes mean two different things depending on a mode this library
    * never sets and cannot observe. `ESC 7` is unambiguous everywhere.
    */
  val SaveCursor: String = s"${Esc}7"

  /** DECRC (`ESC 8`), "restore cursor": puts back whatever [[SaveCursor]] stored.
    *
    * Deliberately absent from [[RestoreAll]], unlike every mode reset there. A terminal whose save register was never
    * written restores the cursor to the home position instead of leaving it alone, so a shutdown hook firing for a
    * process that never entered raw mode — and therefore never saved anything — would move the user's shell cursor to
    * the top-left corner. Pairing it with raw mode, which every dressed-up app enters, keeps every restore matched to a
    * save.
    */
  val RestoreCursor: String = s"${Esc}8"

  /** Every mode the backend can turn on, turned off, in reverse acquisition order.
    *
    * Emitted verbatim by the shutdown hook straight to the process's stdout descriptor, so a terminal left dressed up
    * by a signal-terminated process is still handed back usable. Almost every sequence here is a DEC private-mode
    * *reset* (XTerm `ctlseqs.ms`, "DEC Private Mode Reset"), which is idempotent — resetting a mode that was never set
    * is a no-op, so the hook needs no knowledge of what was actually enabled. [[ResetScrollRegion]] is the exception in
    * form only: DECSTBM with no parameters is not a private-mode reset, but it is idempotent in exactly the same way,
    * and it belongs here for exactly the same reason. A process killed while a scrolling region was set would otherwise
    * leave the user's *shell* scrolling inside a sub-rectangle of their terminal, which is the class of damage this
    * string exists to prevent.
    *
    * [[EndSynchronized]] leads. A frame is written as one `?2026h` … `?2026l` pair (see `JLine3Backend.draw`), which
    * asks the terminal to hold everything back until the closing half arrives so a half-drawn frame is never shown. A
    * process killed between the two halves leaves that update open: until the emulator's own timeout expires the screen
    * stays frozen and would swallow the rest of this restore. Closing the update first makes everything after it appear
    * at once, and closing one that was never opened does nothing.
    */
  val RestoreAll: String =
    s"$EndSynchronized$DisableMouseCapture$ShowCursor$LeaveAlternateScreen$ResetScrollRegion$PopKittyKeyboard" +
      s"$DisableFocusReporting$DisableBracketedPaste$ResetStyle"

  /** OSC 8 hyperlink opener; pair every open with [[LinkClose]].
    *
    * The URL is stripped of C0/C1 controls and DEL: an OSC string ends at BEL or ST (XTerm `ctlseqs.ms`, "Operating
    * System Commands"), so an `ESC \` inside the target would close the hyperlink early and let the rest of the string
    * execute as terminal commands. Link targets routinely come from untrusted text (Markdown, log lines, API
    * responses), which makes this a security boundary, not a cosmetic one. RFC 3986 §2 forbids these bytes in a URI, so
    * nothing legitimate is lost.
    */
  def linkOpen(url: String): String = s"$Esc]8;;${stripControls(url)}$Esc\\"

  /** Removes C0 controls, DEL and C1 controls from `text`, keeping tab. */
  def stripControls(text: String): String =
    if text.forall(isSafeText) then text else text.filter(isSafeText)

  private def isSafeText(c: Char): Boolean =
    c == '\t' || (c >= 0x20 && c != 0x7f && !(c >= 0x80 && c <= 0x9f))

  /** OSC 52 clipboard write: sets the system clipboard (`c`) to `text`, base64-encoded per the protocol. Terminals that
    * don't support OSC 52 ignore it.
    */
  def clipboardCopy(text: String): String =
    val encoded = java.util.Base64.getEncoder.encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    s"$Esc]52;c;$encoded$Esc\\"

  /** OSC 2 window/tab title.
    *
    * Controls are stripped for exactly the reason [[linkOpen]] strips them: an OSC string ends at BEL or ST, so an
    * `ESC \` inside a document name would close the title early and leave the rest of the name to run as terminal
    * commands. Titles come from the same untrusted places link targets do — a filename, a branch name, a fetched page's
    * `<title>`.
    */
  def setTitle(title: String): String = s"$Esc]2;${stripControls(title)}$Esc\\"

  /** XTerm `CSI 22;2t` — pushes the terminal's current window title onto its own title stack.
    *
    * Emitted once, before the first [[setTitle]], so that [[PopTitle]] can hand the shell's own title back on exit
    * without this library ever having to read the title (there is no reliable, non-blocking way to do that).
    */
  val PushTitle: String = s"$Esc[22;2t"

  /** XTerm `CSI 23;2t` — pops the title pushed by [[PushTitle]], restoring whatever the shell had set.
    *
    * Deliberately absent from [[RestoreAll]]: everything in that string is a DEC private-mode *reset*, which is
    * idempotent and therefore safe for a shutdown hook that cannot know what was enabled. A pop is not idempotent — an
    * unmatched one would discard a title stack entry that belonged to something else.
    */
  val PopTitle: String = s"$Esc[23;2t"

  /** Moves the cursor to an absolute zero-based position (ANSI rows/columns are one-based). */
  def moveTo(x: Int, y: Int): String =
    s"$Esc[${y + 1};${x + 1}H"

  /** SU — scrolls the whole screen up by `n` rows (XTerm `ctlseqs.ms`, "Scroll Up").
    *
    * On the primary screen the rows that leave the top go into the terminal's scrollback, where the user can scroll
    * back to them; `n` blank rows appear at the bottom. Unlike writing `n` newlines this does not depend on where the
    * cursor is and does not move it. `n <= 0` produces the empty string, so a caller computing a delta needs no guard.
    */
  def scrollUp(n: Int): String = if n <= 0 then "" else s"$Esc[${n}S"

  /** SD — scrolls the whole screen (or the current scrolling region) down by `n` rows, blanking the rows exposed at the
    * top (XTerm `ctlseqs.ms`, "Scroll Down"). The mirror of [[scrollUp]]; rows pushed off the bottom are lost, since
    * only the top of the screen has scrollback. `n <= 0` produces the empty string.
    */
  def scrollDown(n: Int): String = if n <= 0 then "" else s"$Esc[${n}T"

  /** DECSTBM — confines scrolling to rows `top`..`bottom` inclusive, zero-based (the sequence itself is one-based).
    *
    * A "scrolling region" is the band of rows a scroll is allowed to move. With one set, [[scrollUp]] shifts only the
    * rows inside the band and leaves everything above and below exactly as it was. That is what lets lines be inserted
    * above a live interface without repainting the interface: confine the scroll to the rows above it, scroll them, and
    * write into the space that opened up.
    *
    * Two things every caller has to know. Setting a region homes the cursor (DEC STD 070), so follow this with an
    * explicit [[moveTo]] rather than assuming the cursor stayed where it was. And a region left set makes the *user's
    * shell* scroll inside a sub-rectangle of their terminal after the app exits, so pair every call with
    * [[ResetScrollRegion]] — which is why [[RestoreAll]] carries one too.
    */
  def setScrollRegion(top: Int, bottom: Int): String = s"$Esc[${top + 1};${bottom + 1}r"

  /** Full SGR sequence for `style`, starting from a reset so no previous attribute leaks through; colors are
    * downsampled to what `depth` can display.
    */
  def sgr(style: Style, depth: ColorDepth = ColorDepth.TrueColor): String =
    // appended to directly rather than collected into a List and joined: a full-change frame asks for one sequence per
    // run of same-styled cells, and the intermediate list was the bulk of what that allocated
    val codes     = StringBuilder(Esc).append("[0")
    val effective = if depth == ColorDepth.Monochrome then monochromeStyle(style) else style
    if depth != ColorDepth.NoColor then
      effective.fg.foreach(color => append(codes, foregroundCode(ColorDepth.downsample(color, depth))))
      effective.bg.foreach(color => append(codes, backgroundCode(ColorDepth.downsample(color, depth))))
    ModifierCodes.foreach((flag, code) => if style.modifiers.hasAny(flag) then append(codes, code))
    // the styled-underline selector is a text attribute (kept under NoColor); the underline color is a color (dropped)
    underlineStyleCode(style.underlineStyle).foreach(code => append(codes, code))
    if depth != ColorDepth.NoColor then
      effective.underlineColor.foreach(color => append(codes, underlineColorCode(ColorDepth.downsample(color, depth))))
    codes.append('m').result()

  /** The SGR that moves a terminal already showing `from` to showing `to`, writing only what actually changed.
    *
    * [[sgr]] is the *absolute* form: it opens with a reset (`ESC[0`) and then restates the whole style, so no attribute
    * of whatever came before can leak through. That is always correct, and it is expensive. A run of cells that differs
    * from its neighbour only in the bold flag still rewrites both truecolour colours — roughly thirty bytes where five
    * would do. Over a link where bytes cost time (ssh, mosh, a serial console) that is the largest remaining per-frame
    * cost after the frame diff itself.
    *
    * This form assumes the terminal is currently in exactly `from`. [[FrameEncoder]] guarantees that, because `from` is
    * the style it last emitted on this frame; no other caller should use it. Returns the empty string when the two
    * styles render identically — including when they differ only in a field SGR does not carry, such as the hyperlink.
    *
    * Falls back to `sgr(to, depth)` byte for byte whenever the difference cannot be expressed safely as a delta; see
    * `deltaIsSafe` for which cases those are and why.
    */
  def sgrDelta(from: Style, to: Style, depth: ColorDepth): String =
    if from == to then ""
    else
      val before = if depth == ColorDepth.Monochrome then monochromeStyle(from) else from
      val after  = if depth == ColorDepth.Monochrome then monochromeStyle(to) else to
      if !deltaIsSafe(before, after, depth) then sgr(to, depth) else buildDelta(before, after, depth)

  /** Whether the step from `from` to `to` can be written as a delta at all.
    *
    * Turning an attribute *off* needs a reset code, and three of them are either not universally implemented or too
    * blunt to use here:
    *
    *   - SGR 59 ("default underline colour") is missing from several emulators, so dropping an underline colour back to
    *     the terminal default cannot be done by delta. Under [[ColorDepth.NoColor]] the underline colour is never
    *     emitted in the first place, so a change to it is not a difference at all.
    *   - The `4:0` styled-underline selector has the same patchy support, so any change of [[UnderlineStyle]] — into,
    *     out of, or between the styled forms — takes the absolute path.
    *   - SGR 24 ("no underline") clears the styled underline along with the plain one. So switching the plain
    *     [[Modifiers.Underline]] flag on or off while a styled underline is in force would silently take the style with
    *     it; that combination also takes the absolute path.
    *
    * Everything else has a well-defined reset code and is handled by `buildDelta`.
    */
  private def deltaIsSafe(from: Style, to: Style, depth: ColorDepth): Boolean =
    val underlineStyleUnchanged = from.underlineStyle == to.underlineStyle
    val underlineFlagUnchanged  = from.modifiers.hasAny(Modifiers.Underline) == to.modifiers.hasAny(Modifiers.Underline)
    val underlineColorKept      =
      depth == ColorDepth.NoColor || from.underlineColor == to.underlineColor || to.underlineColor.isDefined
    underlineStyleUnchanged &&
    (underlineFlagUnchanged || to.underlineStyle == UnderlineStyle.None) &&
    underlineColorKept

  /** Builds the delta itself. Only called once `deltaIsSafe` has agreed the step is expressible. */
  private def buildDelta(from: Style, to: Style, depth: ColorDepth): String =
    val codes    = StringBuilder()
    val removed  = from.modifiers.without(to.modifiers)
    // Flags that have to be stated again because the reset code for one of them also cleared a sibling that survives:
    // SGR 22 clears bold *and* dim, and SGR 25 clears both blink rates. Dropping bold from a bold+dim style therefore
    // has to write `22;2`, not a bare `22`, or the dim goes with it. Collected first so each reset is written once.
    var reassert = Modifiers.None
    ResetCodes.foreach: (group, code) =>
      if removed.hasAny(group) then
        appendParam(codes, code)
        reassert = reassert | (to.modifiers & group)
    val added    = to.modifiers.without(from.modifiers) | reassert
    ModifierCodes.foreach((flag, code) => if added.hasAny(flag) then appendParam(codes, code))
    if depth != ColorDepth.NoColor then
      // `Color.Reset` is SGR 39/49, "back to the terminal's own colour", which is exactly what a colour going from
      // `Some` to `None` means. It is substituted *after* the downsample rather than before it: under
      // [[ColorDepth.Monochrome]] every colour is pushed onto black or white, and a `Reset` fed through that would come
      // back out as an explicit white — turning "let the terminal choose" into "paint it white".
      if from.fg != to.fg then
        appendParam(codes, foregroundCode(to.fg.fold(Color.Reset)(ColorDepth.downsample(_, depth))))
      if from.bg != to.bg then
        appendParam(codes, backgroundCode(to.bg.fold(Color.Reset)(ColorDepth.downsample(_, depth))))
      if from.underlineColor != to.underlineColor then
        to.underlineColor.foreach(color => appendParam(codes, underlineColorCode(ColorDepth.downsample(color, depth))))
    if codes.isEmpty then "" else s"$Esc[${codes.result()}m"

  /** Adds one parameter to a delta sequence, which — unlike the absolute form — has no leading `0` to separate from. */
  private def appendParam(codes: StringBuilder, code: String): Unit =
    val _ = if codes.isEmpty then codes.append(code) else codes.append(';').append(code)

  /** Keeps a style legible under [[ColorDepth.Monochrome]], where every color collapses onto one of two tones.
    *
    * Two colors that differ on a full palette can land on the same tone — red text on a blue background both threshold
    * to dark — and the text would then be invisible. When that happens the background keeps the tone it thresholds to
    * and the foreground is flipped to the opposite one.
    *
    * A style that sets a background but no foreground gets the same treatment: its text is drawn in the terminal's
    * default foreground, which may itself threshold to the background's tone, so an explicit contrasting foreground is
    * named rather than gambling on it. This is the ordinary selection highlight, which is exactly the row a user cannot
    * afford to lose. The underline color is corrected the same way, because it thresholds independently of both and can
    * otherwise sink into the background once the text is legible.
    *
    * A style that sets no background at all is left alone: it draws on whatever the terminal's own background is, which
    * this code cannot know.
    *
    * Modifiers are not touched by color depth, so `Modifiers.Reverse` remains the way to express a highlight that
    * survives every rung of the ladder.
    */
  private def monochromeStyle(style: Style): Style =
    style.bg match
      // No background means the cell draws on whatever the terminal's own background is, which this code cannot
      // know, so there is no collision to detect and nothing to correct.
      case None     => style
      case Some(bg) =>
        val contrast  = if ColorDepth.isLight(bg) then Color.Black else Color.White
        // A style that sets *only* a background is the common selection highlight. Its text is drawn in the
        // terminal's default foreground, which may well threshold to the same tone as the background and vanish —
        // so name a foreground that contrasts rather than leaving the row unreadable.
        val fixedFg   = style.fg match
          case Some(fg) if ColorDepth.isLight(fg) != ColorDepth.isLight(bg) => fg
          case _                                                            => contrast
        // The underline color thresholds independently of the two above, so a curly underline can land on the
        // background tone and disappear even once the text is legible.
        val recolored = style.withFg(fixedFg)
        recolored.underlineColor match
          case Some(underline) if ColorDepth.isLight(underline) == ColorDepth.isLight(bg) =>
            recolored.withUnderlineColor(contrast)
          case _                                                                          => recolored

  /** Adds one more SGR parameter to a sequence that already holds at least the leading reset. */
  private def append(codes: StringBuilder, code: String): Unit =
    val _ = codes.append(';').append(code)

  /** SGR 4:n styled-underline selectors; `None`/`Straight` defer to the plain `4` from [[ModifierCodes]]. */
  private def underlineStyleCode(style: UnderlineStyle): Option[String] =
    style match
      case UnderlineStyle.None | UnderlineStyle.Straight => scala.None
      case UnderlineStyle.Double                         => Some("4:2")
      case UnderlineStyle.Curly                          => Some("4:3")
      case UnderlineStyle.Dotted                         => Some("4:4")
      case UnderlineStyle.Dashed                         => Some("4:5")

  /** SGR 58 sets the underline color independently of the foreground; 256/truecolor forms mirror SGR 38. */
  private def underlineColorCode(color: Color): String =
    color match
      case Color.Indexed(index) => s"58:5:$index"
      case Color.Rgb(r, g, b)   => s"58:2::$r:$g:$b"
      case named                =>
        val (r, g, b) = Color.approximateRgb(named)
        s"58:2::$r:$g:$b"

  /** The base SGR code of the foreground colour group; every other foreground code is an offset from it. */
  private val ForegroundBase = 30

  /** The background group sits ten codes above the foreground one, and mirrors it entry for entry. */
  private val BackgroundBase = 40

  /** SGR 30-37 / 90-97 for the foreground, plus 38 for the 256-colour and truecolour forms. */
  private def foregroundCode(color: Color): String = colorCode(color, ForegroundBase)

  /** SGR 40-47 / 100-107 for the background, plus 48 for the 256-colour and truecolour forms. */
  private def backgroundCode(color: Color): String = colorCode(color, BackgroundBase)

  /** One SGR colour code, offset from `base` — 30 for the foreground, 40 for the background.
    *
    * Foreground and background differ by nothing but that constant (ECMA-48 §8.3.117-118): the eight named colours are
    * `base + 0` to `base + 7`, their bright variants sit 60 codes higher, `Reset` is `base + 9`, and the 256-colour and
    * truecolour extensions (ISO 8613-6) share the selector `base + 8`. Encoding that once means a new [[Color]] case or
    * a corrected SGR form cannot be added to one half and forgotten in the other.
    *
    * The match stays explicit rather than deriving the offset from `Color.ordinal`, so adding an enum case still fails
    * compilation instead of silently emitting a wrong code.
    */
  private def colorCode(color: Color, base: Int): String =
    color match
      case Color.Reset          => s"${base + 9}"
      case Color.Black          => s"${base + 0}"
      case Color.Red            => s"${base + 1}"
      case Color.Green          => s"${base + 2}"
      case Color.Yellow         => s"${base + 3}"
      case Color.Blue           => s"${base + 4}"
      case Color.Magenta        => s"${base + 5}"
      case Color.Cyan           => s"${base + 6}"
      case Color.White          => s"${base + 7}"
      case Color.BrightBlack    => s"${base + 60}"
      case Color.BrightRed      => s"${base + 61}"
      case Color.BrightGreen    => s"${base + 62}"
      case Color.BrightYellow   => s"${base + 63}"
      case Color.BrightBlue     => s"${base + 64}"
      case Color.BrightMagenta  => s"${base + 65}"
      case Color.BrightCyan     => s"${base + 66}"
      case Color.BrightWhite    => s"${base + 67}"
      case Color.Indexed(index) => s"${base + 8};5;$index"
      case Color.Rgb(r, g, b)   => s"${base + 8};2;$r;$g;$b"

  /** Every text attribute with its SGR code, in the order they are emitted.
    *
    * A `val`, not a table rebuilt inside [[sgr]]: `sgr` runs once per style change on a frame, and allocating this
    * nine-entry list on each of those calls cost more than everything else the method does.
    */
  private val ModifierCodes: List[(Modifiers, String)] =
    List(
      Modifiers.Bold       -> "1",
      Modifiers.Dim        -> "2",
      Modifiers.Italic     -> "3",
      Modifiers.Underline  -> "4",
      Modifiers.Blink      -> "5",
      Modifiers.RapidBlink -> "6",
      Modifiers.Reverse    -> "7",
      Modifiers.Hidden     -> "8",
      Modifiers.CrossedOut -> "9",
    )

  /** The reset code for each group of text attributes, in the order they are emitted.
    *
    * Grouped rather than one entry per flag because ECMA-48 does not give every attribute its own "off" code: SGR 22
    * turns off bold *and* dim, and SGR 25 turns off both blink rates. `buildDelta` therefore works group by group,
    * writing each reset at most once and restating whichever members of the group survive.
    *
    * A `val` for the same reason [[ModifierCodes]] is one: this is walked once per style change on a frame, and
    * rebuilding the list on each of those calls cost more than the walk.
    */
  private val ResetCodes: List[(Modifiers, String)] =
    List(
      (Modifiers.Bold | Modifiers.Dim)         -> "22",
      Modifiers.Italic                         -> "23",
      Modifiers.Underline                      -> "24",
      (Modifiers.Blink | Modifiers.RapidBlink) -> "25",
      Modifiers.Reverse                        -> "27",
      Modifiers.Hidden                         -> "28",
      Modifiers.CrossedOut                     -> "29",
    )
