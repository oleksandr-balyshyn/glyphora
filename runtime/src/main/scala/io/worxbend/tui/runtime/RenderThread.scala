package io.worxbend.tui.runtime

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}

/** The single-render-thread model.
  *
  * All UI state mutation must happen on a render thread — the thread running a [[Runner]] loop. The guard is
  * deliberately a no-op while no render thread is registered, so unit tests of widgets and signals need no running
  * runtime.
  *
  * Each runner owns its own work queue rather than sharing one process-wide, so two runners in the same JVM (parallel
  * test suites, an embedded app) never execute each other's queued work on the wrong thread.
  */
object RenderThread:

  /** One runner's queue of work waiting to run on its render thread. */
  final class RenderLoop private[runtime] (wake: () => Unit):

    private val pending = ConcurrentLinkedQueue[() => Unit]()

    /** Queues `body` and nudges the runner, so it is picked up on the next iteration rather than at the next poll. */
    private[runtime] def enqueue(body: () => Unit): Unit =
      val _ = pending.add(body)
      wake()

    private[runtime] def drain(): Unit =
      var task = pending.poll()
      while task != null do // scalafix:ok DisableSyntax; java.util.concurrent interop
        task()
        task = pending.poll()

    private[runtime] def isEmpty: Boolean = pending.isEmpty

  // Several runners may live in one JVM and must not race on a single registration slot. Each thread maps to a stack
  // (innermost registration first) so a runner started from inside another runner's loop restores its host on exit
  // instead of deregistering the thread outright.
  private val loops = ConcurrentHashMap[Thread, List[RenderLoop]]()

  /** Work queued when the caller belongs to no runner and more than one is running — genuinely ambiguous, so it is
    * drained by whichever render thread gets there first. With zero or one runner the routing is exact.
    */
  private val detached = RenderLoop(() => ())

  def isRenderThread: Boolean =
    loops.isEmpty || loops.containsKey(Thread.currentThread())

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
    capture().enqueue(() => body)

  /** The loop that should receive work queued from *this* thread.
    *
    * Background workers must call this while still on the render thread (before they go async) so their continuation
    * comes back to the runner that started it — see [[Async]].
    */
  def capture(): RenderLoop =
    val own = loops.get(Thread.currentThread())
    if own != null then own.head // scalafix:ok DisableSyntax; java.util.concurrent interop
    else
      val all = loops.values.iterator
      if !all.hasNext then detached
      else
        val first = all.next()
        if all.hasNext then detached else first.head

  private[tui] def register(thread: Thread, wake: () => Unit): RenderLoop =
    val loop = RenderLoop(wake)
    val _    = loops.compute(
      thread,
      (_, enclosing) => loop :: (if enclosing == null then Nil else enclosing),
    ) // scalafix:ok DisableSyntax; java.util.concurrent interop
    loop

  /** Registers `thread` without a wake-up channel — for drivers that poll instead of blocking. */
  private[tui] def register(thread: Thread): RenderLoop =
    register(thread, () => ())

  /** Pops the calling thread's innermost registration, restoring an enclosing runner's if there is one. */
  private[tui] def unregister(): Unit =
    val _ = loops.compute(
      Thread.currentThread(),
      (_, registered) =>
        val enclosing =
          if registered == null then Nil
          else registered.drop(1) // scalafix:ok DisableSyntax; java.util.concurrent interop
        if enclosing.isEmpty then null else enclosing, // scalafix:ok DisableSyntax; java.util.concurrent interop
    )

  /** Runs everything queued for `loop`, plus anything that could not be attributed to a specific runner. */
  private[tui] def drainPending(loop: RenderLoop): Unit =
    loop.drain()
    detached.drain()

  /** Drains whatever is queued for the calling thread, plus unattributed work. */
  private[tui] def drainPending(): Unit =
    val own = loops.get(Thread.currentThread())
    if own != null then own.head.drain() // scalafix:ok DisableSyntax; java.util.concurrent interop
    detached.drain()

  private[tui] def hasPending(loop: RenderLoop): Boolean =
    !loop.isEmpty || !detached.isEmpty
