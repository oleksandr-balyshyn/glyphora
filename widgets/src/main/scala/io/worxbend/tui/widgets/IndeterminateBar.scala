package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Cell, Rect, Style, Widget}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** How an [[IndeterminateBar]]'s segment travels. Indeterminate bars all say "working, no idea how long", but they say
  * it at different volumes — pick by how much of the user's attention the task deserves.
  */
enum IndeterminateMotion:

  /** A segment sliding to the far edge and back. The classic, and the most eye-catching. */
  case Bounce

  /** A segment sliding off one edge and reappearing at the other — calmer than [[Bounce]], and it never pauses at a
    * turning point, so it reads as steady rather than as struggling.
    */
  case Sweep

  /** A bright head with a tail fading out behind it, sweeping in one direction. */
  case Comet

  /** The whole track brightening and dimming in place. The quietest option: no motion to track across the row, so it
    * sits in a dense dashboard without pulling the eye.
    */
  case Pulse

/** An indeterminate progress bar: a segment travelling along a track, advanced by `phase`.
  *
  * The glyphs come from a [[ProgressStyle]] so a determinate and an indeterminate bar in the same view can be drawn
  * from the same vocabulary, and the travel comes from an [[IndeterminateMotion]].
  */
final case class IndeterminateBar(
    elapsed: FiniteDuration,
    style: Style = Style.Default.dim,
    segmentStyle: Style = Style.Default,
    progressStyle: ProgressStyle = ProgressStyle.Line,
    motion: IndeterminateMotion = IndeterminateMotion.Bounce,
    segmentWidth: Option[Int] = None,
    period: FiniteDuration = 1600.millis,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val segment = math.max(1, math.min(area.width, segmentWidth.getOrElse(math.max(2, area.width / 4))))
      motion match
        case IndeterminateMotion.Bounce => renderTravelling(area, buffer, segment, bounceStart(area.width, segment))
        case IndeterminateMotion.Sweep  => renderTravelling(area, buffer, segment, sweepStart(area.width, segment))
        case IndeterminateMotion.Comet  => renderComet(area, buffer, segment)
        case IndeterminateMotion.Pulse  => renderPulse(area, buffer)

  /** A segment sliding to the far edge and turning back; one `period` covers the round trip. */
  private def bounceStart(width: Int, segment: Int): Int =
    val travel = math.max(1, width - segment)
    val cycle  = Animation.step(elapsed, period, 2 * travel)
    if cycle < travel then cycle else 2 * travel - cycle

  /** A segment sliding off one edge and reappearing at the other; the start may be negative, which the wrap handles. */
  private def sweepStart(width: Int, segment: Int): Int =
    Animation.step(elapsed, period, width + segment) - segment

  private def renderTravelling(area: Rect, buffer: Buffer, segment: Int, start: Int): Unit =
    var x = area.x
    while x < area.right do
      val offset    = x - area.x
      val inSegment = offset >= start && offset < start + segment
      buffer.set(x, area.y, cellFor(inSegment, segmentStyle))
      x += 1

  /** A bright head with a tail behind it: the tail dims by stepping down the style's partial glyphs, or by falling back
    * to the track glyph where the vocabulary has none.
    */
  private def renderComet(area: Rect, buffer: Buffer, segment: Int): Unit =
    val head = Animation.step(elapsed, period, area.width + segment)
    var x    = area.x
    while x < area.right do
      val distance = head - (x - area.x)
      if distance >= 0 && distance < segment then
        val glyph =
          if distance == 0 then progressStyle.fill
          else if progressStyle.isSubCell then
            val step = progressStyle.partials.size - 1 - (distance - 1) * progressStyle.partials.size / segment
            progressStyle.partials(math.max(0, math.min(progressStyle.partials.size - 1, step)))
          else progressStyle.fill
        buffer.set(x, area.y, Cell(glyph, if distance == 0 then segmentStyle else style))
      else buffer.set(x, area.y, Cell(progressStyle.track, style))
      x += 1

  /** The whole track brightening and dimming in place. Lit for the middle half of each period, so it reads as a breath
    * rather than a strobe.
    */
  private def renderPulse(area: Rect, buffer: Buffer): Unit =
    val steps = 4
    val cycle = Animation.step(elapsed, period, steps)
    val lit   = cycle == 1 || cycle == 2
    var x     = area.x
    while x < area.right do
      buffer.set(x, area.y, cellFor(lit, segmentStyle))
      x += 1

  private def cellFor(active: Boolean, activeStyle: Style): Cell =
    if active then Cell(progressStyle.fill, activeStyle) else Cell(progressStyle.track, style)
