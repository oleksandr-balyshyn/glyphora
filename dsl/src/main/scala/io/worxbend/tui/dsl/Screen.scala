package io.worxbend.tui.dsl

import io.worxbend.tui.runtime.ReactiveScope

/** One entry of the app's screen stack.
  *
  * A *modal* screen renders layered over what's beneath it, with everything below removed from the tab order; a *full*
  * screen replaces the view entirely. Push/pop via `TuiApp.pushScreen`/`popScreen`.
  */
trait Screen:
  def view(using ReactiveScope): Element
  def modal: Boolean = true

object Screen:

  /** A modal screen from a view function (`Screen { dialogElement }`). */
  def apply(element: ReactiveScope ?=> Element): Screen =
    new Screen:
      def view(using ReactiveScope): Element = element

  /** A screen that fully replaces the view beneath it. */
  def full(element: ReactiveScope ?=> Element): Screen =
    new Screen:
      def view(using ReactiveScope): Element = element
      override def modal: Boolean            = false

/** Severity of a [[TuiApp.notify]] toast; picks the theme style it renders with. */
enum ToastLevel:
  case Info, Success, Warning, Error

/** An intro shown before the first view render: `content` (typically a `bigText` logo composition) plays `effect` and
  * holds for at least `minimumDuration`; any key skips it. Wire via `TuiApp.splash`.
  */
final case class SplashScreen(
    content: Element,
    effect: io.worxbend.tui.runtime.Effect,
    minimumDuration: scala.concurrent.duration.FiniteDuration = scala.concurrent.duration.DurationInt(1500).millis,
)
