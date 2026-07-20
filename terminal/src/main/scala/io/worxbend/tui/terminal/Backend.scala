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

  /** Blocks up to `timeout` for the next input event; `Right(None)` means the timeout elapsed quietly. */
  def readEvent(timeout: Duration): Either[BackendError, Option[Event]]

  /** Copies `text` to the system clipboard via the OSC 52 terminal sequence.
    *
    * Support is terminal-dependent (and often opt-in for security reasons); terminals that don't understand OSC 52
    * ignore the sequence, so this is best-effort and reports success as long as the write itself succeeds. The default
    * implementation is a no-op for backends without a real terminal.
    */
  def copyToClipboard(text: String): Either[BackendError, Unit] =
    val _ = text
    Right(())

  def close(): Unit

enum BackendError:
  case Io(cause: Throwable)
  case UnsupportedTerminal(reason: String)
  case NotInRawMode
