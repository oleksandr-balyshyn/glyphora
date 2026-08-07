package io.worxbend.tui.examples.loadtest

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}

/** One unit of work the load generator fires, over and over, from many threads at once.
  *
  * `fire` blocks for as long as the request takes and answers with the latency it observed or a short reason it failed
  * — a `String` rather than a `Throwable` because the only thing the UI does with a failure is tally it by text.
  * Implementations must be safe to call concurrently: [[LoadRunner]] shares one `Target` across every worker.
  */
trait Target:

  def fire(): Either[String, FiniteDuration]

  /** What the title bar calls this target. */
  def describe: String

/** The default target: no socket, no server, no network — plausible latencies out of a seeded generator.
  *
  * Determinism matters more than realism here, and it needs care under concurrency. The *order* results come back in is
  * a race, so anything derived from a shared mutable RNG would differ from run to run and no test could assert on it.
  * Instead every request takes a ticket from an atomic counter and derives its own outcome from `seed` and that ticket
  * alone, so the multiset of results for N requests is fixed however the workers interleave.
  *
  * `pace` is the one thing actually slept. A real request costs wall-clock time; without some, the whole run would
  * finish inside a single render tick and the live charts would have nothing to draw. The *reported* latency is
  * synthetic and much larger — sleeping it would make the example tedious to watch and slow to test.
  */
final class FakeTarget(
    seed: Long = 0x10adL,
    failureRate: Double = 0.04,
    pace: FiniteDuration = 1.milli,
) extends Target:

  private val issued = AtomicLong(0L)

  def describe: String = f"fake://in-memory (${failureRate * 100}%.0f%% fail)"

  def fire(): Either[String, FiniteDuration] =
    val ticket = issued.getAndIncrement()
    Thread.sleep(pace.toMillis)
    if FakeTarget.unitInterval(seed ^ FakeTarget.FailureStream, ticket) < failureRate then
      Left(FakeTarget.Failures((ticket % FakeTarget.Failures.size).toInt))
    else Right(latencyOf(ticket))

  /** A cubed uniform: a long right tail, so p99 sits well above p50 the way a real service does. */
  private def latencyOf(ticket: Long): FiniteDuration =
    val uniform = FakeTarget.unitInterval(seed ^ FakeTarget.LatencyStream, ticket)
    (FakeTarget.BaseMicros + (FakeTarget.SpreadMicros * uniform * uniform * uniform).toLong).micros

object FakeTarget:

  private val BaseMicros   = 900L
  private val SpreadMicros = 42000.0

  // two independent streams off the same seed, so changing the failure rate does not reshuffle the latencies
  private val LatencyStream = 0x51ed270bL
  private val FailureStream = 0x2545f491L

  private val Failures: Vector[String] =
    Vector("connection reset by peer", "operation timed out", "HTTP 503")

  /** splitmix64's finalizer: a cheap, well-mixed hash from a counter to a uniform value in `[0, 1)`.
    *
    * The point is that it is a pure function of the ticket — two workers racing for tickets 7 and 8 get the same two
    * outcomes whichever of them wins.
    */
  private def unitInterval(stream: Long, ticket: Long): Double =
    val seeded  = stream + ticket * 0x9e3779b97f4a7c15L
    val mixed   = (seeded ^ (seeded >>> 30)) * 0xbf58476d1ce4e5b9L
    val again   = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL
    val final64 = again ^ (again >>> 31)
    (final64 >>> 11).toDouble / (1L << 53).toDouble

/** A real HTTP GET against `url`, timing the round trip itself.
  *
  * Deliberately not the default. An example's suite has to pass with no network, so nothing in `src/test` ever
  * constructs one of these — see [[FakeTarget]], which is what the spec drives.
  */
final class HttpTarget(url: String, timeout: FiniteDuration = 5.seconds) extends Target:

  // Both of these are immutable and safe to share across the worker pool. Building a client per request would also
  // measure connection setup and TLS negotiation, which is not what a load test is asking about.
  private val client  =
    HttpClient.newBuilder().connectTimeout(java.time.Duration.ofMillis(timeout.toMillis)).build()
  private val request =
    HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofMillis(timeout.toMillis)).GET().build()

  def describe: String = url

  def fire(): Either[String, FiniteDuration] =
    val started = System.nanoTime()
    try
      val response = client.send(request, HttpResponse.BodyHandlers.discarding())
      val elapsed  = (System.nanoTime() - started).nanos
      if response.statusCode() >= 400 then Left(s"HTTP ${response.statusCode()}") else Right(elapsed)
    // `send` declares exactly IOException and InterruptedException. The interrupt is left to propagate: it is how
    // `LoadRunner` tears a stuck worker down, and swallowing it here would defeat that.
    catch case error: java.io.IOException => Left(Option(error.getMessage).getOrElse(error.toString))
