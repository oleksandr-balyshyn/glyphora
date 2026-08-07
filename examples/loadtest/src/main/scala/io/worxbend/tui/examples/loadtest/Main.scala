package io.worxbend.tui.examples.loadtest

import io.worxbend.tui.dsl.*
import io.worxbend.tui.runtime.RunnerConfig

import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}

/** Where the app sits in the start -> run -> summarise cycle. */
enum Phase:
  case Idle
  case Running
  case Finished(outcome: RunOutcome)

/** loadtest: an HTTP load generator with a live TUI, modelled on `oha` — running counters, a throughput sparkline, a
  * latency histogram, a percentile table, a progress bar, and a summary screen when the run ends.
  *
  * What it teaches, and what none of the other examples cover: driving a *pool* of background threads from a TUI
  * without blocking or corrupting the render thread. Three rules make it work, and [[LoadRunner]] documents each at the
  * line that implements it.
  *
  *   - `Async.runCatching` is called from a key binding, i.e. while on the render thread, so the render loop it
  *     captures before submitting is this app's. That is the whole of the "capture before you go async" contract.
  *   - The entire concurrent phase is one `Async` task that owns a private fixed-size pool, rather than N tasks on
  *     `Async`'s unbounded shared pool — so concurrency stays bounded and one `Async` thread is consumed.
  *   - Results cross into the UI in batches, drained by [[onTick]] on the render thread. Nothing on the hot path
  *     touches a `Signal`.
  *
  * Keys: `s` start · `x` stop · `r` reset · `+` / `-` concurrency · `]` / `[` request count · `Enter` dismiss the
  * summary · `q` quit · `Ctrl+P` command palette.
  *
  * Command line: `--requests N`, `--concurrency C`, and `--url <URL>` to swap the built-in fake target for a real HTTP
  * GET. With no arguments it runs entirely offline against [[FakeTarget]].
  */
final class LoadTestApp(
    target: Target = FakeTarget(),
    initialPlan: Plan = Plan(requests = 500, concurrency = 8),
) extends TuiApp:

  /** The tick does two jobs: it drains completed requests onto the render thread, and it paces the redraw. Ten frames a
    * second is plenty for a chart and cheap enough that an idle app costs nothing (a tick that changes no signal
    * schedules no repaint).
    */
  override def config: RunnerConfig = RunnerConfig(tickRate = Some(LoadTestApp.TickRate))

  private val runner = LoadRunner(target)

  // Public because the spec asserts on them; everything else on this app is private.
  val plan: Signal[Plan]              = Signal(initialPlan)
  val phase: Signal[Phase]            = Signal(Phase.Idle)
  val stats: Signal[RunStats]         = Signal(RunStats.empty)
  val elapsed: Signal[FiniteDuration] = Signal(0.millis)

  def workersAlive: Int          = runner.workersAlive
  def workerThreadPrefix: String = runner.threadPrefix

  private val throughput: Signal[Vector[Long]] = Signal(Vector.empty)
  private val peakThroughput: Signal[Long]     = Signal(1L)
  private val summaryOpen: Signal[Boolean]     = Signal(false)
  private var startedNanos: Long               = 0L
  private var runId: Int                       = 0

  // Derived values live here, not in `view`. A `Computed` built inside `view` re-subscribes on every frame and is
  // never released; out here each one recomputes lazily, once per change to `stats`.
  private val latency: Computed[LatencySummary] =
    Computed(LatencySummary.of(stats.get.latencies))

  private val histogram: Computed[Vector[LatencyBucket]] =
    Computed(Histogram.of(stats.get.latencies, LoadTestApp.HistogramBuckets))

  override def bindings: KeyBindings = KeyBindings(
    binding("s", "start the run")(start()),
    binding("x", "stop the run")(stop()),
    binding("r", "reset counters")(reset()),
    binding("+", "raise concurrency")(adjustConcurrency(1)),
    binding("-", "lower concurrency")(adjustConcurrency(-1)),
    binding("]", "double the request count")(scaleRequests(double = true)),
    binding("[", "halve the request count")(scaleRequests(double = false)),
    binding("enter", "dismiss the summary")(dismissSummary()),
    binding("q", "quit")(quitCleanly()),
  )

  /** Ctrl+C should not race the workers on the way out either. `false` lets the normal teardown proceed. */
  override def onInterrupt(): Boolean =
    runner.stop()
    false

  override def onTick(): Unit =
    if phase.peek == Phase.Running then
      absorb()
      elapsed.set((System.nanoTime() - startedNanos).nanos)

  // ---- the run lifecycle, all of it on the render thread ----

  private def start(): Unit =
    if phase.peek != Phase.Running then
      dismissSummary()
      clearCounters()
      startedNanos = System.nanoTime()
      phase.set(Phase.Running)
      runId += 1
      val thisRun = runId
      // Called from a key binding, so we are on the render thread — which is what makes the capture inside
      // `Async.runCatching` name this app's render loop. See LoadRunner.start.
      runner.start(plan.peek)(outcome => finish(thisRun, outcome))

  private def stop(): Unit =
    if phase.peek == Phase.Running then runner.stop()

  private def reset(): Unit =
    runner.stop()
    dismissSummary()
    clearCounters()
    phase.set(Phase.Idle)

  /** The run's continuation, delivered on the render thread by `Async`.
    *
    * `runId` is checked because a run that was reset or restarted while in flight still delivers its callback: the work
    * was submitted, nothing can un-submit it. Comparing generations drops the stale one instead of letting it declare
    * the *current* run finished.
    */
  private def finish(forRun: Int, outcome: RunOutcome): Unit =
    if forRun == runId && phase.peek == Phase.Running then
      // The last batch is already on the queue — the pool had fully drained before this callback was queued — and
      // once the phase leaves Running nobody else will pick it up.
      absorb()
      elapsed.set((System.nanoTime() - startedNanos).nanos)
      phase.set(Phase.Finished(outcome))
      showSummary()

  private def absorb(): Unit =
    val batch = runner.drain()
    stats.update(_.record(batch))
    throughput.update(window => (window :+ batch.size.toLong).takeRight(LoadTestApp.ThroughputWindow))
    peakThroughput.update(peak => math.max(peak, batch.size.toLong))

  private def clearCounters(): Unit =
    stats.set(RunStats.empty)
    throughput.set(Vector.empty)
    peakThroughput.set(1L)
    elapsed.set(0.millis)

  private def quitCleanly(): Unit =
    // Stop the workers *before* leaving the loop. `Async`'s threads are daemons, so the JVM would exit regardless —
    // but a headless test outlives the runner, and "no thread survives quit" is a property worth actually having.
    runner.stop()
    quit()

  private def adjustConcurrency(delta: Int): Unit =
    if phase.peek != Phase.Running then
      plan.update(current => current.copy(concurrency = math.max(1, math.min(64, current.concurrency + delta))))

  private def scaleRequests(double: Boolean): Unit =
    if phase.peek != Phase.Running then
      plan.update { current =>
        val scaled = if double then current.requests * 2 else current.requests / 2
        current.copy(requests = math.max(10, math.min(1000000, scaled)))
      }

  // ---- view ----

  def view(using ReactiveScope): Element =
    given Theme = theme
    scaffold(
      topBar = Some(topBar("loadtest", right = headline)),
      // `statusBar(bindings)` would render all nine hints and run off an 80-column terminal, so the keys are grouped
      // by hand here. The full descriptions still reach the Ctrl+P palette from `bindings`.
      statusBar = Some(
        statusBar(
          Seq("s" -> "start", "x" -> "stop", "r" -> "reset", "+/-" -> "conc", "[/]" -> "reqs", "q" -> "quit")
        )
      ),
    )(
      column(
        progressPanel.length(3),
        row(countersPanel.percent(38), throughputPanel.fill).length(8),
        row(histogramPanel.fill, latencyPanel.length(28)).fill,
      )
    )

  private def headline(using ReactiveScope): String =
    val current = plan.get
    s"${target.describe}  n=${current.requests} c=${current.concurrency}"

  private def phaseLabel(using ReactiveScope): String =
    phase.get match
      case Phase.Idle                                 => "Idle - press s to start"
      case Phase.Running                              => "Running"
      case Phase.Finished(RunOutcome.Completed)       => "Completed"
      case Phase.Finished(RunOutcome.Stopped)         => "Stopped"
      case Phase.Finished(RunOutcome.Crashed(reason)) => s"Crashed: $reason"

  private def progressPanel(using ReactiveScope, Theme): Element =
    val current = stats.get
    val total   = plan.get.requests
    panel(phaseLabel)(progressBar(current.sent, total).labelled(s"${current.sent} / $total"))

  private def countersPanel(using ReactiveScope): Element =
    val current = stats.get
    val seconds = elapsed.get.toMillis / 1000.0
    val rate    = if seconds <= 0.0 then 0.0 else current.sent / seconds
    panel("Counters")(
      text(f"sent     ${current.sent}%7d"),
      text(f"ok       ${current.ok}%7d").color(Color.Green),
      if current.failed > 0 then text(f"failed   ${current.failed}%7d").color(Color.Red)
      else text(f"failed   ${current.failed}%7d").dim,
      text(f"req/s    $rate%7.1f").color(Color.Cyan),
      text(f"elapsed  $seconds%6.1fs"),
      // Not a signal, so this only refreshes because a tick redrew the frame anyway — which is exactly what it is
      // here to show: the pool filling up on start and emptying again on stop.
      text(f"workers  ${runner.workersAlive}%7d").dim,
    )

  private def throughputPanel(using ReactiveScope): Element =
    val window = throughput.get
    val peak   = peakThroughput.get
    panel(s"Throughput (peak $peak per ${LoadTestApp.TickRate.toMillis}ms)")(
      if window.isEmpty then text("no samples yet").dim.fill
      // `sparkline(data)` autoscales to the window's own maximum on every frame, so a live trace rescales under the
      // reader and a flat line looks identical to a spiky one. Pinning `max` to the peak seen this run fixes the
      // scale. The DSL factory does not expose `max`; the node behind it does.
      else SparklineElement(window, max = Some(peak)).color(Color.Cyan).fill
    )

  private def histogramPanel(using ReactiveScope, Theme): Element =
    val buckets = histogram.get
    val tallest = buckets.map(_.count).maxOption.getOrElse(0)
    panel("Latency histogram (ms)")(
      if buckets.isEmpty then text("no samples yet").dim.fill
      else column(buckets.map(bucketRow(_, tallest))*).fill
    )

  /** A horizontal bar per bucket. `BarChart` is the vertical alternative, but it silently drops any bar that does not
    * fully fit the width, and a latency histogram reads better with the range on the left anyway.
    */
  private def bucketRow(bucket: LatencyBucket, tallest: Int)(using Theme): Element =
    row(
      text(f"${LoadTestApp.ms(bucket.lowMicros)}%6.2f-${LoadTestApp.ms(bucket.highMicros)}%6.2f").length(14).dim,
      progressBar(bucket.count, tallest).bare.fill,
      text(f"${bucket.count}%5d").length(6),
    ).length(1)

  private def latencyPanel(using ReactiveScope): Element =
    val summary                                           = latency.get
    // Neither `Table` nor `DataTable` can right-align a column, so the numbers are padded to a fixed width here.
    def statRow(label: String, micros: Long): Seq[String] = Seq(label, f"${LoadTestApp.ms(micros)}%9.2f")
    panel("Latency (ms)")(
      TableElement(
        rows = Seq(
          statRow("p50", summary.p50),
          statRow("p90", summary.p90),
          statRow("p99", summary.p99),
          statRow("max", summary.max),
          statRow("mean", summary.mean),
          statRow("min", summary.min),
        ),
        widths = Seq(Constraint.Length(6), Constraint.Fill(1)),
        header = Some(Seq("stat", "ms")),
      ).fill
    )

  // ---- the end-of-run summary, as a modal screen ----

  private def showSummary(): Unit =
    given Theme = theme
    summaryOpen.set(true)
    pushScreen(Screen { summaryView })

  private def dismissSummary(): Unit =
    if summaryOpen.peek then
      popScreen()
      summaryOpen.set(false)

  private def summaryView(using ReactiveScope, Theme): Element =
    val current      = stats.get
    val summary      = latency.get
    val seconds      = math.max(0.001, elapsed.get.toMillis / 1000.0)
    val success      = if current.sent == 0 then 0.0 else current.ok * 100.0 / current.sent
    // oha's rule, and a good one: the success rate is the only number worth colouring by threshold.
    val successColor =
      if success >= 100.0 then Color.Green else if success >= 99.0 then Color.Yellow else Color.Red
    val worstErrors  = current.errors.toSeq.sortBy((_, count) => -count).take(3)
    val body         =
      Seq(
        text(phaseLabel).bold,
        text(f"requests    ${current.sent}%d in $seconds%.2f s"),
        text(f"success     $success%.2f %%").color(successColor),
        text(f"throughput  ${current.sent / seconds}%.1f req/s"),
        text(f"fastest     ${LoadTestApp.ms(summary.min)}%.2f ms").color(Color.Green),
        text(f"slowest     ${LoadTestApp.ms(summary.max)}%.2f ms").color(Color.Yellow),
        text(
          f"p50/p90/p99 ${LoadTestApp.ms(summary.p50)}%.2f / ${LoadTestApp.ms(summary.p90)}%.2f" +
            f" / ${LoadTestApp.ms(summary.p99)}%.2f ms"
        ).color(Color.Cyan),
        spacer(1),
      ) ++ (
        if worstErrors.isEmpty then Seq(text("no errors").dim)
        else worstErrors.map((reason, count) => text(s"[$count] $reason").color(Color.Red))
      ) ++ Seq(
        spacer,
        text("Enter dismiss  ·  r reset  ·  q quit").dim,
      )
    // `Block` deliberately never paints its interior, and a modal `Screen` layers over the live view — so without a
    // fill of its own the histogram behind this dialog shows through the gaps between the words.
    centered(56, 17)(FilledElement(panel("Run summary")(body*).rounded, summon[Theme].primary))

object Main:
  def main(args: Array[String]): Unit =
    LoadTestApp.fromArgs(args).run().left.foreach(error => println(s"failed to run: $error"))

object LoadTestApp:

  private[loadtest] val TickRate: FiniteDuration = 100.millis
  private val ThroughputWindow                   = 60
  private val HistogramBuckets                   = 8

  private[loadtest] def ms(micros: Long): Double = micros / 1000.0

  /** `--requests N`, `--concurrency C`, `--url <URL>`. Unknown flags are ignored: an example that dies on a typo
    * teaches nothing.
    */
  def fromArgs(args: Array[String]): LoadTestApp =
    val flags  = args.sliding(2, 2).collect { case Array(flag, value) => flag -> value }.toMap
    val plan   = Plan(
      requests = flags.get("--requests").flatMap(_.toIntOption).map(math.max(1, _)).getOrElse(500),
      concurrency = flags.get("--concurrency").flatMap(_.toIntOption).map(math.max(1, _)).getOrElse(8),
    )
    val target = flags.get("--url").map(url => HttpTarget(url)).getOrElse(FakeTarget())
    LoadTestApp(target, plan)
