package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, CharWidth, Event, Size}

import org.jline.terminal.{Terminal, TerminalBuilder}
import org.jline.utils.InfoCmp

import java.io.InterruptedIOException
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

/** [[Backend]] implementation over JLine 3's system terminal.
  *
  * Owns the JLine `Terminal` for its whole lifetime: construct via [[JLine3Backend.create]], release with `close()`
  * (which restores cooked mode, the main screen, and cursor visibility if still active). `draw` keeps a snapshot of the
  * last flushed frame and writes only the diff.
  *
  * Signals are owned here rather than left to JLine's defaults. `INT`/`QUIT` become [[Event.Interrupt]] so the runner
  * unwinds through its normal teardown; `TSTP`/`CONT` hand the terminal back to the shell and take it again on resume;
  * `WINCH` posts a coalesced resize. See [[JLine3Backend.create]] for why the defaults are unusable.
  */
final class JLine3Backend private (terminal: Terminal, colorDepth: ColorDepth) extends Backend:

  // written on the render thread, read by JLine's signal-dispatch thread and by the shutdown hook
  @volatile private var savedAttributes: Option[org.jline.terminal.Attributes] = None
  @volatile private var alternateScreenActive                                  = false
  @volatile private var mouseCaptureActive                                     = false
  @volatile private var cursorHidden                                           = false
  @volatile private var suspendedState                                         = TerminalState.None

  // owned by the render thread alone — no other thread may read or write it. A thread that takes the screen away (the
  // SIGCONT handler re-entering the alternate screen) raises `fullRedrawRequested` instead: a reset written here from
  // the signal-dispatch thread would be overwritten by an in-flight `draw`'s snapshot and the repaint would be lost.
  private var lastFlushed: Option[Buffer] = scala.None

  // raised by any thread that disturbed the screen (alternate-screen entry, SIGCONT's reacquire), consumed by `draw`
  private val fullRedrawRequested = RedrawRequest()

  private val pendingResize    = AtomicReference[Option[Size]](scala.None)
  private val pendingInterrupt = AtomicBoolean(false)
  private val woken            = AtomicBoolean(false)
  private val pollingThread    = AtomicReference[Thread](null) // scalafix:ok DisableSyntax; JLine Java API returns null
  private val decoder          = InputDecoder(timeoutMillis => terminal.reader().read(timeoutMillis))

  private val supportsAlternateScreen =
    terminal.getStringCapability(
      InfoCmp.Capability.enter_ca_mode
    ) != null // scalafix:ok DisableSyntax; JLine Java API returns null

  terminal.handle(Terminal.Signal.WINCH, _ => onResize())
  // INT/QUIT must not kill the JVM: the process would die before any teardown and hand back a raw, alt-screen terminal
  terminal.handle(Terminal.Signal.INT, _ => onInterrupt())
  terminal.handle(Terminal.Signal.QUIT, _ => onInterrupt())
  terminal.handle(Terminal.Signal.TSTP, _ => onStop())
  terminal.handle(Terminal.Signal.CONT, _ => onContinue())

  def size: Either[BackendError, Size] = attempt(currentSize)

  def draw(buffer: Buffer): Either[BackendError, Unit] =
    // claimed before the frame is composed, so a request raised while this frame is in flight survives for the next one
    val forced = fullRedrawRequested.claim()
    val result = attempt {
      val previous = if forced then scala.None else lastFlushed
      val body     = encodeChangedCells(previous.getOrElse(Buffer(buffer.area)), buffer)
      // an unchanged frame writes nothing at all, so a redraw-on-tick app with a static screen stays silent
      if body.nonEmpty then
        // one atomic update: the terminal shows the previous frame until the whole batch has arrived
        val frame =
          AnsiSequences.BeginSynchronized + body + AnsiSequences.ResetStyle + AnsiSequences.EndSynchronized
        terminal.writer().write(frame)
        terminal.writer().flush()
      lastFlushed = Some(buffer.snapshot)
    }
    // the forced frame never reached the terminal and the baseline was not updated: the request has not been served
    if forced && result.isLeft then requestFullRedraw()
    result

  /** Asks the next [[draw]] to repaint every cell. Safe to call from any thread.
    *
    * Raised whenever the screen stops showing what `lastFlushed` describes: the alternate screen was just cleared, or
    * something else owned the terminal in between (the shell, between SIGTSTP and SIGCONT). A flag rather than a reset
    * of the baseline keeps `lastFlushed` render-thread-private, so a request raised while a `draw` is in flight is
    * consumed by the *following* frame instead of being overwritten by that frame's snapshot.
    */
  private[terminal] def requestFullRedraw(): Unit = fullRedrawRequested.raise()

  /** The cell-level ANSI for everything that differs between `previous` and `next`, in row-major order.
    *
    * Cursor position, SGR state and the open hyperlink carry across cells, so each is re-emitted only where it actually
    * changes — that suppression is most of the reason a diffed frame is small enough to write in one go. Returns the
    * empty string when the frame is unchanged.
    */
  private def encodeChangedCells(previous: Buffer, next: Buffer): String =
    val body                        = StringBuilder()
    var expectedX                   = -1
    var currentY                    = -1
    var currentStyle                = ""
    var currentLink: Option[String] = scala.None
    previous.diff(
      next,
      (x, y, cell) => {
        if y != currentY || x != expectedX then body ++= AnsiSequences.moveTo(x, y)
        val sgr = AnsiSequences.sgr(cell.style, colorDepth)
        if sgr != currentStyle then
          body ++= sgr
          currentStyle = sgr
        if cell.style.link != currentLink then
          if currentLink.nonEmpty then body ++= AnsiSequences.LinkClose
          cell.style.link.foreach(url => body ++= AnsiSequences.linkOpen(url))
          currentLink = cell.style.link
        body ++= cell.symbol
        currentY = y
        expectedX = x + math.max(1, CharWidth.of(cell.symbol))
      },
    )
    // a link left open would swallow everything drawn after this frame
    if currentLink.nonEmpty then body ++= AnsiSequences.LinkClose
    body.result()

  def enableRawMode(): Either[BackendError, Unit] =
    attempt {
      savedAttributes = Some(terminal.enterRawMode())
      // modern input modes; terminals without support ignore them and keep legacy behavior
      write(AnsiSequences.EnableBracketedPaste)
      write(AnsiSequences.EnableFocusReporting)
      write(AnsiSequences.PushKittyKeyboard)
    }

  def disableRawMode(): Either[BackendError, Unit] =
    savedAttributes match
      case scala.None       => Left(BackendError.NotInRawMode)
      case Some(attributes) =>
        attempt {
          write(AnsiSequences.PopKittyKeyboard)
          write(AnsiSequences.DisableFocusReporting)
          write(AnsiSequences.DisableBracketedPaste)
          terminal.setAttributes(attributes)
          savedAttributes = scala.None
        }

  /** Enters the alternate screen, or reports that the terminal has none.
    *
    * Gated on terminfo's `smcup`: the Linux console (`TERM=linux`) has no alternate screen, so emitting `CSI ?1049h`
    * there paints the app over the user's scrollback and never gives it back. Failing loudly beats destroying history.
    */
  def enterAlternateScreen(): Either[BackendError, Unit] =
    if !supportsAlternateScreen then
      Left(BackendError.UnsupportedTerminal(s"${terminal.getType} has no alternate screen (no smcup capability)"))
    else
      attempt {
        write(AnsiSequences.EnterAlternateScreen)
        write(AnsiSequences.ClearScreen)
        alternateScreenActive = true
        requestFullRedraw() // the alternate screen starts blank; the next draw must repaint everything
      }

  def leaveAlternateScreen(): Either[BackendError, Unit] =
    attempt {
      write(AnsiSequences.LeaveAlternateScreen)
      alternateScreenActive = false
    }

  def enableMouseCapture(): Either[BackendError, Unit] =
    attempt {
      write(AnsiSequences.EnableMouseCapture)
      mouseCaptureActive = true
    }

  def disableMouseCapture(): Either[BackendError, Unit] =
    attempt {
      write(AnsiSequences.DisableMouseCapture)
      mouseCaptureActive = false
    }

  def hideCursor(): Either[BackendError, Unit] =
    attempt {
      write(AnsiSequences.HideCursor)
      cursorHidden = true
    }

  def showCursor(): Either[BackendError, Unit] =
    attempt {
      write(AnsiSequences.ShowCursor)
      cursorHidden = false
    }

  def readEvent(timeout: Duration): Either[BackendError, Option[Event]] =
    require(timeout > Duration.Zero, s"readEvent timeout must be positive or infinite, got $timeout")
    if pendingInterrupt.getAndSet(false) then Right(Some(Event.Interrupt))
    else
      pendingResize.getAndSet(scala.None) match
        case Some(resized) => Right(Some(Event.Resize(resized)))
        case scala.None    =>
          // something queued render-thread work while we were away: go round the loop instead of blocking again
          if woken.getAndSet(false) then Right(scala.None) else blockingRead(timeout)

  private def blockingRead(timeout: Duration): Either[BackendError, Option[Event]] =
    pollingThread.set(Thread.currentThread())
    try Right(decoder.decode(JLine3Backend.readTimeoutMillis(timeout)))
    catch
      case _: InterruptedIOException => Right(scala.None) // woken deliberately by `wake()`
      case NonFatal(error)           => Left(BackendError.Io(error))
    finally
      pollingThread.set(null) // scalafix:ok DisableSyntax; JLine Java API returns null
      val _ = Thread.interrupted() // drop an interrupt that landed after the read completed

  /** Cuts short an in-flight [[readEvent]].
    *
    * JLine's reader waits on a monitor and converts an interrupt into an `InterruptedIOException` that it throws *and
    * clears* (`NonBlockingReaderImpl.read`), so the reader stays usable and no buffered input is lost.
    */
  override def wake(): Unit =
    woken.set(true)
    val blocked = pollingThread.get()
    if blocked != null && (blocked ne Thread.currentThread()) then
      blocked.interrupt() // scalafix:ok DisableSyntax; JLine Java API returns null

  override def copyToClipboard(text: String): Either[BackendError, Unit] =
    attempt(write(AnsiSequences.clipboardCopy(text)))

  override def suspend[A](body: => A): Either[BackendError, A] =
    attempt {
      val released = releaseTerminal()
      try body
      finally reacquireTerminal(released)
    }

  override def printAbove(lines: Seq[String]): Either[BackendError, Unit] =
    // step out to the primary screen so the lines land in real scrollback, print them, then step back in and repaint
    suspend {
      lines.foreach { line =>
        // these strings reach the terminal uninterpreted, so they get the same control-stripping as link targets
        terminal.writer().write(AnsiSequences.stripControls(line))
        terminal.writer().write("\r\n")
      }
      terminal.writer().flush()
    }

  def close(): Unit =
    val _ = releaseTerminal()
    try terminal.close()
    catch case NonFatal(_) => ()

  /** Last-resort restore, for a shutdown hook that may be racing JLine's own terminal closer.
    *
    * Writes straight to the process's stdout descriptor rather than through the JLine writer: once JLine's
    * `ShutdownHooks` closer has run, `terminal.writer()` throws `IllegalStateException: Terminal has been closed` and
    * every teardown write is silently discarded. Every sequence emitted is an idempotent mode *reset*, so this is safe
    * to call even when nothing was enabled, and safe to call twice.
    */
  override def emergencyRestore(): Unit =
    try
      val out = java.io.FileOutputStream(java.io.FileDescriptor.out)
      out.write(AnsiSequences.RestoreAll.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      out.flush()
    catch case NonFatal(_) => ()

  /** Hands the terminal back to the shell, returning what was active so [[reacquireTerminal]] can restore it. */
  private def releaseTerminal(): TerminalState =
    val state = TerminalState(savedAttributes.nonEmpty, alternateScreenActive, cursorHidden, mouseCaptureActive)
    if state.mouse then bestEffort(disableMouseCapture())
    if state.cursorHidden then bestEffort(showCursor())
    if state.alternateScreen then bestEffort(leaveAlternateScreen())
    if state.raw then bestEffort(disableRawMode())
    try terminal.writer().flush()
    catch case NonFatal(_) => ()
    state

  private def reacquireTerminal(state: TerminalState): Unit =
    if state.raw then bestEffort(enableRawMode())
    if state.alternateScreen then bestEffort(enterAlternateScreen())
    if state.cursorHidden then bestEffort(hideCursor())
    if state.mouse then bestEffort(enableMouseCapture())
    requestFullRedraw() // whatever ran in between owned the screen: repaint everything

  private def onResize(): Unit =
    pendingResize.set(Some(currentSize))
    wake()

  private def onInterrupt(): Unit =
    pendingInterrupt.set(true)
    wake()

  /** SIGTSTP: undress the terminal, then stop for real by re-raising with the default disposition. */
  private def onStop(): Unit =
    suspendedState = releaseTerminal()
    JLine3Backend.stopSelf()

  /** SIGCONT: take the terminal back and force a full repaint at whatever size it is now. */
  private def onContinue(): Unit =
    reacquireTerminal(suspendedState)
    suspendedState = TerminalState.None
    onResize()

  private def currentSize: Size =
    val jlineSize = terminal.getSize
    Size(jlineSize.getColumns, jlineSize.getRows)

  private def bestEffort(step: Either[BackendError, Unit]): Unit =
    step.left.foreach(JLine3Backend.logTeardownFailure)

  private def write(sequence: String): Unit =
    terminal.writer().write(sequence)
    terminal.writer().flush()

  private def attempt[A](body: => A): Either[BackendError, A] =
    try Right(body)
    catch case NonFatal(error) => Left(BackendError.Io(error))

/** Which terminal modes were active at a given moment, so they can be restored in the same shape. */
private[terminal] final case class TerminalState(
    raw: Boolean,
    alternateScreen: Boolean,
    cursorHidden: Boolean,
    mouse: Boolean,
)

private[terminal] object TerminalState:
  val None: TerminalState = TerminalState(false, false, false, false)

object JLine3Backend:

  /** Wraps an already-built JLine terminal.
    *
    * [[create]] is the production entry point; this exists so tests can drive a real backend over a pair of streams,
    * because `create` needs the controlling TTY that CI does not have.
    */
  private[terminal] def wrapping(terminal: Terminal, colorDepth: ColorDepth): JLine3Backend =
    JLine3Backend(terminal, colorDepth)

  /** Opens the process's controlling terminal. Fails with `UnsupportedTerminal` when there is no usable TTY.
    *
    * `colorDepth` defaults to environment-based detection (honoring `NO_COLOR`/`CLICOLOR_FORCE`); pass an explicit
    * value to force a palette regardless of the environment.
    */
  def create(colorDepth: ColorDepth = ColorDepth.detect()): Either[BackendError, JLine3Backend] =
    try
      val terminal = TerminalBuilder
        .builder()
        .system(true)
        // With JLine's default SIG_DFL handler, PosixSysTerminal calls sun.misc.Signal.handle(sig, SIG_DFL) for every
        // Terminal.Signal, which strips the JVM's own SIGINT handler: Ctrl+C then terminates the process outright, no
        // shutdown hook runs, and the terminal is handed back raw and on the alternate screen. Any non-SIG_DFL value
        // makes JLine install Java-level handlers instead, which `terminal.handle` can route into the event loop.
        .signalHandler(Terminal.SignalHandler.SIG_IGN)
        // Never inherit the platform charset: every border and glyph in tui-widgets is non-ASCII, and under a POSIX
        // locale a locale-derived encoder renders the entire UI as '?'. All three must be set — `encoding` is only
        // the fallback JLine consults *after* the `stdin.encoding`/`stdout.encoding` system properties, so on its own
        // it is silently ignored wherever the JDK derived those from the locale.
        .encoding(java.nio.charset.StandardCharsets.UTF_8)
        .stdinEncoding(java.nio.charset.StandardCharsets.UTF_8)
        .stdoutEncoding(java.nio.charset.StandardCharsets.UTF_8)
        .build()
      if terminal.getType == Terminal.TYPE_DUMB || terminal.getType == Terminal.TYPE_DUMB_COLOR then
        terminal.close()
        Left(BackendError.UnsupportedTerminal("dumb terminal (no TTY attached)"))
      else Right(wrapping(terminal, colorDepth))
    catch case NonFatal(error) => Left(BackendError.Io(error))

  /** Stops this process the way the shell expects, after the TSTP handler has handed the terminal back.
    *
    * JLine replaced SIGTSTP's default disposition when the terminal was built — that is what makes
    * `Terminal.Signal.TSTP` routable at all — so returning from the handler would otherwise leave the app running after
    * Ctrl+Z.
    *
    * SIGSTOP rather than re-raising SIGTSTP: it cannot be caught, blocked or ignored, so it always stops us, whereas
    * `sun.misc.Signal.raise` needs a Java handler still installed for the signal it is raising — exactly what we just
    * removed. Sending it costs a `fork` per Ctrl+Z, which is invisible at human speed. If this fails (no `kill`, or
    * Windows, which has no SIGTSTP at all) the app simply keeps running with its terminal restored — the same behaviour
    * as before, never a wedged or half-torn-down state.
    */
  private[terminal] def stopSelf(): Unit =
    try
      val pid     = ProcessHandle.current().pid()
      val stopped = ProcessBuilder("kill", "-STOP", pid.toString).start()
      val _       = stopped.waitFor()
    catch case NonFatal(_) => ()

  /** The millisecond timeout to hand JLine's reader for a [[Backend.readEvent]] timeout.
    *
    * `Duration.Infinite.toMillis` *throws*, so an infinite timeout — which [[Backend.readEvent]] documents and
    * `HeadlessBackend` implements as "block until an event arrives" — used to be caught as an I/O failure and reported
    * as `BackendError.Io`, which the runner treats as fatal. JLine reads a non-positive timeout as an unbounded
    * blocking read, which is exactly what was asked for.
    */
  private[terminal] def readTimeoutMillis(timeout: Duration): Long =
    if timeout.isFinite then timeout.toMillis else 0L

  /** Teardown steps are best-effort, but a silent failure is how a corrupted terminal goes unnoticed for months. */
  private[terminal] def logTeardownFailure(error: BackendError): Unit =
    if System.getenv("GLYPHORA_DEBUG") != null then // scalafix:ok DisableSyntax; JLine Java API returns null
      System.err.println(s"glyphora: terminal teardown step failed: $error")

/** The "repaint every cell next frame" request that [[JLine3Backend]] uses to keep `lastFlushed` render-thread-private.
  *
  * A separate value rather than a bare flag because the ordering is the whole point and is worth testing on its own:
  * `draw` [[claim]]s before it composes a frame, so a [[raise]] from another thread *during* that frame is not consumed
  * by it and survives for the next one. Writing the baseline directly from the raising thread would instead lose the
  * repaint, because the in-flight frame's snapshot would overwrite it.
  *
  * Every operation is safe from any thread.
  */
private[terminal] final class RedrawRequest:

  private val raised = AtomicBoolean(false)

  /** Asks the next claim to repaint everything. Idempotent: two raises before a claim are one repaint. */
  def raise(): Unit = raised.set(true)

  /** Takes the pending request, if any, and clears it. Call before composing the frame that will serve it. */
  def claim(): Boolean = raised.getAndSet(false)

  /** Whether a request is pending, without taking it. Test and diagnostic use only. */
  def isPending: Boolean = raised.get()
