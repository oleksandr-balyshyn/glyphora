package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyCode, KeyEvent, MouseEventKind, Text, Widget}
import io.worxbend.tui.widgets as w

/** A labelled checkbox. Space/Enter (or a click) flips it while focused.
  *
  * The node holds the *value* the view read, not the signal behind it: the factory does the tracked read, so the view's
  * scope subscribes and any writer at all — a background load, another control — repaints the checkbox. `onChange`
  * carries the new value back to whoever owns the state.
  */
final case class CheckboxElement(
    label: String,
    checked: Boolean,
    onChange: Boolean => Unit,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = CheckboxElement
  def widget: Widget                                               = w.Checkbox(label, checked, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): CheckboxElement = copy(props = props)
  private[dsl] override def claim: SizeClaim                       = SizeClaim.OneRow
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]     =
    Some(toggleOnActivate(() => onChange(!checked)))
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(clickActivates(() => onChange(!checked)))

/** A labelled on/off switch — a [[CheckboxElement]] in switch clothing, with the same Space/Enter/click activation. */
final case class ToggleElement(
    label: String,
    on: Boolean,
    onChange: Boolean => Unit,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = ToggleElement
  def widget: Widget                                                         = w.Toggle(label, on, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): ToggleElement             = copy(props = props)
  private[dsl] override def claim: SizeClaim                                 = SizeClaim.OneRow
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]     =
    Some(toggleOnActivate(() => onChange(!on)))
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(clickActivates(() => onChange(!on)))

/** A one-row option cycler. Left/Right step through `options` while focused (wrapping at both ends) and a click
  * advances one; the selection is an index into `options`, so it survives nothing but a stable option list.
  */
final case class SelectElement(
    options: Seq[String],
    selected: Int,
    onSelect: Int => Unit,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = SelectElement
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(clickActivates(() => if options.nonEmpty then onSelect((selected + 1) % options.size)))
  def widget: Widget = w.Select(options, selected, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): SelectElement             = copy(props = props)
  private[dsl] override def claim: SizeClaim                                 = SizeClaim.OneRow
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]     =
    Some(stepsWrapping(options.size, selected, onSelect))

/** Mutually exclusive options: Up/Down move the selection while focused. */
final case class RadioGroupElement(
    options: Seq[String],
    selected: Int,
    onSelect: Int => Unit,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = RadioGroupElement
  def widget: Widget = w.RadioGroup(options, selected, props.style, focusStyled(props).bold)
  private[dsl] def withProps(props: ElementProps): RadioGroupElement     = copy(props = props)
  private[dsl] override def claim: SizeClaim                             = SizeClaim.rows(math.max(1, options.size))
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler] =
    if options.isEmpty then None
    else
      Some(
        keys {
          case KeyEvent(KeyCode.Down, _) => onSelect(math.min(selected + 1, options.size - 1))
          case KeyEvent(KeyCode.Up, _)   => onSelect(math.max(selected - 1, 0))
        }
      )

/** A value slider: Left/Right adjust by `range.step`, Home/End jump to the bounds, while focused.
  *
  * The bounds and the step arrive together as a [[io.worxbend.tui.widgets.SliderRange]]. That is what keeps the key
  * handler honest: it consumes Left/Right by construction, so a zero step would swallow both arrows and do nothing with
  * them, and a negative one would reverse them.
  */
final case class SliderElement(
    value: Int,
    onChange: Int => Unit,
    range: w.SliderRange = w.SliderRange.Percent,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = SliderElement
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some { (event, area) =>
      event.kind match
        case MouseEventKind.Down | MouseEventKind.Drag =>
          if area.width > 3 then
            val fraction = (event.position.x - area.x - 1).toDouble / (area.width - 3)
            onChange(range.min + math.round(math.max(0.0, math.min(1.0, fraction)) * (range.max - range.min)).toInt)
          true
        case _                                         => false
    }
  def widget: Widget = w.Slider(value, range, props.style, focusStyled(props).bold)
  private[dsl] def withProps(props: ElementProps): SliderElement             = copy(props = props)
  private[dsl] override def claim: SizeClaim                                 = SizeClaim.OneRow
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]     =
    Some(
      keys {
        case KeyEvent(KeyCode.Left, _)  => onChange(math.max(range.min, value - range.step))
        case KeyEvent(KeyCode.Right, _) => onChange(math.min(range.max, value + range.step))
        case KeyEvent(KeyCode.Home, _)  => onChange(range.min)
        case KeyEvent(KeyCode.End, _)   => onChange(range.max)
      }
    )

/** A page indicator: Left/Right change the page while focused. */
final case class PaginatorElement(
    current: Int,
    total: Int,
    onChange: Int => Unit,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = PaginatorElement
  def widget: Widget = w.Paginator(current, total, props.style, focusStyled(props).bold)
  private[dsl] def withProps(props: ElementProps): PaginatorElement      = copy(props = props)
  private[dsl] override def claim: SizeClaim                             = SizeClaim.OneRow
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler] =
    if total == 0 then None
    else
      Some(
        keys {
          case KeyEvent(KeyCode.Left, _)  => onChange(math.max(0, current - 1))
          case KeyEvent(KeyCode.Right, _) => onChange(math.min(total - 1, current + 1))
        }
      )

/** A pressable button: Enter or Space triggers `action` while focused. */
final case class ButtonElement(
    label: String,
    action: () => Unit,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = ButtonElement
  def widget: Widget                                                         = w.Button(label, focusStyled(props))
  private[dsl] def withProps(props: ElementProps): ButtonElement             = copy(props = props)
  private[dsl] override def claim: SizeClaim                                 = SizeClaim.OneRow
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler]     = Some(toggleOnActivate(action))
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] = Some(clickActivates(action))

/** A dialog that owns its keys: Left/Right (and Tab) move between the buttons, Space or Enter presses the selected one,
  * Esc cancels.
  *
  * [[Element.dialog]] draws the same picture and answers no keys at all — its own documentation calls it "a picture of
  * a dialog, not a controller". That left every application writing the same three things by hand for a "really quit?":
  * a selected-index signal, the Left/Right/Enter/Esc wiring, and the modal [[Screen]] to push it on. This node is the
  * controller half; [[Screen.confirm]] is the whole thing, selection state included.
  *
  * Selection is caller-owned, like every other control in this package: the node holds the index the view read and an
  * `onSelect` to carry a new one back. `onPress` is handed the index of the button that was pressed — button 0 is the
  * confirming one by convention, because that is the order the labels are given in.
  */
final case class ConfirmDialogElement(
    title: String,
    message: String,
    buttons: Seq[String],
    selected: Int,
    onSelect: Int => Unit,
    onPress: Int => Unit,
    onCancel: () => Unit,
    props: ElementProps = ElementProps(focusable = true),
) extends Element:
  type Self = ConfirmDialogElement

  def widget: Widget =
    w.Dialog(title, Text.raw(message), buttons, selected, props.style, props.focusStyle)

  private[dsl] def withProps(props: ElementProps): ConfirmDialogElement = copy(props = props)

  /** Esc and Tab first, then Space/Enter to press, then Left/Right to move.
    *
    * The order is the layering rule the rest of this file follows: this node's own keys, then the shared vocabulary it
    * falls through to. Tab is bound here rather than left to the app's focus traversal because a modal dialog is
    * normally the only focusable on screen, where Tab would otherwise do nothing at all.
    */
  private[dsl] override def builtinKeyHandler: Option[BuiltinKeyHandler] =
    Some(
      keys {
        case KeyEvent(KeyCode.Escape, _) => onCancel()
        case KeyEvent(KeyCode.Tab, _)    => onSelect(nextIndex)
      }
        .orElse(toggleOnActivate(() => onPress(selected)))
        .orElse(stepsWrapping(buttons.size, selected, onSelect))
    )

  /** A click presses the selected button rather than the button under the pointer: the widget centres its labels and
    * reports no per-button geometry, so there is nothing to hit-test against. Keyboard selection then a click is the
    * gesture this supports; clicking a specific button needs geometry the widget does not publish.
    */
  private[dsl] override def builtinMouseHandler: Option[BuiltinMouseHandler] =
    Some(clickActivates(() => onPress(selected)))

  private def nextIndex: Int = if buttons.isEmpty then 0 else (selected + 1) % buttons.size
