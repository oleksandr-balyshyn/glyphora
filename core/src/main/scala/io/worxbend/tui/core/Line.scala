package io.worxbend.tui.core

/** One horizontal line of styled text, a sequence of differently-styled [[Span]]s. */
final case class Line(spans: Seq[Span]):
  def width: Int = spans.map(_.width).sum

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
