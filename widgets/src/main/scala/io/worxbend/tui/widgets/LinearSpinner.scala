package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Rect, Style, Widget}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** A head travelling a one-cell-thick track at sub-cell resolution, drawn from how long the animation has been running.
  *
  * The row-or-column-shaped member of the animation family: where [[Spinner]] says "working" inside one glyph and
  * [[OrbitSpinner]] says it in a block, this says it along a line — a status row under a log pane, or a column beside
  * one. Like every animation here the frame is a pure function of `elapsed`, so a test renders any moment directly and
  * an app with no `tickRate` still shows a legible first frame.
  *
  * The three knobs are deliberately orthogonal, which is the difference from the obvious design: [[LinearPath]] is the
  * walk, [[LinearTrail]] is the shading, `trailSlots` is the length. A bouncing head with a comet tail and a wrapping
  * window of solid slots are the same widget with different arguments, rather than two modes in which half the options
  * are silently ignored.
  *
  * `trailSlots` counts sub-cell slots, not cells, because that is the resolution the motion happens at — at braille a
  * horizontal track has two slots per cell, so the default four slots is two cells of tail.
  *
  * `rail` is the resting brightness of the whole track on the same `0..1` scale as the trail. It is what turns an empty
  * row into a visible track, and the rule at the meeting point is chosen rather than accidental: a slot is drawn in
  * `headStyle` only where it is *brighter* than the rail, so a comet's tail dissolves into the track instead of ending
  * on a hard edge. `rail = 0.0` leaves the track empty and the head appears to float.
  */
final case class LinearSpinner(
    elapsed: FiniteDuration,
    style: Style = Style.Default.dim,
    headStyle: Style = Style.Default,
    axis: LinearAxis = LinearAxis.Horizontal,
    path: LinearPath = LinearPath.Wrap,
    flow: LinearFlow = LinearFlow.Forward,
    trail: LinearTrail = LinearTrail.Comet,
    trailSlots: Int = 4,
    rail: Double = 0.25,
    resolution: CanvasResolution = CanvasResolution.Braille,
    marker: String = Marker.Circle,
    period: FiniteDuration = 1200.millis,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    val track = axis match
      case LinearAxis.Horizontal => area.copy(height = math.min(1, area.height))
      case LinearAxis.Vertical   => area.copy(width = math.min(1, area.width))
    if !track.isEmpty then
      val dots       = LinearDots(track, axis, resolution, marker)
      val brightness = LinearMotion.intensities(elapsed, period, dots.slots, path, flow, trail, trailSlots)
      val resting    = Fraction.clamped(rail)
      var index      = 0
      while index < brightness.length do
        brightness(index) = math.max(brightness(index), resting)
        index += 1
      dots.render(brightness, buffer, intensity => if intensity > resting then headStyle else style)

  /** One row for a horizontal track, one column for a vertical one — exact, not a bound, because the track is clipped
    * to a single cell before anything is drawn.
    */
  def preferredThickness: Int = 1
