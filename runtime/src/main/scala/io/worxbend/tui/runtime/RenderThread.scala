package io.worxbend.tui.runtime

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.util.control.NonFatal

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

  /** One runner's queue of work waiting to run on its render thread.
    *
    * `onError` is supplied by the runner that owns this loop and receives throwables escaping queued bodies.
    */
  final class RenderLoop private[runtime] (wake: () => Unit, private[runtime] val onError: RenderTaskErrorHandler):

    private val pending = ConcurrentLinkedQueue[() => Unit]()

    /** Queues `body` and nudges the runner, so it is picked up on the next iteration rather than at the next poll. */
    private[runtime] def enqueue(body: () => Unit): Unit =
      val _ = pending.add(body)
      wake()

    /** Runs everything queued, in FIFO order, isolating each body: a `NonFatal` throwable from one task goes to
      * `handler` and the drain continues with the next task, so one failing continuation can neither stop the runner
      * loop nor discard the bodies queued behind it. Fatal errors propagate, as does anything `handler` itself throws.
      *
      * Runs on the render thread that owns this loop.
      */
    private[runtime] def drain(handler: RenderTaskErrorHandler = onError): Unit =
      var task = pending.poll()
      while task != null do // scalafix:ok DisableSyntax; java.util.concurrent interop
        try task()
        catch case NonFatal(error) => handler.handle(error)
        task = pending.poll()

    private[runtime] def isEmpty: Boolean = pending.isEmpty

  // Several runners may live in one JVM and must not race on a single registration slot. Each thread maps to a stack
  // (innermost registration first) so a runner started from inside another runner's loop restores its host on exit
  // instead of deregistering the thread outright.
  private val loops = ConcurrentHashMap[Thread, List[RenderLoop]]()

  /** Work queued when the caller belongs to no runner and more than one is running — genuinely ambiguous, so it is
    * drained by whichever render thread gets there first. With zero or one runner the routing is exact.
    */
  private val detached = RenderLoop(() => (), RenderTaskErrorHandler.rethrow)

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

  /** Registers `thread` as a render thread and returns its queue. `wake` nudges a blocked runner when work is queued
    * (drivers that poll instead of blocking can leave it at the no-op default); `onError` receives throwables escaping
    * queued bodies drained by this loop, and defaults to rethrowing so an unowned loop never swallows a failure.
    */
  private[tui] def register(
      thread: Thread,
      wake: () => Unit = () => (),
      onError: RenderTaskErrorHandler = RenderTaskErrorHandler.rethrow,
  ): RenderLoop =
    val loop = RenderLoop(wake, onError)
    val _    = loops.compute(
      thread,
      (_, enclosing) => loop :: (if enclosing == null then Nil else enclosing),
    ) // scalafix:ok DisableSyntax; java.util.concurrent interop
    loop

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

  /** Runs everything queued for `loop`, plus anything that could not be attributed to a specific runner. Unattributed
    * failures are reported through `loop`'s handler: the detached queue has no owner of its own, and `loop` is the one
    * whose runner would otherwise be taken down by them.
    */
  private[tui] def drainPending(loop: RenderLoop): Unit =
    loop.drain()
    detached.drain(loop.onError)

  /** Drains whatever is queued for the calling thread, plus unattributed work. With no runner registered the detached
    * queue keeps its own rethrowing handler, so a failing body still surfaces on the caller's thread.
    */
  private[tui] def drainPending(): Unit =
    val own = loops.get(Thread.currentThread())
    if own != null then drainPending(own.head) // scalafix:ok DisableSyntax; java.util.concurrent interop
    else detached.drain()

  private[tui] def hasPending(loop: RenderLoop): Boolean =
    !loop.isEmpty || !detached.isEmpty

/** How a render loop reports a `NonFatal` throwable escaping a body queued with [[RenderThread.runLater]] — typically
  * the continuation of some background work (see [[Async]]).
  *
  * Installed per loop rather than passed at the call site the way [[AsyncErrorHandler]] is: each runner owns its own
  * queue, and the queue is drained long after the caller that filled it is gone. It runs on the render thread, inside
  * the drain; anything it throws propagates and stops that drain, which is exactly how
  * [[RenderTaskErrorHandler.rethrow]] surfaces a failure on a loop no runner owns.
  */
trait RenderTaskErrorHandler:
  def handle(error: Throwable): Unit

object RenderTaskErrorHandler:
  /** Rethrows on the render thread. The default for an unowned loop, so a failing continuation in a test that drives
    * [[RenderThread]] directly still fails that test instead of vanishing.
    */
  val rethrow: RenderTaskErrorHandler = error => throw error

  /** Swallows the error silently. */
  val ignore: RenderTaskErrorHandler = _ => ()
