package io.worxbend.tui.widgets

import io.worxbend.tui.core.{GlyphSupport, Symbols}

/** The glyph vocabulary a progress bar draws with: what a filled cell looks like, what an empty one looks like, and
  * optionally how to draw the cell the boundary falls inside.
  *
  * `partials` is what separates a bar that steps a whole cell at a time from one that moves smoothly. Ordered narrowest
  * to widest and excluding `fill` itself, it lets a bar show progress finer than its own cell grid: eight partial
  * blocks turn a 20-column bar into 160 distinguishable positions. Leave it empty and the bar rounds to whole cells,
  * which is the right choice for vocabularies with no partial glyphs (`#`, `=`) and for a bar one or two cells wide,
  * where a partial glyph reads as noise.
  *
  * That distinction is also why the two cases round differently, and it is deliberate: with partials the fill is
  * *floored* and the remainder becomes the boundary glyph, so the bar never claims progress that has not happened;
  * without them it rounds to nearest, because that is the closest a whole cell can get.
  */
final case class ProgressPreset(
    name: String,
    fill: String,
    track: String,
    partials: Vector[String] = Vector.empty,
    head: Option[String] = None,
):

  /** This vocabulary, or [[ProgressPreset.Ascii]] when `support` does not reach what its glyphs need.
    *
    * A vocabulary whose glyphs are already ASCII — `Ascii` and `Arrow` — comes back untouched at every rung; the check
    * is on the glyphs themselves rather than on a list of names, so a preset added later is covered without anyone
    * remembering to add it.
    *
    * Only [[io.worxbend.tui.core.GlyphSupport.Full]] keeps a non-ASCII vocabulary. The block-element and braille fills
    * are the whole non-ASCII catalogue and they sit above the box-drawing rung, so `BoxDrawing` has nothing to keep
    * that `Ascii` would not also have to drop.
    */
  def degraded(support: GlyphSupport): ProgressPreset =
    if support == GlyphSupport.Full || GlyphFloor.allAscii(glyphVocabulary) then this else ProgressPreset.Ascii

  /** Every glyph this preset can draw, in no particular order — what [[degraded]] inspects. */
  private def glyphVocabulary: Iterable[String] = Seq(fill, track) ++ partials ++ head

  /** Whether this vocabulary can draw progress finer than one cell. */
  def isSubCell: Boolean = partials.nonEmpty

  /** The glyphs for `fraction` of a `width`-cell bar, left to right.
    *
    * Returns exactly `width` entries (empty when `width <= 0`). `fraction` is clamped to `[0, 1]`, so a caller may pass
    * a raw ratio; `NaN` reads as no progress rather than as a full bar.
    */
  def glyphs(fraction: Double, width: Int): Vector[String] =
    if width <= 0 then Vector.empty
    else
      val filled  = filledCells(fraction, width)
      val partial = if isSubCell then partialAt(exactCells(fraction, width) - filled) else None
      Vector.tabulate(width): index =>
        if index < filled then fillGlyphAt(index, filled, width)
        else if index == filled then partial.getOrElse(track)
        else track

  /** How many of the `width` cells [[glyphs]] draws as fully filled — what a caller styles as "done". */
  def filledCells(fraction: Double, width: Int): Int =
    if width <= 0 then 0
    else
      val exact = exactCells(fraction, width)
      val full  = if isSubCell then math.floor(exact).toInt else math.round(exact).toInt
      math.max(0, math.min(width, full))

  /** Where the bar's boundary falls, in cells, before any rounding — `2.75` means two whole cells and three quarters of
    * a third.
    *
    * The one place the fraction is turned into a length, so [[glyphs]] and [[filledCells]] cannot disagree about where
    * the boundary is. That agreement is not cosmetic: [[LineGauge]] asks both in the same breath, one for the glyphs
    * and one for which of them to style as done, and a drift of a single cell between them mis-styles the boundary of
    * every bar in the library.
    */
  private def exactCells(fraction: Double, width: Int): Double = Fraction.clamped(fraction) * width

  /** The last filled cell becomes `head` when this preset has one and the bar is not yet complete. An empty bar draws
    * no head at all — a `>` sitting at column zero reads as progress that has not started.
    */
  private def fillGlyphAt(index: Int, filled: Int, width: Int): String =
    head match
      case Some(cap) if index == filled - 1 && filled < width => cap
      case _                                                  => fill

  private def partialAt(remainder: Double): Option[String] =
    if remainder <= 0.0 then None
    else
      // `partials.size + 1` buckets: the top one is a whole `fill`, which the floored cell count already covers
      val bucket = math.floor(remainder * (partials.size + 1)).toInt
      if bucket <= 0 then None else Some(partials(math.min(bucket, partials.size) - 1))

object ProgressPreset:

  /** Heavy and light box-drawing lines. The default, and what a one-row meter reads best as. */
  val Line: ProgressPreset = ProgressPreset("line", fill = "━", track = "─")

  /** Full blocks with eighth-block partials — the smoothest bar available, and the best default for wide bars. */
  val Blocks: ProgressPreset =
    ProgressPreset("blocks", fill = Symbols.Block.Full, track = " ", partials = Symbols.Block.HorizontalPartials)

  /** Full blocks over a shaded track, so the bar's extent is visible even where it is empty. */
  val BlocksShaded: ProgressPreset =
    ProgressPreset(
      "blocks-shaded",
      fill = Symbols.Block.Full,
      track = Symbols.Shade.Light,
      partials = Symbols.Block.HorizontalPartials,
    )

  /** Three shading levels stepping up to a full block — reads well without a color-capable terminal. */
  val Shaded: ProgressPreset =
    ProgressPreset(
      "shaded",
      fill = Symbols.Block.Full,
      track = Symbols.Shade.Light,
      partials = Vector(Symbols.Shade.Medium, Symbols.Shade.Dark),
    )

  /** ASCII only: `#` over `-`. Safe anywhere, including a log file. */
  val Ascii: ProgressPreset = ProgressPreset("ascii", fill = "#", track = "-")

  /** ASCII only, with a leading `>` — the `wget`/`pip` look. */
  val Arrow: ProgressPreset = ProgressPreset("arrow", fill = "=", track = "-", head = Some(">"))

  /** Braille, filling a cell from the bottom up in four steps. */
  val Dots: ProgressPreset =
    ProgressPreset("dots", fill = "⣿", track = "⢀", partials = Vector("⡀", "⡄", "⡆", "⡇", "⣇", "⣧", "⣷"))

  /** Half-height blocks, so the bar sits on a baseline rather than filling the row. */
  val Baseline: ProgressPreset = ProgressPreset("baseline", fill = "▄", track = "▁")

  /** A dotted track with a solid fill — quiet enough for a dense dashboard. */
  val Minimal: ProgressPreset = ProgressPreset("minimal", fill = "▪", track = "·")

  /** Every built-in preset, in catalogue order. */
  val All: Vector[ProgressPreset] =
    Vector(Line, Blocks, BlocksShaded, Shaded, Ascii, Arrow, Dots, Baseline, Minimal)

  /** Looks a preset up by its [[ProgressPreset.name]] — for a config file or a `--progress-preset` flag. */
  def byName(name: String): Option[ProgressPreset] = byNameIndex.get(name)

  private val byNameIndex: Map[String, ProgressPreset] = All.map(preset => preset.name -> preset).toMap
