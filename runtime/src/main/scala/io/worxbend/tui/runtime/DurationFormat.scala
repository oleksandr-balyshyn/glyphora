package io.worxbend.tui.runtime

import scala.concurrent.duration.FiniteDuration

/** `hh:mm:ss` (or `mm:ss` under an hour) for a non-negative duration — the usual readout for [[Stopwatch]]/[[Timer]].
  */
def formatDuration(duration: FiniteDuration): String =
  val totalSeconds = math.max(0L, duration.toSeconds)
  val hours        = totalSeconds / 3600
  val minutes      = totalSeconds % 3600 / 60
  val seconds      = totalSeconds % 60
  if hours > 0 then f"$hours%d:$minutes%02d:$seconds%02d" else f"$minutes%02d:$seconds%02d"
