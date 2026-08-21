package io.worxbend.tui.testsupport

import io.worxbend.tui.core.{Buffer, Cell, Event, KeyCode, KeyEvent, KeyModifiers, MouseEvent, MouseEventKind, Size}
import io.worxbend.tui.terminal.HeadlessBackend

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.{Deadline, DurationInt, FiniteDuration}

/** Drives a TUI app end-to-end without a terminal: the app runs on a background thread against a [[HeadlessBackend]];
  * the test thread posts synthetic input and asserts on the rendered buffer.
  *
  * All posting methods return `this` for chaining: `pilot.typeText("hi").pressKey(KeyCode.Enter).waitForIdle()`.
  *
  * A throwable escaping the app body kills the app thread; the pilot records it and rethrows it on the *test* thread
  * from the next observation of the app's state, so a crash never reads as a clean exit. `appFailure` is owned by
  * [[Pilot.start]], written once by the app thread's uncaught-exception handler and read by the test thread.
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
):

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
    * with no `Moved` event in between and no drag. As with every posting method, the caller is responsible for calling
    * [[waitForIdle]] before asserting on the frame.
    */
  def click(x: Int, y: Int): Pilot =
    backend.postEvent(Event.Mouse(MouseEvent(x, y, MouseEventKind.Down, KeyModifiers.None)))
    backend.postEvent(Event.Mouse(MouseEvent(x, y, MouseEventKind.Up, KeyModifiers.None)))
    this

  def resize(width: Int, height: Int): Pilot =
    backend.resizeTo(Size(width, height))
    this

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
    if !settled then throw AssertionError(s"app did not go idle within $timeout")
    this

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
    backend.lastDrawn.getOrElse(throw AssertionError("nothing has been drawn yet"))

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

  /** Rethrows on the test thread whatever killed the app thread, so a crash surfaces as a failure that names its cause
    * instead of as an empty screen or a clean-looking exit. A no-op while the app is healthy.
    */
  private def rethrowAppFailure(): Unit =
    appFailure.get() match
      case Some(error) => throw AssertionError(s"the tui-pilot-app thread died with $error", error)
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

  /** Starts `app` — any blocking function that drives a runner over `backend` — on a daemon thread and hands back the
    * driver. A throwable escaping `app` is captured on that thread and rethrown on the test thread by the next
    * `waitForIdle`/`screenLines`/`lastFrame`/`isRunning`/`awaitTermination` call, wrapped in an `AssertionError` that
    * carries it as the cause.
    */
  def start(backend: HeadlessBackend)(app: => Unit): Pilot =
    val appFailure = AtomicReference[Option[Throwable]](None)
    val thread     = Thread(
      () => app,
      "tui-pilot-app",
    )
    thread.setUncaughtExceptionHandler((_, error) => appFailure.set(Some(error)))
    thread.setDaemon(true)
    thread.start()
    Pilot(backend, thread, appFailure)
