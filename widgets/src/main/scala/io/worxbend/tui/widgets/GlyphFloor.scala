package io.worxbend.tui.widgets

/** The one test the glyph catalogues share when they degrade themselves to a lower
  * [[io.worxbend.tui.core.GlyphSupport]] rung.
  *
  * Asking "is every glyph in this preset already ASCII?" rather than keeping a hand-written list of the safe presets: a
  * list has to be updated every time a preset is added, and the day it is not, a braille spinner survives a degradation
  * it was never meant to survive. The question is cheap and it cannot go stale.
  */
private[widgets] object GlyphFloor:

  /** Whether `text` is nothing but printable ASCII — no code point above U+007E, and no control characters either. */
  def isAscii(text: String): Boolean = text.forall(c => c >= ' ' && c <= '~')

  /** Whether every one of `texts` passes [[isAscii]]. */
  def allAscii(texts: Iterable[String]): Boolean = texts.forall(isAscii)
