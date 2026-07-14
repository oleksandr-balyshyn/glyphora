package io.worxbend.tui.runtime

import io.worxbend.tui.core.{Buffer, Cell, Color, Rect, Style}

import scala.concurrent.duration.{Duration, DurationInt, DurationLong, FiniteDuration}

/** A post-render frame transform (the tachyonfx model, original implementation): widgets render normally, then active
  * effects mutate the rendered cells based on elapsed time. Effects are stateless in wall-clock — the runtime tracks
  * each effect's start and passes total `elapsed`, which keeps combinators pure and replayable.
  */
trait Effect:

  /** Transforms the rendered frame in place. */
  def process(elapsed: FiniteDuration, buffer: Buffer, area: Rect): Unit

  /** Total running time; `Duration.Inf` for effects that never finish (pulse, marquee). */
  def duration: Duration

  final def isDone(elapsed: FiniteDuration): Boolean =
    duration match
      case finite: FiniteDuration => elapsed >= finite
      case _                      => false

object Effect:

  /** Content fades up from the background: cell colors interpolate from black to their rendered values. */
  def fadeIn(duration: FiniteDuration, easing: Easing = Easing.QuadOut): Effect =
    fade(duration, easing, reverse = false)

  /** Content fades away to black. */
  def fadeOut(duration: FiniteDuration, easing: Easing = Easing.QuadIn): Effect =
    fade(duration, easing, reverse = true)

  /** Cells materialize in a seeded pseudo-random order (tachyonfx's `coalesce`). */
  def coalesce(duration: FiniteDuration, easing: Easing = Easing.QuadOut, seed: Int = 42): Effect =
    scatter(duration, easing, seed, revealed = true)

  /** Cells dissolve away in a seeded pseudo-random order. */
  def dissolve(duration: FiniteDuration, easing: Easing = Easing.QuadIn, seed: Int = 42): Effect =
    scatter(duration, easing, seed, revealed = false)

  /** Content reveals column by column, left to right. */
  def sweepIn(duration: FiniteDuration, easing: Easing = Easing.QuadOut): Effect =
    new TimedEffect(duration, easing):
      def transform(progress: Double, buffer: Buffer, area: Rect): Unit =
        val visibleColumns = math.round(progress * area.width).toInt
        eraseWhere(buffer, area)((x, _) => x - area.x >= visibleColumns)

  /** Content slides in from the right edge. */
  def slideInFromRight(duration: FiniteDuration, easing: Easing = Easing.QuadOut): Effect =
    new TimedEffect(duration, easing):
      def transform(progress: Double, buffer: Buffer, area: Rect): Unit =
        val shift = math.round((1 - progress) * area.width).toInt
        if shift > 0 then
          val snapshot = buffer.snapshot
          var y        = area.y
          while y < area.bottom do
            var x = area.right - 1
            while x >= area.x do
              val sourceX = x - shift
              val cell    = if sourceX >= area.x then snapshot.get(sourceX, y) else Cell.Empty
              buffer.set(x, y, cell)
              x -= 1
            y += 1

  /** Cells reveal in reading order (row-major), like typing. */
  def typewriter(duration: FiniteDuration, easing: Easing = Easing.Linear): Effect =
    new TimedEffect(duration, easing):
      def transform(progress: Double, buffer: Buffer, area: Rect): Unit =
        val total   = area.area
        val visible = math.round(progress * total).toInt
        eraseWhere(buffer, area)((x, y) => (y - area.y) * area.width + (x - area.x) >= visible)

  /** Endless brightness oscillation with the given period. */
  def pulse(period: FiniteDuration = 1.second): Effect =
    new Effect:
      def duration: Duration                                                 = Duration.Inf
      def process(elapsed: FiniteDuration, buffer: Buffer, area: Rect): Unit =
        val phase      = (elapsed.toMillis % period.toMillis).toDouble / period.toMillis
        val brightness = 0.55 + 0.45 * math.sin(phase * 2 * math.Pi)
        mapCells(buffer, area)(style => withFgScaled(style, brightness))

  /** Runs `effects` one after another. */
  def sequence(effects: Effect*): Effect =
    new Effect:
      def duration: Duration = effects.foldLeft(Duration.Zero: Duration)((acc, e) => acc + e.duration)
      def process(elapsed: FiniteDuration, buffer: Buffer, area: Rect): Unit =
        var remaining = elapsed
        val active    = effects.iterator.dropWhile { effect =>
          effect.duration match
            case finite: FiniteDuration if remaining >= finite =>
              remaining -= finite
              true
            case _                                             => false
        }
        if active.hasNext then active.next().process(remaining, buffer, area)

  /** Runs `effects` simultaneously; done when the longest finishes. */
  def parallel(effects: Effect*): Effect =
    new Effect:
      def duration: Duration = effects.map(_.duration).maxOption.getOrElse(Duration.Zero)
      def process(elapsed: FiniteDuration, buffer: Buffer, area: Rect): Unit =
        effects.foreach(_.process(elapsed, buffer, area))

  /** Waits `pause` before `effect` starts (the effect is held at progress zero during the wait). */
  def delay(pause: FiniteDuration, effect: Effect): Effect =
    new Effect:
      def duration: Duration                                                 = effect.duration + pause
      def process(elapsed: FiniteDuration, buffer: Buffer, area: Rect): Unit =
        if elapsed >= pause then effect.process(elapsed - pause, buffer, area)
        else effect.process(Duration.Zero, buffer, area)

  /** Repeats `effect` `times` times. */
  def repeat(effect: Effect, times: Int): Effect =
    new Effect:
      def duration: Duration                                                 = effect.duration * times.toDouble
      def process(elapsed: FiniteDuration, buffer: Buffer, area: Rect): Unit =
        effect.duration match
          case finite: FiniteDuration if finite.toNanos > 0 =>
            val within = (elapsed.toNanos % finite.toNanos).nanos
            val cycle  = elapsed.toNanos / finite.toNanos
            if cycle < times then effect.process(within, buffer, area)
          case _                                            => effect.process(elapsed, buffer, area)

  // ---- shared machinery ----

  private abstract class TimedEffect(val totalDuration: FiniteDuration, easing: Easing = Easing.Linear) extends Effect:
    def duration: Duration                                                       = totalDuration
    def transform(progress: Double, buffer: Buffer, area: Rect): Unit
    final def process(elapsed: FiniteDuration, buffer: Buffer, area: Rect): Unit =
      val t = if totalDuration.toNanos == 0 then 1.0 else elapsed.toNanos.toDouble / totalDuration.toNanos
      transform(easing(t), buffer, area)

  private def fade(totalDuration: FiniteDuration, easing: Easing, reverse: Boolean): Effect =
    new TimedEffect(totalDuration, easing):
      def transform(progress: Double, buffer: Buffer, area: Rect): Unit =
        val level = if reverse then 1 - progress else progress
        if level < 1.0 then mapCells(buffer, area)(style => withFgScaled(style, level))

  private def scatter(totalDuration: FiniteDuration, easing: Easing, seed: Int, revealed: Boolean): Effect =
    new TimedEffect(totalDuration, easing):
      def transform(progress: Double, buffer: Buffer, area: Rect): Unit =
        val threshold = if revealed then progress else 1 - progress
        eraseWhere(buffer, area)((x, y) => cellNoise(x, y, seed) >= threshold)

  /** Deterministic per-cell noise in `[0, 1)` — a small integer hash, stable across frames. */
  private def cellNoise(x: Int, y: Int, seed: Int): Double =
    var h = x * 374761393 + y * 668265263 + seed * 987654323
    h = (h ^ (h >>> 13)) * 1274126177
    ((h ^ (h >>> 16)) & 0x7fffffff).toDouble / Int.MaxValue

  private def eraseWhere(buffer: Buffer, area: Rect)(hide: (Int, Int) => Boolean): Unit =
    var y = area.y
    while y < area.bottom do
      var x = area.x
      while x < area.right do
        if hide(x, y) then buffer.set(x, y, Cell.Empty)
        x += 1
      y += 1

  private def mapCells(buffer: Buffer, area: Rect)(transform: Style => Style): Unit =
    var y = area.y
    while y < area.bottom do
      var x = area.x
      while x < area.right do
        val cell = buffer.get(x, y)
        if !cell.isBlank then buffer.set(x, y, cell.copy(style = transform(cell.style)))
        x += 1
      y += 1

  private def withFgScaled(style: Style, level: Double): Style =
    val (r, g, b) = approximateRgb(style.fg.getOrElse(Color.White))
    style.withFg(
      Color.Rgb(
        math.round(r * level).toInt,
        math.round(g * level).toInt,
        math.round(b * level).toInt,
      )
    )

  /** RGB approximation, delegated to the shared core table. */
  private[runtime] def approximateRgb(color: Color): (Int, Int, Int) =
    Color.approximateRgb(color)
