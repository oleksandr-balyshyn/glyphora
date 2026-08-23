package io.worxbend.tui.examples.counter

import io.worxbend.tui.dsl.*

/** counter: the smallest app written the way a real one is written — declared key bindings, an app shell, and a status
  * bar that cannot go out of step with the keys.
  *
  * This is the runnable twin of the counter in the README and in the "Getting started" guide, and the idiom to copy
  * once an app has more than a key or two. `hello-world` shows the level below it — a raw `onKeyEvent` handler on a
  * single element — which is the escape hatch for one-off local interaction, not the recommendation.
  */
class CounterApp extends TuiApp:

  val count: Signal[Int] = Signal(0)

  /** Declared once, used four times. The same values dispatch the key events, fill the hints in `statusBar(bindings)`
    * below, populate the fuzzy command palette on `Ctrl+P`, and would fill a `helpOverlay(bindings)` — so there is no
    * second list of keys to keep in step with this one.
    *
    * The specs are parsed and checked here, at declaration time. A key the terminal cannot actually deliver is a
    * compile-and-run-time error rather than a binding that silently never fires: `binding("ctrl+i", …)` is rejected
    * with `'ctrl+i' is indistinguishable from Tab on terminals without the kitty keyboard protocol; bind "tab"
    * instead`.
    */
  override def bindings: KeyBindings = KeyBindings(
    binding("+", "increment")(count.update(_ + 1)),
    binding("-", "decrement")(count.update(_ - 1)),
    binding("r", "reset")(count.set(0)),
    binding("q", "quit")(quit()),
  )

  /** Reading `count.get` here is what subscribes this view to the signal: `count.update` in a binding above marks the
    * view stale and the runtime schedules the next frame. There is no refresh call anywhere in this file.
    */
  def view(using ReactiveScope, Theme): Element =
    scaffold(statusBar = Some(statusBar(bindings))) {
      centered(34, 7) {
        panel("Counter")(
          text(s"Count: ${count.get}").bold.fg(Color.Green),
          spacer,
          text("Change state; the view follows.").dim,
        ).rounded
      }
    }

/** `TuiApp` supplies `main`, so wiring the entry point is naming the app the launcher should start.
  *
  * The split into a class plus a one-line object is what the tests need: `TuiApp` keeps its state on the instance and
  * never resets it between runs, so `CounterAppSpec` builds a fresh `CounterApp()` for each scenario while the launcher
  * still has a single object to start.
  */
object Main extends CounterApp
