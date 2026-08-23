package io.worxbend.tui.dslimport

import io.worxbend.tui.dsl.*
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

/** The `dsl` package's one-import promise, made into something the compiler checks.
  *
  * `dsl.scala` states the rule it keeps: if a name appears in the *signature* of anything the package exports, the
  * package re-exports it too, so an application writes `import io.worxbend.tui.dsl.*` and nothing else. Every other
  * suite in this module is declared `package io.worxbend.tui.dsl`, which means it sees the whole package whether it is
  * exported or not — none of them can tell the rule being kept from the rule being broken.
  *
  * This one lives in a different package on purpose. It overrides `TuiApp`'s two lifecycle seams — `colorDepth` and
  * `createBackend` — and runs the app through `run()`, which names every type in their signatures out loud:
  * `ColorDepth`, `BackendError`, `Backend`, `RunnerError`. It also names the two payload types those signatures reach
  * through — `AsyncErrorHandler`, which `Async.run` takes and an app installs as a `given`, and `QueuedTaskFailures`,
  * which is what a `RunnerError.QueuedTask` actually carries. Delete any one of those from `dsl.scala`'s export blocks
  * and this file stops compiling, which is the failure the rule wants. It is a compile-time assertion first and a
  * runtime one second.
  *
  * The single non-`dsl` import is deliberate and is the exception `dsl.scala` documents: *constructing* a backend is a
  * `tui-terminal` job, so `HeadlessBackend` is not re-exported and a test that wires one is expected to say so. `Pilot`
  * is a test tool, not part of the promise.
  */
final class OneImportSpec extends AnyFunSuite:

  test("an app can be written, wired to a backend and run with `io.worxbend.tui.dsl.*` as its only glyphora import"):
    val backend = HeadlessBackend(Size(24, 4))

    val app = new TuiApp:
      private val hits = Signal(0)

      // the two overridable lifecycle seams, named in full: `ColorDepth`, `BackendError` and `Backend` all have to be
      // reachable from the one import for these two lines to compile
      override protected def colorDepth: ColorDepth                         = ColorDepth.Ansi16
      override protected def createBackend(): Either[BackendError, Backend] = Right(backend)

      override def bindings: KeyBindings = KeyBindings(
        binding("a", "count a hit") { hits.update(_ + 1) },
        binding("q", "quit")(quit()),
      )

      def view(using ReactiveScope, Theme): Element =
        column(
          text(s"hits: ${hits.get}"),
          marquee("ticker").speed(4.0).gap(2),
          sparkline(Seq(1L, 4L, 2L)).max(10L),
          table(Seq(Seq("api", "ready")), Constraint.Length(6), Constraint.Fill(1)).header("svc", "state"),
        )

    // `run()` rather than `runWith(backend)`, so the overridden `createBackend` is the thing under test; the result
    // type is written out because that is the fourth name the rule owes us.
    var outcome: Option[Either[RunnerError, Unit]] = None
    val pilot                                      = Pilot.start(backend) {
      val result: Either[RunnerError, Unit] = app.run()
      outcome = Some(result)
      result
    }

    pilot.waitForIdle()
    pilot.press("a").waitForIdle()
    assert(pilot.screenLines.head.startsWith("hits: 1"))

    pilot.press("q")
    assert(pilot.awaitTermination())
    assert(outcome.contains(Right(())))

    // The fifth and sixth names the rule owes us, both reached *through* an exported signature rather than written in
    // one. Installing a background-failure policy means writing the type of the `given`, and reading the error `run()`
    // hands back means naming what a `RunnerError.QueuedTask` carries.
    var reported: Option[Throwable] = None
    val boom                        = RuntimeException("boom")

    given handler: AsyncErrorHandler = error => reported = Some(error)
    handler.handle(boom)
    assert(reported.contains(boom))

    val queued: RunnerError = RunnerError.QueuedTask(QueuedTaskFailures(boom, 3))
    queued match
      case RunnerError.QueuedTask(failures: QueuedTaskFailures) => assert(failures.count == 3)
      case other                                                => fail(s"expected a QueuedTask error, got $other")
