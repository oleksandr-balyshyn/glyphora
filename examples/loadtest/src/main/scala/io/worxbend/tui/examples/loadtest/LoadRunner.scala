package io.worxbend.tui.examples.loadtest

import io.worxbend.tui.runtime.Async

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.{ConcurrentLinkedQueue, ExecutorService, Executors, ThreadFactory, TimeUnit}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** How many requests to fire, and how many at a time. */
final case class Plan(requests: Int, concurrency: Int)

/** How a run ended. A sealed set rather than a `Boolean` or a nullable message, so the summary can say which of the
  * three actually happened without inventing a fourth.
  */
enum RunOutcome:
  case Completed
  case Stopped
  case Crashed(reason: String)

/** The concurrency engine, and the reason this example exists.
  *
  * The problem: a `Signal` may only be written from the render thread ([[io.worxbend.tui.runtime.RenderThread]]), and a
  * load test is a pool of threads producing results as fast as the network allows. Two rules keep those compatible.
  *
  *   1. **Capture the render loop before going async.** `Async.run`/`runCatching` call `RenderThread.capture()` on the
  *      *calling* thread, then submit — so the continuation returns to the runner that started the work. That only
  *      works if the call is made while on a render thread. See [[start]].
  *   2. **Do not marshal one continuation per request.** Workers push their results onto a lock-free queue and never
  *      touch a signal; the app [[drain]]s that queue once per render tick. See the note on [[drain]].
  *
  * Everything mutable here is an atomic or a concurrent collection, because it is read and written from the render
  * thread, the coordinator thread, and every worker at once.
  */
final class LoadRunner(target: Target):

  private val completed   = ConcurrentLinkedQueue[Sample]()
  private val cancelled   = AtomicBoolean(false)
  private val liveWorkers = AtomicInteger(0)
  private val generation  = AtomicInteger(0)

  /** Thread-name prefix for this runner's workers. Unique per instance so a test can prove *its* pool is gone even when
    * another runner is alive in the same JVM.
    */
  val threadPrefix: String = s"loadtest-worker-${LoadRunner.instances.incrementAndGet()}-"

  /** How many worker threads are still alive. Zero once a run has fully torn down. */
  def workersAlive: Int = liveWorkers.get()

  /** Starts a run, calling `onFinished` on the render thread when the pool has drained.
    *
    * '''Must be called from the render thread''' — from a key binding, `onTick`, or another continuation.
    * `Async.runCatching` captures this runner's render loop on the calling thread before it submits the work, so the
    * completion lands back here. Called from a constructor there is no runner registered yet; the capture falls back to
    * the detached loop and, with two apps live (a parallel test suite is exactly that), the callback is drained by
    * whichever render thread reaches it first.
    *
    * The whole concurrent phase sits inside a *single* `Async` task. `Async`'s own pool is an unbounded, process-wide
    * cached pool with no way to bound it, so `-c 50` submitted as fifty `Async.run`s would be fifty threads on a pool
    * shared with everything else in the JVM. One task that owns a private fixed pool consumes one `Async` thread and
    * keeps the concurrency limit meaningful.
    */
  def start(plan: Plan)(onFinished: RunOutcome => Unit): Unit =
    val mine = generation.incrementAndGet()
    cancelled.set(false)
    completed.clear()
    Async.runCatching(driveRun(plan, mine)) {
      case Right(outcome) => onFinished(outcome)
      case Left(failure)  => onFinished(RunOutcome.Crashed(Option(failure.getMessage).getOrElse(failure.toString)))
    }

  /** Asks the workers to stop after their current request.
    *
    * `Async.run` hands back no cancellation handle — only `Async.after`/`every` do — so cancellation is a flag the
    * worker loop reads between requests, plus the timeout the target already enforces on the request itself.
    */
  def stop(): Unit = cancelled.set(true)

  /** Takes everything finished since the last call.
    *
    * This is the whole crossing point from worker threads into the UI, and it is called from the render thread. The
    * obvious alternative — one `Async.run` per request, so each result marshals itself back — is correct and ruinous: a
    * few thousand requests a second queue a few thousand continuations a second onto the render loop and the frame
    * never finishes. Batching at the frame rate costs nothing, because the screen cannot show more than that anyway.
    */
  def drain(): Vector[Sample] =
    val batch = Vector.newBuilder[Sample]
    var next  = completed.poll()
    while next != null do
      batch += next
      next = completed.poll()
    batch.result()

  /** Runs on one `Async` worker thread: owns the pool, waits for it, and reports how it ended. */
  private def driveRun(plan: Plan, mine: Int): RunOutcome =
    val threads   = math.max(1, math.min(LoadRunner.MaxConcurrency, plan.concurrency))
    val remaining = AtomicInteger(math.max(0, plan.requests))
    val pool      = Executors.newFixedThreadPool(threads, workerFactory)
    val settled   =
      try
        (1 to threads).foreach(_ => pool.execute(() => fireUntilDone(remaining, mine)))
        drainPool(pool)
      finally
        // belt and braces: however the block above ended, including `execute` refusing a task, this pool does not
        // outlive the call
        pool.shutdownNow()
    if !settled then RunOutcome.Crashed("workers did not stop within the settle timeout")
    else if cancelled.get() then RunOutcome.Stopped
    else RunOutcome.Completed

  /** One worker: pull a ticket, fire, repeat.
    *
    * Workers pull tickets rather than being handed a slice of the run up front. With a slice, one slow worker holds the
    * whole run open at the end while the others idle; with tickets, a slow response costs only that worker's next
    * request.
    */
  private def fireUntilDone(remaining: AtomicInteger, mine: Int): Unit =
    liveWorkers.incrementAndGet()
    try
      while generation.get() == mine && !cancelled.get() && remaining.getAndDecrement() > 0 do
        // The generation check also guards the enqueue: a worker from a run that was reset a moment ago must not drop
        // a stale sample into the next run's counters.
        val sample = target.fire() match
          case Right(latency) => Sample.Ok(latency.toMicros)
          case Left(reason)   => Sample.Failed(reason)
        if generation.get() == mine then completed.add(sample)
    catch case _: InterruptedException => () // `shutdownNow` reached us mid-request; stopping is what was asked for
    finally liveWorkers.decrementAndGet()

  /** Shuts `pool` down and waits for its threads to actually die, interrupting any that overstay.
    *
    * `false` means a worker is still alive after both waits — a leak, which the summary reports rather than hides.
    */
  private def drainPool(pool: ExecutorService): Boolean =
    pool.shutdown() // refuse new tasks; the ones already submitted run to completion
    if pool.awaitTermination(LoadRunner.SettleTimeout.toMillis, TimeUnit.MILLISECONDS) then true
    else
      pool.shutdownNow()
      pool.awaitTermination(LoadRunner.SettleTimeout.toMillis, TimeUnit.MILLISECONDS)

  /** Daemon threads: nothing this example starts may keep the JVM alive after the UI is gone. */
  private def workerFactory: ThreadFactory =
    val counter = AtomicInteger(0)
    runnable =>
      val thread = Thread(runnable, s"$threadPrefix${counter.getAndIncrement()}")
      thread.setDaemon(true)
      thread

object LoadRunner:
  private val MaxConcurrency                = 256
  private val SettleTimeout: FiniteDuration = 10.seconds
  private val instances                     = AtomicInteger(0)
