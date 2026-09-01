package io.worxbend.tui.testsupport

/** A small warm-up-and-measure harness for the hand-run benchmarks that live under each module's `src/test`.
  *
  * These are not tests and nothing here is ever asserted on. Wall-clock numbers on a shared or loaded machine move by
  * far more than most changes worth making, so a timing threshold in CI would fail for reasons that have nothing to do
  * with the commit under test — the repository already has [[GoldenFrames]] and the ordinary suites for correctness,
  * and a cost that genuinely must hold is asserted as a property (`ViewportCostSpec` in the widget tests counts work
  * done, not seconds spent). What this harness is for is a developer comparing two commits on one machine, in one
  * sitting.
  *
  * Three decisions are worth knowing before reading a number it printed:
  *
  *   - it reports the **median** of the samples, not the mean, because one JIT recompilation or one garbage collection
  *     pause moves a mean and does not move a median;
  *   - it runs `warmups` untimed passes first, so the timed passes measure compiled code rather than the interpreter;
  *   - a benchmark whose result is thrown away can be deleted wholesale by the JIT, so a body that computes something
  *     must hand it to [[consume]].
  *
  * Single-threaded and not thread-safe: run one benchmark at a time on one thread, which is also the only way the
  * numbers mean anything.
  */
object Bench:

  /** What one benchmark measured. `nanosPerOp` is the median sample divided by the iterations in it, so two runs with
    * different iteration counts are still comparable.
    */
  final case class Result(name: String, opsPerSecond: Double, nanosPerOp: Double)

  // Somewhere for a benchmark body to put a value it computed. Read by nothing and written by everything, which is
  // exactly the point: it is a side effect the compiler cannot prove is unobservable, so the work that produced the
  // value cannot be deleted as dead code.
  private var sink: Long = 0L

  /** Swallows a value a benchmark body computed, so the work that produced it survives dead-code elimination.
    *
    * Without this, `encoder.encode(previous, next)` in a body whose result is discarded is a computation with no
    * observable effect, and a benchmark that measures nothing prints a very good number.
    */
  def consume(value: Long): Unit = sink += value

  /** Everything [[consume]] has swallowed since the process started.
    *
    * Reading it is the point rather than the number: a value nothing can ever observe is a value the compiler is free
    * to stop computing, and with it the work the benchmark was written to time. Printing this at the end of a
    * benchmark's `main` is a cheap way to be sure of that; the sum itself means nothing.
    */
  def consumed: Long = sink

  /** Runs `body` `iterations` times per sample, `samples` samples over, after `warmups` untimed samples of the same
    * size, and reports the median sample.
    *
    * `body` is a by-name-free `() => Unit` on purpose: a by-name parameter would be re-evaluated through a closure the
    * JIT treats differently from the call the benchmark is meant to represent.
    */
  def measure(name: String, iterations: Int, warmups: Int = 3, samples: Int = 7)(body: () => Unit): Result =
    require(iterations > 0, s"a benchmark needs at least one iteration, got $iterations")
    require(samples > 0, s"a benchmark needs at least one sample, got $samples")
    var warmup     = 0
    while warmup < warmups do
      run(iterations, body)
      warmup += 1
    val timings    = new Array[Long](samples)
    var sample     = 0
    while sample < samples do
      val start = System.nanoTime()
      run(iterations, body)
      timings(sample) = System.nanoTime() - start
      sample += 1
    // sorting happens outside every timed region, so it costs the measurement nothing
    java.util.Arrays.sort(timings)
    val median     = timings(samples / 2)
    val nanosPerOp = median.toDouble / iterations
    Result(name, if nanosPerOp > 0 then 1e9 / nanosPerOp else Double.PositiveInfinity, nanosPerOp)

  /** Prints `results` as a fixed-width table, so two runs of the same benchmark diff cleanly in a terminal. */
  def report(title: String, results: Seq[Result]): Unit =
    val nameWidth = (results.map(_.name.length) :+ 4).max
    println(title)
    println(row("name".padTo(nameWidth, ' '), "ns/op", "ops/s"))
    results.foreach { result =>
      println(row(result.name.padTo(nameWidth, ' '), f"${result.nanosPerOp}%.1f", f"${result.opsPerSecond}%.0f"))
    }

  /** One table row: the name as given, then two right-aligned number columns, so digits line up between rows. */
  private def row(name: String, nanos: String, ops: String): String =
    s"$name  ${rightAligned(nanos, 12)}  ${rightAligned(ops, 14)}"

  private def rightAligned(text: String, width: Int): String =
    if text.length >= width then text else " " * (width - text.length) + text

  private def run(iterations: Int, body: () => Unit): Unit =
    var index = 0
    while index < iterations do
      body()
      index += 1
