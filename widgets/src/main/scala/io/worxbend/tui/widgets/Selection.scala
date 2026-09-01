package io.worxbend.tui.widgets

/** The keyboard-navigation rules every selectable widget in this module follows.
  *
  * Five widgets — [[ListView]], [[DataTable]], [[Tree]], [[DirectoryTree]] and [[Menu]] — let the user walk a highlight
  * up and down with the arrow keys, and all five have to answer the same two questions: what happens at the ends of the
  * list, and what happens when nothing is selected yet. Stating the answers once here is what keeps a change to either
  * rule from having to be found in five separate files.
  *
  * '''There are two answers to the first question, and the split is deliberate.''' The four list-shaped widgets
  * ''clamp'': pressing "down" on the last row stays there. They scroll, so the rows on screen are a window onto a
  * longer list, and jumping from the bottom of that list back to the top moves the highlight somewhere the reader was
  * not looking and cannot see it arrive — it reads as the selection having been lost. [[Menu]] ''wraps'': a popup menu
  * is short, bounded and entirely on screen, so the highlight leaving the last entry and appearing on the first is a
  * move the reader watches happen, and wrapping saves walking back up a menu whose ends are both visible.
  *
  * The wrapping pair also takes a selectability test, because a menu is the only one of the five whose entries can be
  * un-landable — a separator or a disabled item — and navigation has to step over those rather than onto them.
  *
  * '''Which function to reach for.''' The clamping rule comes in two shapes, because the five widgets do not all
  * address a row the same way. [[ListView]] and [[DataTable]] hold an index into the rows they are rendering, so they
  * call [[next]]/[[previous]]. [[Tree]] and [[DirectoryTree]] hold the *thing* a row shows — a node path, a file path —
  * because expanding a branch renumbers every row below it, so they call [[moveWithin]], which clamps by the same rule
  * over a sequence of values instead of a count. [[Menu]] is the wrapping one and calls
  * [[nextSelectable]]/[[previousSelectable]].
  *
  * '''Beyond one step at a time.''' [[first]] and [[last]] are the Home and End answers, and [[by]] is the general
  * clamped move that PageUp and PageDown use — [[next]] and [[previous]] are simply [[by]] with a `delta` of `+1` and
  * `-1`, so there is one place where the clamping rule lives.
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
  def next(current: Option[Int], count: Int): Option[Int] = by(current, count, 1)

  /** The index one step before `current`, clamped at index 0 — the mirror of [[next]], including its behaviour with
    * nothing selected yet and with an empty list.
    */
  def previous(current: Option[Int], count: Int): Option[Int] = by(current, count, -1)

  /** `current` moved `delta` places, clamped to both ends of a list of `count` items — the screenful jump PageUp and
    * PageDown make, and the one owner of the clamping rule that [[next]] and [[previous]] are the one-step cases of.
    *
    * Clamping rather than wrapping, for the reason [[next]] gives: a page jump that ran off the end and reappeared at
    * the top would move the highlight somewhere the reader is not looking. With nothing selected yet the move starts
    * With nothing selected yet the move starts from a sentinel just outside the list — the same trick [[moveWithin]]
    * uses — so that the very first downward move of one place lands on index 0 rather than skipping it, and any
    * upward move stays on index 0. That is what lets [[next]] and [[previous]] be defined as the `delta` of `+1` and
    * `-1` without changing what either of them did. `None` when the list is empty, because there is nothing to point
    * at.
    */
  def by(current: Option[Int], count: Int, delta: Int): Option[Int] =
    if count <= 0 then None
    else
      val noSelectionStart = if delta > 0 then -1 else 0
      Some(math.max(0, math.min(current.getOrElse(noSelectionStart) + delta, count - 1)))

  /** The first index of a list of `count` items — the Home key's answer, and `None` when the list is empty. */
  def first(count: Int): Option[Int] =
    if count <= 0 then None else Some(0)

  /** The last index of a list of `count` items — the End key's answer, and `None` when the list is empty. */
  def last(count: Int): Option[Int] =
    if count <= 0 then None else Some(count - 1)

  /** The next index after `current` that `isSelectable` accepts, wrapping past the end — the popup-menu rule.
    *
    * Searches at most one full lap, so a list with nothing selectable at all answers `None` rather than spinning. With
    * nothing selected yet the search starts from index 0, so the first move lands on the first selectable entry after
    * it. `None` also when `count` is zero, because there is nothing to point at.
    */
  def nextSelectable(current: Option[Int], count: Int, isSelectable: Int => Boolean): Option[Int] =
    wrapping(current, count, 1, isSelectable)

  /** The nearest selectable index before `current`, wrapping past the start — the mirror of [[nextSelectable]]. */
  def previousSelectable(current: Option[Int], count: Int, isSelectable: Int => Boolean): Option[Int] =
    wrapping(current, count, -1, isSelectable)

  private def wrapping(current: Option[Int], count: Int, delta: Int, isSelectable: Int => Boolean): Option[Int] =
    if count <= 0 then None
    else
      val start = current.getOrElse(0)
      (1 to count).iterator.map(step => math.floorMod(start + delta * step, count)).find(isSelectable)

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
