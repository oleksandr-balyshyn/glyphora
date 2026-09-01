package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyEvent, MouseEvent, MouseEventKind, Rect}

/** Event routing.
  *
  * With a focused element present, a key event starts at the focused element and bubbles up its ancestor chain — at
  * each node the user's `onKeyEvent` runs first, then the framework's built-in behavior (editing, toggling); a `true`
  * result consumes the event. With no focusable elements the tree is walked depth-first, leaves before ancestors, with
  * the same stop-propagation contract.
  *
  * A mouse event is delivered along the chain of elements that actually rendered under the pointer, innermost first:
  * the handler-carrying descendants of the hit focusable, then the hit focusable itself, then its ancestors. At each
  * node the user's `onMouseEvent` runs first and the element's built-in behavior second. Each handler sees a given
  * event at most once — `false` means keep bubbling outward, never deliver again — and an element the pointer is not
  * over is never offered the event at all.
  *
  * A built-in that declines only reaches an enclosing control's built-in for the wheel ([[reachesOuterBuiltin]]), so a
  * wheel over a button inside a scroll view still scrolls it while a drag stays with the element it landed on.
  *
  * Where overlapping *handler-carrying* siblings compete, the last-painted one wins: containers paint children in
  * order, so the position filter walks them in reverse and the pointer resolves to the topmost subtree covering it.
  * Focusables follow the same rule by a different route: `FocusTracker` stamps every area it records with the paint
  * sequence it was recorded at, and `FocusTracker.hitTest` returns the last-painted focusable covering the pointer.
  *
  * A subtree marked `props.inert` — every layer a modal or the command palette covers — is skipped by all four walks:
  * it receives no event and supplies neither a focus path nor a hit-test path.
  */
private[dsl] object EventRouter:

  def dispatchKey(root: Element, event: KeyEvent): Boolean =
    pathToFocused(root) match
      case Some(leafToRoot) => leafToRoot.exists(handlesKey(_, event))
      case None             => dispatchKeyDepthFirst(root, event)

  /** Routes a mouse event to the elements that rendered under the pointer, innermost first.
    *
    * The chain starts at the [[MouseHit]]'s handler-carrying descendants that also cover the pointer, then the hit
    * element itself, then its ancestors; with no hit it is the deepest covered path in the tree. Each element on it is
    * offered the user's `onMouseEvent` and then its own built-in behavior, so an unconsumed event keeps travelling
    * outward until something takes it. An element that did not render under the pointer is never offered the event, and
    * no element is offered the same event twice.
    */
  def dispatchMouse(root: Element, event: MouseEvent, hit: Option[MouseHit]): Boolean =
    chainUnder(root, event, hit).exists(handlesMouse(_, event, hit))

  /** The elements the event travels through, innermost first. */
  private def chainUnder(root: Element, event: MouseEvent, hit: Option[MouseHit]): List[Element] =
    hit.flatMap(found => pathToTracked(root, found.focusIndex)) match
      case Some(leafToRoot) => insideOf(leafToRoot.head, event) ::: leafToRoot
      case None             => pathUnder(root, event).getOrElse(Nil)

  /** The handler-carrying descendants of the hit element that also cover the pointer, innermost first. */
  private def insideOf(leaf: Element, event: MouseEvent): List[Element] =
    deepestUnder(leaf.children, event).getOrElse(Nil)

  /** The covered path through the topmost of these sibling subtrees that covers the pointer.
    *
    * Every container paints its children in order — [[LayersElement]] most visibly, where later children paint over
    * earlier ones — so the search runs back to front and a click lands on what the user can actually see rather than on
    * whatever the topmost layer is drawn over.
    */
  private def deepestUnder(children: Seq[Element], event: MouseEvent): Option[List[Element]] =
    children.reverseIterator.map(pathUnder(_, event)).collectFirst { case Some(path) => path }

  /** The innermost element in this subtree whose recorded area covers the pointer, plus its ancestors up to `element`,
    * innermost first. Elements that recorded no area are not candidates and carry no mouse handler either — the
    * decoration pass records an area for every element that does.
    */
  private def pathUnder(element: Element, event: MouseEvent): Option[List[Element]] =
    if element.props.inert then None
    else
      val deeper = deepestUnder(element.children, event)
      val covers = coveredArea(element, event).isDefined
      deeper match
        case Some(path) => Some(if covers then path :+ element else path)
        case None       => Option.when(covers)(List(element))

  /** Where this element rendered last frame, for the two wrappers the decoration pass adds. */
  private def recordedArea(element: Element): Option[Rect] =
    element match
      case tracked: TrackedElement => tracked.tracker.areaOf(tracked.index)
      case pointer: PointerElement => pointer.tracker.pointerAreaOf(pointer.pointerId)
      case _                       => None

  /** Where this element rendered last frame, if the pointer is inside it. */
  private def coveredArea(element: Element, event: MouseEvent): Option[Rect] =
    recordedArea(element).filter(_.contains(event.position))

  /** The user's `onMouseEvent` first, then the framework's own behavior for this element. */
  private def handlesMouse(element: Element, event: MouseEvent, hit: Option[MouseHit]): Boolean =
    element.props.onMouse.exists(_(event)) ||
      builtinArea(element, event, hit).exists(area => element.builtinMouseHandler.exists(_(event, area)))

  /** The area a built-in behavior runs against, and the gate on whether it runs at all.
    *
    * On the hit element that is the area the hit resolved to. On an *outer* tracked element it is that element's own
    * recorded area, only while the pointer is inside it, and only for a wheel event — see [[reachesOuterBuiltin]].
    */
  private def builtinArea(element: Element, event: MouseEvent, hit: Option[MouseHit]): Option[Rect] =
    element match
      case tracked: TrackedElement =>
        hit
          .collect { case MouseHit(index, area) if index == tracked.index => area }
          .orElse(if reachesOuterBuiltin(event.kind) then coveredArea(tracked, event) else scala.None)
      case _                       => scala.None

  /** Whether a built-in that declined this event may hand it to an enclosing control's built-in.
    *
    * Only the wheel does. Scrolling is the one gesture whose target is the nearest *scrollable* ancestor rather than
    * the innermost thing under the pointer, so a wheel over a button inside a scroll view has to reach the scroll view
    * — that is the whole reason this fallback exists.
    *
    * A press or a drag must not: an outer control's built-in reads the pointer against its own whole area, so
    * `SplitPaneElement` would move its divider for a drag anywhere in either pane. That was harmless only while
    * built-ins ran on the hit element alone, which for a splitPane meant the pointer was over no smaller focusable —
    * effectively the divider or dead space. Drag-selecting inside a `textInput` in a pane must not yank the divider.
    */
  private def reachesOuterBuiltin(kind: MouseEventKind): Boolean =
    kind match
      case MouseEventKind.ScrollUp | MouseEventKind.ScrollDown | MouseEventKind.ScrollLeft |
          MouseEventKind.ScrollRight =>
        true
      case _ => false

  private def pathToTracked(element: Element, index: Int): Option[List[Element]] =
    if element.props.inert then None
    else
      element match
        case tracked: TrackedElement if tracked.index == index => Some(List(tracked))
        case _                                                 =>
          element.children
            .to(LazyList)
            .map(pathToTracked(_, index))
            .collectFirst { case Some(path) => path :+ element }

  /** Routes a key *release* to the focused element and its ancestors, innermost first.
    *
    * Three deliberate differences from [[dispatchKey]], all of them the same decision: a release is not a press.
    *   - Only the user's own `.onKeyRelease` handler is offered. No built-in behaviour fires, because every built-in in
    *     the library is written against a press and would run twice for one keystroke.
    *   - There is no depth-first fallback for a tree with nothing focusable. A release with no focused element has
    *     nowhere sensible to go, and offering it to every node would fire a handler for a key the user pressed while
    *     something else entirely had the keyboard.
    *   - The caller does not fall back to the application's bindings — see `TuiApp`. A binding is a press vocabulary,
    *     and firing it on the way up would run every chord twice.
    */
  def dispatchKeyRelease(root: Element, event: KeyEvent): Boolean =
    pathToFocused(root) match
      case Some(leafToRoot) => leafToRoot.exists(_.props.onKeyUp.exists(_(event)))
      case None             => false

  /** Delivers a bracketed paste to the focused element's paste behavior. */
  def dispatchPaste(root: Element, text: String): Boolean =
    pathToFocused(root) match
      case Some(leafToRoot) => leafToRoot.head.builtinPasteHandler.exists(_(text))
      case None             => false

  /** The user's `onKeyEvent` first, then the framework's own behavior for this element.
    *
    * A built-in only runs on the *focused* element. Both key walks offer the event to elements that are not focused —
    * the ancestors an unconsumed key bubbles through, and every node of the depth-first walk when nothing is focusable
    * at all — and a built-in firing there would mean typing into an unfocused text input, or one arrow key moving two
    * nested lists. The user's own handler is deliberately not gated: `onKeyEvent` on a container is how an app takes
    * keys that no focused descendant wanted.
    */
  private def handlesKey(element: Element, event: KeyEvent): Boolean =
    element.props.onKey.exists(_(event)) ||
      (element.props.focused && element.builtinKeyHandler.exists(_(event)))

  private def dispatchKeyDepthFirst(element: Element, event: KeyEvent): Boolean =
    !element.props.inert &&
      (element.children.exists(dispatchKeyDepthFirst(_, event)) || handlesKey(element, event))

  /** The focused element and its ancestors, innermost first. */
  private def pathToFocused(element: Element): Option[List[Element]] =
    if element.props.inert then None
    else if element.props.focused && element.props.focusable then Some(List(element))
    else
      element.children
        .to(LazyList)
        .map(pathToFocused)
        .collectFirst { case Some(path) => path :+ element }
