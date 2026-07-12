package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Color, Constraint, Line, Style, Text, Widget}
import io.worxbend.tui.testsupport.BufferAssertions.rendered
import io.worxbend.tui.widgets.{Block, BorderType, Column, LayoutItem, Paragraph, Spacer}

import org.scalatest.funsuite.AnyFunSuite

/** The step-6 acceptance criterion (PLAN.md §11): the DSL hello-world produces byte-identical buffer output to the same
  * UI hand-built from `tui-widgets` — proof the DSL is a faithful layer over the widget/runtime stack, not a divergent
  * rendering path.
  */
final class DslFaithfulnessSpec extends AnyFunSuite:

  private val dslTree = panel("Hello")(
    text("Welcome!").bold.color(Color.Cyan),
    spacer,
    text("Press 'q' to quit").dim,
  ).rounded

  private val handBuilt: Widget =
    (area, buffer) =>
      val block = Block(Some(Line.styled("Hello", Style.Default)), BorderType.Rounded)
      block.render(area, buffer)
      Column(
        Seq(
          LayoutItem(Constraint.Length(1), Paragraph(Text.styled("Welcome!", Style.Default.bold.withFg(Color.Cyan)))),
          LayoutItem(Constraint.Fill(1), Spacer),
          LayoutItem(Constraint.Length(1), Paragraph(Text.styled("Press 'q' to quit", Style.Default.dim))),
        )
      ).render(block.inner(area), buffer)

  test("hello-world through the DSL is byte-identical to the hand-built widget tree"):
    val fromDsl = rendered(dslTree.widget, 30, 8)
    val fromWidgets = rendered(handBuilt, 30, 8)
    assert(fromWidgets.diff(fromDsl).isEmpty)

  test("the equivalence holds at other sizes too"):
    Seq((20, 5), (40, 12), (10, 4)).foreach { (width, height) =>
      val fromDsl = rendered(dslTree.widget, width, height)
      val fromWidgets = rendered(handBuilt, width, height)
      assert(fromWidgets.diff(fromDsl).isEmpty, s"differs at ${width}x$height")
    }
