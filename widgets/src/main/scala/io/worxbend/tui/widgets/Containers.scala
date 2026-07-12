package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Constraint, Direction, Layout, Rect, Widget}

/** One slot of a [[Row]] or [[Column]]: how much space the slot claims and what renders inside it. */
final case class LayoutItem(constraint: Constraint, widget: Widget)

/** Lays its items out left-to-right using the core constraint solver and renders each into its segment. */
final case class Row(items: Seq[LayoutItem], spacing: Int = 0) extends Widget:
  def render(area: Rect, buffer: Buffer): Unit =
    Containers.renderSplit(Direction.Horizontal, items, spacing, area, buffer)

/** Lays its items out top-to-bottom using the core constraint solver and renders each into its segment. */
final case class Column(items: Seq[LayoutItem], spacing: Int = 0) extends Widget:
  def render(area: Rect, buffer: Buffer): Unit =
    Containers.renderSplit(Direction.Vertical, items, spacing, area, buffer)

private object Containers:
  def renderSplit(direction: Direction, items: Seq[LayoutItem], spacing: Int, area: Rect, buffer: Buffer): Unit =
    val segments = Layout(direction, items.map(_.constraint), spacing).split(area)
    items.zip(segments).foreach { (item, segment) =>
      if !segment.isEmpty then item.widget.render(segment, buffer)
    }
