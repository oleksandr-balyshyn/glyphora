package io.worxbend.tui.examples.weather

import io.worxbend.tui.dsl.*

import java.util.Locale
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
  private val HistoryLimit: Int               = 5

  // Render thread only: written in `search` (a key handler) and read in the completion it posts, the same
  // generation-counter pattern `examples/loadtest` uses for run ids.
  private var requestId: Int = 0

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
          // `String.format(Locale.ROOT, …)`, not the `f` interpolator: see procmon's `decimal` for the reasoning.
          // The interpolator formats through the default FORMAT locale, so a German machine would draw "22,5°C"
          // for the same response that reads "22.5°C" in CI — a difference the API never made.
          text(
            String.format(
              Locale.ROOT,
              "%.1f°C  ·  humidity %.0f%%  ·  wind %.0f km/h",
              report.temperatureC,
              report.humidityPercent,
              report.windKph,
            )
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
      history.update(existing => (city +: existing.filterNot(_.equalsIgnoreCase(city))).take(HistoryLimit))
      // `Async.runCatching` does the two things this needs and `Future(...).foreach` did neither of: it runs the
      // blocking HTTP call on a worker thread and *resumes on the render thread*, so the `status.set` below is an
      // ordinary signal write with no `RenderThread.runOnRenderThread` hop; and it delivers a thrown exception as a
      // `Left` instead of dropping it, so a `client.fetch` that blows up shows an error rather than leaving the UI
      // spinning on `Status.Loading` for ever.
      requestId += 1
      val thisRequest = requestId
      Async.runCatching(client.fetch(city)) { result =>
        // `requestId` is checked because two searches can overlap: a slow first fetch finishes *after* a fast second
        // one and would otherwise paste its own stale city over the newer result. Comparing generations drops the
        // stale completion, the same guard `examples/loadtest`'s `finish` applies to late run callbacks.
        if thisRequest == requestId then
          result match
            case Right(Right(report)) => status.set(Status.Loaded(report))
            case Right(Left(failure)) => status.set(Status.Failed(city, WeatherError.describe(failure)))
            case Left(thrown)         =>
              status.set(Status.Failed(city, Option(thrown.getMessage).getOrElse(thrown.getClass.getSimpleName)))
      }

object Main extends WeatherApp()
