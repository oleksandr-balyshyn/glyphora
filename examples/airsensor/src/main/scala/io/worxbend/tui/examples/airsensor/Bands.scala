package io.worxbend.tui.examples.airsensor

import io.worxbend.tui.dsl.*

/** Where one reading sits against its metric's thresholds.
  *
  * The band is the whole point of an air-quality display: `812 ppm` means nothing to most people, `Moderate` means
  * something to everyone. It is deliberately a small closed enum rather than a colour, because the screen has to say it
  * in two independent ways — colour for the glance, the word for everyone who cannot rely on colour.
  */
enum Band:
  case Good, Moderate, Elevated, Unhealthy, VeryUnhealthy

  /** The word rendered beside the value. */
  def label: String = this match
    case Good          => "Good"
    case Moderate      => "Moderate"
    case Elevated      => "Elevated"
    case Unhealthy     => "Unhealthy"
    case VeryUnhealthy => "Very unhealthy"

  /** The severity the toolkit already understands, so badges and toasts need no parallel vocabulary. */
  def level: NoticeLevel = this match
    case Good                      => NoticeLevel.Success
    case Moderate | Elevated       => NoticeLevel.Warning
    case Unhealthy | VeryUnhealthy => NoticeLevel.Error

  /** The colour half of the pairing, taken from the theme rather than from hard-coded ANSI colours so a re-theme moves
    * the whole app at once.
    */
  def style(using theme: Theme): Style = this match
    case Good                => theme.success
    case Moderate | Elevated => theme.warning
    case Unhealthy           => theme.error
    case VeryUnhealthy       => theme.error.bold

object Band:

  /** The shape most pollutant thresholds take: four upper-inclusive cut-offs, anything above the last one is the worst
    * band. Written once so the five metrics differ only in their numbers.
    */
  def cascade(good: Double, moderate: Double, elevated: Double, unhealthy: Double)(value: Double): Band =
    if value <= good then Good
    else if value <= moderate then Moderate
    else if value <= elevated then Elevated
    else if value <= unhealthy then Unhealthy
    else VeryUnhealthy

  /** The worst of several bands — the one the summary line reports. */
  def worst(bands: Seq[Band]): Band =
    bands.maxByOption(_.ordinal).getOrElse(Good)

/** Which way a metric has moved over the last few samples. */
enum Trend:
  case Rising, Falling, Steady

  /** One column wide in every case, so a column of cards stays aligned. */
  def arrow: String = this match
    case Rising  => "▲"
    case Falling => "▼"
    case Steady  => "·"

  def label: String = this match
    case Rising  => "rising"
    case Falling => "falling"
    case Steady  => "steady"

object Trend:

  /** How many samples back "a moment ago" means. Three at a five-second cadence is fifteen seconds — long enough that
    * the arrow describes the room rather than the sensor's own jitter.
    */
  private val Window = 3

  /** Compares the newest sample against the one `Window` back.
    *
    * `deadband` is what stops the arrow flickering: a metric wobbling by a fraction of a unit is steady, and without a
    * dead band it would flip between up and down on nearly every refresh.
    */
  def between(samples: Seq[Double], deadband: Double): Trend =
    if samples.sizeIs < 2 then Steady
    else
      val latest  = samples.last
      val earlier = samples(math.max(0, samples.size - 1 - Window))
      if latest - earlier > deadband then Rising
      else if earlier - latest > deadband then Falling
      else Steady

/** One displayable quantity: how to pull it out of a [[Reading]], how to print it, how far its gauge runs, and how to
  * band it.
  *
  * Bundling those five together is what lets the view render every card with the same six lines of code. Adding a
  * metric is then one entry in [[Metric.All]] and nothing else.
  */
final case class Metric(
    label: String,
    unit: String,
    decimals: Int,
    gaugeMax: Double,
    read: Reading => Double,
    classify: Double => Band,
):

  def valueText(reading: Reading): String = format(read(reading))

  /** The gauge's own ceiling, printed under the bar so the bar has a scale rather than being decorative. */
  def scaleText: String = s"${format(gaugeMax)} $unit".trim

  def ratio(reading: Reading): Double = read(reading) / gaugeMax

  def bandOf(reading: Reading): Band = classify(read(reading))

  private def format(value: Double): String = s"%.${decimals}f".format(value)

object Metric:

  /** The bands below are the ones the AirGradient reference client uses; they in turn follow the US EPA and the SGP41
    * index scales. They are upper-inclusive: 800 ppm of CO2 is still `Good`.
    */
  val Aqi: Metric =
    Metric("AQI", "", 0, 500.0, _.aqi, Band.cascade(50.0, 100.0, 150.0, 200.0))

  val Co2: Metric =
    Metric("CO2", "ppm", 0, 2000.0, _.co2Ppm, Band.cascade(800.0, 1000.0, 1500.0, 2000.0))

  val Pm25: Metric =
    Metric("PM2.5", "ug/m3", 1, 125.4, _.pm25, Band.cascade(9.0, 35.4, 55.4, 125.4))

  val Tvoc: Metric =
    Metric("TVOC", "index", 0, 400.0, _.tvocIndex, Band.cascade(100.0, 200.0, 300.0, 400.0))

  /** Temperature is the odd one out: comfort is a *range*, not a ceiling, so it gets a banded classifier instead of a
    * cascade and tops out at `Elevated` — a cold room is uncomfortable, not unhealthy.
    *
    * Its gauge runs to 40 C rather than the reference client's 100 C, which would leave every habitable reading in the
    * first fifth of the bar.
    */
  val Temperature: Metric =
    Metric("Temp", "C", 1, 40.0, _.temperatureC, comfort)

  /** The four cards under the AQI hero panel. */
  val Cards: Seq[Metric] = Seq(Co2, Pm25, Tvoc, Temperature)

  /** Everything with a history worth plotting, hero included. */
  val All: Seq[Metric] = Aqi +: Cards

  private def comfort(celsius: Double): Band =
    if celsius >= 18.0 && celsius <= 26.0 then Band.Good
    else if celsius >= 16.0 && celsius <= 30.0 then Band.Moderate
    else Band.Elevated

/** The US EPA's AQI, computed from PM2.5 alone.
  *
  * Real air-quality indices combine several pollutants; this example follows the AirGradient reference client and
  * derives AQI from PM2.5, which is the dominant term indoors.
  */
object AirQuality:

  /** `(concentration low, concentration high, index low, index high)`, in micrograms per cubic metre. */
  private val Breakpoints: Vector[(Double, Double, Double, Double)] = Vector(
    (0.0, 9.0, 0.0, 50.0),
    (9.1, 35.4, 51.0, 100.0),
    (35.5, 55.4, 101.0, 150.0),
    (55.5, 125.4, 151.0, 200.0),
    (125.5, 225.4, 201.0, 300.0),
    (225.5, 325.4, 301.0, 500.0),
  )

  def aqiFromPm25(pm25: Double): Double =
    // the EPA truncates the reading to one decimal before interpolating, so two sensors reporting 9.04 and 9.09
    // report the same index rather than differing in the last digit of a number nobody reads that precisely
    val truncated = math.floor(math.max(0.0, pm25) * 10.0) / 10.0
    Breakpoints.find((_, concentrationHigh, _, _) => truncated <= concentrationHigh) match
      case Some((concentrationLow, concentrationHigh, indexLow, indexHigh)) =>
        ((indexHigh - indexLow) / (concentrationHigh - concentrationLow)) * (truncated - concentrationLow) + indexLow
      case None                                                             => 500.0
