package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Modifiers, Text}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.DurationInt

final class Tier4Spec extends AnyFunSuite:

  test("a spinner cycles its frames by tick index"):
    assert(trimmedLines(rendered(Spinner(0.millis, "loading"), 12, 1)) == Seq("⠋ loading"))
    assert(trimmedLines(rendered(Spinner(90.millis, "loading"), 12, 1)) == Seq("⠙ loading"))
    assert(trimmedLines(rendered(Spinner(800.millis, "loading"), 12, 1)) == Seq("⠋ loading")) // wraps

  test("wave text highlights the clusters at the crest"):
    val buffer = rendered(AnimatedText("hello", 200.millis, TextEffect.Wave(crestWidth = 1)), 6, 1)
    assert(trimmedLines(buffer) == Seq("hello"))
    assert(buffer.get(2, 0).style.modifiers.has(Modifiers.Bold))
    assert(!buffer.get(0, 0).style.modifiers.has(Modifiers.Bold))

  test("the wave crest advances with the phase"):
    val buffer = rendered(AnimatedText("hello", 400.millis, TextEffect.Wave(crestWidth = 1)), 6, 1)
    assert(buffer.get(4, 0).style.modifiers.has(Modifiers.Bold))
    assert(!buffer.get(2, 0).style.modifiers.has(Modifiers.Bold))

  test("a dialog paints a cleared, bordered box over existing content"):
    val underlying: io.worxbend.tui.core.Widget =
      (area, buffer) =>
        (0 until area.height).foreach(y => buffer.setString(0, y, "#" * area.width, io.worxbend.tui.core.Style.Default))
    val dialog = Dialog("Confirm", Text.raw("Delete file?"), Seq("Yes", "No"), selectedButton = 1)
    val combined: io.worxbend.tui.core.Widget = (area, buffer) =>
      underlying.render(area, buffer)
      dialog.render(area, buffer)
    val buffer                                = rendered(combined, 30, 9)
    val lines                                 = trimmedLines(buffer)
    assert(lines.exists(_.contains("╔Confirm")))
    assert(lines.exists(_.contains("Delete file?")))
    assert(lines.exists(_.contains("[ Yes ] [ No ]")))
    // the dialog interior is cleared: no '#' survives between its side borders on the message row
    val messageRow                            = lines.find(_.contains("Delete file?")).getOrElse(fail("no message row"))
    val interior = messageRow.substring(messageRow.indexOf("║"), messageRow.lastIndexOf("║"))
    assert(!interior.contains("#"))

  test("dialog buttons that do not fit the box are dropped, not painted outside it"):
    import io.worxbend.tui.core.Rect
    val dialog = Dialog("Confirm", Text.raw("Pick"), Seq("Alpha", "Bravo", "Charlie", "Delta"))
    val buffer = io.worxbend.tui.core.Buffer(Rect(0, 0, 40, 9))
    dialog.render(Rect(0, 0, 20, 9), buffer)
    assert(trimmedLines(buffer).forall(_.length <= 20))

  test("dual sparklines render in the top and bottom halves"):
    val widget = DualSparkline(Seq(8, 8), Seq(4, 4), max = Some(8))
    val buffer = rendered(widget, 2, 2)
    assert(trimmedLines(buffer) == Seq("██", "▄▄"))

  /** Geometry only — the box is centred on both axes and never escapes the area it was given. `Rect.centered` itself is
    * covered by core's `RectOpsSpec`; these pin that the dialog actually routes its box through it.
    */
  test("a dialog centres its box on both axes"):
    val lines  = trimmedLines(rendered(Dialog("T", Text.raw("hi")), 30, 9))
    val top    = lines.indexWhere(_.contains("\u2554"))
    val bottom = lines.indexWhere(_.contains("\u255a"))
    assert((top, bottom) == (2, 6))           // a 5-row box in 9 rows leaves 2 rows above and below
    assert(lines(top).indexOf("\u2554") == 5) // a 20-column box in 30 columns leaves 5 columns either side
    assert(lines(top).length == 25)

  test("a dialog too large for its area is clamped inside it, not overflowed"):
    val lines = trimmedLines(rendered(Dialog("T", Text.raw("hi")), 8, 6))
    assert(lines.forall(_.length <= 8))
    assert(lines.head.startsWith("\u2554") && lines.head.endsWith("\u2557"))
