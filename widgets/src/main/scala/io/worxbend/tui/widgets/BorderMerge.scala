package io.worxbend.tui.widgets

/** The weight of one of the four arms running out of a box-drawing glyph.
  *
  * `Nothing` means the glyph draws no line in that direction at all — the arm is absent, not thin. Rounded corners
  * (`╭╮╯╰`) carry `Plain` arms, because rounding changes only how the corner is drawn, not the weight of the lines
  * meeting there.
  */
private[widgets] enum LineStyle:
  case Nothing, Plain, Thick, Double

/** Combines two box-drawing glyphs into the single glyph that shows both, so touching frames join instead of stacking.
  *
  * The problem it solves: two panels laid side by side both draw on the shared column. Writing the second panel's left
  * wall over the first panel's right wall gives one straight wall, but the corners never join — the seam shows `┐` over
  * `┘` where the eye expects `┬` over `┴`. [[Block]] routes its border writes through here when it is given a
  * [[MergeStrategy]] other than `Replace`.
  *
  * How it works: every box-drawing glyph is a statement about four arms — the line weight running right, up, left and
  * down out of the cell. `┌` is "plain right, plain down, nothing up, nothing left"; `─` is "plain right, plain left".
  * Merging decodes both glyphs to their [[Arms]], takes the stronger arm in each direction, and looks the resulting
  * shape back up in the reverse table. `┌` merged onto `─` is "plain right, plain left, plain down" — which is `┬`.
  *
  * A glyph the table does not know — a letter from a title, a space, a shading block — is not a border, so merging
  * gives up and returns the incoming glyph unchanged. That is what keeps titles and content painting over borders
  * normally.
  *
  * The table is keyed by whole single-column glyph strings and is only ever consulted with a whole [[Cell]] symbol, so
  * no display-width or substring arithmetic happens here at all.
  *
  * Pure values, no state: safe to call from any thread.
  */
private[widgets] object BorderMerge:

  /** One glyph's four arm weights, in the order right, up, left, down. */
  final case class Arms(right: LineStyle, up: LineStyle, left: LineStyle, down: LineStyle)

  import LineStyle.*

  /** Glyph to arms, derived from the Unicode names of the box-drawing block (`BOX DRAWINGS LIGHT DOWN AND RIGHT` is
    * `┌`, plain down and plain right). Dashed, diagonal and half-block glyphs are deliberately absent: they have no
    * meaningful junction with anything.
    */
  private val decode: Map[String, Arms] = Map(
    "─" -> Arms(Plain, Nothing, Plain, Nothing),   // LIGHT HORIZONTAL
    "━" -> Arms(Thick, Nothing, Thick, Nothing),   // HEAVY HORIZONTAL
    "│" -> Arms(Nothing, Plain, Nothing, Plain),   // LIGHT VERTICAL
    "┃" -> Arms(Nothing, Thick, Nothing, Thick),   // HEAVY VERTICAL
    "┌" -> Arms(Plain, Nothing, Nothing, Plain),   // LIGHT DOWN AND RIGHT
    "┍" -> Arms(Thick, Nothing, Nothing, Plain),   // DOWN LIGHT AND RIGHT HEAVY
    "┎" -> Arms(Plain, Nothing, Nothing, Thick),   // DOWN HEAVY AND RIGHT LIGHT
    "┏" -> Arms(Thick, Nothing, Nothing, Thick),   // HEAVY DOWN AND RIGHT
    "┐" -> Arms(Nothing, Nothing, Plain, Plain),   // LIGHT DOWN AND LEFT
    "┑" -> Arms(Nothing, Nothing, Thick, Plain),   // DOWN LIGHT AND LEFT HEAVY
    "┒" -> Arms(Nothing, Nothing, Plain, Thick),   // DOWN HEAVY AND LEFT LIGHT
    "┓" -> Arms(Nothing, Nothing, Thick, Thick),   // HEAVY DOWN AND LEFT
    "└" -> Arms(Plain, Plain, Nothing, Nothing),   // LIGHT UP AND RIGHT
    "┕" -> Arms(Thick, Plain, Nothing, Nothing),   // UP LIGHT AND RIGHT HEAVY
    "┖" -> Arms(Plain, Thick, Nothing, Nothing),   // UP HEAVY AND RIGHT LIGHT
    "┗" -> Arms(Thick, Thick, Nothing, Nothing),   // HEAVY UP AND RIGHT
    "┘" -> Arms(Nothing, Plain, Plain, Nothing),   // LIGHT UP AND LEFT
    "┙" -> Arms(Nothing, Plain, Thick, Nothing),   // UP LIGHT AND LEFT HEAVY
    "┚" -> Arms(Nothing, Thick, Plain, Nothing),   // UP HEAVY AND LEFT LIGHT
    "┛" -> Arms(Nothing, Thick, Thick, Nothing),   // HEAVY UP AND LEFT
    "├" -> Arms(Plain, Plain, Nothing, Plain),     // LIGHT VERTICAL AND RIGHT
    "┝" -> Arms(Thick, Plain, Nothing, Plain),     // VERTICAL LIGHT AND RIGHT HEAVY
    "┞" -> Arms(Plain, Thick, Nothing, Plain),     // UP HEAVY AND RIGHT DOWN LIGHT
    "┟" -> Arms(Plain, Plain, Nothing, Thick),     // DOWN HEAVY AND RIGHT UP LIGHT
    "┠" -> Arms(Plain, Thick, Nothing, Thick),     // VERTICAL HEAVY AND RIGHT LIGHT
    "┡" -> Arms(Thick, Thick, Nothing, Plain),     // DOWN LIGHT AND RIGHT UP HEAVY
    "┢" -> Arms(Thick, Plain, Nothing, Thick),     // UP LIGHT AND RIGHT DOWN HEAVY
    "┣" -> Arms(Thick, Thick, Nothing, Thick),     // HEAVY VERTICAL AND RIGHT
    "┤" -> Arms(Nothing, Plain, Plain, Plain),     // LIGHT VERTICAL AND LEFT
    "┥" -> Arms(Nothing, Plain, Thick, Plain),     // VERTICAL LIGHT AND LEFT HEAVY
    "┦" -> Arms(Nothing, Thick, Plain, Plain),     // UP HEAVY AND LEFT DOWN LIGHT
    "┧" -> Arms(Nothing, Plain, Plain, Thick),     // DOWN HEAVY AND LEFT UP LIGHT
    "┨" -> Arms(Nothing, Thick, Plain, Thick),     // VERTICAL HEAVY AND LEFT LIGHT
    "┩" -> Arms(Nothing, Thick, Thick, Plain),     // DOWN LIGHT AND LEFT UP HEAVY
    "┪" -> Arms(Nothing, Plain, Thick, Thick),     // UP LIGHT AND LEFT DOWN HEAVY
    "┫" -> Arms(Nothing, Thick, Thick, Thick),     // HEAVY VERTICAL AND LEFT
    "┬" -> Arms(Plain, Nothing, Plain, Plain),     // LIGHT DOWN AND HORIZONTAL
    "┭" -> Arms(Plain, Nothing, Thick, Plain),     // LEFT HEAVY AND RIGHT DOWN LIGHT
    "┮" -> Arms(Thick, Nothing, Plain, Plain),     // RIGHT HEAVY AND LEFT DOWN LIGHT
    "┯" -> Arms(Thick, Nothing, Thick, Plain),     // DOWN LIGHT AND HORIZONTAL HEAVY
    "┰" -> Arms(Plain, Nothing, Plain, Thick),     // DOWN HEAVY AND HORIZONTAL LIGHT
    "┱" -> Arms(Plain, Nothing, Thick, Thick),     // RIGHT LIGHT AND LEFT DOWN HEAVY
    "┲" -> Arms(Thick, Nothing, Plain, Thick),     // LEFT LIGHT AND RIGHT DOWN HEAVY
    "┳" -> Arms(Thick, Nothing, Thick, Thick),     // HEAVY DOWN AND HORIZONTAL
    "┴" -> Arms(Plain, Plain, Plain, Nothing),     // LIGHT UP AND HORIZONTAL
    "┵" -> Arms(Plain, Plain, Thick, Nothing),     // LEFT HEAVY AND RIGHT UP LIGHT
    "┶" -> Arms(Thick, Plain, Plain, Nothing),     // RIGHT HEAVY AND LEFT UP LIGHT
    "┷" -> Arms(Thick, Plain, Thick, Nothing),     // UP LIGHT AND HORIZONTAL HEAVY
    "┸" -> Arms(Plain, Thick, Plain, Nothing),     // UP HEAVY AND HORIZONTAL LIGHT
    "┹" -> Arms(Plain, Thick, Thick, Nothing),     // RIGHT LIGHT AND LEFT UP HEAVY
    "┺" -> Arms(Thick, Thick, Plain, Nothing),     // LEFT LIGHT AND RIGHT UP HEAVY
    "┻" -> Arms(Thick, Thick, Thick, Nothing),     // HEAVY UP AND HORIZONTAL
    "┼" -> Arms(Plain, Plain, Plain, Plain),       // LIGHT VERTICAL AND HORIZONTAL
    "┽" -> Arms(Plain, Plain, Thick, Plain),       // LEFT HEAVY AND RIGHT VERTICAL LIGHT
    "┾" -> Arms(Thick, Plain, Plain, Plain),       // RIGHT HEAVY AND LEFT VERTICAL LIGHT
    "┿" -> Arms(Thick, Plain, Thick, Plain),       // VERTICAL LIGHT AND HORIZONTAL HEAVY
    "╀" -> Arms(Plain, Thick, Plain, Plain),       // UP HEAVY AND DOWN HORIZONTAL LIGHT
    "╁" -> Arms(Plain, Plain, Plain, Thick),       // DOWN HEAVY AND UP HORIZONTAL LIGHT
    "╂" -> Arms(Plain, Thick, Plain, Thick),       // VERTICAL HEAVY AND HORIZONTAL LIGHT
    "╃" -> Arms(Plain, Thick, Thick, Plain),       // LEFT UP HEAVY AND RIGHT DOWN LIGHT
    "╄" -> Arms(Thick, Thick, Plain, Plain),       // RIGHT UP HEAVY AND LEFT DOWN LIGHT
    "╅" -> Arms(Plain, Plain, Thick, Thick),       // LEFT DOWN HEAVY AND RIGHT UP LIGHT
    "╆" -> Arms(Thick, Plain, Plain, Thick),       // RIGHT DOWN HEAVY AND LEFT UP LIGHT
    "╇" -> Arms(Thick, Thick, Thick, Plain),       // DOWN LIGHT AND UP HORIZONTAL HEAVY
    "╈" -> Arms(Thick, Plain, Thick, Thick),       // UP LIGHT AND DOWN HORIZONTAL HEAVY
    "╉" -> Arms(Plain, Thick, Thick, Thick),       // RIGHT LIGHT AND LEFT VERTICAL HEAVY
    "╊" -> Arms(Thick, Thick, Plain, Thick),       // LEFT LIGHT AND RIGHT VERTICAL HEAVY
    "╋" -> Arms(Thick, Thick, Thick, Thick),       // HEAVY VERTICAL AND HORIZONTAL
    "═" -> Arms(Double, Nothing, Double, Nothing), // DOUBLE HORIZONTAL
    "║" -> Arms(Nothing, Double, Nothing, Double), // DOUBLE VERTICAL
    "╒" -> Arms(Double, Nothing, Nothing, Plain),  // DOWN SINGLE AND RIGHT DOUBLE
    "╓" -> Arms(Plain, Nothing, Nothing, Double),  // DOWN DOUBLE AND RIGHT SINGLE
    "╔" -> Arms(Double, Nothing, Nothing, Double), // DOUBLE DOWN AND RIGHT
    "╕" -> Arms(Nothing, Nothing, Double, Plain),  // DOWN SINGLE AND LEFT DOUBLE
    "╖" -> Arms(Nothing, Nothing, Plain, Double),  // DOWN DOUBLE AND LEFT SINGLE
    "╗" -> Arms(Nothing, Nothing, Double, Double), // DOUBLE DOWN AND LEFT
    "╘" -> Arms(Double, Plain, Nothing, Nothing),  // UP SINGLE AND RIGHT DOUBLE
    "╙" -> Arms(Plain, Double, Nothing, Nothing),  // UP DOUBLE AND RIGHT SINGLE
    "╚" -> Arms(Double, Double, Nothing, Nothing), // DOUBLE UP AND RIGHT
    "╛" -> Arms(Nothing, Plain, Double, Nothing),  // UP SINGLE AND LEFT DOUBLE
    "╜" -> Arms(Nothing, Double, Plain, Nothing),  // UP DOUBLE AND LEFT SINGLE
    "╝" -> Arms(Nothing, Double, Double, Nothing), // DOUBLE UP AND LEFT
    "╞" -> Arms(Double, Plain, Nothing, Plain),    // VERTICAL SINGLE AND RIGHT DOUBLE
    "╟" -> Arms(Plain, Double, Nothing, Double),   // VERTICAL DOUBLE AND RIGHT SINGLE
    "╠" -> Arms(Double, Double, Nothing, Double),  // DOUBLE VERTICAL AND RIGHT
    "╡" -> Arms(Nothing, Plain, Double, Plain),    // VERTICAL SINGLE AND LEFT DOUBLE
    "╢" -> Arms(Nothing, Double, Plain, Double),   // VERTICAL DOUBLE AND LEFT SINGLE
    "╣" -> Arms(Nothing, Double, Double, Double),  // DOUBLE VERTICAL AND LEFT
    "╤" -> Arms(Double, Nothing, Double, Plain),   // DOWN SINGLE AND HORIZONTAL DOUBLE
    "╥" -> Arms(Plain, Nothing, Plain, Double),    // DOWN DOUBLE AND HORIZONTAL SINGLE
    "╦" -> Arms(Double, Nothing, Double, Double),  // DOUBLE DOWN AND HORIZONTAL
    "╧" -> Arms(Double, Plain, Double, Nothing),   // UP SINGLE AND HORIZONTAL DOUBLE
    "╨" -> Arms(Plain, Double, Plain, Nothing),    // UP DOUBLE AND HORIZONTAL SINGLE
    "╩" -> Arms(Double, Double, Double, Nothing),  // DOUBLE UP AND HORIZONTAL
    "╪" -> Arms(Double, Plain, Double, Plain),     // VERTICAL SINGLE AND HORIZONTAL DOUBLE
    "╫" -> Arms(Plain, Double, Plain, Double),     // VERTICAL DOUBLE AND HORIZONTAL SINGLE
    "╬" -> Arms(Double, Double, Double, Double),   // DOUBLE VERTICAL AND HORIZONTAL
    "╭" -> Arms(Plain, Nothing, Nothing, Plain),   // LIGHT ARC DOWN AND RIGHT [arc]
    "╮" -> Arms(Nothing, Nothing, Plain, Plain),   // LIGHT ARC DOWN AND LEFT [arc]
    "╯" -> Arms(Nothing, Plain, Plain, Nothing),   // LIGHT ARC UP AND LEFT [arc]
    "╰" -> Arms(Plain, Plain, Nothing, Nothing),   // LIGHT ARC UP AND RIGHT [arc]
    "╴" -> Arms(Nothing, Nothing, Plain, Nothing), // LIGHT LEFT
    "╵" -> Arms(Nothing, Plain, Nothing, Nothing), // LIGHT UP
    "╶" -> Arms(Plain, Nothing, Nothing, Nothing), // LIGHT RIGHT
    "╷" -> Arms(Nothing, Nothing, Nothing, Plain), // LIGHT DOWN
    "╸" -> Arms(Nothing, Nothing, Thick, Nothing), // HEAVY LEFT
    "╹" -> Arms(Nothing, Thick, Nothing, Nothing), // HEAVY UP
    "╺" -> Arms(Thick, Nothing, Nothing, Nothing), // HEAVY RIGHT
    "╻" -> Arms(Nothing, Nothing, Nothing, Thick), // HEAVY DOWN
    "╼" -> Arms(Thick, Nothing, Plain, Nothing),   // LIGHT LEFT AND HEAVY RIGHT
    "╽" -> Arms(Nothing, Plain, Nothing, Thick),   // LIGHT UP AND HEAVY DOWN
    "╾" -> Arms(Plain, Nothing, Thick, Nothing),   // HEAVY LEFT AND LIGHT RIGHT
    "╿" -> Arms(Nothing, Thick, Nothing, Plain),   // HEAVY UP AND LIGHT DOWN
  )

  /** The four rounded corners, excluded from [[encode]]. */
  private val Rounded: Set[String] = Set("╭", "╮", "╯", "╰")

  /** Arms back to a glyph. Built from [[decode]] rather than written twice, so the two can never disagree.
    *
    * The rounded corners are dropped on the way in: they decode to exactly the same arms as the square corners, and a
    * merged junction has to pick one. Square wins, because a rounded corner that has grown a third arm is no longer a
    * corner — `╭` merged with a line above it is a `┬`, and there is no rounded `┬` in Unicode to reach for.
    */
  private val encode: Map[Arms, String] =
    decode.toSeq.filterNot((glyph, _) => Rounded.contains(glyph)).map((glyph, arms) => arms -> glyph).toMap

  /** The glyph to write at a cell that already holds `existing` when the border wants to draw `incoming` there.
    *
    * Returns `incoming` unchanged whenever merging cannot say anything better: under `MergeStrategy.Replace`, when
    * either glyph is not a box-drawing character, or when the combined shape has no glyph of its own.
    */
  def merge(existing: String, incoming: String, strategy: MergeStrategy): String =
    strategy match
      case MergeStrategy.Replace => incoming
      case _                     =>
        val combined =
          for
            under <- decode.get(existing)
            over  <- decode.get(incoming)
          yield combine(under, over)
        combined.flatMap(arms => lookUp(arms, strategy)).getOrElse(incoming)

  /** The combined shape's glyph, or `None` when Unicode has none.
    *
    * `Fuzzy` gets a second attempt with every double arm weakened to a single one: Unicode has no glyph for a double
    * line crossing a heavy one, so `═` meeting `┃` has no exact answer at all, and `╂` (the heavy cross, one weight
    * lighter on the horizontal) is a far better frame than leaving the two walls unjoined.
    */
  private def lookUp(arms: Arms, strategy: MergeStrategy): Option[String] =
    encode.get(arms).orElse(if strategy == MergeStrategy.Fuzzy then encode.get(weaken(arms)) else None)

  /** Arm-wise combination: an absent arm loses to a present one, and two present arms of different weights resolve to
    * the incoming glyph's weight, so drawing a thick panel over a plain one reads as thick.
    */
  private def combine(under: Arms, over: Arms): Arms =
    Arms(
      combineArm(under.right, over.right),
      combineArm(under.up, over.up),
      combineArm(under.left, over.left),
      combineArm(under.down, over.down),
    )

  private def combineArm(under: LineStyle, over: LineStyle): LineStyle =
    if over == Nothing then under else over

  private def weaken(arms: Arms): Arms =
    Arms(weakenArm(arms.right), weakenArm(arms.up), weakenArm(arms.left), weakenArm(arms.down))

  private def weakenArm(style: LineStyle): LineStyle =
    if style == Double then Plain else style

  /** How many glyphs the table can decode, and how many distinct shapes they encode. Equal unless a row is duplicated,
    * which is the one mistake a hand-maintained lookup table makes silently — the rounded corners are the deliberate
    * exception and are excluded from the reverse table. Exposed for that test and nothing else.
    */
  private[widgets] def glyphCount: Int = decode.size - Rounded.size

  private[widgets] def shapeCount: Int = encode.size
