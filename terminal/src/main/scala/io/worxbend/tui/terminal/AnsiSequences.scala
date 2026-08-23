package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Color, Modifiers, Style, UnderlineStyle}

/** ANSI escape sequences the backend emits. Pure string construction, no I/O — separately testable. */
private[terminal] object AnsiSequences:

  private val Esc = "\u001b"

  val EnterAlternateScreen: String  = s"$Esc[?1049h"
  val LeaveAlternateScreen: String  = s"$Esc[?1049l"
  val ClearScreen: String           = s"$Esc[2J"
  val HideCursor: String            = s"$Esc[?25l"
  val ShowCursor: String            = s"$Esc[?25h"
  val EnableMouseCapture: String    = s"$Esc[?1000h$Esc[?1002h$Esc[?1006h"
  val DisableMouseCapture: String   = s"$Esc[?1006l$Esc[?1002l$Esc[?1000l"
  val ResetStyle: String            = s"$Esc[0m"
  val EnableBracketedPaste: String  = s"$Esc[?2004h"
  val DisableBracketedPaste: String = s"$Esc[?2004l"
  val EnableFocusReporting: String  = s"$Esc[?1004h"
  val DisableFocusReporting: String = s"$Esc[?1004l"
  val BeginSynchronized: String     = s"$Esc[?2026h"
  val EndSynchronized: String       = s"$Esc[?2026l"

  /** Kitty keyboard protocol, progressive enhancement flag 1 (disambiguate escape codes): a lone Esc arrives as
    * `CSI 27 u` instead of a bare ESC byte, removing the read-timeout heuristic on terminals that support it.
    * Unsupported terminals ignore the sequence and keep sending legacy encoding.
    */
  val PushKittyKeyboard: String = s"$Esc[>1u"
  val PopKittyKeyboard: String  = s"$Esc[<u"
  val LinkClose: String         = s"$Esc]8;;$Esc\\"

  /** Every mode the backend can turn on, turned off, in reverse acquisition order.
    *
    * Emitted verbatim by the shutdown hook straight to the process's stdout descriptor, so a terminal left dressed up
    * by a signal-terminated process is still handed back usable. Every sequence here is a DEC private-mode *reset*
    * (XTerm `ctlseqs.ms`, "DEC Private Mode Reset"), which is idempotent — resetting a mode that was never set is a
    * no-op, so the hook needs no knowledge of what was actually enabled.
    */
  val RestoreAll: String =
    s"$DisableMouseCapture$ShowCursor$LeaveAlternateScreen$PopKittyKeyboard$DisableFocusReporting" +
      s"$DisableBracketedPaste$ResetStyle"

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

  /** Moves the cursor to an absolute zero-based position (ANSI rows/columns are one-based). */
  def moveTo(x: Int, y: Int): String =
    s"$Esc[${y + 1};${x + 1}H"

  /** Full SGR sequence for `style`, starting from a reset so no previous attribute leaks through; colors are
    * downsampled to what `depth` can display.
    */
  def sgr(style: Style, depth: ColorDepth = ColorDepth.TrueColor): String =
    val codes = List.newBuilder[String]
    codes += "0"
    if depth != ColorDepth.NoColor then
      style.fg.foreach(color => codes += foregroundCode(ColorDepth.downsample(color, depth)))
      style.bg.foreach(color => codes += backgroundCode(ColorDepth.downsample(color, depth)))
    modifierCodes(style.modifiers).foreach(code => codes += code)
    // the styled-underline selector is a text attribute (kept under NoColor); the underline color is a color (dropped)
    underlineStyleCode(style.underlineStyle).foreach(code => codes += code)
    if depth != ColorDepth.NoColor then
      style.underlineColor.foreach(color => codes += underlineColorCode(ColorDepth.downsample(color, depth)))
    codes.result().mkString(s"$Esc[", ";", "m")

  /** SGR 4:n styled-underline selectors; `None`/`Straight` defer to the plain `4` from [[modifierCodes]]. */
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

  private def modifierCodes(modifiers: Modifiers): List[String] =
    List(
      Modifiers.Bold       -> "1",
      Modifiers.Dim        -> "2",
      Modifiers.Italic     -> "3",
      Modifiers.Underline  -> "4",
      Modifiers.Blink      -> "5",
      Modifiers.Reverse    -> "7",
      Modifiers.Hidden     -> "8",
      Modifiers.CrossedOut -> "9",
    ).collect { case (flag, code) if modifiers.hasAny(flag) => code }
