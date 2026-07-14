package io.worxbend.tui.dsl

import io.worxbend.tui.testsupport.{BufferAssertions, GoldenFrames}

import org.scalatest.funsuite.AnyFunSuite

/** Full-frame snapshot of a deterministic chrome composition — catches visual regressions that substring assertions
  * miss. Regenerate with `GLYPHORA_GOLDEN_UPDATE=dsl/src/test/resources ./mill dsl.test`.
  */
final class GoldenFrameSpec extends AnyFunSuite:

  test("the app-shell composition renders exactly as recorded"):
    val shell = scaffold(
      topBar = Some(topBar("golden", tabs = Seq("One", "Two"), right = "v1")),
      sidebar = Some(sidebar(panel("Menu")(text("alpha"), text("beta")), width = 14)),
      statusBar = Some(statusBar(Seq("q" -> "quit", "tab" -> "next"))),
    ) {
      column(
        rule("content"),
        text("static body line").bold,
        gauge(0.4),
        sparkline(Seq(1L, 4L, 2L, 8L, 5L)),
      )
    }
    GoldenFrames.assertMatches("app-shell", BufferAssertions.rendered(shell.widget, 52, 10))
