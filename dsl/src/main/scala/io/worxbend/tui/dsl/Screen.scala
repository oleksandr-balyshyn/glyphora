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

  /** This screen's element tree. Carries the same two contexts as `TuiApp.view` — see [[View]] — so a screen written as
    * its own class still gets the running app's [[Theme]] rather than the library default.
    */
  def view(using ReactiveScope, Theme): Element
  def presentation: Presentation = Presentation.Modal

  /** Runs on the render thread the moment this screen goes on the stack, before the frame that first shows it.
    *
    * This is a screen's own "now I am running", the counterpart of `TuiApp.onStart` for a subtree that comes and goes.
    * Without it, a screen that polls had to arm its poller in the app's `onStart` and cancel it in the app's `onStop`,
    * even though the screen might be popped long before the app exits — so the polling carried on against a screen
    * nobody could see.
    *
    * {{{
    * private var poller: Option[Cancelable] = None
    * override def onEnter(): Unit = poller = Some(Async.every(5.seconds)(refresh()))
    * override def onLeave(): Unit = poller.foreach(_.cancel())
    * }}}
    *
    * A screen pushed twice gets two `onEnter` calls and two matching [[onLeave]] calls.
    */
  def onEnter(): Unit = ()

  /** Runs on the render thread when this screen leaves the stack: popped, replaced, reset away, or still on the stack
    * when the run ends — in which case it runs before `TuiApp.onStop`, so the app's own resources are still alive.
    *
    * Always paired with exactly one [[onEnter]], including on the exit paths a `Ctrl+C` or a handler that threw takes,
    * because the run's teardown is in a `finally`. Cancel here whatever `onEnter` started; nothing else cancels a
    * repeating `Async.every` for you.
    */
  def onLeave(): Unit = ()

object Screen:

  /** A modal screen from a view function (`Screen { dialogElement }`).
    *
    * `onEnter`/`onLeave` are the same hooks the trait declares, for a screen small enough not to want a class of its
    * own: `Screen(detailView, onEnter = () => startPolling(), onLeave = () => stopPolling())`.
    */
  def apply(element: View, onEnter: () => Unit = () => (), onLeave: () => Unit = () => ()): Screen =
    build(element, Presentation.Modal, onEnter, onLeave)

  /** A screen that fully replaces the view beneath it. */
  def full(element: View, onEnter: () => Unit = () => (), onLeave: () => Unit = () => ()): Screen =
    build(element, Presentation.Full, onEnter, onLeave)

  private def build(element: View, how: Presentation, entering: () => Unit, leaving: () => Unit): Screen =
    new Screen:
      def view(using ReactiveScope, Theme): Element = element
      override def presentation: Presentation       = how
      override def onEnter(): Unit                  = entering()
      override def onLeave(): Unit                  = leaving()

/** An intro shown before the first view render: `content` (typically a `bigText` logo composition) plays `effect` and
  * holds for at least `minimumDuration`; any key skips it. Wire via `TuiApp.splash`.
  */
final case class SplashScreen(
    content: Element,
    effect: Effect,
    minimumDuration: FiniteDuration = 1500.millis,
)
