package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent, KeyModifiers}
import io.worxbend.tui.runtime.{ReactiveScope, RunnerConfig, RunnerError, RunnerHandle, TerminalRunner}
import io.worxbend.tui.terminal.{Backend, JLine3Backend}

import java.util.concurrent.atomic.AtomicReference

/** The application entry point for the declarative DSL (SPEC.md §5.3).
  *
  * `view` is re-evaluated under a tracking [[ReactiveScope]]: any `Signal` read during the last evaluation
  * schedules a redraw when it changes — state lives in signals, not in an explicitly threaded `State` value.
  * Key/mouse events route through the element tree's handlers (innermost first, `true` consumes); an
  * unconsumed `Ctrl+C` quits. Call [[quit]] from any handler to exit cleanly.
  */
trait TuiApp:

  def view(using ReactiveScope): Element

  def config: RunnerConfig = RunnerConfig()

  /** Runs on the process's controlling terminal. Blocks until the app quits. */
  final def run(): Either[RunnerError, Unit] =
    JLine3Backend.create() match
      case Left(error)    => Left(RunnerError.Backend(error))
      case Right(backend) => runWith(backend)

  /** Runs over an explicit backend — how headless tests drive a `TuiApp` (recorded in SPEC.md §9). */
  final def runWith(backend: Backend): Either[RunnerError, Unit] =
    var invalidated = false
    var lastTree: Option[Element] = None
    val scope = ReactiveScope.onInvalidation(() => invalidated = true)

    def handleEvent(event: Event, handle: RunnerHandle): Boolean =
      activeHandle.set(Some(handle))
      event match
        case Event.Key(key) =>
          val consumed = lastTree.exists(EventRouter.dispatchKey(_, key))
          if !consumed && isCtrlC(key) then handle.quit()
          invalidated
        case Event.Mouse(mouse) =>
          val _ = lastTree.exists(EventRouter.dispatchMouse(_, mouse))
          invalidated
        case Event.Resize(_) => true
        case Event.Tick      => invalidated

    val result = TerminalRunner(backend, config).run(
      handleEvent,
      frame =>
        invalidated = false
        val tree = view(using scope)
        lastTree = Some(tree)
        frame.renderWidget(tree.widget, frame.area),
    )
    activeHandle.set(None)
    result

  /** Requests a clean exit; safe to call from event handlers. No-op when the app is not running. */
  protected final def quit(): Unit =
    activeHandle.get().foreach(_.quit())

  private def isCtrlC(key: KeyEvent): Boolean =
    key.code == KeyCode.Char('c') && key.modifiers.has(KeyModifiers.Ctrl)

  private val activeHandle = AtomicReference[Option[RunnerHandle]](None)
