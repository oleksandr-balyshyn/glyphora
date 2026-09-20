package io.worxbend.tui.core

/** What [[Buffer.diff]] emits into the reserved continuation column of an emoji-presentation cluster — the
  * `clearEmojiTrailingCell` workaround as a named choice rather than a `Boolean`.
  *
  * A cluster containing U+FE0F — the variation selector that asks for a character's colourful emoji form — is two
  * columns wide by the Unicode rules this toolkit measures with, so the buffer reserves the column to its right as a
  * continuation and never emits that column: painting the glyph paints both halves. Several terminals draw such a
  * sequence in a single column anyway, and then the second column is never repainted at all, so whatever stood there in
  * an earlier frame stays on screen next to the emoji.
  *
  * Which policy is right is a backend's decision rather than the buffer's, because the opposite artifact is just as
  * real: on a terminal that does draw both columns, an extra blank lands on the right half of the glyph and clips it.
  * Only the code that knows which terminal it is talking to can pick the lesser of the two.
  */
enum TrailingCellPolicy:

  /** Leave the reserved column unpainted — the default, and the right choice for a terminal that draws both columns of
    * an emoji-presentation cluster: the extra emission would land on the right half of the glyph and clip it.
    */
  case Keep

  /** Emit a blank into the changed cluster's reserved column, carrying the owning cell's style so a background fill
    * stays continuous across the pair — the workaround for a terminal that draws the whole sequence in one column and
    * would otherwise leave the previous frame's pixels showing beside it.
    */
  case Clear
