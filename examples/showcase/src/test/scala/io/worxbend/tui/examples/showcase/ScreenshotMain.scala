package io.worxbend.tui.examples.showcase

import io.worxbend.tui.core.{KeyCode, Size}
import io.worxbend.tui.terminal.HeadlessBackend
import io.worxbend.tui.testsupport.Pilot

/** Renders the showcase headlessly and prints the frame as plain text — the README "screenshot" source. Run with
  * `./mill examples.showcase.test.runMain io.worxbend.tui.examples.showcase.ScreenshotMain [w] [h]`.
  */
object ScreenshotMain:

  def main(args: Array[String]): Unit =
    val width   = args.headOption.flatMap(_.toIntOption).getOrElse(72)
    val height  = args.lift(1).flatMap(_.toIntOption).getOrElse(18)
    val backend = HeadlessBackend(Size(width, height))
    val app     = ShowcaseApp()
    val pilot   = Pilot.start(backend) { val _ = app.runWith(backend) }
    pilot.waitForIdle()
    pilot.pressKey(KeyCode.Enter).waitForIdle() // skip the splash
    pilot.typeText("ship it").waitForIdle()
    println(pilot.screenLines.mkString("\n"))
    pilot.pressKey(KeyCode.Escape)
    val _ = pilot.awaitTermination()
