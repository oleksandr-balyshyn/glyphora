package io.worxbend.tui.widgets

/** The extra room a bordered, padded popup needs around its content, stated once for every widget that draws one.
  *
  * A popup box is one border cell and one padding cell on each side, so its natural size is its content plus
  * [[ExtraWidth]] columns and [[ExtraHeight]] rows. [[Menu]], [[Tooltip]], [[Dropdown]] and [[Dialog]] all measure with
  * these numbers rather than restating the literals, so the box and the measurement of it cannot drift apart.
  */
private[widgets] object PopupChrome:

  /** Columns beyond the content: one border plus one pad on the left, and the same on the right. */
  val ExtraWidth: Int = 4

  /** Rows beyond the content: the top and bottom borders. */
  val ExtraHeight: Int = 2
