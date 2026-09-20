package io.worxbend.tui.widgets

/** The inclusive range of world coordinates one axis of a [[Canvas]] maps onto its grid.
  *
  * A named pair, because `(Double, Double)` says nothing about which end is which or what the two numbers are a pair
  * *of* — the same tuple shape also serves this module for points, sizes and rates, and a function handed the wrong one
  * compiles fine. `Bounds` is the one that means "the low and high end of an axis", endpoints included.
  *
  * It is deliberately one-dimensional. Every consumer here — [[Canvas]], [[Chart]], [[Painter]] — carries two of them
  * (`xBounds`, `yBounds`) and treats the two axes independently: the horizontal mapping never reads the vertical range,
  * so a two-dimensional form would be a pair with no operations of its own. Pairing them at the call site keeps each
  * axis nameable on its own.
  *
  * A degenerate bound (`min == max`), or an inverted one, has no mapping onto the grid: the painter drops every paint
  * rather than dividing by zero. Nothing here normalises or rejects such a range — "nothing draws" is the answer the
  * rest of the widget already gives for it.
  */
final case class Bounds(min: Double, max: Double):

  /** The width of the range; zero for a degenerate bound. */
  def span: Double = max - min

  /** Whether `value` lies inside the range, endpoints included. Non-finite values are outside every range. */
  def contains(value: Double): Boolean = value >= min && value <= max

object Bounds:

  /** The same range read from a `(min, max)` tuple — the shape this type replaces. */
  def of(range: (Double, Double)): Bounds = Bounds(range._1, range._2)
