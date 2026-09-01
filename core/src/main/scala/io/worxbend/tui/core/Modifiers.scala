package io.worxbend.tui.core

/** Text-attribute flags (bold, italic, …) packed into an `Int` bitset.
  *
  * An opaque bitset rather than a `Set[Modifier]` on purpose: `Style` values are created per-cell, potentially
  * thousands of times per frame, so `Style` must stay a small value with no boxed collection inside.
  */
opaque type Modifiers = Int

object Modifiers:
  val None: Modifiers       = 0
  val Bold: Modifiers       = 1 << 0
  val Dim: Modifiers        = 1 << 1
  val Italic: Modifiers     = 1 << 2
  val Underline: Modifiers  = 1 << 3
  val Blink: Modifiers      = 1 << 4
  val Reverse: Modifiers    = 1 << 5
  val Hidden: Modifiers     = 1 << 6
  val CrossedOut: Modifiers = 1 << 7

  /** Every flag paired with its name, in bit order. The single table both [[names]] and [[show]] read, so a new flag is
    * added in one place rather than in one place per rendering.
    */
  private val Named: Seq[(Modifiers, String)] = Seq(
    Bold       -> "Bold",
    Dim        -> "Dim",
    Italic     -> "Italic",
    Underline  -> "Underline",
    Blink      -> "Blink",
    Reverse    -> "Reverse",
    Hidden     -> "Hidden",
    CrossedOut -> "CrossedOut",
  )

  /** Every flag at once — the opposite of [[None]] (ratatui spells it `Modifier::all()`).
    *
    * Derived from the [[Named]] table above rather than written out as an OR chain, so a flag added to that table joins
    * this value automatically instead of being forgotten here. Pass it wherever the intent is "no text attributes at
    * all" rather than a named list: `style.without(Modifiers.All)` is what [[Style.Reset]] uses.
    */
  val All: Modifiers = Named.foldLeft(Modifiers.None) { case (accumulated, (flag, _)) => accumulated | flag }

  extension (m: Modifiers)
    def |(other: Modifiers): Modifiers = (m: Int) | (other: Int)

    /** The flags set in *both* bitsets — set intersection. `(Bold | Italic) & (Italic | Dim)` is `Italic`, and anything
      * intersected with [[None]] is `None`. This is the plain name for "what do these two styles agree on", which
      * previously had to be spelled `m.without(m.without(other))` — correct, but a puzzle to read.
      *
      * Scala binds `&` tighter than `|`, so `a & b | c` groups as `(a & b) | c`, the way flag arithmetic groups in
      * every other language.
      */
    def &(other: Modifiers): Modifiers = (m: Int) & (other: Int)

    /** Whether *any* flag of `flag` is set. With a single flag — the overwhelmingly common call — that reads exactly as
      * it looks; with several ORed together it is an any-of test, so `(Bold | Italic).hasAny(Bold | Underline)` is
      * true. Use [[hasAll]] when every flag must be present.
      */
    def hasAny(flag: Modifiers): Boolean = ((m: Int) & (flag: Int)) != 0

    /** Whether *every* flag of `flags` is set. `hasAll(Modifiers.None)` is true — no flag is required. */
    def hasAll(flags: Modifiers): Boolean = ((m: Int) & (flags: Int)) == (flags: Int)

    def isEmpty: Boolean = (m: Int) == 0

    /** This bitset with every flag in `flags` cleared (ratatui's `sub_modifier`). */
    def without(flags: Modifiers): Modifiers = (m: Int) & ~(flags: Int)

    /** The names of the set flags, in declaration order; empty when nothing is set. */
    def names: Seq[String] = Named.collect { case (flag, name) if m.hasAny(flag) => name }

    /** The set flags as `"Bold|Italic"`, or `"None"` when nothing is set — what every `toString` that holds a
      * `Modifiers` should print instead of the raw `Int` the opaque type erases to.
      */
    def show: String = if m.isEmpty then "None" else names.mkString("|")
