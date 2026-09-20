package io.worxbend.tui.runtime

import java.util.Locale

import scala.concurrent.duration.FiniteDuration

private val SecondsPerHour: Long   = 3600L
private val SecondsPerMinute: Long = 60L

/** `hh:mm:ss` (or `mm:ss` under an hour) for a non-negative duration — the usual readout for [[Stopwatch]]/[[Timer]].
  */
def formatDuration(duration: FiniteDuration): String =
  val totalSeconds = math.max(0L, duration.toSeconds)
  val hours        = totalSeconds / SecondsPerHour
  val minutes      = totalSeconds % SecondsPerHour / SecondsPerMinute
  val seconds      = totalSeconds % SecondsPerMinute
  // `String.format(Locale.ROOT, ...)`, not the `f` interpolator: `f"%02d"` formats through the default FORMAT
  // locale, which substitutes non-ASCII digits and a non-'0' pad character under some locales — see
  // core.KeyEvent.keyCodeFor for the same hazard on `toLowerCase`. This is published `tui-runtime` API
  // (Stopwatch.formatted/Timer.formatted), so its output must not depend on the machine it runs on.
  if hours > 0 then String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
  else String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
