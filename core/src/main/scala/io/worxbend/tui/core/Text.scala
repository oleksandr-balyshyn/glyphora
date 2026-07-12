package io.worxbend.tui.core

/** Multi-line styled text. */
final case class Text(lines: Seq[Line]):
  def height: Int = lines.size

  def width: Int = if lines.isEmpty then 0 else lines.map(_.width).max

object Text:
  /** Splits `content` on newlines; each resulting line is unstyled. */
  def raw(content: String): Text =
    Text(content.split("\n", -1).toSeq.map(Line.raw))

  def styled(content: String, style: Style): Text =
    Text(content.split("\n", -1).toSeq.map(line => Line.styled(line, style)))
