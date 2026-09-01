package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Size}

import org.jline.terminal.{Attributes, Terminal, TerminalBuilder}
import org.jline.utils.InfoCmp

import java.io.{FileDescriptor, FileOutputStream, InterruptedIOException}
import java.nio.charset.StandardCharsets.UTF_8
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
  // holds the *cooked*-mode attributes captured when raw mode was entered, so it doubles as "are we in raw mode?"
  @volatile private var cookedAttributes: Option[Attributes]         = None
  @volatile private var alternateScreenActive                        = false
  // which capture mode is in force, or `None` for "capture is off" — the mode has to be remembered, not just the fact
  // of capture, so that taking the terminal back after Ctrl+Z re-requests all-motion tracking rather than silently
  // downgrading a hover-driven app to buttons-only
  @volatile private var mouseCaptureActive: Option[MouseCaptureMode] = None
  @volatile private var cursorHidden                                 = false
  @volatile private var suspendedState                               = TerminalState.Undressed

  // owned by the render thread alone — no other thread may read or write it. A thread that takes the screen away (the
  // SIGCONT handler re-entering the alternate screen) raises `fullRedrawRequested` instead: a reset written here from
  // the signal-dispatch thread would be overwritten by an in-flight `draw`'s snapshot and the repaint would be lost.
  private var lastFlushed: Option[Buffer] = None

  /** Serialises the three things that decide which screen the terminal is showing: writing a composed frame, handing
    * the terminal back to the shell, and taking it again. See [[releaseTerminal]] for what goes wrong without it.
    */
  private val screenOwnership = Object()

  // raised by any thread that disturbed the screen (alternate-screen entry, SIGCONT's reacquire), consumed by `draw`
  private val fullRedrawRequested = RedrawRequest()

  private val frameEncoder = FrameEncoder(colorDepth)

  // whether the shell's own window title has been pushed onto the terminal's title stack. Raised lazily by the first
  // `setTitle`, so an app that never sets a title emits nothing at all and leaves no stack entry for `close()` to pop.
  private val titlePushed = AtomicBoolean(false)

  private val pendingResize    = AtomicReference[Option[Size]](None)
  private val pendingInterrupt = AtomicBoolean(false)
  private val woken            = AtomicBoolean(false)
  // the thread currently parked in `blockingRead`, if any, so `wake` knows whom to interrupt
  private val pollingThread    = AtomicReference[Option[Thread]](None)
  private val decoder          = InputDecoder(timeoutMillis => terminal.reader().read(timeoutMillis))

  private val supportsAlternateScreen =
    terminal.getStringCapability(
      InfoCmp.Capability.enter_ca_mode
    ) != null // scalafix:ok DisableSyntax; getStringCapability returns null when the capability is absent

  terminal.handle(Terminal.Signal.WINCH, _ => onResize())
  // INT/QUIT must not kill the JVM: the process would die before any teardown and hand back a raw, alt-screen terminal
  terminal.handle(Terminal.Signal.INT, _ => onInterrupt())
  terminal.handle(Terminal.Signal.QUIT, _ => onInterrupt())
  terminal.handle(Terminal.Signal.TSTP, _ => onStop())
  terminal.handle(Terminal.Signal.CONT, _ => onContinue())

  def size: Either[BackendError, Size] = attempt(currentSize)

  def draw(buffer: Buffer): Either[BackendError, Unit] =
    // claimed before the frame is composed, so a request raised while this frame is in flight survives for the next one
    val forced  = fullRedrawRequested.claim()
    // a terminal that narrowed has already reflowed what was on screen, and the wrapped remnants sit outside the new,
    // smaller area where no amount of repainting reaches them — see ScreenReset for why only a shrink pays for this
    val erasing = ScreenReset.clearsOnShrink(lastFlushed.map(_.area), buffer.area)
    val result  = attempt {
      // after an erase the display is blank, so the frame is diffed against blankness rather than against a picture
      // the terminal no longer shows
      val previous = if forced || erasing then None else lastFlushed
      val body     = frameEncoder.encode(previous.getOrElse(Buffer(buffer.area)), buffer)
      // an unchanged frame writes nothing at all, so a redraw-on-tick app with a static screen stays silent — unless
      // the erase itself has to go out, which is the one case where "nothing changed" still needs a write
      if body.nonEmpty || erasing then
        // one atomic update: the terminal shows the previous frame until the whole batch has arrived
        val frame =
          AnsiSequences.BeginSynchronized + (if erasing then AnsiSequences.ClearScreen else "") +
            body + AnsiSequences.ResetStyle + AnsiSequences.EndSynchronized
        // under the monitor, so a Ctrl+Z landing mid-frame cannot leave the alternate screen between the two writes
        // and spill this frame's cursor moves and box-drawing over the user's shell
        screenOwnership.synchronized {
          terminal.writer().write(frame)
          terminal.writer().flush()
        }
      lastFlushed = Some(buffer.snapshot)
    }
    // the forced frame never reached the terminal and the baseline was not updated: the request has not been served
    if forced && result.isLeft then requestFullRedraw()
    result

  /** Asks the next [[draw]] to repaint every cell. Safe to call from any thread.
    *
    * Public through [[Backend.requestFullRedraw]] so an app whose screen was disturbed by something this backend did
    * not do — a subprocess it started itself rather than through [[suspend]] — has a supported way to recover.
    *
    * Raised whenever the screen stops showing what `lastFlushed` describes: the alternate screen was just cleared, or
    * something else owned the terminal in between (the shell, between SIGTSTP and SIGCONT). A flag rather than a reset
    * of the baseline keeps `lastFlushed` render-thread-private, so a request raised while a `draw` is in flight is
    * consumed by the *following* frame instead of being overwritten by that frame's snapshot.
    */
  override def requestFullRedraw(): Unit = fullRedrawRequested.raise()

  def enableRawMode(): Either[BackendError, Unit] =
    attempt {
      cookedAttributes = Some(terminal.enterRawMode())
      // modern input modes; terminals without support ignore them and keep legacy behavior
      write(AnsiSequences.EnableBracketedPaste)
      write(AnsiSequences.EnableFocusReporting)
      write(AnsiSequences.PushKittyKeyboard)
    }

  def disableRawMode(): Either[BackendError, Unit] =
    cookedAttributes match
      case None             => Left(BackendError.NotInRawMode)
      case Some(attributes) =>
        attempt {
          write(AnsiSequences.PopKittyKeyboard)
          write(AnsiSequences.DisableFocusReporting)
          write(AnsiSequences.DisableBracketedPaste)
          terminal.setAttributes(attributes)
          cookedAttributes = None
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

  def enableMouseCapture(): Either[BackendError, Unit] = enableMouseCapture(MouseCaptureMode.Buttons)

  override def enableMouseCapture(mode: MouseCaptureMode): Either[BackendError, Unit] =
    attempt {
      write(AnsiSequences.enableMouseCapture(mode))
      mouseCaptureActive = Some(mode)
    }

  def disableMouseCapture(): Either[BackendError, Unit] =
    attempt {
      write(AnsiSequences.DisableMouseCapture)
      mouseCaptureActive = None
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
    Backend.requirePositiveTimeout(timeout)
    if pendingInterrupt.getAndSet(false) then Right(Some(Event.Interrupt))
    else
      pendingResize.getAndSet(None) match
        case Some(resized) => Right(Some(Event.Resize(resized)))
        case None          =>
          // something queued render-thread work while we were away: go round the loop instead of blocking again
          if woken.getAndSet(false) then Right(None) else blockingRead(timeout)

  private def blockingRead(timeout: Duration): Either[BackendError, Option[Event]] =
    pollingThread.set(Some(Thread.currentThread()))
    // Re-checked *after* registering, closing the window this registration opens: a wake that landed between
    // `readEvent`'s check and this registration found nobody to interrupt, so without this second look the render
    // thread parks for the whole timeout — up to a tick interval, or 100 ms with no tick rate — before draining the
    // work that wake was announcing. That is exactly the latency `wake()` exists to remove.
    try if woken.getAndSet(false) then Right(None) else Right(decoder.decode(JLine3Backend.readTimeoutMillis(timeout)))
    catch
      case _: InterruptedIOException => Right(None) // woken deliberately by `wake()`
      case NonFatal(error)           => Left(BackendError.Io(error))
    finally
      pollingThread.set(None) // no read is in flight any more: `wake` has nobody to interrupt
      val _ = Thread.interrupted() // drop an interrupt that landed after the read completed

  /** Cuts short an in-flight [[readEvent]].
    *
    * JLine's reader waits on a monitor and converts an interrupt into an `InterruptedIOException` that it throws *and
    * clears* (`NonBlockingReaderImpl.read`), so the reader stays usable and no buffered input is lost.
    */
  override def wake(): Unit =
    woken.set(true)
    // a thread never interrupts its own read: the `woken` flag above already sends it back round the loop
    pollingThread.get().filter(_ ne Thread.currentThread()).foreach(_.interrupt())

  override def copyToClipboard(text: String): Either[BackendError, Unit] =
    attempt(write(AnsiSequences.clipboardCopy(text)))

  override def setTitle(title: String): Either[BackendError, Unit] =
    attempt {
      // the push has to happen before the first change, because that is the last moment the stack top is still the
      // title the shell set; `compareAndSet` makes it happen exactly once however many times the app retitles itself
      if titlePushed.compareAndSet(false, true) then write(AnsiSequences.PushTitle)
      write(AnsiSequences.setTitle(title))
    }

  override def suspend[A](body: => A): Either[BackendError, A] =
    attempt {
      val released = releaseTerminal()
      try body
      finally reacquireTerminal(released.state)
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

  /** Restores the terminal and releases the JLine handle, reporting the first step that failed.
    *
    * Every step is attempted whatever the earlier ones did — stopping at the first failure would leave the terminal
    * half-dressed, which is worse than the failure itself — so the JLine handle is closed even when undressing failed,
    * and the undressing failure is what gets reported because it is the one the user can see.
    */
  def close(): Either[BackendError, Unit] =
    val released     = releaseTerminal()
    // only if this backend actually pushed one: popping a stack this app never wrote to would discard someone else's
    // title. `getAndSet` makes a second `close()` — the shutdown hook racing the runner's teardown — pop nothing.
    val titleFailure =
      if titlePushed.getAndSet(false) then attempt(write(AnsiSequences.PopTitle)).left.toOption else None
    val closed       = attempt(terminal.close())
    released.failure.orElse(titleFailure).toLeft(()).flatMap(_ => closed)

  /** Last-resort restore, for a shutdown hook that may be racing JLine's own terminal closer.
    *
    * Writes straight to the process's stdout descriptor rather than through the JLine writer: once JLine's
    * `ShutdownHooks` closer has run, `terminal.writer()` throws `IllegalStateException: Terminal has been closed` and
    * every teardown write is silently discarded. Every sequence emitted is an idempotent mode *reset*, so this is safe
    * to call even when nothing was enabled, and safe to call twice.
    *
    * Deliberately takes no monitor either — unlike [[releaseTerminal]], this is the path that must still work when the
    * render thread is wedged mid-frame, and a last-resort restore that can block is not one.
    */
  override def emergencyRestore(): Unit =
    try
      // deliberately never closed: this wraps the process's own stdout descriptor, and closing the wrapper would close
      // stdout for everything that runs after this hook. The wrapper itself holds no resource beyond that descriptor.
      val out = FileOutputStream(FileDescriptor.out)
      out.write(AnsiSequences.RestoreAll.getBytes(UTF_8))
      out.flush()
    catch case NonFatal(_) => ()

  /** Hands the terminal back to the shell, returning what was active so [[reacquireTerminal]] can restore it, together
    * with the first undress step that failed.
    *
    * Every step is attempted even after one fails: an undress that stops halfway leaves the shell on the alternate
    * screen *and* in raw mode instead of just one of the two. The first failure is kept so `close()` can report it — it
    * used to be logged and dropped, which made "your terminal is now unusable" the one failure this library could not
    * tell anyone about.
    *
    * Called from two threads: the render thread (via [[suspend]], [[printAbove]] and `close()`) and JLine's
    * signal-dispatch thread (via the SIGTSTP handler). Both take `screenOwnership` for the whole sequence, and so does
    * the frame write in [[draw]] — which is the case the flags alone could never cover. Undressing writes
    * `LeaveAlternateScreen`; a frame is a full screen of cursor moves, SGR sequences and box-drawing glyphs. A Ctrl+Z
    * landing between the two halves of an unguarded `draw` would put that payload on the *primary* screen: the user's
    * shell and their scrollback, which is durable and outlives the app. Nothing repairs it either, because the
    * backend's diff still believes `lastFlushed` is on screen. (The mirror case — SIGCONT racing a draw — is
    * self-healing, since [[reacquireTerminal]] raises a full redraw.) A frame write costs microseconds, so the signal
    * thread never waits perceptibly.
    *
    * What the monitor does *not* make atomic is a whole `suspend`: the body between release and reacquire runs without
    * it, because that body is `$EDITOR`. A Ctrl+Z arriving then still interleaves two complete undress/redress
    * sequences, which stays tolerable for the reason it always was — every step either way is an idempotent mode reset,
    * so the worst outcome is a mode disabled or re-enabled twice, and the last [[reacquireTerminal]] to run leaves the
    * terminal dressed as its snapshot describes. The flags themselves stay volatile because the single-step public
    * operations ([[enableRawMode]], [[hideCursor]]) write them from outside this monitor.
    */
  private def releaseTerminal(): TerminalRelease = screenOwnership.synchronized:
    val state    = TerminalState(isRawMode, alternateScreenActive, cursorHidden, mouseCaptureActive)
    val failures = Seq.newBuilder[BackendError]

    def undress(active: Boolean, step: => Either[BackendError, Unit]): Unit =
      if active then
        step.left.foreach { error =>
          JLine3Backend.logTeardownFailure(error)
          failures += error
        }

    undress(state.mouse.isDefined, disableMouseCapture())
    undress(state.cursorHidden, showCursor())
    undress(state.alternateScreen, leaveAlternateScreen())
    undress(state.raw, disableRawMode())
    // each step above already flushed its own sequence; this is belt-and-braces and stays silent, so that closing an
    // already-closed terminal (the shutdown hook racing the normal teardown) reports nothing rather than a scare
    try terminal.writer().flush()
    catch case NonFatal(_) => ()
    TerminalRelease(state, failures.result().headOption)

  /** Whether raw mode is currently on, which is exactly "we are holding someone's cooked attributes to put back". */
  private def isRawMode: Boolean = cookedAttributes.nonEmpty

  /** Restores what [[releaseTerminal]] undressed. Same two callers, same two threads, same monitor. */
  private def reacquireTerminal(state: TerminalState): Unit = screenOwnership.synchronized:
    if state.raw then bestEffort(enableRawMode())
    if state.alternateScreen then bestEffort(enterAlternateScreen())
    if state.cursorHidden then bestEffort(hideCursor())
    state.mouse.foreach(mode => bestEffort(enableMouseCapture(mode)))
    requestFullRedraw() // whatever ran in between owned the screen: repaint everything

  private def onResize(): Unit =
    pendingResize.set(Some(currentSize))
    wake()

  private def onInterrupt(): Unit =
    pendingInterrupt.set(true)
    wake()

  /** SIGTSTP: undress the terminal, then stop for real by re-raising with the default disposition. */
  private def onStop(): Unit =
    suspendedState = releaseTerminal().state
    JLine3Backend.stopSelf()

  /** SIGCONT: take the terminal back and force a full repaint at whatever size it is now. */
  private def onContinue(): Unit =
    reacquireTerminal(suspendedState)
    suspendedState = TerminalState.Undressed
    onResize()

  private def currentSize: Size =
    val jlineSize = terminal.getSize
    Size(jlineSize.getColumns, jlineSize.getRows)

  /** Runs a re-dressing step, reporting a failure rather than propagating it.
    *
    * Only [[reacquireTerminal]] uses this. Taking the terminal back has no caller that could act on a failure — it
    * happens on JLine's signal-dispatch thread after SIGCONT, or inside a `finally` — and abandoning the remaining
    * steps would leave the app running against a terminal dressed in neither shape. Undressing is the direction that
    * *does* report, through [[releaseTerminal]].
    */
  private def bestEffort(step: Either[BackendError, Unit]): Unit =
    step.left.foreach(error => System.err.println(s"glyphora: could not reclaim the terminal: ${error.message}"))

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
    mouse: Option[MouseCaptureMode],
)

private[terminal] object TerminalState:
  /** Nothing was dressed up: cooked mode, primary screen, visible cursor, no mouse capture. */
  val Undressed: TerminalState = TerminalState(false, false, false, None)

/** The outcome of handing the terminal back: the modes that were undressed (so they can be re-dressed) and the first
  * step that failed while doing it, if any.
  */
private[terminal] final case class TerminalRelease(state: TerminalState, failure: Option[BackendError])

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
        .encoding(UTF_8)
        .stdinEncoding(UTF_8)
        .stdoutEncoding(UTF_8)
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

  /** Reports a teardown step that failed, always, on `System.err`.
    *
    * This used to be gated behind a `GLYPHORA_DEBUG` environment variable, which meant the single most user-visible
    * failure the library has — the terminal handed back raw, on the alternate screen, or with the cursor hidden — was
    * silent by default. By the time this fires the app is exiting and the alternate screen is already gone, so there is
    * no UI left for the line to corrupt.
    */
  private[terminal] def logTeardownFailure(error: BackendError): Unit =
    System.err.println(s"glyphora: could not restore the terminal: ${error.message}")

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
