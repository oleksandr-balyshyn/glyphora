package io.worxbend.tui.core

/** One grapheme cluster together with the [[Style]] it is drawn in — the smallest unit a terminal cell can hold.
  *
  * A grapheme cluster is what a reader thinks of as one character: a base code point plus whatever attaches to it, so
  * `e` followed by a combining acute accent is one cluster, and so is a multi-code-point emoji. Stepping through text
  * this way rather than character by character is what keeps an accent from being separated from its letter.
  *
  * The style is already fully resolved: whatever base style the enclosing widget supplies has been layered under the
  * span's own with [[Style.patch]], so a consumer never has to resolve anything itself.
  */
final case class StyledGrapheme(cluster: String, style: Style):

  /** Terminal columns this cluster occupies: none for a combining mark, two for a wide character or emoji, else one.
    *
    * Measured through [[CharWidth.of]], the arbitrary-text entry point, rather than the single-cluster one, so a value
    * built by hand out of more than one cluster still measures correctly instead of counting only its first.
    */
  def width: Int = CharWidth.of(cluster)
