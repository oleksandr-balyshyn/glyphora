package io.worxbend.tui.core

/** One horizontal line of styled text, a sequence of differently-styled [[Span]]s.
  *
  * `alignment` is this line's *own* horizontal placement. `None`, the default, means "whatever the widget drawing me
  * was told to use" — that is what every line did before this field existed. Setting it overrides the widget for this
  * one row, so a single block of text can hold a left-aligned heading above right-aligned numbers without being split
  * into several widgets stacked by the layout solver.
  */
final case class Line(spans: Seq[Span], alignment: Option[Alignment] = None):
  def width: Int = spans.map(_.width).sum

  /** This line placed the given way, overriding the widget that draws it. The spans are untouched. */
  def aligned(alignment: Alignment): Line = copy(alignment = Some(alignment))

  /** Pins this row to the left edge even inside a centred or right-aligned widget. */
  def leftAligned: Line = aligned(Alignment.Left)

  /** Centres this row in the columns it is drawn in, whatever the widget was told to do. */
  def centered: Line = aligned(Alignment.Center)

  /** Pins this row to the right edge, which is what a column of numbers wants. */
  def rightAligned: Line = aligned(Alignment.Right)

  /** The line's characters with every [[Style]] dropped — the string a user would copy out of the terminal.
    *
    * The spans are concatenated in order with nothing inserted between them. `plainText.length` counts UTF-16 code
    * units, which says nothing about how many terminal columns the line occupies: ask [[width]] for that. Intended for
    * logging, clipboard payloads and test assertions, never for layout arithmetic.
    */
  def plainText: String = spans.map(_.content).mkString

  /** Every span's [[Style]] put through `transform`, the spans and their content unchanged.
    *
    * A `Line` holds no style of its own — it is its spans — so this is a fold over them rather than a field being
    * set. That makes the calls compose exactly as two `Style` calls would: `line.styled(_.bold).styled(_.withFg(c))`
    * gives every span a style that is both bold and coloured.
    */
  def styled(transform: Style => Style): Line = Line(spans.map(_.styled(transform)))

  /** `base` laid underneath every span's own style — see [[Span.under]]. The way a theme colour reaches a line built
    * by a helper that already set a few attributes of its own, without overruling them.
    */
  def under(base: Style): Line = Line(spans.map(_.under(base)))

  /** `style` layered on top of every span's own — see [[Span.patchStyle]]. The argument wins where it speaks, and
    * each span keeps whatever the argument says nothing about, so the spans stay different from one another.
    */
  def patchStyle(style: Style): Line = Line(spans.map(_.patchStyle(style)))

object Line:
  def raw(content: String): Line = Line(Seq(Span.raw(content)))

  def styled(content: String, style: Style): Line = Line(Seq(Span(content, style)))

  /** `Line("hi")` — an unstyled single-span line.
    *
    * An overload of the case class's own `apply`, so the obvious spelling works: without it `Line("hi")` does not
    * compile at all, because the generated `apply` takes a `Seq[Span]` and a bare `String` is not one. It is exactly
    * [[raw]] under a shorter name; `raw` stays for call sites that pass the function itself (`titles.map(Line.raw)`).
    */
  def apply(content: String): Line = raw(content)
