package io.worxbend.tui.examples.helloworld

import io.worxbend.tui.core.{Color, Event, KeyCode, KeyEvent, Style}
import io.worxbend.tui.runtime.{RunnerHandle, TerminalRunner}
import io.worxbend.tui.terminal.JLine3Backend

/** hello-world, immediate-mode edition (PLAN.md §10 step 4): a full-screen render loop over the JLine
  * backend, redrawn on resize, quitting on `q`. The DSL edition replaces this in step 6.
  */
object Main:

  def main(args: Array[String]): Unit =
    JLine3Backend.create() match
      case Left(error) =>
        println(s"no usable terminal: $error")
      case Right(backend) =>
        TerminalRunner(backend)
          .run(handleEvent, render)
          .left
          .foreach(error => println(s"runner failed: $error"))

  private def handleEvent(event: Event, handle: RunnerHandle): Boolean =
    event match
      case Event.Key(KeyEvent(KeyCode.Char('q'), _)) =>
        handle.quit()
        false
      case Event.Resize(_) => true
      case _               => false

  private def render(frame: io.worxbend.tui.runtime.Frame): Unit =
    frame.renderWidget(
      (area, buffer) =>
        buffer.setString(area.x + 2, area.y + 1, "Hello from glyphora!", Style.Default.bold.withFg(Color.Cyan))
        buffer.setString(area.x + 2, area.y + 3, "Press 'q' to quit", Style.Default.dim),
      frame.area,
    )
