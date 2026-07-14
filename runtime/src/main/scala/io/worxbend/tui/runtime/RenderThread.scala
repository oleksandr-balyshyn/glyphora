package io.worxbend.tui.runtime

import java.util.concurrent.ConcurrentLinkedQueue

/** The single-render-thread model.
  *
  * All UI state mutation must happen on the render thread — the thread running the [[Runner]] loop. The guard is
  * deliberately a no-op while no render thread is registered, so unit tests of widgets and signals need no running
  * runtime.
  */
object RenderThread:

  // a concurrent set: several runners may live in one JVM (parallel test suites, embedded apps) and must
  // not race on a single registration slot
  private val registered = java.util.concurrent.ConcurrentHashMap.newKeySet[Thread]()
  private val pending    = ConcurrentLinkedQueue[() => Unit]()

  def isRenderThread: Boolean =
    registered.isEmpty || registered.contains(Thread.currentThread())

  /** Defect-detection assertion: throws `IllegalStateException` when called off the render thread while one is
    * registered. A programming error, not a recoverable condition — hence throw, not `Either`.
    */
  def checkRenderThread(): Unit =
    if !isRenderThread then
      throw IllegalStateException(
        s"UI state must be mutated on the render thread, not '${Thread.currentThread().getName}'"
      )

  /** Runs `body` inline when already on the render thread, otherwise queues it for the next loop iteration. */
  def runOnRenderThread(body: => Unit): Unit =
    if isRenderThread then body else runLater(body)

  /** Always queues `body`; the runner executes queued work at the start of each loop iteration. */
  def runLater(body: => Unit): Unit =
    val _ = pending.add(() => body)

  private[tui] def register(thread: Thread): Unit =
    val _ = registered.add(thread)

  private[tui] def unregister(): Unit =
    val _ = registered.remove(Thread.currentThread())

  private[tui] def drainPending(): Unit =
    var task = pending.poll()
    while task != null do
      task()
      task = pending.poll()
