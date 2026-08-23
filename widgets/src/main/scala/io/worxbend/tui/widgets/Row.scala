package io.worxbend.tui.widgets

import io.worxbend.tui.core.{Buffer, Constraint, Direction, Flex, Layout, Rect, Widget}

/** One slot of a [[Row]] or [[Column]]: how much space the slot claims and what renders inside it. */
final case class LayoutItem(constraint: Constraint, widget: Widget)

object LayoutItem:

  /** Splits `area` along `direction` by the items' constraints and renders each item into its own segment.
    *
    * The half of [[Row]] and [[Column]] that is the same in both: only the axis differs, so it is stated once here
    * rather than twice in two files that would then have to be kept in step. Segments that come out empty are skipped,
    * because a widget handed a zero-width rect can only guess at what its caller meant.
    */
  private[widgets] def renderSplit(
      direction: Direction,
      items: Seq[LayoutItem],
      spacing: Int,
      flex: Flex,
      area: Rect,
      buffer: Buffer,
  ): Unit =
    val segments = Layout(direction, items.map(_.constraint), spacing, flex).split(area)
    items.zip(segments).foreach { (item, segment) =>
      if !segment.isEmpty then item.widget.render(segment, buffer)
    }

/** Lays its items out left-to-right using the core constraint solver and renders each into its segment. */
final case class Row(items: Seq[LayoutItem], spacing: Int = 0, flex: Flex = Flex.Start) extends Widget:
  def render(area: Rect, buffer: Buffer): Unit =
    LayoutItem.renderSplit(Direction.Horizontal, items, spacing, flex, area, buffer)
