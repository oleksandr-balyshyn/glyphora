package io.worxbend.tui.core

/** Multi-line styled text. */
final case class Text(lines: Seq[Line]):
  def height: Int = lines.size

  def width: Int = if lines.isEmpty then 0 else lines.map(_.width).max

  /** Every line's [[Line.plainText]] joined with `\n`, with no trailing newline added.
    *
    * The inverse of [[Text.raw]] up to styling: `Text.raw(s).plainText == s` for any `s` that contains no `\r`,
    * because `raw` splits on `\n` keeping empty trailing lines and this joins them back the same way. As with
    * [[Line.plainText]], the result is for logging, clipboard payloads and test assertions — not for measuring, which
    * is what [[width]] and [[height]] are for.
    */
  def plainText: String = lines.map(_.plainText).mkString("\n")

  /** Every span of every line put through `transform` — see [[Line.styled]] for why this is a fold over the spans
    * rather than a style stored on the text.
    */
  def styled(transform: Style => Style): Text = Text(lines.map(_.styled(transform)))

  /** `base` laid underneath every span's own style, so a span that chose a setting keeps it — see [[Span.under]]. */
  def under(base: Style): Text = Text(lines.map(_.under(base)))

  /** `style` layered on top of every span of every line — see [[Span.patchStyle]]. */
  def patchStyle(style: Style): Text = Text(lines.map(_.patchStyle(style)))

  /** This text with `line` added below its existing lines. The receiver is unchanged; a new value is returned. */
  def appended(line: Line): Text = Text(lines :+ line)

  /** This text with every line of `other` added below its own, in order. */
  def appendedAll(other: Text): Text = Text(lines ++ other.lines)

  /** This text with `span` added to the right-hand end of its last line.
    *
    * When the text has no lines at all there is no last line to extend, so one is started holding just `span` and the
    * result is a one-line text. That empty case is the whole reason this method exists: code that accumulates a text
    * span by span otherwise has to test `lines.isEmpty` itself at every call site, and the two branches are easy to
    * get the wrong way round.
    *
    * A text whose last line is present but has no spans is *not* the empty case — `span` becomes that line's only
    * span, and the line count does not change.
    */
  def appendedToLast(span: Span): Text =
    if lines.isEmpty then Text(Seq(Line(Seq(span))))
    else Text(lines.init :+ lines.last.appended(span))

  /** [[appended]] as an operator: `header + body + footer` stacks three rows into one block. */
  infix def +(line: Line): Text = appended(line)

  /** [[appendedAll]] as an operator: every row of `other` below this text's own. */
  infix def ++(other: Text): Text = appendedAll(other)

object Text:
  /** A text with no lines: zero rows, zero columns, and the identity for [[Text.appendedAll]], so a fold that
    * accumulates lines has somewhere to start.
    */
  val Empty: Text = Text(Seq.empty)

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
