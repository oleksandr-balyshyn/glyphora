package io.worxbend.tui.widgets

/** What a widget does with content that does not fit the width it is given — used by [[Paragraph]] for its text and by
  * [[Notice]] for its message.
  *
  * This is a named choice rather than a `wrap: Boolean` because the two spellings read the same at a call site
  * (`Paragraph(text, wrap = false)` and `Paragraph(text, Overflow.Clip)` are the same picture) but only one of them
  * survives a reader who has never seen the parameter list: `true` says nothing about which behaviour is on.
  */
enum Overflow:

  /** Draw one row per line of content and cut anything past the right edge. The choice for a status line, a table cell,
    * a badge — anywhere a growing block of text would push the rest of the layout around.
    */
  case Clip

  /** Break content onto further rows so nothing is lost. The break lands between words: a word that does not fit moves
    * to the next row whole, and the blanks that sat at the break are dropped rather than becoming a leading indent.
    * Blanks at the start of a line are content and are kept, so indentation survives. Only a word longer than the whole
    * width is broken inside, and then between grapheme clusters, so a wide character or emoji is never split.
    *
    * The widget then needs more rows than it has lines of content, which is what
    * [[io.worxbend.tui.core.Measured.heightAt]] reports.
    */
  case Wrap
