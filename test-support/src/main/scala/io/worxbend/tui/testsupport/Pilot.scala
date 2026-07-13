package io.worxbend.tui.testsupport

import io.worxbend.tui.core.{Event, KeyCode, KeyEvent, KeyModifiers, MouseEvent, MouseEventKind, Size}
import io.worxbend.tui.terminal.HeadlessBackend

import scala.concurrent.duration.{Deadline, DurationInt, FiniteDuration}

/** Drives a TUI app end-to-end without a terminal: the app runs on a background thread against a [[HeadlessBackend]];
  * the test thread posts synthetic input and asserts on the rendered buffer.
  *
  * All posting methods return `this` for chaining: `pilot.typeText("hi").pressKey(KeyCode.Enter).waitForIdle()`.
  */
final class Pilot private (val backend: HeadlessBackend, thread: Thread):

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
    * has exited. Throws on deadline overrun — an assertion failure, not a modeled error.
    */
  def waitForIdle(timeout: FiniteDuration = 2.seconds): Pilot =
    val deadline         = Deadline.now + timeout
    val idleReadsBefore  = backend.idleReads
    def settled: Boolean =
      !thread.isAlive || (backend.pendingEvents == 0 && backend.idleReads > idleReadsBefore)
    while !settled && deadline.hasTimeLeft() do Thread.sleep(PollSleep.toMillis)
    if !settled then throw AssertionError(s"app did not go idle within $timeout")
    this

  /** The last rendered frame as trimmed lines; empty if nothing has been drawn yet. */
  def screenLines: Seq[String] =
    backend.lastDrawn.map(BufferAssertions.trimmedLines).getOrElse(Seq.empty)

  def screenText: String = screenLines.mkString("\n")

  def isRunning: Boolean = thread.isAlive

  /** Waits for the app to exit on its own (e.g. after posting its quit key). */
  def awaitTermination(timeout: FiniteDuration = 2.seconds): Boolean =
    thread.join(timeout.toMillis)
    !thread.isAlive

  private val PollSleep: FiniteDuration = 5.millis

object Pilot:

  /** Starts `app` — any blocking function that drives a runner over `backend` — on a daemon thread and hands back the
    * driver.
    */
  def start(backend: HeadlessBackend)(app: => Unit): Pilot =
    val thread = Thread(
      () => app,
      "tui-pilot-app",
    )
    thread.setDaemon(true)
    thread.start()
    Pilot(backend, thread)
