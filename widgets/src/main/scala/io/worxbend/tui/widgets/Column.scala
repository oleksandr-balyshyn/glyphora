package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Direction, Flex, Rect, Widget}

/** Lays its items out top-to-bottom using the core constraint solver and renders each into its segment. */
final case class Column(items: Seq[LayoutItem], spacing: Int = 0, flex: Flex = Flex.Start) extends Widget:
  def render(area: Rect, buffer: Buffer): Unit =
    LayoutItem.renderSplit(Direction.Vertical, items, spacing, flex, area, buffer)
