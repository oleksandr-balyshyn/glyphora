package io.worxbend.tui.terminal

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.{Duration, DurationInt}

/** The parts of [[JLine3Backend]] that do not need a real TTY.
  *
  * Constructing the backend needs a controlling terminal, which CI does not have, so anything testable here has to be a
  * pure function on the companion — which is reason enough to keep the timeout arithmetic there rather than inline.
  */
final class JLine3BackendSpec extends AnyFunSuite:

  test("a finite timeout is passed through in milliseconds"):
    assert(JLine3Backend.readTimeoutMillis(250.millis) == 250L)
    assert(JLine3Backend.readTimeoutMillis(2.seconds) == 2000L)

  test("an infinite timeout becomes JLine's blocking read instead of throwing"):
    // `Duration.Inf.toMillis` throws, and the throw was caught as `BackendError.Io` — which the runner treats as
    // fatal, so the blocking read that `Backend.readEvent` documents killed the app instead of waiting
    assert(JLine3Backend.readTimeoutMillis(Duration.Inf) == 0L)
