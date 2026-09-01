package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Position, Size}

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.Duration

/** An in-memory [[Backend]] for headless end-to-end testing: renders into a retained snapshot instead of a TTY and
  * reads synthetic events posted by the test driver.
  *
  * Thread contract: the runner calls `readEvent`/`draw` on the render thread while a test thread posts events and
  * inspects `lastDrawn` — hence the blocking queue and volatile snapshot.
  */
final class HeadlessBackend(initialSize: Size) extends Backend:

  private val events                                           = LinkedBlockingQueue[Event]()
  @volatile private var terminalSize                           = initialSize
  @volatile private var lastFrame: Option[Buffer]              = None
  @volatile private var rawMode                                = false
  @volatile private var alternateScreen                        = false
  @volatile private var mouseCapture: Option[MouseCaptureMode] = None
  @volatile private var cursorVisible                          = true
  @volatile private var lastClipboard: Option[String]          = None
  @volatile private var lastTitle: Option[String]              = None
  @volatile private var caret: Option[Position]                = None
  private val caretMoveCounter                                 = AtomicLong(0)
  private val drawCounter                                      = AtomicLong(0)
  private val idleReadCounter                                  = AtomicLong(0)
  private val suspendCounter                                   = AtomicLong(0)
  private val wakeCounter                                      = AtomicLong(0)
  private val fullRedrawCounter                                = AtomicLong(0)
  private val printedLines                                     = scala.collection.mutable.ArrayBuffer.empty[String]
  private val clears                                           = scala.collection.mutable.ArrayBuffer.empty[ClearType]

  def size: Either[BackendError, Size] = Right(terminalSize)

  def draw(buffer: Buffer): Either[BackendError, Unit] =
    lastFrame = Some(buffer.snapshot)
    val _ = drawCounter.incrementAndGet()
    Right(())

  def enableRawMode(): Either[BackendError, Unit] =
    rawMode = true
    Right(())

  def disableRawMode(): Either[BackendError, Unit] =
    if !rawMode then Left(BackendError.NotInRawMode)
    else
      rawMode = false
      Right(())

  def enterAlternateScreen(): Either[BackendError, Unit] =
    alternateScreen = true
    Right(())

  def leaveAlternateScreen(): Either[BackendError, Unit] =
    alternateScreen = false
    Right(())

  def enableMouseCapture(): Either[BackendError, Unit] = enableMouseCapture(MouseCaptureMode.Buttons)

  override def enableMouseCapture(mode: MouseCaptureMode): Either[BackendError, Unit] =
    mouseCapture = Some(mode)
    Right(())

  def disableMouseCapture(): Either[BackendError, Unit] =
    mouseCapture = None
    Right(())

  def hideCursor(): Either[BackendError, Unit] =
    cursorVisible = false
    Right(())

  def showCursor(): Either[BackendError, Unit] =
    cursorVisible = true
    Right(())

  /** Records where the hardware caret was asked to go, so a test can assert on it exactly as it asserts on a drawn
    * cell. Nothing is validated here: the real terminal clamps an out-of-range position and a test that expects
    * clamping should be asserting against the position the caller computed, not one this class invented.
    */
  override def setCursorPosition(position: Position): Either[BackendError, Unit] =
    caret = Some(position)
    val _ = caretMoveCounter.incrementAndGet()
    Right(())

  def readEvent(timeout: Duration): Either[BackendError, Option[Event]] =
    Backend.requirePositiveTimeout(timeout)
    val polled =
      if timeout.isFinite then Option(events.poll(timeout.toMillis, TimeUnit.MILLISECONDS))
      else Some(events.take())
    if polled.isEmpty then
      val _ = idleReadCounter.incrementAndGet()
    Right(polled)

  /** Records the wake so tests can assert that queued render-thread work asked for one.
    *
    * Deliberately does not disturb the event queue: headless reads use short timeouts, so waiting one out costs
    * nothing, and injecting a synthetic event to break the poll would show up as input the app never received.
    */
  override def wake(): Unit =
    val _ = wakeCounter.incrementAndGet()

  /** Counts the request rather than acting on it: this backend keeps whole frames instead of diffing against a
    * baseline, so there is nothing here to invalidate — but a test above `terminal` still needs to see that the app
    * asked.
    */
  override def requestFullRedraw(): Unit =
    val _ = fullRedrawCounter.incrementAndGet()

  override def copyToClipboard(text: String): Either[BackendError, Unit] =
    lastClipboard = Some(text)
    Right(())

  override def setTitle(title: String): Either[BackendError, Unit] =
    lastTitle = Some(title)
    Right(())

  override def suspend[A](body: => A): Either[BackendError, A] =
    val _      = suspendCounter.incrementAndGet()
    val wasAlt = alternateScreen
    val wasRaw = rawMode
    alternateScreen = false // observable to `body` so tests can assert the terminal was handed back
    rawMode = false
    try Right(body)
    finally
      alternateScreen = wasAlt
      rawMode = wasRaw

  override def clearRegion(kind: ClearType): Either[BackendError, Unit] =
    clears.synchronized { val _ = clears += kind }
    Right(())

  override def printAbove(lines: Seq[String]): Either[BackendError, Unit] =
    printedLines.synchronized { printedLines ++= lines }
    Right(())

  /** Releases the simulated terminal, so a test can assert the runner tore everything down on its way out. There is no
    * device to fail, so this always succeeds.
    */
  def close(): Either[BackendError, Unit] =
    mouseCapture = None
    cursorVisible = true
    alternateScreen = false
    rawMode = false
    caret = None
    Right(())

  // ---- test-driver surface ----

  /** Queues a synthetic event for the runner to read. Safe from any thread. */
  def postEvent(event: Event): Unit = events.put(event)

  /** Changes the reported terminal size and posts the matching resize event. */
  def resizeTo(size: Size): Unit =
    terminalSize = size
    postEvent(Event.Resize(size))

  /** Snapshot of the most recently drawn frame. */
  def lastDrawn: Option[Buffer] = lastFrame

  /** How many frames have been flushed. */
  def drawCount: Long = drawCounter.get()

  /** How many reads timed out with an empty queue — each one means the runner went idle. */
  def idleReads: Long = idleReadCounter.get()

  def pendingEvents: Int = events.size()

  /** How many times the app suspended the terminal (see [[suspend]]). */
  def suspendCount: Long = suspendCounter.get()

  /** How many times [[wake]] was called — i.e. how often background work asked the render thread to look again. */
  def wakeCount: Long = wakeCounter.get()

  /** How many times a full repaint was requested via [[requestFullRedraw]]. */
  def fullRedrawCount: Long = fullRedrawCounter.get()

  /** The erases requested via [[clearRegion]], in order. */
  def clearedRegions: Seq[ClearType] = clears.synchronized(clears.toSeq)

  /** The lines emitted above the app via [[printAbove]], in order. */
  def printedAbove: Seq[String] = printedLines.synchronized(printedLines.toSeq)

  /** Where the hardware caret was last parked by [[setCursorPosition]], or `None` if it never was (or the backend has
    * been closed).
    */
  def cursorPosition: Option[Position] = caret

  /** How many times the caret was moved. A frame that repaints nothing should move it at most once, so this is what
    * catches a render loop emitting a needless cursor move on every tick.
    */
  def cursorMoveCount: Long = caretMoveCounter.get()

  def isRawMode: Boolean         = rawMode
  def isAlternateScreen: Boolean = alternateScreen
  def isMouseCaptured: Boolean   = mouseCapture.isDefined

  /** Which capture mode was last requested, or `None` while capture is off — so a test can assert that an app asking
    * for hover really did ask for all-motion tracking, not merely for "the mouse".
    */
  def mouseCaptureMode: Option[MouseCaptureMode] = mouseCapture
  def isCursorVisible: Boolean                   = cursorVisible

  /** The text most recently sent to the clipboard via [[copyToClipboard]], if any. */
  def clipboardContents: Option[String] = lastClipboard

  /** The window title most recently requested via [[setTitle]], if any. */
  def titleContents: Option[String] = lastTitle
