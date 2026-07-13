package io.worxbend.tui.examples.formdemo

import io.worxbend.tui.core.{KeyCode, KeyModifiers, Size}
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

import org.scalatest.funsuite.AnyFunSuite

/** End-to-end form validation (PLAN.md §11, step 11): `Field.mapValidated` runs on submit, an invalid input surfaces
  * its validation error in the rendered UI, and a corrected form assembles the case class.
  */
final class FormDemoAppSpec extends AnyFunSuite:

  private def startedApp(): (FormDemoApp, Pilot) =
    val backend = HeadlessBackend(Size(50, 12))
    val app     = FormDemoApp()
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    (app, pilot)

  private def submit(pilot: Pilot): Unit =
    pilot.pressKey(KeyCode.Char('s'), KeyModifiers.Ctrl).waitForIdle()

  test("the derived form renders a control per case-class field"):
    val (_, pilot) = startedApp()
    assert(pilot.screenText.contains("username:"))
    assert(pilot.screenText.contains("age:"))
    assert(pilot.screenText.contains("[ ] subscribe"))
    pilot.pressKey(KeyCode.Escape)
    assert(pilot.awaitTermination())

  test("an invalid field surfaces its validation error in the UI"):
    val (app, pilot) = startedApp()
    pilot.typeText("ada").pressKey(KeyCode.Tab).typeText("12").waitForIdle()
    submit(pilot)
    assert(pilot.screenText.contains("! must be 18 or older"))
    assert(app.formState.result.peek.isEmpty)
    pilot.pressKey(KeyCode.Escape)
    assert(pilot.awaitTermination())

  test("an empty required field surfaces its error too"):
    val (_, pilot) = startedApp()
    submit(pilot)
    assert(pilot.screenText.contains("! required"))
    pilot.pressKey(KeyCode.Escape)
    assert(pilot.awaitTermination())

  test("a valid form assembles the case class and clears old errors"):
    val (app, pilot) = startedApp()
    submit(pilot) // provoke errors first
    assert(pilot.screenText.contains("! required"))
    pilot.typeText("ada").pressKey(KeyCode.Tab).typeText("36").pressKey(KeyCode.Tab).pressKey(KeyCode.Char(' '))
    pilot.waitForIdle()
    submit(pilot)
    assert(app.formState.result.peek.contains(Signup("ada", 36, true)))
    assert(pilot.screenText.contains("""submitted: Signup(ada,36,true)"""))
    assert(!pilot.screenText.contains("! required"))
    pilot.pressKey(KeyCode.Escape)
    assert(pilot.awaitTermination())
