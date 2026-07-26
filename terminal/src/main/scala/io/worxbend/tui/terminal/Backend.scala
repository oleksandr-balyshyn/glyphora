package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Event, Size}

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

  def close(): Unit

enum BackendError:
  case Io(cause: Throwable)
  case UnsupportedTerminal(reason: String)
  case NotInRawMode
