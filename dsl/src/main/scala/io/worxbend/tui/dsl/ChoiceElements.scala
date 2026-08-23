package io.worxbend.tui.dsl

import io.worxbend.tui.core.{KeyCode, KeyEvent, MouseEventKind, Widget}
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
