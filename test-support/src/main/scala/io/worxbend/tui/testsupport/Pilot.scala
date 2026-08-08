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
  */
final class Pilot private (
    val backend: HeadlessBackend,
    thread: Thread,
    appFailure: AtomicReference[Option[Throwable]],
):

  def pressKey(code: KeyCode, modifiers: KeyModifiers = KeyModifiers.None): Pilot =
    backend.postEvent(Event.Key(KeyEvent(code, modifiers)))
    this

  def typeText(text: String): Pilot =
    text.foreach(c => pressKey(KeyCode.Char(c)))
    this

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
  def waitForIdle(timeout: FiniteDuration = 2.seconds): Pilot =
    val deadline         = Deadline.now + timeout
    val idleReadsBefore  = backend.idleReads
    def settled: Boolean =
      !thread.isAlive || (backend.pendingEvents == 0 && backend.idleReads > idleReadsBefore)
    while !settled && deadline.hasTimeLeft() do Thread.sleep(PollSleep.toMillis)
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
  def awaitTermination(timeout: FiniteDuration = 2.seconds): Boolean =
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

  private val PollSleep: FiniteDuration = 5.millis

object Pilot:

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
