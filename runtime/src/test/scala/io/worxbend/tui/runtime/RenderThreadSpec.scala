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
