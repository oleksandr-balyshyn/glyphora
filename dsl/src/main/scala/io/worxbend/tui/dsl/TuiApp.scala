package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent, KeyModifiers, MouseEvent, MouseEventKind}
import io.worxbend.tui.runtime.{ReactiveScope, RunnerConfig, RunnerError, RunnerHandle, TerminalRunner}
import io.worxbend.tui.terminal.{Backend, JLine3Backend}

import java.util.concurrent.atomic.AtomicReference

/** The application entry point for the declarative DSL (SPEC.md §5.3).
  *
  * `view` is re-evaluated under a tracking [[ReactiveScope]]: any `Signal` read during the last evaluation schedules a
  * redraw when it changes — state lives in signals, not in an explicitly threaded `State` value.
  *
  * Focus and events (SPEC.md §5.4): focusable elements form a tab order in depth-first view order; `Tab` / `Shift+Tab`
  * cycle focus and a mouse press focuses the innermost focusable under the pointer. Key events start at the focused
  * element and bubble to its ancestors (`true` consumes); an unconsumed `Ctrl+C` quits. Call [[quit]] from any handler
  * to exit cleanly.
  */
trait TuiApp:

  def view(using ReactiveScope): Element

  def config: RunnerConfig = RunnerConfig()

  /** Called on every synthetic tick (requires a `config.tickRate`), on the render thread — the place to advance
    * animation state via `Signal` updates.
    */
  def onTick(): Unit = ()

  /** The app's declared keys (see [[binding]]): consulted for any key event no element consumed, and the
    * source for `statusBar(bindings)` hints and [[helpOverlay]].
    */
  def bindings: KeyBindings = KeyBindings.empty

  /** Runs on the process's controlling terminal. Blocks until the app quits. */
  final def run(): Either[RunnerError, Unit] =
    JLine3Backend.create() match
      case Left(error)    => Left(RunnerError.Backend(error))
      case Right(backend) => runWith(backend)

  /** Runs over an explicit backend — how headless tests drive a `TuiApp` (recorded in SPEC.md §9). */
  final def runWith(backend: Backend): Either[RunnerError, Unit] =
    var invalidated = false
    var lastTree: Option[Element] = None
    val tracker = FocusTracker()
    val scope = ReactiveScope.onInvalidation(() => invalidated = true)

    def handleKey(key: KeyEvent, handle: RunnerHandle): Boolean =
      val consumed = lastTree.exists(EventRouter.dispatchKey(_, key))
      val bound = !consumed && bindings.handle(key)
      var focusMoved = false
      if !consumed && !bound then
        key match
          case KeyEvent(KeyCode.Tab, modifiers) if modifiers.has(KeyModifiers.Shift) =>
            focusMoved = tracker.focusPrevious()
          case KeyEvent(KeyCode.Tab, _) =>
            focusMoved = tracker.focusNext()
          case KeyEvent(KeyCode.Char('c'), modifiers) if modifiers.has(KeyModifiers.Ctrl) =>
            handle.quit()
          case _ => ()
      consumed || bound || focusMoved

    def handleMouse(mouse: MouseEvent): Boolean =
      val focusMoved =
        if mouse.kind == MouseEventKind.Down then
          tracker.hitTest(mouse.x, mouse.y) match
            case Some(index) if index != tracker.focusedIndex =>
              tracker.focusedIndex = index
              true
            case _ => false
        else false
      val consumed = lastTree.exists(EventRouter.dispatchMouse(_, mouse))
      consumed || focusMoved

    def handleEvent(event: Event, handle: RunnerHandle): Boolean =
      activeHandle.set(Some(handle))
      event match
        case Event.Key(key)     => handleKey(key, handle) || invalidated
        case Event.Mouse(mouse) => handleMouse(mouse) || invalidated
        case Event.Resize(_)    => true
        case Event.Tick =>
          onTick()
          invalidated

    val result = TerminalRunner(backend, config).run(
      handleEvent,
      frame =>
        invalidated = false
        val rawTree = view(using scope)
        tracker.focusableCount = FocusPass.countFocusables(rawTree)
        if tracker.focusableCount > 0 then
          tracker.focusedIndex = math.max(0, math.min(tracker.focusedIndex, tracker.focusableCount - 1))
        else tracker.focusedIndex = 0
        tracker.clearAreas()
        val tree = FocusPass.decorate(rawTree, tracker)
        lastTree = Some(tree)
        frame.renderWidget(tree.widget, frame.area),
    )
    activeHandle.set(None)
    result

  /** Requests a clean exit; safe to call from event handlers. No-op when the app is not running. */
  protected final def quit(): Unit =
    activeHandle.get().foreach(_.quit())

  private val activeHandle = AtomicReference[Option[RunnerHandle]](None)
