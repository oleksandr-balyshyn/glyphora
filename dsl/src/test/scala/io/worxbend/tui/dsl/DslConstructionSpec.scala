package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Constraint, KeyCode, KeyEvent, KeyModifiers, Modifiers}
import io.worxbend.tui.widgets.BorderType

import org.scalatest.funsuite.AnyFunSuite

/** DSL-construction tests (PLAN.md §9): build element trees, assert their shape and props as plain data. */
final class DslConstructionSpec extends AnyFunSuite:

  test("the hello-world tree has the documented shape"):
    val tree = panel("Hello")(
      text("Welcome!").bold.color(Color.Cyan),
      spacer,
      text("Press 'q' to quit").dim,
    ).rounded
    tree match
      case PanelElement(Some("Hello"), children, BorderType.Rounded, _) =>
        assert(children.size == 3)
        assert(children(0).asInstanceOf[TextElement].content == "Welcome!")
        assert(children(1).isInstanceOf[SpacerElement])
      case other => fail(s"unexpected tree shape: $other")

  test("styling extensions accumulate into the element style"):
    val element = text("x").bold.dim.color(Color.Red).background(Color.Black)
    assert(element.style.modifiers.has(Modifiers.Bold))
    assert(element.style.modifiers.has(Modifiers.Dim))
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
    assert(text("a\nb").defaultConstraint == Constraint.Length(2))
    assert(row().defaultConstraint == Constraint.Fill(1))
    assert(spacer(2).props.constraint.contains(Constraint.Length(2)))

  test("rounded only affects panels"):
    assert(panel(text("x")).rounded.asInstanceOf[PanelElement].borderType == BorderType.Rounded)
    assert(text("x").rounded == text("x"))

  test("onKeyEvent attaches a handler without disturbing the rest of the props"):
    val handler: KeyEvent => Boolean = _ => true
    val element = text("x").bold.onKeyEvent(handler)
    assert(element.props.onKey.contains(handler))
    assert(element.style.modifiers.has(Modifiers.Bold))

  test("key events route to the innermost handler first and consumption stops propagation"):
    val seen = scala.collection.mutable.Buffer[String]()
    val tree = column(
      text("inner").onKeyEvent { _ =>
        seen += "inner"
        true
      },
    ).onKeyEvent { _ =>
      seen += "outer"
      true
    }
    val consumed = EventRouter.dispatchKey(tree, KeyEvent(KeyCode.Enter, KeyModifiers.None))
    assert(consumed)
    assert(seen.toSeq == Seq("inner"))

  test("an unconsumed event bubbles from leaf to ancestor"):
    val seen = scala.collection.mutable.Buffer[String]()
    val tree = column(
      text("inner").onKeyEvent { _ =>
        seen += "inner"
        false
      },
    ).onKeyEvent { _ =>
      seen += "outer"
      false
    }
    val consumed = EventRouter.dispatchKey(tree, KeyEvent(KeyCode.Enter, KeyModifiers.None))
    assert(!consumed)
    assert(seen.toSeq == Seq("inner", "outer"))
