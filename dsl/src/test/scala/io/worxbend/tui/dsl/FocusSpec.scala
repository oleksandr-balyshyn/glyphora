package io.worxbend.tui.dsl

import io.worxbend.tui.core.Size
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot
import io.worxbend.tui.widgets.TextInputState

import org.scalatest.funsuite.AnyFunSuite

/** Focus and event-routing acceptance tests (PLAN.md §11 step 7): tab-order traversal, focused-first key
  * dispatch, bubbling with stop-propagation, and click-to-focus.
  */
final class FocusSpec extends AnyFunSuite:

  /** Two inputs and a checkbox; whichever is focused receives typed characters. */
  private final class FormApp extends TuiApp:
    val first = TextInputState()
    val second = TextInputState()
    val agreed = Signal(false)
    def view(using ReactiveScope): Element =
      column(
        input(first, placeholder = "first"),
        input(second, placeholder = "second"),
        checkbox("agree", agreed),
      ).onKeyEvent {
        case KeyEvent(KeyCode.Char('q'), m) if m.has(KeyModifiers.Ctrl) =>
          quit()
          true
        case _ => false
      }

  private def startedApp(): (FormApp, Pilot) =
    val backend = HeadlessBackend(Size(30, 5))
    val app = FormApp()
    val pilot = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    (app, pilot)

  private def quitApp(pilot: Pilot): Unit =
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())

  test("typed characters go to the first focusable by default"):
    val (app, pilot) = startedApp()
    pilot.typeText("hi").waitForIdle()
    assert(app.first.value == "hi")
    assert(app.second.value == "")
    quitApp(pilot)

  test("Tab moves focus to the next element in depth-first order"):
    val (app, pilot) = startedApp()
    pilot.pressKey(KeyCode.Tab).typeText("yo").waitForIdle()
    assert(app.first.value == "")
    assert(app.second.value == "yo")
    quitApp(pilot)

  test("Shift+Tab moves focus backwards and wraps around"):
    val (app, pilot) = startedApp()
    pilot.pressKey(KeyCode.Tab, KeyModifiers.Shift).waitForIdle() // 0 -> wraps to last (checkbox)
    pilot.pressKey(KeyCode.Char(' ')).waitForIdle()
    assert(app.agreed.peek)
    quitApp(pilot)

  test("tab cycles past the end back to the first element"):
    val (app, pilot) = startedApp()
    pilot.pressKey(KeyCode.Tab).pressKey(KeyCode.Tab).pressKey(KeyCode.Tab).typeText("x").waitForIdle()
    assert(app.first.value == "x")
    quitApp(pilot)

  test("space toggles the focused checkbox through its signal"):
    val (app, pilot) = startedApp()
    pilot.pressKey(KeyCode.Tab).pressKey(KeyCode.Tab).pressKey(KeyCode.Char(' ')).waitForIdle()
    assert(app.agreed.peek)
    pilot.pressKey(KeyCode.Char(' ')).waitForIdle()
    assert(!app.agreed.peek)
    quitApp(pilot)

  test("the focused input shows its cursor on screen"):
    val (_, pilot) = startedApp()
    pilot.typeText("ab").waitForIdle()
    assert(pilot.screenLines.head.startsWith("ab"))
    quitApp(pilot)

  test("clicking a focusable element focuses it"):
    val (app, pilot) = startedApp()
    pilot.click(2, 1).waitForIdle() // second input renders on row 1
    pilot.typeText("z").waitForIdle()
    assert(app.second.value == "z")
    assert(app.first.value == "")
    quitApp(pilot)

  test("an event unconsumed by the focused element bubbles to ancestors"):
    val (app, pilot) = startedApp()
    // Ctrl+Q is not editing input: the focused element declines it, the root handler quits
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())
    assert(app.first.value == "")

  test("a consumed event stops at the focused element and never reaches ancestors"):
    val backend = HeadlessBackend(Size(30, 5))
    var rootSawChar = false
    val field = TextInputState()
    val app = new TuiApp:
      def view(using ReactiveScope): Element =
        column(input(field)).onKeyEvent {
          case KeyEvent(KeyCode.Char('x'), _) =>
            rootSawChar = true
            true
          case KeyEvent(KeyCode.Char('q'), _) =>
            quit()
            true
          case _ => false
        }
    val pilot = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    pilot.typeText("x").waitForIdle() // consumed by the focused input's editing handler
    assert(field.value == "x")
    assert(!rootSawChar)
    pilot.pressKey(KeyCode.Escape).waitForIdle() // input declines Escape; root also declines: harmless
    pilot.pressKey(KeyCode.Char('q')) // 'q' would be typed into the input...
    pilot.waitForIdle()
    assert(field.value == "xq") // ...proving focused-first ordering
    pilot.pressKey(KeyCode.Char('c'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())
