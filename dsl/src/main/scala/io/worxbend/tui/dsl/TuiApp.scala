package io.worxbend.tui.dsl

import io.worxbend.tui.core.{CharWidth, Event, KeyCode, KeyEvent, KeyModifiers, MouseEvent, MouseEventKind, Size, Style}
import io.worxbend.tui.runtime.{
  Effect,
  Frame,
  GenerationalScope,
  ReactiveScope,
  RunnerConfig,
  RunnerError,
  RunnerHandle,
  Signal,
  TerminalRunner,
}
import io.worxbend.tui.terminal.{Backend, JLine3Backend}
import io.worxbend.tui.widgets.{NoticeLevel, TextInputState}

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}

/** Dimensions of the built-in command palette overlay: how wide the panel is, how many rows its chrome costs on top of
  * the matches (the panel border plus the filter input), and the height it stops growing at.
  */
private val PaletteWidth     = 46
private val PaletteChrome    = 4
private val PaletteMaxHeight = 14

/** How many toasts are drawn at once — older ones stay queued and appear as the visible ones age out. */
private val MaxVisibleToasts = 5

/** How long a toast lives, in ticks, when the caller does not say. */
private val DefaultToastTicks = 30

/** The tick rate a splash screen forces on an app that declared none: the intro is animated frame by frame, so it needs
  * a clock even when the app itself does not.
  */
private val SplashTickRate = 50.millis

/** How far in from the right edge the toast stack sits, and which row it starts on. Row 0 is normally an app's own
  * chrome, so toasts begin one row below it.
  */
private val ToastRightMargin = 1
private val ToastTopRow      = 1

/** The mutable state of a single [[TuiApp.runWith]] invocation: whether a redraw is pending, the focus-decorated tree
  * the last frame produced (events are routed against that tree, not against a freshly evaluated one), and the focus
  * tracker.
  *
  * Owned by one `runWith` call and touched only on the render thread — the event callbacks, the render lambda, and the
  * app's own hooks all run there, so none of these fields is synchronised.
  */
private final class RunState:
  var invalidated: Boolean      = false
  var lastTree: Option[Element] = None
  val tracker: FocusTracker     = FocusTracker()

/** The application entry point for the declarative DSL.
  *
  * `view` is re-evaluated under a tracking [[ReactiveScope]]: any `Signal` read during the last evaluation schedules a
  * redraw when it changes — state lives in signals, not in an explicitly threaded `State` value.
  *
  * Focus and events: focusable elements form a tab order in depth-first view order; `Tab` / `Shift+Tab` cycle focus and
  * a mouse press focuses the innermost focusable under the pointer. Key events start at the focused element and bubble
  * to its ancestors (`true` consumes), then the app's [[bindings]] run; an unconsumed `Ctrl+P` opens the command
  * palette (when bindings exist) and `Ctrl+C` quits.
  *
  * App services: [[pushScreen]]/[[popScreen]] for modal or full-screen navigation (layers below a modal leave the tab
  * order), [[notify]] for tick-aged toasts, [[openPalette]] for the fuzzy command palette over the declared bindings.
  * Call [[quit]] from any handler to exit cleanly.
  */
trait TuiApp:

  def view(using ReactiveScope): Element

  def config: RunnerConfig = RunnerConfig()

  /** The theme the built-in overlays (toasts, palette) and chrome presets render with. */
  def theme: Theme = Theme.Dark

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
    * def view(using ReactiveScope): Element =
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

  /** An intro screen shown before the first `view` render — see [[SplashScreen]]. Any key skips it. */
  def splash: Option[SplashScreen] = None

  /** Starts a post-render [[Effect]] over the whole frame. Needs a `config.tickRate` to animate; the effect is dropped
    * once done.
    */
  protected final def runEffect(effect: Effect): Unit =
    activeEffects = (effect, System.nanoTime()) :: activeEffects

  // ---- navigation ----

  /** Pushes a screen; modal screens layer over the current view, full screens replace it. */
  protected final def pushScreen(screen: Screen): Unit =
    screenStack.update(screen :: _)

  protected final def popScreen(): Unit =
    screenStack.update {
      case _ :: tail => tail
      case Nil       => Nil
    }

  // ---- notifications ----

  /** Shows a toast in the top-right corner for `ttlTicks` ticks (needs a `config.tickRate` to age out). */
  protected final def notify(
      message: String,
      level: NoticeLevel = NoticeLevel.Info,
      ttlTicks: Int = DefaultToastTicks,
  ): Unit =
    toasts.update(_ :+ ActiveToast(message, level, ttlTicks))

  protected final def dismissToasts(): Unit =
    toasts.set(Vector.empty)

  // ---- command palette ----

  /** Opens the fuzzy command palette over the declared [[bindings]]. */
  protected final def openPalette(): Unit =
    paletteQuery.clear()
    paletteSelected = 0
    paletteOpen.set(true)

  protected final def closePalette(): Unit =
    paletteOpen.set(false)

  /** Runs on the process's controlling terminal. Blocks until the app quits. */
  final def run(): Either[RunnerError, Unit] =
    JLine3Backend.create() match
      case Left(error)    => Left(RunnerError.Backend(error))
      case Right(backend) => runWith(backend)

  /** Runs over an explicit backend — how headless tests drive a `TuiApp`.
    *
    * Everything this sets up lives in one [[RunState]], and every stage below is handed that state explicitly rather
    * than closing over it, so the four-stage key precedence the trait's Scaladoc promises is four named methods.
    */
  final def runWith(backend: Backend): Either[RunnerError, Unit] =
    val run             = RunState()
    val scope           = ReactiveScope.generational(() => run.invalidated = true)
    val effectiveConfig =
      if splash.nonEmpty && config.tickRate.isEmpty then config.copy(tickRate = Some(SplashTickRate))
      else config
    val result          = TerminalRunner(backend, effectiveConfig, redrawRequested = () => run.invalidated).run(
      handleEvent(_, run, _),
      frame => renderFrame(frame, run, scope),
    )
    activeHandle.set(None)
    result

  /** Composes one frame: publish the size the view branches on, then either the intro or the reconciled view tree. */
  private def renderFrame(frame: Frame, run: RunState, scope: GenerationalScope): Unit =
    // the frame's area is what is actually about to be painted, so it — not the last resize event — is the size
    // the view branches on. Published before `invalidated` is cleared: the write invalidates the *previous*
    // generation's subscribers, and letting that survive into this frame would schedule a redundant redraw.
    val frameSize = Size(frame.area.width, frame.area.height)
    terminalSizeSignal.set(frameSize)
    run.invalidated = false
    if splashActive then renderSplash(frame)
    else
      scope.beginGeneration()
      val rawTree = ResponsivePass.resolve(effectiveView(using scope), frameSize)
      run.tracker.reconcile(FocusPass.focusKeys(rawTree))
      val tree    = FocusPass.decorate(rawTree, run.tracker, theme.focus)
      run.lastTree = Some(tree)
      frame.renderWidget(tree.widget, frame.area)
      processEffects(frame)

  /** The runner's single event entry point: dispatches one event and answers whether the frame must be redrawn. */
  private def handleEvent(event: Event, run: RunState, handle: RunnerHandle): Boolean =
    activeHandle.set(Some(handle))
    event match
      case Event.Key(key)     => handleKey(key, run, handle) || run.invalidated
      case Event.Mouse(mouse) => handleMouse(mouse, run) || run.invalidated
      case Event.Paste(text)  =>
        val consumed = run.lastTree.exists(EventRouter.dispatchPaste(_, text))
        consumed || run.invalidated
      case Event.FocusGained  =>
        onTerminalFocus(true)
        run.invalidated
      case Event.FocusLost    =>
        onTerminalFocus(false)
        run.invalidated
      case Event.Interrupt    => onInterrupt()
      case Event.Resize(size) =>
        // the render pass sets this too, from the frame it is about to draw; doing it here as well means an
        // `onResize` override — and anything it calls — already peeks the new size rather than the previous frame's
        terminalSizeSignal.set(size)
        onResize(size)
        true
      case Event.Tick         => handleTick(run)

  private def handleTick(run: RunState): Boolean =
    // before user code, so an `onTick` that reads the clock sees this tick's value rather than the last one's
    AnimationClock.advance()
    ageToasts()
    onTick()
    val splashJustFinished  = updateSplashProgress()
    val effectsJustFinished = pruneEffects()
    run.invalidated || activeEffects.nonEmpty || splashActive || splashJustFinished || effectsJustFinished

  private def handleKey(key: KeyEvent, run: RunState, handle: RunnerHandle): Boolean =
    if splashActive then
      splashSkipped = true // any key skips the intro; it never reaches the view
      true
    else routeKey(key, run, handle)

  /** Focused element first, then its ancestors, then the app bindings, then the framework's own keys. */
  private def routeKey(key: KeyEvent, run: RunState, handle: RunnerHandle): Boolean =
    val consumed = run.lastTree.exists(EventRouter.dispatchKey(_, key))
    val bound    = !consumed && !paletteOpen.peek && bindings.handle(key)
    if consumed || bound then true else handleFrameworkKey(key, run.tracker, handle)

  /** The last stage: the keys the framework reserves for itself once nothing else claimed the event — `Tab` /
    * `Shift+Tab` focus traversal, `Ctrl+P` for the command palette, `Ctrl+C` to quit.
    *
    * Answers `true` only when focus actually moved, because that is the one outcome here that changes the next frame
    * without going through a signal; opening the palette and quitting each schedule their own redraw.
    */
  private def handleFrameworkKey(key: KeyEvent, tracker: FocusTracker, handle: RunnerHandle): Boolean =
    key match
      case KeyEvent(KeyCode.Tab, modifiers) if modifiers.has(KeyModifiers.Shift)      => tracker.focusPrevious()
      case KeyEvent(KeyCode.Tab, _)                                                   => tracker.focusNext()
      case KeyEvent(KeyCode.Char('p'), modifiers)
          if modifiers.has(KeyModifiers.Ctrl) && bindings.bindings.nonEmpty && !paletteOpen.peek =>
        openPalette()
        false
      case KeyEvent(KeyCode.Char('c'), modifiers) if modifiers.has(KeyModifiers.Ctrl) =>
        handle.quit()
        false
      case _                                                                          => false

  private def handleMouse(mouse: MouseEvent, run: RunState): Boolean =
    val hit        = run.tracker.hitTest(mouse.x, mouse.y)
    val focusMoved =
      if mouse.kind == MouseEventKind.Down then
        hit match
          case Some(index) if index != run.tracker.focusedIndex =>
            run.tracker.focusTo(index)
            true
          case _                                                => false
      else false
    val target     = hit.flatMap(index => run.tracker.areaOf(index).map(area => (index, area)))
    val consumed   = run.lastTree.exists(EventRouter.dispatchMouse(_, mouse, target))
    consumed || focusMoved

  /** Requests a clean exit; safe to call from event handlers. No-op when the app is not running. */
  protected final def quit(): Unit =
    activeHandle.get().foreach(_.quit())

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

  // ---- composite view: base -> screens -> palette -> toasts ----

  private def effectiveView(using scope: ReactiveScope): Element =
    given Theme     = theme
    val withScreens = screenStack.get.reverse.foldLeft(view) { (below, screen) =>
      if screen.modal then Element.layers(FocusPass.suppressFocus(below), screen.view)
      else screen.view
    }
    val withPalette =
      if paletteOpen.get then Element.layers(FocusPass.suppressFocus(withScreens), paletteElement)
      else withScreens
    val active      = toasts.get
    if active.isEmpty then withPalette
    else Element.layers(withPalette, toastsElement(active))

  private def paletteElement(using theme: Theme): Element =
    val matches = paletteMatches
    paletteSelected = math.max(0, math.min(paletteSelected, math.max(0, matches.size - 1)))
    val listing = matches.zipWithIndex.map { (bound, index) =>
      val marker = if index == paletteSelected then "> " else "  "
      val style  = if index == paletteSelected then theme.focus else theme.primary
      Element.text(s"$marker${bound.label}  ${bound.description}").styled(_ => style).length(1)
    }
    val body    = Element
      .panel("Commands")(
        (Element.input(paletteQuery, placeholder = "type to filter…").length(1) +: listing)*
      )
      .styled(_ => theme.accent)
      .onKeyEvent {
        case KeyEvent(KeyCode.Escape, _) =>
          closePalette()
          true
        case KeyEvent(KeyCode.Down, _)   =>
          paletteSelected += 1
          true
        case KeyEvent(KeyCode.Up, _)     =>
          paletteSelected = math.max(0, paletteSelected - 1)
          true
        case KeyEvent(KeyCode.Enter, _)  =>
          paletteMatches.lift(paletteSelected).foreach { bound =>
            closePalette()
            bound.action()
          }
          true
        case _                           => false
      }
    centered(PaletteWidth, math.min(PaletteChrome + matches.size, PaletteMaxHeight))(body)

  private def paletteMatches: Seq[KeyBinding] =
    val accepts = Fuzzy.matcher(paletteQuery.value)
    bindings.bindings.filter(bound => accepts(bound.description))

  /** The toast stack, composed from [[NoticeElement]] rather than painted into the buffer directly, so it inherits the
    * widget layer's clipping and severity icons instead of re-implementing them.
    *
    * Each toast is its own [[Element.positioned]] overlay because the stack is right-aligned and every row is a
    * different width. The offset is measured against the last published terminal size, which the render pass sets from
    * the frame it is about to paint before it evaluates the view — so it is this frame's width, not the previous one's.
    */
  private def toastsElement(active: Vector[ActiveToast])(using theme: Theme): Element =
    val areaWidth = terminalSizeSignal.peek.width
    val overlays  = active.takeRight(MaxVisibleToasts).zipWithIndex.map { (toast, index) =>
      val style  = toastStyle(toast.level)
      // the trailing space keeps the reversed style reading as a padded badge rather than as text ending mid-cell
      val notice = NoticeElement(s"${toast.message} ", toast.level, None, style, style, style)
      val width  = CharWidth.of(s"${toast.level.icon} ${toast.message} ")
      val dx     = math.max(0, areaWidth - width - ToastRightMargin)
      Element.positioned(dx, ToastTopRow + index, width, 1)(notice)
    }
    Element.layers(overlays.head, overlays.tail*)

  private def toastStyle(level: NoticeLevel)(using theme: Theme): Style =
    val base = level match
      case NoticeLevel.Info    => theme.accent
      case NoticeLevel.Success => theme.success
      case NoticeLevel.Warning => theme.warning
      case NoticeLevel.Error   => theme.error
    base.reverse

  private def ageToasts(): Unit =
    if toasts.peek.nonEmpty then
      toasts.update(_.map(t => t.copy(remainingTicks = t.remainingTicks - 1)).filter(_.remainingTicks > 0))

  private final case class ActiveToast(message: String, level: NoticeLevel, remainingTicks: Int)

  /** The size the last frame was drawn at. Zero until the first render publishes the real one — nothing can observe
    * that gap through [[terminalSize]], which is only readable from `view`.
    */
  private val terminalSizeSignal: Signal[Size]    = Signal(Size(0, 0))
  // Everything below — these signals, the palette cursor, and the splash/effect fields further down — is per-instance
  // state written only from the render thread (event handlers, the render lambda, and the app's own callbacks all run
  // there). None of it is reset by `runWith`, so a `TuiApp` instance is meant to be run once: running the same
  // instance a second time keeps the splash marked finished and any still-running effects queued from the first run.
  private val screenStack: Signal[List[Screen]]   = Signal(Nil)
  private val toasts: Signal[Vector[ActiveToast]] = Signal(Vector.empty)
  private val paletteOpen: Signal[Boolean]        = Signal(false)
  private val paletteQuery: TextInputState        = TextInputState()
  private var paletteSelected: Int                = 0
  private def splashActive: Boolean               =
    splash.nonEmpty && !splashFinished && !splashSkipped

  private def renderSplash(frame: Frame): Unit =
    splash.foreach { intro =>
      if splashStartNanos == 0L then splashStartNanos = System.nanoTime()
      val elapsed = (System.nanoTime() - splashStartNanos).nanos
      frame.renderWidget(intro.content.widget, frame.area)
      frame.applyEffect(intro.effect, elapsed)
    }

  private def processEffects(frame: Frame): Unit =
    if activeEffects.nonEmpty then
      val now = System.nanoTime()
      activeEffects.foreach((effect, started) => frame.applyEffect(effect, (now - started).nanos))

  /** Drops finished effects; `true` when any were dropped (one more redraw shows the un-effected frame). */
  private def pruneEffects(): Boolean =
    if activeEffects.isEmpty then false
    else
      val now             = System.nanoTime()
      val (done, running) = activeEffects.partition((effect, started) => effect.isDone((now - started).nanos))
      activeEffects = running
      done.nonEmpty

  /** Flips the splash to finished once its effect and minimum duration have both elapsed. */
  private def updateSplashProgress(): Boolean =
    splash match
      case Some(intro) if splashActive && splashStartNanos != 0L =>
        val elapsed = (System.nanoTime() - splashStartNanos).nanos
        val total   = intro.effect.duration match
          case finite: FiniteDuration => if finite > intro.minimumDuration then finite else intro.minimumDuration
          case _                      => intro.minimumDuration
        if elapsed >= total then
          splashFinished = true
          true
        else false
      case _                                                     => false

  private var activeEffects: List[(Effect, Long)] = Nil
  private var splashStartNanos: Long              = 0L
  private var splashFinished: Boolean             = false
  private var splashSkipped: Boolean              = false
  private val activeHandle                        = AtomicReference[Option[RunnerHandle]](None)
