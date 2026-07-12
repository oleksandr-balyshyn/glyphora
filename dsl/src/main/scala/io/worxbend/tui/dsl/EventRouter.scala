package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyEvent, MouseEvent}

/** Pre-focus event routing (v1 of SPEC.md §5.4): depth-first, leaves before ancestors, so the innermost
  * handler sees the event first and a `true` result consumes it (stop-propagation). Focus-aware targeting and
  * mouse hit-testing arrive with Tier 2 (PLAN.md §10 step 7).
  */
private[dsl] object EventRouter:

  def dispatchKey(element: Element, event: KeyEvent): Boolean =
    element.children.exists(dispatchKey(_, event)) || element.props.onKey.exists(_(event))

  def dispatchMouse(element: Element, event: MouseEvent): Boolean =
    element.children.exists(dispatchMouse(_, event)) || element.props.onMouse.exists(_(event))
