package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Flex, KeyCode, Modifiers}
import io.worxbend.tui.runtime.ReactiveScope

import org.scalatest.funsuite.AnyFunSuite

final class GrammarSpec extends AnyFunSuite:

  test("Key constants and constructors build the expected events"):
    assert(Key.Up == KeyEvent.of(KeyCode.Up))
    assert(Key.ctrl('s') == Key.CtrlS)
    assert(Key.char('a') == KeyEvent.char('a'))
    assert(Key.f(5) == KeyEvent.of(KeyCode.F(5)))

  test("onKey consumes a bound key, declines others, and returns via the handler"):
    var fired = false
    val el    = text("x").onKey(Key.Up) { fired = true }
    assert(el.props.onKey.exists(_(Key.Up)))
    assert(fired)
    fired = false
    assert(el.props.onKey.exists(handler => !handler(Key.Down)))
    assert(!fired)

  test("onKey binds several keys to one action"):
    var count = 0
    val el    = text("x").onKey(Key.Up, Key.Down) { count += 1 }
    el.props.onKey.foreach(h => { h(Key.Up); h(Key.Down); h(Key.Left) })
    assert(count == 2)

  test("chained onKey calls compose instead of overwriting"):
    var up   = false
    var down = false
    val el   = text("x").onKey(Key.Up) { up = true }.onKey(Key.Down) { down = true }
    el.props.onKey.foreach(h => { h(Key.Up); h(Key.Down) })
    assert(up && down)

  test("withStyle pushes a default style onto the subtree, but a child's own style wins"):
    val tree     = withStyle(_.withFg(Color.Red))(row(text("a").color(Color.Green), text("b")))
    val children = tree.children
    assert(children(0).props.style.fg.contains(Color.Green)) // child override survives
    assert(children(1).props.style.fg.contains(Color.Red)) // inherited default

  test("withStyle unions modifiers down the tree"):
    val tree = withStyle(_.bold)(column(text("a"), text("b")))
    assert(tree.children.forall(_.props.style.modifiers.has(Modifiers.Bold)))

  test("flex helpers set the mode on row/column and are identity elsewhere"):
    assert(row(text("a")).center.asInstanceOf[RowElement].flex == Flex.Center)
    assert(column(text("a")).spaceBetween.asInstanceOf[ColumnElement].flex == Flex.SpaceBetween)
    assert(row(text("a")).gap(3).asInstanceOf[RowElement].spacing == 3)
    assert(text("x").center == text("x")) // identity on a leaf

  test("the View alias is a reactive computation producing an Element"):
    val v: View         = text("from a view alias")
    given ReactiveScope = ReactiveScope.generational(() => ())
    val el: Element     = v // applies the context function with the given scope
    assert(el.isInstanceOf[TextElement])
