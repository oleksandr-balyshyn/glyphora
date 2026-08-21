package io.worxbend.tui.widgets

/** The keyboard-navigation rule every selectable widget in this module follows.
  *
  * Four widgets — [[ListView]], [[DataTable]], [[Tree]] and [[DirectoryTree]] — let the user walk a highlight up and
  * down with the arrow keys, and all four have to answer the same two questions: what happens at the ends of the list,
  * and what happens when nothing is selected yet. Stating the answers once here is what keeps a change to either rule
  * from having to be found in four separate files.
  *
  * These are pure functions of the values passed in. The selection itself lives in caller-owned state, which the render
  * thread reads, so the same thread constraint the widgets carry applies to whoever stores the result.
  */
private[widgets] object Selection:

  /** The index one step after `current` in a list of `count` items.
    *
    * Clamped at the last item rather than wrapping round to the first: a highlight that jumps from the bottom of a long
    * list back to the top loses the reader's place. With nothing selected yet the first move lands on index 0, so
    * pressing "down" into a fresh list selects its first row. `None` when the list is empty, because there is nothing
    * to point at.
    */
  def next(current: Option[Int], count: Int): Option[Int] =
    if count <= 0 then None else Some(current.fold(0)(index => math.min(index + 1, count - 1)))

  /** The index one step before `current`, clamped at index 0 — the mirror of [[next]], including its behaviour with
    * nothing selected yet and with an empty list.
    */
  def previous(current: Option[Int], count: Int): Option[Int] =
    if count <= 0 then None else Some(current.fold(0)(index => math.max(index - 1, 0)))

  /** `current` moved by `delta` places within `items`, addressed by value rather than by index.
    *
    * The tree widgets identify a row by the thing it shows — a node path, a file path — rather than by its position,
    * because expanding a branch renumbers everything below it. A `current` that is no longer in `items` (its branch was
    * collapsed, its file was deleted) is treated as no selection at all, so the move starts from one end: a downward
    * move lands on the first item, an upward move on the last one it can reach. `None` when `items` is empty.
    */
  def moveWithin[A](items: Seq[A], current: Option[A], delta: Int): Option[A] =
    if items.isEmpty then None
    else
      // a sentinel just outside the list, so the first move in either direction lands on index 0
      val noSelectionStart = if delta > 0 then -1 else 1
      val currentIndex     = current.map(items.indexOf).filter(_ >= 0).getOrElse(noSelectionStart)
      val nextIndex        = math.max(0, math.min(currentIndex + delta, items.size - 1))
      Some(items(nextIndex))
