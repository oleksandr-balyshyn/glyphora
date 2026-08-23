package io.worxbend.tui.examples.counter

import io.worxbend.tui.dsl.*

/** counter: a stateful app with keybindings — the event loop, the signal-update → re-render cycle, and the
  * render-thread model, end to end.
  */
class CounterApp extends TuiApp:

  val count: Signal[Int] = Signal(0)

  def view(using ReactiveScope): Element =
    panel("Counter")(
      text(s"Count: ${count.get}").bold.fg(Color.Green),
      spacer,
      text("'+' increment · '-' decrement · 'q' quit").dim,
    ).rounded
      // named keys + `onKey` hide the `KeyEvent`/`true`/`false` ceremony; the calls compose
      .onKey(Key.char('+')) { count.update(_ + 1) }
      .onKey(Key.char('-')) { count.update(_ - 1) }
      .onKey(Key.char('q')) { quit() }

/** `TuiApp` supplies `main`, so wiring the entry point is naming the app the launcher should start. */
object Main extends CounterApp
