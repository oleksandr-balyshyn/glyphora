package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Position, Size, Widget}

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
  // no device to ask, so nothing is known about pixels until a test says otherwise — see `pixelsTo`
  @volatile private var pixelSize: Option[Size]                = None
  @volatile private var lastFrame: Option[Buffer]              = None
  @volatile private var rawMode                                = false
  @volatile private var alternateScreen                        = false
  @volatile private var mouseCapture: Option[MouseCaptureMode] = None
  @volatile private var cursorVisible                          = true
  // blinking is the terminal's own default, so that is where a freshly built backend starts
  @volatile private var cursorBlinking                         = true

  @volatile private var cursorShape: CursorShape      = CursorShape.Default
  @volatile private var lastClipboard: Option[String] = None
  @volatile private var lastTitle: Option[String]     = None
  @volatile private var caret: Option[Position]       = None
  @volatile private var inlineRows                    = 0
  private val caretMoveCounter                        = AtomicLong(0)
  private val appendedLineCounter                     = AtomicLong(0)
  private val drawCounter                             = AtomicLong(0)
  private val idleReadCounter                         = AtomicLong(0)
  private val suspendCounter                          = AtomicLong(0)
  private val wakeCounter                             = AtomicLong(0)
  private val fullRedrawCounter                       = AtomicLong(0)
  private val printedLines                            = scala.collection.mutable.ArrayBuffer.empty[String]
  private val rawWrites                               = scala.collection.mutable.ArrayBuffer.empty[String]
  private val clears                                  = scala.collection.mutable.ArrayBuffer.empty[ClearType]
  private val insertedBlocks                          = scala.collection.mutable.ArrayBuffer.empty[Buffer]
  private val sizeRequests                            = scala.collection.mutable.ArrayBuffer.empty[Size]
  private val scrolls                                 =
    scala.collection.mutable.ArrayBuffer.empty[(RowRange, Int, ScrollDirection)]

  def size: Either[BackendError, Size] = Right(terminalSize)

  /** The cell size, plus whatever [[pixelsTo]] was last told. `pixels` is empty until a test sets one, which is what a
    * real terminal that does not answer `CSI 14 t` also reports.
    */
  override def windowSize: Either[BackendError, WindowSize] = Right(WindowSize(terminalSize, pixelSize))

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

  /** Records the requested blink state. A real terminal may ignore DECSET 12 entirely, and this one deliberately does
    * not model that: the question a test asks is what the app *requested*, since that is the only half the app
    * controls.
    */
  override def setCursorBlink(blinking: Boolean): Either[BackendError, Unit] =
    cursorBlinking = blinking
    Right(())

  /** Records the requested shape instead of emitting DECSCUSR — there is no device here to obey it. A test asserts on
    * [[currentCursorShape]], which is the whole point of this backend.
    */
  override def setCursorShape(shape: CursorShape): Either[BackendError, Unit] =
    cursorShape = shape
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

  /** Records the request and, unlike a real emulator, honours it.
    *
    * A real terminal is free to refuse, so an app must never depend on a resize arriving. This backend grants every
    * request instead, for two reasons: there is no window manager here to model a refusal faithfully, and a test that
    * wants to see the app's reaction to a resize already has [[resizeTo]] to force one. Delegating to `resizeTo` means
    * the app sees a genuine `Event.Resize` through its normal path rather than a size that changed behind its back.
    *
    * [[requestedSizes]] keeps what was asked for, which is the half a test asserting on the *app* wants: it can check
    * the app asked for the right shape without also depending on the grant.
    */
  override def requestSize(size: Size): Either[BackendError, Unit] =
    require(size.width > 0 && size.height > 0, s"requestSize needs a positive size, got $size")
    sizeRequests.synchronized { val _ = sizeRequests += size }
    resizeTo(size)
    Right(())

  /** Records the scroll and applies it to the retained frame, so a test sees the rows where the terminal would have put
    * them.
    *
    * The trait's default is a failure — "this backend has no scroll region" — so that a caller knows to repaint the
    * rows itself. This backend answers `Right` instead, because it *can* model the operation exactly: there is no
    * device to refuse, and a test driving an app that scrolls this way needs to see the app's own frames rather than
    * its fallback path. A test that wants the fallback exercised should drive a backend whose default stands.
    */
  override def scrollRegionUp(region: RowRange, lines: Int): Either[BackendError, Unit] =
    recordScroll(region, lines, ScrollDirection.Up)

  override def scrollRegionDown(region: RowRange, lines: Int): Either[BackendError, Unit] =
    recordScroll(region, lines, ScrollDirection.Down)

  private def recordScroll(region: RowRange, lines: Int, direction: ScrollDirection): Either[BackendError, Unit] =
    if lines <= 0 then Right(())
    else
      scrolls.synchronized { val _ = scrolls += ((region, lines, direction)) }
      lastFrame = lastFrame.map(frame => ScrollDirection.shifted(frame, region, lines, direction))
      Right(())

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

  override def writeRaw(sequence: String): Either[BackendError, Unit] =
    rawWrites.synchronized { val _ = rawWrites += sequence }
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
      val buffer = Backend.renderBlock(terminalSize.width, height, widget)
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
    cursorBlinking = true

    cursorShape = CursorShape.Default
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
    // The read function hands over the next code unit, then reports that nothing arrived. It must answer
    // `ReadExpired`, not `EndOfStream`: end of file is a permanent condition the decoder reports as
    // `Event.EndOfInput` on every later call, so a script that ended with it would decode into an unbounded run of
    // those events rather than stopping — the loop below would never see the `None` that ends it.
    val decoder   = InputDecoder(_ => if remaining.hasNext then remaining.next() else InputDecoder.ReadExpired)
    var draining  = codeUnits.nonEmpty
    while draining do
      decoder.decode(0L) match
        // A decoded event does not end the drain — the decoder may still hold a pushed-back character — but the
        // same exhaustion test has to run here too, or a decoder that keeps answering would spin forever.
        case Some(event) =>
          postEvent(event)
          draining = remaining.hasNext || decoder.hasPushback
        // an undecodable sequence yields no event but may have consumed only part of the input, so keep going while
        // there is any left — or while the decoder is holding a character it pushed back, which is the whole reason a
        // decoded event does not end the loop either. `ESC [ ESC` ends that way: the torn CSI hands the trailing `ESC`
        // back, and only the next decode turns it into the Escape keypress a real terminal would have delivered
        case None        => draining = remaining.hasNext || decoder.hasPushback

  /** Sets the pixel size [[windowSize]] reports, or clears it with `None`.
    *
    * The seam that makes the pixel half of [[WindowSize]] testable at all: no headless backend has a device to ask, so
    * a test states the answer a terminal would have given. Posts no event — pixel geometry is not a resize, and a test
    * that wants both calls [[resizeTo]] as well.
    */
  def pixelsTo(pixels: Option[Size]): Unit = pixelSize = pixels

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

  /** Every region scroll asked for via [[scrollRegionUp]] or [[scrollRegionDown]], in order, as
    * `(rows, lines, direction)`.
    *
    * The interesting assertion is usually the *count*: an app that scrolls a list one row and repaints the whole list
    * anyway is a correct app doing the expensive thing, and this is what tells the two apart.
    */
  def regionScrolls: Seq[(RowRange, Int, ScrollDirection)] = scrolls.synchronized(scrolls.toSeq)

  /** The sizes asked for via [[requestSize]], in order — what the app wanted, as opposed to what it got. */
  def requestedSizes: Seq[Size] = sizeRequests.synchronized(sizeRequests.toSeq)

  /** The erases requested via [[clearRegion]], in order. */
  def clearedRegions: Seq[ClearType] = clears.synchronized(clears.toSeq)

  /** The styled blocks emitted above the app via [[insertBefore]], in order, each as the [[Buffer]] the widget was
    * rendered into. Their text also appears in [[printedAbove]]; this is the view that still carries the styling.
    */
  def insertedAbove: Seq[Buffer] = insertedBlocks.synchronized(insertedBlocks.toSeq)

  /** The lines emitted above the app via [[printAbove]] or [[insertBefore]], in order. */
  def printedAbove: Seq[String] = printedLines.synchronized(printedLines.toSeq)

  /** Every sequence handed to [[writeRaw]], in order — the out-of-band payloads (an image protocol's escape sequence,
    * say) an application sent past the frame diff.
    */
  def rawSequences: Seq[String] = rawWrites.synchronized(rawWrites.toSeq)

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

  /** Whether the app last asked for a blinking caret (see [[setCursorBlink]]). `true` for a backend nothing has asked,
    * because blinking is the terminal's own default — so a test asserting `false` is asserting the app really did ask.
    */
  def isCursorBlinking: Boolean = cursorBlinking

  /** The shape last asked for through [[setCursorShape]]; [[CursorShape.Default]] until something asks, and again after
    * [[close]] — so a test can assert an app handed the user's own cursor shape back on its way out.
    */
  def currentCursorShape: CursorShape = cursorShape

  /** How many rows an inline run reserved on the primary screen; `0` for a full-screen run. */
  def reservedInlineRows: Int = inlineRows

  /** The text most recently sent to the clipboard via [[copyToClipboard]], if any. */
  def clipboardContents: Option[String] = lastClipboard

  /** The window title most recently requested via [[setTitle]], if any. */
  def titleContents: Option[String] = lastTitle
