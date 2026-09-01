package io.worxbend.tui.core

/** A run of text rendered with a single [[Style]]. */
final case class Span(content: String, style: Style):
  def width: Int = CharWidth.of(content)

  /** This span with its [[Style]] put through `transform`, the content untouched: `span.styled(_.bold)`.
    *
    * The point is that a span handed back by a helper can be adjusted instead of taken apart and rebuilt. Before this
    * method the only way to change one was `span.copy(style = span.style.bold)`, which names the span twice and has to
    * spell out the field.
    */
  def styled(transform: Style => Style): Span = copy(style = transform(style))

  /** This span drawn over `base`: `base.patch(style)`, so the span's own settings win and `base` fills in only what
    * the span leaves unset.
    *
    * This is the direction a theme colour travels — the theme is the floor, and a span that already chose a colour of
    * its own keeps it. For the opposite direction, where the argument overrules the span, see [[Style.patch]].
    */
  def under(base: Style): Span = copy(style = base.patch(style))

  /** This span with `style` layered on top of its own: the argument's explicit choices win, and everything the
    * argument stays silent about survives.
    *
    * "Make this already-styled span italic without disturbing its colour" used to be
    * `span.copy(style = span.style.patch(Style.Default.italic))` at every call site. This is that expression under a
    * name. It is [[under]] with the two layers swapped: `under` puts the argument beneath, this puts it on top.
    */
  def patchStyle(style: Style): Span = copy(style = this.style.patch(style))

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
