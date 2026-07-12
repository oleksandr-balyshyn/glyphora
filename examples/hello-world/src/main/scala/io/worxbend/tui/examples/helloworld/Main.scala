package io.worxbend.tui.examples.helloworld

import io.worxbend.tui.terminal.JLine3Backend

/** Placeholder main exercising the JLine backend end-to-end (also the native-image spike target, PLAN.md §12).
  * Replaced by the real hello-world render-loop example in step 4.
  */
object Main:
  def main(args: Array[String]): Unit =
    JLine3Backend.create() match
      case Right(backend) =>
        println(s"terminal size: ${backend.size}")
        backend.close()
      case Left(error) =>
        println(s"no usable terminal: $error")
