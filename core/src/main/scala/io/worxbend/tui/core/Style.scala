package io.worxbend.tui.core

/** How a cell is drawn: optional foreground/background colors plus text-attribute modifiers.
  *
  * `None` for a color means "leave the terminal default in effect". Builders return a new immutable `Style`; they are
  * `with`-prefixed for the color fields because a case-class field and a `def` cannot share a name.
  */
final case class Style(
    fg: Option[Color] = None,
    bg: Option[Color] = None,
    modifiers: Modifiers = Modifiers.None,
):
  def withFg(color: Color): Style = copy(fg = Some(color))
  def withBg(color: Color): Style = copy(bg = Some(color))
  def bold: Style = copy(modifiers = modifiers | Modifiers.Bold)
  def dim: Style = copy(modifiers = modifiers | Modifiers.Dim)
  def italic: Style = copy(modifiers = modifiers | Modifiers.Italic)
  def underline: Style = copy(modifiers = modifiers | Modifiers.Underline)
  def blink: Style = copy(modifiers = modifiers | Modifiers.Blink)
  def reverse: Style = copy(modifiers = modifiers | Modifiers.Reverse)
  def hidden: Style = copy(modifiers = modifiers | Modifiers.Hidden)
  def crossedOut: Style = copy(modifiers = modifiers | Modifiers.CrossedOut)

  /** This style with `other`'s explicit choices layered on top: `other`'s colors win where set, modifiers union. */
  def patch(other: Style): Style =
    Style(
      fg = other.fg.orElse(fg),
      bg = other.bg.orElse(bg),
      modifiers = modifiers | other.modifiers,
    )

object Style:
  val Default: Style = Style()
