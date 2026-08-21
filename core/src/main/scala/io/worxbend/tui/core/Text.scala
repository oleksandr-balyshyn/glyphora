package io.worxbend.tui.core

/** Multi-line styled text. */
final case class Text(lines: Seq[Line]):
  def height: Int = lines.size

  def width: Int = if lines.isEmpty then 0 else lines.map(_.width).max

object Text:
  /** Splits `content` on newlines, keeping trailing empty lines; each resulting line carries `style`.
    *
    * The split is on `\n` alone: the caller owns newline normalisation. CRLF (`\r\n`) input therefore leaves a carriage
    * return at the end of every line, and a `\r` occupies zero terminal columns but one [[Cell]], so the rest of that
    * row renders one column off. Route text of unknown provenance — a file, an HTTP body, a Windows-produced source —
    * through [[CharWidth.withoutControls]] first, or strip the `\r` yourself.
    */
  def styled(content: String, style: Style): Text =
    Text(content.split("\n", -1).toSeq.map(line => Line.styled(line, style)))

  /** [[styled]] with [[Style.Default]]: splits `content` on newlines, each resulting line unstyled. */
  def raw(content: String): Text =
    styled(content, Style.Default)
