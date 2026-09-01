package io.worxbend.tui.testsupport

import io.worxbend.tui.core.{
  Buffer,
  Cell,
  Event,
  KeyCode,
  KeyEvent,
  KeyModifiers,
  MouseButton,
  MouseEvent,
  MouseEventKind,
  Position,
  Size,
}
import io.worxbend.tui.runtime.RunnerError
import io.worxbend.tui.terminal.HeadlessBackend

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.{Deadline, DurationInt, FiniteDuration}

/** Drives a TUI app end-to-end without a terminal: the app runs on a background thread against a [[HeadlessBackend]];
  * the test thread posts synthetic input and asserts on the rendered buffer.
  *
  * All posting methods return `this` for chaining: `pilot.typeText("hi").press("enter").waitForIdle()`.
  *
  * A throwable escaping the app body kills the app thread; the pilot records it and rethrows it on the *test* thread
  * from the next observation of the app's state, so a crash never reads as a clean exit. The same holds for a run that
  * *returned* a `Left(RunnerError)` — an orderly exit that still failed. `appFailure` and `runFailure` are owned by
  * [[Pilot.start]], each written once by the app thread and read by the test thread.
  *
  * Ownership of the observed state: the frame and the input queue live in the [[HeadlessBackend]], not in the pilot.
  * The app thread writes them (it draws frames and consumes posted events) and the test thread reads them through
  * `screenLines`/`screenText`/`lastFrame`/`cellAt` and `waitForIdle`. `HeadlessBackend` keeps that state in thread-safe
  * holders, so reading from the test thread is safe at any moment — but "safe" only means the read will not tear, never
  * that the app has caught up. Posting input and reading the frame without a `waitForIdle` in between can observe the
  * frame from before that input was handled. Call `waitForIdle()` after posting and before asserting.
  */
final class Pilot private (
    val backend: HeadlessBackend,
    thread: Thread,
    appFailure: AtomicReference[Option[Throwable]],
    runFailure: AtomicReference[Option[RunnerError]],
):

  /** Posts one key event per key spec, in order: `press("ctrl+s")`, `press("down", "down", "enter")`.
    *
    * The specs are the ones an application declares its keys with — `binding("ctrl+s", "save")` and `press("ctrl+s")`
    * go through the same [[io.worxbend.tui.core.KeyEvent.parse]], so a test drives the app with the spelling the app
    * was written against instead of a hand-translated [[KeyEvent]] that can drift away from it. Prefer this over
    * [[pressKey]], which stays for tests that want to build the ADT value directly.
    *
    * A malformed spec throws [[IllegalArgumentException]] naming the spec and the parser's complaint, the same way
    * `binding` does: a key spec is written by hand and a typo in one is a mistake in the test, not a condition to
    * handle.
    */
  def press(specs: String*): Pilot =
    specs.foreach { spec =>
      KeyEvent.parse(spec) match
        case Right(event)  => backend.postEvent(Event.Key(event))
        case Left(problem) => throw IllegalArgumentException(s"bad key spec '$spec': $problem")
    }
    this

  /** Posts one key event built straight from the [[KeyCode]]/[[KeyModifiers]] ADT. [[press]] says the same thing in the
    * application's own vocabulary and is what most tests want.
    */
  def pressKey(code: KeyCode, modifiers: KeyModifiers = KeyModifiers.None): Pilot =
    backend.postEvent(Event.Key(KeyEvent(code, modifiers)))
    this

  /** Posts one key event per Unicode **code point** of `text`, in order.
    *
    * Code points, not UTF-16 code units and not grapheme clusters, because that is what a real terminal delivers: the
    * `InputDecoder` recombines a surrogate pair into one [[KeyCode.Char]] and reports a combining mark as its own key
    * event. A letter followed by a combining accent therefore drives the app with two events here, exactly as it would
    * at a real keyboard, while an emoji outside the Basic Multilingual Plane arrives as the single key event it is —
    * iterating `Char`s would have split it into two lone surrogates that mean nothing to the app.
    */
  def typeText(text: String): Pilot =
    text.codePoints().forEach(cp => pressKey(KeyCode.Char(cp)))
    this

  /** Synthesises a press/release pair at `(x, y)`: a `Down` immediately followed by an `Up` at the same coordinates,
    * with no `Moved` event in between and no drag. The convenience form of [[mouseDown]] + [[mouseUp]], which is what
    * to reach for when a test needs to observe the app between the two halves of a click.
    *
    * As with every posting method, the caller is responsible for calling [[waitForIdle]] before asserting on the frame.
    */
  def click(x: Int, y: Int): Pilot =
    mouseDown(x, y)
    mouseUp(x, y)

  /** [[click]] with a button other than the left one — a right-click for a context menu, a middle-click for a paste.
    *
    * A separate method rather than a defaulted parameter on [[click]], because [[click]] takes no modifiers either and
    * growing it two optional arguments would make the common two-argument call harder to read, not easier.
    */
  def clickWith(x: Int, y: Int, button: MouseButton): Pilot =
    postMouse(x, y, MouseEventKind.Down, KeyModifiers.None, button)
    postMouse(x, y, MouseEventKind.Up, KeyModifiers.None, button)

  /** Posts a button press at `(x, y)` and nothing else, leaving the button held as far as the app is concerned. */
  def mouseDown(
      x: Int,
      y: Int,
      modifiers: KeyModifiers = KeyModifiers.None,
      button: MouseButton = MouseButton.Left,
  ): Pilot =
    postMouse(x, y, MouseEventKind.Down, modifiers, button)

  /** Posts a button release at `(x, y)`. */
  def mouseUp(
      x: Int,
      y: Int,
      modifiers: KeyModifiers = KeyModifiers.None,
      button: MouseButton = MouseButton.Left,
  ): Pilot =
    postMouse(x, y, MouseEventKind.Up, modifiers, button)

  /** Posts a pointer move to `(x, y)` with no button held — what a terminal reports under mouse-motion tracking. */
  def mouseMove(x: Int, y: Int): Pilot =
    postMouse(x, y, MouseEventKind.Moved, KeyModifiers.None, MouseButton.Unknown)

  /** Posts a whole drag gesture: `Down` at the start point, one `Drag` at the end point, then `Up` there.
    *
    * One intermediate `Drag` rather than a path of them, because a widget that tracks a drag reads the latest position
    * and not the route taken; a test that needs the route posts the steps itself with [[mouseDown]] and this sequence's
    * parts.
    */
  def drag(
      fromX: Int,
      fromY: Int,
      toX: Int,
      toY: Int,
      modifiers: KeyModifiers = KeyModifiers.None,
      button: MouseButton = MouseButton.Left,
  ): Pilot =
    postMouse(fromX, fromY, MouseEventKind.Down, modifiers, button)
    postMouse(toX, toY, MouseEventKind.Drag, modifiers, button)
    postMouse(toX, toY, MouseEventKind.Up, modifiers, button)

  /** Posts `times` scroll-up notches at `(x, y)`. */
  def scrollUp(x: Int, y: Int, times: Int = 1, modifiers: KeyModifiers = KeyModifiers.None): Pilot =
    scroll(x, y, MouseEventKind.ScrollUp, times, modifiers)

  /** Posts `times` scroll-down notches at `(x, y)`. */
  def scrollDown(x: Int, y: Int, times: Int = 1, modifiers: KeyModifiers = KeyModifiers.None): Pilot =
    scroll(x, y, MouseEventKind.ScrollDown, times, modifiers)

  /** Posts `times` horizontal wheel notches to the left at `(x, y)` — what a sideways trackpad swipe sends.
    *
    * No built-in element consumes these, so a test uses them to drive an application's own `onMouseEvent`.
    */
  def scrollLeft(x: Int, y: Int, times: Int = 1, modifiers: KeyModifiers = KeyModifiers.None): Pilot =
    scroll(x, y, MouseEventKind.ScrollLeft, times, modifiers)

  /** Posts `times` horizontal wheel notches to the right at `(x, y)`. */
  def scrollRight(x: Int, y: Int, times: Int = 1, modifiers: KeyModifiers = KeyModifiers.None): Pilot =
    scroll(x, y, MouseEventKind.ScrollRight, times, modifiers)

  private def scroll(x: Int, y: Int, kind: MouseEventKind, times: Int, modifiers: KeyModifiers): Pilot =
    var remaining = times
    while remaining > 0 do
      // a wheel notch presses nothing, so it names no button — the same thing a real terminal reports
      postMouse(x, y, kind, modifiers, MouseButton.Unknown)
      remaining -= 1
    this

  private def postMouse(x: Int, y: Int, kind: MouseEventKind, modifiers: KeyModifiers, button: MouseButton): Pilot =
    backend.postEvent(Event.Mouse(MouseEvent(Position(x, y), kind, modifiers, button)))
    this

  def resize(width: Int, height: Int): Pilot =
    backend.resizeTo(Size(width, height))
    this

  /** Posts raw terminal input: the code units go through the *production* input decoder, and whatever events it
    * produces are queued for the app.
    *
    * [[press]] builds a [[KeyEvent]] from a key spec and queues it directly, which never involves the decoder. That
    * leaves one thing untested that this toolkit has already been bitten by: an application binding spelled `ctrl+s`
    * and a decoder that turns the bytes a terminal sends for Ctrl+S into some *other* key both look correct on their
    * own, while the app is dead in a real terminal. Sending the bytes exercises both vocabularies at once.
    *
    * The values are UTF-16 code units as a terminal reader hands them back, not UTF-8 bytes. Use this for what a key
    * spec cannot say — a bracketed paste, an SGR mouse report, a kitty-protocol key — and for the sequences that must
    * decode to *nothing*, such as a device-attributes reply, where the assertion is that the app saw no event at all.
    *
    * One call is one decoder, so a sequence has to be sent whole. Splitting a bracketed paste across two calls does not
    * simulate a paste arriving in two reads: the first call's decoder reaches the end of its input in the middle of the
    * paste and reports the empty paste it had read up to that point.
    */
  def sendBytes(codeUnits: Int*): Pilot =
    backend.postInput(codeUnits)
    this

  /** [[sendBytes]] spelled the way an escape sequence is written down: `sendEscape("[A")` sends `ESC [ A`, the up
    * arrow. Only the `ESC` is supplied; every character of `body` is sent as its own code unit.
    */
  def sendEscape(body: String): Pilot =
    sendBytes(0x1b +: body.map(_.toInt)*)

  /** Posts one bracketed paste carrying `text` as a single event.
    *
    * A terminal in bracketed-paste mode hands an application the whole pasted string at once instead of a storm of key
    * events, which is how a paste and a very fast typist stay distinguishable. That makes this a *different code path*
    * from [[typeText]] — the DSL routes it to an element's paste handler rather than to its key handler — so a test of
    * what an input does with a long or multi-line paste has to post the paste and cannot type it.
    */
  def paste(text: String): Pilot =
    backend.postEvent(Event.Paste(text))
    this

  /** Posts `times` synthetic ticks, the event a runner configured with a tick rate injects on its own.
    *
    * Posting them by hand is what makes an animation or timeout test exact: the app under test needs no tick rate at
    * all, so nothing depends on wall-clock timing, and a test that wants ten ticks gets exactly ten.
    */
  def tick(times: Int = 1): Pilot =
    var remaining = times
    while remaining > 0 do
      backend.postEvent(Event.Tick)
      remaining -= 1
    this

  /** Posts the report a terminal sends when its window regains focus (the "mode 1004" focus report). */
  def focusGained(): Pilot =
    backend.postEvent(Event.FocusGained)
    this

  /** Posts the report a terminal sends when its window loses focus — the hook an app uses to pause an animation or dim
    * its chrome while the user is looking somewhere else.
    */
  def focusLost(): Pilot =
    backend.postEvent(Event.FocusLost)
    this

  /** Posts an interrupt — what reaches the application when `Ctrl+C` raises SIGINT.
    *
    * It arrives as an ordinary event rather than killing the JVM, and an application that does not consume it quits
    * through its normal teardown. A test therefore usually follows this with [[awaitTermination]]; follow it with
    * [[waitForIdle]] instead when the app under test is expected to *consume* the interrupt and stay up.
    */
  def interrupt(): Pilot =
    backend.postEvent(Event.Interrupt)
    this

  /** Moves a [[ManualClock]] the app under test was started with forward by `delta`, then waits for the frames the
    * ticks it is now owed will paint.
    *
    * This is the exact alternative to sleeping. One advance of at least the runner's tick rate owes the app exactly
    * **one** tick, however far past the rate it goes: when the runner fires a tick it records the reading it fired at,
    * rather than adding one rate to the previous deadline, so the next tick is due one whole rate after the advance and
    * not partway through it. Three ticks are therefore three advances, which is also how a test says "three separate
    * moments" rather than "one long jump".
    *
    * `draws` is how many frames those ticks are expected to paint — one, for a tick handler that answers `Redraw`. Pass
    * `draws = 0` for an app whose ticks paint nothing: the advance still happens and nothing is waited for.
    *
    * Frames are counted from before the advance, so a frame the app happened to paint for its own reasons a moment
    * earlier cannot be miscounted as one of these.
    */
  def advanceClock(clock: ManualClock, delta: FiniteDuration, draws: Long = 1L): Pilot =
    val before = backend.drawCount
    val _      = clock.advance(delta)
    waitForDraws(before + draws)

  /** Waits until the app has consumed every posted event and gone idle (an empty-queue read timeout), or the app thread
    * has exited. Throws on deadline overrun — an assertion failure, not a modeled error. An app thread that died from a
    * throwable is not an exit: this fails with that throwable as the cause.
    */
  def waitForIdle(timeout: FiniteDuration = Pilot.DefaultTimeout): Pilot =
    val deadline         = Deadline.now + timeout
    val idleReadsBefore  = backend.idleReads
    def settled: Boolean =
      !thread.isAlive || (backend.pendingEvents == 0 && backend.idleReads > idleReadsBefore)
    while !settled && deadline.hasTimeLeft() do Thread.sleep(Pilot.PollSleep.toMillis)
    rethrowAppFailure()
    if !settled then throw CallSite.attribute(AssertionError(s"app did not go idle within $timeout"))
    this

  /** Waits until `condition` holds, and fails the test naming `description` if it never does.
    *
    * [[waitForIdle]] proves the *posted event queue* drained, which is all a test asserting on the result of a keypress
    * needs. It says nothing about work that finished somewhere else and landed on a later render-thread drain — an
    * `Async` continuation, a timer, a background thread's result — so a test waiting on that has to wait on the thing
    * itself. Hand-rolled deadline polls do this by returning quietly when the clock runs out, and the assertion that
    * follows then fails somewhere else, describing a symptom rather than the wait that did not finish. This throws
    * instead, at the wait.
    *
    * `condition` is re-evaluated on the test thread every few milliseconds, so read state that is safe to read from
    * there — the pilot's own observations are, per this class's ownership note. A throwable that killed the app thread
    * aborts the wait immediately rather than running the clock out, so a crashed app reports its own cause.
    *
    * @param description
    *   what is being waited for, phrased to complete "timed out waiting for …"
    */
  def waitUntil(description: String, timeout: FiniteDuration = Pilot.DefaultTimeout)(condition: => Boolean): Pilot =
    val deadline = Deadline.now + timeout
    rethrowAppFailure()
    while !condition && deadline.hasTimeLeft() do
      Thread.sleep(Pilot.PollSleep.toMillis)
      rethrowAppFailure()
    if !condition then throw CallSite.attribute(AssertionError(s"timed out after $timeout waiting for $description"))
    this

  /** Waits until the app has drawn at least `count` frames in total since it started.
    *
    * For the tests whose subject *is* the redraw — an animation that must keep ticking, a signal change that must
    * schedule a frame. Counting from app start rather than from this call means a test reads `backend.drawCount` first
    * and asks for `+ n`, which is explicit about how many frames it expects rather than hiding it in a helper.
    */
  def waitForDraws(count: Long, timeout: FiniteDuration = Pilot.DefaultTimeout): Pilot =
    waitUntil(s"$count drawn frames", timeout)(backend.drawCount >= count)

  /** The last rendered frame as trimmed lines; empty if nothing has been drawn yet. Fails with the app's throwable if
    * the app thread died, so a crash never reads as a blank screen.
    */
  def screenLines: Seq[String] =
    rethrowAppFailure()
    backend.lastDrawn.map(BufferAssertions.trimmedLines).getOrElse(Seq.empty)

  def screenText: String = screenLines.mkString("\n")

  /** The last rendered frame itself, for assertions about style rather than glyphs — colors, modifiers, and anything
    * else [[screenLines]] flattens away. Fails the test when nothing has been drawn, because an assertion against a
    * silently empty frame passes for the wrong reason.
    */
  def lastFrame: Buffer =
    rethrowAppFailure()
    backend.lastDrawn.getOrElse(throw CallSite.attribute(AssertionError("nothing has been drawn yet")))

  /** Compares the last rendered frame against the `golden/<name>.txt` fixture, and returns `this` so a snapshot sits in
    * a chain of interactions:
    *
    * {{{
    * pilot.press("tab").waitForIdle().assertGolden("form-focused")
    * }}}
    *
    * The fluent spelling of `GoldenFrames.assertMatches(name, pilot)`. See [[GoldenFrames]] for the recording workflow
    * and for what a fixture does and does not record — it is glyphs and layout, never styling.
    */
  def assertGolden(name: String): Pilot =
    GoldenFrames.assertMatches(name, lastFrame)
    this

  /** The cell at `(x, y)` of the last rendered frame. */
  def cellAt(x: Int, y: Int): Cell = lastFrame.get(x, y)

  /** Whether the app thread is still running. A thread that died from a throwable is not merely stopped: this fails
    * with that throwable as the cause.
    */
  def isRunning: Boolean =
    rethrowAppFailure()
    thread.isAlive

  /** Waits for the app to exit on its own (e.g. after posting its quit key). A thread that died from a throwable is not
    * a clean exit: this fails with that throwable as the cause rather than reporting success.
    */
  def awaitTermination(timeout: FiniteDuration = Pilot.DefaultTimeout): Boolean =
    thread.join(timeout.toMillis)
    rethrowAppFailure()
    !thread.isAlive

  /** Fails on the test thread if the app did not finish cleanly, so neither kind of failure surfaces as an empty screen
    * or a clean-looking exit. A no-op while the app is healthy.
    *
    * Two kinds, because there are two ways for a run to be over and wrong. A throwable that escaped the app body killed
    * the thread and is rethrown with the original as the cause. A run that *returned* [[RunnerError]] exited in an
    * orderly way and reports nothing to the thread's uncaught-exception handler — the terminal could not be restored,
    * the event handler threw, a background continuation failed — and used to read here as a perfectly clean exit, with
    * whatever the test asserted about the last frame passing on stale state.
    */
  private def rethrowAppFailure(): Unit =
    appFailure.get() match
      case Some(error) => throw CallSite.attribute(AssertionError(s"the tui-pilot-app thread died with $error", error))
      case None        =>
        runFailure.get() match
          case Some(error) =>
            throw CallSite.attribute(AssertionError(s"the app's runner returned a failure: ${error.message}"))
          case None        => ()

object Pilot:

  /** How long the test thread sleeps between checks while waiting for the app to go idle. Small enough that a settled
    * app is noticed almost at once, large enough not to spin a core.
    */
  private val PollSleep: FiniteDuration = 5.millis

  /** The pilot's patience, shared by [[Pilot.waitForIdle]] and [[Pilot.awaitTermination]] so that one value sets how
    * long a test waits before reporting a hung app.
    */
  private[testsupport] val DefaultTimeout: FiniteDuration = 2.seconds

  /** Starts `app` — any blocking expression that drives a runner over `backend` — on a daemon thread and hands back the
    * driver.
    *
    * `app` returns what the runner returned, which in practice means the body is `app.runWith(backend)` or
    * `TerminalRunner(backend).run(...)` and nothing has to be written to discard it. Both ways for that run to be wrong
    * are then observed rather than assumed:
    *
    *   - A throwable escaping `app` is captured on the app thread.
    *   - A `Left(RunnerError)` — an orderly exit that nonetheless failed: the terminal could not be restored, the event
    *     handler threw, a queued continuation failed — is recorded too.
    *
    * Either one is reported on the *test* thread by the next
    * `waitForIdle`/`screenLines`/`lastFrame`/`isRunning`/`awaitTermination` call, as an `AssertionError` that carries
    * the throwable as its cause or names `RunnerError.message`. An app with nothing to return — a test fixture that
    * blocks on a latch, say — ends its body with `Right(())`.
    */
  def start(backend: HeadlessBackend)(app: => Either[RunnerError, Unit]): Pilot =
    val appFailure = AtomicReference[Option[Throwable]](None)
    val runFailure = AtomicReference[Option[RunnerError]](None)
    val thread     = Thread(
      () => app.left.foreach(error => runFailure.set(Some(error))),
      "tui-pilot-app",
    )
    thread.setUncaughtExceptionHandler((_, error) => appFailure.set(Some(error)))
    thread.setDaemon(true)
    thread.start()
    Pilot(backend, thread, appFailure, runFailure)

  /** Starts an app against a backend this method owns: it builds a [[HeadlessBackend]] of `size`, hands that backend to
    * `app`, and leaves it reachable afterwards as `pilot.backend`.
    *
    * Every pilot test used to write the same three lines — construct the backend, pass it to `start`, then name it a
    * second time inside the block as `app.runWith(backend)`. Here the block takes the backend as its parameter, so a
    * test that has no other use for it never has to name it:
    *
    * {{{
    * val app   = CounterApp()
    * val pilot = Pilot.start(Size(40, 10))(app.runWith).waitForIdle()
    * }}}
    *
    * Failure reporting is exactly [[start(backend:io\.worxbend\.tui\.terminal\.HeadlessBackend)*]]'s: this overload
    * owns nothing but the backend's construction.
    *
    * The parameter is a function of the backend rather than an application object because `tui-test` is built on
    * `tui-core`, `tui-terminal` and `tui-runtime` and on nothing above them. An overload taking a `TuiApp` would point
    * this module's dependency edge upward at `tui-dsl`.
    */
  def start(size: Size)(app: HeadlessBackend => Either[RunnerError, Unit]): Pilot =
    val backend = HeadlessBackend(size)
    start(backend)(app(backend))
