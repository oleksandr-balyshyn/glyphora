package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Rect, Style}

import org.jline.terminal.{Terminal, TerminalBuilder}
import org.scalatest.funsuite.AnyFunSuite

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** The diff baseline of [[JLine3Backend]], driven against a real backend over a pair of streams.
  *
  * `JLine3Backend.create` needs the controlling TTY that CI does not have, so these run over [[JLine3Backend.wrapping]]
  * and a non-system JLine terminal writing into a capture stream. What is under test is the ownership rule:
  * `lastFlushed` is touched only by the render thread, and every other thread that takes the screen away raises
  * `requestFullRedraw()` instead — which must survive a `draw` that is already in flight.
  */
final class JLine3BackendRedrawSpec extends AnyFunSuite:

  /** How long any wait here may take before it is a failure. Generous for a loaded CI runner, finite on principle: an
    * unbounded wait in a test is a hang, not a test.
    */
  private val Patience: FiniteDuration = 30.seconds

  test("a repaint request raised while a draw is in flight is honoured by the next frame"):
    withBackend { (backend, out) =>
      assert(backend.draw(frame("first")) == Right(()))
      val _ = out.drain()

      val inFlight = CountDownLatch(1)
      val release  = CountDownLatch(1)
      out.parkNextWrite { () =>
        inFlight.countDown()
        val _ = release.await(Patience.toSeconds, TimeUnit.SECONDS)
      }

      // the assertion cannot live in the thread body: a failure there would not fail the test
      @volatile var drawn: Option[Either[BackendError, Unit]] = None
      val drawing = Thread(() => drawn = Some(backend.draw(frame("second"))))
      drawing.setDaemon(true) // a wedged draw must not outlive the suite and hold the JVM open
      drawing.start()

      // the drawing thread is now parked inside the frame's first write: past its baseline choice, before its snapshot
      assert(inFlight.await(Patience.toSeconds, TimeUnit.SECONDS), "the draw never reached the capture stream")
      backend.requestFullRedraw() // exactly what the SIGCONT handler does, via `reacquireTerminal`
      release.countDown()
      drawing.join(Patience.toMillis)
      assert(!drawing.isAlive, "the parked draw never finished")

      assert(drawn == Some(Right(())))
      val _ = out.drain()

      // the request outlived the in-flight frame, so this repaints every cell even though nothing changed
      assert(backend.draw(frame("second")) == Right(()))
      assert(out.drain().contains("second"))

      // and it was spent doing so: the frame after that diffs against the snapshot again
      assert(backend.draw(frame("second")) == Right(()))
      assert(out.drain().isEmpty)
    }

  test("re-entering the alternate screen repaints every cell, then the next frame diffs normally"):
    withBackend { (backend, out) =>
      assert(backend.draw(frame("first")) == Right(()))
      val _ = out.drain()
      assert(backend.draw(frame("first")) == Right(()))
      assert(out.drain().isEmpty) // the baseline is being kept, which is what makes the next assertion mean something

      assert(backend.enterAlternateScreen() == Right(()))
      val _ = out.drain() // smcup and the clear

      assert(backend.draw(frame("first")) == Right(()))
      assert(out.drain().contains("first"))

      assert(backend.draw(frame("first")) == Right(()))
      assert(out.drain().isEmpty)
    }

  test("suspending and resuming forces the next frame to repaint everything"):
    withBackend { (backend, out) =>
      assert(backend.draw(frame("first")) == Right(()))
      val _ = out.drain()
      assert(backend.draw(frame("first")) == Right(()))
      assert(out.drain().isEmpty)

      // the path both `printAbove` and the TSTP/CONT pair take: whatever ran in between owned the screen
      assert(backend.suspend(()) == Right(()))
      val _ = out.drain()

      assert(backend.draw(frame("first")) == Right(()))
      assert(out.drain().contains("first"))
    }

  private def frame(text: String): Buffer =
    val buffer = Buffer(Rect(0, 0, 20, 3))
    buffer.setString(0, 0, text, Style.Default)
    buffer

  private def withBackend(body: (JLine3Backend, CaptureStream) => Unit): Unit =
    val out      = CaptureStream()
    val input    = NeverReadable()
    val terminal = headlessTerminal(input, out)
    try body(JLine3Backend.wrapping(terminal, ColorDepth.Ansi16), out)
    finally
      // Release before closing. Nothing in this suite reads — `JLine3Backend`'s decoder only touches
      // `terminal.reader()` from inside its lambda — so today JLine's pump never starts and `close()` returns at once.
      // That is a property of what these tests happen to exercise, not a guarantee: anything that resumes the terminal
      // would leave the pump parked on a stream with no EOF, and `close()` pauses the terminal by *joining* that pump.
      // Releasing first costs nothing and takes the deadlock off the table.
      input.release()
      terminal.close()

  private def headlessTerminal(in: InputStream, out: OutputStream): Terminal =
    TerminalBuilder
      .builder()
      .name("glyphora-test")
      .system(false)
      .streams(in, out)
      .`type`("xterm-256color") // JLine bundles this terminfo entry, and it has smcup
      .encoding(StandardCharsets.UTF_8)
      .paused(true)
      .signalHandler(Terminal.SignalHandler.SIG_IGN)
      .build()

/** Everything the backend wrote, with a one-shot hook that parks the writing thread partway through a frame. */
private final class CaptureStream extends OutputStream:

  private val written                    = ByteArrayOutputStream()
  private val armed                      = AtomicBoolean(false)
  @volatile private var hook: () => Unit = () => ()

  /** Parks the next thread to write a byte, once.
    *
    * Hooking `write` rather than `flush` is the difference between a test and a hang. A frame always reaches the stream
    * as bytes, but whether JLine's writer propagates a `flush` down to the underlying `OutputStream` depends on how the
    * terminal was built — on CI it does not, and a park hook that never fires left the test thread waiting on a latch
    * forever, with the suite green up to that point and the runner held until the job timeout.
    */
  def parkNextWrite(body: () => Unit): Unit =
    hook = body
    armed.set(true)

  def write(b: Int): Unit =
    if armed.getAndSet(false) then hook()
    synchronized(written.write(b))

  def drain(): String = synchronized {
    val text = written.toString(StandardCharsets.UTF_8)
    written.reset()
    text
  }

/** An input stream that yields nothing while the test runs, so JLine cannot see EOF and close the terminal underneath
  * it — but that can be released at teardown, so JLine's pump thread is never left parked on it.
  *
  * The distinction matters: a reader that blocks forever also blocks `Terminal.close()`, which joins the pump.
  */
private final class NeverReadable extends InputStream:

  private val until = CountDownLatch(1)

  /** Lets a blocked reader see EOF, so the terminal can be closed without hanging on its own pump thread. */
  def release(): Unit = until.countDown()

  def read(): Int =
    try
      until.await()
      -1
    catch case _: InterruptedException => -1

  override def close(): Unit = release()
