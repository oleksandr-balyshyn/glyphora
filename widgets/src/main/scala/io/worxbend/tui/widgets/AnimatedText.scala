package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, CharWidth, Rect, Style, Widget}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** What an [[AnimatedText]] does to its content over time.
  *
  * Each case carries its own knobs rather than [[AnimatedText]] carrying the union of all of them, so a call site
  * cannot set a typewriter's cursor on a shimmer and quietly have it ignored.
  */
enum TextEffect:

  /** Reveals the text one grapheme at a time, with a blinking cursor at the write head; the cursor disappears once the
    * whole string is out, so a finished line looks finished.
    */
  case Typewriter(charactersPerSecond: Double = 24.0, cursor: String = "▋")

  /** A bright crest travelling through the text — the quietest of the four, and the one that survives on a terminal
    * with no color, because it moves emphasis rather than hue.
    */
  case Wave(crestWidth: Int = 2, cellsPerSecond: Double = 12.0)

  /** Colors every grapheme from a ramp, with the ramp itself scrolling, so the text runs through the gradient. Needs a
    * color-capable terminal to read as anything at all.
    */
  case Gradient(ramp: ColorRamp, cellsPerSecond: Double = 6.0)

  /** A narrow highlight sweeping across, with sparkle glyphs riding its leading edge — the "loading shine" effect.
    * Pauses between sweeps rather than looping continuously, which is what stops it becoming a strobe.
    */
  case Shimmer(sparkle: String = "✦", period: FiniteDuration = 2200.millis)

  /** Sends a vertical wave through the text, each grapheme bouncing on a phase offset from its neighbour, leaving a
    * dimmed after-image `trail` rows behind. Needs at least two rows of area to show motion; at one row it degrades to
    * a flat line rather than to nothing.
    */
  case Bounce(trail: Int = 2, cyclesPerSecond: Double = 0.8)

/** Text with a time-based effect applied to it, drawn from how long the animation has been running.
  *
  * Like every animation here, the frame is a pure function of `elapsed`, so a test renders any moment directly and the
  * ambient `AnimationClock` drives it in an app with no counter to thread through.
  *
  * `style` is the resting appearance and `highlightStyle` the emphasised one; an effect uses one, the other, or a
  * blend, according to what it does.
  */
final case class AnimatedText(
    content: String,
    elapsed: FiniteDuration,
    effect: TextEffect = TextEffect.Wave(),
    style: Style = Style.Default,
    highlightStyle: Style = Style.Default.bold,
) extends Widget:

  private lazy val clusters: Vector[String] = CharWidth.graphemeClusters(content).toVector

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty && clusters.nonEmpty then
      effect match
        case TextEffect.Typewriter(speed, cursor) => renderTypewriter(area, buffer, speed, cursor)
        case TextEffect.Wave(crest, speed)        => renderWave(area, buffer, crest, speed)
        case TextEffect.Gradient(ramp, speed)     => renderGradient(area, buffer, ramp, speed)
        case TextEffect.Shimmer(sparkle, period)  => renderShimmer(area, buffer, sparkle, period)
        case TextEffect.Bounce(trail, cycles)     => renderBounce(area, buffer, trail, cycles)

  /** Writes `cluster` at `x` if it fits, blanking the continuation cell of a wide glyph; returns the next x. */
  private def put(buffer: Buffer, x: Int, y: Int, cluster: String, cellStyle: Style, right: Int): Int =
    val width = math.max(1, CharWidth.of(cluster))
    if x + width <= right then
      buffer.set(x, y, Cell(cluster, cellStyle))
      if width == 2 then buffer.set(x + 1, y, Cell.Empty)
    x + width

  private def renderTypewriter(area: Rect, buffer: Buffer, speed: Double, cursor: String): Unit =
    val revealed = if speed <= 0 then clusters.size else (elapsed.toNanos / 1e9 * speed).toInt
    val shown    = math.max(0, math.min(clusters.size, revealed))
    var x        = area.x
    var index    = 0
    while index < shown && x < area.right do
      x = put(buffer, x, area.y, clusters(index), style, area.right)
      index += 1
    // the cursor blinks only while there is still text to come, so a finished line reads as finished
    if shown < clusters.size && x < area.right then
      val blinkOn = Animation.step(elapsed, AnimatedText.CursorBlink, 2) == 0
      if blinkOn then
        val _ = put(buffer, x, area.y, cursor, highlightStyle, area.right)

  private def renderWave(area: Rect, buffer: Buffer, crestWidth: Int, speed: Double): Unit =
    val crest = Animation.stepAtRate(elapsed, speed, clusters.size)
    var x     = area.x
    clusters.zipWithIndex.foreach: (cluster, index) =>
      val onCrest = math.abs(index - crest) < math.max(1, crestWidth)
      x = put(buffer, x, area.y, cluster, if onCrest then highlightStyle else style, area.right)

  private def renderGradient(area: Rect, buffer: Buffer, ramp: ColorRamp, speed: Double): Unit =
    val offset = Animation.stepAtRate(elapsed, speed, clusters.size)
    var x      = area.x
    clusters.zipWithIndex.foreach: (cluster, index) =>
      // the ramp scrolls through the text rather than the text through the ramp, so the string stays put
      val position =
        if clusters.sizeIs <= 1 then 0.0 else math.floorMod(index - offset, clusters.size) / (clusters.size - 1.0)
      x = put(buffer, x, area.y, cluster, style.withFg(ramp.at(position)), area.right)

  private def renderShimmer(area: Rect, buffer: Buffer, sparkle: String, period: FiniteDuration): Unit =
    // the sweep crosses in the first half of the period and rests for the second, so it reads as a shine, not a strobe
    val sweepSlots = math.max(1, clusters.size * 2)
    val head       = Animation.step(elapsed, period, sweepSlots)
    var x          = area.x
    clusters.zipWithIndex.foreach: (cluster, index) =>
      val distance  = head - index
      val cellStyle =
        if distance == 0 then highlightStyle
        else if distance > 0 && distance <= AnimatedText.ShimmerTail then highlightStyle.dim
        else style
      val glyph     = if distance == 0 && CharWidth.of(sparkle) > 0 then sparkle else cluster
      x = put(buffer, x, area.y, glyph, cellStyle, area.right)

  private def renderBounce(area: Rect, buffer: Buffer, trail: Int, cycles: Double): Unit =
    val rows    = area.height
    val seconds = elapsed.toNanos / 1e9
    var x       = area.x
    clusters.zipWithIndex.foreach: (cluster, index) =>
      val width = math.max(1, CharWidth.of(cluster))
      if x + width <= area.right then
        // a phase offset per grapheme turns a shared bounce into a wave travelling along the string
        val phase  = seconds * cycles * 2 * math.Pi - index * AnimatedText.BouncePhaseStep
        val height = if rows <= 1 then 0 else ((math.sin(phase) + 1) / 2 * (rows - 1)).round.toInt
        val row    = area.y + math.max(0, math.min(rows - 1, rows - 1 - height))
        var back   = math.max(0, trail)
        while back > 0 do
          val trailRow = row + back
          // the after-image is the same glyph as the head, so it occupies the same columns: a two-column cluster has to
          // claim its continuation cell on the trail row too, or whatever was underneath shows through the middle of it
          if trailRow < area.bottom then
            val _ = put(buffer, x, trailRow, cluster, style.dim, area.right)
          back -= 1
        val _      = put(buffer, x, row, cluster, highlightStyle, area.right)
      x += width

  /** The rows this text needs: one, except for a bounce, which needs room to move. */
  def preferredHeight: Int = effect match
    case TextEffect.Bounce(trail, _) => math.max(2, trail + 2)
    case _                           => 1

object AnimatedText:

  /** Half a blink cycle, matching the rate most terminals blink their own cursor at. */
  private val CursorBlink: FiniteDuration = 1000.millis

  /** How many graphemes behind the shimmer head stay lit. */
  private val ShimmerTail = 2

  /** Radians between neighbouring graphemes in a bounce — a third of a cycle across three characters reads as a wave
    * rather than as everything moving together.
    */
  private val BouncePhaseStep = 0.6
