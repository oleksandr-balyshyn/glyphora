package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Direction, Layout, Constraint, Rect, Style, Widget}

/** Two sparklines sharing one area (top/bottom halves) for visually comparing a pair of series — e.g.
  * ingress vs. egress. Each series scales independently unless a shared `max` is given.
  */
final case class DualSparkline(
    upper: Seq[Long],
    lower: Seq[Long],
    max: Option[Long] = None,
    upperStyle: Style = Style.Default,
    lowerStyle: Style = Style.Default.dim,
) extends Widget:

  def render(area: Rect, buffer: Buffer): Unit =
    if !area.isEmpty then
      val halves = Layout(Direction.Vertical, Seq(Constraint.Ratio(1, 2), Constraint.Fill(1))).split(area)
      Sparkline(upper, max, upperStyle).render(halves(0), buffer)
      Sparkline(lower, max, lowerStyle).render(halves(1), buffer)
