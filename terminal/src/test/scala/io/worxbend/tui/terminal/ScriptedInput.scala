package io.worxbend.tui.terminal

/** Feeding [[InputDecoder]] a fixed script of characters, the way every decoder suite needs to.
  *
  * These three things — the sentinel a read function returns when nothing is available, the decoder built over an
  * iterator of character codes, and the two escape-sequence builders — were written out separately in six suites that
  * could not see each other. They are one vocabulary: what a byte script *is*. A suite's own higher-level wrappers
  * (`decoded`, `dropped`, `replay`) stay where they are, because those encode what that suite is asking, not how a
  * script is spelled.
  */
private[terminal] object ScriptedInput:

  /** What the decoder's read function returns when no character is available.
    *
    * Any negative value means "nothing arrived"; naming it once keeps a suite from writing `-1` — which is also
    * [[InputDecoder]]'s own "no pushback" marker — and wondering why the script behaves oddly.
    */
  val NothingAvailable: Int = -2

  /** The escape character every sequence below starts with. */
  val Esc: Int = 0x1b

  /** A decoder fed from a fixed script of character codes; reads past the end report a timeout. */
  def decoder(chars: Int*): InputDecoder =
    val iterator = chars.iterator
    InputDecoder(_ => if iterator.hasNext then iterator.next() else NothingAvailable)

  /** The character codes of the CSI sequence `ESC [ body`. */
  def csi(body: String): Seq[Int] = Esc +: '['.toInt +: body.map(_.toInt)

  /** The character codes of the SS3 sequence `ESC O body`. */
  def ss3(body: String): Seq[Int] = Esc +: 'O'.toInt +: body.map(_.toInt)
