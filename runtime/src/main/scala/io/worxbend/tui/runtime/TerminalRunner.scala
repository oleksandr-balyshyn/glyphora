package io.worxbend.tui.runtime

import io.worxbend.tui.core.{Buffer, Event, Rect}
import io.worxbend.tui.terminal.{Backend, BackendError}

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

/** The production [[Runner]]: raw mode + alternate screen setup, diff-driven redraws, tick emission, resize handling,
  * and render-thread registration around a [[Backend]].
  *
  * `nanoTime` is injectable so tick scheduling is testable; production code uses the system clock.
  */
final class TerminalRunner(
    backend: Backend,
    config: RunnerConfig = RunnerConfig(),
    nanoTime: () => Long = () => System.nanoTime(),
    redrawRequested: () => Boolean = () => false,
) extends Runner:

  def run(
      handleEvent: (Event, RunnerHandle) => Boolean,
      render: Frame => Unit,
  ): Either[RunnerError, Unit] =
    setup() match
      case Left(error) =>
        backend.close()
        Left(RunnerError.Backend(error))
      case Right(())   =>
        val recorder          = new QueuedTaskFailureRecorder
        val loop              = RenderThread.register(
          Thread.currentThread(),
          () => backend.wake(),
          config.onTaskError.getOrElse(recorder),
        )
        val restoreOnShutdown = installRestoreHook()
        try
          val outcome = runLoop(handleEvent, render, loop)
          report(outcome, recorder.collected)
        finally
          RenderThread.unregister()
          backend.close()
          removeRestoreHook(restoreOnShutdown)

  /** Combines the loop's own outcome with the queued-task failures it absorbed into what [[run]] returns.
    *
    * A terminal backend failure ends the loop but says nothing about the tasks that already failed, so it carries them
    * rather than replacing them. Called from `run` on the render thread, once the loop has exited.
    */
  private def report(
      outcome: Either[BackendError, Unit],
      tasks: Option[QueuedTaskFailures],
  ): Either[RunnerError, Unit] =
    outcome match
      case Left(error) => Left(RunnerError.Backend(error, tasks))
      case Right(())   => tasks.map(RunnerError.QueuedTask(_)).toLeft(())

  /** Registers a JVM shutdown hook that restores the terminal, and hands the hook back so `run` can remove it again.
    *
    * A `try/finally` alone only protects against exceptions unwinding this thread; a signal that terminates the JVM
    * directly (SIGTERM, SIGHUP) skips straight to shutdown hooks. `close()` is the tidy path, but by the time a hook
    * runs the backend's own resources may already have been torn down underneath it — so follow up with
    * `emergencyRestore()`, which takes the shortest path to a usable terminal and cannot fail.
    *
    * The returned thread is owned by `run`, which is the only caller and the only thing that unregisters it.
    */
  private def installRestoreHook(): Thread =
    val hook = new Thread(
      () =>
        try backend.close()
        finally backend.emergencyRestore(),
      "glyphora-terminal-restore",
    )
    Runtime.getRuntime.addShutdownHook(hook)
    hook

  /** Unregisters the hook [[installRestoreHook]] returned, on the normal teardown path where `run` has already closed
    * the backend itself. Tolerates a JVM that is already shutting down: the hook is then running (or about to), there
    * is nothing left to remove, and its work is harmless anyway.
    */
  private def removeRestoreHook(hook: Thread): Unit =
    try
      val _ = Runtime.getRuntime.removeShutdownHook(hook)
    catch case _: IllegalStateException => ()

  private def setup(): Either[BackendError, Unit] =
    for
      _ <- backend.enableRawMode()
      _ <- backend.enterAlternateScreen()
      _ <- backend.hideCursor()
      _ <- if config.mouseCapture then backend.enableMouseCapture() else Right(())
    yield ()

  private def runLoop(
      handleEvent: (Event, RunnerHandle) => Boolean,
      render: Frame => Unit,
      loop: RenderThread.RenderLoop,
  ): Either[BackendError, Unit] =
    val state    = LoopState()
    var lastTick = nanoTime()

    val handle   = BackendHandle(backend, state)
    val composer = FrameComposer(backend, render)

    def redraw(): Unit = state.record(composer.compose())

    /** Dispatches one event; `true` when the frame should be repainted afterward. */
    def dispatch(event: Event): Boolean =
      event match
        case Event.Interrupt =>
          // an app that does not consume Ctrl+C quits cleanly, so teardown runs on the normal path
          if handleEvent(event, handle) then true
          else
            handle.quit()
            false
        case _: Event.Resize =>
          // a resize always repaints, whatever the handler answers, because the composed frame no longer fits the
          // terminal. The handler still runs, and runs first, so an app that tracks its own dimensions sees the event.
          val _ = handleEvent(event, handle)
          true
        case _               => handleEvent(event, handle)

    redraw()
    while state.isLive do
      RenderThread.drainPending(loop)
      // queued work (runLater/runOnRenderThread) may have invalidated state between events
      if redrawRequested() && state.isLive then redraw()
      backend.readEvent(pollTimeout(lastTick)) match
        case Left(error)        => state.fail(error)
        case Right(Some(event)) =>
          // deliberately one event per redraw: the element tree that routes focus and hit-testing is published *by*
          // rendering, so folding several key events into one frame would dispatch the later ones against a stale
          // tree — Tab would move focus and the next keystroke would still go to the previous element. Floods are
          // bounded anyway: a paste arrives as a single `Event.Paste`, and resizes coalesce inside the backend.
          if dispatch(event) && state.isLive then redraw()
        case Right(None)        => ()
      config.tickRate.foreach { rate =>
        if nanoTime() - lastTick >= rate.toNanos then
          lastTick = nanoTime()
          if handleEvent(Event.Tick, handle) && state.isLive then redraw()
      }
    state.outcome

  /** How long to block on input when the app asked for no tick rate at all. */
  private val DefaultPollTimeout: FiniteDuration = 100.millis

  /** The floor for any poll: short enough to be indistinguishable from "check now", never zero. */
  private val MinPollNanos: Long = 1_000_000L

  /** Block on input at most until the next tick is due (or a coarse default poll when there is no tick rate), so ticks
    * stay on schedule while input stays responsive.
    *
    * Never returns zero: [[Backend.readEvent]] treats a non-positive timeout as "block until an event arrives", so an
    * overrunning frame would otherwise wedge the loop until the user happened to press a key.
    */
  private def pollTimeout(lastTick: Long): FiniteDuration =
    config.tickRate match
      case None       => DefaultPollTimeout
      case Some(rate) =>
        val remainingNanos = rate.toNanos - (nanoTime() - lastTick)
        val clamped        = math.max(MinPollNanos, math.min(remainingNanos, rate.toNanos))
        Duration.fromNanos(clamped)

/** Composes one frame into a [[Buffer]] and hands it to the backend to flush, for the lifetime of one
  * [[TerminalRunner]] loop.
  *
  * This exists to own the buffer-reuse policy in one place. The backend diffs each frame against the one it last
  * flushed, so nothing is gained by allocating a fresh buffer per frame — the previous one is cleared and reused for as
  * long as the terminal keeps the same size, and only a resize forces a new allocation.
  *
  * Confined to the render thread, which is where the loop calls it; the cached buffer needs no synchronisation.
  */
private final class FrameComposer(backend: Backend, render: Frame => Unit):

  private var frameBuffer: Option[Buffer] = None

  /** Asks the backend for the current size, composes the frame into the (reused or freshly sized) buffer and flushes
    * it. A failure at either backend call is returned rather than thrown, for the loop to record.
    */
  def compose(): Either[BackendError, Unit] =
    backend.size.flatMap { size =>
      val area   = Rect(size)
      val buffer = frameBuffer.filter(_.area == area).getOrElse(Buffer(area))
      buffer.reset()
      frameBuffer = Some(buffer)
      render(Frame(area, buffer))
      backend.draw(buffer)
    }

/** The two reasons a [[TerminalRunner]] loop stops, held in one place so every site asks the same question.
  *
  * The loop asks "should I keep going?" at four points and can be stopped from five more, so both questions get one
  * name each — [[isLive]] and [[record]] — instead of being spelled out at each site. A third reason to stop is then a
  * change to [[isLive]] alone; spelled out, one missed site would draw an extra frame after the failure.
  *
  * Confined to the render thread: the loop, the [[BackendHandle]] it hands to event handlers, and the redraw/dispatch
  * helpers all run there, so neither field needs to be volatile.
  */
private final class LoopState:

  private var running                       = true
  private var failure: Option[BackendError] = None

  /** Whether the loop should run another iteration: nobody has asked to quit and no backend call has failed. */
  def isLive: Boolean = running && failure.isEmpty

  /** Asks for a clean exit once the current iteration finishes — what [[RunnerHandle.quit]] does. */
  def stop(): Unit = running = false

  /** Records the backend failure that ends the loop. The most recent one is what gets reported. */
  def fail(error: BackendError): Unit = failure = Some(error)

  /** Records `result`'s failure if it has one; a successful value is deliberately discarded. */
  def record[A](result: Either[BackendError, A]): Unit = result.left.foreach(fail)

  /** What the loop returns to `run`: the recorded failure, or `Right(())` when it exited cleanly. */
  def outcome: Either[BackendError, Unit] = failure.toLeft(())

/** The [[RunnerHandle]] handed to event handlers for the lifetime of one [[TerminalRunner]] loop.
  *
  * Every method here runs on the render thread, because that is where handlers are invoked. A backend call that fails
  * is not thrown: it is recorded on the shared [[LoopState]], which ends the loop after the current iteration rather
  * than unwinding it mid-frame.
  */
private final class BackendHandle(backend: Backend, state: LoopState) extends RunnerHandle:

  def quit(): Unit                           = state.stop()
  def runOnRenderThread(body: => Unit): Unit = RenderThread.runOnRenderThread(body)
  def copyToClipboard(text: String): Unit    = state.record(backend.copyToClipboard(text))

  // the backend forces a full repaint once `body` hands the terminal back
  override def suspend(body: => Unit): Unit         = state.record(backend.suspend(body))
  override def printAbove(lines: Seq[String]): Unit = state.record(backend.printAbove(lines))

/** Collects the failures of bodies queued onto the render thread during a single [[TerminalRunner.run]].
  *
  * Recording rather than rethrowing is the whole point: absorbing a throwing continuation keeps the loop running, and
  * the failures are reported once the app has exited rather than tearing it down mid-frame. Every failure is counted,
  * and every one up to [[MaxSuppressedTaskFailures]] keeps its own stack trace — a continuation that throws on every
  * tick must not report as a single incident, which is the silent-failure mode this default exists to avoid in the
  * first place.
  *
  * An instance is owned by the `run` that created it and is touched only from the render thread, so its state needs no
  * volatile and no lock.
  */
private final class QueuedTaskFailureRecorder extends RenderTaskErrorHandler:

  /** How many queued-task throwables past the first keep their own stack trace on the reported failure. Bounded because
    * a long-lived app can fail on every tick; the reported count stays exact regardless.
    */
  private val MaxSuppressedTaskFailures: Int = 16

  private var firstFailure: Option[Throwable] = None
  private var failureCount                    = 0

  def handle(error: Throwable): Unit =
    failureCount += 1
    firstFailure match
      case None        => firstFailure = Some(error)
      case Some(first) =>
        // `addSuppressed(self)` throws, and a cached throwable rethrown every tick is exactly the shape that hits it
        if !(first eq error) && first.getSuppressed.length < MaxSuppressedTaskFailures then first.addSuppressed(error)

  /** What the run reports: the throwable that started it plus the exact total, or `None` when nothing failed. */
  def collected: Option[QueuedTaskFailures] = firstFailure.map(QueuedTaskFailures(_, failureCount))
