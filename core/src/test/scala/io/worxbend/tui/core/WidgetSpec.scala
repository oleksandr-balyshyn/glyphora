package io.worxbend.tui.core

import org.scalatest.funsuite.AnyFunSuite

final class WidgetSpec extends AnyFunSuite:

  test("a lambda is a valid widget via SAM conversion"):
    val widget: Widget = (area, buffer) => buffer.setString(area.x, area.y, "hi", Style.Default)
    val buf            = Buffer(Rect(0, 0, 5, 1))
    widget.render(buf.area, buf)
    assert(buf.get(0, 0).symbol == "h")

  test("a stateful widget reads caller-owned state at render time"):
    final case class CounterState(count: Int)
    val widget = new StatefulWidget[CounterState]:
      def render(area: Rect, buffer: Buffer, state: CounterState): Unit =
        buffer.setString(area.x, area.y, state.count.toString, Style.Default)
    val buf    = Buffer(Rect(0, 0, 5, 1))
    widget.render(buf.area, buf, CounterState(7))
    assert(buf.get(0, 0).symbol == "7")
