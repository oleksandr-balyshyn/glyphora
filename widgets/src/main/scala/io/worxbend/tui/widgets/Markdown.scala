package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Color, Line, Rect, Span, Style, Text, Widget}

/** Styles for each Markdown construct; override individual entries to theme the view. */
final case class MarkdownTheme(
    heading1: Style = Style.Default.bold.withFg(Color.Cyan),
    heading2: Style = Style.Default.bold,
    heading3: Style = Style.Default.underline,
    strong: Style = Style.Default.bold,
    emphasis: Style = Style.Default.italic,
    code: Style = Style.Default.withFg(Color.Yellow),
    quote: Style = Style.Default.dim.italic,
    bullet: Style = Style.Default.withFg(Color.Cyan),
    link: Style = Style.Default.withFg(Color.Blue).underline,
    syntax: SyntaxTheme = SyntaxTheme(),
)

/** Renders a pragmatic Markdown subset: `#`/`##`/`###`+ headings, `-`/`*` bullets, `1.` numbered items, `>`
  * blockquotes, fenced code blocks, and inline `**strong**` / `*emphasis*` / `` `code` ``.
  *
  * Inline `[label](url)` links render underlined with an OSC 8 target. Deliberately excluded: images, tables, nested
  * lists, and syntax highlighting inside code fences — this is a document *viewer* for help screens and READMEs, not a
  * rendering-complete engine. Prose wraps at the area width (cluster-safe); code blocks render verbatim.
  */
final case class Markdown(
    source: String,
    theme: MarkdownTheme = MarkdownTheme(),
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    Paragraph(MarkdownParser.parse(source, theme), wrap = true).render(area, buffer)

object Markdown:

  /** The rows the rendered markdown occupies at `width` — the measurement counterpart of rendering. */
  def heightOf(source: String, width: Int, theme: MarkdownTheme = MarkdownTheme()): Int =
    Paragraph.heightOf(MarkdownParser.parse(source, theme), width)

private[widgets] object MarkdownParser:

  def parse(source: String, theme: MarkdownTheme): Text =
    var fenceLanguage: Option[Language] = None
    val lines                           = source.split("\n", -1).toSeq.map { raw =>
      if raw.trim.startsWith("```") then
        fenceLanguage = if fenceLanguage.isDefined then None else Some(Language.of(raw.trim.drop(3)))
        Line(Seq.empty)
      else
        fenceLanguage match
          // an untagged (or unknown-language) fence renders verbatim, as before; a tagged one is highlighted
          case Some(Language.Generic) => Line.styled(raw, theme.code)
          case Some(language)         => SyntaxHighlighter.highlightLine(raw, language, theme.syntax)
          case None                   => blockLine(raw, theme)
    }
    Text(lines)

  private def blockLine(raw: String, theme: MarkdownTheme): Line =
    val trimmed = raw.trim
    if trimmed.isEmpty then Line(Seq.empty)
    else if trimmed.startsWith("###") then Line.styled(dropMarker(trimmed, '#'), theme.heading3)
    else if trimmed.startsWith("##") then Line.styled(dropMarker(trimmed, '#'), theme.heading2)
    else if trimmed.startsWith("#") then Line.styled(dropMarker(trimmed, '#'), theme.heading1)
    else if trimmed.startsWith("> ") then Line.styled("▎ " + trimmed.drop(2), theme.quote)
    else if trimmed.startsWith("- ") || trimmed.startsWith("* ") then
      Line(Span("• ", theme.bullet) +: inlineSpans(trimmed.drop(2), theme))
    else
      numberedItem(trimmed) match
        case Some((marker, rest)) => Line(Span(marker + " ", theme.bullet) +: inlineSpans(rest, theme))
        case None                 => Line(inlineSpans(raw, theme))

  private def dropMarker(text: String, marker: Char): String =
    text.dropWhile(_ == marker).stripLeading

  private def numberedItem(text: String): Option[(String, String)] =
    val digits = text.takeWhile(_.isDigit)
    if digits.nonEmpty && text.startsWith(digits + ". ") then Some((digits + ".", text.drop(digits.length + 2)))
    else None

  /** Inline scanner for `**strong**`, `*emphasis*`, and `` `code` ``; unclosed markers render literally. */
  private def inlineSpans(text: String, theme: MarkdownTheme): Seq[Span] =
    val spans = Seq.newBuilder[Span]
    val plain = StringBuilder()

    def flushPlain(): Unit =
      if plain.nonEmpty then
        spans += Span.raw(plain.result())
        plain.clear()

    var index = 0
    while index < text.length do
      styledRun(text, index, theme) match
        case Some((span, consumed)) =>
          flushPlain()
          spans += span
          index += consumed
        case None                   =>
          plain += text.charAt(index)
          index += 1
    flushPlain()
    spans.result()

  /** A styled run starting exactly at `index`, with how many chars it consumed, or `None`. */
  private def styledRun(text: String, index: Int, theme: MarkdownTheme): Option[(Span, Int)] =
    linkRun(text, index, theme)
      .orElse(
        delimited(text, index, "**")
          .map((content, consumed) => (Span(content, theme.strong), consumed))
          .orElse(delimited(text, index, "*").map((content, consumed) => (Span(content, theme.emphasis), consumed)))
          .orElse(delimited(text, index, "`").map((content, consumed) => (Span(content, theme.code), consumed)))
      )

  /** `[label](url)`: the label renders link-styled with the OSC 8 target attached. */
  private def linkRun(text: String, index: Int, theme: MarkdownTheme): Option[(Span, Int)] =
    if text.charAt(index) != '[' then None
    else
      val close = text.indexOf("](", index + 1)
      val end   = if close < 0 then -1 else text.indexOf(')', close + 2)
      if close < 0 || end < 0 then None
      else
        val label = text.slice(index + 1, close)
        val url   = text.slice(close + 2, end)
        if label.isEmpty || url.isEmpty then None
        else Some((Span(label, theme.link.withLink(url)), end + 1 - index))

  /** Non-empty text between `marker` at `index` and its next occurrence. */
  private def delimited(text: String, index: Int, marker: String): Option[(String, Int)] =
    if !text.startsWith(marker, index) then None
    else
      val contentStart = index + marker.length
      val end          = text.indexOf(marker, contentStart)
      if end <= contentStart then None
      else Some((text.slice(contentStart, end), end + marker.length - index))
