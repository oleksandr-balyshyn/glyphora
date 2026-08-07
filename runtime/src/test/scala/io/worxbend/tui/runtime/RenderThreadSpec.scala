package io.worxbend.tui.runtime

import org.scalatest.funsuite.AnyFunSuite

final class RenderThreadSpec extends AnyFunSuite:

  test("the guard is a no-op while no render thread is registered"):
    assert(RenderThread.isRenderThread)
    RenderThread.checkRenderThread() // must not throw

  test("Signal.set works without a registered render thread"):
    val signal = Signal(1)
    signal.set(2)
    assert(signal.peek == 2)

  test("checkRenderThread throws from a foreign thread while one is registered"):
    RenderThread.register(Thread.currentThread())
    try
      RenderThread.checkRenderThread() // current thread is the render thread: fine
      var thrown: Option[Throwable] = None
      val foreign                   = Thread { () =>
        try RenderThread.checkRenderThread()
        catch case error: IllegalStateException => thrown = Some(error)
      }
      foreign.start()
      foreign.join()
      assert(thrown.exists(_.isInstanceOf[IllegalStateException]))
    finally RenderThread.unregister()

  test("Signal.set from a foreign thread is a defect while a render thread is registered"):
    RenderThread.register(Thread.currentThread())
    try
      val signal                    = Signal(1)
      var thrown: Option[Throwable] = None
      val foreign                   = Thread { () =>
        try signal.set(2)
        catch case error: IllegalStateException => thrown = Some(error)
      }
      foreign.start()
      foreign.join()
      assert(thrown.nonEmpty)
      assert(signal.peek == 1)
    finally RenderThread.unregister()

  test("runOnRenderThread runs inline when already on the render thread"):
    var ran = false
    RenderThread.runOnRenderThread { ran = true }
    assert(ran)

  test("runLater queues work until the runner drains it"):
    var ran = false
    RenderThread.runLater { ran = true }
    assert(!ran)
    RenderThread.drainPending()
    assert(ran)

  test("runOnRenderThread queues from a foreign thread and drains on the render thread"):
    RenderThread.register(Thread.currentThread())
    try
      var ran     = false
      val foreign = Thread(() => RenderThread.runOnRenderThread { ran = true })
      foreign.start()
      foreign.join()
      assert(!ran)
      RenderThread.drainPending()
      assert(ran)
    finally RenderThread.unregister()

  test("a nested registration unregisters back to the outer runner instead of leaving the guard open"):
    val outer = RenderThread.register(Thread.currentThread())
    try
      val inner = RenderThread.register(Thread.currentThread()) // a runner started from inside another runner's loop
      assert(inner ne outer)
      RenderThread.unregister() // the inner runner exits; the outer one is still running this thread

      var thrown: Option[Throwable] = None
      val foreign                   = Thread { () =>
        try RenderThread.checkRenderThread()
        catch case error: IllegalStateException => thrown = Some(error)
      }
      foreign.start()
      foreign.join()
      assert(thrown.nonEmpty, "the guard must not fail open once the nested runner unregistered")

      var ran = false
      RenderThread.runLater { ran = true }
      outer.drain() // the outer runner's own queue, not the unattributed one
      assert(ran, "work queued after the nested runner exited belongs to the outer runner's queue")
    finally RenderThread.unregister()

  test("with no runner registered runOnRenderThread degrades to running inline on the caller's thread"):
    // documents a known limitation: with nothing to marshal onto, background work updates state where it already is
    var ranOn   = Option.empty[String]
    val foreign = Thread(() => RenderThread.runOnRenderThread { ranOn = Some(Thread.currentThread().getName) })
    foreign.setName("foreign-worker")
    foreign.start()
    foreign.join()
    assert(ranOn.contains("foreign-worker"))

  test("work queued with no runner registered is drained by whichever runner starts next"):
    // documents a known limitation: the unattributed queue is process-wide, so a continuation left over from one app
    // runs on the next app's render thread
    var ran  = false
    RenderThread.runLater { ran = true }
    assert(!ran)
    val loop = RenderThread.register(Thread.currentThread())
    try
      RenderThread.drainPending(loop)
      assert(ran)
    finally RenderThread.unregister()

  test("the guard admits any registered render thread, so one runner can still mutate another's signals"):
    // documents a known limitation: registration is per-process, not per-signal, so ownership is not enforced
    val signal = Signal(1)
    RenderThread.register(Thread.currentThread())
    try
      var thrown: Option[Throwable] = None
      val otherRunner               = Thread { () =>
        RenderThread.register(Thread.currentThread())
        try
          try signal.set(2)
          catch case error: IllegalStateException => thrown = Some(error)
        finally RenderThread.unregister()
      }
      otherRunner.start()
      otherRunner.join()
      assert(thrown.isEmpty)
      assert(signal.peek == 2)
    finally RenderThread.unregister()

  test("two render threads can be registered at once without racing each other"):
    RenderThread.register(Thread.currentThread())
    try
      @volatile var otherOk = false
      val other             = Thread { () =>
        RenderThread.register(Thread.currentThread())
        try
          RenderThread.checkRenderThread() // both threads are render threads
          otherOk = true
        finally RenderThread.unregister()
      }
      other.start()
      other.join()
      assert(otherOk)
      RenderThread.checkRenderThread() // still registered here after the other unregistered itself
    finally RenderThread.unregister()
