package io.worxbend.tui.runtime

import java.util.concurrent.{Executors, ScheduledExecutorService, ThreadFactory, TimeUnit}
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

/** A handle to cancel a scheduled or repeating task started through [[Async]]. */
trait Cancelable:
  def cancel(): Unit

object Cancelable:
  val noop: Cancelable = () => ()

/** Structured background work for a signals-driven app — glyphora's answer to bubbletea's `Cmd`/`Msg`.
  *
  * The problem it solves: [[Signal]]s may only be mutated on the render thread ([[RenderThread]]), so a naive
  * `new Thread { data.set(fetch()) }` throws. `Async` runs the work off-thread, then **marshals the continuation back
  * onto the render thread** (via [[RenderThread.runLater]]), where it may safely update signals. The runner drains that
  * queue at the top of each loop iteration and repaints if a signal changed — so the result appears as an ordinary
  * reactive update, no `Msg` plumbing.
  *
  * All executor threads are daemons, so pending work never keeps the JVM alive after the app quits. Callbacks scheduled
  * while no runner is active simply wait on the queue until one drains it (or are dropped when the process exits).
  *
  * **Lifetime.** Every entry point here captures its target runner at the moment it is called, and that runner's queue
  * stops accepting work once the runner exits. Concretely:
  *
  *   - Work already queued when the runner exits still runs: the runner drains its queue one last time on the way out,
  *     so a continuation that arrived during the final iteration is not lost.
  *   - Work queued *after* that is silently dropped — there is no longer a render thread to run it on, and keeping it
  *     would only grow memory.
  *   - A [[Cancelable]] from [[after]] or [[every]] is **not** cancelled by the app quitting. The scheduler is a
  *     process-lifetime daemon singleton, so an uncancelled `every` keeps firing (into a queue that now discards its
  *     bodies) until the JVM exits. Cancel it when the app quits — from `TuiApp.onStop`, which runs on every exit path
  *     including Ctrl+C, or wherever a bare [[Runner]] app finishes — which matters most in the embedded and
  *     multiple-runner cases this module is built for.
  *
  * The mirror-image rule applies to *starting* repeating work. Every entry point here captures the render loop of the
  * thread that calls it, and a thread that belongs to no runner — a constructor running before any runner is registered
  * — captures the shared unattributed queue instead. Those bodies are **not** discarded: they wait, and the next runner
  * to start drains the whole backlog in one go, so a poller armed from a field initialiser fires its accumulated ticks
  * all at once at startup. Only the oldest are dropped, and only once the backlog passes `RenderThread`'s cap on that
  * queue. Start repeating work from `TuiApp.onStart` (or a bare runner's `onStart`), which is the first moment the app
  * is certainly on the render thread.
  */
object Async:

  /** Runs `work` on a background thread; when it finishes, `onResult` runs on the render thread with the value. Use for
    * one-shot IO (HTTP, disk) whose result feeds a signal: `Async.run(api.fetch())(rows.set)`. See [[runCatching]] for
    * an `Either` result the app handles itself.
    *
    * `work` failing is the ordinary case, not a defect — a fetch times out, a file is missing — so the default
    * `onError` hands the throwable back to the render loop that armed the call ([[AsyncErrorHandler.toRenderThread]]),
    * where the runner reports it as `RunnerError.QueuedTask` with the others. Throwing it on the worker thread instead
    * (still available as [[AsyncErrorHandler.rethrow]]) prints a stack trace over the alternate screen the app is
    * drawing on, which the frame diff never repaints. Install a `given AsyncErrorHandler` to take reporting over.
    *
    * The default is evaluated at the call site, on the calling thread, which is what lets it capture the right loop.
    */
  def run[A](work: => A)(onResult: A => Unit)(using
      onError: AsyncErrorHandler = AsyncErrorHandler.toRenderThread()
  ): Unit =
    val deliver = deliverToRenderThread(onResult)
    onWorker {
      try deliver(work)
      catch case NonFatal(error) => onError.handle(error)
    }

  /** Like [[run]] but delivers `Right(value)` or `Left(throwable)` to `onDone` on the render thread — no ambient error
    * handler needed. The idiomatic way to drive a load into `Signal[Either[Throwable, A]]` (or a loading/error state).
    */
  def runCatching[A](work: => A)(onDone: Either[Throwable, A] => Unit): Unit =
    val deliver = deliverToRenderThread(onDone)
    onWorker {
      val outcome: Either[Throwable, A] =
        try Right(work)
        catch case NonFatal(error) => Left(error)
      deliver(outcome)
    }

  /** Runs `body` on the render thread once, after `delay`. Returns a handle to cancel it before it fires. */
  def after(delay: FiniteDuration)(body: => Unit): Cancelable =
    cancelling(
      scheduler.schedule(
        resumeOnRenderThread(body),
        delay.toMillis,
        TimeUnit.MILLISECONDS,
      )
    )

  /** Runs `body` on the render thread every `interval` (first tick after one `interval`). Returns a handle to stop it.
    * The place to drive animation or polling without a global `config.tickRate`.
    */
  def every(interval: FiniteDuration)(body: => Unit): Cancelable =
    val millis = math.max(1L, interval.toMillis)
    cancelling(
      scheduler.scheduleAtFixedRate(
        resumeOnRenderThread(body),
        millis,
        millis,
        TimeUnit.MILLISECONDS,
      )
    )

  /** Hands `body` to a background thread. The returned `Future` is discarded on purpose: cancellation of one-shot work
    * is not offered (only the scheduled entry points return a [[Cancelable]]), and every failure is already dealt with
    * inside `body` by the entry point that built it.
    */
  private def onWorker(body: => Unit): Unit =
    // named rather than passed inline because `submit` is overloaded for `Runnable` and `Callable`
    val task: Runnable = () => body
    val _              = worker.submit(task)

  /** Wraps `onValue` so that it is invoked on the render thread rather than on whichever thread produced the value.
    *
    * **Must be called on the caller's thread, before any work is handed to an executor.** That is the load-bearing part
    * and the reason this helper exists: [[RenderThread.capture]] resolves *the runner registered right here, right
    * now*, and it is the returned function — not this one — that runs on the worker. Calling `capture()` from inside
    * the worker instead still compiles and still passes a single-runner test, but resolves whatever loop that thread
    * happens to see, so the continuation is delivered to the wrong runner as soon as two runners coexist in one JVM.
    */
  private def deliverToRenderThread[A](onValue: A => Unit): A => Unit =
    val target = RenderThread.capture()
    value => target.enqueue(() => onValue(value))

  /** The scheduler-side counterpart of [[deliverToRenderThread]]: a `Runnable` the timer thread can run that does
    * nothing but hand `body` back to the render thread.
    *
    * Subject to the same rule — **call it on the caller's thread**, not from inside the scheduled task, so the runner
    * is captured before the work goes async.
    */
  private def resumeOnRenderThread(body: => Unit): Runnable =
    val target = RenderThread.capture()
    () => target.enqueue(() => body)

  /** Adapts a scheduled `future` to the [[Cancelable]] the timer entry points return. Never interrupts a body that has
    * already started running on the render thread; it only prevents runs that have not begun.
    */
  private def cancelling(future: java.util.concurrent.Future[?]): Cancelable =
    () => future.cancel(false)

  private val worker =
    Executors.newCachedThreadPool(daemonFactory("glyphora-async"))

  private val scheduler: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor(daemonFactory("glyphora-timer"))

  private def daemonFactory(prefix: String): ThreadFactory =
    new ThreadFactory:
      private val counter                       = new java.util.concurrent.atomic.AtomicInteger(0)
      def newThread(runnable: Runnable): Thread =
        val thread = new Thread(runnable, s"$prefix-${counter.getAndIncrement()}")
        thread.setDaemon(true)
        thread

/** How [[Async.run]] reports a failure of its background work. Provided as a `using` value so apps can install their
  * own (log, toast, set an error signal) without changing call sites.
  */
trait AsyncErrorHandler:
  def handle(error: Throwable): Unit

object AsyncErrorHandler:
  /** Rethrows on the worker thread, where the JVM's default uncaught-exception handler prints the stack trace to
    * standard error.
    *
    * For a terminal app that is the *same* tty the UI is drawn on, so the trace lands on top of the alternate screen
    * and stays there: the backend flushes only cells that differ from the frame it last flushed, and that frame still
    * describes what the app believes is on screen. Nothing repaints until a resize. Use this only where standard error
    * is not the UI — a bare [[Runner]] app writing elsewhere, or a process with no runner at all. [[toRenderThread]] is
    * the default for [[Async.run]] for exactly this reason.
    */
  val rethrow: AsyncErrorHandler = error => throw error

  /** Swallows the error silently. */
  val ignore: AsyncErrorHandler = _ => ()

  /** Rethrows on the render loop of the calling thread: the throwable is queued back to that runner, which absorbs it
    * through its `RenderTaskErrorHandler` and reports it as `RunnerError.QueuedTask` — count, first throwable and
    * suppressed stack traces — once the app exits. The default for [[Async.run]].
    *
    * **Construct it on the arming thread**, which the default argument does automatically: like the result delivery
    * itself, it resolves the target loop *now*, so a two-runner app reports each failure to the runner that started the
    * work rather than to whichever loop the worker thread happens to resolve. A handler installed once as a long-lived
    * `given` should be [[onRenderThread]] instead, which resolves per failure.
    */
  def toRenderThread(): AsyncErrorHandler =
    val target = RenderThread.capture()
    error => target.enqueue(() => throw error)

  /** Reports the error by running `report` on the render thread. */
  def onRenderThread(report: Throwable => Unit): AsyncErrorHandler =
    error => RenderThread.runLater(report(error))
