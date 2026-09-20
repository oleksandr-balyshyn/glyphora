package io.worxbend.tui.examples.dashboard

import io.worxbend.tui.dsl.*

import java.util.Locale
import scala.concurrent.duration.DurationInt

/** dashboard: `Gauge` + `Sparkline` + `Chart` under tick-rate animation — layout composition and the tick/redraw cycle.
  * `q` quits.
  */
class DashboardApp extends TuiApp:

  override def config: RunnerConfig = RunnerConfig(tickRate = Some(100.millis))

  val tick: Signal[Int] = Signal(0)

  override def onTick(): Unit = tick.update(_ + 1)

  def view(using ReactiveScope, Theme): Element =
    import DashboardApp.*
    val t       = tick.get
    val load    = (math.sin(t * LoadSpeed) + 1) / 2
    val samples = Vector.tabulate(SparklineSamples)(i =>
      (math.sin((t + i) * SparklineFrequency) * WaveAmplitude + WaveMidline).toLong
    )
    val wave    = Vector.tabulate(WaveSamples)(i =>
      (i * WaveXStep, math.sin((t * WaveScrollSpeed + i) * WaveFrequency) * WaveAmplitude + WaveMidline)
    )
    column(
      row(
        panel("Load")(gauge(load)).percent(50),
        panel("Throughput")(sparkline(samples).fill).percent(50),
      ).length(4),
      // `titleBottom` writes into the bottom border, so the reading costs no content row. It is formatted through
      // `String.format(Locale.ROOT, …)` rather than the `f` interpolator for the reason procmon's `decimal` spells
      // out: the interpolator uses the default FORMAT locale, which writes digits in its own script and separators.
      panel("Signal")(
        chart(
          Seq(Dataset("wave", wave, graphType = GraphType.Line)),
          xBounds = ChartXBounds,
          yBounds = ChartYBounds,
        ).fill
      ).titleBottom(String.format(Locale.ROOT, "load %.0f%%", load * 100)).fill,
      // one row, two styles, and no hand-counted column widths
      line(s"tick $t · press ".styled(_.dim), "q".styled(_.bold), " to quit".styled(_.dim)),
    ).onKeyEvent {
      case KeyEvent(KeyCode.Char('q'), _) =>
        quit()
        true
      case _                              => false
    }

private object DashboardApp:

  /** Radians of load-gauge phase per tick — how fast the gauge breathes. */
  private val LoadSpeed: Double = 0.1

  /** Samples behind the throughput sparkline. */
  private val SparklineSamples: Int = 60

  /** Points behind the signal chart's wave. */
  private val WaveSamples: Int = 120

  /** Horizontal distance between two neighbouring wave points. */
  private val WaveXStep: Double = 0.5

  /** Radians of wave phase per tick — how fast the wave scrolls. */
  private val WaveScrollSpeed: Double = 0.5

  /** Wave frequency, in radians per x-unit. */
  private val WaveFrequency: Double = 0.1

  /** Sparkline frequency, in radians per sample. */
  private val SparklineFrequency: Double = 0.25

  /** Half the wave's peak-to-peak height, shared by the sparkline and the chart. */
  private val WaveAmplitude: Double = 40.0

  /** The value both waves oscillate around. */
  private val WaveMidline: Double = 50.0

  /** Headroom kept between the wave's extremes and the chart's edges. */
  private val WaveMargin: Double = 10.0

  /** Derived from the point count and step so the wave fills the plot exactly. */
  private val ChartXBounds: Bounds = Bounds(0.0, WaveSamples * WaveXStep)

  /** Derived from the wave itself — midline ± amplitude, plus margin — so the line can never leave the plot. */
  private val ChartYBounds: Bounds =
    Bounds(WaveMidline - WaveAmplitude - WaveMargin, WaveMidline + WaveAmplitude + WaveMargin)

object Main extends DashboardApp
