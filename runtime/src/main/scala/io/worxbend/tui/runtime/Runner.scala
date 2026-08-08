package io.worxbend.tui.runtime

import io.worxbend.tui.core.Event
import io.worxbend.tui.terminal.BackendError

import scala.concurrent.duration.Duration

/** Runner configuration: an optional tick rate (synthetic [[Event.Tick]]s for animation) and whether to capture mouse
  * events.
  *
  * `onTaskError` decides what happens when a body queued onto the render thread (an [[Async]] continuation, a timer
  * body) throws. `None` — the default — accumulates them and returns them from [[Runner.run]] as
  * [[RunnerError.QueuedTask]] once the app exits: the first throwable, the total count, and the later throwables
  * attached to the first as suppressed exceptions. Installing a handler takes reporting over instead: it is called as
  * each failure happens, and `run` then returns `Right` unless the backend itself failed. Either way the loop survives
  * the failing body and the bodies queued behind it still run — but only as long as the reporting itself does not
  * throw. A handler that throws is not isolated: it unwinds out of the drain, abandoning the bodies queued behind it,
  * and out of [[Runner.run]]. Keep a handler total.
  */
final case class RunnerConfig(
    tickRate: Option[Duration] = None,
    mouseCapture: Boolean = false,
    onTaskError: Option[RenderTaskErrorHandler] = None,
)

/** The mid-level API tier: owns the event/render loop over a `Backend`.
  *
  * `handleEvent` returns whether the UI should redraw; `render` fills the frame on each redraw. `run` blocks until the
  * app quits (via [[RunnerHandle.quit]]) or the backend fails, and always restores the terminal on the way out. The
  * calling thread becomes the render thread for the duration of `run`.
  */
trait Runner:
  def run(
      handleEvent: (Event, RunnerHandle) => Boolean,
      render: Frame => Unit,
  ): Either[RunnerError, Unit]

/** Handed to event handlers: request loop exit, marshal work onto the render thread, or reach the backend for
  * out-of-band terminal operations like clipboard access.
  */
trait RunnerHandle:
  def quit(): Unit
  def runOnRenderThread(body: => Unit): Unit

  /** Copies `text` to the system clipboard (OSC 52). Best-effort; unsupported terminals ignore it. */
  def copyToClipboard(text: String): Unit

  /** Hands the terminal to `body` (leaving the app's screen) and restores afterward — e.g. launch `$EDITOR`. Call it
    * from an event handler (already on the render thread). Default: just runs `body`.
    */
  def suspend(body: => Unit): Unit = body

  /** Prints `lines` into the terminal scrollback above the live UI (durable log output). Default: a no-op. */
  def printAbove(lines: Seq[String]): Unit = ()

/** Every queued-body failure one run absorbed, collapsed into a single report.
  *
  * `first` is the throwable that started it and `count` how many failures there were in total; the later throwables are
  * attached to `first` as suppressed exceptions, up to a cap — so a continuation that fails on every tick for a week
  * neither reports as one incident nor accumulates a week of stack traces.
  */
final case class QueuedTaskFailures(first: Throwable, count: Int)

enum RunnerError:
  /** The backend failed, which ends the loop. `queuedTasks` carries whatever queued-body failures the loop had already
    * absorbed, so they are still reported when a terminal failure happens to follow them.
    */
  case Backend(error: BackendError, queuedTasks: Option[QueuedTaskFailures] = None)

  /** Bodies queued onto the render thread threw. The loop absorbed them and kept running; this reports them once the
    * app has exited. Install [[RunnerConfig.onTaskError]] to handle them as they happen instead.
    */
  case QueuedTask(failures: QueuedTaskFailures)
