package io.worxbend.tui.core

/** The line drawn under a cell's glyph, independent of the plain underline modifier.
  *
  * `Straight` mirrors the classic `Modifiers.Underline`; the richer variants use the colon-parameterised SGR 4
  * extension (`4:2`…`4:5`) that modern terminals (kitty, VTE, WezTerm, iTerm2) understand and older ones ignore.
  */
enum UnderlineStyle:
  case None, Straight, Double, Curly, Dotted, Dashed

/** How a cell is drawn: optional foreground/background colors plus text-attribute modifiers.
  *
  * `None` for a color means "leave the terminal default in effect". Builders return a new immutable `Style`; they are
  * `with`-prefixed for the color fields because a case-class field and a `def` cannot share a name.
  */
final case class Style(
    fg: Option[Color] = None,
    bg: Option[Color] = None,
    modifiers: Modifiers = Modifiers.None,
    link: Option[String] = None,
    underlineColor: Option[Color] = None,
    underlineStyle: UnderlineStyle = UnderlineStyle.None,
):
  def withFg(color: Color): Style = copy(fg = Some(color))

  /** Attaches an OSC 8 hyperlink target — terminals that support it make the cells clickable. */
  def withLink(url: String): Style = copy(link = Some(url))
  def withBg(color: Color): Style  = copy(bg = Some(color))
  def bold: Style                  = copy(modifiers = modifiers | Modifiers.Bold)
  def dim: Style                   = copy(modifiers = modifiers | Modifiers.Dim)
  def italic: Style                = copy(modifiers = modifiers | Modifiers.Italic)
  def underline: Style             = copy(modifiers = modifiers | Modifiers.Underline)
  def blink: Style                 = copy(modifiers = modifiers | Modifiers.Blink)
  def reverse: Style               = copy(modifiers = modifiers | Modifiers.Reverse)
  def hidden: Style                = copy(modifiers = modifiers | Modifiers.Hidden)
  def crossedOut: Style            = copy(modifiers = modifiers | Modifiers.CrossedOut)

  /** Clears specific text-attribute flags (ratatui's `sub_modifier`) — e.g. `style.without(Modifiers.Bold)` un-bolds a
    * style inherited from a theme or parent.
    */
  def without(flags: Modifiers): Style = copy(modifiers = modifiers.without(flags))

  def notBold: Style      = without(Modifiers.Bold)
  def notDim: Style       = without(Modifiers.Dim)
  def notItalic: Style    = without(Modifiers.Italic)
  def notUnderline: Style = without(Modifiers.Underline)
  def notReverse: Style   = without(Modifiers.Reverse)

  /** Drops the foreground color, restoring the terminal default. */
  def withoutFg: Style = copy(fg = None)

  /** Drops the background color, restoring the terminal default. */
  def withoutBg: Style = copy(bg = None)

  /** Colors the underline separately from the glyph (SGR 58) — terminals without support draw a default-colored line.
    */
  def withUnderlineColor(color: Color): Style = copy(underlineColor = Some(color))

  /** Picks a styled underline (straight/double/curly/dotted/dashed); anything but `None` also underlines. */
  def withUnderlineStyle(style: UnderlineStyle): Style = copy(underlineStyle = style)
  def doubleUnderline: Style                           = withUnderlineStyle(UnderlineStyle.Double)
  def curlyUnderline: Style                            = withUnderlineStyle(UnderlineStyle.Curly)
  def dottedUnderline: Style                           = withUnderlineStyle(UnderlineStyle.Dotted)
  def dashedUnderline: Style                           = withUnderlineStyle(UnderlineStyle.Dashed)

  /** This style with `other`'s explicit choices layered on top: `other`'s colors win where set, modifiers union. */
  def patch(other: Style): Style =
    Style(
      fg = other.fg.orElse(fg),
      bg = other.bg.orElse(bg),
      modifiers = modifiers | other.modifiers,
      link = other.link.orElse(link),
      underlineColor = other.underlineColor.orElse(underlineColor),
      underlineStyle = if other.underlineStyle == UnderlineStyle.None then underlineStyle else other.underlineStyle,
    )

object Style:
  val Default: Style = Style()
