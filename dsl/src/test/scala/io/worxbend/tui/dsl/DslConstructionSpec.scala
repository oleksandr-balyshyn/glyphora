package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Constraint, KeyCode, KeyEvent, KeyModifiers, Modifiers}
import io.worxbend.tui.widgets.BorderType

import org.scalatest.funsuite.AnyFunSuite

/** DSL-construction tests: build element trees, assert their shape and props as plain data. */
final class DslConstructionSpec extends AnyFunSuite:

  test("the hello-world tree has the documented shape"):
    val tree = panel("Hello")(
      text("Welcome!").bold.fg(Color.Cyan),
      spacer,
      text("Press 'q' to quit").dim,
    ).rounded
    tree match
      case PanelElement(Some("Hello"), children, BorderType.Rounded, _, _, _, _) =>
        assert(children.size == 3)
        assert(children(0).asInstanceOf[TextElement].content == "Welcome!")
        assert(children(1).isInstanceOf[SpacerElement])
      case other => fail(s"unexpected tree shape: $other")

  test("styling extensions accumulate into the element style"):
    val element = text("x").bold.dim.fg(Color.Red).bg(Color.Black)
    assert(element.style.modifiers.hasAny(Modifiers.Bold))
    assert(element.style.modifiers.hasAny(Modifiers.Dim))
    assert(element.style.fg.contains(Color.Red))
    assert(element.style.bg.contains(Color.Black))

  test("layout extensions set the explicit constraint"):
    assert(text("x").length(5).props.constraint.contains(Constraint.Length(5)))
    assert(text("x").percent(30).props.constraint.contains(Constraint.Percentage(30)))
    assert(text("x").fill.props.constraint.contains(Constraint.Fill(1)))
    assert(text("x").fill(3).props.constraint.contains(Constraint.Fill(3)))
    assert(text("x").minSize(2).props.constraint.contains(Constraint.Min(2)))
    assert(text("x").maxSize(9).props.constraint.contains(Constraint.Max(9)))

  test("text claims one row per line by default; containers fill"):
    assert(text("a\nb").claim.vertical == Constraint.Length(2))
    assert(text("ab\nwide line").claim.horizontal == Constraint.Length(9))
    assert(row().claim.vertical == Constraint.Fill(1))
    assert(spacer(2).props.constraint.contains(Constraint.Length(2)))

  test("border builders are typed to panels and give back a panel"):
    assert(panel(text("x")).rounded.borderType == BorderType.Rounded)
    assert(panel(text("x")).doubleBorder.borderType == BorderType.Double)
    // `text("x").rounded` does not compile: `rounded` exists only on PanelElement
    assertDoesNotCompile("""text("x").rounded""")

  /** The type annotations are the assertion: every fluent call gives back the element's own type, so the node's own
    * builders stay reachable after a styling or layout call and the two orders mean the same thing.
    */
  test("styling and layout calls preserve the element's own type in either order"):
    given Theme                    = Theme.default
    val first: ProgressBarElement  = progressBar(0.5).bare.fill
    val second: ProgressBarElement = progressBar(0.5).fill.bare
    assert(first == second)
    val styledPanel: PanelElement  = panel(text("x")).bold.length(3).rounded
    assert(styledPanel.borderType == BorderType.Rounded)

  test("onKeyEvent attaches a handler without disturbing the rest of the props"):
    val handler: KeyEvent => Boolean = _ => true
    val element                      = text("x").bold.onKeyEvent(handler)
    assert(element.props.onKey.contains(handler))
    assert(element.style.modifiers.hasAny(Modifiers.Bold))

  test("key events route to the innermost handler first and consumption stops propagation"):
    val seen     = scala.collection.mutable.Buffer[String]()
    val tree     = column(
      text("inner").onKeyEvent { _ =>
        seen += "inner"
        true
      }
    ).onKeyEvent { _ =>
      seen += "outer"
      true
    }
    val consumed = EventRouter.dispatchKey(tree, KeyEvent(KeyCode.Enter, KeyModifiers.None))
    assert(consumed)
    assert(seen.toSeq == Seq("inner"))

  test("an unconsumed event bubbles from leaf to ancestor"):
    val seen     = scala.collection.mutable.Buffer[String]()
    val tree     = column(
      text("inner").onKeyEvent { _ =>
        seen += "inner"
        false
      }
    ).onKeyEvent { _ =>
      seen += "outer"
      false
    }
    val consumed = EventRouter.dispatchKey(tree, KeyEvent(KeyCode.Enter, KeyModifiers.None))
    assert(!consumed)
    assert(seen.toSeq == Seq("inner", "outer"))
