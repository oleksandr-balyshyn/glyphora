package io.worxbend.tui.dsl

import io.worxbend.tui.core.Effect
import io.worxbend.tui.runtime.{ReactiveScope, Signal}

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

  /** The keys this screen declares for as long as it is the top of the app's stack, in the same form as
    * `TuiApp.bindings` — see [[binding]].
    *
    * Before this existed, a screen that wanted its own shortcut had two unhappy options. Putting it on a root element
    * handler made it fire from wherever the tree was showing, including screens it had nothing to do with; putting it
    * in `TuiApp.bindings` made it a permanent app key that had to test the navigation depth itself before deciding
    * whether it meant anything. Declaring it here scopes it: `TuiApp` merges these over the app's own bindings while
    * this screen is on top, and they are gone the moment it is popped.
    *
    * They are merged *first*, so a screen key shadows an app key that answers to the same spec — `KeyBindings.handle`
    * runs the first binding that matches. They feed the status-bar hints, the help overlay and the command palette too,
    * through `TuiApp.activeBindings`, so the chrome advertises exactly the keys that will actually fire.
    */
  def bindings: KeyBindings = KeyBindings.empty

  /** A short human-readable name for this screen — "Settings", "Confirm delete" — or `None` when it has none.
    *
    * The library never draws this by itself. It exists so an application can build its own chrome from the stack:
    * `TuiApp.screenLabels` collects the named screens outermost-first, which is exactly the sequence a breadcrumb or a
    * title bar wants. Deriving the name from the class instead is not an option here — that would mean runtime
    * reflection, which this library refuses to depend on so that native images need no reflection configuration.
    */
  def label: Option[String] = None

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
    *
    * `label` names the screen for the application's own breadcrumbs and title bars — see [[Screen.label]]; leaving it
    * empty means the screen is unnamed.
    *
    * `keys` declares the shortcuts that exist only while this screen is on top — see [[Screen.bindings]] — so a dialog
    * can own its `Esc` without the app having to test the navigation depth:
    * `Screen(body, keys = KeyBindings(binding("esc", "close")(popScreen())))`.
    */
  def apply(
      element: View,
      onEnter: () => Unit = () => (),
      onLeave: () => Unit = () => (),
      keys: KeyBindings = KeyBindings.empty,
      label: String = "",
  ): Screen =
    build(element, Presentation.Modal, onEnter, onLeave, keys, label)

  /** A ready-made modal "are you sure?": Left/Right (and Tab) move between the two buttons, Space or Enter presses the
    * selected one, Esc cancels.
    *
    * The screen owns the selection state, which is the last thing an application had to write by hand for this. Before
    * it, a confirmation meant a `Signal` for the selected index, the Left/Right/Enter/Esc wiring, and a `Screen` to
    * hold them, repeated per confirmation.
    *
    * Neither callback pops the screen: what should happen after a confirmation is the application's business, and a
    * screen that popped itself would take away the choice. A confirmation that closes reads
    * `pushScreen(Screen.confirm("Quit", "Discard unsaved changes?")({ popScreen(); quit() }, popScreen()))`. Both
    * callbacks are by-name, so nothing runs when the screen is built.
    *
    * @param confirmLabel
    *   the first button, and the one selected when the screen opens.
    */
  def confirm(
      title: String,
      message: String,
      confirmLabel: String = "OK",
      cancelLabel: String = "Cancel",
  )(onConfirm: => Unit, onCancel: => Unit): Screen =
    new Screen:
      private val selected                                        = Signal(0)
      private val labels                                          = Seq(confirmLabel, cancelLabel)
      def view(using scope: ReactiveScope, theme: Theme): Element =
        Element.confirmDialog(title, message, labels, selected.get)(
          index => selected.set(index),
          index => if index == 0 then onConfirm else onCancel,
          () => onCancel,
        )

  /** A screen that fully replaces the view beneath it. Takes the same `onEnter`/`onLeave` hooks and screen-scoped
    * `keys` as the modal form above.
    */
  def full(
      element: View,
      onEnter: () => Unit = () => (),
      onLeave: () => Unit = () => (),
      keys: KeyBindings = KeyBindings.empty,
      label: String = "",
  ): Screen =
    build(element, Presentation.Full, onEnter, onLeave, keys, label)

  private def build(
      element: View,
      how: Presentation,
      entering: () => Unit,
      leaving: () => Unit,
      keys: KeyBindings,
      name: String,
  ): Screen =
    new Screen:
      def view(using ReactiveScope, Theme): Element = element
      override def presentation: Presentation       = how
      override def onEnter(): Unit                  = entering()
      override def onLeave(): Unit                  = leaving()
      override def bindings: KeyBindings            = keys
      // an empty string is the "no name given" spelling: the parameter has to have a default, and `Option[String]` as
      // a parameter type would make every labelled call site write `Some(...)` for nothing
      override def label: Option[String]            = Option(name).filter(_.nonEmpty)

/** An intro shown before the first view render: `content` (typically a `bigText` logo composition) plays `effect` and
  * holds for at least `minimumDuration`; any key skips it. Wire via `TuiApp.splash`.
  */
final case class SplashScreen(
    content: Element,
    effect: Effect,
    minimumDuration: FiniteDuration = 1500.millis,
)
