package io.worxbend.tui.dsl

import io.worxbend.tui.core.Rect

import scala.collection.mutable

/** Per-app focus bookkeeping, owned by a single `TuiApp.runWith` invocation and touched only on the render thread:
  * which focusable (by depth-first order index) has focus, how many exist, and where each rendered last frame (for
  * click-to-focus hit-testing).
  */
private[dsl] final class FocusTracker:

  var focusedIndex: Int          = 0
  var focusableCount: Int        = 0
  var focusedKey: Option[String] = None
  private val areas              = mutable.Map[Int, Rect]()

  def record(index: Int, area: Rect): Unit =
    areas(index) = area

  def clearAreas(): Unit = areas.clear()

  /** Re-anchors focus against the focus keys of the tree that is about to render (depth-first order, `None` for unkeyed
    * focusables): a keyed element keeps focus even when its position moved, and the index is clamped into the new
    * range. Areas recorded for the previous frame are dropped — this frame's render re-records them.
    */
  def reconcile(keys: Seq[Option[String]]): Unit =
    focusableCount = keys.size
    focusedKey.map(key => keys.indexOf(Some(key))).filter(_ >= 0).foreach(focusedIndex = _)
    focusedIndex = if focusableCount > 0 then math.max(0, math.min(focusedIndex, focusableCount - 1)) else 0
    focusedKey = keys.lift(focusedIndex).flatten
    clearAreas()

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

  def areaOf(index: Int): Option[io.worxbend.tui.core.Rect] = areas.get(index)

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

  /** The focus keys of every focusable in depth-first order (`None` for unkeyed ones) — the domain of
    * [[FocusTracker.focusedIndex]], and what lets focus follow an element across renders when the tree changes shape.
    */
  def focusKeys(element: Element): Vector[Option[String]] =
    val own = if element.props.focusable then Vector(element.props.focusKey) else Vector.empty
    own ++ element.children.flatMap(focusKeys)

  /** Rebuilds the tree with the focused element marked (`props.focused = true`) and every focusable wrapped in a
    * [[TrackedElement]] that records its rendered area. Indices are assigned in depth-first pre-order — the tab order.
    */
  def decorate(root: Element, tracker: FocusTracker, focusStyle: io.worxbend.tui.core.Style): Element =
    var counter = 0

    def transform(element: Element): Element =
      val current =
        if element.props.focusable then
          val index  = counter
          counter += 1
          val marked =
            if index == tracker.focusedIndex then
              element.withProps(element.props.copy(focused = true, focusStyle = focusStyle))
            else element
          TrackedElement(marked, index, tracker)
        else element
      current.withChildren(current.children.map(transform))

    transform(root)
