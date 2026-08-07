package io.worxbend.tui.examples.airsensor

import io.worxbend.tui.core.{KeyCode, Size}
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.{DurationInt, FiniteDuration}

final class AirSensorAppSpec extends AnyFunSuite:

  private val clean = Reading(co2Ppm = 640, pm25 = 4.1, tvocIndex = 72, temperatureC = 21.2)
  private val foul  = Reading(co2Ppm = 1900, pm25 = 90.0, tvocIndex = 380, temperatureC = 31.0)

  /** The poll interval used by every test that drives the app by hand: long enough that no timer fires behind the
    * assertions, so only the `r` key produces a second reading.
    */
  private val Manual = 10.seconds

  private def startedApp(
      script: Vector[Either[String, Reading]],
      interval: FiniteDuration = Manual,
  ): (AirSensorApp, Pilot, HeadlessBackend) =
    val backend = HeadlessBackend(Size(96, 30))
    val app     = AirSensorApp(FakeSensor(script), interval)
    // `runWith` takes the headless backend; `run()` would open the real TTY. The `val _` discards its Either so the
    // block types as Unit, which `-Wunused:all -Werror` insists on.
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    (app, pilot, backend)

  /** `waitForIdle` proves the posted event queue drained; it says nothing about an `Async` continuation landing
    * afterwards. Every sensor read lands on a later render-thread drain, so the assertions poll for it.
    */
  private def waitFor(timeout: FiniteDuration = 5.seconds)(predicate: => Boolean): Unit =
    val deadline = System.nanoTime() + timeout.toNanos
    while !predicate && System.nanoTime() < deadline do Thread.sleep(20)

  test("the first reading fills the hero panel and every metric card"):
    val (app, pilot, _) = startedApp(Vector(Right(clean)))
    waitFor()(pilot.screenText.contains("640 ppm"))

    val screen = pilot.screenText
    assert(screen.contains("AQI 23")) // 4.1 ug/m3 interpolated onto the EPA's first breakpoint
    assert(screen.contains("640 ppm"))
    assert(screen.contains("4.1 ug/m3"))
    assert(screen.contains("72 index"))
    assert(screen.contains("21.2 C"))
    assert(screen.contains("Good"))   // the band as a word, not only as a colour
    assert(app.status.peek == Status.Ready)
    assert(app.history.peek == Vector(clean))
    assert(app.worstBand.peek == Band.Good)

    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("a failed poll explains itself and keeps the last good reading on screen"):
    val (app, pilot, _) = startedApp(Vector(Right(clean), Left("sensor offline")))
    waitFor()(pilot.screenText.contains("640 ppm"))

    pilot.pressKey(KeyCode.Char('r'))
    waitFor()(pilot.screenText.contains("sensor offline"))

    val screen = pilot.screenText
    assert(screen.contains("showing the last good reading"))
    assert(screen.contains("640 ppm")) // the cards are still there — a failure never blanks the pane
    assert(app.status.peek == Status.Failed("sensor offline"))
    assert(app.history.peek == Vector(clean))

    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("readings arrive on the poll timer with no key presses"):
    val (app, pilot, backend) = startedApp(Vector(Right(clean), Right(foul)), interval = 150.millis)
    val drawsBefore           = backend.drawCount
    // poll rather than sleeping a fixed time: under parallel test load the timer thread may be starved for a while
    waitFor()(app.history.peek.sizeIs >= 2)

    assert(app.history.peek.take(2) == Vector(clean, foul))
    assert(backend.drawCount > drawsBefore) // the timer alone drove repaints
    assert(pilot.screenText.contains("History · last"))

    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("the band word and the worst-band summary follow the reading"):
    val (app, pilot, _) = startedApp(Vector(Right(clean), Right(foul)))
    waitFor()(pilot.screenText.contains("640 ppm"))
    assert(app.worstBand.peek == Band.Good)
    assert(pilot.screenText.contains("air quality: Good"))

    pilot.pressKey(KeyCode.Char('r'))
    waitFor()(pilot.screenText.contains("1900 ppm"))

    val screen = pilot.screenText
    assert(screen.contains("Unhealthy"))
    assert(screen.contains("Elevated")) // temperature bands on a range, so 31 C is uncomfortable, not unhealthy
    assert(app.worstBand.peek == Band.Unhealthy)

    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("h collapses the history pane and ? opens the help overlay"):
    val (_, pilot, _) = startedApp(Vector(Right(clean)))
    waitFor()(pilot.screenText.contains("History · last"))

    pilot.pressKey(KeyCode.Char('h')).waitForIdle()
    assert(!pilot.screenText.contains("History · last"))

    pilot.pressKey(KeyCode.Char('?')).waitForIdle()
    assert(pilot.screenText.contains("airsensor keys"))

    pilot.pressKey(KeyCode.Char('q'))
    assert(pilot.awaitTermination())

  test("an AirGradient /measures/current payload parses into a reading"):
    val body =
      """{"wifi":-52,"serialno":"ecda3b1eaaaa","rco2":812,"pm01":3.1,"pm02":6.8,"pm10":7.4,
        |"tvocIndex":143,"noxIndex":1,"atmp":21.4,"rhum":47.2,"boot":9}""".stripMargin

    assert(AirGradientClient.readingFrom(body) == Right(Reading(812.0, 6.8, 143.0, 21.4)))
    assert(AirGradientClient.readingFrom("""{"rco2":812}""") == Left("missing field 'pm02'"))

  test("AQI interpolates between the EPA's PM2.5 breakpoints"):
    assert(math.round(AirQuality.aqiFromPm25(0.0)) == 0L)
    assert(math.round(AirQuality.aqiFromPm25(9.0)) == 50L)
    assert(math.round(AirQuality.aqiFromPm25(35.4)) == 100L)
    assert(math.round(AirQuality.aqiFromPm25(1000.0)) == 500L)
    // banding is upper-inclusive, so 9.0 ug/m3 is still the top of Good
    assert(Metric.Pm25.classify(9.0) == Band.Good)
    assert(Metric.Pm25.classify(9.1) == Band.Moderate)
