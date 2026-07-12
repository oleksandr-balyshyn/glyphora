package io.worxbend.tui.dsl

import io.worxbend.tui.core.Rect

import scala.collection.mutable

/** Per-app focus bookkeeping, owned by a single `TuiApp.runWith` invocation and touched only on the render thread:
  * which focusable (by depth-first order index) has focus, how many exist, and where each rendered last frame (for
  * click-to-focus hit-testing).
  */
private[dsl] final class FocusTracker:

  var focusedIndex: Int = 0
  var focusableCount: Int = 0
  private val areas = mutable.Map[Int, Rect]()

  def record(index: Int, area: Rect): Unit =
    areas(index) = area

  def clearAreas(): Unit = areas.clear()

  def focusNext(): Boolean =
    if focusableCount > 1 then
      focusedIndex = (focusedIndex + 1) % focusableCount
      true
    else false

  def focusPrevious(): Boolean =
    if focusableCount > 1 then
      focusedIndex = (focusedIndex - 1 + focusableCount) % focusableCount
      true
    else false

  /** The innermost focusable rendered at this position, if any. */
  def hitTest(x: Int, y: Int): Option[Int] =
    val hits = areas.filter((_, area) => area.contains(io.worxbend.tui.core.Position(x, y)))
    hits.minByOption((_, area) => area.area).map((index, _) => index)

private[dsl] object FocusPass:

  /** A copy of the tree with every element made unfocusable — how layers *below* a modal drop out of the tab order
    * while remaining visible.
    */
  def suppressFocus(element: Element): Element =
    val cleared =
      if element.props.focusable then element.withProps(element.props.copy(focusable = false)) else element
    cleared.withChildren(cleared.children.map(suppressFocus))

  /** Number of focusable elements in depth-first order — the domain of [[FocusTracker.focusedIndex]]. */
  def countFocusables(element: Element): Int =
    val own = if element.props.focusable then 1 else 0
    own + element.children.map(countFocusables).sum

  /** Rebuilds the tree with the focused element marked (`props.focused = true`) and every focusable wrapped in a
    * [[TrackedElement]] that records its rendered area. Indices are assigned in depth-first pre-order — the tab order.
    */
  def decorate(root: Element, tracker: FocusTracker): Element =
    var counter = 0

    def transform(element: Element): Element =
      val current =
        if element.props.focusable then
          val index = counter
          counter += 1
          val marked = element.withProps(element.props.copy(focused = index == tracker.focusedIndex))
          TrackedElement(marked, index, tracker)
        else element
      current.withChildren(current.children.map(transform))

    transform(root)
