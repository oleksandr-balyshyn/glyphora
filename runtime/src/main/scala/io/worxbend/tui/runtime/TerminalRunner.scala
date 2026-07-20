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
        RenderThread.register(Thread.currentThread())
        // a `try/finally` alone only protects against exceptions unwinding this thread; a signal that terminates the
        // JVM directly (SIGTERM, SIGHUP) skips straight to shutdown hooks and would otherwise leave the terminal in
        // raw mode / the alternate screen. `close()` is idempotent (each teardown step is guarded by its own flag),
        // so the hook racing the normal-path close below is harmless either way.
        val restoreOnShutdown = new Thread(() => backend.close(), "glyphora-terminal-restore")
        Runtime.getRuntime.addShutdownHook(restoreOnShutdown)
        try loop(handleEvent, render).left.map(RunnerError.Backend(_))
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

  private def loop(
      handleEvent: (Event, RunnerHandle) => Boolean,
      render: Frame => Unit,
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

    def redraw(): Unit =
      val drawn =
        for
          size <- backend.size
          area   = Rect(size)
          buffer = frameBuffer.filter(_.area == area).getOrElse(Buffer(area))
          _      = buffer.reset()
          _      = frameBuffer = Some(buffer)
          _      = render(Frame(area, buffer))
          _ <- backend.draw(buffer)
        yield ()
      drawn.left.foreach(error => failure = Some(error))

    redraw()
    while running && failure.isEmpty do
      RenderThread.drainPending()
      // queued work (runLater/runOnRenderThread) may have invalidated state between events
      if redrawRequested() && running && failure.isEmpty then redraw()
      backend.readEvent(pollTimeout(lastTick)) match
        case Left(error)        => failure = Some(error)
        case Right(Some(event)) =>
          val wantsRedraw = handleEvent(event, handle)
          val isResize    = event match
            case Event.Resize(_) => true
            case _               => false
          if (wantsRedraw || isResize) && running && failure.isEmpty then redraw()
        case Right(None)        => ()
      config.tickRate.foreach { rate =>
        if nanoTime() - lastTick >= rate.toNanos then
          lastTick = nanoTime()
          if handleEvent(Event.Tick, handle) && running && failure.isEmpty then redraw()
      }
    failure.toLeft(())

  /** Block on input at most until the next tick is due (or a coarse default poll when there is no tick rate), so ticks
    * stay on schedule while input stays responsive.
    */
  private def pollTimeout(lastTick: Long): FiniteDuration =
    config.tickRate match
      case None       => DefaultPollTimeout
      case Some(rate) =>
        val remainingNanos = rate.toNanos - (nanoTime() - lastTick)
        val clamped        = math.max(0L, math.min(remainingNanos, rate.toNanos))
        Duration.fromNanos(clamped)

  private val DefaultPollTimeout: FiniteDuration = 100.millis
