package io.worxbend.tui.dsl

import io.worxbend.tui.core.Size
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot
import io.worxbend.tui.widgets.ScrollViewState

import org.scalatest.funsuite.AnyFunSuite

final class MeasurementSpec extends AnyFunSuite:

  test("fixed-size elements report their intrinsic height"):
    assert(text("a\nb\nc").intrinsicHeight(20).contains(3))
    assert(gauge(0.5).intrinsicHeight(20).contains(1))
    assert(spacer.intrinsicHeight(20).isEmpty) // fill: unmeasurable

  test("columns sum measurable children; rows take the max; panels add their borders"):
    assert(column(text("a"), text("b\nc")).intrinsicHeight(20).contains(3))
    assert(row(text("a"), text("b\nc")).intrinsicHeight(20).contains(2))
    assert(panel("t")(text("a"), text("b")).intrinsicHeight(20).contains(4))
    assert(column(text("a"), spacer).intrinsicHeight(20).isEmpty) // a fill child poisons the sum

  test("markdown measures its wrapped height for the width"):
    val element = markdown("1234567890 1234567890") // 21 columns of prose
    assert(element.intrinsicHeight(30).contains(1))
    assert(element.intrinsicHeight(10).exists(_ >= 2)) // must wrap at narrow widths

  test("a scrollView without an explicit height measures its content end to end"):
    val backend = HeadlessBackend(Size(12, 3))
    val state   = ScrollViewState()
    val content = column((0 until 8).map(n => text(s"row $n"))*)
    val app     = new TuiApp:
      override def bindings: KeyBindings     = KeyBindings(binding("ctrl+q", "quit")(quit()))
      def view(using ReactiveScope): Element = scrollView(content, state)
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    assert(pilot.screenLines.head.startsWith("row 0"))
    pilot.pressKey(KeyCode.PageDown).waitForIdle()
    assert(state.offset == 5) // 8 measured rows - 3 viewport: measurement found the real content height
    assert(pilot.screenLines.head.startsWith("row 5"))
    pilot.pressKey(KeyCode.Char('q'), KeyModifiers.Ctrl)
    assert(pilot.awaitTermination())
