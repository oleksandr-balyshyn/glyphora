package io.worxbend.tui.core

/** Multi-line styled text. */
final case class Text(lines: Seq[Line]):
  def height: Int = lines.size

  def width: Int = if lines.isEmpty then 0 else lines.map(_.width).max

object Text:
  /** Splits `content` on newlines, keeping trailing empty lines; each resulting line carries `style`. */
  def styled(content: String, style: Style): Text =
    Text(content.split("\n", -1).toSeq.map(line => Line.styled(line, style)))

  /** [[styled]] with [[Style.Default]]: splits `content` on newlines, each resulting line unstyled. */
  def raw(content: String): Text =
    styled(content, Style.Default)
