package io.worxbend.tui.runtime

import io.worxbend.tui.core.{Buffer, Event, Size}
import io.worxbend.tui.terminal.{Backend, BackendError}

import scala.concurrent.duration.Duration

import org.scalatest.funsuite.AnyFunSuite

/** `RunnerConfig.keyEventTypes` has exactly one job: ask the backend, once, during setup. */
final class KeyEventTypesConfigSpec extends AnyFunSuite:

  /** Counts the setup calls and then ends the loop, so a run finishes without a terminal. */
  private final class SetupSpy extends Backend:
    var keyEventTypesAsked                                                = 0
    def size: Either[BackendError, Size]                                  = Right(Size(20, 3))
    def draw(b: Buffer): Either[BackendError, Unit]                       = Right(())
    def enableRawMode()                                                   = Right(())
    def disableRawMode()                                                  = Right(())
    def enterAlternateScreen()                                            = Right(())
    def leaveAlternateScreen()                                            = Right(())
    def enableMouseCapture()                                              = Right(())
    def disableMouseCapture()                                             = Right(())
    def hideCursor()                                                      = Right(())
    def showCursor()                                                      = Right(())
    def close(): Either[BackendError, Unit]                               = Right(())
    override def enableKeyEventTypes(): Either[BackendError, Unit]        =
      keyEventTypesAsked += 1
      Right(())
    def readEvent(timeout: Duration): Either[BackendError, Option[Event]] = Right(Some(Event.Interrupt))

  private def runOnce(config: RunnerConfig): SetupSpy =
    val backend = SetupSpy()
    val _       = TerminalRunner(backend, config).run(
      _ => (),
      (_, handle) => { handle.quit(); EventOutcome.Ignored },
      _ => (),
    )
    backend

  test("the flag is requested exactly once when the application asked for it"):
    assert(runOnce(RunnerConfig(keyEventTypes = true)).keyEventTypesAsked == 1)

  /** Off by default, and it has to stay off: the flag doubles the input volume for every keystroke, and an app that
    * never reads a release gains nothing for the traffic.
    */
  test("the flag is never requested by default"):
    assert(runOnce(RunnerConfig()).keyEventTypesAsked == 0)
    assert(runOnce(RunnerConfig(keyEventTypes = false)).keyEventTypesAsked == 0)
