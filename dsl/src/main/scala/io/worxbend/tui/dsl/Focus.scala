package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Position, Rect, Style}

import scala.collection.mutable

/** How a rect drawn into a scroll view's offscreen buffer maps onto the surface that scroll view renders into:
  * `dx`/`dy` translate content coordinates (the scroll offset included), `viewport` is the visible window that clips
  * the result. Composed innermost-first by [[FocusTracker.record]], so nested scroll views translate all the way out to
  * the screen.
  */
private[dsl] final case class ViewportTransform(dx: Int, dy: Int, viewport: Rect):
  def apply(rect: Rect): Rect = rect.offset(dx, dy).intersection(viewport)

/** Per-app focus bookkeeping, owned by a single `TuiApp.runWith` invocation and touched only on the render thread:
  * which focusable (by depth-first order index) has focus, how many exist, and where each rendered last frame (for
  * click-to-focus hit-testing).
  *
  * It also carries a second, independent area map for the non-focusable elements that carry an `onMouseEvent`, so
  * [[EventRouter]] can filter mouse delivery by pointer position for them too. That map is deliberately *not* part of
  * hit-testing or the tab order: a pointer id is not a focus index.
  */
private[dsl] final class FocusTracker:

  var focusedIndex: Int          = 0
  var focusableCount: Int        = 0
  var focusedKey: Option[String] = None
  private val areas              = mutable.Map[Int, Rect]()
  private val pointerAreas       = mutable.Map[Int, Rect]()
  private var viewports          = List.empty[ViewportTransform]

  /** Records where `index` rendered, mapped out of any offscreen scroll buffers it rendered inside: translated into
    * screen coordinates and clipped to every enclosing viewport. A focusable scrolled out of view clips to nothing and
    * is not recorded at all, so [[hitTest]] can never return it and [[areaOf]] never hands an empty area to a built-in
    * mouse handler.
    */
  def record(index: Int, area: Rect): Unit =
    val onScreen = onScreenArea(area)
    if !onScreen.isEmpty then areas(index) = onScreen

  /** Records where an element that carries an `onMouseEvent` but is not focusable rendered, so mouse delivery can be
    * filtered by pointer position the way click-to-focus already is. Keyed by the pointer id the decoration pass
    * assigns — a numbering independent of the focus index, and invisible to the tab order. Translated onto the screen
    * and dropped when scrolled out of view, exactly as [[record]] does.
    */
  def recordPointer(id: Int, area: Rect): Unit =
    val onScreen = onScreenArea(area)
    if !onScreen.isEmpty then pointerAreas(id) = onScreen

  /** Published by a scroll view for exactly the duration of its content render, and paired with [[popViewport]] in a
    * `finally` so an exception mid-render cannot leak a translation into a sibling subtree.
    */
  def pushViewport(viewport: ViewportTransform): Unit = viewports = viewport :: viewports

  def popViewport(): Unit = viewports = viewports.drop(1)

  /** A rendered rect translated out of every offscreen scroll buffer it was drawn into, innermost first: an inner
    * scroll view's transform maps into the *enclosing* content space, and the enclosing one then maps that onward.
    */
  private def onScreenArea(area: Rect): Rect =
    viewports.foldLeft(area)((rect, viewport) => viewport(rect))

  def clearAreas(): Unit =
    areas.clear()
    pointerAreas.clear()
    viewports = Nil

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
      focusTo((focusedIndex + 1) % focusableCount)
      true
    else false

  def focusPrevious(): Boolean =
    if focusableCount > 1 then
      focusTo((focusedIndex - 1 + focusableCount) % focusableCount)
      true
    else false

  /** Moves focus to `index` deliberately (Tab, a click), forgetting the remembered [[focusedKey]].
    *
    * The key exists so focus can *follow* an element whose position changed between renders. Keeping the old key here
    * would instead pull focus straight back to where it was on the next render, which is what an explicit move is
    * asking not to happen; the render pass re-derives the key from the new index.
    */
  def focusTo(index: Int): Unit =
    // clamped here as well as in `reconcile`, so the class holds its own invariant no matter which caller moves focus:
    // an index outside the range would render a frame with nothing focused at all
    focusedIndex = if focusableCount > 0 then math.max(0, math.min(index, focusableCount - 1)) else 0
    focusedKey = None

  def areaOf(index: Int): Option[Rect] = areas.get(index)

  def pointerAreaOf(id: Int): Option[Rect] = pointerAreas.get(id)

  /** The innermost focusable rendered at `pos`, if any. `pos` is absolute, the same coordinate space a
    * [[io.worxbend.tui.core.MouseEvent]] reports in.
    */
  def hitTest(pos: Position): Option[Int] =
    val hits = areas.filter((_, area) => area.contains(pos))
    hits.minByOption((_, area) => area.area).map((index, _) => index)

private[dsl] object FocusPass:

  /** A copy of the tree with every element made unfocusable and marked `inert` — how a layer *below* a modal drops out
    * of the tab order *and* out of event routing while remaining visible. Suppression covers input, not merely tabbing:
    * no element in the returned tree receives a key or a mouse event, whether or not the layer above it contains a
    * focusable of its own ([[EventRouter]] refuses to descend into an inert subtree).
    */
  def suppressFocus(element: Element): Element =
    val cleared = element.withProps(
      element.props.copy(focusable = false, focusState = element.props.focusState.copy(inert = true))
    )
    cleared.withChildren(cleared.children.map(suppressFocus))

  /** The focus keys of every focusable in depth-first order (`None` for unkeyed ones) — the domain of
    * [[FocusTracker.focusedIndex]], and what lets focus follow an element across renders when the tree changes shape.
    */
  def focusKeys(element: Element): Vector[Option[String]] =
    val own = if element.props.focusable then Vector(element.props.focusKey) else Vector.empty
    own ++ element.children.flatMap(focusKeys)

  /** Rebuilds the tree with the theme's focus cue stamped on every node, the focused element marked (`props.focused =
    * true`), and every focusable wrapped in a [[TrackedElement]] that records its rendered area; a non-focusable
    * element that carries an `onMouseEvent` is wrapped in a [[PointerElement]] instead, which records its area for
    * pointer-filtered mouse delivery. Focus indices are assigned in depth-first pre-order — the tab order; pointer ids
    * are a separate numbering that no other pass reads.
    *
    * `focusStyle` goes onto *every* node, not only the focused one, because it is the app's theme cue and not a
    * per-element flag: a collection element paints its selected row in it whether or not it currently holds focus (see
    * [[ListElement]]), so a list sitting beside the focused one — or one in a subtree [[suppressFocus]] cleared for a
    * modal — still has to draw the app's highlight rather than the plain reverse-video default. `props.focused` is what
    * distinguishes the one element keystrokes go to, and it is set here on exactly one node.
    */
  def decorate(root: Element, tracker: FocusTracker, focusStyle: Style): Element =
    var counter        = 0
    var pointerCounter = 0

    def themed(element: Element, focused: Boolean): Element =
      element.withProps(
        element.props.copy(focusState = element.props.focusState.copy(focused = focused, focusStyle = focusStyle))
      )

    def transform(element: Element): Element =
      val current =
        if element.props.focusable then
          val index = counter
          counter += 1
          TrackedElement(themed(element, focused = index == tracker.focusedIndex), index, tracker)
        else if element.props.onMouse.isDefined then
          val id = pointerCounter
          pointerCounter += 1
          PointerElement(themed(element, focused = false), id, tracker)
        else themed(element, focused = false)
      current.withChildren(viewportWrapped(element, current.children.map(transform), tracker))

    transform(root)

  /** A scroll view renders its content into an offscreen buffer, so every rect the content subtree is handed — and
    * hence every area recorded underneath it — is in content coordinates. Wrapping the content re-anchors those records
    * onto the screen.
    */
  private def viewportWrapped(element: Element, children: Seq[Element], tracker: FocusTracker): Seq[Element] =
    element match
      case scroll: ScrollViewElement => children.map(child => ScrollViewportElement(child, tracker, scroll.state))
      case _                         => children
