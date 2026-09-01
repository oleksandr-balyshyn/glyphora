package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Effect, Event, KeyCode, KeyEvent, KeyModifiers, Size, Widget}
import io.worxbend.tui.runtime.{
  Async,
  Cancelable,
  EventOutcome,
  Frame,
  GenerationalScope,
  ReactiveScope,
  RenderThread,
  RunnerConfig,
  RunnerError,
  RunnerHandle,
  Signal,
  TerminalRunner,
}
import io.worxbend.tui.terminal.{Backend, BackendError, ColorDepth, JLine3Backend}
import io.worxbend.tui.widgets.NoticeLevel

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** How long a toast lives when the caller does not say. */
private val DefaultToastDuration: FiniteDuration = 3.seconds

/** How many rounds of portal draining one frame allows — see `TuiApp.drainPortals`. */
private val MaxPortalRounds: Int = 8

/** The mutable state of a single [[TuiApp.runWith]] invocation: whether a redraw is pending, the focus-decorated tree
  * the last frame produced (events are routed against that tree, not against a freshly evaluated one), the focus
  * tracker, and the intro player.
  *
  * Owned by one `runWith` call and touched only on the render thread — the event callbacks, the render lambda, and the
  * app's own hooks all run there, so none of these fields is synchronised. The splash player lives here rather than on
  * the app because an intro belongs to a run: running the same app twice plays it twice.
  */
private final class RunState(val splash: SplashPlayer):
  var invalidated: Boolean = false

  /** The render-and-dispatch engine this run drives. `TuiApp` is that engine plus this file's policy — Tab traversal,
    * Ctrl+P, Ctrl+C, screens, toasts, the splash — so the focus bookkeeping and the tree events are routed against live
    * in [[ElementHost]] and are reached through here.
    */
  val host: ElementHost = ElementHost()

  /** This run's focus tracker, which is the host's. Named here because the layer bookkeeping below and the imperative
    * `focusTo`/`clearFocus` helpers both work directly against it.
    */
  def tracker: FocusTracker = host.tracker

  /** The state `useSignal`/`useState` hand out, keyed by where in the view the call was made. Owned by this run and
    * touched only while its render thread is evaluating the view, like everything else here — running the same app a
    * second time therefore starts every component-local value fresh.
    */
  val viewState: ViewState = ViewState()

  /** This run's render loop, captured in `onStart` while it is still registered, so the exit path can hand the
    * [[AnimationClock]] entry back. `None` until then, and for a run whose `onStart` never happened.
    */
  var renderLoop: Option[RenderThread.RenderLoop] = None

  /** How many layers covered the app's own view when the last frame was composed — pushed screens plus the command
    * palette — and which screen was on top. Compared against the current state on every frame to decide whether focus
    * should move into an incoming layer or back out of one that has gone; see `TuiApp.syncFocusLayers`.
    */
  var layerCount: Int           = 0
  var topScreen: Option[Screen] = None

  /** Whether the runner itself is producing ticks for this run — that is, whether the app configured a `tickRate`. When
    * it is, the ambient ticker below stays out of the way entirely and nothing about ticking changes.
    */
  var runnerTicks: Boolean = false

  /** The repeating tick this run started for itself because the frame it composed contained an animation, and the
    * interval it is running at. Retargeted after every frame and cancelled on the way out; `None` means the app
    * currently owes no ticks at all, which is the state an app showing nothing animated sits in.
    */
  var ambientTicker: Option[Cancelable]       = None
  var ambientInterval: Option[FiniteDuration] = None

/** The application entry point for the declarative DSL.
  *
  * `view` is re-evaluated under a tracking [[ReactiveScope]]: any `Signal` read during the last evaluation schedules a
  * redraw when it changes — state lives in signals, not in an explicitly threaded `State` value.
  *
  * Focus and events: focusable elements form a tab order in depth-first view order; `Tab` / `Shift+Tab` cycle focus and
  * a mouse press focuses the innermost focusable under the pointer; [[focusTo]] and [[clearFocus]] move it from code.
  * Key events start at the focused element and bubble to its ancestors (`true` consumes), then the top screen's
  * `Screen.bindings` merged over the app's [[bindings]] run — see [[activeBindings]]; an unconsumed `Ctrl+P` opens the
  * command palette (when bindings exist) and `Ctrl+C` quits.
  *
  * App services: [[pushScreen]]/[[popScreen]]/[[replaceScreen]]/[[resetScreens]] for modal or full-screen navigation
  * (layers below a modal leave the tab order), with [[currentScreen]]/[[screenDepth]] as reactive reads of where
  * navigation stands, [[notify]] for timed toasts, [[openPalette]] for the fuzzy command palette over the declared
  * bindings. Call [[quit]] from any handler to exit cleanly.
  *
  * Lifecycle: [[onStart]] runs on the render thread before the first frame — start pollers and timers there — and
  * [[onStop]] runs on every exit path, which is where they are cancelled. A screen has the same pair of its own,
  * `Screen.onEnter`/`Screen.onLeave`, for work that belongs to a subtree rather than to the whole app. Between the two,
  * [[requestRedraw]] schedules a frame for state the reactive layer cannot see, such as a mutable widget state filled
  * in by a background result.
  *
  * Running it: the trait supplies `main`, so `object MyApp extends TuiApp` is already a runnable program. Reach for
  * [[run]] directly only when the app is started from code that owns the process (an existing `main`, a launcher), and
  * for [[runWith]] when a test drives it over a `HeadlessBackend`.
  */
trait TuiApp:

  /** The whole UI, as a function of current state. Re-evaluated whenever a `Signal` it read changes.
    *
    * Both contexts are supplied by the framework on every frame. The [[ReactiveScope]] is what makes a `Signal` read
    * inside the body subscribe the next redraw. The [[Theme]] is this app's own [[theme]], handed in so that a themed
    * helper called from here — `statusBar(bindings)`, `topBar(...)`, `panel(...)` — picks up the override rather than
    * the library default.
    */
  def view(using ReactiveScope, Theme): Element

  def config: RunnerConfig = RunnerConfig()

  /** The theme the built-in overlays (toasts, palette), the chrome presets and the themed element factories render
    * with. Overriding it re-themes all of them, because it is handed to [[view]] as a `using` parameter.
    *
    * Read once per frame, on the render thread, *outside* the tracking scope — so this is a constant for the lifetime
    * of a run unless something else asks for a repaint. An app that switches theme at runtime should back it with a
    * `Signal[Theme]` and read that signal inside `view`:
    *
    * {{{
    * private val palette = Signal(Theme.Dark)
    * override def theme: Theme = palette.peek
    * def view(using ReactiveScope, Theme): Element =
    *   given Theme = palette.get // tracked: flipping the signal repaints, and shadows the handed-in theme
    *   scaffold(statusBar = Some(statusBar(bindings)))(body)
    * }}}
    */
  def theme: Theme = Theme.Dark

  /** Called once on the render thread, after the terminal is ready and before the first frame is composed.
    *
    * This is the app's "now I am running" seam, and the only correct place to arm repeating background work. `Async`
    * captures the render loop of the thread that calls it, and an app instance is constructed long before any loop
    * exists — so `Async.every(...)` in a constructor or a field initialiser attaches to no loop and its results are
    * discarded forever. Started here, it lands on this run's loop.
    *
    * Calling [[quit]] from here exits before anything is drawn, which is how a start-up check ("no config file", "not a
    * TTY") declines to run at all. Whatever this starts should be stopped in [[onStop]].
    *
    * {{{
    * override def onStart(): Unit = poller = Some(Async.every(5.seconds)(refresh()))
    * override def onStop(): Unit  = poller.foreach(_.cancel())
    * }}}
    */
  def onStart(): Unit = ()

  /** Called once on the way out of [[runWith]], whatever ended the run: [[quit]], an unconsumed `Ctrl+C`, a backend
    * failure, or an event handler that threw. The place to cancel timers, stop pollers, and close whatever [[onStart]]
    * opened — nothing else cancels a repeating `Async.every` for you.
    *
    * By the time this runs the loop has exited and the terminal has been handed back, so the app's terminal services
    * ([[quit]], [[suspend]], [[printAbove]], [[copyToClipboard]]) are no-ops here; this hook is for the app's own
    * resources. It does not run when [[runWith]] was never reached — a [[createBackend]] that fails means there was no
    * run to stop.
    */
  def onStop(): Unit = ()

  /** Called on every synthetic tick (requires a `config.tickRate`), on the render thread — the place to advance
    * animation state via `Signal` updates.
    */
  def onTick(): Unit = ()

  /** Called when the terminal window gains or loses focus (terminals with mode-1004 reporting). */
  def onTerminalFocus(focused: Boolean): Unit = ()

  /** Called on the render thread whenever the terminal is resized, before the frame that reflects the new size.
    *
    * A redraw already happens on every resize, and [[terminalSize]] is already updated by the time this runs — this is
    * for the side effects a resize implies, like clamping a scroll offset or re-fetching a differently sized page.
    * Overriding it is not needed to make the view respond to size.
    */
  def onResize(size: Size): Unit = ()

  /** The terminal's current size, as a reactive read: a `view` that branches on it re-evaluates on every resize.
    *
    * {{{
    * def view(using ReactiveScope, Theme): Element =
    *   if terminalSize.width < 80 then column(header, tabbedContent(pages, active))
    *   else row(sidebar.percent(25), detail.fill)
    * }}}
    *
    * This is the whole-terminal size. To branch deeper in the tree without threading it through every builder, use
    * [[Element.responsive]], which resolves against the same size.
    */
  protected final def terminalSize(using scope: ReactiveScope): Size = terminalSizeSignal.get(using scope)

  /** The current [[Breakpoint]] band, as a reactive read — `terminalSize` bucketed by width. */
  protected final def breakpoint(using scope: ReactiveScope): Breakpoint = Breakpoint.of(terminalSize)

  /** Called when an interrupt (`Ctrl+C`, i.e. SIGINT/SIGQUIT) reaches the app.
    *
    * Return `true` to consume it and keep running — the place to ask "really quit?" or cancel in-flight work. The
    * default declines, so the app exits through its normal teardown and the terminal is restored.
    *
    * Note that `Ctrl+C` arrives here as a signal on most terminals, but as an ordinary `Ctrl+C` key event on terminals
    * that speak the kitty keyboard protocol (which reports the key instead of letting the line discipline raise a
    * signal). Both routes end in a clean quit; override this and the `Ctrl+C` binding together if you change that.
    */
  def onInterrupt(): Boolean = false

  /** The app's declared keys (see [[binding]]): consulted for any key event no element consumed, and the source for
    * `statusBar(bindings)` hints, [[helpOverlay]], and the command palette.
    */
  def bindings: KeyBindings = KeyBindings.empty

  /** The keys that will actually fire right now: the top screen's `Screen.bindings` followed by the app's own
    * [[bindings]] — as a reactive read, so a `view` that shows them recomputes when a screen is pushed or popped.
    *
    * This is what a `view` should hand to the chrome — `statusBar(activeBindings)`, `helpOverlay(activeBindings)` —
    * rather than [[bindings]]. Passing the app's own list advertises keys the screen on top may have shadowed and omits
    * the ones that screen added, so the hints disagree with what pressing them does.
    *
    * The screen comes first because `KeyBindings.handle` runs the first binding that matches: a screen key wins over an
    * app key that answers to the same spec, and the app's other keys are still there. When nothing is pushed, this is
    * exactly [[bindings]].
    */
  protected final def activeBindings(using scope: ReactiveScope): KeyBindings =
    merged(screenStack.get(using scope).headOption)

  /** [[activeBindings]] read without subscribing: the spelling for the event path, which must not add a dependency to
    * whatever view happens to be recomputing. Dispatch, the `Ctrl+P` gate and the command palette all read this one
    * accessor, so they cannot disagree about which keys exist.
    */
  private def activeBindingsNow: KeyBindings = merged(screenStack.peek.headOption)

  /** The screen's bindings followed by the app's, minus any app binding the screen has completely taken over.
    *
    * Dropping the shadowed ones matters because this one list feeds three different consumers. `KeyBindings.handle`
    * runs the first binding that matches, so an app binding every one of whose triggers the screen also declares can
    * never fire while that screen is up. Left in the list it would still be drawn as a status-bar hint and offered as a
    * command-palette row — advertising a key that does nothing, or worse, does the screen's thing instead. An app
    * binding that answers to two keys of which the screen claims only one survives, because its other key still works.
    */
  private def merged(top: Option[Screen]): KeyBindings =
    top.fold(bindings) { screen =>
      val claimed  = screen.bindings.bindings.flatMap(_.triggers).toSet
      // `triggers.nonEmpty &&` matters: `forall` on an empty sequence answers true, so a command with no keys at all
      // — a palette-only entry, reached by name through Ctrl+P and by nothing else — read as "the screen claimed
      // every one of its keys" and was dropped for as long as any screen was on the stack. A binding with no trigger
      // cannot be shadowed by anything, because there is nothing to shadow.
      val shadowed = (app: KeyBinding) => app.triggers.nonEmpty && app.triggers.forall(claimed.contains)
      screen.bindings ++ KeyBindings(bindings.bindings.filterNot(shadowed)*)
    }

  /** An intro screen shown before the first `view` render — see [[SplashScreen]]. Any key skips it. */
  def splash: Option[SplashScreen] = None

  // ---- services ----

  /** Starts a post-render [[Effect]] over the whole frame. Needs a `config.tickRate` to animate; the effect is dropped
    * once done.
    */
  protected final def runEffect(effect: Effect): Unit = effects.start(effect)

  /** Pushes a screen; modal screens layer over the current view, full screens replace it.
    *
    * The screen's `Screen.onEnter` runs immediately afterwards, on the render thread, before the frame that first shows
    * it — after the stack has been written, so a callback that reads [[screenDepthNow]] sees itself on it.
    */
  protected final def pushScreen(screen: Screen): Unit =
    screenStack.update(screen :: _)
    screen.onEnter()

  /** Pops the top screen and runs its `Screen.onLeave`. No-op on an empty stack. */
  protected final def popScreen(): Unit =
    val popped = screenStack.peek.headOption
    screenStack.update {
      case _ :: tail => tail
      case Nil       => Nil
    }
    popped.foreach(_.onLeave())

  /** Swaps the screen on top for `screen` in a single update.
    *
    * A `popScreen()` followed by a `pushScreen(...)` writes the stack twice, and anything that renders in between — a
    * redraw the pop itself asked for — briefly shows the layer underneath. This writes once, so the swap is one frame.
    * On an empty stack it does the same thing as [[pushScreen]].
    */
  protected final def replaceScreen(screen: Screen): Unit =
    val replaced = screenStack.peek.headOption
    screenStack.update {
      case _ :: tail => screen :: tail
      case Nil       => screen :: Nil
    }
    replaced.foreach(_.onLeave())
    screen.onEnter()

  /** Unwinds every pushed screen at once, so the app's own [[view]] is what shows again.
    *
    * The one-call way out of a deep drill-down ("home" from four levels in). Already-empty is a silent no-op: a
    * `Signal` set to a value equal to the one it holds notifies nobody, so no redundant frame is scheduled.
    *
    * Every unwound screen's `Screen.onLeave` runs, innermost first — the same order as popping them one at a time.
    */
  protected final def resetScreens(): Unit =
    val unwound = screenStack.peek
    screenStack.set(Nil)
    unwound.foreach(_.onLeave())

  /** The screen on top, as a reactive read — `None` means the app's own [[view]] is showing.
    *
    * Reading it from `view` subscribes that view to navigation, so a breadcrumb or a title bar recomputes when a screen
    * is pushed or popped.
    */
  protected final def currentScreen(using scope: ReactiveScope): Option[Screen] =
    screenStack.get(using scope).headOption

  /** How many screens are stacked over the app's own [[view]] — `0` when none are — as a reactive read.
    *
    * Use this from `view` (a breadcrumb that shows the depth). An event handler has no [[ReactiveScope]] and must not
    * subscribe anything anyway, so it reads [[screenDepthNow]] instead.
    */
  protected final def screenDepth(using scope: ReactiveScope): Int =
    screenStack.get(using scope).size

  /** The names of the screens on the stack, outermost first — the sequence a breadcrumb wants — as a reactive read.
    *
    * A screen with no `Screen.label` contributes nothing rather than a blank, so a dialog that never wanted a name does
    * not open a gap in the trail. The app's own [[view]] is not in this list: it is the thing everything else is
    * stacked *on*, and only the application knows what to call it.
    *
    * {{{
    * text(("Home" +: screenLabels).mkString(" > "))
    * }}}
    *
    * There is deliberately no accessor for the screens themselves. Handing out the list would publish the order the
    * stack happens to be stored in, and every use for it that came up — a breadcrumb, a title bar — wants the names.
    */
  protected final def screenLabels(using scope: ReactiveScope): Seq[String] =
    screenStack.get(using scope).reverse.flatMap(_.label)

  /** [[screenDepth]] read without subscribing — the spelling for an event handler, which has no [[ReactiveScope]].
    *
    * What it is for: one global `Esc` binding that means "go back a level" while anything is pushed and "quit" at the
    * top, without the app keeping a parallel counter of its own.
    *
    * {{{
    * binding("esc", "back")(if screenDepthNow > 0 then popScreen() else quit())
    * }}}
    */
  protected final def screenDepthNow: Int = screenStack.peek.size

  /** Shows a toast in the top-right corner for `duration` (needs a `config.tickRate` for it to age out again). */
  protected final def notify(
      message: String,
      level: NoticeLevel = NoticeLevel.Info,
      duration: FiniteDuration = DefaultToastDuration,
  ): Unit =
    toasts.push(message, level, duration)

  protected final def dismissToasts(): Unit = toasts.dismissAll()

  /** This app's cross-cutting services as a value, so helpers written outside the app's own body can use them.
    *
    * Every service — [[notify]], [[pushScreen]], [[quit]] and the rest — is a `protected` method, which means only code
    * inside the app can call it. Handing this out instead lets a helper anywhere take `(using AppServices)` and
    * navigate, notify or ask for a repaint, with no callbacks threaded down from the app. Each member delegates to the
    * app method of the same name, so there is exactly one implementation of every behaviour.
    *
    * It is published as a `given` below, so a call made from any method of this app resolves it with nothing to write;
    * pass it explicitly when the call site is not lexically inside the app. `AppServices` extends [[Notifications]], so
    * a helper that only needs to raise a toast can ask for that narrower type and still be satisfied by this.
    *
    * {{{
    * // in some other file
    * def saveRow(row: Row)(using n: Notifications): Unit = { repository.save(row); n.success("saved") }
    *
    * // in the app
    * binding("ctrl+s", "save")(saveRow(selected))   // resolves `services` on its own
    * }}}
    */
  protected final lazy val services: AppServices = new AppServices:
    def notify(message: String, level: NoticeLevel, duration: FiniteDuration): Unit =
      TuiApp.this.notify(message, level, duration)
    def dismissToasts(): Unit                                                       = TuiApp.this.dismissToasts()
    def pushScreen(screen: Screen): Unit                                            = TuiApp.this.pushScreen(screen)
    def popScreen(): Unit                                                           = TuiApp.this.popScreen()
    def openPalette(): Unit                                                         = TuiApp.this.openPalette()
    def closePalette(): Unit                                                        = TuiApp.this.closePalette()
    def runEffect(effect: Effect): Unit                                             = TuiApp.this.runEffect(effect)
    def requestRedraw(): Unit                                                       = TuiApp.this.requestRedraw()
    def copyToClipboard(text: String): Unit                                         = TuiApp.this.copyToClipboard(text)
    def quit(): Unit                                                                = TuiApp.this.quit()

  /** Just this app's toast stack, for a helper that notifies and does nothing else. The same object as [[services]]. */
  protected final def notifications: Notifications = services

  protected final given AppServices = services

  /** Opens the fuzzy command palette over the declared [[bindings]]. */
  protected final def openPalette(): Unit = palette.open()

  protected final def closePalette(): Unit = palette.close()

  /** Requests a clean exit; safe to call from event handlers. No-op when the app is not running. */
  protected final def quit(): Unit =
    activeHandle.get().foreach(_.quit())

  /** Schedules one frame. No-op when the app is not running.
    *
    * Most state does not need this. A `Signal` write already invalidates the view that read it, and an event handler
    * that returns `true` already asks for a repaint. What needs it is state the reactive layer cannot see, mutated
    * outside the event path: the caller-owned widget states — `ListState`, `TextInputState`, `DataTableState`,
    * `LogState`, `TreeState` — are plain mutable objects, so
    *
    * {{{
    * Async.run(fetchRows())(rows => tableState.rows = rows)
    * }}}
    *
    * updates the table on the render thread correctly and yet leaves the previous frame on screen until the user
    * happens to press a key. Ask for the frame the mutation earned:
    *
    * {{{
    * Async.run(fetchRows()) { rows =>
    *   tableState.rows = rows
    *   requestRedraw()
    * }
    * }}}
    *
    * Call it on the render thread — which every `Async` continuation, timer body and event handler already is.
    */
  protected final def requestRedraw(): Unit =
    activeHandle.get().foreach(_.requestRedraw())

  /** Moves keyboard focus to the focusable declared with `.key(name)`, and asks for the frame that shows it.
    *
    * This is the imperative half of focus, next to the `Tab` traversal and click-to-focus the framework does by itself.
    * What needs it: a form that failed validation putting the cursor back on the offending field, a list row selection
    * handing focus to the detail pane beside it, a dialog that opens with the cursor already in its search box.
    *
    * {{{
    * input(email).key("email")                     // in the view
    * if !emailIsValid then focusTo("email")        // in a handler
    * }}}
    *
    * Answers `false`, and changes nothing, when no focusable with that key was in the frame the app last rendered — the
    * element lives in a branch the view did not render, the key is misspelt, or the app is not running. The key is then
    * *remembered*, so focus follows that element across later renders even when the tree changes shape.
    *
    * Call it on the render thread, which every event handler, timer body and `Async` continuation already is.
    */
  protected final def focusTo(key: String): Boolean =
    activeFocus.get().exists { tracker =>
      val moved = tracker.focusToKey(key)
      if moved then requestRedraw()
      moved
    }

  /** Drops focus entirely: no element renders focused, and keys go past the tree straight to the app's [[bindings]]
    * until `Tab`, a mouse press or [[focusTo]] puts focus back. `Tab` from here lands on the first focusable and
    * `Shift+Tab` on the last.
    *
    * Useful when a view enters a mode where element-level keys would be in the way — a "press any key to continue"
    * state, or a global search overlay whose keys must not be eaten by whatever happened to be focused. No-op when the
    * app is not running.
    */
  protected final def clearFocus(): Unit =
    activeFocus.get().foreach { tracker =>
      tracker.clearFocus()
      requestRedraw()
    }

  /** The `.key(name)` of the element that currently holds focus — `None` when it is unkeyed, nothing is focusable,
    * focus was cleared, or the app is not running. Read without subscribing, for an event handler.
    */
  protected final def focusedKey: Option[String] =
    activeFocus.get().flatMap(_.focusedKey)

  /** Copies `text` to the system clipboard via OSC 52. Best-effort — terminals without OSC 52 support ignore it, and it
    * is a no-op when the app is not running.
    */
  protected final def copyToClipboard(text: String): Unit =
    activeHandle.get().foreach(_.copyToClipboard(text))

  /** Hands the terminal to `body` — leaving the app's alternate screen and raw mode — then restores the UI and forces a
    * full repaint. The place to launch an external program like `$EDITOR` or a shell. Call from an event handler.
    */
  protected final def suspend(body: => Unit): Unit =
    activeHandle.get().foreach(_.suspend(body))

  /** Prints `lines` into the terminal scrollback above the live UI (durable after the app exits) — a `tea.Println`
    * equivalent for surfacing occasional log output. Call from an event handler.
    */
  protected final def printAbove(lines: String*): Unit =
    activeHandle.get().foreach(_.printAbove(lines))

  /** [[printAbove]] with styling: draws a block `height` rows tall and inserts *that* into the scrollback above the
    * live UI, so a durable line can carry colour, bold and hyperlinks.
    *
    * `printAbove` emits plain text — the backend strips control sequences out of what it is given, which is the right
    * thing to do with strings of unknown provenance and also means an inserted line can never be styled. Here the app
    * draws instead: `widget` receives a buffer as wide as the terminal and `height` rows tall, and whatever it paints
    * becomes permanent output above the UI. Call it from an event handler, like `printAbove`.
    *
    * Curried so the call site reads as a block:
    * {{{
    * insertBefore(1) { (area, buffer) =>
    *   buffer.setString(area.x, area.y, "done: config written", Style.Default.withFg(Color.Green).bold)
    * }
    * }}}
    *
    * A `height` of zero or less inserts nothing.
    */
  protected final def insertBefore(height: Int)(widget: Widget): Unit =
    activeHandle.get().foreach(_.insertBefore(height, widget))

  // ---- entry points ----

  /** The program entry point, so `object MyApp extends TuiApp` runs as-is — no hand-written `main` needed.
    *
    * A failed run reports on standard error and exits non-zero: standard *output* is where the UI was just drawn, and a
    * process that could not start its own terminal has not succeeded.
    */
  final def main(args: Array[String]): Unit =
    val _ = args // the shell hands these over; a TuiApp reads its own configuration from its members
    run() match
      case Right(_)    => ()
      case Left(error) =>
        System.err.println(s"glyphora: ${error.message}")
        sys.exit(1)

  /** How many colors [[createBackend]] tells the default JLine backend it may use.
    *
    * Defaults to sniffing the environment (`NO_COLOR`, `CLICOLOR_FORCE`, `COLORTERM`, `TERM`). Override it to pin a
    * palette — `ColorDepth.Ansi16` is how you check that a theme still reads on a sixteen-color terminal without
    * finding one. Read once per [[run]], on the thread that called it.
    */
  protected def colorDepth: ColorDepth = ColorDepth.detect()

  /** Opens the terminal [[run]] will draw on. JLine on the process's controlling terminal by default.
    *
    * Override it to substitute another [[Backend]] — a recording backend, a remote one, a `HeadlessBackend` wired to
    * something other than a test — and the app keeps its `main` and its `run()`. Everything else about the app is
    * unchanged, because [[runWith]] is what actually runs the loop and it takes whatever backend it is handed. Called
    * once per `run()`, before any rendering starts.
    */
  protected def createBackend(): Either[BackendError, Backend] = JLine3Backend.create(colorDepth = colorDepth)

  /** Runs on the terminal [[createBackend]] opens. Blocks until the app quits. */
  final def run(): Either[RunnerError, Unit] =
    createBackend() match
      case Left(error)    => Left(RunnerError.Backend(error))
      case Right(backend) => runWith(backend)

  /** Runs over an explicit backend — how headless tests drive a `TuiApp`.
    *
    * Everything this sets up lives in one [[RunState]], and every stage below is handed that state explicitly rather
    * than closing over it, so the four-stage key precedence the trait's Scaladoc promises is four named methods.
    *
    * The lifetime of one run, in order: [[onStart]] on the render thread, then frames and events until something ends
    * the loop, then [[onStop]] — in a `finally`, so a backend failure or a handler that threw does not skip it.
    */
  final def runWith(backend: Backend): Either[RunnerError, Unit] =
    val intro           = splash
    val run             = RunState(SplashPlayer(intro, () => System.nanoTime()))
    val scope           = ReactiveScope.generational(() => run.invalidated = true)
    val effectiveConfig =
      if intro.nonEmpty && config.tickRate.isEmpty then config.copy(tickRate = Some(SplashPlayer.TickRate))
      else config
    run.runnerTicks = effectiveConfig.tickRate.isDefined
    try
      TerminalRunner(backend, effectiveConfig, redrawRequested = () => run.invalidated).run(
        handle =>
          activeHandle.set(Some(handle))
          // published for the same reason and with the same lifetime as the handle: `focusTo`/`clearFocus` are called
          // from app code that cannot see this run's `RunState`, and must be inert once the run is over
          activeFocus.set(Some(run.tracker))
          // on the render thread, and while this run is still registered on it: the clock this app's animations read
          // is the one belonging to this loop, and the exit path below can no longer resolve it for itself
          run.renderLoop = Some(AnimationClock.attachToCurrentLoop())
          onStart()
        ,
        handleEvent(_, run, _),
        frame => renderFrame(frame, run, scope),
      )
    finally
      // dropped *before* `onStop`, so the terminal services an app might reach for during teardown are inert rather
      // than talking to a backend the runner has already closed
      activeHandle.set(None)
      activeFocus.set(None)
      // this run started it, so this run stops it: an uncancelled `Async.every` is a process-lifetime daemon
      run.ambientTicker.foreach(_.cancel())
      run.ambientTicker = None
      run.ambientInterval = None
      // a screen still on the stack when the run ends gets its `onLeave`, innermost first, *before* the app's own
      // `onStop`: a screen's cleanup runs while whatever the app opened for it is still there, and every `onEnter` on
      // every exit path — a quit, a Ctrl+C, a handler that threw — is matched by exactly one `onLeave`
      leaveRemainingScreens()
      onStop()
      // after `onStop`, so an app cancelling timers there still animates whatever its last frames show
      run.renderLoop.foreach(AnimationClock.releaseLoop)

  /** Runs `Screen.onLeave` for everything still on the stack when a run ends, innermost first, so every `onEnter` is
    * matched exactly once however the run finished.
    *
    * It deliberately does *not* clear the stack, which is why this is not simply [[resetScreens]]. By the time the
    * `finally` runs, the runner has already handed its render loop back, so this thread is no longer a render thread —
    * and a `Signal` write from here throws the render-thread guard whenever some other runner in the process is still
    * registered. Leaving the value alone also matches every other piece of per-instance state, none of which `runWith`
    * resets: running the same instance a second time keeps whatever the first run left behind.
    */
  private def leaveRemainingScreens(): Unit =
    screenStack.peek.foreach(_.onLeave())

  // ---- the loop ----

  /** Composes one frame: publish the size the view branches on, then either the intro or the reconciled view tree. */
  private def renderFrame(frame: Frame, run: RunState, scope: GenerationalScope): Unit =
    // the frame's area is what is actually about to be painted, so it — not the last resize event — is the size
    // the view branches on. Published before `invalidated` is cleared: the write invalidates the *previous*
    // generation's subscribers, and letting that survive into this frame would schedule a redundant redraw.
    val frameSize = Size(frame.area.width, frame.area.height)
    terminalSizeSignal.set(frameSize)
    run.invalidated = false
    if run.splash.isActive then run.splash.render(frame)
    else
      scope.beginGeneration()
      // the demand is rebuilt from what *this* frame renders, so an animation that has just gone off screen stops
      // costing ticks as soon as the frame without it is composed
      AnimationClock.beginFrame()
      // the view is evaluated exactly once per frame, which is what lets `useSignal`/`useState` identify a piece of
      // state by the order its call is reached in. `sweep` runs after the responsive pass, so a hook reached only while
      // resolving a `responsive(...)` branch still counts as visited and keeps its slot.
      run.viewState.beginGeneration()
      val rawTree =
        ViewState.during(run.viewState)(ResponsivePass.resolve(effectiveView(using scope), frameSize))
      run.viewState.sweep()
      syncFocusLayers(run)
      // portals are collected while the tree paints and drawn afterwards, so a popup anchored deep inside a bordered
      // pane escapes it. Post-render effects still come last: they process the finished frame, and a portal is frame
      // content like anything else.
      PortalQueue.begin()
      try
        run.host.renderTree(rawTree, theme.focus, tree => frame.renderWidget(tree.widget, frame.area))
        drainPortals(frame)
      finally PortalQueue.end()
      effects.applyTo(frame)
      retargetAmbientTicker(run)

  /** Draws the portals the tree queued, then the portals *those* queued, and so on until nothing new arrives.
    *
    * Each portal is drawn at its own absolute rectangle, intersected with the frame — the terminal is the only thing
    * that clips a portal. Rendering portal content can queue further portals (a submenu opened from a menu that is
    * itself in a portal), which is why this loops instead of draining once.
    *
    * The round cap stops content that re-queues a portal on every pass from spinning the render thread forever. It is
    * deliberately generous: real nesting is a menu inside a dialog inside a screen, never eight deep.
    */
  private def drainPortals(frame: Frame): Unit =
    var round  = 0
    var queued = PortalQueue.drain()
    while queued.nonEmpty && round < MaxPortalRounds do
      queued.foreach { (target, content) =>
        // Rendered at its own rectangle, not at the intersection with the screen. `Buffer.set` already drops a cell
        // outside the frame, so handing the content its full rectangle clips it and nothing more. Handing it the
        // intersection instead *moved* it: a portal starting five columns left of the screen has an intersection whose
        // origin is column 0, so its content was laid out from there and the user saw the portal's first five columns
        // where its sixth through tenth belonged. Off the right or the bottom the two agree, which is why only the
        // left and top edges ever showed it.
        if !target.intersection(frame.area).isEmpty then frame.renderWidget(content.widget, target)
      }
      queued = PortalQueue.drain()
      round += 1

  /** Turns this run's own repeating tick on, off, or onto a different interval, according to what the frame just
    * composed actually needs.
    *
    * Why this exists: the ambient [[AnimationClock]] is what makes `spinner`, `marquee`, `indeterminateBar` and a timed
    * `Effect` animate with no counter to thread through — but the ticks that advance it used to come only from a
    * globally configured `config.tickRate`. An app that rendered a spinner and set no tick rate got a frozen spinner
    * and no diagnostic at all. Meanwhile an app that *did* set one paid for a tick every interval forever, whether or
    * not anything on screen was moving.
    *
    * Both are the same missing negotiation, and the render pass already knows the answer: every ambient animation reads
    * the clock, and each read says how often it needs one. So after each frame this asks for the shortest interval
    * anything on it wanted — plus a tick for the live toasts and post-render effects, which age in wall-clock time and
    * are not part of the tree — and retargets a single `Async.every`. Nothing animated on the frame means no ticker at
    * all.
    *
    * An app that configured a `tickRate` keeps being driven by the runner, exactly as before: this does nothing at all
    * for such a run, so `onTick` and the frame cadence of every existing app are untouched. `onTick` is deliberately
    * *not* called from the ambient tick either — it is documented as requiring a `config.tickRate`, and an app that
    * never asked for ticks should not suddenly start receiving them.
    *
    * Runs on the render thread, at the end of the render pass, which is where `Async.every` must be armed for its body
    * to come back to this loop.
    *
    * One limit worth knowing. A tick arrives as a task queued back to the render loop, and a loop with no configured
    * `tickRate` blocks on input for up to 100ms between draining that queue — so an ambient animation advances at that
    * granularity however short an interval it asked for. That is the difference between "the spinner spins" and "the
    * spinner is perfectly smooth"; an app that wants the second still sets `config.tickRate`.
    */
  private def retargetAmbientTicker(run: RunState): Unit =
    if !run.runnerTicks then
      // the toasts and the post-render effects age in wall-clock time and are not part of the tree, so they ask for
      // ticks separately; the ticker runs at whichever of the two demands is the shorter
      val ageing = if !effects.isEmpty || toasts.isLive then Some(AnimationClock.DefaultInterval) else None
      val wanted = (AnimationClock.frameDemand.toList ++ ageing.toList).minOption
      if wanted != run.ambientInterval then
        run.ambientTicker.foreach(_.cancel())
        run.ambientInterval = wanted
        run.ambientTicker = wanted.map(interval => Async.every(interval)(ambientTick()))

  /** One ambient tick: advance the clock, age the toasts and the post-render effects, and ask for the frame that shows
    * the result.
    *
    * The redraw is unconditional rather than conditional on something having changed, because the whole reason this
    * ticker is running is that the last frame contained an animation whose next position is a function of the clock
    * alone — and a `Signal` set to an equal value notifies nobody, so an "only if something changed" test would leave
    * that animation frozen between the two frames where its glyph happens to repeat.
    */
  private def ambientTick(): Unit =
    AnimationClock.advanceUnlessPinned()
    toasts.age()
    val _ = effects.prune()
    requestRedraw()

  /** Moves focus into a layer that has just appeared, or back out of one that has gone, before the frame is reconciled.
    *
    * A layer is a pushed [[Screen]] or the open command palette. Both remove everything beneath them from the tab
    * order, so the set of focusables changes completely — and `reconcile` on its own would merely clamp the old index
    * into the new, much shorter range. Opening a three-field dialog while the app's fifth control was focused would
    * land the cursor on the dialog's *last* field rather than its first, and closing it again would leave focus
    * wherever the clamp had dropped it rather than where the user left it.
    *
    * The count is compared rather than each navigation call announcing itself, because the layers do not all go through
    * this trait: the command palette closes itself from inside its own key handler. A comparison cannot get out of step
    * with the truth the way a queue of announcements can. The screen on top is compared as well, so [[replaceScreen]] —
    * which swaps a layer without changing the count — also starts the incoming screen at its first control.
    *
    * Reads through `peek` rather than `get`: this is bookkeeping about the frame, and subscribing it would make every
    * frame depend on the navigation signal whether or not the view looked at it.
    */
  private def syncFocusLayers(run: RunState): Unit =
    val screens = screenStack.peek
    val layers  = screens.size + (if palette.isOpenNow then 1 else 0)
    val top     = screens.headOption
    // reference identity, not `==`: two screens can be equal values and still be different pushes
    val sameTop = (top, run.topScreen) match
      case (Some(now), Some(before)) => now eq before
      case (None, None)              => true
      case _                         => false
    // a swap at the same depth: the layer that was covering the view has gone and a different one has taken over
    if layers == run.layerCount && layers > 0 && !sameTop then
      run.tracker.popLayer()
      run.tracker.pushLayer()
    else
      var count = run.layerCount
      while count < layers do
        run.tracker.pushLayer()
        count += 1
      while count > layers do
        run.tracker.popLayer()
        count -= 1
    run.layerCount = layers
    run.topScreen = top

  /** The runner's single event entry point: dispatches one event and answers whether the frame must be redrawn.
    *
    * "Redrawn" is the union of two independent reasons: an element or binding consumed the event and said so, or the
    * dispatch wrote a `Signal` the last view read, which `run.invalidated` records. Either one owes a frame.
    *
    * [[Event.Interrupt]] is the exception, and deliberately does not fold `invalidated` in. There the runner reads the
    * answer as "did the app consume Ctrl+C?", so a signal that happened to change while declining the interrupt must
    * not be mistaken for consuming it — that would leave Ctrl+C not quitting. See [[EventOutcome]].
    */
  private def handleEvent(event: Event, run: RunState, handle: RunnerHandle): EventOutcome =
    activeHandle.set(Some(handle))
    val redraw = event match
      case Event.Key(key)        => handleKey(key, run, handle) || run.invalidated
      case Event.Mouse(mouse)    => run.host.dispatchMouse(mouse) || run.invalidated
      // Releases reach the focused element's own `.onKeyRelease` and stop there. They deliberately do not bubble on
      // to `bindings`: a binding names a press, so firing it again on the way up would run every chord twice.
      case Event.KeyRelease(key) => run.host.dispatchKeyRelease(key) || run.invalidated
      case Event.Paste(text)     =>
        val consumed = run.host.dispatchPaste(text)
        consumed || run.invalidated
      case Event.FocusGained     =>
        onTerminalFocus(true)
        run.invalidated
      case Event.FocusLost       =>
        onTerminalFocus(false)
        run.invalidated
      case Event.Interrupt       => onInterrupt()
      case Event.Resize(size)    =>
        // the render pass sets this too, from the frame it is about to draw; doing it here as well means an
        // `onResize` override — and anything it calls — already peeks the new size rather than the previous frame's
        terminalSizeSignal.set(size)
        onResize(size)
        true
      case Event.Tick            => handleTick(run)
    if redraw then EventOutcome.Redraw else EventOutcome.Ignored

  private def handleTick(run: RunState): Boolean =
    // before user code, so an `onTick` that reads the clock sees this tick's value rather than the last one's
    AnimationClock.advance()
    toasts.age()
    onTick()
    val splashJustFinished  = run.splash.advance()
    val effectsJustFinished = effects.prune()
    run.invalidated || !effects.isEmpty || run.splash.isActive || splashJustFinished || effectsJustFinished

  private def handleKey(key: KeyEvent, run: RunState, handle: RunnerHandle): Boolean =
    if run.splash.isActive then
      run.splash.skip() // any key skips the intro; it never reaches the view
      true
    else routeKey(key, run, handle)

  /** Focused element first, then its ancestors, then the top screen's bindings merged over the app's, then the
    * framework's own keys.
    */
  private def routeKey(key: KeyEvent, run: RunState, handle: RunnerHandle): Boolean =
    val consumed = run.host.dispatchKey(key)
    val bound    = !consumed && !palette.isOpenNow && activeBindingsNow.handle(key)
    if consumed || bound then true else handleFrameworkKey(key, run.tracker, handle)

  /** The last stage: the keys the framework reserves for itself once nothing else claimed the event — `Tab` /
    * `Shift+Tab` focus traversal, `Ctrl+P` for the command palette, `Ctrl+C` to quit, and `Esc` when the modal on top
    * of the stack asked to be closed by it (see `Screen.dismissal`).
    *
    * `Esc` is handled here, last, and not earlier, so that an element or a key binding that wants `Esc` for itself
    * still gets it: a text field leaving its editing mode consumes the key long before the event reaches this method.
    *
    * Answers `true` only when focus actually moved, because that is the one outcome here that changes the next frame
    * without going through a signal; opening the palette and quitting each schedule their own redraw.
    */
  private def handleFrameworkKey(key: KeyEvent, tracker: FocusTracker, handle: RunnerHandle): Boolean =
    key match
      case KeyEvent(KeyCode.Tab, modifiers) if modifiers.hasAny(KeyModifiers.Shift)      => tracker.focusPrevious()
      case KeyEvent(KeyCode.Tab, _)                                                      => tracker.focusNext()
      case KeyEvent(KeyCode.Char('p'), modifiers)
          if modifiers.hasAny(KeyModifiers.Ctrl) && activeBindingsNow.bindings.nonEmpty && !palette.isOpenNow =>
        openPalette()
        false
      case KeyEvent(KeyCode.Char('c'), modifiers) if modifiers.hasAny(KeyModifiers.Ctrl) =>
        handle.quit()
        false
      case KeyEvent(KeyCode.Escape, _) if topScreenClosesOnEscape                        =>
        popScreen()
        false
      case _                                                                             => false

  /** Whether the screen on top of the stack is a modal that asked to be closed by `Esc`.
    *
    * Reads the stack with `peek` rather than `get`: this runs from the event loop, not from a view evaluation, so
    * subscribing here would attach a dependency to whatever view happened to be recomputing.
    */
  private def topScreenClosesOnEscape: Boolean =
    screenStack.peek.headOption
      .exists(screen => screen.presentation == Presentation.Modal && screen.dismissal.byEscape)

  /** The composed view: base -> screens -> palette -> toasts.
    *
    * The theme is resolved once here and passed *into* `view` and every screen's `view` as a `using` argument, not
    * merely installed as a given around the call — a given installed here is lexically scoped to this method and would
    * not be in scope inside an app's own `view` body.
    */
  private def effectiveView(using scope: ReactiveScope): Element =
    given Theme     = theme
    val withScreens = screenStack.get.reverse.foldLeft(view) { (below, screen) =>
      screen.presentation match
        case Presentation.Modal =>
          // a modal that closes on a click outside is wrapped in the backdrop that notices such a click; the layer
          // underneath stays inert either way, so nothing down there sees the press
          val top =
            if screen.dismissal.byClickOutside then dismissibleOverlay(screen.view)(() => popScreen())
            else screen.view
          Element.layers(FocusPass.suppressFocus(below), top)
        case Presentation.Full  => screen.view
    }
    val withPalette =
      if palette.isOpen then Element.layers(FocusPass.suppressFocus(withScreens), palette.element)
      else withScreens
    toasts.overlay(terminalSizeSignal.peek.width) match
      case Some(stack) => Element.layers(withPalette, stack)
      case None        => withPalette

  // ---- per-instance state ----

  /** The size the last frame was drawn at. Zero until the first render publishes the real one — nothing can observe
    * that gap through [[terminalSize]], which is only readable from `view`.
    */
  private val terminalSizeSignal: Signal[Size] = Signal(Size(0, 0))

  // Everything below is per-instance state written only from the render thread (event handlers, the render lambda, and
  // the app's own callbacks all run there). None of it is reset by `runWith`, so running the same instance a second
  // time keeps whatever the first run left behind — a screen still on the stack, an effect still running.
  private val screenStack: Signal[List[Screen]] = Signal(Nil)
  private val toasts: ToastStack                = ToastStack()
  private val palette: CommandPalette           = CommandPalette(() => activeBindingsNow)
  private val effects: EffectStack              = EffectStack(() => System.nanoTime())
  private val activeHandle                      = AtomicReference[Option[RunnerHandle]](None)

  /** The running invocation's focus tracker, so [[focusTo]] and [[clearFocus]] can reach it. `None` outside a run,
    * which is what makes both of them no-ops rather than failures when the app is not running.
    */
  private val activeFocus = AtomicReference[Option[FocusTracker]](None)
