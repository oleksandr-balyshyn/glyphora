package io.worxbend.tui.runtime

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.DurationInt

final class TimersSpec extends AnyFunSuite:

  test("formatDuration switches to hh:mm:ss past an hour"):
    assert(formatDuration(0.seconds) == "00:00")
    assert(formatDuration(75.seconds) == "01:15")
    assert(formatDuration(3661.seconds) == "1:01:01")

  test("a stopwatch only accrues time while running"):
    val watch = Stopwatch()
    watch.tick(1.second) // not started yet
    assert(watch.elapsed == 0.seconds)
    watch.start()
    watch.tick(2.seconds)
    watch.tick(3.seconds)
    assert(watch.elapsed == 5.seconds)
    watch.stop()
    watch.tick(10.seconds)
    assert(watch.elapsed == 5.seconds)
    assert(watch.formatted == "00:05")

  test("reset clears a stopwatch and stops it"):
    val watch = Stopwatch()
    watch.start()
    watch.tick(9.seconds)
    watch.reset()
    assert(watch.elapsed == 0.seconds)
    assert(!watch.isRunning)

  test("a timer counts down, clamps at zero, and fires justExpired exactly once"):
    val timer = Timer(5.seconds)
    timer.start()
    timer.tick(2.seconds)
    assert(timer.remaining == 3.seconds)
    assert(!timer.isExpired)
    timer.tick(10.seconds)   // over-shoot clamps to zero
    assert(timer.remaining == 0.seconds)
    assert(timer.isExpired)
    assert(!timer.isRunning) // expiry stops it
    assert(timer.justExpired())
    assert(!timer.justExpired()) // only the first time

  test("a timer created already at zero is immediately expired and never fires justExpired late"):
    val timer = Timer(0.seconds)
    assert(timer.isExpired)
    timer.start()
    assert(!timer.isRunning) // cannot start an already-expired timer
