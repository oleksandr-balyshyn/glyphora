package io.worxbend.tui.terminal

/** Shared scaffolding for the specs that drive `InputDecoder` from a fixed script of code units.
  *
  * `InputDecoder`'s read function reports "nothing available" with any negative value (see its Scaladoc); these
  * fixtures use [[NothingAvailable]] so a suite never has to re-pick one.
  */
private[terminal] trait DecoderFixtures:

  /** The value the scripted read function returns once the script is exhausted, standing in for a timed-out read. */
  protected final val NothingAvailable: Int = -2

  /** A decoder fed from a fixed script of code units; reads past the end report a timeout. */
  protected def decoderFor(chars: Int*): InputDecoder =
    val iterator = chars.iterator
    InputDecoder(_ => if iterator.hasNext then iterator.next() else NothingAvailable)

  /** The bytes of `CSI <body>`, as a terminal would send them. */
  protected def csi(body: String): Seq[Int] = 0x1b +: '['.toInt +: body.map(_.toInt)
