package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Position, Size}

import org.jline.terminal.Terminal

import scala.concurrent.duration.{Duration, FiniteDuration}

/** The query/reply round trips of [[JLine3Backend]]: asking the terminal something with an escape sequence and reading
  * its answer off the input stream.
  *
  * Every round trip has the same shape. The request goes out under the backend's `screenOwnership` monitor, like every
  * other write, so a Ctrl+Z handover cannot land between the question and the terminal. The *read* deliberately runs
  * outside that monitor: a read holding it for the length of the timeout would block the signal handler that hands the
  * terminal back on Ctrl+Z — exactly the key a user reaches for when something seems stuck. What protects the decoder
  * from a second reader is the render-thread contract in [[Backend.queryCursorPosition]], not the monitor.
  *
  * A terminal that does not implement a query never answers, so every read is bounded by a timeout and a silence is
  * reported as "unknown" (`None`, or [[TerminalCapabilities.unknown]] from [[probeCapabilities]]), never as an I/O
  * failure: nothing broke, the terminal simply cannot say.
  */
private[terminal] final class ReplyQueries(
    screenOwnership: AnyRef,
    terminal: Terminal,
    decoder: InputDecoder,
):

  /** Writes the capability queries and reads what comes back, or skips the round trip entirely.
    *
    * DA1 goes last because it is the fence: terminals answer in the order the queries arrived, so its reply means
    * everything that was going to be answered has been. A terminal that answers nothing at all costs the timeout once,
    * at start-up, and leaves every field unknown — which is exactly today's behaviour, since unknown means "use it".
    *
    * `GLYPHORA_NO_CAPABILITY_PROBE` set to any non-empty value skips the round trip, for a CI harness or a terminal
    * where even a short start-up read is unwanted. Skipping is safe by construction: it produces the same unknown value
    * a silent terminal would.
    */
  def probeCapabilities(timeout: FiniteDuration): TerminalCapabilities =
    if sys.env.get("GLYPHORA_NO_CAPABILITY_PROBE").exists(_.nonEmpty) then TerminalCapabilities.unknown
    else
      screenOwnership.synchronized {
        write(AnsiSequences.queryPrivateMode(CapabilityReplies.SynchronizedOutputMode))
        write(AnsiSequences.queryPrivateMode(CapabilityReplies.BracketedPasteMode))
        write(AnsiSequences.queryPrivateMode(CapabilityReplies.FocusReportingMode))
        write(AnsiSequences.QueryKittyKeyboard)
        write(AnsiSequences.QueryPrimaryDeviceAttributes)
      }
      decoder.readCapabilityReport(timeout)

  /** Writes `ESC[6n` and waits up to `timeout` for the cursor report, or answers `None` when the terminal never does.
    */
  def cursorPosition(timeout: Duration): Option[Position] =
    write(AnsiSequences.RequestCursorPosition)
    decoder.readCursorReport(timeout)

  /** Writes `ESC[14t` and waits up to `timeout` for the text-area size, or answers `None` when the terminal never
    * replies — the ordinary outcome, including on most Windows terminals.
    */
  def textAreaSize(timeout: FiniteDuration): Option[Size] =
    write(AnsiSequences.RequestTextAreaPixels)
    decoder.readTextAreaSize(timeout)

  /** Writes one sequence to the terminal and flushes it, under `screenOwnership`. The monitor is reentrant, so the
    * five-query burst in [[probeCapabilities]] takes it once around the whole batch and this simply re-enters.
    */
  private def write(sequence: String): Unit =
    screenOwnership.synchronized {
      terminal.writer().write(sequence)
      terminal.writer().flush()
    }
