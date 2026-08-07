package io.worxbend.tui.examples.airsensor

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration as JDuration
import java.util.concurrent.atomic.AtomicInteger

/** One complete sample from the sensor: everything the dashboard shows, taken at the same instant.
  *
  * A single immutable snapshot rather than one signal per metric. The cards must never disagree with each other, and a
  * value mutated in place is `==` to itself, so `Signal.set` would notify nobody — see the rolling history in
  * `AirSensorApp`.
  */
final case class Reading(co2Ppm: Double, pm25: Double, tvocIndex: Double, temperatureC: Double):

  /** Derived rather than stored, because the device does not report it. */
  def aqi: Double = AirQuality.aqiFromPm25(pm25)

/** The one seam between the app and the outside world.
  *
  * `read()` is called on a background thread and returns `Either` rather than throwing, so a dead sensor is an ordinary
  * value the state machine can render instead of an exception that has to be caught somewhere unhelpful.
  */
trait SensorClient:
  def read(): Either[String, Reading]

/** A scripted sensor that walks a fixed sequence and then repeats it.
  *
  * This is the default, so `./mill examples.airsensor.run` works on a laptop with no device and no network, and so the
  * headless tests are exactly reproducible. Point the app at [[AirGradientClient]] to read a real device.
  */
final class FakeSensor(script: Vector[Either[String, Reading]] = FakeSensor.DefaultScript) extends SensorClient:
  require(script.nonEmpty, "a scripted sensor needs at least one entry")

  // read() runs on an Async worker thread, so the cursor cannot be a plain var
  private val cursor = AtomicInteger(0)

  def read(): Either[String, Reading] =
    script(cursor.getAndIncrement() % script.size)

object FakeSensor:

  /** A small room with the door shut, then opened: CO2 and TVOC climb through every band and fall back, and a burst of
    * particulates crosses the PM2.5 thresholds on the way. Chosen so a reader running the example sees every colour and
    * every band word within a minute rather than a flat green screen.
    */
  val DefaultScript: Vector[Either[String, Reading]] =
    Vector(
      Reading(co2Ppm = 640, pm25 = 4.1, tvocIndex = 72, temperatureC = 21.2),
      Reading(co2Ppm = 715, pm25 = 6.8, tvocIndex = 96, temperatureC = 21.6),
      Reading(co2Ppm = 905, pm25 = 12.4, tvocIndex = 148, temperatureC = 22.1),
      Reading(co2Ppm = 1180, pm25 = 24.9, tvocIndex = 212, temperatureC = 22.8),
      Reading(co2Ppm = 1465, pm25 = 41.2, tvocIndex = 268, temperatureC = 23.4),
      Reading(co2Ppm = 1720, pm25 = 58.6, tvocIndex = 331, temperatureC = 24.1),
      Reading(co2Ppm = 1290, pm25 = 33.5, tvocIndex = 240, temperatureC = 23.6),
      Reading(co2Ppm = 880, pm25 = 11.2, tvocIndex = 130, temperatureC = 22.4),
    ).map(Right(_))

/** Reads an AirGradient ONE over its local HTTP API — `GET http://<device>/measures/current`.
  *
  * Two plain GETs' worth of machinery: no authentication, no dependency, and a five-second timeout so a device that has
  * dropped off the network fails as a message on screen rather than as a frozen poller.
  */
final class AirGradientClient(
    baseUrl: String = "http://airgradient.local",
    httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(JDuration.ofSeconds(5)).build(),
) extends SensorClient:

  def read(): Either[String, Reading] =
    fetch(s"$baseUrl/measures/current").flatMap(AirGradientClient.readingFrom)

  private def fetch(url: String): Either[String, String] =
    try
      val request  = HttpRequest.newBuilder(URI.create(url)).timeout(JDuration.ofSeconds(5)).GET().build()
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      if response.statusCode() == 200 then Right(response.body())
      else Left(s"sensor returned HTTP ${response.statusCode()}")
    catch
      case e: java.io.IOException  => Left(describe(e))
      case e: InterruptedException => Left(describe(e))

  private def describe(error: Throwable): String =
    Option(error.getMessage).getOrElse(error.getClass.getSimpleName)

object AirGradientClient:

  /** `/measures/current` returns a flat object of numbers, so a full JSON parser would be more machinery than the
    * payload deserves. This reads `"name": number` pairs and ignores everything else, which is exactly enough — and it
    * keeps the example dependency-free, the same trade `examples/weather` makes for its own API.
    */
  private val NumericField = """"([A-Za-z0-9_]+)"\s*:\s*(-?\d+(?:\.\d+)?)""".r

  def numericFields(body: String): Map[String, Double] =
    NumericField.findAllMatchIn(body).map(matched => matched.group(1) -> matched.group(2).toDouble).toMap

  /** Split out from the client so the parsing is testable without a device on the network. */
  def readingFrom(body: String): Either[String, Reading] =
    val numbers                                     = numericFields(body)
    def field(name: String): Either[String, Double] = numbers.get(name).toRight(s"missing field '$name'")
    for
      co2  <- field("rco2")
      pm25 <- field("pm02")
      tvoc <- field("tvocIndex")
      temp <- field("atmp")
    yield Reading(co2, pm25, tvoc, temp)
