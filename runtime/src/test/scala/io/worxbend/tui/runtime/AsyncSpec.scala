package io.worxbend.tui.runtime

import org.scalatest.funsuite.AnyFunSuite

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt

/** `Async` runs work off-thread and enqueues the continuation onto the render thread. With no runner active, tests play
  * the runner's role by draining `RenderThread` themselves. `Signal.set` is legal here because no render thread is
  * registered, so the guard is a no-op.
  */
final class AsyncSpec extends AnyFunSuite:

  /** Drains pending render-thread work repeatedly until `done` or the deadline, mimicking the runner loop. */
  private def pumpUntil(deadlineMillis: Long)(done: => Boolean): Unit =
    val end = System.currentTimeMillis() + deadlineMillis
    while !done && System.currentTimeMillis() < end do
      RenderThread.drainPending()
      Thread.sleep(2)
    RenderThread.drainPending()

  test("run computes off-thread and delivers the result to the render-thread continuation"):
    val result = Signal(0)
    Async.run(20 + 22)(result.set)
    pumpUntil(2000)(result.peek == 42)
    assert(result.peek == 42)

  test("runCatching delivers Right on success and Left on failure"):
    val ok = Signal[Option[Either[Throwable, Int]]](None)
    Async.runCatching(7 * 6)(v => ok.set(Some(v)))
    pumpUntil(2000)(ok.peek.isDefined)
    assert(ok.peek == Some(Right(42)))

    val bad = Signal[Option[Either[Throwable, Int]]](None)
    Async.runCatching[Int](throw new RuntimeException("boom"))(v => bad.set(Some(v)))
    pumpUntil(2000)(bad.peek.isDefined)
    assert(bad.peek.exists(_.isLeft))

  test("after fires once on the render thread"):
    val fired = new AtomicInteger(0)
    Async.after(20.millis)(fired.incrementAndGet())
    pumpUntil(2000)(fired.get() > 0)
    Thread.sleep(60)
    RenderThread.drainPending()
    assert(fired.get() == 1)

  test("after can be canceled before it fires"):
    val fired  = new AtomicInteger(0)
    val handle = Async.after(500.millis)(fired.incrementAndGet())
    handle.cancel()
    Thread.sleep(120)
    RenderThread.drainPending()
    assert(fired.get() == 0)

  test("every ticks repeatedly until canceled"):
    val ticks   = new AtomicInteger(0)
    val handle  = Async.every(15.millis)(ticks.incrementAndGet())
    pumpUntil(2000)(ticks.get() >= 3)
    handle.cancel()
    Thread.sleep(50)
    RenderThread.drainPending()
    val settled = ticks.get()
    Thread.sleep(50)
    RenderThread.drainPending()
    assert(ticks.get() == settled)
    assert(settled >= 3)
