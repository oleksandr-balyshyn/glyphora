package io.worxbend.tui.examples.loadtest

import io.worxbend.tui.core.Size
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters.*

/** Everything here runs headless: no TTY, no network, no device. The only target the suite ever constructs is
  * [[FakeTarget]], whose outcome for request `n` is a pure function of the seed and `n`, so the counts below are exact
  * however the worker threads happen to interleave.
  */
final class LoadTestAppSpec extends AnyFunSuite:

  private def startedApp(target: Target, plan: Plan): (LoadTestApp, Pilot) =
    val backend = HeadlessBackend(Size(88, 30))
    val app     = LoadTestApp(target, plan)
    val pilot   = Pilot.start(backend) { app.runWith(backend) }
    pilot.waitForIdle()
    (app, pilot)

  /** Polls rather than sleeping a fixed time.
    *
    * `waitForIdle` proves the posted key events were consumed; it says nothing about a background run finishing, whose
    * results only reach the UI on a later render tick. Under parallel test load that tick can be starved for a while,
    * so every assertion about the run waits on a condition with a generous deadline.
    */
  private def waitUntil(timeout: FiniteDuration = 20.seconds)(condition: => Boolean): Boolean =
    val deadline = System.nanoTime() + timeout.toNanos
    while !condition && System.nanoTime() < deadline do Thread.sleep(20)
    condition

  private def liveThreadsNamed(prefix: String): Seq[String] =
    Thread.getAllStackTraces.keySet.asScala.toSeq
      .filter(thread => thread.isAlive && thread.getName.startsWith(prefix))
      .map(_.getName)

  test("a completed run accounts for every request and raises the summary screen"):
    val (app, pilot) = startedApp(FakeTarget(failureRate = 0.0), Plan(requests = 60, concurrency = 6))
    pilot.press("s")

    assert(waitUntil()(app.phase.peek == Phase.Finished(RunOutcome.Completed)))
    val finished = app.stats.peek
    assert(finished.sent == 60)
    assert(finished.ok == 60)
    assert(finished.failed == 0)
    assert(finished.latencies.size == 60)
    assert(waitUntil()(pilot.screenText.contains("Run summary")))
    assert(pilot.screenText.contains("success"))
    assert(pilot.screenText.contains("no errors"))

    pilot.press("q")
    assert(pilot.awaitTermination(5.seconds))

  test("failures are tallied by reason and do not abort the run"):
    val (app, pilot) = startedApp(FakeTarget(failureRate = 1.0, pace = 0.millis), Plan(requests = 40, concurrency = 4))
    pilot.press("s")

    assert(waitUntil()(app.phase.peek == Phase.Finished(RunOutcome.Completed)))
    val finished = app.stats.peek
    assert(finished.sent == 40)
    assert(finished.ok == 0)
    assert(finished.failed == 40)
    assert(finished.errors.values.sum == 40)
    assert(waitUntil()(pilot.screenText.contains("Run summary")))
    assert(pilot.screenText.contains("operation timed out"))

    pilot.press("q")
    assert(pilot.awaitTermination(5.seconds))

  test("stopping mid-flight ends the run short and empties the worker pool"):
    val plan         = Plan(requests = 5000, concurrency = 4)
    val (app, pilot) = startedApp(FakeTarget(failureRate = 0.0, pace = 2.millis), plan)
    pilot.press("s")

    assert(waitUntil()(app.stats.peek.sent > 0))
    assert(waitUntil()(pilot.screenText.contains("Running")))
    pilot.press("x")

    assert(waitUntil()(app.phase.peek == Phase.Finished(RunOutcome.Stopped)))
    assert(app.stats.peek.sent > 0)
    assert(app.stats.peek.sent < plan.requests)
    assert(waitUntil()(app.workersAlive == 0))
    assert(waitUntil()(pilot.screenText.contains("Stopped")))

    pilot.press("q")
    assert(pilot.awaitTermination(5.seconds))

  test("reset clears the counters and dismisses the summary"):
    val (app, pilot) = startedApp(FakeTarget(failureRate = 0.0), Plan(requests = 30, concurrency = 3))
    pilot.press("s")
    assert(waitUntil()(app.phase.peek == Phase.Finished(RunOutcome.Completed)))
    assert(waitUntil()(pilot.screenText.contains("Run summary")))

    pilot.press("r")
    assert(waitUntil()(app.phase.peek == Phase.Idle))
    assert(app.stats.peek == RunStats.empty)
    assert(waitUntil()(!pilot.screenText.contains("Run summary")))
    assert(pilot.screenText.contains("press s to start"))

    pilot.press("q")
    assert(pilot.awaitTermination(5.seconds))

  test("quitting mid-run leaves no worker thread behind"):
    val (app, pilot) =
      startedApp(FakeTarget(failureRate = 0.0, pace = 2.millis), Plan(requests = 5000, concurrency = 8))
    pilot.press("s")
    assert(waitUntil()(app.stats.peek.sent > 0))
    assert(liveThreadsNamed(app.workerThreadPrefix).nonEmpty, "the pool should be busy before we quit")

    pilot.press("q")
    assert(pilot.awaitTermination(5.seconds))

    // The runner is gone, but the pool it started is not the runner's to garbage-collect: `q` has to stop it. The
    // thread-name prefix is unique per LoadRunner, so this cannot be satisfied by some other test's pool dying.
    assert(waitUntil()(app.workersAlive == 0))
    assert(waitUntil(5.seconds)(liveThreadsNamed(app.workerThreadPrefix).isEmpty))

  test("the plan is adjustable while idle and frozen while running"):
    // The pace is deliberately slow: this is the one test that presses keys *while the run is in flight*, so the run
    // has to still be in flight when they arrive. At 2ms the 80 requests finished in ~30ms and the `[` occasionally
    // landed after the phase had already left Running, halving the requests and failing the freeze assertion.
    val (app, pilot) = startedApp(FakeTarget(failureRate = 0.0, pace = 50.millis), Plan(requests = 40, concurrency = 4))

    pilot.press("+", "+", "]").waitForIdle()
    assert(app.plan.peek == Plan(requests = 80, concurrency = 6))
    assert(pilot.screenText.contains("n=80 c=6"))

    pilot.press("s")
    assert(waitUntil()(app.phase.peek == Phase.Running))
    pilot.press("+", "[").waitForIdle()
    assert(app.plan.peek == Plan(requests = 80, concurrency = 6))

    assert(waitUntil()(app.phase.peek == Phase.Finished(RunOutcome.Completed)))
    assert(app.stats.peek.sent == 80)

    pilot.press("q")
    assert(pilot.awaitTermination(5.seconds))
