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
  val EnableMouseCapture: String    = s"$Esc[?1000h$Esc[?1002h$Esc[?1006h"
  val EnableMouseAllMotion: String  = s"$Esc[?1000h$Esc[?1002h$Esc[?1003h$Esc[?1006h"
  // resets 1003 as well, whether or not it was ever set: a DEC private-mode reset for a mode that is already off is a
  // no-op on every terminal, and sending it unconditionally means no path can leave all-motion tracking stuck on,
  // flooding the user's shell with reports after the app has exited
  val DisableMouseCapture: String   = s"$Esc[?1006l$Esc[?1003l$Esc[?1002l$Esc[?1000l"
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
    * by a signal-terminated process is still handed back usable. Every sequence here is a DEC private-mode *reset*
    * (XTerm `ctlseqs.ms`, "DEC Private Mode Reset"), which is idempotent — resetting a mode that was never set is a
    * no-op, so the hook needs no knowledge of what was actually enabled.
    *
    * [[EndSynchronized]] leads. A frame is written as one `?2026h` … `?2026l` pair (see `JLine3Backend.draw`), which
    * asks the terminal to hold everything back until the closing half arrives so a half-drawn frame is never shown. A
    * process killed between the two halves leaves that update open: until the emulator's own timeout expires the screen
    * stays frozen and would swallow the rest of this restore. Closing the update first makes everything after it appear
    * at once, and closing one that was never opened does nothing.
    */
  val RestoreAll: String =
    s"$EndSynchronized$DisableMouseCapture$ShowCursor$LeaveAlternateScreen$PopKittyKeyboard" +
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
