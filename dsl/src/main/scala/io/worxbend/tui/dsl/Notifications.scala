package io.worxbend.tui.dsl

import io.worxbend.tui.widgets.NoticeLevel

import scala.concurrent.duration.FiniteDuration

/** One application's toast stack, as a value that can be passed around.
  *
  * `TuiApp.notify` is a `protected` method on the app, so only code written inside the app's own body can raise a
  * toast. A helper function or an element factory living in another file — the shape most applications reach for as
  * soon as a view grows past one screen — had to be handed a callback by the app to say anything to the user.
  *
  * This is the same capability expressed as a value. Take it as a `using` parameter and any helper anywhere can notify:
  *
  * {{{
  * def saveRow(row: Row)(using notifications: Notifications): Unit =
  *   repository.save(row)
  *   notifications.success(s"saved ${row.name}")
  * }}}
  *
  * Ownership and threads: an instance is bound to one `TuiApp` and, like everything else that writes a `Signal`, must
  * be called on that app's render thread — which every event handler, timer body and `Async` continuation already is. A
  * toast needs a `config.tickRate`, or an ambient animation, for it to age out again; that is unchanged.
  */
trait Notifications:

  /** Shows `message` in the top-right corner for `duration`. */
  def notify(message: String, level: NoticeLevel, duration: FiniteDuration): Unit

  /** Drops every toast, shown or still queued. */
  def dismissToasts(): Unit

  /** `message` for the default three seconds, in the theme's accent colour. */
  final def info(message: String): Unit = notify(message, NoticeLevel.Info, DefaultToastDuration)

  /** `message` for the default three seconds, in the theme's success colour. */
  final def success(message: String): Unit = notify(message, NoticeLevel.Success, DefaultToastDuration)

  /** `message` for the default three seconds, in the theme's warning colour. */
  final def warn(message: String): Unit = notify(message, NoticeLevel.Warning, DefaultToastDuration)

  /** `message` for the default three seconds, in the theme's error colour. */
  final def error(message: String): Unit = notify(message, NoticeLevel.Error, DefaultToastDuration)
