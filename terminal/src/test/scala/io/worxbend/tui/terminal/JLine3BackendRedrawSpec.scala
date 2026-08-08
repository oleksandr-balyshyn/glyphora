package io.worxbend.tui.terminal

import io.worxbend.tui.core.{Buffer, Rect, Style}

import org.jline.terminal.{Terminal, TerminalBuilder}
import org.scalatest.funsuite.AnyFunSuite

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch

/** The diff baseline of [[JLine3Backend]], driven against a real backend over a pair of streams.
  *
  * `JLine3Backend.create` needs the controlling TTY that CI does not have, so these run over [[JLine3Backend.wrapping]]
  * and a non-system JLine terminal writing into a capture stream. What is under test is the ownership rule:
  * `lastFlushed` is touched only by the render thread, and every other thread that takes the screen away raises
  * `requestFullRedraw()` instead — which must survive a `draw` that is already in flight.
  */
final class JLine3BackendRedrawSpec extends AnyFunSuite:

  test("a repaint request raised while a draw is in flight is honoured by the next frame"):
    withBackend { (backend, out) =>
      assert(backend.draw(frame("first")) == Right(()))
      val _ = out.drain()

      val inFlight = CountDownLatch(1)
      val release  = CountDownLatch(1)
      out.onFlush = () =>
        inFlight.countDown()
        release.await()

      // the assertion cannot live in the thread body: a failure there would not fail the test
      @volatile var drawn: Option[Either[BackendError, Unit]] = None
      val drawing = Thread(() => drawn = Some(backend.draw(frame("second"))))
      drawing.start()

      // the drawing thread is now parked inside `flush`: past its baseline choice, past its bytes, before its snapshot
      inFlight.await()
      backend.requestFullRedraw() // exactly what the SIGCONT handler does, via `reacquireTerminal`
      release.countDown()
      drawing.join()

      out.onFlush = () => ()
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
    val terminal = headlessTerminal(out)
    try body(JLine3Backend.wrapping(terminal, ColorDepth.Ansi16), out)
    finally terminal.close()

  private def headlessTerminal(out: OutputStream): Terminal =
    TerminalBuilder
      .builder()
      .name("glyphora-test")
      .system(false)
      .streams(NeverReadable(), out)
      .`type`("xterm-256color") // JLine bundles this terminfo entry, and it has smcup
      .encoding(StandardCharsets.UTF_8)
      .paused(true)
      .signalHandler(Terminal.SignalHandler.SIG_IGN)
      .build()

/** Everything the backend wrote, with a hook that can park the writing thread partway through a frame. */
private final class CaptureStream extends OutputStream:

  private val written = ByteArrayOutputStream()

  /** Run on whichever thread called `flush` — the render thread in these tests. */
  @volatile var onFlush: () => Unit = () => ()

  def write(b: Int): Unit = synchronized(written.write(b))

  override def flush(): Unit = onFlush()

  def drain(): String = synchronized {
    val text = written.toString(StandardCharsets.UTF_8)
    written.reset()
    text
  }

/** An input stream that never yields and never ends, so JLine cannot see EOF and close the terminal under the test. */
private final class NeverReadable extends InputStream:

  private val never = CountDownLatch(1)

  def read(): Int =
    try
      never.await()
      -1
    catch case _: InterruptedException => -1
