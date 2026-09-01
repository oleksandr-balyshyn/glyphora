package io.worxbend.tui.dsl

import io.worxbend.tui.dsl.*

import org.scalatest.funsuite.AnyFunSuite

/** The one-import promise, checked for the canvas.
  *
  * `canvas(...)` takes `Shape`s, and writing a `Shape` of your own means writing `def draw(painter: Painter)` — so an
  * application that imports only `io.worxbend.tui.dsl.*` has to be able to spell `Painter`. Before `Painter` was
  * re-exported it could not, and the promise held for every widget except the one meant to be extended.
  *
  * The assertion here is mostly that this file compiles at all; the rendered check that follows only confirms the shape
  * was actually handed to the canvas.
  */
final class CanvasShapeImportSpec extends AnyFunSuite:

  /** A shape written the way an application would write one: dot space, no import beyond `dsl.*`. */
  private final case class CornerDot(style: Style) extends Shape:
    def draw(painter: Painter): Unit =
      painter.paintDot(0, 0, style)

  test("a user-written Shape needs nothing beyond the dsl import"):
    val element = canvas((0.0, 1.0), (0.0, 1.0))(CornerDot(Style.Default))
    element.widget match
      case built: io.worxbend.tui.widgets.Canvas => assert(built.shapes == Seq(CornerDot(Style.Default)))
      case other                                 => fail(s"expected a Canvas widget, got $other")
