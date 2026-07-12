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

  /** Routes a mouse event to the hit tracked element (user handler, then built-in behavior with the element's
    * rendered area), bubbling unconsumed events up its ancestors' `onMouseEvent` handlers.
    */
  def dispatchMouseAt(root: Element, index: Int, area: io.worxbend.tui.core.Rect, event: MouseEvent): Boolean =
    pathToTracked(root, index) match
      case Some(leafToRoot) =>
        val leaf = leafToRoot.head
        val leafConsumed =
          leaf.props.onMouse.exists(_(event)) || leaf.builtinMouseHandler.exists(_(event, area))
        leafConsumed || leafToRoot.tail.exists(_.props.onMouse.exists(_(event)))
      case None => false

  private def pathToTracked(element: Element, index: Int): Option[List[Element]] =
    element match
      case tracked: TrackedElement if tracked.index == index => Some(List(tracked))
      case _ =>
        element.children
          .to(LazyList)
          .map(pathToTracked(_, index))
          .collectFirst { case Some(path) => path :+ element }

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
