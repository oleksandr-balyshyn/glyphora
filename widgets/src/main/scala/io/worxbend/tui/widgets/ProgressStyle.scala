package io.worxbend.tui.widgets

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
final case class ProgressStyle(
    name: String,
    fill: String,
    track: String,
    partials: Vector[String] = Vector.empty,
    head: Option[String] = None,
):

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
      val clamped = if fraction.isNaN then 0.0 else math.max(0.0, math.min(1.0, fraction))
      val exact   = clamped * width
      val full    = if isSubCell then math.floor(exact).toInt else math.round(exact).toInt
      val filled  = math.max(0, math.min(width, full))
      val partial = if isSubCell then partialAt(exact - filled) else None
      Vector.tabulate(width): index =>
        if index < filled then fillGlyphAt(index, filled, width)
        else if index == filled then partial.getOrElse(track)
        else track

  /** How many of the `width` cells [[glyphs]] draws as fully filled — what a caller styles as "done". */
  def filledCells(fraction: Double, width: Int): Int =
    if width <= 0 then 0
    else
      val clamped = if fraction.isNaN then 0.0 else math.max(0.0, math.min(1.0, fraction))
      val exact   = clamped * width
      val full    = if isSubCell then math.floor(exact).toInt else math.round(exact).toInt
      math.max(0, math.min(width, full))

  /** The last filled cell becomes `head` when this style has one and the bar is not yet complete. An empty bar draws no
    * head at all — a `>` sitting at column zero reads as progress that has not started.
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

object ProgressStyle:

  /** Heavy and light box-drawing lines. The default, and what a one-row meter reads best as. */
  val Line: ProgressStyle = ProgressStyle("line", fill = "━", track = "─")

  /** Full blocks with eighth-block partials — the smoothest bar available, and the best default for wide bars. */
  val Blocks: ProgressStyle =
    ProgressStyle("blocks", fill = "█", track = " ", partials = Vector("▏", "▎", "▍", "▌", "▋", "▊", "▉"))

  /** Full blocks over a shaded track, so the bar's extent is visible even where it is empty. */
  val BlocksShaded: ProgressStyle =
    ProgressStyle("blocks-shaded", fill = "█", track = "░", partials = Vector("▏", "▎", "▍", "▌", "▋", "▊", "▉"))

  /** Three shading levels stepping up to a full block — reads well without a color-capable terminal. */
  val Shaded: ProgressStyle =
    ProgressStyle("shaded", fill = "█", track = "░", partials = Vector("▒", "▓"))

  /** ASCII only: `#` over `-`. Safe anywhere, including a log file. */
  val Ascii: ProgressStyle = ProgressStyle("ascii", fill = "#", track = "-")

  /** ASCII only, with a leading `>` — the `wget`/`pip` look. */
  val Arrow: ProgressStyle = ProgressStyle("arrow", fill = "=", track = "-", head = Some(">"))

  /** Braille, filling a cell from the bottom up in four steps. */
  val Dots: ProgressStyle =
    ProgressStyle("dots", fill = "⣿", track = "⢀", partials = Vector("⡀", "⡄", "⡆", "⡇", "⣇", "⣧", "⣷"))

  /** Half-height blocks, so the bar sits on a baseline rather than filling the row. */
  val Baseline: ProgressStyle = ProgressStyle("baseline", fill = "▄", track = "▁")

  /** A dotted track with a solid fill — quiet enough for a dense dashboard. */
  val Minimal: ProgressStyle = ProgressStyle("minimal", fill = "▪", track = "·")

  /** Every built-in style, in catalogue order. */
  val All: Vector[ProgressStyle] =
    Vector(Line, Blocks, BlocksShaded, Shaded, Ascii, Arrow, Dots, Baseline, Minimal)

  /** Looks a style up by its [[ProgressStyle.name]] — for a config file or a `--progress-style` flag. */
  def byName(name: String): Option[ProgressStyle] = byNameIndex.get(name)

  private val byNameIndex: Map[String, ProgressStyle] = All.map(style => style.name -> style).toMap
