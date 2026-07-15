package io.worxbend.tui.examples.weather

import io.worxbend.tui.core.{KeyCode, Size}
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.{DurationInt, FiniteDuration}

private final class FakeWeatherClient(response: Either[WeatherError, WeatherReport]) extends WeatherClient:
  @volatile var lastRequestedCity: Option[String] = None

  def fetch(city: String): Either[WeatherError, WeatherReport] =
    lastRequestedCity = Some(city)
    response

final class WeatherAppSpec extends AnyFunSuite:

  /** Polls the rendered screen until `predicate` holds — the async fetch lands on the render thread on the next
    * `RenderThread` drain, not synchronously with the key press that triggered it.
    */
  private def waitUntil(pilot: Pilot, timeout: FiniteDuration = 5.seconds)(predicate: String => Boolean): Unit =
    val deadline = System.nanoTime() + timeout.toNanos
    while !predicate(pilot.screenText) && System.nanoTime() < deadline do Thread.sleep(20)

  private val sampleReport = WeatherReport(
    city = "Lisbon",
    country = "Portugal",
    temperatureC = 22.5,
    humidityPercent = 61.0,
    windKph = 14.0,
    isDay = true,
    conditionCode = 1,
  )

  test("typing a city and pressing Enter shows the fetched conditions"):
    val client  = FakeWeatherClient(Right(sampleReport))
    val backend = HeadlessBackend(Size(60, 16))
    val app     = WeatherApp(client)
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    pilot.typeText("Lisbon").pressKey(KeyCode.Enter)
    waitUntil(pilot)(_.contains("Lisbon, Portugal"))

    assert(client.lastRequestedCity.contains("Lisbon"))
    assert(pilot.screenText.contains("Mainly clear"))
    assert(pilot.screenText.contains("22.5"))
    pilot.pressKey(KeyCode.Escape)
    assert(pilot.awaitTermination(2.seconds))

  test("a failed lookup shows an error instead of crashing"):
    val client  = FakeWeatherClient(Left(WeatherError.CityNotFound("Nowhereville")))
    val backend = HeadlessBackend(Size(60, 16))
    val app     = WeatherApp(client)
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    pilot.typeText("Nowhereville").pressKey(KeyCode.Enter)
    waitUntil(pilot)(_.contains("Couldn't fetch Nowhereville"))

    assert(pilot.screenText.contains("Couldn't fetch Nowhereville"))
    pilot.pressKey(KeyCode.Escape)
    assert(pilot.awaitTermination(2.seconds))
