package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Position, Size}

import scala.concurrent.duration.Duration

/** A terminal a TUI can draw to and read input from.
  *
  * Implementations own the physical (or simulated) terminal: raw mode, the alternate screen, cursor visibility, and
  * translating [[Buffer]] diffs into whatever the device understands. Everything above this trait — runtime, widgets,
  * DSL — is backend-agnostic.
  *
  * I/O failures are values (`Either[BackendError, A]`) because callers can meaningfully degrade (log and continue vs.
  * abort); `throw` is reserved for genuine defects.
  *
  * `draw` flushes only the cells that changed since the previous `draw` call (diff-based updates); the first call after
  * construction, or after the buffer area changes, flushes everything.
  */
trait Backend:
  def size: Either[BackendError, Size]
  def draw(buffer: Buffer): Either[BackendError, Unit]
  def enableRawMode(): Either[BackendError, Unit]
  def disableRawMode(): Either[BackendError, Unit]
  def enterAlternateScreen(): Either[BackendError, Unit]
  def leaveAlternateScreen(): Either[BackendError, Unit]
  def enableMouseCapture(): Either[BackendError, Unit]
  def disableMouseCapture(): Either[BackendError, Unit]
  def hideCursor(): Either[BackendError, Unit]
  def showCursor(): Either[BackendError, Unit]

  /** Starts mouse capture in `mode`, which decides whether pointer motion with no button held is reported at all.
    *
    * The no-argument [[enableMouseCapture]] is `MouseCaptureMode.Buttons`. Overriding this one is what lets an app ask
    * for `MouseCaptureMode.AllMotion` and receive [[io.worxbend.tui.core.MouseEventKind.Moved]] — hover. The default
    * body ignores the mode and requests buttons-only, so a backend written before all-motion tracking existed keeps
    * compiling and keeps behaving exactly as it did.
    */
  def enableMouseCapture(mode: MouseCaptureMode): Either[BackendError, Unit] =
    val _ = mode
    enableMouseCapture()

  /** Parks the terminal's own cursor — the hardware caret — on the zero-based cell `position`.
    *
    * This is the caret the operating system knows about: an input method editor (IME, the software that turns a
    * sequence of keystrokes into a Chinese, Japanese or Korean character) anchors its candidate popup to it, and a
    * screen reader reports it as the insertion point. It is *not* the highlighted cell a text widget paints into the
    * [[Buffer]]; a focused text field normally wants both, because the painted block is what a sighted user sees and
    * the hardware caret is what everything else follows.
    *
    * Position only: visibility stays with [[hideCursor]] and [[showCursor]]. Before this method existed the caret was
    * hidden once at start-up and then left wherever the last cell of the frame diff happened to leave it; now a caller
    * that wants a visible caret moves it here and calls `showCursor()`, and one that wants none calls `hideCursor()`
    * without having to move anything.
    *
    * Call it *after* [[draw]], on the render thread: a frame flush is a stream of cursor moves, so a caret parked
    * before the flush is walked away from by the flush itself. A position outside the terminal is clamped by the device
    * rather than rejected here, so callers that care must bound it against [[size]] themselves.
    *
    * The default is a no-op for backends with no real terminal.
    */
  def setCursorPosition(position: Position): Either[BackendError, Unit] =
    val _ = position
    Right(())

  /** Blocks up to `timeout` for the next input event; `Right(None)` means nothing arrived.
    *
    * `timeout` must be **strictly positive**, or infinite to block until an event arrives. Zero is rejected rather than
    * treated as "poll once": the underlying JLine reader treats a non-positive timeout as an unbounded blocking read,
    * so a zero here would wedge the event loop until the user happened to press a key.
    *
    * `Right(None)` covers both an elapsed timeout and input that decoded to nothing (a device-attributes reply, a torn
    * sequence) — callers must treat it as "no event this round", never as end-of-input.
    */
  def readEvent(timeout: Duration): Either[BackendError, Option[Event]]

  /** Interrupts an in-flight [[readEvent]] so the caller can re-check its own state promptly. Safe from any thread.
    *
    * Without this, work queued onto the render thread from a background thread (an `Async` result, a timer) is
    * invisible until the current poll expires. The default is a no-op for backends whose reads are already cheap to
    * wait out.
    */
  def wake(): Unit = ()

  /** Restores the terminal by the shortest path available, for callers that may be racing the backend's own teardown.
    *
    * Unlike `close()` this makes no attempt to be tidy and keeps no state: it exists for a JVM shutdown hook, where the
    * normal machinery may already have been torn down underneath it. Must be idempotent and must never throw. The
    * default is a no-op for backends with no real terminal to restore.
    */
  def emergencyRestore(): Unit = ()

  /** Copies `text` to the system clipboard via the OSC 52 terminal sequence.
    *
    * Support is terminal-dependent (and often opt-in for security reasons); terminals that don't understand OSC 52
    * ignore the sequence, so this is best-effort and reports success as long as the write itself succeeds. The default
    * implementation is a no-op for backends without a real terminal.
    */
  def copyToClipboard(text: String): Either[BackendError, Unit] =
    val _ = text
    Right(())

  /** Sets the terminal's window or tab title (the OSC 2 sequence).
    *
    * Best-effort, like [[copyToClipboard]]: a terminal that does not understand OSC 2 ignores the sequence, so success
    * here means "the write went out", not "the title changed". Where the terminal supports XTerm's title stack the
    * previous title is pushed before the first change and popped by `close()`, so the shell's own title comes back when
    * the app exits.
    *
    * The default implementation is a no-op for backends without a real terminal.
    */
  def setTitle(title: String): Either[BackendError, Unit] =
    val _ = title
    Right(())

  /** Temporarily hands the real terminal back so `body` can own it — leave the alternate screen, drop raw mode and
    * mouse capture, run `body` (e.g. launch `$EDITOR`), then restore the app's screen and force a full repaint. Must
    * run on the render thread. The default just runs `body` without touching the terminal, so headless/basic backends
    * stay correct.
    */
  def suspend[A](body: => A): Either[BackendError, A] =
    Right(body)

  /** Emits `lines` into the terminal's scrollback *above* the live UI (like Bubble Tea's `tea.Println` / ratatui's
    * `insert_before`) — durable log lines that remain after the app exits. Must run on the render thread. The default
    * is a no-op.
    */
  def printAbove(lines: Seq[String]): Either[BackendError, Unit] =
    val _ = lines
    Right(())

  /** Erases part of the screen, as `kind` describes.
    *
    * Must run on the render thread. It also invalidates the diff baseline through [[requestFullRedraw]], because after
    * an erase the terminal no longer shows what the last flushed frame described, and a diff against that frame would
    * leave every unchanged cell blank. The default is a successful no-op for a backend with no screen to erase.
    */
  def clearRegion(kind: ClearType): Either[BackendError, Unit] =
    val _ = kind
    Right(())

  /** Discards what this backend believes is currently on screen, so the next [[draw]] writes every cell again.
    *
    * `draw` is diff-based: it emits only the cells that differ from the frame it last flushed. That is correct for
    * exactly as long as nothing but this backend writes to the terminal. When something else does — a subprocess
    * started outside [[suspend]], a library that printed to the real stdout, a terminal that dropped its screen on its
    * own — the picture on screen and the backend's baseline disagree, and every cell the app would have redrawn to the
    * value it already holds stays unwritten. The corruption then survives until the app happens to change that exact
    * cell. Calling this says "assume nothing on screen survived", and the next frame repaints in full.
    *
    * Safe to call from any thread, and idempotent: two calls before the next frame are one repaint, not two. It does
    * not itself schedule a frame — a caller that is not already drawing every tick must ask for one. The default is a
    * no-op for backends that keep no such baseline.
    */
  def requestFullRedraw(): Unit = ()

  /** Scrolls the terminal up by `n` rows: the top `n` rows leave the screen into the terminal's scrollback and `n`
    * blank rows appear at the bottom.
    *
    * This is the primitive an *inline* viewport is built from — a UI that lives below the shell prompt on the primary
    * screen rather than taking over the whole terminal. Reserving room for such a UI is "scroll the shell up by the
    * height I need"; letting a finished chunk of output become permanent scrollback is the same operation. Where
    * [[printAbove]] emits text the backend formats for you, this only makes room, and the caller then draws into it.
    *
    * Meaningless on the alternate screen, which has no scrollback: call it only while the primary screen is current.
    * Must run on the render thread. `n <= 0` is a no-op rather than a failure, so a caller computing a delta needs no
    * guard of its own. The default is a no-op for backends with no real terminal.
    */
  def appendLines(n: Int): Either[BackendError, Unit] =
    val _ = n
    Right(())

  /** Restores the terminal and releases everything this backend owns.
    *
    * Fallible like every other operation here, because the failure it can report is the most user-visible one the
    * library has: a terminal handed back in raw mode, on the alternate screen, or with the cursor hidden leaves the
    * user's shell unusable and gives no hint why. The returned value is the *first* step that failed; the remaining
    * steps are still attempted, since a half-restored terminal is worse than a fully attempted one.
    *
    * Must be idempotent: the runner closes the backend on its normal teardown path and a JVM shutdown hook may close it
    * again.
    */
  def close(): Either[BackendError, Unit]

private[terminal] object Backend:

  /** Enforces the strictly-positive (or infinite) timeout that [[Backend.readEvent]] documents.
    *
    * Lives here so every implementation raises the identical failure: JLine reads a non-positive timeout as an
    * unbounded blocking read, so a zero that slipped through one backend and not the other would wedge the event loop
    * in production while the headless tests kept passing.
    *
    * `> Zero` rather than a finiteness test: it accepts `Duration.Inf` (block until an event arrives) and rejects
    * `Duration.MinusInf`, which would otherwise take the blocking branch and never return.
    */
  def requirePositiveTimeout(timeout: Duration): Unit =
    require(timeout > Duration.Zero, s"readEvent timeout must be positive or infinite, got $timeout")

/** Which part of the screen [[Backend.clearRegion]] erases.
  *
  * Everything except `All` is relative to where the cursor currently is, which is what makes the partial forms useful
  * to a viewport that owns only some of the screen: it positions the cursor at the start of its own region and erases
  * from there, leaving the user's scrollback above it intact.
  */
enum ClearType:

  /** The whole display (`CSI 2J`). */
  case All

  /** From the cursor to the end of the display (`CSI 0J`). */
  case AfterCursor

  /** From the start of the display up to and including the cursor (`CSI 1J`). */
  case BeforeCursor

  /** The whole line the cursor is on (`CSI 2K`). */
  case CurrentLine

  /** From the cursor to the end of its line (`CSI 0K`). */
  case UntilNewLine

/** Why a [[Backend]] operation could not be carried out. */
enum BackendError:

  /** The terminal's underlying I/O failed — a closed stream, a disconnected TTY, a write that could not complete. */
  case Io(cause: Throwable)

  /** The terminal lacks a capability the operation needs (no alternate screen, no TTY at all). */
  case UnsupportedTerminal(reason: String)

  /** Raw mode was asked to be left when it had never been entered. */
  case NotInRawMode

  /** One human-readable line describing the failure, for an app that reports it to the user.
    *
    * The generated `toString` of an enum case is a constructor call — `Io(java.io.IOException: Stream closed)` — which
    * is a fine debugging string and a poor thing to print at someone. This is the sentence to print instead.
    */
  def message: String =
    this match
      case Io(cause)                   =>
        val detail = Option(cause.getMessage).getOrElse(cause.getClass.getName)
        s"terminal I/O failed: $detail"
      case UnsupportedTerminal(reason) => s"terminal not supported: $reason"
      case NotInRawMode                => "the terminal is not in raw mode"
