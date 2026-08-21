package io.worxbend.tui.runtime

import scala.concurrent.duration.FiniteDuration

/** How far along a fixed-length animation is, as a fraction in `[0, 1]` before easing is applied.
  *
  * Shared by [[Tween]] and the timed [[Effect]]s so that the two agree on the answer — in particular on what a
  * zero-length animation means, which is the one case plain division cannot express.
  */
private[runtime] object Progress:

  /** `elapsed / total` as a fraction, with a zero-length animation reading as finished rather than dividing by zero.
    *
    * The result is deliberately *not* clamped: an `elapsed` past `total` returns a value above 1, and callers that care
    * hand it to `Easing.apply`, which clamps into `[0, 1]` itself.
    */
  def normalized(elapsed: FiniteDuration, total: FiniteDuration): Double =
    if total.toNanos == 0 then 1.0 else elapsed.toNanos.toDouble / total.toNanos
