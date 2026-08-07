package io.worxbend.tui.examples.loadtest

/** One completed request, as the workers hand it back.
  *
  * Latency is carried as whole microseconds rather than a `FiniteDuration` because everything downstream wants a
  * number: `Sparkline` takes `Seq[Long]`, percentiles want a sortable key, and the histogram buckets by arithmetic.
  */
enum Sample:
  case Ok(micros: Long)
  case Failed(reason: String)

/** Everything the screen knows about a run, as one immutable value.
  *
  * One value rather than five separate signals on purpose. The tick that drains a batch of results writes it once, so
  * the view always sees a consistent snapshot (never "sent" updated but "ok" not yet) and the frame is invalidated once
  * instead of five times.
  */
final case class RunStats(
    sent: Int,
    ok: Int,
    failed: Int,
    latencies: Vector[Long],
    errors: Map[String, Int],
):

  /** Folds a whole drained batch in at once — per-sample `copy` would rebuild the error map thousands of times a second
    * for no benefit.
    */
  def record(batch: Vector[Sample]): RunStats =
    if batch.isEmpty then this
    else
      val successes = batch.collect { case Sample.Ok(micros) => micros }
      val failures  = batch.collect { case Sample.Failed(reason) => reason }
      RunStats(
        sent = sent + batch.size,
        ok = ok + successes.size,
        failed = failed + failures.size,
        latencies = latencies ++ successes,
        errors = failures.foldLeft(errors)((tally, reason) => tally.updated(reason, tally.getOrElse(reason, 0) + 1)),
      )

object RunStats:
  val empty: RunStats = RunStats(sent = 0, ok = 0, failed = 0, latencies = Vector.empty, errors = Map.empty)

/** The percentile block, all in microseconds. */
final case class LatencySummary(count: Int, min: Long, max: Long, mean: Long, p50: Long, p90: Long, p99: Long)

object LatencySummary:

  val empty: LatencySummary = LatencySummary(0, 0L, 0L, 0L, 0L, 0L, 0L)

  def of(latencies: Vector[Long]): LatencySummary =
    if latencies.isEmpty then empty
    else
      val sorted = latencies.sorted
      LatencySummary(
        count = sorted.size,
        min = sorted.head,
        max = sorted.last,
        mean = sorted.sum / sorted.size,
        p50 = percentile(sorted, 0.50),
        p90 = percentile(sorted, 0.90),
        p99 = percentile(sorted, 0.99),
      )

  /** Nearest-rank percentile over an already-sorted vector. The toolkit ships no statistics helpers — this is the whole
    * of what a load test needs, so it lives here rather than being hunted for.
    */
  private def percentile(sorted: Vector[Long], quantile: Double): Long =
    sorted(math.min(sorted.size - 1, (sorted.size * quantile).toInt))

/** One bar of the latency histogram: a half-open microsecond range and how many samples landed in it. */
final case class LatencyBucket(lowMicros: Long, highMicros: Long, count: Int)

object Histogram:

  /** Equal-width buckets spanning min..max of the sample set.
    *
    * The edges move as the run goes on, which is the honest thing to draw: a fixed scale chosen from the first hundred
    * requests hides the tail that shows up in the last thousand.
    */
  def of(latencies: Vector[Long], buckets: Int): Vector[LatencyBucket] =
    if latencies.isEmpty || buckets <= 0 then Vector.empty
    else
      val low    = latencies.min
      val span   = math.max(1L, latencies.max - low)
      val counts = Array.fill(buckets)(0)
      latencies.foreach { value =>
        val index = math.min(buckets - 1, ((value - low) * buckets / span).toInt)
        counts(index) += 1
      }
      Vector.tabulate(buckets) { index =>
        LatencyBucket(low + span * index / buckets, low + span * (index + 1) / buckets, counts(index))
      }
