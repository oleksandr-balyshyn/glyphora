package io.worxbend.tui.widgets

import io.worxbend.tui.core.Style

/** How a column chart lets one bar look different from the rest.
  *
  * A chart has a single `style`/`barStyle` for all of its bars. Colouring one bar — the reading that broke a threshold,
  * the sample the cursor is on — used to mean splitting the data across two widgets and lining them up by hand. Instead
  * a chart takes an *override function*: given a bar's index and its value, it answers `Some(style)` for a bar that
  * should look different and `None` for one that should not.
  *
  * The answer is *patched over* the chart's own style rather than replacing it (see `Style.patch`), so an override that
  * sets only a foreground colour keeps the chart's background and text attributes. That is what makes
  * `(_, value) => Option.when(value > limit)(Style.Default.withFg(Color.Red))` do the obvious thing.
  */
object BarStyling:

  /** The override every chart starts with: no bar is restyled.
    *
    * A single shared value, not a fresh `(_, _) => None` per chart, because two charts built with the same arguments
    * have to compare equal — the DSL rebuilds its widgets every frame and construction tests pattern-match them, and a
    * new lambda each time would make every such comparison false.
    */
  val NoOverride: (Int, Long) => Option[Style] = (_, _) => None

  /** The style one bar is drawn in: the chart's own, with the override patched over it when there is one. */
  private[widgets] def styleAt(base: Style, restyle: (Int, Long) => Option[Style], index: Int, value: Long): Style =
    restyle(index, value).fold(base)(base.patch)
