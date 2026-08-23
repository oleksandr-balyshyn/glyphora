package io.worxbend.tui.examples.weather

import io.worxbend.tui.dsl.*

import scala.concurrent.duration.DurationInt

private enum Status:
  case Idle
  case Loading(city: String)
  case Loaded(report: WeatherReport)
  case Failed(city: String, message: String)

/** weather: fetches live conditions from the free Open-Meteo API — a real HTTP round trip kicked off from a key
  * handler, run on a background thread by `Async.runCatching`, and resumed on the render thread once the response
  * lands. Shows how to bridge blocking I/O into the render-thread-only `Signal` model without hand-rolling a hop.
  *
  * Keys: type a city + `Enter` to search · `Esc` to quit.
  */
class WeatherApp(client: WeatherClient = OpenMeteoClient()) extends TuiApp:

  override def config: RunnerConfig = RunnerConfig(tickRate = Some(120.millis))

  private val cityInput                       = TextInputState()
  private val status: Signal[Status]          = Signal(Status.Idle)
  private val history: Signal[Vector[String]] = Signal(Vector.empty)

  def view(using ReactiveScope, Theme): Element =
    column(
      panel("City")(
        input(cityInput, placeholder = "e.g. Kyiv, Lisbon, Tokyo...").onKeyEvent {
          case KeyEvent(KeyCode.Enter, _) =>
            search()
            true
          case _                          => false
        }
      ).length(3),
      panel("Current Conditions")(currentConditionsView).fill,
      recentSearchesView,
      text("Enter: search · Esc: quit").dim,
    ).onKeyEvent {
      case KeyEvent(KeyCode.Escape, _) =>
        quit()
        true
      case _                           => false
    }

  private def currentConditionsView(using ReactiveScope, Theme): Element =
    status.get match
      case Status.Idle                  => text("Type a city name and press Enter.").dim
      case Status.Loading(city)         =>
        spinner(s"fetching weather for $city...")
      case Status.Failed(city, message) => text(s"Couldn't fetch $city: $message").fg(Color.Red)
      case Status.Loaded(report)        =>
        column(
          text(s"${report.city}${if report.country.isEmpty then "" else s", ${report.country}"}").bold,
          text(report.condition + (if report.isDay then "" else " (night)")),
          text(
            f"${report.temperatureC}%.1f°C  ·  humidity ${report.humidityPercent}%.0f%%  " +
              f"·  wind ${report.windKph}%.0f km/h"
          ),
        )

  private def recentSearchesView(using ReactiveScope): Element =
    val recent = history.get
    if recent.isEmpty then spacer(1) else text("recent: " + recent.mkString(" · ")).dim

  private def search(): Unit =
    val city = cityInput.value.trim
    if city.nonEmpty then
      cityInput.clear()
      status.set(Status.Loading(city))
      history.update(existing => (city +: existing.filterNot(_.equalsIgnoreCase(city))).take(5))
      // `Async.runCatching` does the two things this needs and `Future(...).foreach` did neither of: it runs the
      // blocking HTTP call on a worker thread and *resumes on the render thread*, so the `status.set` below is an
      // ordinary signal write with no `RenderThread.runOnRenderThread` hop; and it delivers a thrown exception as a
      // `Left` instead of dropping it, so a `client.fetch` that blows up shows an error rather than leaving the UI
      // spinning on `Status.Loading` for ever.
      Async.runCatching(client.fetch(city)) {
        case Right(Right(report)) => status.set(Status.Loaded(report))
        case Right(Left(failure)) => status.set(Status.Failed(city, WeatherError.describe(failure)))
        case Left(thrown)         =>
          status.set(Status.Failed(city, Option(thrown.getMessage).getOrElse(thrown.getClass.getSimpleName)))
      }

object Main extends WeatherApp()
