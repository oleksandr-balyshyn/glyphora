package io.worxbend.tui.core

/** One horizontal line of styled text, a sequence of differently-styled [[Span]]s. */
final case class Line(spans: Seq[Span]):
  def width: Int = spans.map(_.width).sum

object Line:
  def raw(content: String): Line = Line(Seq(Span.raw(content)))

  def styled(content: String, style: Style): Line = Line(Seq(Span(content, style)))
