package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Constraint, KeyEvent, MouseEvent, Style}

/** The cross-cutting properties every [[Element]] carries: its style, an optional layout constraint (how much
  * space it claims inside a `row`/`column`/`panel`), and its event handlers. Styling/layout extension methods
  * produce a new element with updated props — elements stay immutable values.
  */
final case class ElementProps(
    style: Style = Style.Default,
    constraint: Option[Constraint] = None,
    onKey: Option[KeyEvent => Boolean] = None,
    onMouse: Option[MouseEvent => Boolean] = None,
)
