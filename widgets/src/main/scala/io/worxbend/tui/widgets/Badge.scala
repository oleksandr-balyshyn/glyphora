package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Rect, Style, Widget}

/** How a [[Badge]] is drawn. The three read at different volumes, which is the point of having three: a solid badge
  * shouts, an outline badge sits inside prose, and a dot is almost silent.
  */
enum BadgeVariant:

  /** Reversed text with a space either side — the loudest, for a status a reader must not miss. */
  case Solid

  /** Bracketed text in the badge's color, background untouched, so it sits inside a line of prose without a block of
    * colour behind it.
    */
  case Outline

  /** A coloured dot before plain text. Carries the colour but not the emphasis — the right one for a dense list where
    * every row has a badge and none of them should shout.
    */
  case Dot

/** A short inline label: a status tag, a count, a category.
  *
  * One row, sized to its content — put it in a `row` beside the thing it labels. The badge clips rather than wrapping,
  * because a badge that wrapped would stop reading as one.
  */
final case class Badge(
    label: String,
    variant: BadgeVariant = BadgeVariant.Solid,
    style: Style = Style.Default,
    dotSymbol: String = "●",
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      variant match
        case BadgeVariant.Solid   =>
          buffer.setString(area.x, area.y, CharWidth.substringByWidth(s" $label ", area.width), style.reverse)
        case BadgeVariant.Outline =>
          buffer.setString(area.x, area.y, CharWidth.substringByWidth(s"[$label]", area.width), style)
        case BadgeVariant.Dot     =>
          val dot   = CharWidth.substringByWidth(dotSymbol, area.width)
          buffer.setString(area.x, area.y, dot, style)
          val textX = area.x + CharWidth.of(dot) + 1
          if textX < area.right then
            buffer.setString(textX, area.y, CharWidth.substringByWidth(label, area.right - textX), Style.Default)

  /** The width this badge wants, so a caller can size a column for it rather than guessing. */
  def preferredWidth: Int =
    val text = CharWidth.of(label)
    variant match
      case BadgeVariant.Solid   => text + 2
      case BadgeVariant.Outline => text + 2
      case BadgeVariant.Dot     => text + CharWidth.of(dotSymbol) + 1

object Badge:

  /** A badge carrying a severity's own tag and colour — `Badge.of(NoticeLevel.Error, errorStyle)` is `FAIL`. */
  def of(level: NoticeLevel, style: Style, variant: BadgeVariant = BadgeVariant.Solid): Badge =
    Badge(level.tag, variant, style)
