package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, CharWidth, Event, Size}

import org.jline.terminal.{Terminal, TerminalBuilder}

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

/** [[Backend]] implementation over JLine 3's system terminal.
  *
  * Owns the JLine `Terminal` for its whole lifetime: construct via [[JLine3Backend.create]], release with `close()`
  * (which restores cooked mode, the main screen, and cursor visibility if still active). `draw` keeps a snapshot of the
  * last flushed frame and writes only the diff.
  */
final class JLine3Backend private (terminal: Terminal) extends Backend:

  private var savedAttributes: Option[org.jline.terminal.Attributes] = None
  private var lastFlushed: Option[Buffer] = None
  private var alternateScreenActive = false
  private var mouseCaptureActive = false
  private var cursorHidden = false
  private val pendingResize = AtomicReference[Option[Size]](None)
  private val decoder = InputDecoder(timeoutMillis => terminal.reader().read(timeoutMillis))

  terminal.handle(
    Terminal.Signal.WINCH,
    _ => pendingResize.set(Some(currentSize)),
  )

  def size: Either[BackendError, Size] = attempt(currentSize)

  def draw(buffer: Buffer): Either[BackendError, Unit] =
    attempt {
      val previous = lastFlushed.getOrElse(Buffer(buffer.area))
      val output = StringBuilder()
      var expectedX = -1
      var currentY = -1
      var currentStyle = ""
      var currentLink: Option[String] = None
      previous.diff(buffer).foreach { (pos, cell) =>
        if pos.y != currentY || pos.x != expectedX then output ++= AnsiSequences.moveTo(pos.x, pos.y)
        val sgr = AnsiSequences.sgr(cell.style)
        if sgr != currentStyle then
          output ++= sgr
          currentStyle = sgr
        if cell.style.link != currentLink then
          if currentLink.nonEmpty then output ++= AnsiSequences.LinkClose
          cell.style.link.foreach(url => output ++= AnsiSequences.linkOpen(url))
          currentLink = cell.style.link
        output ++= cell.symbol
        currentY = pos.y
        expectedX = pos.x + math.max(1, CharWidth.of(cell.symbol))
      }
      if currentLink.nonEmpty then output ++= AnsiSequences.LinkClose
      output ++= AnsiSequences.ResetStyle
      terminal.writer().write(output.result())
      terminal.writer().flush()
      lastFlushed = Some(buffer.snapshot)
    }

  def enableRawMode(): Either[BackendError, Unit] =
    attempt {
      savedAttributes = Some(terminal.enterRawMode())
    }

  def disableRawMode(): Either[BackendError, Unit] =
    savedAttributes match
      case None => Left(BackendError.NotInRawMode)
      case Some(attributes) =>
        attempt {
          terminal.setAttributes(attributes)
          savedAttributes = None
        }

  def enterAlternateScreen(): Either[BackendError, Unit] =
    attempt {
      write(AnsiSequences.EnterAlternateScreen)
      write(AnsiSequences.ClearScreen)
      alternateScreenActive = true
      lastFlushed = None // the alternate screen starts blank; the next draw must repaint everything
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
    pendingResize.getAndSet(None) match
      case Some(resized) => Right(Some(Event.Resize(resized)))
      case None          => attempt(decoder.decode(timeout.toMillis))

  def close(): Unit =
    // best-effort teardown in reverse acquisition order; a failing step must not block the ones after it
    if mouseCaptureActive then bestEffort(disableMouseCapture())
    if cursorHidden then bestEffort(showCursor())
    if alternateScreenActive then bestEffort(leaveAlternateScreen())
    if savedAttributes.nonEmpty then bestEffort(disableRawMode())
    try terminal.close()
    catch case NonFatal(_) => ()

  private def currentSize: Size =
    val jlineSize = terminal.getSize
    Size(jlineSize.getColumns, jlineSize.getRows)

  private def bestEffort(step: Either[BackendError, Unit]): Unit =
    val _ = step

  private def write(sequence: String): Unit =
    terminal.writer().write(sequence)
    terminal.writer().flush()

  private def attempt[A](body: => A): Either[BackendError, A] =
    try Right(body)
    catch case NonFatal(error) => Left(BackendError.Io(error))

object JLine3Backend:

  /** Opens the process's controlling terminal. Fails with `UnsupportedTerminal` when there is no usable TTY. */
  def create(): Either[BackendError, JLine3Backend] =
    try
      val terminal = TerminalBuilder.builder().system(true).build()
      if terminal.getType == Terminal.TYPE_DUMB || terminal.getType == Terminal.TYPE_DUMB_COLOR then
        terminal.close()
        Left(BackendError.UnsupportedTerminal("dumb terminal (no TTY attached)"))
      else Right(JLine3Backend(terminal))
    catch case NonFatal(error) => Left(BackendError.Io(error))
