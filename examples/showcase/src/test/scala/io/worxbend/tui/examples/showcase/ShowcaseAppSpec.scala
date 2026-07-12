package io.worxbend.tui.examples.showcase

import io.worxbend.tui.core.{KeyCode, KeyModifiers, Size}
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

final class ShowcaseAppSpec extends AnyFunSuite:

  private def startedApp(): (ShowcaseApp, Pilot) =
    val backend = HeadlessBackend(Size(70, 20))
    val app = ShowcaseApp()
    val pilot = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    (app, pilot)

  test("the shell renders top bar, sidebar, tabs, and status hints"):
    val (_, pilot) = startedApp()
    assert(pilot.screenText.contains("glyphora"))
    assert(pilot.screenText.contains("Menu"))
    assert(pilot.screenText.contains("Widgets │ Log │ About"))
    assert(pilot.screenText.contains("ctrl+t switch theme"))
    pilot.pressKey(KeyCode.Escape)
    assert(pilot.awaitTermination())

  test("theme switching, toasts, and the modal all work end to end"):
    val (app, pilot) = startedApp()
    pilot.pressKey(KeyCode.Char('t'), KeyModifiers.Ctrl).waitForIdle()
    assert(app.themeIndex.peek == 1)
    pilot.pressKey(KeyCode.Char('n'), KeyModifiers.Ctrl).waitForIdle()
    assert(pilot.screenText.contains("hello from glyphora"))
    pilot.pressKey(KeyCode.Char('o'), KeyModifiers.Ctrl).waitForIdle()
    assert(pilot.screenText.contains("About"))
    assert(pilot.screenText.contains("press Esc to close"))
    pilot.pressKey(KeyCode.Escape).waitForIdle()
    assert(!pilot.screenText.contains("press Esc to close"))
    pilot.pressKey(KeyCode.Escape)
    assert(pilot.awaitTermination())
