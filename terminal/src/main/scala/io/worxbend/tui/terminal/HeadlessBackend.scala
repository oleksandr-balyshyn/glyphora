package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Position, Rect, Size, Widget}

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
  @volatile private var inlineRows                             = 0
  private val caretMoveCounter                                 = AtomicLong(0)
  private val appendedLineCounter                              = AtomicLong(0)
  private val drawCounter                                      = AtomicLong(0)
  private val idleReadCounter                                  = AtomicLong(0)
  private val suspendCounter                                   = AtomicLong(0)
  private val wakeCounter                                      = AtomicLong(0)
  private val fullRedrawCounter                                = AtomicLong(0)
  private val printedLines                                     = scala.collection.mutable.ArrayBuffer.empty[String]
  private val clears                                           = scala.collection.mutable.ArrayBuffer.empty[ClearType]
  private val insertedBlocks                                   = scala.collection.mutable.ArrayBuffer.empty[Buffer]

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

  override def reserveInlineRows(rows: Int): Either[BackendError, Unit] =
    inlineRows = math.max(0, rows)
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

  /** Records the rendered block itself, not only its text.
    *
    * The inherited implementation would render the widget and then flatten it to plain strings for [[printAbove]],
    * which throws away the styling that is the whole reason this method exists — a test could then never tell a bold
    * red `ERROR` prefix from the word "ERROR". The block is kept as a [[Buffer]] for [[insertedAbove]] to hand out, and
    * its text is *also* appended to [[printedAbove]], so a test written against the plain-text view still sees
    * everything the app emitted above the UI, in the order it emitted it.
    */
  override def insertBefore(height: Int, widget: Widget): Either[BackendError, Unit] =
    if height <= 0 then Right(())
    else
      val area   = Rect(0, 0, terminalSize.width, height)
      val buffer = Buffer(area)
      widget.render(area, buffer)
      insertedBlocks.synchronized { val _ = insertedBlocks += buffer }
      printedLines.synchronized { printedLines ++= Backend.plainRows(buffer) }
      Right(())

  /** Adds `n` to the running total a test asserts on. There is no simulated scrollback to move rows into, so the count
    * is the whole observable effect — which is exactly the question an inline-viewport test asks: how much room did the
    * app ask the shell for?
    */
  override def appendLines(n: Int): Either[BackendError, Unit] =
    if n <= 0 then Right(())
    else
      val _ = appendedLineCounter.addAndGet(n.toLong)
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

  /** Feeds raw terminal input through the *production* [[InputDecoder]] and queues whatever it decodes.
    *
    * [[postEvent]] hands the runner an [[Event]] a test built by hand, which skips the decoder entirely. That is the
    * right thing for most tests and the wrong thing for one: an application binding spelled `ctrl+s` and a decoder that
    * turns byte `0x13` into some other key would both look correct on their own, and the app would still be dead in a
    * real terminal. Posting the bytes joins the two halves — the key spec the app is written with, and the sequence a
    * terminal actually sends.
    *
    * `codeUnits` are UTF-16 code units as a terminal reader hands them back (that is what `JLine3Backend` reads), not
    * UTF-8 bytes. A sequence the decoder deliberately drops — a device-attributes reply, say — queues nothing at all,
    * which is exactly what a test about such a reply wants to assert.
    *
    * Decoding happens on the calling thread and is finished before this returns, so the decoder's one-reader rule
    * holds: the app thread only ever takes completed events off the queue. A trailing lone `ESC` costs the decoder's
    * escape timeout (50 ms) before it reports, the same as it would at a real terminal.
    */
  def postInput(codeUnits: Seq[Int]): Unit =
    val remaining = codeUnits.iterator
    // the decoder's read function: hand over the next code unit, or the "nothing available" sentinel once they run out
    val decoder   = InputDecoder(_ => if remaining.hasNext then remaining.next() else -1)
    var draining  = codeUnits.nonEmpty
    while draining do
      decoder.decode(0L) match
        case Some(event) => postEvent(event)
        // an undecodable sequence yields no event but may have consumed only part of the input, so keep going while
        // there is any left; the decoder may also be holding a pushed-back character, which is why a decoded event
        // does not end the loop either
        case None        => draining = remaining.hasNext

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

  /** The styled blocks emitted above the app via [[insertBefore]], in order, each as the [[Buffer]] the widget was
    * rendered into. Their text also appears in [[printedAbove]]; this is the view that still carries the styling.
    */
  def insertedAbove: Seq[Buffer] = insertedBlocks.synchronized(insertedBlocks.toSeq)

  /** The lines emitted above the app via [[printAbove]] or [[insertBefore]], in order. */
  def printedAbove: Seq[String] = printedLines.synchronized(printedLines.toSeq)

  /** Total rows scrolled off the top via [[appendLines]] — how much room an inline viewport asked the shell for. */
  def appendedLineCount: Long = appendedLineCounter.get()

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

  /** How many rows an inline run reserved on the primary screen; `0` for a full-screen run. */
  def reservedInlineRows: Int = inlineRows

  /** The text most recently sent to the clipboard via [[copyToClipboard]], if any. */
  def clipboardContents: Option[String] = lastClipboard

  /** The window title most recently requested via [[setTitle]], if any. */
  def titleContents: Option[String] = lastTitle
