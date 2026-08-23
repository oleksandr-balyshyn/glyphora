package io.worxbend.tui.core

import scala.concurrent.duration.{Duration, DurationInt, DurationLong, FiniteDuration}

/** A post-render frame transform (the tachyonfx model, original implementation): widgets render normally, then active
  * effects mutate the rendered cells based on elapsed time.
  *
  * An effect holds no clock of its own — it is a pure function of the `elapsed` it is handed, which is what keeps the
  * combinators ([[Effect.sequence]], [[Effect.repeat]], …) composable and replayable. The flip side is that **the
  * caller owns the clock**: whoever calls [[process]], directly or through `Frame.applyEffect` in `tui-runtime`, must
  * remember when this particular effect started and pass the time since then. `TuiApp.runEffect` in `tui-dsl` keeps
  * that timestamp per active effect, so a DSL app gets it for free; an app driving a bare `Runner` has to keep it
  * itself. Passing time since the frame, or since the application started, compiles and type-checks but leaves the
  * effect pinned at progress 1.0 or oscillating out of phase.
  */
trait Effect:

  /** Transforms the rendered frame in place. `elapsed` is measured from this effect's own start — see the class doc. */
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
    fade(duration, easing, level = identity)

  /** Content fades away to black. */
  def fadeOut(duration: FiniteDuration, easing: Easing = Easing.QuadIn): Effect =
    fade(duration, easing, level = 1 - _)

  /** Cells materialize in a seeded pseudo-random order (tachyonfx's `coalesce`). */
  def coalesce(duration: FiniteDuration, easing: Easing = Easing.QuadOut, seed: Int = 42): Effect =
    scatter(duration, easing, seed, threshold = identity)

  /** Cells dissolve away in a seeded pseudo-random order. */
  def dissolve(duration: FiniteDuration, easing: Easing = Easing.QuadIn, seed: Int = 42): Effect =
    scatter(duration, easing, seed, threshold = 1 - _)

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

  /** Endless brightness oscillation with the given period, which is clamped to at least one millisecond. */
  def pulse(period: FiniteDuration = 1.second): Effect =
    new Effect:
      // the phase is computed at millisecond resolution, so a sub-millisecond period would divide by zero and unwind
      // the render loop; one millisecond is the shortest cycle that resolution can express (as in `Async.every`)
      private val periodMillis                                               = math.max(1L, period.toMillis)
      def duration: Duration                                                 = Duration.Inf
      def process(elapsed: FiniteDuration, buffer: Buffer, area: Rect): Unit =
        val phase      = (elapsed.toMillis % periodMillis).toDouble / periodMillis
        val brightness = PulseMidBrightness + PulseBrightnessSwing * math.sin(phase * 2 * math.Pi)
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

  /** Repeats `effect` `times` times; `times <= 0` plays it not at all. */
  def repeat(effect: Effect, times: Int): Effect =
    new Effect:
      // "not at all" is a zero-length effect, not an undefined one: `Duration.Inf * 0` is `Duration.Undefined`, which
      // `isDone` can never satisfy, so the runtime would keep the effect (and its per-tick redraws) alive forever
      def duration: Duration                                                 =
        if times <= 0 then Duration.Zero else effect.duration * times.toDouble
      def process(elapsed: FiniteDuration, buffer: Buffer, area: Rect): Unit =
        if times > 0 then
          effect.duration match
            case finite: FiniteDuration if finite.toNanos > 0 =>
              val within = (elapsed.toNanos % finite.toNanos).nanos
              val cycle  = elapsed.toNanos / finite.toNanos
              if cycle < times then effect.process(within, buffer, area)
            case _                                            => effect.process(elapsed, buffer, area)

  // ---- shared machinery ----

  // `pulse` oscillates each cell's brightness between 10% and 100% of its colour: the midpoint is where the sine sits
  // at phase zero, the swing is how far either side of it the sine reaches. Kept as the midpoint/swing pair the sine
  // consumes rather than as a min/max pair, so the numbers the curve uses are the numbers written here.
  private val PulseMidBrightness   = 0.55
  private val PulseBrightnessSwing = 0.45

  private abstract class TimedEffect(val totalDuration: FiniteDuration, easing: Easing = Easing.Linear) extends Effect:
    def duration: Duration                                                       = totalDuration
    def transform(progress: Double, buffer: Buffer, area: Rect): Unit
    final def process(elapsed: FiniteDuration, buffer: Buffer, area: Rect): Unit =
      transform(easing(Progress.normalized(elapsed, totalDuration)), buffer, area)

  /** Brightness ramp shared by [[fadeIn]] and [[fadeOut]]. `level` turns eased progress into the fraction of each
    * cell's colour that survives — `identity` fades content up, `1 - _` fades it down to black.
    */
  private def fade(totalDuration: FiniteDuration, easing: Easing, level: Double => Double): Effect =
    new TimedEffect(totalDuration, easing):
      def transform(progress: Double, buffer: Buffer, area: Rect): Unit =
        val scale = level(progress)
        if scale < 1.0 then mapCells(buffer, area)(style => withFgScaled(style, scale))

  /** Seeded per-cell reveal shared by [[coalesce]] and [[dissolve]]. `threshold` turns eased progress into the noise
    * cutoff a cell must beat to stay hidden — `identity` reveals cells as progress rises, `1 - _` hides them.
    */
  private def scatter(totalDuration: FiniteDuration, easing: Easing, seed: Int, threshold: Double => Double): Effect =
    new TimedEffect(totalDuration, easing):
      def transform(progress: Double, buffer: Buffer, area: Rect): Unit =
        val cutoff = threshold(progress)
        eraseWhere(buffer, area)((x, y) => cellNoise(x, y, seed) >= cutoff)

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

  /** Replaces every non-blank cell's style in `area` with `transform` of it.
    *
    * `transform` must be a pure function of the style: consecutive cells almost always share one, so the last input and
    * its result are remembered and re-derived only when the style actually changes. Without that, a whole-frame fade
    * built a fresh `Color` and `Style` for each of a frame's ten thousand cells to arrive at the same answer.
    */
  private def mapCells(buffer: Buffer, area: Rect)(transform: Style => Style): Unit =
    var lastIn: Style  = Style.Default
    var lastOut: Style = transform(Style.Default)
    var y              = area.y
    while y < area.bottom do
      var x = area.x
      while x < area.right do
        val cell = buffer.get(x, y)
        if !cell.isBlank then
          if !((cell.style eq lastIn) || cell.style == lastIn) then
            lastIn = cell.style
            lastOut = transform(cell.style)
          buffer.set(x, y, cell.copy(style = lastOut))
        x += 1
      y += 1

  /** Scales a style's foreground by `level`.
    *
    * `Color.rgb` rather than the bare `Rgb` constructor because the overshoot easings (`Back*`, `Elastic*`) dip below
    * zero mid-flight, and an unclamped negative channel would reach the terminal as a literal `38;2;-7;-7;-7`.
    */
  private def withFgScaled(style: Style, level: Double): Style =
    val (r, g, b) = Color.approximateRgb(style.fg.getOrElse(Color.White))
    style.withFg(
      Color.rgb(
        math.round(r * level).toInt,
        math.round(g * level).toInt,
        math.round(b * level).toInt,
      )
    )
