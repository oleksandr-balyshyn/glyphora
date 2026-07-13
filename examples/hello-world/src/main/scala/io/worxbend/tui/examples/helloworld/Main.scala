package io.worxbend.tui.examples.helloworld

import io.worxbend.tui.dsl.*

/** hello-world, DSL edition: a static paragraph in a bordered panel. */
object HelloWorld extends TuiApp:

  def view(using ReactiveScope): Element =
    panel("Hello")(
      text("Welcome to glyphora!").bold.color(Color.Cyan),
      spacer,
      text("Press 'q' to quit").dim,
    ).rounded.onKeyEvent {
      case KeyEvent(KeyCode.Char('q'), _) =>
        quit()
        true
      case _                              => false
    }

object Main:
  def main(args: Array[String]): Unit =
    HelloWorld.run().left.foreach(error => println(s"failed to run: $error"))
