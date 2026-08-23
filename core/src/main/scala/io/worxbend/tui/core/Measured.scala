package io.worxbend.tui.core

/** A widget's own answer to "how much space does my content need?".
  *
  * Layout in this toolkit is top-down: a container hands a [[Widget]] a [[Rect]] and the widget paints inside it. That
  * is enough for rendering, but not for the passes that have to decide how big the area should be in the first place —
  * a scroll view needs the full height of content it is about to show a window onto, and a column that sums its
  * children needs each child's rows. Before this trait each such widget grew its own private spelling of the same
  * question (`heightOf`, `preferredWidth`, `widthOf`, `preferredHeight`), and every caller had to know which one its
  * particular widget happened to expose. This is the single contract they all speak instead.
  *
  * '''`None` is not a size.''' It means "this widget cannot say" — a chart that fills whatever it is given genuinely
  * has no natural height. A caller must treat it as unmeasurable and fall back to something it can defend (the area it
  * already has, an explicit constraint the user set), never as zero and never as one. Answering a made-up number here
  * is worse than answering `None`, because a wrong measurement silently mis-sizes a layout while `None` at least tells
  * the caller it is guessing.
  *
  * Both methods take the *other* axis, because content size is usually a function of it: wrapped prose is
  * `heightAt(width)`, and a rotated or column-flowing widget would be `widthAt(height)`. A widget that does not care
  * about the axis it is handed ignores the argument.
  *
  * '''Ownership.''' Implementations answer from their own immutable fields — the same fields `render` draws from — so
  * measurement and rendering cannot drift apart. Nothing here touches a buffer or a terminal, so unlike
  * [[Widget.render]] these methods are safe to call from any thread.
  */
trait Measured:

  /** The rows this widget's content needs when given `width` columns, or `None` if it cannot say. */
  def heightAt(width: Int): Option[Int] = None

  /** The columns this widget's content needs when given `height` rows, or `None` if it cannot say. */
  def widthAt(height: Int): Option[Int] = None
