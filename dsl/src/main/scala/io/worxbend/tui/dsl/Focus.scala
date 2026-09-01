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

/** Which tracked focusable a pointer resolved to, and the area that resolution produced.
  *
  * Both fields are easy to mistake for something else, which is why they are named rather than left as a tuple.
  * `focusIndex` is a *focus* index — a position in the depth-first tab order, the key of [[FocusTracker.areaOf]] — not
  * the pointer id that keys the deliberately independent `pointerAreaOf` map. `area` is where the hit *resolved to*,
  * which is not always the element's own recorded area: [[EventRouter]] branches on that difference when it decides
  * what an outer element's built-in behavior runs against.
  */
private[dsl] final case class MouseHit(focusIndex: Int, area: Rect)

/** Where a focusable rendered this frame, and *when* in the frame's paint order it rendered.
  *
  * `sequence` is a counter that [[FocusTracker.record]] bumps on every recorded area and [[FocusTracker.clearAreas]]
  * resets at the start of each frame. A container paints itself before its children and paints its children in order,
  * so a larger `sequence` means "painted later", and on a terminal — where every cell holds exactly one character —
  * painted later means painted *over*. That makes the greatest sequence covering a cell the thing the user actually
  * sees there, which is the rule [[FocusTracker.hitTest]] resolves a click by.
  */
private[dsl] final case class PaintedArea(area: Rect, sequence: Int)

/** An element in the tree about to render asking to be given focus: where it sits in the tab order, and what identifies
  * it across renders.
  *
  * The identity is the element's focus key when it has one and its position in the tab order otherwise, which is why
  * both fields are here and why the whole value is compared. [[FocusTracker.reconcile]] acts on a request only when it
  * differs from the one the previous frame carried, so an autofocusing element grabs focus once — when it appears — and
  * then leaves the keyboard wherever the user takes it. Without a key an element that *moves* in the tab order looks
  * like a different request and grabs focus again, which is why `.autofocus` reads better with a `.key(...)` beside it.
  */
private[dsl] final case class AutofocusRequest(index: Int, key: Option[String])

/** Where focus stands: a position in the depth-first tab order, and the focus key of the element that was at that
  * position when focus landed there.
  *
  * The two travel together because they are only ever written together — moving focus sets both, and a key without the
  * index it was resolved from says nothing about which element renders focused. Also what [[FocusTracker.pushLayer]]
  * remembers: focus as it stood before a layer covered it, to put the cursor back when that layer goes away.
  */
private final case class FocusAnchor(index: Int, key: Option[String])

private object FocusAnchor:

  /** Nothing focused — see the `-1` note on [[FocusTracker.focusedIndex]]. Named `Cleared` rather than `None` so it
    * cannot be misread as `Option.None` in a file whose focus keys are themselves `Option[String]`.
    */
  val Cleared: FocusAnchor = FocusAnchor(-1, scala.None)

/** Where every element rendered in the frame being composed, in the order it was painted — the map click-to-focus hit
  * testing resolves against, and the one an element's built-in mouse behavior reads its own area from.
  *
  * Two independent maps: focusables keyed by focus index (the depth-first tab order), and the non-focusable elements
  * that carry an `onMouseEvent` keyed by the pointer id the decoration pass assigns, so mouse delivery can be filtered
  * by pointer position for them too. The second is deliberately *not* part of hit-testing or the tab order: a pointer
  * id is not a focus index.
  *
  * A container paints itself before its children and paints its children in order, so a later paint sequence means
  * painted *over* — see [[PaintedArea]]. Owned by one [[FocusTracker]] and touched only on the render thread.
  */
private[dsl] final class PaintedAreas:

  private val areas        = mutable.Map[Int, PaintedArea]()
  private val pointerAreas = mutable.Map[Int, Rect]()
  private var viewports    = List.empty[ViewportTransform]

  /** How many areas have been recorded so far this frame; the paint sequence the next [[record]] stamps. */
  private var paintCounter = 0

  /** Records where the focusable at `index` rendered, mapped out of any offscreen scroll buffers it rendered inside:
    * translated into screen coordinates and clipped to every enclosing viewport. A focusable scrolled out of view clips
    * to nothing and is not recorded at all, so [[hitTest]] can never return it and [[areaOf]] never hands an empty area
    * to a built-in mouse handler.
    */
  def record(index: Int, area: Rect): Unit =
    val onScreen = onScreenArea(area)
    if !onScreen.isEmpty then
      areas(index) = PaintedArea(onScreen, paintCounter)
      paintCounter += 1

  /** Records where an element that carries an `onMouseEvent` but is not focusable rendered, keyed by the pointer id the
    * decoration pass assigns. Translated onto the screen and dropped when scrolled out of view, as [[record]] does.
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

  /** Starts a frame: everything recorded for the previous one is dropped, and the next [[record]] paints first. */
  def clear(): Unit =
    areas.clear()
    pointerAreas.clear()
    paintCounter = 0
    viewports = Nil

  def areaOf(index: Int): Option[Rect] = areas.get(index).map(_.area)

  def pointerAreaOf(id: Int): Option[Rect] = pointerAreas.get(id)

  /** The focusable the user can actually *see* at `pos`, if any: of every focusable covering that cell, the one painted
    * last. `pos` is absolute, the same coordinate space a [[io.worxbend.tui.core.MouseEvent]] reports in.
    *
    * Before, this picked the smallest covering rectangle instead. That rule assumes the innermost rectangle is also the
    * visible one, which holds inside a single subtree — a button nested in a panel is both smaller and painted later —
    * but fails between `layers`: a modal panel drawn over a small button underneath it is the *larger* rectangle, so a
    * click on the modal was handed to the button the modal hides. Paint order answers both cases with one rule, and it
    * is the same rule [[EventRouter]] already uses for overlapping handler-carrying siblings, so the two halves of hit
    * testing no longer disagree.
    */
  def hitTest(pos: Position): Option[Int] =
    val hits = areas.filter((_, area) => area.area.contains(pos))
    hits.maxByOption((_, area) => area.sequence).map((index, _) => index)

/** Per-app focus bookkeeping, owned by a single `TuiApp.runWith` invocation and touched only on the render thread:
  * which focusable (by depth-first order index) has focus, how many exist, and where each rendered last frame (for
  * click-to-focus hit-testing).
  *
  * Where things rendered is [[PaintedAreas]]' job, held as a field and forwarded: this class decides where focus *is*,
  * that one remembers where the frame *put* everything.
  */
private[dsl] final class FocusTracker:

  private var anchor: FocusAnchor = FocusAnchor(0, scala.None)

  var focusableCount: Int = 0

  /** Which focusable holds focus, as a position in the depth-first tab order — or `-1`, meaning nothing does.
    *
    * `-1` is a real state, not an "uninitialised" marker: [[clearFocus]] puts the tracker there deliberately, and while
    * it lasts no element renders focused and every key goes straight past the tree to the app's bindings. `Tab` from
    * there lands on the first focusable and `Shift+Tab` on the last.
    */
  def focusedIndex: Int = anchor.index

  /** The focus key of the element focus is anchored to, if it declared one. */
  def focusedKey: Option[String] = anchor.key

  def focusedKey_=(key: Option[String]): Unit = anchor = anchor.copy(key = key)

  /** The focus keys of the tree the last [[reconcile]] saw, in tab order (`None` for an unkeyed focusable). Kept so
    * [[focusToKey]] can answer "which index is the element named `email`?" without walking the tree again.
    */
  private var focusKeysSeen = Vector.empty[Option[String]]
  private var lastAutofocus = Option.empty[AutofocusRequest]

  /** Where everything rendered this frame — a separate job from "where focus is", forwarded rather than exposed so a
    * caller cannot record into a tracker whose focus state disagrees.
    */
  private val painted = PaintedAreas()

  /** @see [[PaintedAreas.record]] */
  def record(index: Int, area: Rect): Unit = painted.record(index, area)

  /** @see [[PaintedAreas.recordPointer]] */
  def recordPointer(id: Int, area: Rect): Unit = painted.recordPointer(id, area)

  /** @see [[PaintedAreas.pushViewport]] */
  def pushViewport(viewport: ViewportTransform): Unit = painted.pushViewport(viewport)

  /** @see [[PaintedAreas.popViewport]] */
  def popViewport(): Unit = painted.popViewport()

  /** @see [[PaintedAreas.clear]] */
  def clearAreas(): Unit = painted.clear()

  /** Re-anchors focus against the focus keys of the tree that is about to render (depth-first order, `None` for unkeyed
    * focusables): a keyed element keeps focus even when its position moved, and the index is clamped into the new
    * range. Areas recorded for the previous frame are dropped — this frame's render re-records them.
    *
    * The "nothing is focused" state survives this. A tracker at `-1` — which is where [[clearFocus]] leaves it — stays
    * at `-1` however the tree changed shape, because the clamp is what an *existing* focus needs and re-anchoring a
    * deliberately dropped one would put the cursor back where the app asked it not to be.
    *
    * `autofocus` is the element asking to be focused, if the tree contains one. It is honoured only when it differs
    * from the request the previous frame carried — that is, when the asking element has just appeared. Honouring it on
    * every frame instead would pin the keyboard to that one element forever: Tab would move focus and the next render
    * would take it straight back.
    */
  def reconcile(keys: Seq[Option[String]], autofocus: Option[AutofocusRequest]): Unit =
    focusKeysSeen = keys.toVector
    focusableCount = keys.size
    if anchor.index < 0 then anchor = FocusAnchor.Cleared
    else
      val reanchored = anchor.key.map(key => keys.indexOf(Some(key))).filter(_ >= 0).getOrElse(anchor.index)
      val index      = clampedIndex(reanchored)
      anchor = FocusAnchor(index, keys.lift(index).flatten)
    val appeared = autofocus.filterNot(request => lastAutofocus.contains(request))
    lastAutofocus = autofocus
    // an element that has just appeared takes focus even from the cleared state: asking for it is the whole point
    appeared.foreach { request =>
      val index = clampedIndex(request.index)
      anchor = FocusAnchor(index, keys.lift(index).flatten)
    }
    clearAreas()

  /** An index pulled back inside the tab order, or zero when there is nothing focusable to point at. */
  private def clampedIndex(index: Int): Int =
    if focusableCount > 0 then math.max(0, math.min(index, focusableCount - 1)) else 0

  /** Moves focus to the focusable declared with `.key(name)` in the last reconciled tree; `false` — and nothing changed
    * — when no focusable carries that key, which is what happens when the element sits in a branch the view did not
    * render this frame.
    *
    * Unlike [[focusTo]] the key is *remembered*, so focus then follows that element across later renders even when the
    * tree changes shape: this is a lasting "the cursor belongs on the email field", not a one-off jump to a position.
    */
  def focusToKey(key: String): Boolean =
    val index = focusKeysSeen.indexOf(Some(key))
    if index < 0 then false
    else
      anchor = FocusAnchor(index, Some(key))
      true

  /** Drops focus entirely: no element renders focused, and keys go past the tree straight to the app's bindings until
    * `Tab`, a mouse press or [[focusToKey]] puts focus back.
    */
  def clearFocus(): Unit = anchor = FocusAnchor.Cleared

  /** Focus as each covering layer found it — see [[pushLayer]]; innermost layer first. */
  private var covered = List.empty[FocusAnchor]

  /** Called when a layer goes over the current tree — a modal or full screen is pushed, the command palette opens.
    *
    * Remembers where focus was and anchors it at the top of the tab order, so the incoming layer starts on its *first*
    * control. Without this the old index is merely clamped into the new layer's range, which is why opening a
    * three-field dialog while the app's fifth control was focused used to land the cursor on the dialog's last field.
    */
  def pushLayer(): Unit =
    covered = anchor :: covered
    anchor = FocusAnchor(0, scala.None)

  /** Called when that layer goes away. Puts focus back where the layer found it.
    *
    * `reconcile` still runs afterwards, so a tree that changed shape underneath the layer cannot restore an index that
    * is now out of range: the restored key re-anchors when it is still there, and the index is clamped when it is not.
    * Popping more layers than were pushed anchors at the top rather than failing — the two calls are driven by a depth
    * comparison and are balanced by construction, and guessing at the tab order is a better failure than an exception
    * in the render pass.
    */
  def popLayer(): Unit =
    covered match
      case frame :: rest =>
        anchor = frame
        covered = rest
      case Nil           => anchor = FocusAnchor(0, scala.None)

  /** `Tab`. From the no-focus state this lands on the *first* focusable rather than the second one. */
  def focusNext(): Boolean =
    if focusableCount == 0 then false
    else if focusedIndex < 0 then
      focusTo(0)
      true
    else if focusableCount > 1 then
      focusTo((focusedIndex + 1) % focusableCount)
      true
    else false

  /** `Shift+Tab`. From the no-focus state this lands on the *last* focusable — stepping backwards out of "nothing"
    * arrives at the end of the tab order, the same way it wraps from the first element.
    */
  def focusPrevious(): Boolean =
    if focusableCount == 0 then false
    else if focusedIndex < 0 then
      focusTo(focusableCount - 1)
      true
    else if focusableCount > 1 then
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
    anchor = FocusAnchor(clampedIndex(index), scala.None)

  /** @see [[PaintedAreas.areaOf]] */
  def areaOf(index: Int): Option[Rect] = painted.areaOf(index)

  /** @see [[PaintedAreas.pointerAreaOf]] */
  def pointerAreaOf(id: Int): Option[Rect] = painted.pointerAreaOf(id)

  /** @see [[PaintedAreas.hitTest]] */
  def hitTest(pos: Position): Option[Int] = painted.hitTest(pos)

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

  /** The first focusable in the tab order that asked for focus with `.autofocus`, if any.
    *
    * "First" rather than "the one", because nothing stops a tree from carrying two — a screen with an autofocusing
    * search box pushed over a form with an autofocusing first field, say. Taking the first in depth-first order makes
    * that case decided rather than arbitrary. (A modal covers the layer below it through [[FocusPass.suppressFocus]],
    * which clears `focusable` on every node underneath, so a covered element's request is not in the tab order at all
    * and cannot win.)
    */
  def autofocusRequest(root: Element): Option[AutofocusRequest] =
    def search(element: Element, index: Int): (Option[AutofocusRequest], Int) =
      val own  =
        if element.props.focusable && element.props.autofocus then Some(AutofocusRequest(index, element.props.focusKey))
        else None
      val next = if element.props.focusable then index + 1 else index
      element.children.foldLeft((own, next)) { case ((found, position), child) =>
        val (childFound, after) = search(child, position)
        (found.orElse(childFound), after)
      }
    search(root, 0)._1

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
