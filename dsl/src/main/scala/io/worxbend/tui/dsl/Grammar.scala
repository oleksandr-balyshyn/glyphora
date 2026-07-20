package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyCode, KeyEvent, KeyModifiers, Style}

/** Named key constants and constructors, so handlers read `onKey(Key.Up){ … }` instead of matching raw
  * `KeyEvent(KeyCode.Up, _)`. Mirrors terminus's `Key.up` / `Key.controlQ` vocabulary.
  */
object Key:

  val Up: KeyEvent        = KeyEvent.of(KeyCode.Up)
  val Down: KeyEvent      = KeyEvent.of(KeyCode.Down)
  val Left: KeyEvent      = KeyEvent.of(KeyCode.Left)
  val Right: KeyEvent     = KeyEvent.of(KeyCode.Right)
  val Enter: KeyEvent     = KeyEvent.of(KeyCode.Enter)
  val Escape: KeyEvent    = KeyEvent.of(KeyCode.Escape)
  val Tab: KeyEvent       = KeyEvent.of(KeyCode.Tab)
  val BackTab: KeyEvent   = KeyEvent(KeyCode.Tab, KeyModifiers.Shift)
  val Backspace: KeyEvent = KeyEvent.of(KeyCode.Backspace)
  val Delete: KeyEvent    = KeyEvent.of(KeyCode.Delete)
  val Insert: KeyEvent    = KeyEvent.of(KeyCode.Insert)
  val Home: KeyEvent      = KeyEvent.of(KeyCode.Home)
  val End: KeyEvent       = KeyEvent.of(KeyCode.End)
  val PageUp: KeyEvent    = KeyEvent.of(KeyCode.PageUp)
  val PageDown: KeyEvent  = KeyEvent.of(KeyCode.PageDown)
  val Space: KeyEvent     = KeyEvent.char(' ')

  /** A bare printable character with no modifiers. */
  def char(c: Char): KeyEvent = KeyEvent.char(c)

  /** A function key, `f(1)` … `f(12)`. */
  def f(n: Int): KeyEvent = KeyEvent.of(KeyCode.F(n))

  /** `Ctrl`+letter (e.g. `Key.ctrl('s')`). */
  def ctrl(c: Char): KeyEvent = KeyEvent(KeyCode.Char(c), KeyModifiers.Ctrl)

  /** `Alt`/Option+letter. */
  def alt(c: Char): KeyEvent = KeyEvent(KeyCode.Char(c), KeyModifiers.Alt)

  /** `Shift`+a named key. */
  def shift(code: KeyCode): KeyEvent = KeyEvent(code, KeyModifiers.Shift)

  val CtrlC: KeyEvent = ctrl('c')
  val CtrlS: KeyEvent = ctrl('s')
  val CtrlP: KeyEvent = ctrl('p')
  val CtrlQ: KeyEvent = ctrl('q')
  val CtrlD: KeyEvent = ctrl('d')

/** Ergonomic key handlers that hide the `true`/`false` stop-propagation ceremony.
  *
  * `onKey` binds an action to one or more keys; it consumes the event only when a bound key matches, delegating
  * anything else to a handler already on the element — so several `.onKey(…)` calls compose instead of overwriting each
  * other. Use [[Element.onKeyEvent]] directly when a handler needs the raw event or conditional consumption.
  */
extension (element: Element)

  def onKey(keys: KeyEvent*)(handler: => Unit): Element =
    val previous = element.props.onKey
    element.withProps(
      element.props.copy(onKey = Some { event =>
        if keys.contains(event) then
          handler
          true
        else previous.exists(_(event))
      })
    )

/** Pushes a default style onto a whole subtree (terminus's auto-restoring `foreground.green { … }`, as a retained
  * transform): every style-aware descendant renders with `transform(...)` as its base, with any style the node set
  * itself layered on top. Style-ignoring leaves (raw `widget(...)`, images) are unaffected.
  */
def withStyle(transform: Style => Style)(inner: Element): Element =
  val base                          = transform(Style.Default)
  def apply(node: Element): Element =
    val restyled = node.withProps(node.props.copy(style = base.patch(node.props.style)))
    restyled.withChildren(restyled.children.map(apply))
  apply(inner)
