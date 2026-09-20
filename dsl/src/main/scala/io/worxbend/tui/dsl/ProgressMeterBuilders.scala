package io.worxbend.tui.dsl

import io.worxbend.tui.core.Color
import io.worxbend.tui.widgets as w

/** The caption and fill-ramp builders shared by the one-row progress meters, [[GaugeElement]] and
  * [[ProgressBarElement]].
  *
  * A view reaches for whichever meter fits and gets the same vocabulary either way: the same caption trio and the same
  * two ramps, so the two are captioned and colored identically whichever one a view reaches for. They were once the
  * same five method bodies written out on both elements — a pair that had already drifted in its wording and would have
  * drifted in its behavior next.
  *
  * The `Self` type is F-bounded so every builder answers the element's own type and a mixed chain —
  * `progressBar(0.5).bare.preset(...)` — keeps the element-specific builders reachable after a shared one. An
  * implementer supplies three hooks: the caption and ramp it currently carries, and a rebuild with both replaced.
  */
trait ProgressMeterBuilders[Self <: ProgressMeterBuilders[Self]]:

  /** The caption the meter currently carries. */
  protected def progressLabel: w.ProgressLabel

  /** The ramp the fill is currently colored by, or `None` for the flat fill style. */
  protected def progressRamp: Option[w.ColorRamp]

  /** This meter rebuilt with the caption and the fill ramp replaced together. */
  protected def withProgress(label: w.ProgressLabel, ramp: Option[w.ColorRamp]): Self

  /** Replaces the percentage caption with fixed text. */
  def label(text: String): Self = withProgress(w.ProgressLabel.Text(text), progressRamp)

  /** Shows fixed text followed by the percentage, as in `syncing 42%`. */
  def labelled(text: String): Self = withProgress(w.ProgressLabel.TextAndPercentage(text), progressRamp)

  /** Drops the caption entirely, leaving the bar uninterrupted. */
  def bare: Self = withProgress(w.ProgressLabel.Hidden, progressRamp)

  /** Colors the fill by how far along it is — see [[io.worxbend.tui.widgets.ColorRamp]] for the built-in ramps.
    * `ColorRamp.Traffic` walks green through amber to red, which is what an "how bad is it" meter (disk usage, an
    * air-quality index) wants and a download bar does not.
    */
  def ramp(chosen: w.ColorRamp): Self = withProgress(progressLabel, Some(chosen))

  /** A two-color ramp built inline, for the many cases with no named preset. */
  def ramp(from: Color, to: Color): Self = withProgress(progressLabel, Some(w.ColorRamp(from, to)))
