package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Constraint, KeyEvent, MouseEvent, Style}

/** The cross-cutting properties every [[Element]] carries: its style, an optional layout constraint (how much
  * space it claims inside a `row`/`column`/`panel`), its event handlers, and focus participation. Styling and
  * layout extension methods produce a new element with updated props — elements stay immutable values.
  *
  * `focusable` opts the element into tab-order traversal; `focused` is set by the framework's focus pass each
  * render, never by user code.
  */
final case class ElementProps(
    style: Style = Style.Default,
    constraint: Option[Constraint] = None,
    onKey: Option[KeyEvent => Boolean] = None,
    onMouse: Option[MouseEvent => Boolean] = None,
    focusable: Boolean = false,
    focused: Boolean = false,
)
