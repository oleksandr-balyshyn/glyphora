package io.worxbend.tui.examples.weather

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration as JDuration

enum WeatherError:
  case CityNotFound(query: String)
  case NetworkFailure(reason: String)
  case UnexpectedResponse(reason: String)

object WeatherError:
  def describe(error: WeatherError): String = error match
    case CityNotFound(query)     => s"no city found matching \"$query\""
    case NetworkFailure(reason)  => s"network error: $reason"
    case UnexpectedResponse(why) => s"unexpected response: $why"

final case class WeatherReport(
    city: String,
    country: String,
    temperatureC: Double,
    humidityPercent: Double,
    windKph: Double,
    isDay: Boolean,
    conditionCode: Int,
):
  def condition: String = WmoCode.describe(conditionCode)

private final case class Location(name: String, country: String, latitude: Double, longitude: Double)

/** Looks up current conditions for a city name. */
trait WeatherClient:
  def fetch(city: String): Either[WeatherError, WeatherReport]

/** Talks to the free, key-less Open-Meteo APIs (https://open-meteo.com/): geocodes the city name, then pulls current
  * conditions for that location. Two plain HTTP GETs and a handful of JSON fields — the shape most public weather APIs
  * share.
  */
final class OpenMeteoClient(
    httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(JDuration.ofSeconds(5)).build()
) extends WeatherClient:

  def fetch(city: String): Either[WeatherError, WeatherReport] =
    for
      location <- geocode(city)
      report   <- currentConditions(location)
    yield report

  private def geocode(city: String): Either[WeatherError, Location] =
    val url =
      s"https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=${encode(city)}"
    for
      body   <- get(url)
      json   <- Json.parse(body).left.map(WeatherError.UnexpectedResponse.apply)
      result <- json
        .field("results")
        .flatMap(_.asArray)
        .flatMap(_.headOption)
        .toRight(WeatherError.CityNotFound(city))
      name   <- result
        .field("name")
        .flatMap(_.asString)
        .toRight(WeatherError.UnexpectedResponse("missing name"))
      lat    <- result
        .field("latitude")
        .flatMap(_.asDouble)
        .toRight(WeatherError.UnexpectedResponse("missing latitude"))
      lon    <- result
        .field("longitude")
        .flatMap(_.asDouble)
        .toRight(WeatherError.UnexpectedResponse("missing longitude"))
      country = result.field("country").flatMap(_.asString).getOrElse("")
    yield Location(name, country, lat, lon)

  private def currentConditions(location: Location): Either[WeatherError, WeatherReport] =
    val url =
      s"https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}" +
        "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code,is_day&timezone=auto"
    for
      body        <- get(url)
      json        <- Json.parse(body).left.map(WeatherError.UnexpectedResponse.apply)
      current     <- json.field("current").toRight(WeatherError.UnexpectedResponse("missing current conditions"))
      temperature <- current
        .field("temperature_2m")
        .flatMap(_.asDouble)
        .toRight(WeatherError.UnexpectedResponse("missing temperature"))
      humidity    <- current
        .field("relative_humidity_2m")
        .flatMap(_.asDouble)
        .toRight(WeatherError.UnexpectedResponse("missing humidity"))
      wind        <- current
        .field("wind_speed_10m")
        .flatMap(_.asDouble)
        .toRight(WeatherError.UnexpectedResponse("missing wind speed"))
      code        <- current
        .field("weather_code")
        .flatMap(_.asInt)
        .toRight(WeatherError.UnexpectedResponse("missing weather code"))
      isDay = current.field("is_day").flatMap(_.asInt).forall(_ != 0)
    yield WeatherReport(location.name, location.country, temperature, humidity, wind, isDay, code)

  private def get(url: String): Either[WeatherError, String] =
    try
      val request  = HttpRequest.newBuilder(URI.create(url)).timeout(JDuration.ofSeconds(8)).GET().build()
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      if response.statusCode() == 200 then Right(response.body())
      else Left(WeatherError.NetworkFailure(s"HTTP ${response.statusCode()}"))
    catch
      case e: java.io.IOException  => Left(WeatherError.NetworkFailure(describeException(e)))
      case e: InterruptedException => Left(WeatherError.NetworkFailure(describeException(e)))

  private def encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

  private def describeException(e: Throwable): String = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)

/** WMO weather-interpretation codes, as used by Open-Meteo's `weather_code` field. */
object WmoCode:
  private val descriptions: Map[Int, String] = Map(
    0  -> "Clear sky",
    1  -> "Mainly clear",
    2  -> "Partly cloudy",
    3  -> "Overcast",
    45 -> "Fog",
    48 -> "Depositing rime fog",
    51 -> "Light drizzle",
    53 -> "Moderate drizzle",
    55 -> "Dense drizzle",
    56 -> "Light freezing drizzle",
    57 -> "Dense freezing drizzle",
    61 -> "Slight rain",
    63 -> "Moderate rain",
    65 -> "Heavy rain",
    66 -> "Light freezing rain",
    67 -> "Heavy freezing rain",
    71 -> "Slight snow fall",
    73 -> "Moderate snow fall",
    75 -> "Heavy snow fall",
    77 -> "Snow grains",
    80 -> "Slight rain showers",
    81 -> "Moderate rain showers",
    82 -> "Violent rain showers",
    85 -> "Slight snow showers",
    86 -> "Heavy snow showers",
    95 -> "Thunderstorm",
    96 -> "Thunderstorm with slight hail",
    99 -> "Thunderstorm with heavy hail",
  )

  def describe(code: Int): String = descriptions.getOrElse(code, s"Unknown conditions (code $code)")
