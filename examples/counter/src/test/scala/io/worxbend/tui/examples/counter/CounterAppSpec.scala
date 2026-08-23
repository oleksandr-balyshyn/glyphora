package io.worxbend.tui.examples.counter

import io.worxbend.tui.core.Size
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

/** Headless end-to-end test for the counter's primary interaction path. */
final class CounterAppSpec extends AnyFunSuite:

  test("increments and decrements re-render the count, and q quits"):
    val backend = HeadlessBackend(Size(44, 6))
    val app     = CounterApp()
    val pilot   = Pilot.start(backend) { app.runWith(backend) }
    pilot.waitForIdle()
    assert(pilot.screenText.contains("Count: 0"))
    pilot.press("+", "+", "+").waitForIdle()
    assert(pilot.screenText.contains("Count: 3"))
    pilot.press("-").waitForIdle()
    assert(pilot.screenText.contains("Count: 2"))
    assert(app.count.peek == 2)
    pilot.press("q")
    assert(pilot.awaitTermination())
