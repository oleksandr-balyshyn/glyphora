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
  */
object Async:

  /** Runs `work` on a background thread; when it finishes, `onResult` runs on the render thread with the value. Use for
    * one-shot IO (HTTP, disk) whose result feeds a signal: `Async.run(api.fetch())(rows.set)`. Exceptions are reported
    * through `onError` (default: rethrow on the worker thread) — see [[runCatching]] for an `Either` result.
    */
  def run[A](work: => A)(onResult: A => Unit)(using onError: AsyncErrorHandler = AsyncErrorHandler.rethrow): Unit =
    val deliver = deliverToRenderThread(onResult)
    val _       = worker.submit(new Runnable {
      def run(): Unit =
        try deliver(work)
        catch case NonFatal(error) => onError.handle(error)
    })

  /** Like [[run]] but delivers `Right(value)` or `Left(throwable)` to `onDone` on the render thread — no ambient error
    * handler needed. The idiomatic way to drive a load into `Signal[Either[Throwable, A]]` (or a loading/error state).
    */
  def runCatching[A](work: => A)(onDone: Either[Throwable, A] => Unit): Unit =
    val deliver = deliverToRenderThread(onDone)
    val _       = worker.submit(new Runnable {
      def run(): Unit =
        val outcome: Either[Throwable, A] =
          try Right(work)
          catch case NonFatal(error) => Left(error)
        deliver(outcome)
    })

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
  /** Rethrows on the worker thread (surfaces in logs / the default uncaught-exception handler). */
  val rethrow: AsyncErrorHandler = error => throw error

  /** Swallows the error silently. */
  val ignore: AsyncErrorHandler = _ => ()

  /** Reports the error by running `report` on the render thread. */
  def onRenderThread(report: Throwable => Unit): AsyncErrorHandler =
    error => RenderThread.runLater(report(error))
