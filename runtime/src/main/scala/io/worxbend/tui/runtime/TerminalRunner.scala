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
        // recorded, not fatal: absorbing a throwing continuation is the point, so the loop keeps running and the
        // failures are reported once the app has exited rather than tearing it down mid-frame. Only `run` writes and
        // reads these, and only from the render thread, so no volatile is needed.
        var taskFailure: Option[Throwable] = None
        var taskFailureCount               = 0
        // every failure counts, and every one up to the cap keeps its own stack trace: a continuation that throws on
        // every tick must not report as a single incident, which is the silent-failure mode this default exists to
        // avoid in the first place
        val record: RenderTaskErrorHandler = error =>
          taskFailureCount += 1
          taskFailure match
            case None        => taskFailure = Some(error)
            case Some(first) =>
              // `addSuppressed(self)` throws, and a cached throwable rethrown every tick is exactly the shape that
              // hits it
              if !(first eq error) && first.getSuppressed.length < MaxSuppressedTaskFailures then
                first.addSuppressed(error)
        val loop                           = RenderThread.register(
          Thread.currentThread(),
          () => backend.wake(),
          config.onTaskError.getOrElse(record),
        )
        // a `try/finally` alone only protects against exceptions unwinding this thread; a signal that terminates the
        // JVM directly (SIGTERM, SIGHUP) skips straight to shutdown hooks. `close()` is the tidy path, but by the time
        // a hook runs the backend's own resources may already have been torn down underneath it — so follow up with
        // `emergencyRestore()`, which takes the shortest path to a usable terminal and cannot fail.
        val restoreOnShutdown              = new Thread(
          () =>
            try backend.close()
            finally backend.emergencyRestore(),
          "glyphora-terminal-restore",
        )
        Runtime.getRuntime.addShutdownHook(restoreOnShutdown)
        try
          val outcome     = runLoop(handleEvent, render, loop)
          val taskOutcome = taskFailure.map(QueuedTaskFailures(_, taskFailureCount))
          outcome match
            // a terminal failure ends the loop but says nothing about the tasks that already failed, so it carries
            // them rather than replacing them
            case Left(error) => Left(RunnerError.Backend(error, taskOutcome))
            case Right(())   => taskOutcome.map(RunnerError.QueuedTask(_)).toLeft(())
        finally
          RenderThread.unregister()
          backend.close()
          try Runtime.getRuntime.removeShutdownHook(restoreOnShutdown)
          catch case _: IllegalStateException => () // the JVM is already shutting down; the hook will just no-op

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
    var running                       = true
    var failure: Option[BackendError] = None
    var frameBuffer: Option[Buffer]   = None
    var lastTick                      = nanoTime()

    val handle = new RunnerHandle:
      def quit(): Unit                           = running = false
      def runOnRenderThread(body: => Unit): Unit = RenderThread.runOnRenderThread(body)
      def copyToClipboard(text: String): Unit    =
        backend.copyToClipboard(text).left.foreach(error => failure = Some(error))
      // both run on the render thread (handlers are invoked there); the backend forces a full repaint afterward
      override def suspend(body: => Unit): Unit  = backend.suspend(body).left.foreach(error => failure = Some(error))
      override def printAbove(lines: Seq[String]): Unit =
        backend.printAbove(lines).left.foreach(error => failure = Some(error))

    def redraw(): Unit =
      val drawn = backend.size.flatMap { size =>
        val area   = Rect(size)
        // the previous frame's buffer is reused whenever the terminal did not resize: the backend diffs against what
        // it last flushed, so only the composition has to be redone
        val buffer = frameBuffer.filter(_.area == area).getOrElse(Buffer(area))
        buffer.reset()
        frameBuffer = Some(buffer)
        render(Frame(area, buffer))
        backend.draw(buffer)
      }
      drawn.left.foreach(error => failure = Some(error))

    /** Dispatches one event; `true` when the frame should be repainted afterward. */
    def dispatch(event: Event): Boolean =
      event match
        case Event.Interrupt =>
          // an app that does not consume Ctrl+C quits cleanly, so teardown runs on the normal path
          if handleEvent(event, handle) then true
          else
            handle.quit()
            false
        case _               =>
          val wantsRedraw = handleEvent(event, handle)
          wantsRedraw || event.isInstanceOf[Event.Resize] // scalafix:ok DisableSyntax; narrowing a caught Throwable

    redraw()
    while running && failure.isEmpty do
      RenderThread.drainPending(loop)
      // queued work (runLater/runOnRenderThread) may have invalidated state between events
      if redrawRequested() && running && failure.isEmpty then redraw()
      backend.readEvent(pollTimeout(lastTick)) match
        case Left(error)        => failure = Some(error)
        case Right(Some(event)) =>
          // deliberately one event per redraw: the element tree that routes focus and hit-testing is published *by*
          // rendering, so folding several key events into one frame would dispatch the later ones against a stale
          // tree — Tab would move focus and the next keystroke would still go to the previous element. Floods are
          // bounded anyway: a paste arrives as a single `Event.Paste`, and resizes coalesce inside the backend.
          if dispatch(event) && running && failure.isEmpty then redraw()
        case Right(None)        => ()
      config.tickRate.foreach { rate =>
        if nanoTime() - lastTick >= rate.toNanos then
          lastTick = nanoTime()
          if handleEvent(Event.Tick, handle) && running && failure.isEmpty then redraw()
      }
    failure.toLeft(())

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

  private val DefaultPollTimeout: FiniteDuration = 100.millis

  /** The floor for any poll: short enough to be indistinguishable from "check now", never zero. */
  private val MinPollNanos: Long = 1_000_000L

  /** How many queued-task throwables past the first keep their own stack trace on the reported failure. Bounded because
    * a long-lived app can fail on every tick; the reported count stays exact regardless.
    */
  private val MaxSuppressedTaskFailures: Int = 16
