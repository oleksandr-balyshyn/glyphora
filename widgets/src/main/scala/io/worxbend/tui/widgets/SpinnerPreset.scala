package io.worxbend.tui.widgets

import io.worxbend.tui.core.{CharWidth, GlyphSupport}

import scala.concurrent.duration.{Duration, DurationDouble, DurationInt, FiniteDuration}

/** One named spinner animation: the frames to cycle through and how long each frame holds.
  *
  * Frames are reported padded to [[width]], the widest frame's display width, so a spinner whose frames differ in width
  * does not shove its label back and forth every tick. That is the whole reason this is a type rather than a bare
  * `Seq[String]`: the padding needs the frame set to compute, and a caller passing raw frames cannot do it.
  *
  * `frameDuration` is wall-clock, not a tick count, and that is deliberate. A preset cannot see the app's tick rate, so
  * a speed expressed in ticks means the same preset reads as sluggish under `tickRate = 200.millis` and frantic under
  * `50.millis`. Holding each frame for a duration makes an animation look the same in every app that shows it, and lets
  * a slow-ticking app still get a smooth spinner by ticking faster only while one is on screen.
  */
final case class SpinnerPreset(
    name: String,
    frames: Vector[String],
    frameDuration: FiniteDuration = 80.millis,
):
  require(frames.nonEmpty, s"spinner preset '$name' has no frames")
  require(frameDuration > Duration.Zero, s"spinner preset '$name' has a frame duration of $frameDuration")

  /** The display width every frame is padded to — the widest frame's. */
  val width: Int = frames.map(CharWidth.of).max

  /** The frame to show `elapsed` into the animation, right-padded to [[width]].
    *
    * Negative elapsed times wrap rather than throw: a clock that ran backwards (a caller subtracting timestamps) should
    * show the wrong frame, not take the app down.
    */
  def frameAt(elapsed: FiniteDuration): String = padded(frameIndexAt(elapsed))

  /** Which frame index is showing `elapsed` into the animation. Split out of [[frameAt]] for the callers that need the
    * *position* and not the glyph — a [[SpinnerGrid]] colouring a slot by how far through the cycle it is cannot get
    * that back out of a padded string.
    */
  def frameIndexAt(elapsed: FiniteDuration): Int =
    Animation.step(elapsed, cycleDuration, frames.size)

  /** How long one full cycle takes — useful for syncing a caption or a second animation to it. */
  def cycleDuration: FiniteDuration = frameDuration * frames.size.toLong

  /** The same animation running the other way. */
  def reversed: SpinnerPreset = copy(name = s"$name-reversed", frames = frames.reverse)

  /** The same animation running `factor` times slower. */
  def slowedBy(factor: Double): SpinnerPreset =
    require(factor > 0, s"slowedBy needs a positive factor, got $factor")
    copy(frameDuration = (frameDuration.toNanos * factor).nanos)

  /** The same animation at an explicit frame interval. */
  def withFrameDuration(duration: FiniteDuration): SpinnerPreset = copy(frameDuration = duration)

  /** The same animation at an explicit rate, for callers who think in frames per second. */
  def atFps(fps: Double): SpinnerPreset =
    require(fps > 0, s"atFps needs a positive rate, got $fps")
    copy(frameDuration = (1000.0 / fps).millis)

  /** This animation, or a plain ASCII one when `support` does not reach what its frames need.
    *
    * A preset whose frames are already ASCII is returned untouched, whatever the rung — that is checked rather than
    * looked up in a list, so a preset added later cannot quietly escape the check. Everything else falls back to
    * [[SpinnerPreset.Line]], the `|/-\` spinner, which is the animation every terminal ever built can draw.
    *
    * Only [[io.worxbend.tui.core.GlyphSupport.Full]] keeps a non-ASCII preset: the braille, block and emoji catalogues
    * are all above the box-drawing rung, so there is nothing for `BoxDrawing` to keep that `Ascii` would not also have
    * to drop.
    */
  def degraded(support: GlyphSupport): SpinnerPreset =
    if support == GlyphSupport.Full || GlyphFloor.allAscii(frames) then this else SpinnerPreset.Line

  private def padded(index: Int): String =
    val frame   = frames(index)
    val padding = width - CharWidth.of(frame)
    if padding <= 0 then frame else frame + " ".repeat(padding)

/** The built-in spinner animations.
  *
  * Grouped by the glyph repertoire they need, because that is what decides whether a preset is safe on a given
  * terminal: [[AsciiPresets]] need nothing, [[BraillePresets]] and [[BlockPresets]] need a font with the Unicode
  * blocks, and [[EmojiPresets]] need color emoji and take two columns per frame.
  */
object SpinnerPreset:

  // ---------------------------------------------------------------------- braille

  /** The classic ten-frame braille spinner; the default. */
  val Dots: SpinnerPreset = SpinnerPreset("dots", Vector("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"))

  /** A single dot orbiting the braille cell — quieter than [[Dots]]. */
  val DotsOrbit: SpinnerPreset = SpinnerPreset("dots-orbit", Vector("⠁", "⠂", "⠄", "⡀", "⢀", "⠠", "⠐", "⠈"))

  /** Alternating halves of the cell, a heavier pulse. */
  val DotsPulse: SpinnerPreset = SpinnerPreset("dots-pulse", Vector("⢹", "⢺", "⢼", "⣸", "⣇", "⡧", "⡗", "⡏"))

  /** A filled cell with one dot missing, rotating. */
  val DotsRing: SpinnerPreset = SpinnerPreset("dots-ring", Vector("⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷"))

  /** A wave rolling across two braille cells. */
  val DotsWave: SpinnerPreset =
    SpinnerPreset("dots-wave", Vector("⠁⠀", "⠂⠀", "⠄⠀", "⡀⠀", "⢀⠀", "⠠⠀", "⠐⠀", "⠈⠀", "⠀⠁", "⠀⠂", "⠀⠄", "⠀⡀"))

  // ---------------------------------------------------------------------- ascii

  /** The universal `|/-\` spinner. Safe on any terminal, in any font. */
  val Line: SpinnerPreset = SpinnerPreset("line", Vector("|", "/", "-", "\\"))

  /** A growing then shrinking run of dots — the plain "working…" caption. */
  val Ellipsis: SpinnerPreset =
    SpinnerPreset("ellipsis", Vector("   ", ".  ", ".. ", "..."), frameDuration = 250.millis)

  /** A bar bouncing inside brackets. ASCII only. */
  val BouncingBar: SpinnerPreset = SpinnerPreset(
    "bouncing-bar",
    Vector("[=   ]", "[==  ]", "[ == ]", "[  ==]", "[   =]", "[  ==]", "[ == ]", "[==  ]"),
    frameDuration = 120.millis,
  )

  /** A ball bouncing inside parentheses. */
  val BouncingBall: SpinnerPreset = SpinnerPreset(
    "bouncing-ball",
    Vector("( ●   )", "(  ●  )", "(   ● )", "(    ●)", "(   ● )", "(  ●  )", "( ●   )", "(●    )"),
    frameDuration = 120.millis,
  )

  /** Layers stacking up and collapsing. */
  val Layers: SpinnerPreset = SpinnerPreset("layers", Vector("-", "=", "≡", "=", "-"), frameDuration = 180.millis)

  // ---------------------------------------------------------------------- blocks and geometry

  /** A bar growing from the floor and sinking back. */
  val GrowVertical: SpinnerPreset =
    SpinnerPreset("grow-vertical", Vector("▁", "▃", "▄", "▅", "▆", "▇", "▆", "▅", "▄", "▃"), frameDuration = 120.millis)

  /** A bar growing from the left edge of the cell and shrinking back. */
  val GrowHorizontal: SpinnerPreset = SpinnerPreset(
    "grow-horizontal",
    Vector("▏", "▎", "▍", "▌", "▋", "▊", "▉", "▊", "▋", "▌", "▍", "▎"),
    frameDuration = 120.millis,
  )

  /** A quadrant hopping round the corners of the cell. */
  val Quadrant: SpinnerPreset = SpinnerPreset("quadrant", Vector("▖", "▘", "▝", "▗"))

  /** A filled quarter sweeping round a square. */
  val Square: SpinnerPreset = SpinnerPreset("square", Vector("◰", "◳", "◲", "◱"), frameDuration = 120.millis)

  /** A filled half rotating — the smoothest of the geometric set. */
  val CircleHalves: SpinnerPreset =
    SpinnerPreset("circle-halves", Vector("◐", "◓", "◑", "◒"), frameDuration = 120.millis)

  /** A quarter sweeping round a circle. */
  val CircleQuarters: SpinnerPreset =
    SpinnerPreset("circle-quarters", Vector("◴", "◷", "◶", "◵"), frameDuration = 120.millis)

  /** A light arc rotating; the quietest option that still reads as motion. */
  val Arc: SpinnerPreset = SpinnerPreset("arc", Vector("◜", "◠", "◝", "◞", "◡", "◟"))

  /** A filled triangle rotating through the corners. */
  val Triangle: SpinnerPreset = SpinnerPreset("triangle", Vector("◢", "◣", "◤", "◥"), frameDuration = 120.millis)

  /** Box-drawing spokes rotating, like a turning wheel. */
  val Pipe: SpinnerPreset = SpinnerPreset("pipe", Vector("┤", "┘", "┴", "└", "├", "┌", "┬", "┐"))

  /** A four-pointed star pulsing. */
  val Star: SpinnerPreset = SpinnerPreset("star", Vector("✶", "✸", "✹", "✺", "✹", "✷"), frameDuration = 120.millis)

  /** Two glyphs flipping — a minimal "still working" tick. */
  val Toggle: SpinnerPreset = SpinnerPreset("toggle", Vector("⊶", "⊷"), frameDuration = 320.millis)

  /** A dot travelling along three positions. */
  val Points: SpinnerPreset = SpinnerPreset("points", Vector("∙∙∙", "●∙∙", "∙●∙", "∙∙●"), frameDuration = 180.millis)

  /** An arrow rotating through the eight compass points. */
  val Arrow: SpinnerPreset =
    SpinnerPreset("arrow", Vector("←", "↖", "↑", "↗", "→", "↘", "↓", "↙"), frameDuration = 120.millis)

  /** A bubble swelling and popping. */
  val Balloon: SpinnerPreset =
    SpinnerPreset("balloon", Vector(".", "o", "O", "°", "O", "o", "."), frameDuration = 180.millis)

  // ---------------------------------------------------------------------- emoji (two columns per frame)

  /** The lunar cycle. Needs color emoji; every frame is two columns wide. */
  val Moon: SpinnerPreset =
    SpinnerPreset("moon", Vector("🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘"), frameDuration = 180.millis)

  /** A spinning globe. Needs color emoji; every frame is two columns wide. */
  val Earth: SpinnerPreset = SpinnerPreset("earth", Vector("🌍", "🌎", "🌏"), frameDuration = 250.millis)

  /** A clock face running round twelve hours. Needs color emoji; every frame is two columns wide. */
  val Clock: SpinnerPreset = SpinnerPreset(
    "clock",
    Vector("🕛", "🕐", "🕑", "🕒", "🕓", "🕔", "🕕", "🕖", "🕗", "🕘", "🕙", "🕚"),
    frameDuration = 120.millis,
  )

  /** Coloured hearts cycling. Needs color emoji; every frame is two columns wide. */
  val Hearts: SpinnerPreset =
    SpinnerPreset("hearts", Vector("💛", "💙", "💜", "💚", "❤️"), frameDuration = 180.millis)

  // ------------------------------------------------------------------- flux: braille

  /** A cell filling from the floor and draining back. The braille density ladder itself, which is why a
    * [[LinearSpinner]] at braille resolution shades its track with exactly these glyphs — the two animations agree by
    * construction rather than by coincidence.
    */
  val DotsFill: SpinnerPreset =
    SpinnerPreset("dots-fill", Vector("⣀", "⣤", "⣶", "⣾", "⣿", "⣾", "⣶", "⣤"))

  /** A line of dots dropping through the cell and springing back. Reads as weight rather than as rotation, which makes
    * it the one braille preset that still says "working" at a glance in a dense row of spinners.
    */
  val DotsBounce: SpinnerPreset =
    SpinnerPreset("dots-bounce", Vector("⠉", "⠒", "⣀", "⠒"), frameDuration = 120.millis)

  /** Two dots orbiting opposite each other. Twice the apparent motion of [[DotsOrbit]] at the same frame rate, and the
    * symmetry keeps it from reading as a single dot that stutters.
    */
  val DotsPair: SpinnerPreset =
    SpinnerPreset("dots-pair", Vector("⠉", "⠘", "⠰", "⢠", "⣀", "⡄", "⠆", "⠃"))

  // ------------------------------------------------------------------- flux: blocks and geometry

  /** A bar rotating through four positions, drawn with box and geometric glyphs rather than ASCII. The same animation
    * as [[Line]] but continuous at the corners, so it does not flicker between stroke weights the way `|/-\` does in
    * most fonts.
    */
  val Rotor: SpinnerPreset =
    SpinnerPreset("rotor", Vector("│", "╱", "─", "╲"), frameDuration = 120.millis)

  /** A solid triangle pointing round the compass. Louder than [[Arrow]] — a filled shape rather than a stroke — so it
    * suits a single prominent spinner and not a column of them.
    */
  val Pointer: SpinnerPreset =
    SpinnerPreset("pointer", Vector("▲", "▶", "▼", "◀"), frameDuration = 120.millis)

  /** Half the cell filled, rotating. The heaviest of the four-frame set and the most legible at small font sizes,
    * because every frame covers half the cell rather than a quarter.
    */
  val Halves: SpinnerPreset =
    SpinnerPreset("halves", Vector("▀", "▐", "▄", "▌"), frameDuration = 120.millis)

  /** A corner hopping round the cell. The quietest box-drawing option: it sits flush against chrome drawn from the same
    * block, so a spinner inside a bordered pane looks like part of the border rather than an intruder on it.
    */
  val Corners: SpinnerPreset =
    SpinnerPreset("corners", Vector("┌", "┐", "┘", "└"), frameDuration = 120.millis)

  /** A circle filling in quarters and snapping back — the pie-loading idiom. The snap is deliberate: it reads as "one
    * unit of work finished, starting the next", which is the honest thing to say when there is no percentage to show.
    */
  val Pie: SpinnerPreset =
    SpinnerPreset("pie", Vector("○", "◔", "◑", "◕", "●"), frameDuration = 140.millis)

  /** A diamond filling and emptying. Only two distinct glyph states, so it is held longer per frame than the rest of
    * the geometric set — at the usual rate two states read as a strobe rather than as a pulse.
    */
  val Diamond: SpinnerPreset =
    SpinnerPreset("diamond", Vector("◇", "◈", "◆", "◈"), frameDuration = 160.millis)

  // ---------------------------------------------------------------------- catalogues

  /** Presets that need nothing beyond ASCII — the safe choice for unknown terminals and CI logs. Pinned by a test:
    * every frame here is genuinely ASCII, so this catalogue can be trusted rather than spot-checked.
    */
  val AsciiPresets: Vector[SpinnerPreset] = Vector(Line, Ellipsis, BouncingBar)

  /** Presets drawn from the Unicode braille block. */
  val BraillePresets: Vector[SpinnerPreset] =
    Vector(Dots, DotsOrbit, DotsPulse, DotsRing, DotsWave, DotsFill, DotsBounce, DotsPair)

  /** Presets drawn from the block, box-drawing and geometric-shape blocks. */
  val BlockPresets: Vector[SpinnerPreset] =
    Vector(
      GrowVertical,
      GrowHorizontal,
      Quadrant,
      Square,
      CircleHalves,
      CircleQuarters,
      Arc,
      Triangle,
      Pipe,
      Star,
      Toggle,
      Points,
      Arrow,
      BouncingBall,
      Layers,
      Balloon,
    )

  /** Presets needing color emoji. Every frame is two columns wide, so they shift a following label by two. */
  val EmojiPresets: Vector[SpinnerPreset] = Vector(Moon, Earth, Clock, Hearts)

  /** Every built-in preset, in catalogue order. */
  val All: Vector[SpinnerPreset] = AsciiPresets ++ BraillePresets ++ BlockPresets ++ EmojiPresets

  /** Looks a preset up by its [[SpinnerPreset.name]] — for a config file or a `--spinner` flag. */
  def byName(name: String): Option[SpinnerPreset] = byNameIndex.get(name)

  private val byNameIndex: Map[String, SpinnerPreset] = All.map(preset => preset.name -> preset).toMap
