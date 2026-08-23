package io.worxbend.tui.examples.dashboard

import io.worxbend.tui.dsl.*

import scala.concurrent.duration.DurationInt

/** dashboard: `Gauge` + `Sparkline` + `Chart` under tick-rate animation — layout composition and the tick/redraw cycle.
  * `q` quits.
  */
class DashboardApp extends TuiApp:

  override def config: RunnerConfig = RunnerConfig(tickRate = Some(100.millis))

  val tick: Signal[Int] = Signal(0)

  override def onTick(): Unit = tick.update(_ + 1)

  def view(using ReactiveScope, Theme): Element =
    val t       = tick.get
    val load    = (math.sin(t * 0.1) + 1) / 2
    val samples = Vector.tabulate(60)(i => (math.sin((t + i) * 0.25) * 40 + 50).toLong)
    val wave    = Vector.tabulate(120)(i => (i * 0.5, math.sin((t * 0.5 + i) * 0.1) * 40 + 50))
    column(
      row(
        panel("Load")(gauge(load)).percent(50),
        panel("Throughput")(sparkline(samples).fill).percent(50),
      ).length(4),
      // `titleBottom` writes into the bottom border, so the reading costs no content row
      panel("Signal")(
        chart(
          Seq(Dataset("wave", wave, graphType = GraphType.Line)),
          xBounds = (0.0, 60.0),
          yBounds = (0.0, 100.0),
        ).fill
      ).titleBottom(f"load ${load * 100}%.0f%%").fill,
      // one row, two styles, and no hand-counted column widths
      line(s"tick $t · press ".styled(_.dim), "q".styled(_.bold), " to quit".styled(_.dim)),
    ).onKeyEvent {
      case KeyEvent(KeyCode.Char('q'), _) =>
        quit()
        true
      case _                              => false
    }

object Main extends DashboardApp
