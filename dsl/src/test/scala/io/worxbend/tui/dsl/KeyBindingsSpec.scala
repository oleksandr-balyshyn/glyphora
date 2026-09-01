package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyCode, KeyEvent, KeyModifiers}

import org.scalatest.funsuite.AnyFunSuite

/** Binding declaration and dispatch. The spec vocabulary itself is `KeyEvent.parse`, tested in `core`'s
  * `KeyEventParseSpec`; what is left here is what a [[KeyBinding]] does with the events it parsed.
  */
final class KeyBindingsSpec extends AnyFunSuite:

  test("a '+' binding declares and fires like any other key"):
    var count    = 0
    val declared = KeyBindings(binding("+", "increment")(count += 1))
    assert(declared.handle(Key.char('+')))
    assert(!declared.handle(Key.char('-')))
    assert(count == 1)
    assert(declared.hints == Seq(("+", "increment")))

  test("a binding on an unreachable ctrl spec throws at declaration time"):
    assertThrows[IllegalArgumentException](binding("ctrl+i", "reindent")(()))

  test("a malformed binding spec throws at declaration time"):
    assertThrows[IllegalArgumentException](binding("not-a-key", "boom")(()))

  test("handle fires the first matching binding only"):
    var fired    = List.empty[String]
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

  /** A vim-flavoured app wants `j` and `down` to mean one command. Declaring the action twice would fire correctly but
    * advertise the command twice — two hints, two help rows, two palette entries — so one binding carries both keys.
    */
  test("a multi-key binding fires on every trigger and is advertised once"):
    var count    = 0
    val bindings = KeyBindings(binding(Seq("down", "j"), "next")(count += 1))
    assert(bindings.handle(Key.Down))
    assert(bindings.handle(Key.char('j')))
    assert(!bindings.handle(Key.Up))
    assert(count == 2)
    assert(bindings.hints == Seq("down" -> "next"))

  test("a multi-key binding rejects a malformed spec anywhere in the list"):
    assertThrows[IllegalArgumentException](binding(Seq("down", "banana"), "next")(()))
    assertThrows[IllegalArgumentException](binding(Seq.empty[String], "next")(()))

  test("a compiler-checked key declares the same binding the string spec does"):
    var fromLiteral = 0
    var fromSpec    = 0
    val checked     = binding(key"ctrl+s", "ctrl+s", "save")(fromLiteral += 1)
    val spelled     = binding("ctrl+s", "save")(fromSpec += 1)

    assert(checked.triggers == spelled.triggers)
    assert(checked.label == spelled.label)
    assert(KeyBindings(checked).handle(KeyEvent(KeyCode.Char('s'), KeyModifiers.Ctrl)))
    assert(fromLiteral == 1 && fromSpec == 0)

  test("a several-key binding still shows one hint"):
    val declared = binding(Seq(key"down", key"j"), "down", "next")(())
    assert(declared.triggers.sizeIs == 2)
    assert(KeyBindings(declared).hints == Seq(("down", "next")))

  test("a binding declared with no keys at all is a programmer error"):
    assertThrows[IllegalArgumentException](binding(Seq.empty[KeyEvent], "none", "boom")(()))
