package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Color, Modifiers, Style}

/** ANSI escape sequences the backend emits. Pure string construction, no I/O — separately testable. */
private[terminal] object AnsiSequences:

  private val Esc = "\u001b"

  val EnterAlternateScreen: String = s"$Esc[?1049h"
  val LeaveAlternateScreen: String = s"$Esc[?1049l"
  val ClearScreen: String          = s"$Esc[2J"
  val HideCursor: String           = s"$Esc[?25l"
  val ShowCursor: String           = s"$Esc[?25h"
  val EnableMouseCapture: String   = s"$Esc[?1000h$Esc[?1002h$Esc[?1006h"
  val DisableMouseCapture: String  = s"$Esc[?1006l$Esc[?1002l$Esc[?1000l"
  val ResetStyle: String           = s"$Esc[0m"
  val EnableBracketedPaste: String  = s"$Esc[?2004h"
  val DisableBracketedPaste: String = s"$Esc[?2004l"
  val EnableFocusReporting: String  = s"$Esc[?1004h"
  val DisableFocusReporting: String = s"$Esc[?1004l"
  val BeginSynchronized: String     = s"$Esc[?2026h"
  val EndSynchronized: String       = s"$Esc[?2026l"

  /** Kitty keyboard protocol, progressive enhancement flag 1 (disambiguate escape codes): a lone Esc arrives
    * as `CSI 27 u` instead of a bare ESC byte, removing the read-timeout heuristic on terminals that support
    * it. Unsupported terminals ignore the sequence and keep sending legacy encoding.
    */
  val PushKittyKeyboard: String = s"$Esc[>1u"
  val PopKittyKeyboard: String  = s"$Esc[<u"
  val LinkClose: String            = s"$Esc]8;;$Esc\\"

  /** OSC 8 hyperlink opener; pair every open with [[LinkClose]]. */
  def linkOpen(url: String): String = s"$Esc]8;;$url$Esc\\"

  /** Moves the cursor to an absolute zero-based position (ANSI rows/columns are one-based). */
  def moveTo(x: Int, y: Int): String =
    s"$Esc[${y + 1};${x + 1}H"

  /** Full SGR sequence for `style`, starting from a reset so no previous attribute leaks through; colors are
    * downsampled to what `depth` can display.
    */
  def sgr(style: Style, depth: ColorDepth = ColorDepth.TrueColor): String =
    val codes = List.newBuilder[String]
    codes += "0"
    style.fg.foreach(color => codes += foregroundCode(ColorDepth.downsample(color, depth)))
    style.bg.foreach(color => codes += backgroundCode(ColorDepth.downsample(color, depth)))
    modifierCodes(style.modifiers).foreach(code => codes += code)
    codes.result().mkString(s"$Esc[", ";", "m")

  private def foregroundCode(color: Color): String =
    color match
      case Color.Reset          => "39"
      case Color.Black          => "30"
      case Color.Red            => "31"
      case Color.Green          => "32"
      case Color.Yellow         => "33"
      case Color.Blue           => "34"
      case Color.Magenta        => "35"
      case Color.Cyan           => "36"
      case Color.White          => "37"
      case Color.Indexed(index) => s"38;5;$index"
      case Color.Rgb(r, g, b)   => s"38;2;$r;$g;$b"

  private def backgroundCode(color: Color): String =
    color match
      case Color.Reset          => "49"
      case Color.Black          => "40"
      case Color.Red            => "41"
      case Color.Green          => "42"
      case Color.Yellow         => "43"
      case Color.Blue           => "44"
      case Color.Magenta        => "45"
      case Color.Cyan           => "46"
      case Color.White          => "47"
      case Color.Indexed(index) => s"48;5;$index"
      case Color.Rgb(r, g, b)   => s"48;2;$r;$g;$b"

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
    ).collect { case (flag, code) if modifiers.has(flag) => code }
