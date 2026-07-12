package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyEvent, MouseEvent}

/** Event routing (SPEC.md §5.4).
  *
  * With a focused element present, a key event starts at the focused element and bubbles up its ancestor chain — at
  * each node the user's `onKeyEvent` runs first, then the framework's built-in behavior (editing, toggling); a `true`
  * result consumes the event. With no focusable elements the tree is walked depth-first, leaves before ancestors, with
  * the same stop-propagation contract.
  */
private[dsl] object EventRouter:

  def dispatchKey(root: Element, event: KeyEvent): Boolean =
    pathToFocused(root) match
      case Some(leafToRoot) => leafToRoot.exists(handlesKey(_, event))
      case None             => dispatchKeyDepthFirst(root, event)

  def dispatchMouse(element: Element, event: MouseEvent): Boolean =
    element.children.exists(dispatchMouse(_, event)) || element.props.onMouse.exists(_(event))

  private def handlesKey(element: Element, event: KeyEvent): Boolean =
    element.props.onKey.exists(_(event)) || element.builtinKeyHandler.exists(_(event))

  private def dispatchKeyDepthFirst(element: Element, event: KeyEvent): Boolean =
    element.children.exists(dispatchKeyDepthFirst(_, event)) || handlesKey(element, event)

  /** The focused element and its ancestors, innermost first. */
  private def pathToFocused(element: Element): Option[List[Element]] =
    if element.props.focused && element.props.focusable then Some(List(element))
    else
      element.children
        .to(LazyList)
        .map(pathToFocused)
        .collectFirst { case Some(path) => path :+ element }
