package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Size}

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.Duration

/** An in-memory [[Backend]] for headless end-to-end testing (adapted from Textual's headless driver, PLAN.md §9):
  * renders into a retained snapshot instead of a TTY and reads synthetic events posted by the test driver.
  *
  * Thread contract: the runner calls `readEvent`/`draw` on the render thread while a test thread posts events and
  * inspects `lastDrawn` — hence the blocking queue and volatile snapshot.
  */
final class HeadlessBackend(initialSize: Size) extends Backend:

  private val events                              = LinkedBlockingQueue[Event]()
  @volatile private var terminalSize              = initialSize
  @volatile private var lastFrame: Option[Buffer] = None
  @volatile private var rawMode                   = false
  @volatile private var alternateScreen           = false
  @volatile private var mouseCapture              = false
  @volatile private var cursorVisible             = true
  private val drawCounter                         = AtomicLong(0)
  private val idleReadCounter                     = AtomicLong(0)

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

  def enableMouseCapture(): Either[BackendError, Unit] =
    mouseCapture = true
    Right(())

  def disableMouseCapture(): Either[BackendError, Unit] =
    mouseCapture = false
    Right(())

  def hideCursor(): Either[BackendError, Unit] =
    cursorVisible = false
    Right(())

  def showCursor(): Either[BackendError, Unit] =
    cursorVisible = true
    Right(())

  def readEvent(timeout: Duration): Either[BackendError, Option[Event]] =
    val polled =
      if timeout.isFinite then Option(events.poll(timeout.toMillis, TimeUnit.MILLISECONDS))
      else Some(events.take())
    if polled.isEmpty then
      val _ = idleReadCounter.incrementAndGet()
    Right(polled)

  def close(): Unit = ()

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

  def isRawMode: Boolean         = rawMode
  def isAlternateScreen: Boolean = alternateScreen
  def isMouseCaptured: Boolean   = mouseCapture
  def isCursorVisible: Boolean   = cursorVisible
