package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyCode, KeyEvent, KeyModifiers}

import org.scalatest.funsuite.AnyFunSuite

final class KeyBindingsSpec extends AnyFunSuite:

  test("parseKey handles plain characters, named keys, and function keys"):
    assert(KeyBindings.parseKey("q") == Right(KeyEvent.of(KeyCode.Char('q'))))
    assert(KeyBindings.parseKey("enter") == Right(KeyEvent.of(KeyCode.Enter)))
    assert(KeyBindings.parseKey("esc") == Right(KeyEvent.of(KeyCode.Escape)))
    assert(KeyBindings.parseKey("space") == Right(KeyEvent.of(KeyCode.Char(' '))))
    assert(KeyBindings.parseKey("f5") == Right(KeyEvent.of(KeyCode.F(5))))
    assert(KeyBindings.parseKey("pgdn") == Right(KeyEvent.of(KeyCode.PageDown)))

  test("parseKey composes modifiers"):
    assert(KeyBindings.parseKey("ctrl+s") == Right(KeyEvent(KeyCode.Char('s'), KeyModifiers.Ctrl)))
    assert(KeyBindings.parseKey("shift+tab") == Right(KeyEvent(KeyCode.Tab, KeyModifiers.Shift)))
    assert(
      KeyBindings.parseKey("ctrl+alt+x") ==
        Right(KeyEvent(KeyCode.Char('x'), KeyModifiers.Ctrl | KeyModifiers.Alt))
    )

  test("parseKey rejects nonsense"):
    assert(KeyBindings.parseKey("").isLeft)
    assert(KeyBindings.parseKey("ctrl+").isLeft)
    assert(KeyBindings.parseKey("banana").isLeft)
    assert(KeyBindings.parseKey("a+b").isLeft)

  test("a malformed binding spec throws at declaration time"):
    assertThrows[IllegalArgumentException](binding("not-a-key", "boom")(()))

  test("handle fires the first matching binding only"):
    var fired = List.empty[String]
    val bindings = KeyBindings(
      binding("q", "quit") { fired = "quit" :: fired },
      binding("ctrl+s", "save") { fired = "save" :: fired },
    )
    assert(bindings.handle(KeyEvent(KeyCode.Char('s'), KeyModifiers.Ctrl)))
    assert(!bindings.handle(KeyEvent.of(KeyCode.Char('x'))))
    assert(fired == List("save"))

  test("hints expose labels and descriptions in declaration order"):
    val bindings = KeyBindings(
      binding("q", "quit")(()),
      binding("?", "help")(()).copy(showInHints = false),
      binding("ctrl+s", "save")(()),
    )
    assert(bindings.hints == Seq("q" -> "quit", "ctrl+s" -> "save"))
