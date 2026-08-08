package io.worxbend.tui.terminal

import org.scalatest.funsuite.AnyFunSuite

import java.util.concurrent.{CountDownLatch, TimeUnit}

/** The redraw-request protocol behind `JLine3Backend.requestFullRedraw`.
  *
  * `JLine3Backend` cannot be constructed without a controlling terminal, which CI does not have — see
  * [[JLine3BackendSpec]], which says the same and is why its subject is a companion function. An earlier version of
  * this suite tried to work around that with a JLine terminal built over a stream pair. On CI not one byte ever reached
  * that stream, so a park hook that never fired left the suite waiting on a latch until the job timed out. The ordering
  * that actually carries the fix therefore lives in [[RedrawRequest]] and is tested here directly.
  *
  * What `draw` adds on top is one line of ordering — claim before composing, re-raise if the frame failed — which is
  * reviewable at its single call site rather than reachable from a test without a TTY.
  */
final class JLine3BackendRedrawSpec extends AnyFunSuite:

  /** Finite on principle: an unbounded wait in a test is a hang, not a test. */
  private val PatienceSeconds: Long = 30L

  test("a claim with nothing raised does not force a repaint"):
    val request = RedrawRequest()
    assert(!request.isPending)
    assert(!request.claim())

  test("a raised request is served by the next claim and only that one"):
    val request = RedrawRequest()
    request.raise()
    assert(request.isPending)
    assert(request.claim())
    assert(!request.isPending) // spent: the frame that claimed it repainted every cell
    assert(!request.claim())

  test("two raises before a claim are one repaint, not two"):
    val request = RedrawRequest()
    request.raise()
    request.raise()
    assert(request.claim())
    assert(!request.claim())

  /** The defect this change exists for: `reacquireTerminal` used to reset the diff baseline directly from JLine's
    * signal-dispatch thread, and an in-flight `draw`'s snapshot then overwrote it, so the repaint after Ctrl+Z/fg was
    * lost. Claiming before composing is what makes a request outlive the frame it lands in.
    */
  test("a request raised after a frame claimed is kept for the following frame"):
    val request = RedrawRequest()
    request.raise()

    assert(request.claim()) // frame N claims, and is now composing
    request.raise()         // SIGCONT lands mid-frame
    // frame N finishes and takes its snapshot; the request must not have gone with it
    assert(request.isPending)
    assert(request.claim()) // frame N+1 serves it

  test("a raise from another thread while a frame is composing survives that frame"):
    val request   = RedrawRequest()
    val composing = CountDownLatch(1)
    val raised    = CountDownLatch(1)

    assert(!request.claim()) // frame N claims nothing, then starts composing
    val signalThread = Thread { () =>
      val _ = composing.await(PatienceSeconds, TimeUnit.SECONDS)
      request.raise()
      raised.countDown()
    }
    signalThread.setDaemon(true) // a wedged helper must not hold the JVM open after the suite
    signalThread.start()

    composing.countDown()
    assert(raised.await(PatienceSeconds, TimeUnit.SECONDS), "the signal thread never raised the request")
    signalThread.join(PatienceSeconds * 1000L)

    assert(request.isPending) // frame N's snapshot did not consume it
    assert(request.claim())
