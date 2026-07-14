package io.worxbend.tui.dsl

import io.worxbend.tui.core.Size
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot
import io.worxbend.tui.widgets.TextAreaState

import org.scalatest.funsuite.AnyFunSuite

/** End-to-end coverage for the Tier 5 DSL elements' built-in key handling. */
final class Tier5ElementsSpec extends AnyFunSuite:

  test("a focused textArea edits multi-line text, consumes Enter, and undoes with Ctrl+Z"):
    val backend = HeadlessBackend(Size(30, 6))
    val state   = TextAreaState()
    val app     = new TuiApp:
      def view(using ReactiveScope): Element =
        column(textArea(state)).onKeyEvent {
          case KeyEvent(KeyCode.Char('q'), m) if m.has(KeyModifiers.Ctrl) =>
            quit()
            true
          case _                                                          => false
        }
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    pilot.typeText("one").pressKey(KeyCode.Enter).typeText("two").waitForIdle()
    assert(state.value == "one\ntwo")
    assert(pilot.screenLines.take(2) == Seq("one", "two"))
    pilot.pressKey(KeyCode.Char('z'), KeyModifiers.Ctrl).waitForIdle()
    assert(state.value == "one\ntw")
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())

  test("a focused textArea leaves Tab available for focus traversal"):
    val backend     = HeadlessBackend(Size(30, 6))
    val editorState = TextAreaState()
    val secondState = TextAreaState()
    val app         = new TuiApp:
      def view(using ReactiveScope): Element =
        column(textArea(editorState), textArea(secondState)).onKeyEvent {
          case KeyEvent(KeyCode.Escape, _) =>
            quit()
            true
          case _                           => false
        }
    val pilot       = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    pilot.typeText("a").pressKey(KeyCode.Tab).typeText("b").waitForIdle()
    assert(editorState.value == "a")
    assert(secondState.value == "b")
    pilot.pressKey(KeyCode.Escape)
    assert(pilot.awaitTermination())

  test("a paste lands whole in the focused textArea and folds to one line in an input"):
    val backend   = HeadlessBackend(Size(30, 6))
    val inputSt   = io.worxbend.tui.widgets.TextInputState()
    val editorSt  = TextAreaState()
    val app       = new TuiApp:
      override def bindings: KeyBindings = KeyBindings(binding("ctrl+q", "quit")(quit()))
      def view(using ReactiveScope): Element = column(input(inputSt), textArea(editorSt))
    val pilot     = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    backend.postEvent(io.worxbend.tui.core.Event.Paste("one\ntwo"))
    pilot.waitForIdle()
    assert(inputSt.value == "one two") // focused single-line input folds the newline
    pilot.pressKey(KeyCode.Tab).waitForIdle()
    backend.postEvent(io.worxbend.tui.core.Event.Paste("three\nfour"))
    pilot.waitForIdle()
    assert(editorSt.value == "three\nfour") // multi-line editor keeps it
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())
