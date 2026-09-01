package io.worxbend.tui.core

/** A run of text rendered with a single [[Style]]. */
final case class Span(content: String, style: Style):
  def width: Int = CharWidth.of(content)

object Span:
  def raw(content: String): Span = Span(content, Style.Default)

  /** `Span.styled("hi", warn)` — the named counterpart to [[raw]], and exactly the case class's own
    * `Span(content, style)`.
    *
    * It exists so the three text values read as one vocabulary at a call site: before it, `Line.styled(t, s)` sitting
    * next to a bare `Span(t, s)` looked like two different kinds of thing even though both build a styled run. It also
    * gives callers a function value to pass, as in `labels.map(Span.styled(_, warn))`.
    */
  def styled(content: String, style: Style): Span = Span(content, style)

  /** `Span("hi")` — an unstyled run, the same value [[raw]] returns.
    *
    * An overload of the case class's generated two-argument `apply`, so the obvious spelling works: without it
    * `Span("hi")` does not compile at all, because the generated `apply` wants a `Style` as well. [[raw]] stays for
    * call sites that pass the function itself, as in `parts.map(Span.raw)`, where an overloaded name cannot be
    * eta-expanded without an explicit type.
    */
  def apply(content: String): Span = raw(content)
