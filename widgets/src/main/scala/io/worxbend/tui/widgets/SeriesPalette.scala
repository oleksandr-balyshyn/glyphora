package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Color, Style}

/** Distinct styles cycled across chart series/sectors when the caller does not supply any.
  *
  * Public because it is the visible default of the `styles` parameter on [[StackedBarChart]] and [[PieChart]]: a
  * default a reader can see in the signature but could not name, extend, or inspect would be worse than no default.
  */
object SeriesPalette:

  /** The six styles cycled through, in order. Chosen to stay distinguishable on both light and dark terminals. */
  val Default: Vector[Style] = Vector(
    Style.Default.withFg(Color.Cyan),
    Style.Default.withFg(Color.Green),
    Style.Default.withFg(Color.Yellow),
    Style.Default.withFg(Color.Magenta),
    Style.Default.withFg(Color.Red),
    Style.Default.withFg(Color.Blue),
  )

  /** The style for series `index`, wrapping round [[Default]]. */
  def at(index: Int): Style = Default(index % Default.size)

  /** Cycles `styles`, falling back to [[Default]] when the caller passed an empty palette — `styles` is a public
    * parameter, and `index % 0` is an `ArithmeticException` out of the middle of a render.
    */
  private[widgets] def cycle(styles: Seq[Style], index: Int): Style =
    val palette = if styles.isEmpty then Default else styles
    palette(math.floorMod(index, palette.size))
