package io.worxbend.tui.examples.helloworld

import io.worxbend.tui.dsl.*

/** hello-world, DSL edition: a static paragraph in a bordered panel. */
class HelloWorld extends TuiApp:

  def view(using ReactiveScope): Element =
    panel("Hello")(
      text("Welcome to glyphora!").bold.fg(Color.Cyan),
      spacer,
      text("Press 'q' to quit").dim,
    ).rounded.onKeyEvent {
      case KeyEvent(KeyCode.Char('q'), _) =>
        quit()
        true
      case _                              => false
    }

/** `TuiApp` supplies `main`, so the entry point is one line naming the app the launcher should start. */
object Main extends HelloWorld
