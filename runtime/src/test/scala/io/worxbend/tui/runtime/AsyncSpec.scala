package io.worxbend.tui.runtime

import org.scalatest.funsuite.AnyFunSuite

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
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

  /** The failure path of `run` is the one an app hits in production — a fetch times out, a file is missing — so its
    * default has to reach somewhere an app can see. These pin what each handler actually does with it.
    */
  test("a failing run reports to the ambient handler and never delivers a result"):
    val failure             = AtomicReference[Option[Throwable]](None)
    val delivered           = new AtomicInteger(0)
    given AsyncErrorHandler = error => failure.set(Some(error))

    Async.run[Int](throw new IllegalStateException("boom"))(_ => delivered.incrementAndGet())
    pumpUntil(2000)(failure.get().isDefined)

    assert(failure.get().exists(_.getMessage == "boom"))
    assert(delivered.get() == 0)

  /** The default, and the reason it is not `rethrow`: rethrowing happens on the worker thread, where the only listener
    * is the JVM's uncaught-exception handler printing to standard error — which for a terminal app is the very tty the
    * UI is drawn on, so the stack trace lands on top of the alternate screen and the frame diff never repaints over it.
    * Handing the throwable back to the render loop that armed the call puts it where the runner already collects
    * queued-task failures and reports them as `RunnerError.QueuedTask`.
    */
  test("a failing run with no ambient handler reports through the render loop that armed it"):
    val seen      = scala.collection.mutable.ArrayBuffer.empty[Throwable]
    val delivered = new AtomicInteger(0)
    val loop      = RenderThread.register(Thread.currentThread(), () => (), error => { val _ = seen.append(error) })
    try
      Async.run[Int](throw new IllegalStateException("boom"))(_ => delivered.incrementAndGet())
      val end = System.currentTimeMillis() + 2000
      while seen.isEmpty && System.currentTimeMillis() < end do
        RenderThread.drainPending(loop)
        Thread.sleep(2)
      RenderThread.drainPending(loop)
      assert(seen.map(_.getMessage).toList == List("boom"), "the failure never reached the render loop")
      assert(delivered.get() == 0, "a failure must not also deliver a result")
    finally RenderThread.unregister()

  test("AsyncErrorHandler.ignore swallows the failure and the queue keeps working"):
    given AsyncErrorHandler = AsyncErrorHandler.ignore
    val afterwards          = new AtomicInteger(0)

    Async.run[Int](throw new RuntimeException("boom"))(_ => ())
    Async.run(1 + 1)(_ => afterwards.incrementAndGet())
    pumpUntil(2000)(afterwards.get() > 0)

    assert(afterwards.get() == 1)

  test("AsyncErrorHandler.onRenderThread reports from the drain rather than on the worker"):
    val reportedOn          = AtomicReference[Option[String]](None)
    given AsyncErrorHandler = AsyncErrorHandler.onRenderThread(_ => reportedOn.set(Some(Thread.currentThread.getName)))

    Async.run[Int](throw new RuntimeException("boom"))(_ => ())
    pumpUntil(2000)(reportedOn.get().isDefined)

    // the report ran on whichever thread called `RenderThread.drainPending` — here the test thread, never the worker
    assert(reportedOn.get().contains(Thread.currentThread.getName))
    assert(!reportedOn.get().exists(_.startsWith("glyphora-async")))

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
