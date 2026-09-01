package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Color, Line, Rect, Span, Style}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

/** Text pinned to world coordinates on a [[Canvas]].
  *
  * Before this, annotating a plot meant computing cell positions by hand *outside* the widget, which stops being right
  * the moment the bounds or the area change.
  */
final class CanvasLabelSpec extends AnyFunSuite:

  private val bounds = (0.0, 4.0)

  private def labelled(labels: CanvasLabel*): Seq[String] =
    trimmedLines(rendered(Canvas(bounds, bounds, Seq.empty, labels = labels), 5, 5))

  test("a label starts in the cell holding its world point, with y pointing up"):
    assert(labelled(CanvasLabel(0.0, 4.0, Line("hi"))) == Seq("hi", "", "", "", ""))
    assert(labelled(CanvasLabel(0.0, 0.0, Line("hi"))) == Seq("", "", "", "", "hi"))

  test("a label moves with its point when the bounds change"):
    // The point of world coordinates: the same label follows the data instead of staying at a fixed cell.
    def rowOf(scale: Double): Int =
      val canvas = Canvas((0.0, scale), (0.0, scale), Seq.empty, labels = Seq(CanvasLabel(0.0, scale, Line("x"))))
      trimmedLines(rendered(canvas, 5, 5)).indexWhere(_.nonEmpty)
    assert(rowOf(4.0) == 0)
    assert(rowOf(1000.0) == 0)

  test("a label outside the bounds is dropped, as a point would be"):
    assert(labelled(CanvasLabel(9.0, 9.0, Line("hi"))).forall(_.isEmpty))
    assert(labelled(CanvasLabel(-1.0, 2.0, Line("hi"))).forall(_.isEmpty))

  test("a label at a non-finite coordinate is dropped"):
    assert(labelled(CanvasLabel(Double.NaN, 2.0, Line("hi"))).forall(_.isEmpty))
    assert(labelled(CanvasLabel(2.0, Double.PositiveInfinity, Line("hi"))).forall(_.isEmpty))

  test("labels are drawn after every shape, whatever order the caller listed them in"):
    // A dot in the same cell must not punch a hole through the text that names it.
    val canvas = Canvas(
      bounds,
      bounds,
      Seq(Shape.Points(Seq((0.0, 4.0), (4.0, 4.0)))),
      marker = "#",
      labels = Seq(CanvasLabel(0.0, 4.0, Line("ab"))),
    )
    assert(trimmedLines(rendered(canvas, 5, 5)).head == "ab  #")

  test("a label is clipped at the canvas's own right edge, not the buffer's"):
    // The canvas occupies the left three columns; a long label must not run into what is drawn beside it.
    val buffer = Buffer(Rect(0, 0, 8, 1))
    buffer.setString(3, 0, "KEEP", Style.Default)
    val canvas = Canvas(bounds, bounds, Seq.empty, labels = Seq(CanvasLabel(0.0, 4.0, Line("overlong"))))
    canvas.render(Rect(0, 0, 3, 1), buffer)
    assert(trimmedLines(buffer) == Seq("oveKEEP"))

  test("a label ending in a wide character is cut between clusters, never through one"):
    // "日" is two columns; with three columns of room only the first fits, and the second must not half-print.
    val buffer = Buffer(Rect(0, 0, 6, 1))
    val canvas = Canvas(bounds, bounds, Seq.empty, labels = Seq(CanvasLabel(0.0, 4.0, Line("日日日"))))
    canvas.render(Rect(0, 0, 3, 1), buffer)
    assert(buffer.get(0, 0).symbol == "日")
    assert(buffer.get(2, 0).symbol == " ")

  test("a multi-span label keeps each span's own style and lays them out end to end"):
    val line   = Line(Seq(Span("hot", Style.Default.withFg(Color.Red)), Span("!", Style.Default.withFg(Color.Blue))))
    val canvas = Canvas(bounds, bounds, Seq.empty, labels = Seq(CanvasLabel(0.0, 4.0, line)))
    val buffer = rendered(canvas, 5, 5)
    assert(trimmedLines(buffer).head == "hot!")
    assert(buffer.get(0, 0).style.fg.contains(Color.Red))
    assert(buffer.get(3, 0).style.fg.contains(Color.Blue))

  test("labels are placed by cell at every resolution, since a character has no half cell to sit in"):
    CanvasResolution.values.foreach { resolution =>
      val canvas =
        Canvas(bounds, bounds, Seq.empty, resolution = resolution, labels = Seq(CanvasLabel(0.0, 4.0, Line("hi"))))
      assert(trimmedLines(rendered(canvas, 5, 5)) == Seq("hi", "", "", "", ""), s"$resolution")
    }

  test("a canvas with no labels renders exactly as it did before"):
    val shapes = Seq(Shape.RectangleShape(0.0, 0.0, 4.0, 4.0))
    val plain  = Canvas(bounds, bounds, shapes, marker = "#")
    assert(trimmedLines(rendered(plain, 5, 5)) == Seq("#####", "#   #", "#   #", "#   #", "#####"))

  test("an empty label and an empty canvas both render without throwing"):
    assert(labelled(CanvasLabel(2.0, 2.0, Line(""))).forall(_.isEmpty))
    val buffer = Buffer(Rect(0, 0, 1, 1))
    Canvas(bounds, bounds, Seq.empty, labels = Seq(CanvasLabel(2.0, 2.0, Line("hi"))))
      .render(Rect(0, 0, 0, 0), buffer)
    assert(buffer.get(0, 0).symbol == " ")
