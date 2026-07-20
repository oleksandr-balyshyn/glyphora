package io.worxbend.tui.runtime

import io.worxbend.tui.core.Event
import io.worxbend.tui.terminal.BackendError

import scala.concurrent.duration.Duration

/** Runner configuration: an optional tick rate (synthetic [[Event.Tick]]s for animation) and whether to capture mouse
  * events.
  */
final case class RunnerConfig(
    tickRate: Option[Duration] = None,
    mouseCapture: Boolean = false,
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

enum RunnerError:
  case Backend(error: BackendError)
