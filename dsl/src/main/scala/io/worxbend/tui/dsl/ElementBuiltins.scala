package io.worxbend.tui.dsl

import io.worxbend.tui.core.{Constraint, Direction, KeyCode, KeyEvent, MouseEventKind, Style}
import io.worxbend.tui.runtime.Signal
import io.worxbend.tui.widgets as w

// ---- the shared built-in vocabulary ----
//
// The small combinators every element reuses for its layout claim, its focus styling, and the framework key and
// mouse behavior it performs while focused. They own no state of their own — each closes over state the *element*
// (and therefore the app) owns — and they are called only from `builtinKeyHandler`/`builtinMouseHandler`, so they run
// on the render thread like every other part of a frame.

/** How far PageUp/PageDown move a scrollable, in rows. One place to change the page step for every scrollable. */
private val PageStep = 10

/** The divider bounds and keyboard step of every [[SplitPaneElement]]: the split is never driven past these, so neither
  * pane can be collapsed away entirely by a drag or by a held `[`/`]`.
  */
private val MinSplitPercent = 10
private val MaxSplitPercent = 90
private val SplitStep       = 5

private def clampSplit(percent: Int): Int = math.max(MinSplitPercent, math.min(MaxSplitPercent, percent))

/** Cells a [[PanelElement]]'s border takes off the inner area along one axis — one on each side. */
private val PanelBorderCells = 2

/** What a one-line control claims from its container: exactly one row when stacked vertically, whatever width is going
  * when laid out horizontally.
  */
private def singleRow(direction: Direction): Constraint =
  direction match
    case Direction.Vertical   => Constraint.Length(1)
    case Direction.Horizontal => Constraint.Fill(1)

/** Focused interactive elements render with the focus style (the theme's, once the focus pass ran) layered over their
  * own, so the user can see where keystrokes go.
  */
private def focusStyled(props: ElementProps): Style =
  if props.focused then props.style.patch(props.focusStyle) else props.style

/** A mouse press activates the control (focus already moved on the press). */
private def clickActivates(activate: () => Unit): BuiltinMouseHandler =
  (event, _) =>
    if event.kind == MouseEventKind.Down then
      activate()
      true
    else false

/** Wheel events scroll by one step. */
private def wheelScrolls(up: () => Unit, down: () => Unit): BuiltinMouseHandler =
  (event, _) =>
    event.kind match
      case MouseEventKind.ScrollUp   =>
        up()
        true
      case MouseEventKind.ScrollDown =>
        down()
        true
      case _                         => false

/** Space/Enter activates a two-state control. Only reached while the element is focused — [[EventRouter]] gates every
  * built-in key handler on that.
  */
private def toggleOnActivate(activate: () => Unit): KeyEvent => Boolean =
  event =>
    event.code match
      case KeyCode.Char(' ') | KeyCode.Enter =>
        activate()
        true
      case _                                 => false

/** The selection key vocabulary shared by every element with a moving highlight (lists, trees, menus, tables): Up/Down
  * move the selection one entry. `next`/`previous` do the moving on the caller-owned state. Anything else is left
  * unconsumed so an element can layer its own keys on top and fall through to this.
  */
private def selectionKeys(next: () => Unit, previous: () => Unit): KeyEvent => Boolean =
  event =>
    event.code match
      case KeyCode.Down =>
        next()
        true
      case KeyCode.Up   =>
        previous()
        true
      case _            => false

/** The scrolling key vocabulary shared by every viewport-shaped element: Up/Down move one row, PageUp/PageDown move
  * [[PageStep]] rows. `up`/`down` are handed the row count to move and do the scrolling on the caller-owned state;
  * anything else is left unconsumed so it keeps bubbling.
  */
private def scrollKeys(up: Int => Unit, down: Int => Unit): KeyEvent => Boolean =
  event =>
    event.code match
      case KeyCode.Up       =>
        up(1)
        true
      case KeyCode.Down     =>
        down(1)
        true
      case KeyCode.PageUp   =>
        up(PageStep)
        true
      case KeyCode.PageDown =>
        down(PageStep)
        true
      case _                => false

/** The caret and erase key vocabulary shared by every single-line text field: Backspace/Delete erase either side of the
  * caret, Left/Right/Home/End move it. `state` is the caller-owned editing state the keys mutate; anything else is left
  * unconsumed, which is what lets a field layer its own `Char` handling on top and fall through to this.
  */
private def cursorKeys(state: w.TextInputState): KeyEvent => Boolean =
  event =>
    event.code match
      case KeyCode.Backspace =>
        state.backspace()
        true
      case KeyCode.Delete    =>
        state.delete()
        true
      case KeyCode.Left      =>
        state.moveLeft()
        true
      case KeyCode.Right     =>
        state.moveRight()
        true
      case KeyCode.Home      =>
        state.moveHome()
        true
      case KeyCode.End       =>
        state.moveEnd()
        true
      case _                 => false

/** Left/Right step `index` through `size` positions, wrapping at both ends. `size` is by-name because it is derived
  * from the element's current contents, and nothing is consumed when there is nothing to step through — an empty option
  * list or a tab bar with no pages leaves the arrows free to bubble.
  */
private def stepsWrapping(size: => Int, index: Signal[Int]): KeyEvent => Boolean =
  event =>
    val count = size
    if count == 0 then false
    else
      event.code match
        case KeyCode.Left  =>
          index.update(current => (current - 1 + count) % count)
          true
        case KeyCode.Right =>
          index.update(current => (current + 1) % count)
          true
        case _             => false
