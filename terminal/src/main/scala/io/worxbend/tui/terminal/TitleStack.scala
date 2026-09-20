package io.worxbend.tui.terminal

import java.util.concurrent.atomic.AtomicBoolean

/** The XTerm window-title stack as [[JLine3Backend]] uses it: the shell's own title is pushed before the app's first
  * change, and popped when the app closes, so the shell gets its title back.
  *
  * The push is lazy — an app that never sets a title emits nothing at all and leaves no stack entry for [[release]] to
  * pop — and exactly-once in both directions, because this is the one piece of terminal state where doing the work
  * twice is worse than not doing it: a second push leaves an entry nobody pops, and a second pop discards the title of
  * whatever is above us.
  *
  * `emit` is the backend's monitored, fallible write. The flag is atomic because `close()` — and so [[release]] — can
  * be run twice, the shutdown hook racing the runner's teardown.
  */
private[terminal] final class TitleStack(emit: String => Either[BackendError, Unit]):

  private val pushed = AtomicBoolean(false)

  /** Sets the window title, pushing the shell's own title first if that has not happened yet.
    *
    * The push has to happen before the first change, because that is the last moment the stack top is still the title
    * the shell set; `compareAndSet` makes it happen exactly once however many times the app retitles itself.
    */
  def set(title: String): Either[BackendError, Unit] =
    if pushed.compareAndSet(false, true) then
      emit(AnsiSequences.PushTitle).flatMap(_ => emit(AnsiSequences.setTitle(title)))
    else emit(AnsiSequences.setTitle(title))

  /** Pops the pushed title if there is one, answering the failure when the pop itself failed.
    *
    * Only if this backend actually pushed one: popping a stack this app never wrote to would discard someone else's
    * title. `getAndSet` makes a second call — the shutdown hook racing the runner's teardown — pop nothing.
    */
  def release(): Option[BackendError] =
    if pushed.getAndSet(false) then emit(AnsiSequences.PopTitle).left.toOption else None
