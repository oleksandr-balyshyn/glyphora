package io.worxbend.tui.core

/** Where a run of text may be broken onto the next row.
  *
  * [[CharWidth]] owns the question "how many terminal columns does this take"; this object owns the orthogonal question
  * "is a break allowed here". They are separate because two characters that look identical on screen can answer them
  * differently: U+0020 SPACE and U+00A0 NO-BREAK SPACE both draw one blank column, but a wrapper may break on the first
  * and must never break on the second (NO-BREAK SPACE is what "10 kg" or "Fig. 3" is written with precisely so the two
  * halves stay on one row).
  *
  * The classification is deliberately Unicode-aware rather than a `c == ' '` test:
  *   - `java.lang.Character.isWhitespace` already answers `false` for U+00A0 NO-BREAK SPACE, U+2007 FIGURE SPACE and
  *     U+202F NARROW NO-BREAK SPACE, which is exactly the behaviour a wrapper wants, and `true` for the ideographic
  *     space U+3000, the tab and the line separators. [[isBreakingSpace]] is therefore a thin, tested wrapper over it
  *     rather than a hand-written table.
  *   - U+200B ZERO WIDTH SPACE is the opposite case: it is a break opportunity that occupies no column at all (Thai and
  *     Japanese text, and long URLs, use it to say "you may break here"), so a wrapper driven purely by display width
  *     can never notice it. [[isZeroWidthBreak]] makes it visible to the wrapper.
  *
  * Every method takes a *grapheme cluster* — one user-perceived character as produced by [[CharWidth.graphemeClusters]]
  * — not a `Char`, because a cluster may span several code points (an emoji with a skin-tone modifier, a letter with
  * combining marks) and such a cluster is never a break opportunity.
  *
  * Pure and stateless: safe to call from any thread.
  */
object LineBreaks:

  /** U+00A0 NO-BREAK SPACE: draws a blank column but forbids a break. */
  val NoBreakSpace: Char = ' '

  /** U+200B ZERO WIDTH SPACE: draws nothing but permits a break. */
  val ZeroWidthSpace: Char = '​'

  /** True when `cluster` is whitespace a wrapper may break on and then discard.
    *
    * "Discard" is the part that makes this different from "is this blank": the spaces sitting at the point a row was
    * broken are not carried to the start of the next row, so a paragraph wrapped mid-sentence does not gain a stray
    * leading indent. A multi-code-point cluster is never a breaking space, and the three no-break spaces named in the
    * object documentation are never one either.
    */
  def isBreakingSpace(cluster: String): Boolean =
    singleCodePoint(cluster).exists(cp => Character.isWhitespace(cp) && cp != ZeroWidthSpace.toInt)

  /** True when a break is allowed *before* `cluster` without anything being discarded — U+200B ZERO WIDTH SPACE only.
    *
    * A wrapper that meets one ends the current word there and starts the next one; because the character is zero-width,
    * nothing is drawn either way, so it costs no column whether the break is taken or not.
    */
  def isZeroWidthBreak(cluster: String): Boolean =
    singleCodePoint(cluster).contains(ZeroWidthSpace.toInt)

  /** True when a break is allowed *after* `cluster`, because the cluster ends in U+200B ZERO WIDTH SPACE.
    *
    * This is the form a wrapper actually needs. Because the character occupies no column,
    * [[CharWidth.graphemeClusters]] absorbs it into the cluster in front of it, so "a" followed by a zero width space
    * arrives as the single cluster `"a​"` and never as a cluster of its own. Asking "does this cluster end in one"
    * therefore finds break opportunities that [[isZeroWidthBreak]], which only recognises the character standing alone
    * at the very start of a string, would miss.
    */
  def endsWithZeroWidthBreak(cluster: String): Boolean =
    cluster.nonEmpty && cluster.charAt(cluster.length - 1) == ZeroWidthSpace

  /** The single code point of `cluster`, or `None` when the cluster is empty or made of several code points. */
  private def singleCodePoint(cluster: String): Option[Int] =
    if cluster.isEmpty then None
    else
      val first = cluster.codePointAt(0)
      if Character.charCount(first) == cluster.length then Some(first) else None
