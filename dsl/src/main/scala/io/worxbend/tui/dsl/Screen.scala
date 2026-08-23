package io.worxbend.tui.dsl

import io.worxbend.tui.core.Effect
import io.worxbend.tui.runtime.ReactiveScope

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** How a pushed [[Screen]] relates to the view underneath it. */
enum Presentation:

  /** Renders layered over what is beneath it, with everything below removed from the tab order — a dialog. */
  case Modal

  /** Replaces the view beneath it entirely — a page. */
  case Full

/** One entry of the app's screen stack. See [[Presentation]] for how it sits over the view beneath; push and pop via
  * `TuiApp.pushScreen`/`popScreen`.
  */
trait Screen:
  def view(using ReactiveScope): Element
  def presentation: Presentation = Presentation.Modal

object Screen:

  /** A modal screen from a view function (`Screen { dialogElement }`). */
  def apply(element: ReactiveScope ?=> Element): Screen =
    new Screen:
      def view(using ReactiveScope): Element = element

  /** A screen that fully replaces the view beneath it. */
  def full(element: ReactiveScope ?=> Element): Screen =
    new Screen:
      def view(using ReactiveScope): Element  = element
      override def presentation: Presentation = Presentation.Full

/** An intro shown before the first view render: `content` (typically a `bigText` logo composition) plays `effect` and
  * holds for at least `minimumDuration`; any key skips it. Wire via `TuiApp.splash`.
  */
final case class SplashScreen(
    content: Element,
    effect: Effect,
    minimumDuration: FiniteDuration = 1500.millis,
)
