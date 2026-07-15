package io.worxbend.tui.examples.weather

import io.worxbend.tui.dsl.*
import io.worxbend.tui.runtime.{RenderThread, RunnerConfig}
import io.worxbend.tui.widgets.TextInputState

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

private enum Status:
  case Idle
  case Loading(city: String)
  case Loaded(report: WeatherReport)
  case Failed(city: String, message: String)

/** weather: fetches live conditions from the free Open-Meteo API — a real HTTP round trip kicked off from a key
  * handler, run on a background thread, and reflected back into the UI via `RenderThread.runOnRenderThread` once the
  * response lands. Shows how to bridge async I/O into the render-thread-only `Signal` model.
  *
  * Keys: type a city + `Enter` to search · `Esc` to quit.
  */
final class WeatherApp(client: WeatherClient = OpenMeteoClient()) extends TuiApp:

  override def config: RunnerConfig = RunnerConfig(tickRate = Some(120.millis))

  private val cityInput                       = TextInputState()
  private val status: Signal[Status]          = Signal(Status.Idle)
  private val history: Signal[Vector[String]] = Signal(Vector.empty)
  private val spinnerFrame: Signal[Int]       = Signal(0)

  override def onTick(): Unit = spinnerFrame.update(_ + 1)

  def view(using ReactiveScope): Element =
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
    ).rounded.onKeyEvent {
      case KeyEvent(KeyCode.Escape, _) =>
        quit()
        true
      case _                           => false
    }

  private def currentConditionsView(using ReactiveScope): Element =
    status.get match
      case Status.Idle                  => text("Type a city name and press Enter.").dim
      case Status.Loading(city)         =>
        row(spinner(spinnerFrame.get), text(s" fetching weather for $city..."))
      case Status.Failed(city, message) => text(s"Couldn't fetch $city: $message").color(Color.Red)
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
      Future(client.fetch(city)).foreach { result =>
        RenderThread.runOnRenderThread {
          status.set(result.fold(error => Status.Failed(city, WeatherError.describe(error)), Status.Loaded.apply))
        }
      }

object Main:
  def main(args: Array[String]): Unit =
    WeatherApp().run().left.foreach(error => println(s"failed to run: $error"))
