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
)

/** Renders a pragmatic Markdown subset: `#`/`##`/`###`+ headings, `-`/`*` bullets, `1.` numbered items, `>`
  * blockquotes, fenced code blocks, and inline `**strong**` / `*emphasis*` / `` `code` ``.
  *
  * Deliberately excluded (recorded in SPEC.md §9): links, images, tables, nested lists, and syntax highlighting inside
  * code fences — this is a document *viewer* for help screens and READMEs, not a rendering-complete engine. Prose wraps
  * at the area width (cluster-safe); code blocks render verbatim.
  */
final case class Markdown(
    source: String,
    theme: MarkdownTheme = MarkdownTheme(),
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    Paragraph(MarkdownParser.parse(source, theme), wrap = true).render(area, buffer)

private[widgets] object MarkdownParser:

  def parse(source: String, theme: MarkdownTheme): Text =
    var inCodeFence = false
    val lines = source.split("\n", -1).toSeq.map { raw =>
      if raw.trim.startsWith("```") then
        inCodeFence = !inCodeFence
        Line(Seq.empty)
      else if inCodeFence then Line.styled(raw, theme.code)
      else blockLine(raw, theme)
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
        case None =>
          plain += text.charAt(index)
          index += 1
    flushPlain()
    spans.result()

  /** A styled run starting exactly at `index`, with how many chars it consumed, or `None`. */
  private def styledRun(text: String, index: Int, theme: MarkdownTheme): Option[(Span, Int)] =
    delimited(text, index, "**")
      .map((content, consumed) => (Span(content, theme.strong), consumed))
      .orElse(delimited(text, index, "*").map((content, consumed) => (Span(content, theme.emphasis), consumed)))
      .orElse(delimited(text, index, "`").map((content, consumed) => (Span(content, theme.code), consumed)))

  /** Non-empty text between `marker` at `index` and its next occurrence. */
  private def delimited(text: String, index: Int, marker: String): Option[(String, Int)] =
    if !text.startsWith(marker, index) then None
    else
      val contentStart = index + marker.length
      val end = text.indexOf(marker, contentStart)
      if end <= contentStart then None
      else Some((text.slice(contentStart, end), end + marker.length - index))
