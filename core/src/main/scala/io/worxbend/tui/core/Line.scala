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
