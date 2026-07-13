package io.worxbend.tui.examples.dashboard

import io.worxbend.tui.core.{KeyCode, Size}
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.DurationInt

final class DashboardAppSpec extends AnyFunSuite:

  test("ticks drive redraws without any input"):
    val backend     = HeadlessBackend(Size(60, 16))
    val app         = DashboardApp()
    val pilot       = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    val ticksBefore = app.tick.peek
    val drawsBefore = backend.drawCount
    // poll instead of a fixed sleep: under parallel test load the tick thread may be starved for a while
    val deadline    = System.nanoTime() + 10.seconds.toNanos
    while (app.tick.peek == ticksBefore || backend.drawCount == drawsBefore) && System.nanoTime() < deadline do
      Thread.sleep(50)
    assert(app.tick.peek > ticksBefore)
    assert(backend.drawCount > drawsBefore)
    assert(pilot.screenText.contains("Load"))
    assert(pilot.screenText.contains("Throughput"))
    assert(pilot.screenText.contains("Signal"))
    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination(2.seconds))
