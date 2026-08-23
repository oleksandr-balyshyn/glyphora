package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Constraint, KeyCode, KeyEvent, KeyModifiers, Modifiers}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}
import io.worxbend.tui.widgets.{BigText, BorderType}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.DurationInt

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
        children(0) match
          case TextElement(content, _) => assert(content == "Welcome!")
          case other                   => fail(s"expected the first child to be a TextElement, got $other")
        children(1) match
          case _: SpacerElement => ()
          case other            => fail(s"expected the second child to be a SpacerElement, got $other")
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

  /** A one-row control claims one row *vertically* and whatever the container has *horizontally*. Saying so with a
    * baked-in `Constraint.Length(1)` in the node's props instead — which is what `progressBar`, `indeterminateBar` and
    * `marquee` used to do — sets both axes at once, so the same bar came out one column wide inside a `row` and every
    * call site had to write `.fill` to undo it.
    */
  test("one-row controls claim a row of height, not a column of width"):
    given Theme = Theme.default
    Seq(progressBar(0.5), indeterminateBarAt(0.millis), marqueeAt("news", 0.millis)).foreach { element =>
      assert(element.props.constraint.isEmpty, s"$element should leave the constraint to the caller")
      assert(element.claim == SizeClaim.OneRow, s"$element should claim one row and a fill of width")
    }

  /** The same rule for the fixed-height widgets behind the [[WidgetElement]] escape hatch. `rule`, `link`, `bigText`
    * and `tooltip` each used to bake their *height* into `props.constraint`, which a container applies along whichever
    * axis it runs — so inside a `row` a divider was one column of `─`, a link showed one letter, and a tooltip was
    * three columns of border with the help text gone. Their height is a fact about the widget, so it belongs in the
    * claim.
    */
  test("fixed-height wrapped widgets keep their width inside a row"):
    given Theme = Theme.default
    Seq(
      rule("a")                -> 1,
      link("docs", "http://x") -> 1,
      bigText("HI")            -> BigText.GlyphHeight,
      tooltip("some help")     -> 3,
    ).foreach { case (element, expectedRows) =>
      assert(element.props.constraint.isEmpty, s"$element should leave the constraint to the caller")
      assert(element.claim == SizeClaim.rows(expectedRows), s"$element should claim $expectedRows rows and fill width")
    }
    // and the layout actually honours it: two rules share a 40-column row instead of taking a column each
    assert(trimmedLines(rendered(row(rule(), rule()).widget, 40, 1)).head.length == 40)
    assert(trimmedLines(rendered(row(text("See: "), link("docs", "http://x")).widget, 40, 1)).head == "See: docs")

  /** `.rows(n)` is the escape hatch's own way to say a height, and it survives into the claim rather than into the
    * axis-ambiguous constraint that `.length(n)` sets.
    */
  test("widget(...).rows(n) claims height without claiming width"):
    assert(widget((_, _) => ()).claim == SizeClaim.Fill)
    assert(widget((_, _) => ()).rows(3).claim == SizeClaim.rows(3))
    assert(widget((_, _) => ()).rows(3).props.constraint.isEmpty)

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

  /** The knobs the factory Scaladoc promised. `table`'s doc said "add a header with `.header(...)`" and `sparkline`'s
    * said "pin the ceiling with `.max(n)`" while neither method existed, and `marquee` returned the builder-less
    * escape-hatch node, so its two real knobs — speed and gap — could not be reached from the DSL at all.
    */
  test("the header, ceiling and ticker builders reach the widget"):
    val headed = table(Seq(Seq("api", "ready")), Constraint.Length(6), Constraint.Fill(1)).header("svc", "state")
    assert(headed.header.contains(Seq("svc", "state")))
    assert(trimmedLines(rendered(headed.widget, 20, 2)).head.startsWith("svc"))

    // the same series drawn against its own peak and against a pinned ceiling of ten: the taller of the two glyphs is
    // what makes two sparklines side by side incomparable until the ceiling is pinned
    val floating = sparkline(Seq(1L, 4L))
    val pinned   = floating.max(10L)
    assert(pinned.max.contains(10L))
    assert(rendered(floating.widget, 2, 1).get(1, 0).symbol != rendered(pinned.widget, 2, 1).get(1, 0).symbol)

    val ticker: MarqueeElement = marqueeAt("news", 0.millis).speed(4.0).gap(2)
    assert(ticker.cellsPerSecond == 4.0)
    assert(ticker.gap == 2)
    // one lap is the four clusters of "news" plus the two-cell gap, so six cells in three seconds is two a second
    assert(ticker.period(3.seconds).cellsPerSecond == 2.0)
    assert(ticker.claim.vertical == Constraint.Length(1))

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
