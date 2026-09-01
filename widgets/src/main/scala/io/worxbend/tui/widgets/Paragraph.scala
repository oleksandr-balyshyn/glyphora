package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, CharWidth, Line, LineBreaks, Measured, Rect, Span, Style, Text, Widget}

/** Multi-line styled text with alignment and optional wrapping.
  *
  * `alignment` places every line; a [[Line]] that carries an alignment of its own overrides it for that one row, and a
  * wrapped line keeps its alignment on every row it spills onto.
  *
  * With [[Overflow.Wrap]] the text breaks at word boundaries: a word moves to the next row whole rather than being cut
  * in half, and the blanks that sat at the break are dropped instead of becoming a stray indent on the next row. A word
  * longer than the whole width has nowhere to go, so that one is broken between grapheme clusters — never inside one,
  * so a wide character or emoji is never split. With [[Overflow.Clip]], the default, long lines are cut at the area
  * edge.
  *
  * [[heightAt]] reports the rows the paragraph needs from the same `overflow` field `render` draws with, so the two
  * cannot disagree about whether the text wraps.
  */
final case class Paragraph(
    text: Text,
    alignment: Alignment = Alignment.Left,
    overflow: Overflow = Overflow.Clip,
    style: Style = Style.Default,
) extends Widget
    with Measured:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      // lazily: wrapping the whole document to draw one screenful makes render cost scale with the text, not the area
      val lines = overflow match
        case Overflow.Wrap => text.lines.iterator.flatMap(Paragraph.wrapLine(_, area.width))
        case Overflow.Clip => text.lines.iterator
      lines.take(area.height).zipWithIndex.foreach { (line, row) =>
        val lineWidth = math.min(line.width, area.width)
        // the line's own alignment wins over the paragraph's; `None` means "use the paragraph's", which is what
        // every line said before `Line` could carry one
        val startX    = line.alignment.getOrElse(alignment).originAt(area.x, area.width, lineWidth)
        val _         = LineRenderer.render(buffer, startX, area.y + row, line, area.right - startX, style)
      }

  /** The rows this text occupies at `width` — the measurement counterpart of [[render]], and always an answer: a
    * paragraph always knows its own height. A clipping paragraph is one row per line; a wrapping one counts the rows
    * `wrapLine` would produce, which is why the two share that function rather than each doing the arithmetic.
    *
    * A wrapping paragraph given no columns at all (`width <= 0`) measures zero rows, because zero rows is what it
    * draws: there is nothing to wrap into, `wrapLine` returns no rows and `render` returns immediately on an empty
    * area. It used to answer one row per source line there, which is the one width at which measurement and rendering
    * could disagree. Zero is an answer, not a refusal — [[io.worxbend.tui.core.Measured]] reserves `None` for "cannot
    * say", and a paragraph with no columns can say. A clipping paragraph is still one row per line at any width,
    * because clipping never consults the width to decide how many rows there are.
    */
  override def heightAt(width: Int): Option[Int] =
    val rows = overflow match
      case Overflow.Clip               => text.lines.size
      case Overflow.Wrap if width <= 0 => 0
      case Overflow.Wrap               => text.lines.map(line => math.max(1, Paragraph.wrapLine(line, width).size)).sum
    Some(rows)

  /** The columns this text needs so that no line is clipped and no line has to wrap — the width counterpart of
    * [[heightAt]], and always an answer, because the longest line is a property of the text itself and needs no
    * layout to work out.
    *
    * The `height` argument is ignored: a paragraph flows top to bottom, so how many rows it is given does not change
    * how wide it would like to be. The answer is the same for both [[Overflow]] modes on purpose — `Overflow.Clip`
    * needs this width to avoid cutting a line off, `Overflow.Wrap` needs it to avoid reflowing one. It is the longest
    * *line*, not the longest word: at the longest-word width the text still fits, but only after wrapping, which is
    * the thing this number exists to avoid.
    */
  override def widthAt(height: Int): Option[Int] =
    Some(text.width)

object Paragraph:

  /** Breaks `line` into the rows it occupies at `width`, at word boundaries where the row has one.
    *
    * This is the single owner of the wrapping decision: `render` draws the rows it returns and `heightAt` counts them,
    * so the two can never disagree. The rules, in the order the loop applies them:
    *
    *   - A word is committed to the current row only if the whole word fits. Otherwise the row is flushed and the word
    *     starts the next one.
    *   - The blanks at the point of the break are dropped, so a wrapped row never begins with the space that caused the
    *     break. Blanks the caller wrote at the *start* of the source line are content, not a break point, and are kept
    *     — indentation survives wrapping.
    *   - What counts as a blank is [[io.worxbend.tui.core.LineBreaks.isBreakingSpace]], so U+00A0 NO-BREAK SPACE holds
    *     two words together and U+200B ZERO WIDTH SPACE offers a break that costs no column.
    *   - A single word longer than the whole width cannot be placed whole anywhere, so it is broken between grapheme
    *     clusters, and a single cluster wider than the whole width gets a row of its own and is clipped by the
    *     renderer. Dropping it would be worse than clipping it: `heightAt` counts these rows, so a dropped cluster
    *     would shift every following line up by a row.
    *   - A source line that produces no rows at all (it was empty, or nothing but blanks) still yields one empty row,
    *     so a blank line in the source stays a blank line on screen.
    *
    * Span styles survive the reflow: each output row carries the spans it was built from, so bold or coloured runs from
    * [[Markdown]] keep their styling across a break.
    */
  private[widgets] def wrapLine(line: Line, width: Int): Seq[Line] =
    if width <= 0 then Seq.empty
    else if line.width <= width then Seq(line)
    else
      val wrapped = List.newBuilder[Line]
      var rows    = 0

      // The row being filled, the run of blanks seen since the last word, and the word being read. A word is only
      // moved from `word` onto `row` when it is known to fit, which is what makes the break land between words.
      var rowSpans    = Vector.empty[Span]
      var rowWidth    = 0
      var gapSpans    = Vector.empty[Span]
      var gapWidth    = 0
      var wordSpans   = Vector.empty[Span]
      var wordWidth   = 0
      // True until the first row is flushed: while it holds, the pending blanks are the caller's own indentation
      // rather than a break point, and are kept.
      var atLineStart = true

      def flushRow(): Unit =
        wrapped += Line(rowSpans, line.alignment)
        rows += 1
        rowSpans = Vector.empty
        rowWidth = 0
        atLineStart = false

      def commitWord(): Unit =
        if wordSpans.nonEmpty then
          if rowSpans.isEmpty then
            if atLineStart then
              rowSpans = gapSpans ++ wordSpans
              rowWidth = gapWidth + wordWidth
            else
              rowSpans = wordSpans
              rowWidth = wordWidth
          else if rowWidth + gapWidth + wordWidth <= width then
            rowSpans = rowSpans ++ gapSpans ++ wordSpans
            rowWidth = rowWidth + gapWidth + wordWidth
          else
            flushRow()
            rowSpans = wordSpans
            rowWidth = wordWidth
          wordSpans = Vector.empty
          wordWidth = 0
          // The blanks have now either been written into the row or dropped at a break; either way they are spent.
          // They are only cleared here, with a word: a run of blanks with no word after it yet is still growing.
          gapSpans = Vector.empty
          gapWidth = 0

      line.spans.foreach { span =>
        val clusters = CharWidth.graphemeClusters(span.content)
        while clusters.hasNext do
          val cluster = clusters.next()
          if LineBreaks.isBreakingSpace(cluster) then
            commitWord()
            gapSpans = appended(gapSpans, cluster, span.style)
            gapWidth += CharWidth.of(cluster)
          else if LineBreaks.isZeroWidthBreak(cluster) then
            // A break opportunity with no glyph, standing on its own at the very start of the text: end the word here
            // and drop the character, which draws nothing whether the break is taken or not.
            commitWord()
          else
            val clusterWidth = CharWidth.of(cluster)
            if wordWidth + clusterWidth > width then
              // The word alone is wider than any row can be, so it has to be broken. Put what has been read onto a
              // row of its own and carry on reading the rest of the word into the next one.
              commitWord()
              if rowSpans.nonEmpty then flushRow()
            wordSpans = appended(wordSpans, cluster, span.style)
            wordWidth += clusterWidth
            // A zero width space rides along inside the cluster before it, and says a break is allowed after that
            // cluster: end the word here so the next one may start on a new row.
            if LineBreaks.endsWithZeroWidthBreak(cluster) then commitWord()
      }
      commitWord()
      if rowSpans.nonEmpty || rows == 0 then flushRow()
      wrapped.result()

  /** `spans` with `text` on the end, merged into the last span when it carries the same style, so a run of clusters
    * read one at a time comes back out as one span rather than one span per character.
    */
  private def appended(spans: Vector[Span], text: String, style: Style): Vector[Span] =
    if spans.nonEmpty && spans.last.style == style then spans.init :+ Span(spans.last.content + text, style)
    else spans :+ Span(text, style)
