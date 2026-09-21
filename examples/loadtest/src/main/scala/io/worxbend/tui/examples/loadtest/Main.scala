package io.worxbend.tui.examples.loadtest

import io.worxbend.tui.dsl.*

import java.util.Locale
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

  // Derived values live inside [[RunStats]], not in `Computed`s over a raw latencies vector: a `Computed` recomputes
  // every time `stats` changes, which while a run is in flight is every render tick, each recompute re-sorting a
  // vector that grows to a million samples. `RunStats.record` folds the summary and histogram in as it absorbs each
  // batch, so the panels below read them like any other field.
  override def bindings: KeyBindings = KeyBindings(
    binding("s", "start the run")(start()),
    binding("x", "stop the run")(stop()),
    binding("r", "reset counters")(reset()),
    binding("+", "raise concurrency")(adjustConcurrency(1)),
    binding("-", "lower concurrency")(adjustConcurrency(-1)),
    binding("]", "double the request count")(scaleRequests(_ * 2)),
    binding("[", "halve the request count")(scaleRequests(_ / 2)),
    binding("enter", "dismiss the summary")(dismissSummary()),
    binding("q", "quit")(quit()),
  )

  /** Stops the workers on the way out, whatever ended the run — `q`, Ctrl+C, a backend failure.
    *
    * `Async`'s threads are daemons, so the JVM would exit regardless — but a headless test outlives the runner, and "no
    * thread survives quit" is a property worth actually having.
    */
  override def onStop(): Unit = runner.stop()

  override def onTick(): Unit =
    if phase.peek == Phase.Running then
      absorb()
      elapsed.set((System.nanoTime() - startedNanos).nanos)

  // ---- the run lifecycle, all of it on the render thread ----

  /** Runs `action` only while no run is in flight; mid-run, plan edits and restarts are silently ignored. */
  private def whenNotRunning(action: => Unit): Unit =
    if phase.peek != Phase.Running then action

  private def start(): Unit =
    whenNotRunning {
      dismissSummary()
      clearCounters()
      startedNanos = System.nanoTime()
      phase.set(Phase.Running)
      runId += 1
      val thisRun = runId
      // Called from a key binding, so we are on the render thread — which is what makes the capture inside
      // `Async.runCatching` name this app's render loop. See LoadRunner.start.
      runner.start(plan.peek)(outcome => finish(thisRun, outcome))
    }

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
    stats.update(_.record(batch, LoadTestApp.HistogramBuckets))
    throughput.update(window => (window :+ batch.size.toLong).takeRight(LoadTestApp.ThroughputWindow))
    peakThroughput.update(peak => math.max(peak, batch.size.toLong))

  private def clearCounters(): Unit =
    stats.set(RunStats.empty)
    throughput.set(Vector.empty)
    peakThroughput.set(1L)
    elapsed.set(0.millis)

  private def adjustConcurrency(delta: Int): Unit =
    whenNotRunning {
      plan.update(current => current.copy(concurrency = LoadTestApp.clampConcurrency(current.concurrency + delta)))
    }

  /** Rewrites the request count with `scale`, e.g. `_ * 2` for the `]` key and `_ / 2` for `[`.
    *
    * Taking the arithmetic as a function rather than a `double: Boolean` flag keeps the two keys reading as what they
    * do at the call site. Render thread only, like every other key handler: it writes a `Signal`.
    */
  private def scaleRequests(scale: Int => Int): Unit =
    whenNotRunning {
      plan.update(current => current.copy(requests = LoadTestApp.clampRequests(scale(current.requests))))
    }

  // ---- view ----

  def view(using ReactiveScope, Theme): Element =
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

  /** Elapsed run time in seconds. The two readers guard the zero case differently — the live counter shows `0.0`, the
    * summary clamps to a millisecond so its throughput can never divide by zero — so the guard stays at each call site
    * and only the conversion is shared.
    */
  private def elapsedSeconds(using ReactiveScope): Double = elapsed.get.toMillis / 1000.0

  private def countersPanel(using ReactiveScope, Theme): Element =
    val current   = stats.get
    val seconds   = elapsedSeconds
    val rate      = if seconds <= 0.0 then 0.0 else current.sent / seconds
    val failedRow = text(fixed("failed   %7d", current.failed))
    panel("Counters")(
      text(fixed("sent     %7d", current.sent)),
      text(fixed("ok       %7d", current.ok)).fg(Color.Green),
      if current.failed > 0 then failedRow.fg(Color.Red) else failedRow.dim,
      text(fixed("req/s    %7.1f", rate)).fg(Color.Cyan),
      text(fixed("elapsed  %6.1fs", seconds)),
      // Not a signal, so this only refreshes because a tick redrew the frame anyway — which is exactly what it is
      // here to show: the pool filling up on start and emptying again on stop.
      text(fixed("workers  %7d", runner.workersAlive)).dim,
    )

  private def throughputPanel(using ReactiveScope, Theme): Element =
    val window = throughput.get
    val peak   = peakThroughput.get
    panel(s"Throughput (peak $peak per ${LoadTestApp.TickRate.toMillis}ms)")(
      if window.isEmpty then text("no samples yet").dim.fill
      // `sparkline(data)` autoscales to the window's own maximum on every frame, so a live trace rescales under the
      // reader and a flat line looks identical to a spiky one. Pinning `max` to the peak seen this run fixes the
      // scale. The DSL factory does not expose `max`; the node behind it does.
      else sparkline(window).max(peak).fg(Color.Cyan).fill
    )

  private def histogramPanel(using ReactiveScope, Theme): Element =
    val buckets = stats.get.histogram
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
      text(fixed("%6.2f-%6.2f", LoadTestApp.ms(bucket.lowMicros), LoadTestApp.ms(bucket.highMicros)))
        .length(14)
        .dim,
      progressBar(bucket.count, tallest).bare.fill,
      text(fixed("%5d", bucket.count)).length(6),
    ).length(1)

  private def latencyPanel(using ReactiveScope, Theme): Element =
    val summary                                           = stats.get.summary
    // `TableElement`'s rows are plain strings with no per-column alignment, so the numbers are padded to a fixed
    // width here.
    def statRow(label: String, micros: Long): Seq[String] = Seq(label, fixed("%9.2f", LoadTestApp.ms(micros)))
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
    summaryOpen.set(true)
    pushScreen(Screen { summaryView })

  private def dismissSummary(): Unit =
    if summaryOpen.peek then
      popScreen()
      summaryOpen.set(false)

  private def summaryView(using ReactiveScope, Theme): Element =
    val current      = stats.get
    val summary      = current.summary
    val seconds      = math.max(0.001, elapsedSeconds)
    val success      = if current.sent == 0 then 0.0 else current.ok * 100.0 / current.sent
    // oha's rule, and a good one: the success rate is the only number worth colouring by threshold.
    val successColor =
      if success >= 100.0 then Color.Green else if success >= 99.0 then Color.Yellow else Color.Red
    val worstErrors  = current.errors.toSeq.sortBy((_, count) => -count).take(3)
    val body         =
      Seq(
        text(phaseLabel).bold,
        text(fixed("requests    %d in %.2f s", current.sent, seconds)),
        text(fixed("success     %.2f %%", success)).fg(successColor),
        text(fixed("throughput  %.1f req/s", current.sent / seconds)),
        text(fixed("fastest     %.2f ms", LoadTestApp.ms(summary.min))).fg(Color.Green),
        text(fixed("slowest     %.2f ms", LoadTestApp.ms(summary.max))).fg(Color.Yellow),
        text(
          fixed(
            "p50/p90/p99 %.2f / %.2f / %.2f ms",
            LoadTestApp.ms(summary.p50),
            LoadTestApp.ms(summary.p90),
            LoadTestApp.ms(summary.p99),
          )
        ).fg(Color.Cyan),
        spacer(1),
      ) ++ (
        if worstErrors.isEmpty then Seq(text("no errors").dim)
        else worstErrors.map((reason, count) => text(s"[$count] $reason").fg(Color.Red))
      ) ++ Seq(
        spacer,
        text("Enter dismiss  ·  r reset  ·  q quit").dim,
      )
    // `Block` deliberately never paints its interior, and a modal `Screen` layers over the live view — so without a
    // fill of its own the histogram behind this dialog shows through the gaps between the words.
    centered(LoadTestApp.SummaryWidth, LoadTestApp.SummaryHeight)(
      FilledElement(panel("Run summary")(body*).rounded, summon[Theme].primary)
    )

  /** `String.format` pinned to [[Locale.ROOT]]: every number on this screen is formatted through here.
    *
    * The `f` interpolator this replaced formats through the *default* FORMAT locale, so on a comma-decimal machine the
    * summary read "12,50 ms" where the histogram beside it read "12.50", and under a locale whose digits are not ASCII
    * every counter came out in another script. procmon's `decimal`/`integer` state the same rule; this screen has too
    * many call sites to repeat it at each one. The widths here are hand-padded (`%7d`, `%6.2f`) to line the columns up,
    * which a locale-dependent separator would also undo.
    *
    * `args` is `Any*` and forwards to `String.format`'s Java `Object...` rather than being declared `AnyRef*`: a Scala
    * `AnyRef*` parameter does not accept an `Int` or a `Double` at all ("implicit conversions were not tried because
    * the result of an implicit conversion must be more specific than AnyRef"), while Java varargs of `Object` box
    * primitives at the call site.
    */
  private def fixed(spec: String, args: Any*): String = String.format(Locale.ROOT, spec, args*)

/** The one example that reads its command line, so it builds the app first and then hands over to the `main` `TuiApp`
  * supplies — which is also where the failure reporting lives.
  */
object Main:
  def main(args: Array[String]): Unit = LoadTestApp.fromArgs(args).main(args)

object LoadTestApp:

  private[loadtest] val TickRate: FiniteDuration = 100.millis
  private val ThroughputWindow                   = 60
  private val HistogramBuckets                   = 8

  // What `+`/`-` and `[`/`]` may not push the plan past. One thread is the floor because zero workers would never
  // finish; 64 is a laptop's worth of sockets, and a million requests is more than an example should ever be asked to
  // queue. Ten is the floor for the request count so that the percentile table still has something to compute from.
  private val MinConcurrency = 1
  private val MaxConcurrency = 64
  private val MinRequests    = 10
  private val MaxRequests    = 1000000

  // The summary modal's fixed footprint: wide enough for the longest summary line, tall enough for the body plus
  // the three worst errors.
  private val SummaryWidth  = 56
  private val SummaryHeight = 17

  private def clampConcurrency(value: Int): Int = math.max(MinConcurrency, math.min(MaxConcurrency, value))

  private def clampRequests(value: Int): Int = math.max(MinRequests, math.min(MaxRequests, value))

  private[loadtest] def ms(micros: Long): Double = micros / 1000.0

  /** `--requests N`, `--concurrency C`, `--url <URL>`. Unknown flags are ignored: an example that dies on a typo
    * teaches nothing.
    */
  def fromArgs(args: Array[String]): LoadTestApp =
    val flags  = args.sliding(2, 2).collect { case Array(flag, value) => flag -> value }.toMap
    val plan   = Plan(
      requests = intFlag(flags, "--requests", 500),
      concurrency = intFlag(flags, "--concurrency", 8),
    )
    val target = flags.get("--url").map(url => HttpTarget(url)).getOrElse(FakeTarget())
    LoadTestApp(target, plan)

  /** An integer flag with a fallback: unparseable values are ignored, and anything that parses is clamped to at least 1
    * — a one-shot floor for the command line, deliberately simpler than the key handlers' live clamps.
    */
  private def intFlag(flags: Map[String, String], name: String, default: Int): Int =
    flags.get(name).flatMap(_.toIntOption).map(math.max(1, _)).getOrElse(default)
