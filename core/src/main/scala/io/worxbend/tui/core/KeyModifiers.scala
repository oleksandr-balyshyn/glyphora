package io.worxbend.tui.core

/** Modifier keys held during a key or mouse event, packed into an `Int` bitset (same pattern as [[Modifiers]]). */
opaque type KeyModifiers = Int

object KeyModifiers:
  val None: KeyModifiers  = 0
  val Shift: KeyModifiers = 1 << 0
  val Ctrl: KeyModifiers  = 1 << 1
  val Alt: KeyModifiers   = 1 << 2

  extension (m: KeyModifiers)
    def |(other: KeyModifiers): KeyModifiers = (m: Int) | (other: Int)

    /** Whether *any* modifier of `flag` is held. With a single modifier — the overwhelmingly common call — that reads
      * exactly as it looks; with several ORed together it is an any-of test. Use [[hasAll]] for every-of.
      */
    def hasAny(flag: KeyModifiers): Boolean = ((m: Int) & (flag: Int)) != 0

    /** Whether *every* modifier of `flags` is held, e.g. a Ctrl+Shift chord. */
    def hasAll(flags: KeyModifiers): Boolean = ((m: Int) & (flags: Int)) == (flags: Int)

    def isEmpty: Boolean = (m: Int) == 0

    /** The names of the held modifiers, in declaration order; empty when none are held. */
    def names: Seq[String] = Named.collect { case (flag, name) if m.hasAny(flag) => name }

    /** The held modifiers as `"Ctrl|Shift"`, or `"None"` when none are — what a `toString` holding a `KeyModifiers`
      * should print instead of the raw `Int` the opaque type erases to.
      */
    def show: String = if m.isEmpty then "None" else names.mkString("|")

  /** Every modifier paired with its name, in bit order: the single table [[names]] and [[show]] both read. */
  private val Named: Seq[(KeyModifiers, String)] = Seq(
    Shift -> "Shift",
    Ctrl  -> "Ctrl",
    Alt   -> "Alt",
  )
