package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Modifiers, Text}
import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class Tier4Spec extends AnyFunSuite:

  test("a spinner cycles its frames by tick index"):
    assert(trimmedLines(rendered(Spinner(0, "loading"), 12, 1)) == Seq("⠋ loading"))
    assert(trimmedLines(rendered(Spinner(1, "loading"), 12, 1)) == Seq("⠙ loading"))
    assert(trimmedLines(rendered(Spinner(10, "loading"), 12, 1)) == Seq("⠋ loading")) // wraps

  test("wave text highlights the clusters at the crest"):
    val buffer = rendered(WaveText("hello", phase = 2, crestWidth = 1), 6, 1)
    assert(trimmedLines(buffer) == Seq("hello"))
    assert(buffer.get(2, 0).style.modifiers.has(Modifiers.Bold))
    assert(!buffer.get(0, 0).style.modifiers.has(Modifiers.Bold))

  test("the wave crest advances with the phase"):
    val buffer = rendered(WaveText("hello", phase = 4, crestWidth = 1), 6, 1)
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

  test("dual sparklines render in the top and bottom halves"):
    val widget = DualSparkline(Seq(8, 8), Seq(4, 4), max = Some(8))
    val buffer = rendered(widget, 2, 2)
    assert(trimmedLines(buffer) == Seq("██", "▄▄"))

  test("Dialog.centered centers and clamps"):
    import io.worxbend.tui.core.Rect
    assert(Dialog.centered(Rect(0, 0, 20, 10), 10, 4) == Rect(5, 3, 10, 4))
    assert(Dialog.centered(Rect(0, 0, 6, 3), 10, 4) == Rect(0, 0, 6, 3))
